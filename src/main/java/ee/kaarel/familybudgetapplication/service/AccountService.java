package ee.kaarel.familybudgetapplication.service;

import ee.kaarel.familybudgetapplication.appConfig.ApiException;
import ee.kaarel.familybudgetapplication.dto.account.AccountResponse;
import ee.kaarel.familybudgetapplication.dto.account.AdjustBalanceRequest;
import ee.kaarel.familybudgetapplication.dto.account.CreateAccountRequest;
import ee.kaarel.familybudgetapplication.dto.account.AccountShareResponse;
import ee.kaarel.familybudgetapplication.dto.account.ShareAccountRequest;
import ee.kaarel.familybudgetapplication.dto.account.UpdateAccountRequest;
import ee.kaarel.familybudgetapplication.dto.common.ListResponse;
import ee.kaarel.familybudgetapplication.model.Account;
import ee.kaarel.familybudgetapplication.model.AccountBalanceAdjustment;
import ee.kaarel.familybudgetapplication.model.AccountUser;
import ee.kaarel.familybudgetapplication.model.AccountUserRole;
import ee.kaarel.familybudgetapplication.model.AccountType;
import ee.kaarel.familybudgetapplication.model.NotificationType;
import ee.kaarel.familybudgetapplication.model.Role;
import ee.kaarel.familybudgetapplication.model.User;
import ee.kaarel.familybudgetapplication.repository.AccountRepository;
import ee.kaarel.familybudgetapplication.repository.AccountBalanceAdjustmentRepository;
import ee.kaarel.familybudgetapplication.repository.AccountUserRepository;
import ee.kaarel.familybudgetapplication.repository.TransactionRepository;
import ee.kaarel.familybudgetapplication.repository.UserRepository;
import jakarta.persistence.criteria.JoinType;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountService {

    private final AccountRepository accountRepository;
    private final AccountBalanceAdjustmentRepository accountBalanceAdjustmentRepository;
    private final AccountUserRepository accountUserRepository;
    private final TransactionRepository transactionRepository;
    private final UserRepository userRepository;
    private final CurrentUserService currentUserService;
    private final NotificationService notificationService;

    public AccountService(
            AccountRepository accountRepository,
            AccountBalanceAdjustmentRepository accountBalanceAdjustmentRepository,
            AccountUserRepository accountUserRepository,
            TransactionRepository transactionRepository,
            UserRepository userRepository,
            CurrentUserService currentUserService,
            NotificationService notificationService
    ) {
        this.accountRepository = accountRepository;
        this.accountBalanceAdjustmentRepository = accountBalanceAdjustmentRepository;
        this.accountUserRepository = accountUserRepository;
        this.transactionRepository = transactionRepository;
        this.userRepository = userRepository;
        this.currentUserService = currentUserService;
        this.notificationService = notificationService;
    }

    @Transactional(readOnly = true)
    public ListResponse<AccountResponse> getAccounts(Pageable pageable) {
        User currentUser = currentUserService.getCurrentUser();
        Pageable sorted = PageableUtils.withDefaultSort(pageable, Sort.by("owner.username").ascending().and(Sort.by("name").ascending()));
        Specification<Account> specification = visibleAccountSpecification(currentUser);
        Page<Account> page = accountRepository.findAll(specification, sorted);
        Map<Long, AccountUserRole> accessRoles = loadAccessRoles(currentUser, page.getContent());
        return new ListResponse<>(
                page.map(account -> toResponse(account, accessRoles.get(account.getId()))).getContent(),
                page.getTotalElements()
        );
    }

    @Transactional(readOnly = true)
    public List<Account> getVisibleAccounts(User currentUser) {
        return accountRepository.findAll(visibleAccountSpecification(currentUser), Sort.by("owner.username").ascending().and(Sort.by("name").ascending()));
    }

    @Transactional
    public AccountResponse createAccount(CreateAccountRequest request) {
        User currentUser = currentUserService.getCurrentUser();
        Account account = new Account();
        account.setName(request.name());
        account.setOwner(currentUser);
        account.setType(request.type());
        account.setDefault(false);
        account.setDeletionRequested(false);
        account.setDeletionRequestedAt(null);
        Account savedAccount = accountRepository.save(account);
        grantAccountAccess(savedAccount, currentUser, AccountUserRole.OWNER);
        return toResponse(savedAccount);
    }

    @Transactional
    public AccountResponse shareAccount(Long id, ShareAccountRequest request) {
        User currentUser = currentUserService.getCurrentUser();
        Account account = getAccount(id);
        ensureCanAccessAccount(currentUser, account);

        if (!canShareAccount(currentUser, account)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "You cannot share this account");
        }

        if (request.role() == AccountUserRole.OWNER) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Ownership cannot be assigned through sharing");
        }

        User targetUser = userRepository.findById(request.userId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "User not found"));

        if (!canShareWithUser(currentUser, targetUser)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "You cannot share this account with this user");
        }

        if (account.getOwner().getId().equals(targetUser.getId())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Account owner already has access");
        }

        grantAccountAccess(account, targetUser, request.role());
        notificationService.notifyAccountShared(targetUser, currentUser, account, request.role());
        return toResponse(account);
    }

    @Transactional
    public AccountResponse revokeShare(Long id, Long userId) {
        User currentUser = currentUserService.getCurrentUser();
        Account account = getAccount(id);
        ensureCanAccessAccount(currentUser, account);

        if (!canShareAccount(currentUser, account)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "You cannot share this account");
        }

        User targetUser = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "User not found"));

        if (account.getOwner().getId().equals(targetUser.getId())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Account owner access cannot be removed");
        }

        AccountUser accountUser = accountUserRepository.findByAccountAndUser(account, targetUser)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Account access not found"));

        if (accountUser.getRole() == AccountUserRole.OWNER) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Account owner access cannot be removed");
        }

        account.getAccountUsers().removeIf(entry -> entry.getUser().getId().equals(targetUser.getId()));
        targetUser.getAccountUsers().removeIf(entry -> entry.getAccount().getId().equals(account.getId()));
        accountUserRepository.delete(accountUser);
        notificationService.notifyAccountUnshared(targetUser, currentUser, account);
        return toResponse(account);
    }

    @Transactional
    public AccountResponse updateAccount(Long id, UpdateAccountRequest request) {
        User currentUser = currentUserService.getCurrentUser();
        Account account = getAccount(id);
        ensureCanAccessAccount(currentUser, account);

        if (!canRenameAccount(currentUser, account)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "You cannot rename this account");
        }

        account.setName(request.name());
        return toResponse(accountRepository.save(account));
    }

    @Transactional
    public AccountResponse adjustBalance(Long id, AdjustBalanceRequest request) {
        User currentUser = currentUserService.getCurrentUser();
        Account account = getAccount(id);
        ensureCanAccessAccount(currentUser, account);

        if (!canRenameAccount(currentUser, account)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "You cannot adjust this account balance");
        }

        BigDecimal amount = request.amount();
        if (amount == null || amount.compareTo(BigDecimal.ZERO) == 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Adjustment amount must not be zero");
        }

        AccountBalanceAdjustment adjustment = new AccountBalanceAdjustment();
        adjustment.setAccount(account);
        adjustment.setAmount(amount);
        adjustment.setComment(request.comment().trim());
        account.getManualAdjustments().add(adjustment);

        accountRepository.save(account);
        return toResponse(account);
    }

    @Transactional
    public void deleteAccount(Long id) {
        User currentUser = currentUserService.getCurrentUser();
        Account account = getAccount(id);
        ensureCanAccessAccount(currentUser, account);

        if (account.isDefault()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Default MAIN accounts cannot be deleted");
        }

        if (currentUser.getRole() == Role.ADMIN) {
            transactionRepository.deleteAll(transactionRepository.findAllByFromAccountOrToAccount(account, account));
            accountRepository.delete(account);
            return;
        }

        if (!canManageAccount(currentUser, account)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "You cannot request deletion for this account");
        }

        account.setDeletionRequested(true);
        account.setDeletionRequestedAt(OffsetDateTime.now());
        accountRepository.save(account);
        notificationService.notifyAdmins(
                NotificationType.ACCOUNT_DELETION_APPROVAL_REQUIRED,
                "Account deletion approval required for account " + account.getName() + " (id=" + account.getId() + ")"
        );
    }

    @Transactional(readOnly = true)
    public Account getAccount(Long id) {
        return accountRepository.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Account not found"));
    }

    @Transactional(readOnly = true)
    public Account getDefaultMainAccount(User user) {
        return accountRepository.findByOwnerAndTypeAndIsDefaultTrue(user, AccountType.MAIN)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Default account not found"));
    }

    public void ensureCanAccessAccount(User currentUser, Account account) {
        if (currentUser.getRole() == Role.ADMIN) {
            return;
        }
        if (hasVisibleAccess(currentUser, account)) {
            return;
        }

        throw new ApiException(HttpStatus.FORBIDDEN, "You cannot access this account");
    }

    public boolean canManageAccount(User currentUser, Account account) {
        if (currentUser.getRole() == Role.ADMIN) {
            return true;
        }
        if (currentUser.getRole() == Role.CHILD) {
            return account.getOwner().getId().equals(currentUser.getId());
        }
        return account.getOwner().getId().equals(currentUser.getId()) || account.getOwner().getRole() == Role.CHILD;
    }

    public boolean canRenameAccount(User currentUser, Account account) {
        if (currentUser.getRole() == Role.ADMIN) {
            return true;
        }

        return account.getOwner().getId().equals(currentUser.getId());
    }

    public boolean canShareAccount(User currentUser, Account account) {
        return currentUser.getRole() == Role.ADMIN || account.getOwner().getId().equals(currentUser.getId());
    }

    public boolean canTransactFromAccount(User currentUser, Account account) {
        if (currentUser.getRole() == Role.ADMIN) {
            return true;
        }

        AccountUserRole accountRole = getAccountRole(currentUser, account);
        return accountRole == AccountUserRole.OWNER || accountRole == AccountUserRole.EDITOR;
    }

    private Specification<Account> visibleAccountSpecification(User currentUser) {
        return (root, query, cb) -> {
            if (query != null && !Long.class.equals(query.getResultType()) && !long.class.equals(query.getResultType())) {
                root.fetch("owner", JoinType.LEFT);
                root.fetch("accountUsers", JoinType.LEFT).fetch("user", JoinType.LEFT);
                query.distinct(true);
            }
            if (currentUser.getRole() == Role.ADMIN) {
                return cb.conjunction();
            }
            var accountUserJoin = root.join("accountUsers", JoinType.LEFT);
            if (currentUser.getRole() == Role.CHILD) {
                return cb.or(
                        cb.equal(root.get("owner").get("id"), currentUser.getId()),
                        cb.equal(accountUserJoin.get("user").get("id"), currentUser.getId())
                );
            }
            return cb.or(
                    cb.equal(root.get("owner").get("id"), currentUser.getId()),
                    cb.equal(root.get("owner").get("role"), Role.ADMIN),
                    cb.equal(root.get("owner").get("role"), Role.CHILD),
                    cb.equal(accountUserJoin.get("user").get("id"), currentUser.getId())
            );
        };
    }

    public AccountResponse toResponse(Account account) {
        User currentUser = currentUserService.getCurrentUser();
        return toResponse(account, getAccountRole(currentUser, account));
    }

    private AccountResponse toResponse(Account account, AccountUserRole accessRole) {
        BigDecimal balance = getCalculatedBalance(account);
        return new AccountResponse(
                account.getId(),
                account.getName(),
                account.getOwner().getId(),
                account.getOwner().getUsername(),
                account.getOwner().getRole(),
                account.getType(),
                account.isDefault(),
                account.isDeletionRequested(),
                balance,
                accessRole,
                buildSharedUsers(account)
        );
    }

    public BigDecimal getCalculatedBalance(Account account) {
        return transactionRepository.calculateBalance(account)
                .add(accountBalanceAdjustmentRepository.calculateAdjustmentBalance(account))
                .add(normalizeMoney(account.getInitialBalance()));
    }

    private BigDecimal normalizeMoney(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    public void grantAccountAccess(Account account, User user, AccountUserRole role) {
        AccountUser accountUser = accountUserRepository.findByAccountAndUser(account, user)
                .orElseGet(AccountUser::new);
        accountUser.setAccount(account);
        accountUser.setUser(user);
        accountUser.setRole(role);
        accountUserRepository.save(accountUser);
        account.getAccountUsers().removeIf(entry -> entry.getUser().getId().equals(user.getId()));
        account.getAccountUsers().add(accountUser);
        user.getAccountUsers().removeIf(entry -> entry.getAccount().getId().equals(account.getId()));
        user.getAccountUsers().add(accountUser);
    }

    private AccountUserRole getAccountRole(User currentUser, Account account) {
        if (currentUser.getRole() == Role.ADMIN) {
            return AccountUserRole.OWNER;
        }

        if (account.getOwner().getId().equals(currentUser.getId())) {
            return AccountUserRole.OWNER;
        }

        return accountUserRepository.findByAccountAndUser(account, currentUser)
                .map(AccountUser::getRole)
                .orElse(null);
    }

    private Map<Long, AccountUserRole> loadAccessRoles(User currentUser, List<Account> accounts) {
        if (currentUser.getRole() == Role.ADMIN) {
            Map<Long, AccountUserRole> accessRoles = new HashMap<>();
            for (Account account : accounts) {
                accessRoles.put(account.getId(), AccountUserRole.OWNER);
            }
            return accessRoles;
        }

        Map<Long, AccountUserRole> accessRoles = new HashMap<>();
        for (AccountUser accountUser : accountUserRepository.findAllByUser(currentUser)) {
            accessRoles.put(accountUser.getAccount().getId(), accountUser.getRole());
        }

        for (Account account : accounts) {
            if (account.getOwner().getId().equals(currentUser.getId())) {
                accessRoles.putIfAbsent(account.getId(), AccountUserRole.OWNER);
            }
        }

        return accessRoles;
    }

    private boolean hasVisibleAccess(User currentUser, Account account) {
        if (currentUser.getRole() == Role.ADMIN) {
            return true;
        }

        if (account.getOwner().getId().equals(currentUser.getId())) {
            return true;
        }

        if (currentUser.getRole() == Role.CHILD) {
            return accountUserRepository.findByAccountAndUser(account, currentUser).isPresent();
        }

        if (account.getOwner().getRole() == Role.CHILD || account.getOwner().getRole() == Role.ADMIN) {
            return true;
        }

        return accountUserRepository.findByAccountAndUser(account, currentUser).isPresent();
    }

    private boolean canShareWithUser(User currentUser, User targetUser) {
        if (currentUser.getRole() == Role.ADMIN) {
            return true;
        }
        if (currentUser.getRole() == Role.PARENT) {
            return true;
        }
        return targetUser.getId().equals(currentUser.getId());
    }

    private List<AccountShareResponse> buildSharedUsers(Account account) {
        return account.getAccountUsers().stream()
                .filter(accountUser -> accountUser.getRole() != AccountUserRole.OWNER)
                .sorted((left, right) -> left.getUser().getUsername().compareToIgnoreCase(right.getUser().getUsername()))
                .map(accountUser -> new AccountShareResponse(
                        accountUser.getUser().getId(),
                        accountUser.getUser().getUsername(),
                        accountUser.getRole()
                ))
                .toList();
    }
}
