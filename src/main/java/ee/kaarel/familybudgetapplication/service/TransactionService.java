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
import java.time.OffsetDateTime;
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
            Long subcategoryId,
            OffsetDateTime from,
            OffsetDateTime to
    ) {
        User currentUser = currentUserService.getCurrentUser();
        Pageable sorted = PageableUtils.withDefaultSort(pageable, Sort.by(Sort.Order.desc("createdAt")));
        Page<Transaction> page = transactionRepository.findAll(
                visibleTransactions(currentUser, userId, categoryId, subcategoryId, from, to),
                sorted
        );
        return new ListResponse<>(page.map(this::toResponse).getContent(), page.getTotalElements());
    }

    @Transactional
    public TransactionResponse create(CreateTransactionRequest request) {
        User currentUser = currentUserService.getCurrentUser();
        return switch (request.type()) {
            case INCOME -> createIncome(currentUser, request);
            case EXPENSE -> createExpense(currentUser, request);
            case TRANSFER -> createTransfer(currentUser, request);
        };
    }

    private TransactionResponse createIncome(User currentUser, CreateTransactionRequest request) {
        if (request.toAccountId() == null || request.categoryId() == null || request.fromAccountId() != null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Income requires toAccount and category only");
        }
        Account toAccount = accountService.getAccount(request.toAccountId());
        validateAccountModification(currentUser, toAccount);
        Category category = categoryService.getCategory(request.categoryId());
        categoryService.ensureVisible(currentUser, category);
        validateCategoryType(category, TransactionType.INCOME);

        Transaction transaction = new Transaction();
        transaction.setAmount(request.amount());
        transaction.setType(TransactionType.INCOME);
        transaction.setToAccount(toAccount);
        transaction.setCategory(category);
        transaction.setCreatedBy(currentUser);
        transaction.setCreatedAt(OffsetDateTime.now());
        transaction.setComment(request.comment());
        Transaction saved = transactionRepository.save(transaction);
        recurringPaymentService.markRecurringAsPaidByTransaction(category, toAccount.getOwner(), saved.getCreatedAt().getYear(), saved.getCreatedAt().getMonthValue());
        return toResponse(saved);
    }

    private TransactionResponse createExpense(User currentUser, CreateTransactionRequest request) {
        if (request.fromAccountId() == null || request.categoryId() == null || request.toAccountId() != null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Expense requires fromAccount and category only");
        }
        Account fromAccount = accountService.getAccount(request.fromAccountId());
        validateAccountModification(currentUser, fromAccount);
        Category category = categoryService.getCategory(request.categoryId());
        categoryService.ensureVisible(currentUser, category);
        validateCategoryType(category, TransactionType.EXPENSE);

        Transaction transaction = new Transaction();
        transaction.setAmount(request.amount());
        transaction.setType(TransactionType.EXPENSE);
        transaction.setFromAccount(fromAccount);
        transaction.setCategory(category);
        transaction.setCreatedBy(currentUser);
        transaction.setCreatedAt(OffsetDateTime.now());
        transaction.setComment(request.comment());
        Transaction saved = transactionRepository.save(transaction);
        recurringPaymentService.markRecurringAsPaidByTransaction(category, fromAccount.getOwner(), saved.getCreatedAt().getYear(), saved.getCreatedAt().getMonthValue());
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

        Transaction transaction = new Transaction();
        transaction.setAmount(request.amount());
        transaction.setType(TransactionType.TRANSFER);
        transaction.setFromAccount(fromAccount);
        transaction.setToAccount(toAccount);
        transaction.setCreatedBy(currentUser);
        transaction.setCreatedAt(OffsetDateTime.now());
        transaction.setComment(request.comment());
        Transaction saved = transactionRepository.save(transaction);

        if (!fromAccount.getOwner().getId().equals(toAccount.getOwner().getId())) {
            notificationService.createNotification(
                    toAccount.getOwner(),
                    NotificationType.MONEY_RECEIVED,
                    "You received " + request.amount() + " from " + fromAccount.getOwner().getUsername() + " to account " + toAccount.getName()
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
        accountService.ensureCanAccessAccount(currentUser, fromAccount);
        accountService.ensureCanAccessAccount(currentUser, toAccount);
        validateAccountModification(currentUser, fromAccount);

        boolean sameOwner = fromAccount.getOwner().getId().equals(toAccount.getOwner().getId());
        if (sameOwner) {
            return;
        }

        if (fromAccount.getType() != AccountType.MAIN || toAccount.getType() != AccountType.MAIN) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Transfers between users must be MAIN to MAIN");
        }
        if (currentUser.getRole() == Role.CHILD && !fromAccount.getOwner().getId().equals(currentUser.getId())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Children can only transfer from their own MAIN account");
        }
    }

    private Specification<Transaction> visibleTransactions(
            User currentUser,
            Long userId,
            Long categoryId,
            Long subcategoryId,
            OffsetDateTime from,
            OffsetDateTime to
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
            } else if (currentUser.getRole() == Role.PARENT) {
                predicates = cb.and(predicates, cb.or(
                        cb.equal(root.get("fromAccount").get("owner").get("id"), currentUser.getId()),
                        cb.equal(root.get("toAccount").get("owner").get("id"), currentUser.getId()),
                        cb.equal(root.get("fromAccount").get("owner").get("role"), Role.CHILD),
                        cb.equal(root.get("toAccount").get("owner").get("role"), Role.CHILD)
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
            if (subcategoryId != null) {
                predicates = cb.and(predicates, cb.equal(root.get("category").get("id"), subcategoryId));
            }
            if (from != null) {
                predicates = cb.and(predicates, cb.greaterThanOrEqualTo(root.get("createdAt"), from));
            }
            if (to != null) {
                predicates = cb.and(predicates, cb.lessThanOrEqualTo(root.get("createdAt"), to));
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
                transaction.getCreatedAt(),
                transaction.getComment()
        );
    }
}
