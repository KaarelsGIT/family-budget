package ee.kaarel.familybudgetapplication.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ee.kaarel.familybudgetapplication.appConfig.ApiException;
import ee.kaarel.familybudgetapplication.dto.transaction.UpdateTransactionRequest;
import ee.kaarel.familybudgetapplication.model.Account;
import ee.kaarel.familybudgetapplication.model.AccountType;
import ee.kaarel.familybudgetapplication.model.Category;
import ee.kaarel.familybudgetapplication.model.CategoryGroup;
import ee.kaarel.familybudgetapplication.model.ReminderStatus;
import ee.kaarel.familybudgetapplication.model.NotificationType;
import ee.kaarel.familybudgetapplication.model.RecurringTransaction;
import ee.kaarel.familybudgetapplication.model.Role;
import ee.kaarel.familybudgetapplication.model.TransactionReminder;
import ee.kaarel.familybudgetapplication.model.Transaction;
import ee.kaarel.familybudgetapplication.model.TransactionType;
import ee.kaarel.familybudgetapplication.model.User;
import ee.kaarel.familybudgetapplication.model.UserStatus;
import ee.kaarel.familybudgetapplication.repository.TransactionRepository;
import ee.kaarel.familybudgetapplication.repository.TransactionReminderRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
class TransactionServiceTest {

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private CurrentUserService currentUserService;

    @Mock
    private AccountService accountService;

    @Mock
    private CategoryService categoryService;

    @Mock
    private NotificationService notificationService;

    @Mock
    private UserService userService;

    @Mock
    private TransactionReminderRepository transactionReminderRepository;

    @InjectMocks
    private TransactionService transactionService;

    @Test
    void updateNotifiesSharedUsersWithUpdatedType() {
        User actor = createUser(1L, "John", 100L);
        Account account = createAccount(10L, actor, "Shared Account");
        Transaction transaction = createTransaction(100L, actor, account, TransactionType.EXPENSE, BigDecimal.valueOf(20));

        when(currentUserService.getCurrentUser()).thenReturn(actor);
        when(transactionRepository.findById(transaction.getId())).thenReturn(Optional.of(transaction));
        when(accountService.getCalculatedBalance(account)).thenReturn(BigDecimal.valueOf(100));
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(invocation -> invocation.getArgument(0));

        transactionService.update(
                transaction.getId(),
                new UpdateTransactionRequest(BigDecimal.valueOf(25), null, null, null, LocalDate.now(), "Updated comment")
        );

        verify(notificationService).notifySharedAccountTransactionUsers(
                eq(account),
                eq(actor),
                eq(TransactionType.EXPENSE),
                eq(BigDecimal.valueOf(25)),
                eq(transaction.getId()),
                eq(NotificationType.TRANSACTION_UPDATED)
        );
    }

    @Test
    void deleteNotifiesSharedUsersWithDeletedType() {
        User actor = createUser(1L, "John", 100L);
        Account account = createAccount(10L, actor, "Shared Account");
        Transaction transaction = createTransaction(100L, actor, account, TransactionType.INCOME, BigDecimal.valueOf(20));

        when(currentUserService.getCurrentUser()).thenReturn(actor);
        when(transactionRepository.findById(transaction.getId())).thenReturn(Optional.of(transaction));

        transactionService.delete(transaction.getId());

        verify(notificationService).notifySharedAccountTransactionUsers(
                eq(account),
                eq(actor),
                eq(TransactionType.INCOME),
                eq(BigDecimal.valueOf(20)),
                eq(transaction.getId()),
                eq(NotificationType.TRANSACTION_DELETED)
        );
    }

