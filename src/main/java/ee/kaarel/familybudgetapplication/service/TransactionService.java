package ee.kaarel.familybudgetapplication.service;

import ee.kaarel.familybudgetapplication.appConfig.ApiException;
import ee.kaarel.familybudgetapplication.dto.common.ListResponse;
import ee.kaarel.familybudgetapplication.dto.transaction.CreateTransactionRequest;
import ee.kaarel.familybudgetapplication.dto.transaction.TransactionResponse;
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
    private final UserService userService;

    public TransactionService(
            TransactionRepository transactionRepository,
            CurrentUserService currentUserService,
            AccountService accountService,
            CategoryService categoryService,
            NotificationService notificationService,
            RecurringPaymentService recurringPaymentService,
            UserService userService
    ) {
        this.transactionRepository = transactionRepository;
        this.currentUserService = currentUserService;
        this.accountService = accountService;
        this.categoryService = categoryService;
        this.notificationService = notificationService;
        this.recurringPaymentService = recurringPaymentService;
        this.userService = userService;
    }

    @Transactional(readOnly = true)
    public ListResponse<TransactionResponse> getTransactions(
            Pageable pageable,
            Long userId,
            Long categoryId,
            LocalDate from,
            LocalDate to
    ) {
        User currentUser = currentUserService.getCurrentUser();
        Pageable sorted = PageableUtils.withDefaultSort(pageable, Sort.by(Sort.Order.desc("transactionDate"), Sort.Order.desc("createdAt")));
        Page<Transaction> page = transactionRepository.findAll(
                visibleTransactions(currentUser, userId, categoryId, from, to),
                sorted
        );
        return new ListResponse<>(page.map(this::toResponse).getContent(), page.getTotalElements());
    }

    @Transactional
    public TransactionResponse create(CreateTransactionRequest request) {
        User currentUser = currentUserService.getCurrentUser();

        if (request.categoryId() != null) {
            Category category = categoryService.getCategory(request.categoryId());
            categoryService.ensureVisible(currentUser, category);

            return switch (category.getType()) {
                case INCOME -> createIncome(currentUser, request, category);
                case EXPENSE -> createExpense(currentUser, request, category);
                case TRANSFER -> throw new ApiException(HttpStatus.BAD_REQUEST, "Transfer categories are not supported for categorized transactions");
            };
        }

        return createTransfer(currentUser, request);
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
        if (request.fromAccountId().equals(request.toAccountId())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Transfer accounts must differ");
        }
        Account fromAccount = accountService.getAccount(request.fromAccountId());
        Account toAccount = accountService.getAccount(request.toAccountId());
        validateTransfer(currentUser, fromAccount, toAccount);
        ensureBalanceWillNotGoNegative(fromAccount, request.amount());

        Transaction transaction = new Transaction();
        transaction.setAmount(request.amount());
        transaction.setType(TransactionType.TRANSFER);
        transaction.setFromAccount(fromAccount);
        transaction.setToAccount(toAccount);
        transaction.setCreatedBy(currentUser);
        transaction.setTransactionDate(resolveTransactionDate(request));
        transaction.setCreatedAt(OffsetDateTime.now());
        transaction.setComment(request.comment());
        Transaction saved = transactionRepository.save(transaction);

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
        if (!accountService.canManageAccount(currentUser, account)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "You cannot modify transactions for this account");
        }
    }

    private void validateTransfer(User currentUser, Account fromAccount, Account toAccount) {
        validateAccountModification(currentUser, fromAccount);
        if (!fromAccount.getOwner().getId().equals(currentUser.getId())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "You can only transfer from your own accounts");
        }

        boolean sameOwner = fromAccount.getOwner().getId().equals(toAccount.getOwner().getId());
        if (sameOwner) {
            return;
        }

        userService.ensureSelectableUser(currentUser, toAccount.getOwner());
        if (toAccount.getType() != AccountType.MAIN || !toAccount.isDefault()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Transfers to other users must go to their default MAIN account");
        }
    }

    private void ensureBalanceWillNotGoNegative(Account account, BigDecimal amount) {
        if (accountService.getCalculatedBalance(account).compareTo(amount) < 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Kontol ei ole piisavalt raha");
        }
    }

    private Specification<Transaction> visibleTransactions(
            User currentUser,
            Long userId,
            Long categoryId,
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

            var predicates = cb.conjunction();

            if (currentUser.getRole() == Role.CHILD) {
                predicates = cb.and(predicates, cb.or(
                        cb.equal(root.get("createdBy").get("id"), currentUser.getId()),
                        cb.equal(root.get("fromAccount").get("owner").get("id"), currentUser.getId()),
                        cb.equal(root.get("toAccount").get("owner").get("id"), currentUser.getId())
                ));
            }

            if (userId != null) {
                predicates = cb.and(predicates, cb.or(
                        cb.equal(root.get("fromAccount").get("owner").get("id"), userId),
                        cb.equal(root.get("toAccount").get("owner").get("id"), userId),
                        cb.equal(root.get("createdBy").get("id"), userId)
                ));
            }
            if (categoryId != null) {
                predicates = cb.and(predicates, cb.or(
                        cb.equal(root.get("category").get("id"), categoryId),
                        cb.equal(root.get("category").get("parentCategory").get("id"), categoryId)
                ));
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
