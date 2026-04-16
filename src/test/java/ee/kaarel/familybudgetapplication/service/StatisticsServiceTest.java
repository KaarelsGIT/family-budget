package ee.kaarel.familybudgetapplication.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ee.kaarel.familybudgetapplication.appConfig.ApiException;
import ee.kaarel.familybudgetapplication.dto.statistics.YearlyStatisticsResponse;
import ee.kaarel.familybudgetapplication.dto.statistics.YearlyStatisticsRow;
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
import org.springframework.http.HttpStatus;

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

    @Test
    void childCannotRequestStatisticsForAnotherUser() {
        User currentUser = createUser(1L, "Child");
        User otherUser = createUser(2L, "Parent");

        currentUser.setRole(Role.CHILD);
        otherUser.setRole(Role.PARENT);

        when(currentUserService.getCurrentUser()).thenReturn(currentUser);

        ApiException exception = assertThrows(ApiException.class, () -> statisticsService.getYearly(2026, otherUser.getId(), null));

        Assertions.assertEquals(HttpStatus.FORBIDDEN, exception.getStatus());
    }

    @Test
    void savingsCalculatedFromTransfersToSavingsAccounts() {
        User currentUser = createUser(1L, "John");
        Account mainAccount = createAccount(10L, currentUser, "Main");
        Account savingsAccount = createAccount(20L, currentUser, "Savings");
        savingsAccount.setType(AccountType.SAVINGS);

        when(currentUserService.getCurrentUser()).thenReturn(currentUser);
        when(accountService.getVisibleAccounts(currentUser)).thenReturn(List.of(mainAccount, savingsAccount));

        // Rows:
        // 1. Income 1000
        // 2. Expense 200
        // 3. Transfer to Savings 300
        // 4. Transfer to Main (not savings) 100
        List<YearlyStatisticsRow> rows = List.of(
                new YearlyStatisticsRow(1, TransactionType.INCOME, null, null, null, 10L, "Main", AccountType.MAIN, null, null, 100L, "Salary", new BigDecimal("1000.00"), 1L),
                new YearlyStatisticsRow(1, TransactionType.EXPENSE, 10L, "Main", AccountType.MAIN, null, null, null, null, null, 200L, "Food", new BigDecimal("200.00"), 1L),
                new YearlyStatisticsRow(1, TransactionType.TRANSFER, 10L, "Main", AccountType.MAIN, 20L, "Savings", AccountType.SAVINGS, null, null, null, null, new BigDecimal("300.00"), 1L),
                new YearlyStatisticsRow(1, TransactionType.TRANSFER, 20L, "Savings", AccountType.SAVINGS, 10L, "Main", AccountType.MAIN, null, null, null, null, new BigDecimal("100.00"), 1L)
        );

        when(transactionRepository.findYearlyStatisticsRows(eq(2026), eq(1L), isNull(), any())).thenReturn(rows);

        YearlyStatisticsResponse response = statisticsService.getYearly(2026, null, null);

        assertEquals(0, new BigDecimal("1000.00").compareTo(response.totals().income()));
        assertEquals(0, new BigDecimal("200.00").compareTo(response.totals().expenses()));
        // Old logic would be 1000 - 200 = 800.
        // New logic: only transfers TO savings account = 300.
        assertEquals(0, new BigDecimal("300.00").compareTo(response.totals().savings()));
        assertEquals(0, new BigDecimal("30.00").compareTo(response.totals().savingsRateYear())); // (300/1000)*100
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
