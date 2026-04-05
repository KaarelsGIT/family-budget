package ee.kaarel.familybudgetapplication.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ee.kaarel.familybudgetapplication.dto.statistics.YearlyStatisticsResponse;
import ee.kaarel.familybudgetapplication.model.Account;
import ee.kaarel.familybudgetapplication.model.AccountType;
import ee.kaarel.familybudgetapplication.model.Role;
import ee.kaarel.familybudgetapplication.model.TransactionType;
import ee.kaarel.familybudgetapplication.model.User;
import ee.kaarel.familybudgetapplication.model.UserStatus;
import ee.kaarel.familybudgetapplication.repository.TransactionRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.Assertions;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class StatisticsServiceTest {

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private CurrentUserService currentUserService;

    @Mock
    private AccountService accountService;

    @Mock
    private UserService userService;

    @InjectMocks
    private StatisticsService statisticsService;

    @Test
    void getYearlyUsesVisibleAccountsAndDefaultCurrentUser() {
        User currentUser = createUser(1L, "John");
        Account visibleAccount = createAccount(10L, currentUser, "Main");

        when(currentUserService.getCurrentUser()).thenReturn(currentUser);
        when(accountService.getVisibleAccounts(currentUser)).thenReturn(List.of(visibleAccount));
        when(transactionRepository.findYearlyStatisticsRows(eq(2026), eq(1L), isNull(), any())).thenReturn(List.of());

        YearlyStatisticsResponse response = statisticsService.getYearly(2026, null, null);

        verify(transactionRepository).findYearlyStatisticsRows(2026, 1L, null, List.of(visibleAccount.getId()));
        Assertions.assertEquals(2026, response.year());
        Assertions.assertEquals(0, response.totals().income().compareTo(BigDecimal.ZERO));
    }

    private User createUser(Long id, String username) {
        User user = new User();
        user.setId(id);
        user.setUsername(username);
        user.setPassword("password");
        user.setRole(Role.PARENT);
        user.setStatus(UserStatus.ACTIVE);
        user.setPreferredLanguage("en");
        return user;
    }

    private Account createAccount(Long id, User owner, String name) {
        Account account = new Account();
        account.setId(id);
        account.setOwner(owner);
        account.setName(name);
        account.setType(AccountType.MAIN);
        account.setDefault(true);
        return account;
    }
}
