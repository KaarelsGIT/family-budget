package ee.kaarel.familybudgetapplication.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

import ee.kaarel.familybudgetapplication.appConfig.ApiException;
import ee.kaarel.familybudgetapplication.dto.user.UserResponse;
import ee.kaarel.familybudgetapplication.model.AccountType;
import ee.kaarel.familybudgetapplication.model.Role;
import ee.kaarel.familybudgetapplication.model.User;
import ee.kaarel.familybudgetapplication.model.UserStatus;
import ee.kaarel.familybudgetapplication.repository.AccountRepository;
import ee.kaarel.familybudgetapplication.repository.NotificationRepository;
import ee.kaarel.familybudgetapplication.repository.TransactionRepository;
import ee.kaarel.familybudgetapplication.repository.UserRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
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
        User currentUser = createUser(1L, "child", Role.CHILD, null);
        User otherUser = createUser(2L, "parent", Role.PARENT, null);

        when(currentUserService.getCurrentUser()).thenReturn(currentUser);
        when(userRepository.findAll()).thenReturn(List.of(otherUser, currentUser));
        when(accountRepository.findByOwnerAndTypeAndIsDefaultTrue(currentUser, AccountType.MAIN)).thenReturn(Optional.empty());

        List<UserResponse> users = userService.getUsers(true);

        assertEquals(1, users.size());
        assertEquals(currentUser.getId(), users.get(0).id());
    }

    @Test
    void selectableUsersForParentStillIncludeOtherUsers() {
        User currentUser = createUser(1L, "alice", Role.PARENT, null);
        User childUser = createUser(2L, "bob", Role.CHILD, null);

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
    void transferTargetsForChildIncludeAllFamilyUsers() {
        User currentUser = createUser(1L, "child", Role.CHILD, 100L);
        User parentUser = createUser(2L, "parent", Role.PARENT, 100L);
        User adminUser = createUser(3L, "admin", Role.ADMIN, 100L);
        User unrelated = createUser(4L, "other", Role.PARENT, 200L);

        when(currentUserService.getCurrentUser()).thenReturn(currentUser);
        when(userRepository.findAllByFamilyId(100L)).thenReturn(List.of(unrelated, adminUser, parentUser, currentUser));
        when(accountRepository.findByOwnerAndTypeAndIsDefaultTrue(currentUser, AccountType.MAIN)).thenReturn(Optional.empty());
        when(accountRepository.findByOwnerAndTypeAndIsDefaultTrue(parentUser, AccountType.MAIN)).thenReturn(Optional.empty());
        when(accountRepository.findByOwnerAndTypeAndIsDefaultTrue(adminUser, AccountType.MAIN)).thenReturn(Optional.empty());

        List<UserResponse> users = userService.getTransferTargets();

        assertEquals(3, users.size());
        assertEquals(adminUser.getId(), users.get(0).id());
        assertEquals(currentUser.getId(), users.get(1).id());
        assertEquals(parentUser.getId(), users.get(2).id());
    }

    @Test
    void transferTargetsForParentIncludeAllFamilyUsers() {
        User currentUser = createUser(1L, "parent", Role.PARENT, 100L);
        User childUser = createUser(2L, "child", Role.CHILD, 100L);
        User adminUser = createUser(3L, "admin", Role.ADMIN, 100L);
        User unrelated = createUser(4L, "other", Role.PARENT, 200L);

        when(currentUserService.getCurrentUser()).thenReturn(currentUser);
        when(userRepository.findAllByFamilyId(100L)).thenReturn(List.of(unrelated, adminUser, childUser, currentUser));
        when(accountRepository.findByOwnerAndTypeAndIsDefaultTrue(currentUser, AccountType.MAIN)).thenReturn(Optional.empty());
        when(accountRepository.findByOwnerAndTypeAndIsDefaultTrue(childUser, AccountType.MAIN)).thenReturn(Optional.empty());
        when(accountRepository.findByOwnerAndTypeAndIsDefaultTrue(adminUser, AccountType.MAIN)).thenReturn(Optional.empty());

        List<UserResponse> users = userService.getTransferTargets();

        assertEquals(3, users.size());
        assertEquals(adminUser.getId(), users.get(0).id());
        assertEquals(childUser.getId(), users.get(1).id());
        assertEquals(currentUser.getId(), users.get(2).id());
    }

    @Test
    void transferTargetsForAdminIncludeAllFamilyUsers() {
        User currentUser = createUser(1L, "admin", Role.ADMIN, 100L);
        User childUser = createUser(2L, "child", Role.CHILD, 100L);
        User parentUser = createUser(3L, "parent", Role.PARENT, 100L);
        User unrelated = createUser(4L, "other", Role.PARENT, 200L);

        when(currentUserService.getCurrentUser()).thenReturn(currentUser);
        when(userRepository.findAllByFamilyId(100L)).thenReturn(List.of(unrelated, parentUser, childUser, currentUser));
        when(accountRepository.findByOwnerAndTypeAndIsDefaultTrue(currentUser, AccountType.MAIN)).thenReturn(Optional.empty());
        when(accountRepository.findByOwnerAndTypeAndIsDefaultTrue(childUser, AccountType.MAIN)).thenReturn(Optional.empty());
        when(accountRepository.findByOwnerAndTypeAndIsDefaultTrue(parentUser, AccountType.MAIN)).thenReturn(Optional.empty());

        List<UserResponse> users = userService.getTransferTargets();

        assertEquals(3, users.size());
        assertEquals(currentUser.getId(), users.get(0).id());
        assertEquals(childUser.getId(), users.get(1).id());
        assertEquals(parentUser.getId(), users.get(2).id());
    }

    @Test
    void transferTargetsMatchNullFamilyIdExactly() {
        User currentUser = createUser(1L, "parent", Role.PARENT, null);
        User childUser = createUser(2L, "child", Role.CHILD, null);
        User adminUser = createUser(3L, "admin", Role.ADMIN, null);
        User unrelated = createUser(4L, "other", Role.PARENT, 200L);

        when(currentUserService.getCurrentUser()).thenReturn(currentUser);
        when(userRepository.findAllByFamilyId(null)).thenReturn(List.of(unrelated, childUser, adminUser, currentUser));
        when(accountRepository.findByOwnerAndTypeAndIsDefaultTrue(currentUser, AccountType.MAIN)).thenReturn(Optional.empty());
        when(accountRepository.findByOwnerAndTypeAndIsDefaultTrue(childUser, AccountType.MAIN)).thenReturn(Optional.empty());
        when(accountRepository.findByOwnerAndTypeAndIsDefaultTrue(adminUser, AccountType.MAIN)).thenReturn(Optional.empty());

        List<UserResponse> users = userService.getTransferTargets();

        assertEquals(3, users.size());
        assertEquals(adminUser.getId(), users.get(0).id());
        assertEquals(childUser.getId(), users.get(1).id());
        assertEquals(currentUser.getId(), users.get(2).id());
    }

    @Test
    void transferTargetValidationRejectsUsersOutsideFamily() {
        User currentUser = createUser(1L, "child", Role.CHILD, 100L);
        User unrelated = createUser(3L, "other", Role.PARENT, 200L);

        ApiException exception = org.junit.jupiter.api.Assertions.assertThrows(
                ApiException.class,
                () -> userService.ensureTransferTargetAllowed(currentUser, unrelated)
        );

        assertEquals(HttpStatus.FORBIDDEN, exception.getStatus());
        assertEquals("You cannot transfer money to this user", exception.getMessage());
    }

    private User createUser(Long id, String username, Role role, Long familyId) {
        User user = new User();
        user.setId(id);
        user.setUsername(username);
        user.setPassword("password");
        user.setRole(role);
        user.setFamilyId(familyId);
        user.setStatus(UserStatus.ACTIVE);
        user.setPreferredLanguage("en");
        return user;
    }
}
