package ee.kaarel.familybudgetapplication.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ee.kaarel.familybudgetapplication.dto.transaction.UpdateTransactionRequest;
import ee.kaarel.familybudgetapplication.model.Account;
import ee.kaarel.familybudgetapplication.model.AccountType;
import ee.kaarel.familybudgetapplication.model.Category;
import ee.kaarel.familybudgetapplication.model.CategoryGroup;
import ee.kaarel.familybudgetapplication.model.NotificationType;
import ee.kaarel.familybudgetapplication.model.Role;
import ee.kaarel.familybudgetapplication.model.Transaction;
import ee.kaarel.familybudgetapplication.model.TransactionType;
import ee.kaarel.familybudgetapplication.model.User;
import ee.kaarel.familybudgetapplication.model.UserStatus;
import ee.kaarel.familybudgetapplication.repository.TransactionRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
    private RecurringPaymentService recurringPaymentService;

    @Mock
    private UserService userService;

    @InjectMocks
    private TransactionService transactionService;

    @Test
    void updateNotifiesSharedUsersWithUpdatedType() {
        User actor = createUser(1L, "John");
        Account account = createAccount(10L, actor, "Shared Account");
        Transaction transaction = createTransaction(100L, actor, account, TransactionType.EXPENSE, BigDecimal.valueOf(20));

        when(currentUserService.getCurrentUser()).thenReturn(actor);
        when(transactionRepository.findById(transaction.getId())).thenReturn(Optional.of(transaction));
        when(accountService.getCalculatedBalance(account)).thenReturn(BigDecimal.valueOf(100));
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(invocation -> invocation.getArgument(0));

        transactionService.update(
                transaction.getId(),
                new UpdateTransactionRequest(BigDecimal.valueOf(25), null, null, LocalDate.now(), "Updated comment")
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
        User actor = createUser(1L, "John");
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
        User actor = createUser(1L, "John");
        User recipient = createUser(2L, "Jane");
        Account fromAccount = createAccount(10L, actor, "Source");
        Account toAccount = createAccount(20L, recipient, "Target");
        fromAccount.setType(AccountType.MAIN);
        toAccount.setType(AccountType.MAIN);
        toAccount.setDefault(true);
        Transaction transaction = createTransferTransaction(100L, actor, fromAccount, toAccount, BigDecimal.valueOf(25));

        when(currentUserService.getCurrentUser()).thenReturn(actor);
        when(transactionRepository.findById(transaction.getId())).thenReturn(Optional.of(transaction));
        when(accountService.getAccount(fromAccount.getId())).thenReturn(fromAccount);
        when(accountService.getAccount(toAccount.getId())).thenReturn(toAccount);
        when(accountService.canTransactFromAccount(actor, fromAccount)).thenReturn(true);
        when(accountService.getCalculatedBalance(fromAccount)).thenReturn(BigDecimal.valueOf(100));
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(invocation -> invocation.getArgument(0));

        transactionService.updateTransfer(
                transaction.getId(),
                new UpdateTransactionRequest(
                        BigDecimal.valueOf(30),
                        fromAccount.getId(),
                        toAccount.getId(),
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
    }

    @Test
    void deleteTransferNotifiesBothAccounts() {
        User actor = createUser(1L, "John");
        User recipient = createUser(2L, "Jane");
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
        return account;
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
