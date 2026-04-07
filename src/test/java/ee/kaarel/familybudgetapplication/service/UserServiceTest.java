package ee.kaarel.familybudgetapplication.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

import ee.kaarel.familybudgetapplication.dto.user.UserResponse;
import ee.kaarel.familybudgetapplication.model.Account;
import ee.kaarel.familybudgetapplication.model.AccountType;
import ee.kaarel.familybudgetapplication.model.Role;
import ee.kaarel.familybudgetapplication.model.User;
import ee.kaarel.familybudgetapplication.model.UserStatus;
import ee.kaarel.familybudgetapplication.repository.AccountRepository;
import ee.kaarel.familybudgetapplication.repository.NotificationRepository;
import ee.kaarel.familybudgetapplication.repository.RecurringPaymentRepository;
import ee.kaarel.familybudgetapplication.repository.TransactionRepository;
import ee.kaarel.familybudgetapplication.repository.UserRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private RecurringPaymentRepository recurringPaymentRepository;

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private CurrentUserService currentUserService;

    @Mock
    private AccountService accountService;

    @InjectMocks
    private UserService userService;

    @Test
    void selectableUsersForChildIncludeOnlyCurrentUser() {
        User currentUser = createUser(1L, "child", Role.CHILD);
        User otherUser = createUser(2L, "parent", Role.PARENT);

        when(currentUserService.getCurrentUser()).thenReturn(currentUser);
        when(userRepository.findAll()).thenReturn(List.of(otherUser, currentUser));
        when(accountRepository.findByOwnerAndTypeAndIsDefaultTrue(currentUser, AccountType.MAIN)).thenReturn(Optional.empty());

        List<UserResponse> users = userService.getUsers(true);

        assertEquals(1, users.size());
        assertEquals(currentUser.getId(), users.get(0).id());
    }

    @Test
    void selectableUsersForParentStillIncludeOtherUsers() {
        User currentUser = createUser(1L, "alice", Role.PARENT);
        User childUser = createUser(2L, "bob", Role.CHILD);

        when(currentUserService.getCurrentUser()).thenReturn(currentUser);
        when(userRepository.findAll()).thenReturn(List.of(childUser, currentUser));
        when(accountRepository.findByOwnerAndTypeAndIsDefaultTrue(currentUser, AccountType.MAIN)).thenReturn(Optional.empty());
        when(accountRepository.findByOwnerAndTypeAndIsDefaultTrue(childUser, AccountType.MAIN)).thenReturn(Optional.empty());

        List<UserResponse> users = userService.getUsers(true);

        assertEquals(2, users.size());
        assertEquals(currentUser.getId(), users.get(0).id());
        assertEquals(childUser.getId(), users.get(1).id());
    }

    @Test
    void transferTargetsForChildIncludeVisibleOwnersAndSelf() {
        User currentUser = createUser(1L, "child", Role.CHILD);
        User sharedOwner = createUser(2L, "parent", Role.PARENT);
        User adminOwner = createUser(3L, "admin", Role.ADMIN);
        User unrelated = createUser(4L, "other", Role.PARENT);

        Account sharedAccount = createAccount(10L, sharedOwner);
        Account ownAccount = createAccount(11L, currentUser);
        Account adminAccount = createAccount(12L, adminOwner);

        when(currentUserService.getCurrentUser()).thenReturn(currentUser);
        when(accountService.getVisibleAccounts(currentUser)).thenReturn(List.of(sharedAccount, ownAccount, adminAccount));
        when(userRepository.findAll()).thenReturn(List.of(unrelated, adminOwner, sharedOwner, currentUser));
        when(accountRepository.findByOwnerAndTypeAndIsDefaultTrue(currentUser, AccountType.MAIN)).thenReturn(Optional.empty());
        when(accountRepository.findByOwnerAndTypeAndIsDefaultTrue(sharedOwner, AccountType.MAIN)).thenReturn(Optional.empty());

        List<UserResponse> users = userService.getTransferTargets();

        assertEquals(3, users.size());
        assertEquals(adminOwner.getId(), users.get(0).id());
        assertEquals(currentUser.getId(), users.get(1).id());
        assertEquals(sharedOwner.getId(), users.get(2).id());
    }

    @Test
    void transferTargetsForParentIncludeVisibleChildAndAdmin() {
        User currentUser = createUser(1L, "parent", Role.PARENT);
        User childUser = createUser(2L, "child", Role.CHILD);
        User adminOwner = createUser(3L, "admin", Role.ADMIN);
        User unrelated = createUser(4L, "other", Role.PARENT);

        Account childAccount = createAccount(10L, childUser);
        Account ownAccount = createAccount(11L, currentUser);
        Account adminAccount = createAccount(12L, adminOwner);

        when(currentUserService.getCurrentUser()).thenReturn(currentUser);
        when(accountService.getVisibleAccounts(currentUser)).thenReturn(List.of(childAccount, ownAccount, adminAccount));
        when(userRepository.findAll()).thenReturn(List.of(unrelated, adminOwner, childUser, currentUser));
        when(accountRepository.findByOwnerAndTypeAndIsDefaultTrue(currentUser, AccountType.MAIN)).thenReturn(Optional.empty());
        when(accountRepository.findByOwnerAndTypeAndIsDefaultTrue(childUser, AccountType.MAIN)).thenReturn(Optional.empty());

        List<UserResponse> users = userService.getTransferTargets();

        assertEquals(3, users.size());
        assertEquals(adminOwner.getId(), users.get(0).id());
        assertEquals(childUser.getId(), users.get(1).id());
        assertEquals(currentUser.getId(), users.get(2).id());
    }

    @Test
    void transferTargetValidationRejectsUnlinkedUserForChild() {
        User currentUser = createUser(1L, "child", Role.CHILD);
        User unrelated = createUser(3L, "other", Role.PARENT);

        when(accountService.getVisibleAccounts(currentUser)).thenReturn(List.of());

        try {
            userService.ensureTransferTargetAllowed(currentUser, unrelated);
        } catch (Exception exception) {
            assertEquals("You cannot transfer money to this user", exception.getMessage());
            return;
        }

        throw new AssertionError("Expected transfer target validation to fail");
    }

    private User createUser(Long id, String username, Role role) {
        User user = new User();
        user.setId(id);
        user.setUsername(username);
        user.setPassword("password");
        user.setRole(role);
        user.setStatus(UserStatus.ACTIVE);
        user.setPreferredLanguage("en");
        return user;
    }

    private Account createAccount(Long id, User owner) {
        Account account = new Account();
        account.setId(id);
        account.setOwner(owner);
        account.setName(owner.getUsername() + " MAIN");
        account.setType(AccountType.MAIN);
        account.setDefault(true);
        return account;
    }
}
