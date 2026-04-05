package ee.kaarel.familybudgetapplication.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ee.kaarel.familybudgetapplication.model.Account;
import ee.kaarel.familybudgetapplication.model.AccountUser;
import ee.kaarel.familybudgetapplication.model.Notification;
import ee.kaarel.familybudgetapplication.model.NotificationType;
import ee.kaarel.familybudgetapplication.model.Role;
import ee.kaarel.familybudgetapplication.model.TransactionType;
import ee.kaarel.familybudgetapplication.model.User;
import ee.kaarel.familybudgetapplication.repository.AccountUserRepository;
import ee.kaarel.familybudgetapplication.repository.NotificationRepository;
import ee.kaarel.familybudgetapplication.repository.UserRepository;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private AccountUserRepository accountUserRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private CurrentUserService currentUserService;

    @InjectMocks
    private NotificationService notificationService;

    @Test
    void notifySharedAccountTransactionUsersNotifiesEveryOtherAccessorWithTransactionLink() {
        User actor = createUser(1L, "John", "en", Role.PARENT);
        User recipient = createUser(2L, "Jane", "en", Role.PARENT);
        Account account = createAccount(10L, "Shared Account");
        AccountUser actorAccess = createAccountUser(account, actor);
        AccountUser recipientAccess = createAccountUser(account, recipient);

        when(accountUserRepository.findAllByAccount(account)).thenReturn(List.of(actorAccess, recipientAccess));
        when(notificationRepository.existsByUserAndTypeAndRelatedTransactionIdAndRelatedAccountId(
                eq(recipient),
                eq(NotificationType.TRANSACTION_CREATED),
                eq(77L),
                eq(account.getId())
        ))
                .thenReturn(false);

        notificationService.notifySharedAccountTransactionUsers(
                account,
                actor,
                TransactionType.EXPENSE,
                BigDecimal.valueOf(50),
                77L,
                NotificationType.TRANSACTION_CREATED
        );

        ArgumentCaptor<Notification> notificationCaptor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(notificationCaptor.capture());
        verify(notificationRepository).existsByUserAndTypeAndRelatedTransactionIdAndRelatedAccountId(
                recipient,
                NotificationType.TRANSACTION_CREATED,
                77L,
                account.getId()
        );

        Notification saved = notificationCaptor.getValue();
        assertThat(saved.getUser()).isSameAs(recipient);
        assertThat(saved.getType()).isEqualTo(NotificationType.TRANSACTION_CREATED);
        assertThat(saved.getRelatedTransactionId()).isEqualTo(77L);
        assertThat(saved.getRelatedAccountId()).isEqualTo(account.getId());
        assertThat(saved.getMessage()).isEqualTo("John added expense €50.00 from Shared Account");
        assertThat(saved.getCreatedAt()).isBeforeOrEqualTo(OffsetDateTime.now());
    }

    private User createUser(Long id, String username, String preferredLanguage, Role role) {
        User user = new User();
        user.setId(id);
        user.setUsername(username);
        user.setPassword("password");
        user.setRole(role);
        user.setPreferredLanguage(preferredLanguage);
        return user;
    }

    private Account createAccount(Long id, String name) {
        Account account = new Account();
        account.setId(id);
        account.setName(name);
        return account;
    }

    private AccountUser createAccountUser(Account account, User user) {
        AccountUser accountUser = new AccountUser();
        accountUser.setAccount(account);
        accountUser.setUser(user);
        return accountUser;
    }
}
