package ee.kaarel.familybudgetapplication.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import ee.kaarel.familybudgetapplication.appConfig.ApiException;
import ee.kaarel.familybudgetapplication.dto.transfer.TransferTargetsResponse;
import ee.kaarel.familybudgetapplication.model.AccountUser;
import ee.kaarel.familybudgetapplication.model.AccountUserRole;
import ee.kaarel.familybudgetapplication.model.Account;
import ee.kaarel.familybudgetapplication.model.AccountType;
import ee.kaarel.familybudgetapplication.model.Role;
import ee.kaarel.familybudgetapplication.model.User;
import ee.kaarel.familybudgetapplication.model.UserStatus;
import ee.kaarel.familybudgetapplication.repository.AccountBalanceAdjustmentRepository;
import ee.kaarel.familybudgetapplication.repository.AccountRepository;
import ee.kaarel.familybudgetapplication.repository.AccountUserRepository;
import ee.kaarel.familybudgetapplication.repository.TransactionRepository;
import ee.kaarel.familybudgetapplication.repository.UserRepository;
import java.util.List;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
class AccountServiceTest {

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private AccountBalanceAdjustmentRepository accountBalanceAdjustmentRepository;

    @Mock
    private AccountUserRepository accountUserRepository;

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private CurrentUserService currentUserService;

    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private AccountService accountService;

    @Test
    void transferTargetMainAccountIsResolvedAndValidated() {
        User targetUser = createUser(2L, "parent", Role.PARENT);
        Account mainAccount = createMainAccount(10L, targetUser);

        when(accountRepository.findAllByOwner(targetUser)).thenReturn(List.of(mainAccount));

        Account resolved = accountService.getTransferTargetMainAccount(targetUser);

        assertThat(resolved.getId()).isEqualTo(mainAccount.getId());
    }

    @Test
    void transferTargetMainAccountMissingFails() {
        User targetUser = createUser(2L, "parent", Role.PARENT);

        when(accountRepository.findAllByOwner(targetUser)).thenReturn(List.of());

        ApiException exception = assertThrows(ApiException.class, () -> accountService.getTransferTargetMainAccount(targetUser));

        assertThat(exception.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void transferTargetsForChildIncludeOwnAndSharedAccounts() {
        User child = createUser(1L, "child", Role.CHILD);
        User parent = createUser(2L, "parent", Role.PARENT);
        User admin = createUser(3L, "admin", Role.ADMIN);

        Account childMain = createMainAccount(10L, child);
        Account parentMain = createMainAccount(20L, parent);
        Account adminMain = createMainAccount(30L, admin);
        AccountUser parentShare = createAccountUser(parentMain, child, AccountUserRole.VIEWER);
        AccountUser adminShare = createAccountUser(adminMain, child, AccountUserRole.EDITOR);

        when(currentUserService.getCurrentUser()).thenReturn(child);
        when(accountRepository.findAll(any(org.springframework.data.domain.Sort.class))).thenReturn(List.of(childMain, parentMain, adminMain));
        when(accountUserRepository.findByAccountAndUser(parentMain, child)).thenReturn(Optional.of(parentShare));
        when(accountUserRepository.findByAccountAndUser(adminMain, child)).thenReturn(Optional.of(adminShare));
        when(transactionRepository.calculateBalance(childMain)).thenReturn(BigDecimal.ZERO);
        when(transactionRepository.calculateBalance(parentMain)).thenReturn(BigDecimal.ZERO);
        when(transactionRepository.calculateBalance(adminMain)).thenReturn(BigDecimal.ZERO);
        when(accountBalanceAdjustmentRepository.calculateAdjustmentBalance(childMain)).thenReturn(BigDecimal.ZERO);
        when(accountBalanceAdjustmentRepository.calculateAdjustmentBalance(parentMain)).thenReturn(BigDecimal.ZERO);
        when(accountBalanceAdjustmentRepository.calculateAdjustmentBalance(adminMain)).thenReturn(BigDecimal.ZERO);

        TransferTargetsResponse targets = accountService.getTransferTargets();

        assertThat(targets.myAccounts()).extracting(account -> account.id()).containsExactly(childMain.getId());
        assertThat(targets.otherUsers()).hasSize(2);
        assertThat(targets.otherUsers())
                .extracting(user -> user.username())
                .containsExactly("admin", "parent");
    }

    private AccountUser createAccountUser(Account account, User user, AccountUserRole role) {
        AccountUser accountUser = new AccountUser();
        accountUser.setAccount(account);
        accountUser.setUser(user);
        accountUser.setRole(role);
        return accountUser;
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

    private Account createMainAccount(Long id, User owner) {
        Account account = new Account();
        account.setId(id);
        account.setOwner(owner);
        account.setName(owner.getUsername() + " MAIN");
        account.setType(AccountType.MAIN);
        account.setDefault(true);
        return account;
    }
}