    @Test
    void updateTransferAllowsSharedEditorAndNotifiesBothAccounts() {
        User actor = createUser(1L, "John", 100L);
        User recipient = createUser(2L, "Jane", 100L);
        Account fromAccount = createAccount(10L, actor, "Source");
        Account toAccount = createAccount(20L, recipient, "Target");
        fromAccount.setType(AccountType.MAIN);
        toAccount.setType(AccountType.MAIN);
        toAccount.setDefault(true);
        Transaction transaction = createTransferTransaction(100L, actor, fromAccount, toAccount, BigDecimal.valueOf(25));

        when(currentUserService.getCurrentUser()).thenReturn(actor);
        when(transactionRepository.findById(transaction.getId())).thenReturn(Optional.of(transaction));
        when(accountService.getAccount(fromAccount.getId())).thenReturn(fromAccount);
        when(accountService.canTransactFromAccount(actor, fromAccount)).thenReturn(true);
        when(userService.findUser(recipient.getId())).thenReturn(recipient);
        when(accountService.getTransferTargetMainAccount(recipient)).thenReturn(toAccount);
        when(accountService.getCalculatedBalance(fromAccount)).thenReturn(BigDecimal.valueOf(100));
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(invocation -> invocation.getArgument(0));

        transactionService.updateTransfer(
                transaction.getId(),
                new UpdateTransactionRequest(
                        BigDecimal.valueOf(30),
                        fromAccount.getId(),
                        null,
                        recipient.getId(),
                        LocalDate.now(),
                        "Updated transfer"
                )
        );

        verify(notificationService).notifySharedAccountTransactionUsers(
                eq(fromAccount),
                eq(actor),
                eq(TransactionType.TRANSFER),
                eq(BigDecimal.valueOf(30)),
                eq(transaction.getId()),
                eq(NotificationType.TRANSACTION_UPDATED)
        );
        verify(notificationService).notifySharedAccountTransactionUsers(
                eq(toAccount),
                eq(actor),
                eq(TransactionType.TRANSFER),
                eq(BigDecimal.valueOf(30)),
                eq(transaction.getId()),
                eq(NotificationType.TRANSACTION_UPDATED)
        );
        verify(notificationService).notifyMoneyReceived(
                eq(recipient),
                eq(actor),
                eq(BigDecimal.valueOf(30)),
                eq("Source")
        );
    }

    @Test
    void createTransferUsesTargetUser() {
        User actor = createUser(1L, "John", 100L);
        User recipient = createUser(2L, "Jane", 100L);
        Account fromAccount = createAccount(10L, actor, "Source");
        Account toAccount = createAccount(20L, recipient, "Target");
        fromAccount.setType(AccountType.MAIN);
        toAccount.setType(AccountType.MAIN);
        toAccount.setDefault(true);

        when(currentUserService.getCurrentUser()).thenReturn(actor);
        when(accountService.getAccount(fromAccount.getId())).thenReturn(fromAccount);
        when(accountService.canTransactFromAccount(actor, fromAccount)).thenReturn(true);
        when(userService.findUser(recipient.getId())).thenReturn(recipient);
        when(accountService.getTransferTargetMainAccount(recipient)).thenReturn(toAccount);
        when(accountService.getCalculatedBalance(fromAccount)).thenReturn(BigDecimal.valueOf(100));
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(invocation -> invocation.getArgument(0));

        transactionService.create(new ee.kaarel.familybudgetapplication.dto.transaction.CreateTransactionRequest(
                BigDecimal.valueOf(25),
                TransactionType.TRANSFER,
                fromAccount.getId(),
                null,
                recipient.getId(),
                null,
                LocalDate.now(),
                "Transfer to account",
                null
        ));

        verify(notificationService).notifySharedAccountTransactionUsers(
                eq(toAccount),
                eq(actor),
                eq(TransactionType.TRANSFER),
                eq(BigDecimal.valueOf(25)),
                any(),
                eq(NotificationType.TRANSACTION_CREATED)
        );
        verify(notificationService).notifyMoneyReceived(
                eq(recipient),
                eq(actor),
                eq(BigDecimal.valueOf(25)),
                eq("Source")
        );
    }

