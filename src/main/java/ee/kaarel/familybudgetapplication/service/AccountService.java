package ee.kaarel.familybudgetapplication.service;

import ee.kaarel.familybudgetapplication.appConfig.ApiException;
import ee.kaarel.familybudgetapplication.dto.account.AccountResponse;
import ee.kaarel.familybudgetapplication.dto.account.AdjustBalanceRequest;
import ee.kaarel.familybudgetapplication.dto.account.CreateAccountRequest;
import ee.kaarel.familybudgetapplication.dto.account.UpdateAccountRequest;
import ee.kaarel.familybudgetapplication.dto.common.ListResponse;
import ee.kaarel.familybudgetapplication.model.Account;
import ee.kaarel.familybudgetapplication.model.AccountBalanceAdjustment;
import ee.kaarel.familybudgetapplication.model.AccountType;
import ee.kaarel.familybudgetapplication.model.NotificationType;
import ee.kaarel.familybudgetapplication.model.Role;
import ee.kaarel.familybudgetapplication.model.User;
import ee.kaarel.familybudgetapplication.repository.AccountRepository;
import ee.kaarel.familybudgetapplication.repository.AccountBalanceAdjustmentRepository;
import ee.kaarel.familybudgetapplication.repository.TransactionRepository;
import jakarta.persistence.criteria.JoinType;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
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
    private final TransactionRepository transactionRepository;
    private final CurrentUserService currentUserService;
    private final NotificationService notificationService;

    public AccountService(
            AccountRepository accountRepository,
            AccountBalanceAdjustmentRepository accountBalanceAdjustmentRepository,
            TransactionRepository transactionRepository,
            CurrentUserService currentUserService,
            NotificationService notificationService
    ) {
        this.accountRepository = accountRepository;
        this.accountBalanceAdjustmentRepository = accountBalanceAdjustmentRepository;
        this.transactionRepository = transactionRepository;
        this.currentUserService = currentUserService;
        this.notificationService = notificationService;
    }

    @Transactional(readOnly = true)
    public ListResponse<AccountResponse> getAccounts(Pageable pageable) {
        User currentUser = currentUserService.getCurrentUser();
        Pageable sorted = PageableUtils.withDefaultSort(pageable, Sort.by("owner.username").ascending().and(Sort.by("name").ascending()));
        Specification<Account> specification = visibleAccountSpecification(currentUser);
        Page<Account> page = accountRepository.findAll(specification, sorted);
        return new ListResponse<>(page.map(this::toResponse).getContent(), page.getTotalElements());
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
        return toResponse(accountRepository.save(account));
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
        if (currentUser.getRole() == Role.CHILD && !account.getOwner().getId().equals(currentUser.getId())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "You cannot access this account");
        }
        if (currentUser.getRole() == Role.PARENT
                && account.getOwner().getRole() == Role.PARENT
                && !account.getOwner().getId().equals(currentUser.getId())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "You cannot access another parent account");
        }
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

    private Specification<Account> visibleAccountSpecification(User currentUser) {
        return (root, query, cb) -> {
            if (query != null && !Long.class.equals(query.getResultType()) && !long.class.equals(query.getResultType())) {
                root.fetch("owner", JoinType.LEFT);
                query.distinct(true);
            }
            if (currentUser.getRole() == Role.ADMIN) {
                return cb.conjunction();
            }
            if (currentUser.getRole() == Role.CHILD) {
                return cb.equal(root.get("owner").get("id"), currentUser.getId());
            }
            return cb.or(
                    cb.equal(root.get("owner").get("id"), currentUser.getId()),
                    cb.equal(root.get("owner").get("role"), Role.ADMIN),
                    cb.equal(root.get("owner").get("role"), Role.CHILD)
            );
        };
    }

    public AccountResponse toResponse(Account account) {
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
                balance
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
}
