package ee.kaarel.familybudgetapplication.service;

import ee.kaarel.familybudgetapplication.appConfig.ApiException;
import ee.kaarel.familybudgetapplication.dto.common.ListResponse;
import ee.kaarel.familybudgetapplication.dto.transaction.CreateTransactionRequest;
import ee.kaarel.familybudgetapplication.dto.transaction.TransactionResponse;
import ee.kaarel.familybudgetapplication.dto.transaction.UpdateTransactionRequest;
import ee.kaarel.familybudgetapplication.model.Account;
import ee.kaarel.familybudgetapplication.model.AccountType;
import ee.kaarel.familybudgetapplication.model.Category;
import ee.kaarel.familybudgetapplication.model.NotificationType;
import ee.kaarel.familybudgetapplication.model.Role;
import ee.kaarel.familybudgetapplication.model.Transaction;
import ee.kaarel.familybudgetapplication.model.TransactionType;
import ee.kaarel.familybudgetapplication.model.User;
import ee.kaarel.familybudgetapplication.repository.TransactionRepository;
import jakarta.persistence.criteria.JoinType;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TransactionService {

    private final TransactionRepository transactionRepository;
    private final CurrentUserService currentUserService;
    private final AccountService accountService;
    private final CategoryService categoryService;
    private final NotificationService notificationService;
    private final RecurringPaymentService recurringPaymentService;

    public TransactionService(
            TransactionRepository transactionRepository,
            CurrentUserService currentUserService,
            AccountService accountService,
            CategoryService categoryService,
            NotificationService notificationService,
            RecurringPaymentService recurringPaymentService
    ) {
        this.transactionRepository = transactionRepository;
        this.currentUserService = currentUserService;
        this.accountService = accountService;
        this.categoryService = categoryService;
        this.notificationService = notificationService;
        this.recurringPaymentService = recurringPaymentService;
    }

    @Transactional(readOnly = true)
    public ListResponse<TransactionResponse> getTransactions(
            Pageable pageable,
            Long userId,
            Long categoryId,
            Long mainCategoryId,
            Long subCategoryId,
            TransactionType type,
            LocalDate from,
            LocalDate to
    ) {
        User currentUser = currentUserService.getCurrentUser();
        Long effectiveUserId = userId != null ? userId : currentUser.getId();
        Pageable sorted = PageableUtils.withDefaultSort(pageable, Sort.by(Sort.Order.desc("transactionDate"), Sort.Order.desc("createdAt")));
        Page<Transaction> page = transactionRepository.findAll(
                visibleTransactions(currentUser, effectiveUserId, categoryId, mainCategoryId, subCategoryId, type, from, to),
                sorted
        );
        return new ListResponse<>(page.map(this::toResponse).getContent(), page.getTotalElements());
    }

    @Transactional
    public TransactionResponse create(CreateTransactionRequest request) {
        User currentUser = currentUserService.getCurrentUser();
        return createInternal(currentUser, request);
    }

    @Transactional
    public TransactionResponse createForUser(User currentUser, CreateTransactionRequest request) {
        return createInternal(currentUser, request);
    }

    private TransactionResponse createInternal(User currentUser, CreateTransactionRequest request) {
        if (request.type() == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Transaction type is required");
        }

        return switch (request.type()) {
            case INCOME -> {
                if (request.categoryId() == null) {
                    throw new ApiException(HttpStatus.BAD_REQUEST, "Income requires a category");
                }
                Category category = categoryService.getCategory(request.categoryId());
                categoryService.ensureVisible(currentUser, category);
                yield createIncome(currentUser, request, category);
            }
            case EXPENSE -> {
                if (request.categoryId() == null) {
                    throw new ApiException(HttpStatus.BAD_REQUEST, "Expense requires a category");
                }
                Category category = categoryService.getCategory(request.categoryId());
                categoryService.ensureVisible(currentUser, category);
                yield createExpense(currentUser, request, category);
            }
            case TRANSFER -> createTransfer(currentUser, request);
        };
    }

    @Transactional
    public TransactionResponse update(Long id, UpdateTransactionRequest request) {
        User currentUser = currentUserService.getCurrentUser();
        Transaction transaction = getTransaction(id);
        if (transaction.getType() == TransactionType.TRANSFER) {
            return updateTransfer(currentUser, transaction, request);
        }

        ensureEditableByCreator(currentUser, transaction);

        if (request.amount() == null || request.amount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Transaction amount must be greater than zero");
        }

        if (request.transactionDate() == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Transaction date is required");
        }

        if (transaction.getType() == TransactionType.EXPENSE) {
            Account account = transaction.getFromAccount();
            ensureExpenseUpdateWillNotGoNegative(transaction, account, request.amount());
        }

        transaction.setAmount(request.amount());
        transaction.setTransactionDate(request.transactionDate());
        transaction.setComment(request.comment());

        Transaction saved = transactionRepository.save(transaction);
        notifySharedAccountTransactionIfNeeded(getTransactionAccount(saved), saved.getType(), saved.getAmount(), saved.getId(), NotificationType.TRANSACTION_UPDATED);
        return toResponse(saved);
    }

    @Transactional
    public void delete(Long id) {
        User currentUser = currentUserService.getCurrentUser();
        Transaction transaction = getTransaction(id);
        if (transaction.getType() == TransactionType.TRANSFER) {
            deleteTransfer(currentUser, transaction);
            return;
        }

        ensureEditableByCreator(currentUser, transaction);
        notifySharedAccountTransactionIfNeeded(getTransactionAccount(transaction), transaction.getType(), transaction.getAmount(), transaction.getId(), NotificationType.TRANSACTION_DELETED);
        transactionRepository.delete(transaction);
    }

    @Transactional
    public TransactionResponse updateTransfer(Long id, UpdateTransactionRequest request) {
        User currentUser = currentUserService.getCurrentUser();
        Transaction transaction = getTransaction(id);
        return updateTransfer(currentUser, transaction, request);
    }

    @Transactional
    public void deleteTransfer(Long id) {
        User currentUser = currentUserService.getCurrentUser();
        Transaction transaction = getTransaction(id);
        deleteTransfer(currentUser, transaction);
    }

    private TransactionResponse createIncome(User currentUser, CreateTransactionRequest request, Category category) {
        if (request.toAccountId() == null || request.categoryId() == null || request.fromAccountId() != null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Income requires toAccount and category only");
        }
        Account toAccount = accountService.getAccount(request.toAccountId());
        validateAccountModification(currentUser, toAccount);
        validateCategoryType(category, TransactionType.INCOME);

        Transaction transaction = new Transaction();
        transaction.setAmount(request.amount());
        transaction.setType(TransactionType.INCOME);
        transaction.setToAccount(toAccount);
        transaction.setCategory(category);
        transaction.setCreatedBy(currentUser);
        transaction.setTransactionDate(resolveTransactionDate(request));
        transaction.setCreatedAt(OffsetDateTime.now());
        transaction.setComment(request.comment());
        Transaction saved = transactionRepository.save(transaction);
        notifySharedAccountTransactionIfNeeded(toAccount, TransactionType.INCOME, saved.getAmount(), saved.getId(), NotificationType.TRANSACTION_CREATED);
        recurringPaymentService.markRecurringAsPaidByTransaction(
                category,
                toAccount.getOwner(),
                saved.getTransactionDate().getYear(),
                saved.getTransactionDate().getMonthValue()
        );
        return toResponse(saved);
    }

    private TransactionResponse createExpense(User currentUser, CreateTransactionRequest request, Category category) {
        if (request.fromAccountId() == null || request.categoryId() == null || request.toAccountId() != null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Expense requires fromAccount and category only");
        }
        Account fromAccount = accountService.getAccount(request.fromAccountId());
        validateAccountModification(currentUser, fromAccount);
        validateCategoryType(category, TransactionType.EXPENSE);
        ensureBalanceWillNotGoNegative(fromAccount, request.amount());

        Transaction transaction = new Transaction();
        transaction.setAmount(request.amount());
        transaction.setType(TransactionType.EXPENSE);
        transaction.setFromAccount(fromAccount);
        transaction.setCategory(category);
        transaction.setCreatedBy(currentUser);
        transaction.setTransactionDate(resolveTransactionDate(request));
        transaction.setCreatedAt(OffsetDateTime.now());
        transaction.setComment(request.comment());
        Transaction saved = transactionRepository.save(transaction);
        notifySharedAccountTransactionIfNeeded(fromAccount, TransactionType.EXPENSE, saved.getAmount(), saved.getId(), NotificationType.TRANSACTION_CREATED);
        recurringPaymentService.markRecurringAsPaidByTransaction(
                category,
                fromAccount.getOwner(),
                saved.getTransactionDate().getYear(),
                saved.getTransactionDate().getMonthValue()
        );
        return toResponse(saved);
    }

    private TransactionResponse createTransfer(User currentUser, CreateTransactionRequest request) {
        if (request.fromAccountId() == null || request.toAccountId() == null || request.categoryId() != null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Transfer requires fromAccount and toAccount, and category must be null");
        }
        Account fromAccount = accountService.getAccount(request.fromAccountId());
        Account toAccount = accountService.getAccount(request.toAccountId());
        validateTransfer(currentUser, fromAccount, toAccount);
        if (fromAccount.getId().equals(toAccount.getId())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Transfer accounts must differ");
        }
        ensureBalanceWillNotGoNegative(fromAccount, request.amount());

        Transaction transaction = new Transaction();
        transaction.setAmount(request.amount());
        transaction.setTransferId(UUID.randomUUID().toString());
        transaction.setType(TransactionType.TRANSFER);
        transaction.setFromAccount(fromAccount);
        transaction.setToAccount(toAccount);
        transaction.setCreatedBy(currentUser);
        transaction.setTransactionDate(resolveTransactionDate(request));
        transaction.setCreatedAt(OffsetDateTime.now());
        transaction.setComment(request.comment());
        Transaction saved = transactionRepository.save(transaction);
        notifySharedAccountTransactionIfNeeded(fromAccount, TransactionType.TRANSFER, saved.getAmount(), saved.getId(), NotificationType.TRANSACTION_CREATED);
        notifySharedAccountTransactionIfNeeded(toAccount, TransactionType.TRANSFER, saved.getAmount(), saved.getId(), NotificationType.TRANSACTION_CREATED);

        if (!fromAccount.getOwner().getId().equals(toAccount.getOwner().getId())) {
            notificationService.notifyMoneyReceived(
                    toAccount.getOwner(),
                    fromAccount.getOwner(),
                    request.amount(),
                    toAccount.getName()
            );
        }
        return toResponse(saved);
    }

    private void validateCategoryType(Category category, TransactionType type) {
        if (category.getType() != type) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Category type does not match transaction type");
        }
    }

    private void validateAccountModification(User currentUser, Account account) {
        accountService.ensureCanAccessAccount(currentUser, account);
        if (!accountService.canTransactFromAccount(currentUser, account)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "You cannot modify transactions for this account");
        }
    }

    private void validateTransfer(User currentUser, Account fromAccount, Account toAccount) {
        validateAccountModification(currentUser, fromAccount);
        if (!accountService.canTransferToAccount(currentUser, toAccount)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "You cannot transfer money to this account");
        }
    }

    private void ensureBalanceWillNotGoNegative(Account account, BigDecimal amount) {
        if (accountService.getCalculatedBalance(account).compareTo(amount) < 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Kontol ei ole piisavalt raha");
        }
    }

    private void ensureTransferWillNotGoNegative(Transaction transaction, Account newFromAccount, BigDecimal newAmount) {
        BigDecimal availableAfterReversal = accountService.getCalculatedBalance(newFromAccount);

        if (transaction.getFromAccount() != null && transaction.getFromAccount().getId().equals(newFromAccount.getId())) {
            availableAfterReversal = availableAfterReversal.add(transaction.getAmount());
        } else if (transaction.getToAccount() != null && transaction.getToAccount().getId().equals(newFromAccount.getId())) {
            availableAfterReversal = availableAfterReversal.subtract(transaction.getAmount());
        }

        if (newAmount.compareTo(availableAfterReversal) > 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Kontol ei ole piisavalt raha");
        }
    }

    private void ensureExpenseUpdateWillNotGoNegative(Transaction transaction, Account account, BigDecimal newAmount) {
        BigDecimal currentBalance = accountService.getCalculatedBalance(account);
        BigDecimal availableAfterRestoringCurrentAmount = currentBalance.add(transaction.getAmount());
        if (newAmount.compareTo(availableAfterRestoringCurrentAmount) > 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Kontol ei ole piisavalt raha");
        }
    }

    private TransactionResponse updateTransfer(User currentUser, Transaction transaction, UpdateTransactionRequest request) {
        if (request.fromAccountId() == null || request.toAccountId() == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Transfer requires fromAccount and toAccount");
        }
        if (request.amount() == null || request.amount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Transaction amount must be greater than zero");
        }
        if (request.transactionDate() == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Transaction date is required");
        }

        Account newFromAccount = accountService.getAccount(request.fromAccountId());
        Account newToAccount = accountService.getAccount(request.toAccountId());
        ensureTransferEditableByUser(currentUser, transaction, newFromAccount, newToAccount);
        validateTransfer(currentUser, newFromAccount, newToAccount);
        if (newFromAccount.getId().equals(newToAccount.getId())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Transfer accounts must differ");
        }
        ensureTransferWillNotGoNegative(transaction, newFromAccount, request.amount());

        transaction.setAmount(request.amount());
        transaction.setFromAccount(newFromAccount);
        transaction.setToAccount(newToAccount);
        transaction.setTransactionDate(request.transactionDate());
        transaction.setComment(request.comment());

        Transaction saved = transactionRepository.save(transaction);
        notifySharedAccountTransactionIfNeeded(newFromAccount, TransactionType.TRANSFER, saved.getAmount(), saved.getId(), NotificationType.TRANSACTION_UPDATED);
        notifySharedAccountTransactionIfNeeded(newToAccount, TransactionType.TRANSFER, saved.getAmount(), saved.getId(), NotificationType.TRANSACTION_UPDATED);
        return toResponse(saved);
    }

    private void deleteTransfer(User currentUser, Transaction transaction) {
        Account fromAccount = transaction.getFromAccount();
        Account toAccount = transaction.getToAccount();
        ensureTransferEditableByUser(currentUser, transaction, fromAccount, toAccount);

        notifySharedAccountTransactionIfNeeded(fromAccount, TransactionType.TRANSFER, transaction.getAmount(), transaction.getId(), NotificationType.TRANSACTION_DELETED);
        notifySharedAccountTransactionIfNeeded(toAccount, TransactionType.TRANSFER, transaction.getAmount(), transaction.getId(), NotificationType.TRANSACTION_DELETED);
        transactionRepository.delete(transaction);
    }

    private void ensureEditableByCreator(User currentUser, Transaction transaction) {
        if (!transaction.getCreatedBy().getId().equals(currentUser.getId())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "You can only modify your own transactions");
        }
    }

    private void ensureTransferEditableByUser(User currentUser, Transaction transaction, Account newFromAccount, Account newToAccount) {
        if (currentUser.getRole() == Role.ADMIN) {
            return;
        }

        if (transaction.getCreatedBy().getId().equals(currentUser.getId())) {
            return;
        }

        accountService.ensureCanAccessAccount(currentUser, newFromAccount);

        if (!accountService.canTransactFromAccount(currentUser, newFromAccount)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "You cannot modify transactions for this account");
        }
    }

    private void notifySharedAccountTransactionIfNeeded(Account account, TransactionType type, BigDecimal amount, Long transactionId, NotificationType notificationType) {
        notificationService.notifySharedAccountTransactionUsers(
                account,
                currentUserService.getCurrentUser(),
                type,
                amount,
                transactionId,
                notificationType
        );
    }

    private Account getTransactionAccount(Transaction transaction) {
        return transaction.getType() == TransactionType.INCOME ? transaction.getToAccount() : transaction.getFromAccount();
    }

    @Transactional(readOnly = true)
    public Transaction getTransaction(Long id) {
        return transactionRepository.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Transaction not found"));
    }

    private Specification<Transaction> visibleTransactions(
            User currentUser,
            Long userId,
            Long categoryId,
            Long mainCategoryId,
            Long subCategoryId,
            TransactionType type,
            LocalDate from,
            LocalDate to
    ) {
        return (root, query, cb) -> {
            if (query != null && !Long.class.equals(query.getResultType()) && !long.class.equals(query.getResultType())) {
                root.fetch("fromAccount", JoinType.LEFT);
                root.fetch("toAccount", JoinType.LEFT);
                root.fetch("category", JoinType.LEFT);
                root.fetch("createdBy", JoinType.LEFT);
                query.distinct(true);
            }

            var fromAccountJoin = root.join("fromAccount", JoinType.LEFT);
            var toAccountJoin = root.join("toAccount", JoinType.LEFT);
            var categoryJoin = root.join("category", JoinType.LEFT);
            var createdByJoin = root.join("createdBy", JoinType.LEFT);

            var predicates = cb.conjunction();

            if (type != null) {
                predicates = cb.and(predicates, cb.equal(root.get("type"), type));
            }

            if (currentUser.getRole() == Role.CHILD) {
                predicates = cb.and(predicates, cb.or(
                        cb.equal(createdByJoin.get("id"), currentUser.getId()),
                        cb.equal(fromAccountJoin.get("owner").get("id"), currentUser.getId()),
                        cb.equal(toAccountJoin.get("owner").get("id"), currentUser.getId())
                ));
            }

            if (userId != null) {
                predicates = cb.and(predicates, cb.or(
                        cb.equal(fromAccountJoin.get("owner").get("id"), userId),
                        cb.equal(toAccountJoin.get("owner").get("id"), userId),
                        cb.equal(createdByJoin.get("id"), userId)
                ));
            }

            if (categoryId != null) {
                predicates = cb.and(predicates, cb.or(
                        cb.equal(categoryJoin.get("id"), categoryId),
                        cb.equal(categoryJoin.get("parentCategory").get("id"), categoryId)
                ));
            }
            if (mainCategoryId != null) {
                predicates = cb.and(predicates, cb.or(
                        cb.equal(categoryJoin.get("id"), mainCategoryId),
                        cb.equal(categoryJoin.get("parentCategory").get("id"), mainCategoryId)
                ));
            }
            if (subCategoryId != null) {
                predicates = cb.and(predicates, cb.equal(categoryJoin.get("id"), subCategoryId));
            }
            if (from != null) {
                OffsetDateTime fromDateTime = from.atStartOfDay().atOffset(ZoneOffset.UTC);
                predicates = cb.and(predicates, cb.or(
                        cb.and(
                                cb.isNotNull(root.get("transactionDate")),
                                cb.greaterThanOrEqualTo(root.get("transactionDate"), from)
                        ),
                        cb.and(
                                cb.isNull(root.get("transactionDate")),
                                cb.greaterThanOrEqualTo(root.get("createdAt"), fromDateTime)
                        )
                ));
            }
            if (to != null) {
                OffsetDateTime toDateTime = to.plusDays(1).atStartOfDay().atOffset(ZoneOffset.UTC).minusNanos(1);
                predicates = cb.and(predicates, cb.or(
                        cb.and(
                                cb.isNotNull(root.get("transactionDate")),
                                cb.lessThanOrEqualTo(root.get("transactionDate"), to)
                        ),
                        cb.and(
                                cb.isNull(root.get("transactionDate")),
                                cb.lessThanOrEqualTo(root.get("createdAt"), toDateTime)
                        )
                ));
            }
            return predicates;
        };
    }

    public TransactionResponse toResponse(Transaction transaction) {
        return new TransactionResponse(
                transaction.getId(),
                transaction.getAmount(),
                transaction.getTransferId(),
                transaction.getType(),
                transaction.getFromAccount() == null ? null : transaction.getFromAccount().getId(),
                transaction.getFromAccount() == null ? null : transaction.getFromAccount().getName(),
                transaction.getToAccount() == null ? null : transaction.getToAccount().getId(),
                transaction.getToAccount() == null ? null : transaction.getToAccount().getName(),
                transaction.getCategory() == null ? null : transaction.getCategory().getId(),
                transaction.getCategory() == null ? null : transaction.getCategory().getName(),
                transaction.getCreatedBy().getId(),
                transaction.getCreatedBy().getUsername(),
                transaction.getTransactionDate() == null ? transaction.getCreatedAt().toLocalDate() : transaction.getTransactionDate(),
                transaction.getCreatedAt(),
                transaction.getComment()
        );
    }

    private LocalDate resolveTransactionDate(CreateTransactionRequest request) {
        return request.transactionDate() != null
                ? request.transactionDate()
                : OffsetDateTime.now().toLocalDate();
    }
}
