package ee.kaarel.familybudgetapplication.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ee.kaarel.familybudgetapplication.dto.common.ListResponse;
import ee.kaarel.familybudgetapplication.dto.recurring.CreateRecurringTransactionRequest;
import ee.kaarel.familybudgetapplication.dto.recurring.RecurringTransactionResponse;
import ee.kaarel.familybudgetapplication.dto.transaction.CreateTransactionRequest;
import ee.kaarel.familybudgetapplication.dto.transaction.TransactionResponse;
import ee.kaarel.familybudgetapplication.model.Account;
import ee.kaarel.familybudgetapplication.model.Category;
import ee.kaarel.familybudgetapplication.model.CategoryGroup;
import ee.kaarel.familybudgetapplication.model.RecurringTransaction;
import ee.kaarel.familybudgetapplication.model.ReminderStatus;
import ee.kaarel.familybudgetapplication.model.Role;
import ee.kaarel.familybudgetapplication.model.TransactionReminder;
import ee.kaarel.familybudgetapplication.model.TransactionType;
import ee.kaarel.familybudgetapplication.model.User;
import ee.kaarel.familybudgetapplication.model.UserStatus;
import ee.kaarel.familybudgetapplication.repository.RecurringTransactionRepository;
import ee.kaarel.familybudgetapplication.repository.TransactionRepository;
import ee.kaarel.familybudgetapplication.repository.TransactionReminderRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RecurringTransactionServiceTest {

    private static final ZoneId TALLINN = ZoneId.of("Europe/Tallinn");

    @Mock
    private RecurringTransactionRepository recurringTransactionRepository;

    @Mock
    private TransactionReminderRepository transactionReminderRepository;

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private CurrentUserService currentUserService;

    @Mock
    private TransactionService transactionService;

    @Mock
    private NotificationService notificationService;

    @Mock
    private AccountService accountService;

    @Mock
    private CategoryService categoryService;

    @InjectMocks
    private RecurringTransactionService recurringTransactionService;

    @Test
    void generateDueRemindersCreatesReminderNotificationAndAdvancesNextDueDate() {
        LocalDate today = LocalDate.now(TALLINN);
        User owner = createUser(1L, "Kaarel", Role.PARENT);
        Category category = createCategory(10L, owner, "Gym", TransactionType.EXPENSE);
        Account account = createAccount(20L, owner, "LHV");
        RecurringTransaction recurringTransaction = createRecurringTransaction(
                30L,
                owner,
                category,
                account,
                BigDecimal.valueOf(19.99),
                "Gym membership",
                31,
                today
        );

        when(recurringTransactionRepository.findAllByActiveTrueAndNextDueDateLessThanEqual(today))
                .thenReturn(List.of(recurringTransaction));
        when(transactionReminderRepository.existsByRecurringTransactionAndDueDate(recurringTransaction, today)).thenReturn(false);
        when(transactionReminderRepository.save(any(TransactionReminder.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(recurringTransactionRepository.save(any(RecurringTransaction.class))).thenAnswer(invocation -> invocation.getArgument(0));

        recurringTransactionService.generateDueReminders();

        ArgumentCaptor<TransactionReminder> reminderCaptor = ArgumentCaptor.forClass(TransactionReminder.class);
        verify(transactionReminderRepository).save(reminderCaptor.capture());
        TransactionReminder savedReminder = reminderCaptor.getValue();

        assertThat(savedReminder.getRecurringTransaction()).isSameAs(recurringTransaction);
        assertThat(savedReminder.getUser()).isSameAs(owner);
        assertThat(savedReminder.getDueDate()).isEqualTo(today);
        assertThat(savedReminder.getStatus()).isEqualTo(ReminderStatus.PENDING);

        verify(notificationService).notifyRecurringTransactionDue(owner, recurringTransaction, savedReminder);

        LocalDate expectedNextDueDate = recurringTransactionService.calculateNextDueDate(
                today.plusDays(1),
                recurringTransaction.getDueDayOfMonth()
        );
        assertThat(recurringTransaction.getNextDueDate()).isEqualTo(expectedNextDueDate);
    }

    @Test
    void getPendingRemindersReturnsPendingReminderForCurrentUser() {
        User owner = createUser(1L, "Kaarel", Role.PARENT);
        Category category = createCategory(10L, owner, "Salary", TransactionType.INCOME);
        RecurringTransaction recurringTransaction = createRecurringTransaction(
                30L,
                owner,
                category,
                null,
                BigDecimal.valueOf(1865.92),
                "Monthly salary",
                2,
                LocalDate.now(TALLINN)
        );
        TransactionReminder reminder = createReminder(40L, recurringTransaction, owner, LocalDate.now(TALLINN), ReminderStatus.PENDING);

        when(currentUserService.getCurrentUser()).thenReturn(owner);
        when(transactionReminderRepository.findAllByUserAndStatusOrderByDueDateAsc(owner, ReminderStatus.PENDING))
                .thenReturn(List.of(reminder));

        ListResponse<?> response = recurringTransactionService.getPendingReminders();

        assertThat(response.total()).isEqualTo(1);
        assertThat(response.data()).hasSize(1);

        verify(transactionReminderRepository).findAllByUserAndStatusOrderByDueDateAsc(owner, ReminderStatus.PENDING);
    }

    @Test
    void createRecurringTransactionStoresCommentAndNextDueDate() {
        LocalDate today = LocalDate.now(TALLINN);
        User owner = createUser(1L, "Kaarel", Role.PARENT);
        Category category = createCategory(10L, owner, "Salary", TransactionType.INCOME);

        when(currentUserService.getCurrentUser()).thenReturn(owner);
        when(categoryService.getCategory(category.getId())).thenReturn(category);
        when(recurringTransactionRepository.save(any(RecurringTransaction.class))).thenAnswer(invocation -> invocation.getArgument(0));

        RecurringTransactionResponse response = recurringTransactionService.create(
                new CreateRecurringTransactionRequest("Monthly salary", category.getId(), BigDecimal.valueOf(1000), 15, true, null)
        );

        assertThat(response.name()).isEqualTo("Monthly salary");
        assertThat(response.amount()).isEqualTo(BigDecimal.valueOf(1000));
        assertThat(response.dueDay()).isEqualTo(15);
        assertThat(response.ownerId()).isEqualTo(owner.getId());
        assertThat(response.active()).isTrue();
        assertThat(response.currentMonthStatus()).isNotNull();
        assertThat(response.currentMonthStatus().year()).isEqualTo(today.getYear());
    }

    @Test
    void createRecurringTransactionAllowsMultipleRulesForSameCategory() {
        User owner = createUser(1L, "Kaarel", Role.PARENT);
        Category category = createCategory(10L, owner, "Internet", TransactionType.EXPENSE);

        when(currentUserService.getCurrentUser()).thenReturn(owner);
        when(categoryService.getCategory(category.getId())).thenReturn(category);
        when(recurringTransactionRepository.save(any(RecurringTransaction.class))).thenAnswer(invocation -> invocation.getArgument(0));

        RecurringTransactionResponse first = recurringTransactionService.create(
                new CreateRecurringTransactionRequest("Telia Internet", category.getId(), BigDecimal.valueOf(25), 10, true, null)
        );
        RecurringTransactionResponse second = recurringTransactionService.create(
                new CreateRecurringTransactionRequest("Telia Mobile", category.getId(), BigDecimal.valueOf(15), 12, true, null)
        );

        ArgumentCaptor<RecurringTransaction> captor = ArgumentCaptor.forClass(RecurringTransaction.class);
        verify(recurringTransactionRepository, times(2)).save(captor.capture());

        List<RecurringTransaction> savedTransactions = captor.getAllValues();
        assertThat(savedTransactions).hasSize(2);
        assertThat(savedTransactions.get(0)).isNotSameAs(savedTransactions.get(1));
        assertThat(savedTransactions.get(0).getComment()).isEqualTo("Telia Internet");
        assertThat(savedTransactions.get(1).getComment()).isEqualTo("Telia Mobile");
        assertThat(first.name()).isEqualTo("Telia Internet");
        assertThat(second.name()).isEqualTo("Telia Mobile");
    }

    @Test
    void completeReminderCreatesTransactionAndMarksCompleted() {
        LocalDate today = LocalDate.now(TALLINN);
        User owner = createUser(1L, "Kaarel", Role.PARENT);
        Category category = createCategory(10L, owner, "Salary", TransactionType.INCOME);
        Account account = createAccount(20L, owner, "Main");
        RecurringTransaction recurringTransaction = createRecurringTransaction(
                30L,
                owner,
                category,
                account,
                BigDecimal.valueOf(1865.92),
                "Monthly salary",
                2,
                today
        );
        TransactionReminder reminder = createReminder(40L, recurringTransaction, owner, today, ReminderStatus.PENDING);
        TransactionResponse transactionResponse = new TransactionResponse(
                99L,
                BigDecimal.valueOf(1865.92),
                null,
                TransactionType.INCOME,
                null,
                null,
                account.getId(),
                account.getName(),
                category.getId(),
                category.getName(),
                owner.getId(),
                owner.getUsername(),
                today,
                OffsetDateTime.now(),
                "Monthly salary"
        );

        when(currentUserService.getCurrentUser()).thenReturn(owner);
        when(transactionReminderRepository.findById(reminder.getId())).thenReturn(Optional.of(reminder));
        when(transactionService.createForUser(eq(owner), any(CreateTransactionRequest.class))).thenReturn(transactionResponse);
        when(transactionReminderRepository.save(any(TransactionReminder.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TransactionResponse result = recurringTransactionService.completeReminder(reminder.getId());

        ArgumentCaptor<CreateTransactionRequest> requestCaptor = ArgumentCaptor.forClass(CreateTransactionRequest.class);
        verify(transactionService).createForUser(eq(owner), requestCaptor.capture());

        CreateTransactionRequest request = requestCaptor.getValue();
        assertThat(request.type()).isEqualTo(TransactionType.INCOME);
        assertThat(request.categoryId()).isEqualTo(category.getId());
        assertThat(request.toAccountId()).isEqualTo(account.getId());
        assertThat(request.fromAccountId()).isNull();
        assertThat(request.transactionDate()).isEqualTo(today);
        assertThat(request.comment()).isEqualTo("Monthly salary");
        assertThat(result).isSameAs(transactionResponse);
        assertThat(reminder.getStatus()).isEqualTo(ReminderStatus.COMPLETED);
    }

    @Test
    void completeReminderFallsBackToDefaultAccountWhenRecurringAccountIsMissing() {
        LocalDate today = LocalDate.now(TALLINN);
        User owner = createUser(1L, "Kaarel", Role.PARENT);
        Category category = createCategory(10L, owner, "Utility", TransactionType.EXPENSE);
        Account defaultAccount = createAccount(20L, owner, "Main");
        RecurringTransaction recurringTransaction = createRecurringTransaction(
                30L,
                owner,
                category,
                null,
                BigDecimal.valueOf(42.50),
                "Electricity",
                2,
                today
        );
        TransactionReminder reminder = createReminder(40L, recurringTransaction, owner, today, ReminderStatus.PENDING);
        TransactionResponse transactionResponse = new TransactionResponse(
                99L,
                BigDecimal.valueOf(42.50),
                null,
                TransactionType.EXPENSE,
                defaultAccount.getId(),
                defaultAccount.getName(),
                null,
                null,
                category.getId(),
                category.getName(),
                owner.getId(),
                owner.getUsername(),
                today,
                OffsetDateTime.now(),
                "Electricity"
        );

        when(currentUserService.getCurrentUser()).thenReturn(owner);
        when(transactionReminderRepository.findById(reminder.getId())).thenReturn(Optional.of(reminder));
        when(accountService.getDefaultMainAccount(owner)).thenReturn(defaultAccount);
        when(transactionService.createForUser(eq(owner), any(CreateTransactionRequest.class))).thenReturn(transactionResponse);
        when(transactionReminderRepository.save(any(TransactionReminder.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TransactionResponse result = recurringTransactionService.completeReminder(reminder.getId());

        ArgumentCaptor<CreateTransactionRequest> requestCaptor = ArgumentCaptor.forClass(CreateTransactionRequest.class);
        verify(transactionService).createForUser(eq(owner), requestCaptor.capture());

        CreateTransactionRequest request = requestCaptor.getValue();
        assertThat(request.fromAccountId()).isEqualTo(defaultAccount.getId());
        assertThat(request.toAccountId()).isNull();
        assertThat(result).isSameAs(transactionResponse);
        assertThat(reminder.getStatus()).isEqualTo(ReminderStatus.COMPLETED);
    }

    @Test
    void skipReminderMarksReminderAsSkipped() {
        LocalDate today = LocalDate.now(TALLINN);
        User owner = createUser(1L, "Kaarel", Role.PARENT);
        Category category = createCategory(10L, owner, "Utility", TransactionType.EXPENSE);
        RecurringTransaction recurringTransaction = createRecurringTransaction(
                30L,
                owner,
                category,
                createAccount(20L, owner, "Main"),
                BigDecimal.valueOf(42.50),
                "Electricity",
                2,
                today
        );
        TransactionReminder reminder = createReminder(40L, recurringTransaction, owner, today, ReminderStatus.PENDING);

        when(currentUserService.getCurrentUser()).thenReturn(owner);
        when(transactionReminderRepository.findById(reminder.getId())).thenReturn(Optional.of(reminder));
        when(transactionReminderRepository.save(any(TransactionReminder.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = recurringTransactionService.skipReminder(reminder.getId());

        assertThat(reminder.getStatus()).isEqualTo(ReminderStatus.SKIPPED);
        assertThat(response.status()).isEqualTo(ReminderStatus.SKIPPED);
    }

    private User createUser(Long id, String username, Role role) {
        User user = new User();
        user.setId(id);
        user.setUsername(username);
        user.setPassword("password");
        user.setRole(role);
        user.setStatus(UserStatus.ACTIVE);
        user.setPreferredLanguage("et");
        return user;
    }

    private Category createCategory(Long id, User owner, String name, TransactionType type) {
        Category category = new Category();
        category.setId(id);
        category.setUserId(owner.getId());
        category.setName(name);
        category.setType(type);
        category.setGroup(CategoryGroup.FAMILY);
        return category;
    }

    private Account createAccount(Long id, User owner, String name) {
        Account account = new Account();
        account.setId(id);
        account.setOwner(owner);
        account.setName(name);
        return account;
    }

    private RecurringTransaction createRecurringTransaction(
            Long id,
            User owner,
            Category category,
            Account account,
            BigDecimal amount,
            String comment,
            Integer dueDayOfMonth,
            LocalDate nextDueDate
    ) {
        RecurringTransaction recurringTransaction = new RecurringTransaction();
        recurringTransaction.setId(id);
        recurringTransaction.setUser(owner);
        recurringTransaction.setCategory(category);
        recurringTransaction.setAccount(account);
        recurringTransaction.setAmount(amount);
        recurringTransaction.setComment(comment);
        recurringTransaction.setDueDayOfMonth(dueDayOfMonth);
        recurringTransaction.setNextDueDate(nextDueDate);
        recurringTransaction.setActive(true);
        return recurringTransaction;
    }

    private TransactionReminder createReminder(
            Long id,
            RecurringTransaction recurringTransaction,
            User owner,
            LocalDate dueDate,
            ReminderStatus status
    ) {
        TransactionReminder reminder = new TransactionReminder();
        reminder.setId(id);
        reminder.setRecurringTransaction(recurringTransaction);
        reminder.setUser(owner);
        reminder.setDueDate(dueDate);
        reminder.setStatus(status);
        return reminder;
    }
}