    @Test
    void createTransferRejectsUnauthorizedTargetAccount() {
        User actor = createUser(1L, "John", 100L);
        User otherChild = createUser(3L, "Kid", 200L);
        otherChild.setRole(Role.CHILD);
        Account fromAccount = createAccount(10L, actor, "Source");
        fromAccount.setType(AccountType.MAIN);

        when(currentUserService.getCurrentUser()).thenReturn(actor);
        when(accountService.getAccount(fromAccount.getId())).thenReturn(fromAccount);
        when(userService.findUser(otherChild.getId())).thenReturn(otherChild);
        doThrow(new ApiException(HttpStatus.FORBIDDEN, "You cannot transfer money to this user"))
                .when(userService).ensureTransferTargetAllowed(actor, otherChild);

        ApiException exception = assertThrows(ApiException.class, () -> transactionService.create(
                new ee.kaarel.familybudgetapplication.dto.transaction.CreateTransactionRequest(
                        BigDecimal.valueOf(25),
                        TransactionType.TRANSFER,
                        fromAccount.getId(),
                        null,
                        otherChild.getId(),
                        null,
                        LocalDate.now(),
                        "Transfer to account",
                        null
                )
        ));

        assertEquals(HttpStatus.FORBIDDEN, exception.getStatus());
    }

    @Test
    void createCompletesMatchingReminderForCurrentMonth() {
        User actor = createUser(1L, "John", 100L);
        Account account = createAccount(10L, actor, "Shared Account");
        Category category = createCategory(20L, actor, "Utilities", TransactionType.EXPENSE);
        LocalDate today = LocalDate.now();

        RecurringTransaction recurringTransaction = new RecurringTransaction();
        recurringTransaction.setId(30L);
        recurringTransaction.setUser(actor);
        recurringTransaction.setCategory(category);

        TransactionReminder reminder = new TransactionReminder();
        reminder.setId(40L);
        reminder.setRecurringTransaction(recurringTransaction);
        reminder.setUser(actor);
        reminder.setDueDate(today.withDayOfMonth(1));
        reminder.setStatus(ReminderStatus.PENDING);

        when(currentUserService.getCurrentUser()).thenReturn(actor);
        when(categoryService.getCategory(category.getId())).thenReturn(category);
        when(accountService.getAccount(account.getId())).thenReturn(account);
        when(accountService.canTransactFromAccount(actor, account)).thenReturn(true);
        when(accountService.getCalculatedBalance(account)).thenReturn(BigDecimal.valueOf(100));
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(transactionReminderRepository.findAllByUserAndStatusOrderByDueDateAsc(actor, ReminderStatus.PENDING))
                .thenReturn(List.of(reminder));
        when(transactionReminderRepository.save(any(TransactionReminder.class))).thenAnswer(invocation -> invocation.getArgument(0));

        transactionService.create(new ee.kaarel.familybudgetapplication.dto.transaction.CreateTransactionRequest(
                BigDecimal.valueOf(25),
                TransactionType.EXPENSE,
                account.getId(),
                null,
                null,
                category.getId(),
                today,
                "Utilities payment",
                null
        ));

        ArgumentCaptor<TransactionReminder> reminderCaptor = ArgumentCaptor.forClass(TransactionReminder.class);
        verify(transactionReminderRepository).save(reminderCaptor.capture());
        assertEquals(ReminderStatus.COMPLETED, reminderCaptor.getValue().getStatus());
    }

