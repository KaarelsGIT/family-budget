package ee.kaarel.familybudgetapplication.service;

import ee.kaarel.familybudgetapplication.appConfig.ApiException;
import ee.kaarel.familybudgetapplication.dto.user.CreateUserRequest;
import ee.kaarel.familybudgetapplication.dto.user.UpdateUserRequest;
import ee.kaarel.familybudgetapplication.dto.user.UserResponse;
import ee.kaarel.familybudgetapplication.model.Account;
import ee.kaarel.familybudgetapplication.model.AccountUserRole;
import ee.kaarel.familybudgetapplication.model.AccountType;
import ee.kaarel.familybudgetapplication.model.Role;
import ee.kaarel.familybudgetapplication.model.User;
import ee.kaarel.familybudgetapplication.model.UserStatus;
import ee.kaarel.familybudgetapplication.repository.AccountRepository;
import ee.kaarel.familybudgetapplication.repository.NotificationRepository;
import ee.kaarel.familybudgetapplication.repository.RecurringPaymentRepository;
import ee.kaarel.familybudgetapplication.repository.TransactionRepository;
import ee.kaarel.familybudgetapplication.repository.UserRepository;
import java.util.Comparator;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final RecurringPaymentRepository recurringPaymentRepository;
    private final NotificationRepository notificationRepository;
    private final PasswordEncoder passwordEncoder;
    private final CurrentUserService currentUserService;
    private final AccountService accountService;

    public UserService(
            UserRepository userRepository,
            AccountRepository accountRepository,
            TransactionRepository transactionRepository,
            RecurringPaymentRepository recurringPaymentRepository,
            NotificationRepository notificationRepository,
            PasswordEncoder passwordEncoder,
            CurrentUserService currentUserService,
            AccountService accountService
    ) {
        this.userRepository = userRepository;
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
        this.recurringPaymentRepository = recurringPaymentRepository;
        this.notificationRepository = notificationRepository;
        this.passwordEncoder = passwordEncoder;
        this.currentUserService = currentUserService;
        this.accountService = accountService;
    }

    @Transactional
    public UserResponse createUser(CreateUserRequest request) {
        User currentUser = currentUserService.getCurrentUser();
        if (currentUser.getRole() != Role.ADMIN) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Only admins can create users");
        }
        if (request.role() == Role.ADMIN) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Admin user can only be created on startup");
        }
        if (userRepository.existsByUsername(request.username())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Username already exists");
        }

        User user = new User();
        user.setUsername(request.username());
        user.setPassword(passwordEncoder.encode(request.password()));
        user.setRole(request.role());
        user.setStatus(UserStatus.PENDING);
        user.setPreferredLanguage("et");
        User savedUser = userRepository.save(user);

        Account mainAccount = new Account();
        mainAccount.setName(savedUser.getUsername() + " MAIN");
        mainAccount.setOwner(savedUser);
        mainAccount.setType(AccountType.MAIN);
        mainAccount.setDefault(true);
        mainAccount.setDeletionRequested(false);
        Account savedMainAccount = accountRepository.save(mainAccount);
        accountService.grantAccountAccess(savedMainAccount, savedUser, AccountUserRole.OWNER);
        return toResponse(savedUser);
    }

    @Transactional(readOnly = true)
    public List<UserResponse> getUsers(boolean selectable) {
        User currentUser = currentUserService.getCurrentUser();
        if (selectable) {
            return userRepository.findAll().stream()
                    .filter(user -> canSelectUser(currentUser, user))
                    .sorted(Comparator.comparing(User::getUsername, String.CASE_INSENSITIVE_ORDER))
                    .map(this::toResponse)
                    .toList();
        }
        if (currentUser.getRole() != Role.ADMIN) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Only admins can view all users");
        }

        return userRepository.findAll().stream()
                .sorted(Comparator.comparing(User::getUsername, String.CASE_INSENSITIVE_ORDER))
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public void ensureSelectableUser(User currentUser, User targetUser) {
        if (!canSelectUser(currentUser, targetUser)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "You cannot transfer money to this user");
        }
    }

    @Transactional
    public UserResponse updateUser(Long id, UpdateUserRequest request) {
        User currentUser = currentUserService.getCurrentUser();
        User targetUser = userRepository.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "User not found"));

        boolean self = currentUser.getId().equals(id);
        boolean admin = currentUser.getRole() == Role.ADMIN;
        if (!self && !admin) {
            throw new ApiException(HttpStatus.FORBIDDEN, "You cannot update this user");
        }

        if (request.username() != null && !request.username().equals(targetUser.getUsername())) {
            if (userRepository.existsByUsername(request.username())) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "Username already exists");
            }
            targetUser.setUsername(request.username());
        }
        if (request.password() != null) {
            targetUser.setPassword(passwordEncoder.encode(request.password()));
        }
        if (request.role() != null) {
            if (!admin) {
                throw new ApiException(HttpStatus.FORBIDDEN, "Only admins can change roles");
            }
            if (targetUser.getRole() == Role.ADMIN || request.role() == Role.ADMIN) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "Admin role changes are not supported");
            }
            targetUser.setRole(request.role());
        }
        if (request.preferredLanguage() != null) {
            validatePreferredLanguage(request.preferredLanguage());
            targetUser.setPreferredLanguage(request.preferredLanguage());
        }
        if (targetUser.getStatus() == UserStatus.PENDING) {
            targetUser.setStatus(UserStatus.ACTIVE);
        }
        return toResponse(userRepository.save(targetUser));
    }

    @Transactional
    public void deleteUser(Long id) {
        User currentUser = currentUserService.getCurrentUser();
        if (currentUser.getRole() != Role.ADMIN) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Only admins can delete users");
        }

        User targetUser = userRepository.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "User not found"));

        if (currentUser.getId().equals(id)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Admin cannot delete their own user");
        }

        if (targetUser.getRole() == Role.ADMIN) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Admin users cannot be deleted");
        }

        transactionRepository.deleteAllLinkedToUser(targetUser);
        recurringPaymentRepository.deleteAllByOwner(targetUser);
        notificationRepository.deleteAllByUser(targetUser);
        accountRepository.deleteAllByOwner(targetUser);
        userRepository.delete(targetUser);
    }

    @Transactional(readOnly = true)
    public User findUser(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "User not found"));
    }

    public UserResponse toResponse(User user) {
        Long defaultMainAccountId = accountRepository.findByOwnerAndTypeAndIsDefaultTrue(user, AccountType.MAIN)
                .map(Account::getId)
                .orElse(null);
        return new UserResponse(
                user.getId(),
                user.getUsername(),
                user.getRole(),
                user.getStatus(),
                user.getPreferredLanguage(),
                defaultMainAccountId
        );
    }

    private void validatePreferredLanguage(String preferredLanguage) {
        if (!"et".equals(preferredLanguage) && !"en".equals(preferredLanguage) && !"fi".equals(preferredLanguage)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Unsupported language");
        }
    }

    private boolean canSelectUser(User currentUser, User targetUser) {
        if (currentUser.getRole() == Role.ADMIN) {
            return true;
        }
        if (currentUser.getRole() == Role.PARENT) {
            return true;
        }
        return targetUser.getId().equals(currentUser.getId());
    }
}
