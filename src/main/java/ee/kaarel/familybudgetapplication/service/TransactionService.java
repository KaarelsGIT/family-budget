package ee.kaarel.familybudgetapplication.service;


import ee.kaarel.familybudgetapplication.appConfig.ApiException;
import ee.kaarel.familybudgetapplication.dto.transaction.CreateTransactionRequest;
import ee.kaarel.familybudgetapplication.dto.transaction.TransactionListResponse;
import ee.kaarel.familybudgetapplication.dto.transaction.TransactionResponse;
import ee.kaarel.familybudgetapplication.dto.transaction.UpdateTransactionRequest;
import ee.kaarel.familybudgetapplication.model.*;
import ee.kaarel.familybudgetapplication.repository.TransactionReminderRepository;
import ee.kaarel.familybudgetapplication.repository.TransactionRepository;
import jakarta.persistence.criteria.JoinType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class TransactionService {

    private final TransactionRepository transactionRepository;
    private final CurrentUserService currentUserService;
    private final AccountService accountService;
    private final CategoryService categoryService;
    private final NotificationService notificationService;
    private final UserService userService;
    private final TransactionReminderRepository transactionReminderRepository;

    public TransactionService(
            TransactionRepository transactionRepository,
            CurrentUserService currentUserService,
            AccountService accountService,
            CategoryService categoryService,
            NotificationService notificationService,
            UserService userService,
            TransactionReminderRepository transactionReminderRepository
    ) {
        this.transactionRepository = transactionRepository;
        this.currentUserService = currentUserService;
        this.accountService = accountService;
        this.categoryService = categoryService;
        this.notificationService = notificationService;
        this.userService = userService;
        this.transactionReminderRepository = transactionReminderRepository;
    }

    @Transactional(readOnly = true)
    public TransactionListResponse getTransactions(
            Pageable pageable, String sort, Long userId, Long categoryId,
            Long mainCategoryId, Long subCategoryId, TransactionType type,
            LocalDate from, LocalDate to
    ) {
        User currentUser = currentUserService.getCurrentUser();
        Long effectiveUserId = userId != null ? userId : currentUser.getId();

        Sort parsedSort = parseSort(sort, pageable.getSort());
        Pageable sorted = PageableUtils.withDefaultSort(pageable, parsedSort);

        // 1. Teeme kindlaks, et Specification ei oleks liiga range
        Specification<Transaction> spec = visibleTransactions(currentUser, effectiveUserId, categoryId, mainCategoryId, subCategoryId, type, from, to);

        // 2. Võtame lehekülje andmed
        Page<Transaction> page = transactionRepository.findAll(spec, sorted);

        // 3. Arvutame summad käsitsi tsükliga, et vältida Stream-i vigu ja tagada null-safety
        List<Transaction> allMatching = transactionRepository.findAll(spec);

        BigDecimal incomeSum = BigDecimal.ZERO;
        BigDecimal expenseSum = BigDecimal.ZERO;
        BigDecimal transferSum = BigDecimal.ZERO;

        for (Transaction t : allMatching) {
            BigDecimal amt = t.getAmount() != null ? t.getAmount() : BigDecimal.ZERO;
            if (t.getType() == TransactionType.INCOME) {
                incomeSum = incomeSum.add(amt);
            } else if (t.getType() == TransactionType.EXPENSE) {
                expenseSum = expenseSum.add(amt);
            } else if (t.getType() == TransactionType.TRANSFER) {
                transferSum = transferSum.add(amt);
            }
        }

        return new TransactionListResponse(
                page.map(this::toResponse).getContent(),
                page.getTotalElements(),
                incomeSum,
                expenseSum,
                transferSum
        );
    }

    private BigDecimal calculateSumByType(List<Transaction> transactions, TransactionType type) {
        return transactions.stream()
                .filter(t -> t.getType() == type)
                .map(Transaction::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    @Transactional
    public TransactionResponse create(CreateTransactionRequest request) {
        return createInternal(currentUserService.getCurrentUser(), request);
    }

    /**
     * Vajalik RecurringTransactionService jaoks, et luua tehinguid meeldetuletustest.
     */
    @Transactional
    public TransactionResponse createForUser(User user, CreateTransactionRequest request) {
        return createInternal(user, request);
    }

    private TransactionResponse createInternal(User user, CreateTransactionRequest request) {
        if (request.type() == null) throw new ApiException(HttpStatus.BAD_REQUEST, "Transaction type is required");

        return switch (request.type()) {
            case INCOME -> createIncome(user, request);
            case EXPENSE -> createExpense(user, request);
            case TRANSFER -> createTransfer(user, request);
        };
    }

    private TransactionResponse createIncome(User user, CreateTransactionRequest request) {
        rejectDuplicateSubmission(user, request);
        Category category = categoryService.getCategory(request.categoryId());
        categoryService.ensureVisible(user, category);
        validateCategoryType(category, TransactionType.INCOME);

        Account toAccount = accountService.getAccountForUpdate(request.toAccountId());
        validateAccountModification(user, toAccount);

        Transaction t = buildBaseTransaction(user, request, TransactionType.INCOME);
        t.setToAccount(toAccount);
        t.setCategory(category);

        Transaction saved = transactionRepository.save(t);
        completeMatchingReminderIfPresent(saved, request.reminderId());
        notifySharedAccountTransactionIfNeeded(toAccount, TransactionType.INCOME, saved.getAmount(), saved.getId(), NotificationType.TRANSACTION_CREATED);
        return toResponse(saved);
    }

    private TransactionResponse createExpense(User user, CreateTransactionRequest request) {
        rejectDuplicateSubmission(user, request);
        Category category = categoryService.getCategory(request.categoryId());
        categoryService.ensureVisible(user, category);
        validateCategoryType(category, TransactionType.EXPENSE);

        Account fromAccount = accountService.getAccountForUpdate(request.fromAccountId());
        validateAccountModification(user, fromAccount);
        ensureBalanceWillNotGoNegative(fromAccount, request.amount());

        Transaction t = buildBaseTransaction(user, request, TransactionType.EXPENSE);
        t.setFromAccount(fromAccount);
        t.setCategory(category);

        Transaction saved = transactionRepository.save(t);
        completeMatchingReminderIfPresent(saved, request.reminderId());
        notifySharedAccountTransactionIfNeeded(fromAccount, TransactionType.EXPENSE, saved.getAmount(), saved.getId(), NotificationType.TRANSACTION_CREATED);
        return toResponse(saved);
    }

    private TransactionResponse createTransfer(User user, CreateTransactionRequest request) {
        rejectDuplicateSubmission(user, request);
        Account fromAccount = accountService.getAccountForUpdate(request.fromAccountId());
        Account toAccount = resolveTransferTargetAccount(user, request.toAccountId(), request.targetUserId());
        toAccount = accountService.getAccountForUpdate(toAccount.getId());

        validateAccountModification(user, fromAccount);
        validateTransferTarget(user, fromAccount, toAccount);
        ensureBalanceWillNotGoNegative(fromAccount, request.amount());

        Transaction t = buildBaseTransaction(user, request, TransactionType.TRANSFER);
        t.setTransferId(UUID.randomUUID().toString());
        t.setFromAccount(fromAccount);
        t.setToAccount(toAccount);

        Transaction saved = transactionRepository.save(t);
        notifySharedAccountTransactionIfNeeded(fromAccount, TransactionType.TRANSFER, saved.getAmount(), saved.getId(), NotificationType.TRANSACTION_CREATED);
        notifySharedAccountTransactionIfNeeded(toAccount, TransactionType.TRANSFER, saved.getAmount(), saved.getId(), NotificationType.TRANSACTION_CREATED);

        if (request.targetUserId() != null) {
            notificationService.notifyMoneyReceived(userService.findUser(request.targetUserId()), fromAccount.getOwner(), request.amount(), fromAccount.getName(), saved.getId());
        }
        return toResponse(saved);
    }

    @Transactional
    public TransactionResponse update(Long id, UpdateTransactionRequest request) {
        User currentUser = currentUserService.getCurrentUser();
        Transaction transaction = getTransaction(id);

        if (transaction.getType() == TransactionType.TRANSFER) {
            return updateTransfer(currentUser, transaction, request);
        }

        ensureEditableByCreator(currentUser, transaction);
        validateBasicUpdateRequest(request);

        if (transaction.getType() == TransactionType.EXPENSE) {
            ensureExpenseUpdateWillNotGoNegative(transaction, transaction.getFromAccount(), request.amount());
        }

        transaction.setAmount(request.amount());
        transaction.setTransactionDate(request.transactionDate());
        transaction.setComment(request.comment());

        Transaction saved = transactionRepository.save(transaction);
        notifySharedAccountTransactionIfNeeded(getTransactionAccount(saved), saved.getType(), saved.getAmount(), saved.getId(), NotificationType.TRANSACTION_UPDATED);
        return toResponse(saved);
    }

    private TransactionResponse updateTransfer(User user, Transaction t, UpdateTransactionRequest req) {
        validateBasicUpdateRequest(req);
        Account newFrom = accountService.getAccount(req.fromAccountId());
        Account newTo = resolveTransferTargetAccount(user, req.toAccountId(), req.targetUserId());

        ensureTransferEditableByUser(user, t, newFrom, newTo);
        ensureTransferWillNotGoNegative(t, newFrom, req.amount());

        User prevTargetUser = t.getToAccount().getOwner();

        t.setAmount(req.amount());
        t.setFromAccount(newFrom);
        t.setToAccount(newTo);
        t.setTransactionDate(req.transactionDate());
        t.setComment(req.comment());

        Transaction saved = transactionRepository.save(t);

        if (!prevTargetUser.getId().equals(newTo.getOwner().getId())) {
            notificationService.deleteTransferNotifications(prevTargetUser, saved.getId());
        }
        notificationService.notifyMoneyReceived(newTo.getOwner(), newFrom.getOwner(), saved.getAmount(), newFrom.getName(), saved.getId());

        return toResponse(saved);
    }

    @Transactional
    public void delete(Long id) {
        User user = currentUserService.getCurrentUser();
        Transaction t = getTransaction(id);

        if (t.getType() == TransactionType.TRANSFER) {
            deleteTransfer(user, t);
            return;
        }

        ensureEditableByCreator(user, t);
        notifySharedAccountTransactionIfNeeded(getTransactionAccount(t), t.getType(), t.getAmount(), t.getId(), NotificationType.TRANSACTION_DELETED);
        transactionRepository.delete(t);
    }

    private void deleteTransfer(User user, Transaction t) {
        ensureTransferEditableByUser(user, t, t.getFromAccount(), t.getToAccount());
        notificationService.deleteTransferNotifications(t.getToAccount().getOwner(), t.getId());
        transactionRepository.delete(t);
    }

    // --- Private Helperid ---

    private Transaction buildBaseTransaction(User user, CreateTransactionRequest req, TransactionType type) {
        Transaction t = new Transaction();
        t.setAmount(req.amount());
        t.setType(type);
        t.setCreatedBy(user);
        t.setTransactionDate(req.transactionDate() != null ? req.transactionDate() : LocalDate.now());
        t.setCreatedAt(OffsetDateTime.now());
        t.setComment(req.comment());
        return t;
    }

    private void validateBasicUpdateRequest(UpdateTransactionRequest req) {
        if (req.amount() == null || req.amount().compareTo(BigDecimal.ZERO) <= 0)
            throw new ApiException(HttpStatus.BAD_REQUEST, "Amount must be greater than zero");
        if (req.transactionDate() == null)
            throw new ApiException(HttpStatus.BAD_REQUEST, "Date is required");
    }

    private void validateTransferTarget(User user, Account from, Account to) {
        accountService.ensureCanAccessAccount(user, to);
        if (!accountService.canTransferToAccount(user, to)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Cannot transfer to this account");
        }
        if (from.getId().equals(to.getId())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Accounts must differ");
        }
        // Perekonnapõhine kontroll
        Long familyId = user.getFamilyId();
        if (familyId == null || !familyId.equals(from.getOwner().getFamilyId()) || !familyId.equals(to.getOwner().getFamilyId())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Only transfers within your family are allowed");
        }
    }

    private Account resolveTransferTargetAccount(User user, Long toAccountId, Long targetUserId) {
        if (toAccountId != null) {
            Account acc = accountService.getAccount(toAccountId);
            accountService.ensureCanAccessAccount(user, acc);
            return acc;
        }
        if (targetUserId != null) {
            User targetUser = userService.findUser(targetUserId);
            userService.ensureTransferTargetAllowed(user, targetUser);
            return accountService.getTransferTargetMainAccount(targetUser);
        }
        throw new ApiException(HttpStatus.BAD_REQUEST, "Transfer target required");
    }

    private void ensureBalanceWillNotGoNegative(Account acc, BigDecimal amount) {
        if (accountService.getCalculatedBalance(acc).compareTo(amount) < 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Kontol ei ole piisavalt raha");
        }
    }

    private void ensureExpenseUpdateWillNotGoNegative(Transaction t, Account acc, BigDecimal newAmount) {
        BigDecimal available = accountService.getCalculatedBalance(acc).add(t.getAmount());
        if (newAmount.compareTo(available) > 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Kontol ei ole piisavalt raha");
        }
    }

    private void ensureTransferWillNotGoNegative(Transaction t, Account newFrom, BigDecimal newAmount) {
        BigDecimal available = accountService.getCalculatedBalance(newFrom);
        if (t.getFromAccount() != null && t.getFromAccount().getId().equals(newFrom.getId())) {
            available = available.add(t.getAmount());
        }
        if (newAmount.compareTo(available) > 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Kontol ei ole piisavalt raha");
        }
    }

    private void ensureEditableByCreator(User user, Transaction t) {
        if (!t.getCreatedBy().getId().equals(user.getId())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "You can only modify your own transactions");
        }
    }

    private void ensureTransferEditableByUser(User user, Transaction t, Account newFrom, Account newTo) {
        if (t.getCreatedBy().getId().equals(user.getId())) return;
        accountService.ensureCanAccessAccount(user, newFrom);
        if (!accountService.canTransactFromAccount(user, newFrom)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "You cannot modify transactions for this account");
        }
    }

    private void completeMatchingReminderIfPresent(Transaction t, Long reminderId) {
        if (t.getCategory() == null || t.getTransactionDate() == null || t.getCreatedBy() == null) return;

        if (reminderId != null) {
            transactionReminderRepository.findById(reminderId)
                    .filter(r -> r.getStatus() == ReminderStatus.PENDING)
                    .ifPresent(r -> completeReminderLink(t, r));
        } else {
            YearMonth month = YearMonth.from(t.getTransactionDate());
            transactionReminderRepository.findAllByUserAndStatusOrderByDueDateAsc(t.getCreatedBy(), ReminderStatus.PENDING).stream()
                    .filter(r -> r.getRecurringTransaction().getCategory().getId().equals(t.getCategory().getId()))
                    .filter(r -> YearMonth.from(r.getDueDate()).equals(month))
                    .findFirst()
                    .ifPresent(r -> completeReminderLink(t, r));
        }
    }

    private void completeReminderLink(Transaction t, TransactionReminder r) {
        r.setStatus(ReminderStatus.COMPLETED);
        r.setTransaction(t);
        transactionReminderRepository.save(r);
    }

    private void validateCategoryType(Category c, TransactionType type) {
        if (c.getType() != type) throw new ApiException(HttpStatus.BAD_REQUEST, "Category type mismatch");
    }

    private void validateAccountModification(User user, Account acc) {
        accountService.ensureCanAccessAccount(user, acc);
        if (!accountService.canTransactFromAccount(user, acc)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Cannot modify transactions for this account");
        }
    }

    private Account getTransactionAccount(Transaction t) {
        return t.getType() == TransactionType.INCOME ? t.getToAccount() : t.getFromAccount();
    }

    private void notifySharedAccountTransactionIfNeeded(Account acc, TransactionType type, BigDecimal amt, Long id, NotificationType nType) {
        notificationService.notifySharedAccountTransactionUsers(acc, currentUserService.getCurrentUser(), type, amt, id, nType);
    }

    private void rejectDuplicateSubmission(User user, CreateTransactionRequest request) {
        OffsetDateTime cutoff = OffsetDateTime.now().minusSeconds(5).truncatedTo(ChronoUnit.SECONDS);
        List<Transaction> recentTransactions = transactionRepository.findAllByCreatedByAndCreatedAtAfterOrderByCreatedAtDesc(user, cutoff);

        for (Transaction recent : recentTransactions) {
            if (isSameTransaction(recent, request)) {
                throw new RuntimeException("Duplicate transaction submission detected");
            }
        }
    }

    private boolean isSameTransaction(Transaction transaction, CreateTransactionRequest request) {
        if (transaction.getType() != request.type()) return false;
        if (!safeEquals(transaction.getAmount(), request.amount())) return false;
        if (!safeEquals(transaction.getTransactionDate(), request.transactionDate())) return false;
        if (!safeEquals(transaction.getComment(), request.comment())) return false;
        if (!safeEquals(getAccountId(transaction.getFromAccount()), request.fromAccountId())) return false;
        if (!safeEquals(getAccountId(transaction.getToAccount()), request.toAccountId())) return false;
        return safeEquals(getCategoryId(transaction.getCategory()), request.categoryId());
    }

    private Long getAccountId(Account account) {
        return account != null ? account.getId() : null;
    }

    private Long getCategoryId(Category category) {
        return category != null ? category.getId() : null;
    }

    private boolean safeEquals(Object left, Object right) {
        return left == null ? right == null : left.equals(right);
    }

    @Transactional(readOnly = true)
    public Transaction getTransaction(Long id) {
        return transactionRepository.findById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Transaction not found"));
    }

    public TransactionResponse toResponse(Transaction t) {
        return new TransactionResponse(
                t.getId(), t.getAmount(), t.getTransferId(), t.getType(),
                t.getFromAccount() != null ? t.getFromAccount().getId() : null,
                t.getFromAccount() != null ? t.getFromAccount().getName() : null,
                t.getToAccount() != null ? t.getToAccount().getId() : null,
                t.getToAccount() != null ? t.getToAccount().getName() : null,
                t.getCategory() != null ? t.getCategory().getId() : null,
                t.getCategory() != null ? t.getCategory().getName() : null,
                t.getCreatedBy().getId(), t.getCreatedBy().getUsername(),
                t.getTransactionDate() != null ? t.getTransactionDate() : t.getCreatedAt().toLocalDate(),
                t.getCreatedAt(), t.getComment()
        );
    }

    private Specification<Transaction> visibleTransactions(
            User currentUser, Long userId, Long catId, Long mCatId, Long sCatId,
            TransactionType type, LocalDate from, LocalDate to
    ) {
        return (root, query, cb) -> {
            List<jakarta.persistence.criteria.Predicate> predicates = new ArrayList<>();

            // 1. Kasutaja filter (vastab baasi veerule created_by_id)
            predicates.add(cb.equal(root.get("createdBy").get("id"), userId));

            // 2. Tüübi filter
            if (type != null) {
                predicates.add(cb.equal(root.get("type"), type));
            }

            // 3. Kuupäeva filter
            if (from != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("transactionDate"), from));
            }
            if (to != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("transactionDate"), to));
            }

            var categoryJoin = root.join("category", JoinType.LEFT);
            if (catId != null || mCatId != null || sCatId != null) {
                Long targetId = catId != null ? catId : (sCatId != null ? sCatId : mCatId);
                predicates.add(cb.or(
                        cb.equal(categoryJoin.get("id"), targetId),
                        cb.equal(categoryJoin.get("parentCategory").get("id"), targetId)
                ));
            }

            return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };
    }

    private Sort parseSort(String sortParam, Sort incomingSort) {
        List<Sort.Order> orders = new ArrayList<>();
        if (sortParam != null && !sortParam.isBlank()) {
            for (String token : sortParam.split(",")) {
                String[] parts = token.trim().split(":");
                String field = parts[0].trim();
                String direction = parts.length > 1 ? parts[1].trim() : "asc";
                Sort.Order safeOrder = toSafeOrder(field, direction);
                if (safeOrder != null) orders.add(safeOrder);
            }
        } else if (incomingSort != null && incomingSort.isSorted()) {
            for (Sort.Order order : incomingSort) {
                Sort.Order safeOrder = toSafeOrder(order.getProperty(), order.getDirection().name());
                if (safeOrder != null) orders.add(safeOrder);
            }
        }
        if (orders.isEmpty()) {
            orders.add(Sort.Order.desc("transactionDate"));
            orders.add(Sort.Order.desc("id"));
        }
        return Sort.by(orders);
    }

    private Sort.Order toSafeOrder(String rawProp, String rawDir) {
        String prop = switch (rawProp) {
            case "transaction_date", "transactionDate" -> "transactionDate";
            case "created_at", "createdAt" -> "createdAt";
            case "amount" -> "amount";
            case "type" -> "type";
            case "categoryName" -> "category.name";
            case "accountName" -> "fromAccount.name";
            default -> null;
        };
        if (prop == null) return null;
        Sort.Direction dir = "desc".equalsIgnoreCase(rawDir) ? Sort.Direction.DESC : Sort.Direction.ASC;
        return new Sort.Order(dir, prop).nullsLast();
    }
}