    @Test
    void createForUserCompletesExplicitReminder() {
        User actor = createUser(1L, "John", 100L);
        Account account = createAccount(10L, actor, "Shared Account");
        Category category = createCategory(20L, actor, "Utilities", TransactionType.EXPENSE);
        LocalDate today = LocalDate.now();
        RecurringTransaction recurringTransaction = new RecurringTransaction();
        recurringTransaction.setId(30L);
        recurringTransaction.setUser(actor);
        recurringTransaction.setCategory(category);

        TransactionReminder reminder = new TransactionReminder();
        reminder.setId(40L);
        reminder.setRecurringTransaction(recurringTransaction);
        reminder.setUser(actor);
        reminder.setDueDate(today.withDayOfMonth(1));
        reminder.setStatus(ReminderStatus.PENDING);

        when(currentUserService.getCurrentUser()).thenReturn(actor);
        when(categoryService.getCategory(category.getId())).thenReturn(category);
        when(accountService.getAccount(account.getId())).thenReturn(account);
        when(accountService.canTransactFromAccount(actor, account)).thenReturn(true);
        when(accountService.getCalculatedBalance(account)).thenReturn(BigDecimal.valueOf(100));
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(transactionReminderRepository.findById(reminder.getId())).thenReturn(Optional.of(reminder));
        when(transactionReminderRepository.save(any(TransactionReminder.class))).thenAnswer(invocation -> invocation.getArgument(0));

        transactionService.createForUser(actor, new ee.kaarel.familybudgetapplication.dto.transaction.CreateTransactionRequest(
                BigDecimal.valueOf(25),
                TransactionType.EXPENSE,
                account.getId(),
                null,
                null,
                category.getId(),
                today,
                "Utilities payment",
                reminder.getId()
        ));

        ArgumentCaptor<TransactionReminder> reminderCaptor = ArgumentCaptor.forClass(TransactionReminder.class);
        verify(transactionReminderRepository).save(reminderCaptor.capture());
        assertEquals(ReminderStatus.COMPLETED, reminderCaptor.getValue().getStatus());
    }

    @Test
    void deleteTransferNotifiesBothAccounts() {
        User actor = createUser(1L, "John", 100L);
        User recipient = createUser(2L, "Jane", 100L);
        Account fromAccount = createAccount(10L, actor, "Source");
        Account toAccount = createAccount(20L, recipient, "Target");
        Transaction transaction = createTransferTransaction(100L, actor, fromAccount, toAccount, BigDecimal.valueOf(25));

        when(currentUserService.getCurrentUser()).thenReturn(actor);
        when(transactionRepository.findById(transaction.getId())).thenReturn(Optional.of(transaction));

        transactionService.deleteTransfer(transaction.getId());

        verify(notificationService).notifySharedAccountTransactionUsers(
                eq(fromAccount),
                eq(actor),
                eq(TransactionType.TRANSFER),
                eq(BigDecimal.valueOf(25)),
                eq(transaction.getId()),
                eq(NotificationType.TRANSACTION_DELETED)
        );
        verify(notificationService).notifySharedAccountTransactionUsers(
                eq(toAccount),
                eq(actor),
                eq(TransactionType.TRANSFER),
                eq(BigDecimal.valueOf(25)),
                eq(transaction.getId()),
                eq(NotificationType.TRANSACTION_DELETED)
        );
    }

    private User createUser(Long id, String username, Long familyId) {
        User user = new User();
        user.setId(id);
        user.setUsername(username);
        user.setPassword("password");
        user.setRole(Role.PARENT);
        user.setFamilyId(familyId);
        user.setStatus(UserStatus.ACTIVE);
        user.setPreferredLanguage("en");
        return user;
    }

    private Account createAccount(Long id, User owner, String name) {
        Account account = new Account();
        account.setId(id);
        account.setOwner(owner);
        account.setName(name);
        return account;
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

    private Transaction createTransaction(Long id, User createdBy, Account account, TransactionType type, BigDecimal amount) {
        Transaction transaction = new Transaction();
        transaction.setId(id);
        transaction.setCreatedBy(createdBy);
        transaction.setType(type);
        transaction.setAmount(amount);
        transaction.setCreatedAt(OffsetDateTime.now());
        transaction.setTransactionDate(LocalDate.now());
        if (type == TransactionType.INCOME) {
            transaction.setToAccount(account);
        } else {
            transaction.setFromAccount(account);
        }
        return transaction;
    }

    private Transaction createTransferTransaction(Long id, User createdBy, Account fromAccount, Account toAccount, BigDecimal amount) {
        Transaction transaction = new Transaction();
        transaction.setId(id);
        transaction.setCreatedBy(createdBy);
        transaction.setType(TransactionType.TRANSFER);
        transaction.setAmount(amount);
        transaction.setCreatedAt(OffsetDateTime.now());
        transaction.setTransactionDate(LocalDate.now());
        transaction.setFromAccount(fromAccount);
        transaction.setToAccount(toAccount);
        transaction.setTransferId("transfer-" + id);
        return transaction;
    }
}
