package ee.kaarel.familybudgetapplication.service;

import ee.kaarel.familybudgetapplication.appConfig.ApiException;
import ee.kaarel.familybudgetapplication.dto.common.ListResponse;
import ee.kaarel.familybudgetapplication.dto.recurring.CreateRecurringTransactionRequest;
import ee.kaarel.familybudgetapplication.dto.recurring.RecurringTransactionCategoryResponse;
import ee.kaarel.familybudgetapplication.dto.recurring.RecurringTransactionResponse;
import ee.kaarel.familybudgetapplication.dto.recurring.RecurringTransactionStatusResponse;
import ee.kaarel.familybudgetapplication.dto.recurring.UpdateRecurringTransactionRequest;
import ee.kaarel.familybudgetapplication.dto.reminder.ReminderPayDataResponse;
import ee.kaarel.familybudgetapplication.dto.reminder.ReminderResponse;
import ee.kaarel.familybudgetapplication.dto.transaction.CreateTransactionRequest;
import ee.kaarel.familybudgetapplication.dto.transaction.TransactionResponse;
import ee.kaarel.familybudgetapplication.model.Account;
import ee.kaarel.familybudgetapplication.model.Category;
import ee.kaarel.familybudgetapplication.model.ReminderStatus;
import ee.kaarel.familybudgetapplication.model.RecurringTransaction;
import ee.kaarel.familybudgetapplication.model.Role;
import ee.kaarel.familybudgetapplication.model.TransactionReminder;
import ee.kaarel.familybudgetapplication.model.TransactionType;
import ee.kaarel.familybudgetapplication.model.User;
import ee.kaarel.familybudgetapplication.repository.RecurringTransactionRepository;
import ee.kaarel.familybudgetapplication.repository.TransactionRepository;
import ee.kaarel.familybudgetapplication.repository.TransactionReminderRepository;
import jakarta.persistence.criteria.JoinType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RecurringTransactionService {

    private static final ZoneId TALLINN = ZoneId.of("Europe/Tallinn");

    private final RecurringTransactionRepository recurringTransactionRepository;
    private final TransactionReminderRepository transactionReminderRepository;
    private final TransactionRepository transactionRepository;
    private final CurrentUserService currentUserService;
    private final TransactionService transactionService;
    private final NotificationService notificationService;
    private final CategoryService categoryService;
    private final AccountService accountService;

    public RecurringTransactionService(
            RecurringTransactionRepository recurringTransactionRepository,
            TransactionReminderRepository transactionReminderRepository,
            TransactionRepository transactionRepository,
            CurrentUserService currentUserService,
            TransactionService transactionService,
            NotificationService notificationService,
            CategoryService categoryService,
            AccountService accountService
    ) {
        this.recurringTransactionRepository = recurringTransactionRepository;
        this.transactionReminderRepository = transactionReminderRepository;
        this.transactionRepository = transactionRepository;
        this.currentUserService = currentUserService;
        this.transactionService = transactionService;
        this.notificationService = notificationService;
        this.categoryService = categoryService;
        this.accountService = accountService;
    }

    @Transactional
    public ListResponse<RecurringTransactionResponse> getRecurringTransactions(Pageable pageable) {
        runReminderEngine();
        User currentUser = currentUserService.getCurrentUser();
        Pageable sorted = PageableUtils.withDefaultSort(pageable, Sort.by("nextDueDate").ascending().and(Sort.by("category.name").ascending()));
        Page<RecurringTransaction> page = recurringTransactionRepository.findAll(visibleRecurringTransactions(currentUser), sorted);
        return new ListResponse<>(page.map(this::toResponse).getContent(), page.getTotalElements());
    }

    @Transactional(readOnly = true)
    public ListResponse<RecurringTransactionCategoryResponse> getRecurringCategories() {
        User currentUser = currentUserService.getCurrentUser();
        List<RecurringTransactionCategoryResponse> categories = categoryService.getCategories(Pageable.unpaged())
                .data()
                .stream()
                .filter(category -> category.type() == TransactionType.INCOME || category.type() == TransactionType.EXPENSE)
                .filter(category -> currentUser.getRole() != Role.CHILD || category.group() == ee.kaarel.familybudgetapplication.model.CategoryGroup.CHILD)
                .map(category -> new RecurringTransactionCategoryResponse(
                        category.id(),
                        category.name(),
                        category.parentCategoryName() == null
                                ? category.name()
                                : category.parentCategoryName() + " > " + category.name(),
                        category.parentCategoryId(),
                        category.parentCategoryName(),
                        category.type(),
                        category.group()
                ))
                .sorted((left, right) -> left.displayName().compareToIgnoreCase(right.displayName()))
                .toList();

        return new ListResponse<>(categories, categories.size());
    }

    @Transactional
    public RecurringTransactionResponse create(CreateRecurringTransactionRequest request) {
        User currentUser = currentUserService.getCurrentUser();
        Category category = categoryService.getCategory(request.categoryId());
        categoryService.ensureVisible(currentUser, category);
        validateCategoryForRecurring(category);

        RecurringTransaction recurringTransaction = new RecurringTransaction();
        apply(recurringTransaction, currentUser, category, request.name(), request.amount(), request.dueDay(), request.active(), request.accountId());
        RecurringTransaction saved = recurringTransactionRepository.save(recurringTransaction);
        return toResponse(saved);
    }

    @Transactional
    public RecurringTransactionResponse update(Long id, UpdateRecurringTransactionRequest request) {
        User currentUser = currentUserService.getCurrentUser();
        RecurringTransaction recurringTransaction = getRecurringTransaction(id);
        ensureAccess(currentUser, recurringTransaction);

        Category category = recurringTransaction.getCategory();
        if (request.categoryId() != null) {
            category = categoryService.getCategory(request.categoryId());
            categoryService.ensureVisible(currentUser, category);
            validateCategoryForRecurring(category);
        }

        String name = request.name() != null ? request.name() : recurringTransaction.getComment();
        BigDecimal amount = request.amount() != null ? request.amount() : recurringTransaction.getAmount();
        Integer dueDay = request.dueDay() != null ? request.dueDay() : recurringTransaction.getDueDayOfMonth();
        Boolean active = request.active() != null ? request.active() : recurringTransaction.isActive();
        Long accountId = request.accountId() != null ? request.accountId() : recurringTransaction.getAccount() == null ? null : recurringTransaction.getAccount().getId();

        apply(recurringTransaction, currentUser, category, name, amount, dueDay, active, accountId);
        RecurringTransaction saved = recurringTransactionRepository.save(recurringTransaction);
        return toResponse(saved);
    }

    @Transactional
    public void delete(Long id) {
        User currentUser = currentUserService.getCurrentUser();
        RecurringTransaction recurringTransaction = getRecurringTransaction(id);
        ensureAccess(currentUser, recurringTransaction);
        recurringTransactionRepository.delete(recurringTransaction);
    }

    @Scheduled(cron = "0 0 0 * * *", zone = "Europe/Tallinn")
    @Transactional
    public void generateDueReminders() {
        runReminderEngine();
    }

    @Scheduled(cron = "0 0 8 * * *", zone = "Europe/Tallinn")
    @Transactional
    public void notifyUpcomingRecurringReminders() {
        processUpcomingRecurringTransactions(LocalDate.now(TALLINN));
    }

    public void runReminderEngine() {
        LocalDate today = LocalDate.now(TALLINN);
        processCurrentMonthRecurringTransactions(today);
        processDueRecurringTransactions(today);
        processUpcomingRecurringTransactions(today);
    }

    @Transactional
    public ListResponse<ReminderResponse> getPendingReminders() {
        runReminderEngine();

        User currentUser = currentUserService.getCurrentUser();
        List<TransactionReminder> reminders = currentUser.getRole() == Role.ADMIN
                ? transactionReminderRepository.findAllByStatusOrderByDueDateAsc(ReminderStatus.PENDING)
                : transactionReminderRepository.findAllByUserAndStatusOrderByDueDateAsc(currentUser, ReminderStatus.PENDING);

        return new ListResponse<>(reminders.stream().map(this::toResponse).toList(), reminders.size());
    }

    @Transactional(readOnly = true)
    public ReminderPayDataResponse getPayData(Long id) {
        TransactionReminder reminder = getReminder(id);
        ensureAccess(currentUserService.getCurrentUser(), reminder);
        if (reminder.getStatus() != ReminderStatus.PENDING) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Reminder is not pending");
        }
        return toPayDataResponse(reminder);
    }

    @Transactional(readOnly = true)
    public ReminderResponse findMatchingPendingReminder(Long categoryId, LocalDate transactionDate) {
        if (categoryId == null || transactionDate == null) {
            return null;
        }

        User currentUser = currentUserService.getCurrentUser();
        YearMonth transactionMonth = YearMonth.from(transactionDate);
        List<TransactionReminder> reminders = currentUser.getRole() == Role.ADMIN
                ? transactionReminderRepository.findAllByStatusOrderByDueDateAsc(ReminderStatus.PENDING)
                : transactionReminderRepository.findAllByUserAndStatusOrderByDueDateAsc(currentUser, ReminderStatus.PENDING);

        return reminders.stream()
                .filter(reminder -> reminder.getRecurringTransaction().getCategory().getId().equals(categoryId))
                .filter(reminder -> YearMonth.from(reminder.getDueDate()).equals(transactionMonth))
                .findFirst()
                .map(this::toResponse)
                .orElse(null);
    }

    @Transactional
    public TransactionResponse completeReminder(Long id) {
        User currentUser = currentUserService.getCurrentUser();
        TransactionReminder reminder = getReminder(id);
        ensureAccess(currentUser, reminder);
        if (reminder.getStatus() == ReminderStatus.SKIPPED) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Reminder is not pending");
        }
        if (reminder.getStatus() == ReminderStatus.COMPLETED && reminder.getTransaction() != null) {
            return transactionService.toResponse(reminder.getTransaction());
        }

        TransactionResponse transaction = transactionService.createForUser(reminder.getRecurringTransaction().getUser(), buildPaymentRequest(reminder));
        reminder.setTransaction(transactionService.getTransaction(transaction.id()));
        reminder.setStatus(ReminderStatus.COMPLETED);
        transactionReminderRepository.save(reminder);
        createNextReminderIfNeeded(reminder.getRecurringTransaction(), reminder.getDueDate());
        return transaction;
    }

    @Transactional
    public ReminderResponse linkReminderToTransaction(Long id, ee.kaarel.familybudgetapplication.model.Transaction transaction) {
        User currentUser = currentUserService.getCurrentUser();
        TransactionReminder reminder = getReminder(id);
        ensureAccess(currentUser, reminder);

        if (reminder.getStatus() == ReminderStatus.SKIPPED) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Reminder is not pending");
        }

        if (!reminder.getRecurringTransaction().getCategory().getId().equals(transaction.getCategory().getId())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Transaction category does not match reminder category");
        }

        if (transaction.getTransactionDate() == null || !YearMonth.from(transaction.getTransactionDate()).equals(YearMonth.from(reminder.getDueDate()))) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Transaction date does not match reminder month");
        }

        if (reminder.getStatus() == ReminderStatus.COMPLETED) {
            return toResponse(transactionReminderRepository.save(reminder));
        }

        reminder.setStatus(ReminderStatus.COMPLETED);
        return toResponse(transactionReminderRepository.save(reminder));
    }

    @Transactional
    public ReminderResponse skipReminder(Long id) {
        User currentUser = currentUserService.getCurrentUser();
        TransactionReminder reminder = getReminder(id);
        ensureAccess(currentUser, reminder);

        if (reminder.getStatus() != ReminderStatus.PENDING) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Reminder is not pending");
        }

        reminder.setStatus(ReminderStatus.SKIPPED);
        return toResponse(transactionReminderRepository.save(reminder));
    }

    @Transactional(readOnly = true)
    public RecurringTransaction getRecurringTransaction(Long id) {
        return recurringTransactionRepository.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Recurring transaction not found"));
    }

    @Transactional(readOnly = true)
    public TransactionReminder getReminder(Long id) {
        return transactionReminderRepository.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Reminder not found"));
    }

    public RecurringTransactionResponse toResponse(RecurringTransaction recurringTransaction) {
        RecurringTransactionStatusResponse currentMonthStatus = buildCurrentMonthStatus(recurringTransaction);
        return new RecurringTransactionResponse(
                recurringTransaction.getId(),
                recurringTransaction.getComment(),
                recurringTransaction.getAmount(),
                recurringTransaction.getDueDayOfMonth(),
                recurringTransaction.getCategory().getId(),
                recurringTransaction.getCategory().getName(),
                recurringTransaction.getUser().getId(),
                recurringTransaction.getUser().getUsername(),
                recurringTransaction.isActive(),
                currentMonthStatus
        );
    }

    public ReminderResponse toResponse(TransactionReminder reminder) {
        RecurringTransaction recurringTransaction = reminder.getRecurringTransaction();
        return new ReminderResponse(
                reminder.getId(),
                recurringTransaction.getId(),
                reminder.getTransaction() == null ? null : reminder.getTransaction().getId(),
                reminder.getUser().getId(),
                reminder.getUser().getUsername(),
                recurringTransaction.getCategory().getId(),
                recurringTransaction.getCategory().getName(),
                recurringTransaction.getAccount() == null ? null : recurringTransaction.getAccount().getId(),
                recurringTransaction.getAccount() == null ? null : recurringTransaction.getAccount().getName(),
                recurringTransaction.getAmount(),
                recurringTransaction.getComment(),
                reminder.getDueDate(),
                reminder.getStatus(),
                recurringTransaction.getCategory().getType(),
                reminder.isUrgent()
        );
    }

    public LocalDate nextDueDate(Integer dueDayOfMonth) {
        return calculateNextDueDate(LocalDate.now(TALLINN), dueDayOfMonth);
    }

    public LocalDate calculateNextDueDate(LocalDate referenceDate, Integer dueDayOfMonth) {
        validateDueDay(dueDayOfMonth);

        YearMonth currentMonth = YearMonth.from(referenceDate);
        LocalDate currentMonthDueDate = currentMonth.atDay(Math.min(dueDayOfMonth, currentMonth.lengthOfMonth()));
        if (!currentMonthDueDate.isBefore(referenceDate)) {
            return currentMonthDueDate;
        }

        YearMonth nextMonth = currentMonth.plusMonths(1);
        return nextMonth.atDay(Math.min(dueDayOfMonth, nextMonth.lengthOfMonth()));
    }

    private void apply(
            RecurringTransaction recurringTransaction,
            User currentUser,
            Category category,
            String name,
            BigDecimal amount,
            Integer dueDayOfMonth,
            Boolean active,
            Long accountId
    ) {
        String normalizedName = normalizeName(name);
        validateRecurringTransaction(category, amount, dueDayOfMonth);

        if (recurringTransaction.getUser() == null) {
            recurringTransaction.setUser(currentUser);
        }
        recurringTransaction.setCategory(category);
        recurringTransaction.setAmount(amount);
        recurringTransaction.setComment(normalizedName);
        recurringTransaction.setDueDayOfMonth(dueDayOfMonth);
        recurringTransaction.setNextDueDate(calculateNextDueDate(LocalDate.now(TALLINN), dueDayOfMonth));
        recurringTransaction.setActive(active == null || active);
        recurringTransaction.setAccount(resolveAccount(accountId));
    }

    private void validateRecurringTransaction(Category category, BigDecimal amount, Integer dueDayOfMonth) {
        if (category == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Category is required");
        }
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Recurring transaction amount must be greater than zero");
        }
        validateDueDay(dueDayOfMonth);
    }

    private void validateCategoryForRecurring(Category category) {
        if (category.getType() == TransactionType.TRANSFER) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Recurring transactions cannot use transfer categories");
        }
    }

    private void validateDueDay(Integer dueDayOfMonth) {
        if (dueDayOfMonth == null || dueDayOfMonth < 1 || dueDayOfMonth > 31) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Recurring transaction due day must be between 1 and 31");
        }
    }

    private Account resolveAccount(Long accountId) {
        if (accountId == null) {
            return null;
        }
        User currentUser = currentUserService.getCurrentUser();
        Account account = accountService.getAccount(accountId);
        accountService.ensureCanAccessAccount(currentUser, account);
        return account;
    }

    private Account resolveCompletionAccount(RecurringTransaction recurringTransaction) {
        if (recurringTransaction.getAccount() != null) {
            return recurringTransaction.getAccount();
        }
        return accountService.getDefaultMainAccount(recurringTransaction.getUser());
    }

    private String normalizeName(String name) {
        String normalized = name == null ? null : name.trim();
        if (normalized == null || normalized.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Recurring transaction name is required");
        }
        return normalized;
    }

    private void ensureAccess(User currentUser, RecurringTransaction recurringTransaction) {
        if (currentUser.getRole() == Role.ADMIN) {
            return;
        }
        if (!recurringTransaction.getUser().getId().equals(currentUser.getId())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "You cannot access this recurring transaction");
        }
    }

    private void ensureAccess(User currentUser, TransactionReminder reminder) {
        if (currentUser.getRole() == Role.ADMIN) {
            return;
        }
        if (!reminder.getUser().getId().equals(currentUser.getId())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "You cannot access this reminder");
        }
    }

    private TransactionResponse findMatchingTransaction(TransactionReminder reminder) {
        LocalDate from = YearMonth.from(reminder.getDueDate()).atDay(1);
        LocalDate to = YearMonth.from(reminder.getDueDate()).atEndOfMonth();
        TransactionType type = reminder.getRecurringTransaction().getCategory().getType();

        return transactionRepository.findAllByCreatedByAndCategoryAndTypeAndTransactionDateBetween(
                        reminder.getUser(),
                        reminder.getRecurringTransaction().getCategory(),
                        type,
                        from,
                        to
                ).stream()
                .findFirst()
                .map(transactionService::toResponse)
                .orElse(null);
    }

    private void processDueRecurringTransactions(LocalDate today) {
        List<RecurringTransaction> dueTransactions = recurringTransactionRepository.findAllByActiveTrueAndNextDueDateLessThanEqual(today);

        for (RecurringTransaction recurringTransaction : dueTransactions) {
            LocalDate dueDate = recurringTransaction.getNextDueDate();
            createReminderIfAbsent(recurringTransaction, dueDate);
            recurringTransaction.setNextDueDate(calculateNextDueDate(dueDate.plusDays(1), recurringTransaction.getDueDayOfMonth()));
            recurringTransactionRepository.save(recurringTransaction);
        }
    }

    private void processCurrentMonthRecurringTransactions(LocalDate today) {
        YearMonth currentMonth = YearMonth.from(today);
        List<RecurringTransaction> activeTransactions = recurringTransactionRepository.findAllByActiveTrue();

        for (RecurringTransaction recurringTransaction : activeTransactions) {
            LocalDate dueDate = currentMonth.atDay(Math.min(recurringTransaction.getDueDayOfMonth(), currentMonth.lengthOfMonth()));
            if (transactionReminderRepository.existsByRecurringTransactionAndDueDate(recurringTransaction, dueDate)) {
                continue;
            }

            createReminderIfAbsent(recurringTransaction, dueDate);
            recurringTransaction.setNextDueDate(calculateNextDueDate(dueDate.plusDays(1), recurringTransaction.getDueDayOfMonth()));
            recurringTransactionRepository.save(recurringTransaction);
        }
    }

    private void processUpcomingRecurringTransactions(LocalDate today) {
        LocalDate dueTomorrow = today.plusDays(1);
        List<RecurringTransaction> upcomingTransactions = recurringTransactionRepository.findAllByActiveTrueAndNextDueDate(dueTomorrow);

        for (RecurringTransaction recurringTransaction : upcomingTransactions) {
            createReminderIfAbsent(recurringTransaction, dueTomorrow);
        }
    }

    private void createReminderIfAbsent(RecurringTransaction recurringTransaction, LocalDate dueDate) {
        createReminderIfAbsent(recurringTransaction, dueDate, true);
    }

    private void createReminderIfAbsent(RecurringTransaction recurringTransaction, LocalDate dueDate, boolean notify) {
        if (transactionReminderRepository.existsByRecurringTransactionAndDueDate(recurringTransaction, dueDate)) {
            return;
        }

        TransactionReminder reminder = new TransactionReminder();
        reminder.setRecurringTransaction(recurringTransaction);
        reminder.setUser(recurringTransaction.getUser());
        reminder.setDueDate(dueDate);
        reminder.setStatus(ReminderStatus.PENDING);
        reminder = transactionReminderRepository.save(reminder);
        if (notify) {
            notificationService.notifyRecurringTransactionDue(recurringTransaction.getUser(), recurringTransaction, reminder);
        }
    }

    private void createNextReminderIfNeeded(RecurringTransaction recurringTransaction, LocalDate completedReminderDate) {
        LocalDate nextDueDate = calculateNextDueDate(completedReminderDate.plusDays(1), recurringTransaction.getDueDayOfMonth());
        if (transactionReminderRepository.existsByRecurringTransactionAndDueDate(recurringTransaction, nextDueDate)) {
            return;
        }

        createReminderIfAbsent(recurringTransaction, nextDueDate, false);
        recurringTransaction.setNextDueDate(nextDueDate);
        recurringTransactionRepository.save(recurringTransaction);
    }

    private RecurringTransactionStatusResponse buildCurrentMonthStatus(RecurringTransaction recurringTransaction) {
        LocalDate now = LocalDate.now(TALLINN);
        LocalDate dueDate = YearMonth.of(now.getYear(), now.getMonthValue())
                .atDay(Math.min(recurringTransaction.getDueDayOfMonth(), YearMonth.of(now.getYear(), now.getMonthValue()).lengthOfMonth()));
        TransactionReminder reminder = transactionReminderRepository.findByRecurringTransactionAndDueDate(recurringTransaction, dueDate)
                .orElse(null);
        long reminderId = reminder == null ? 0L : reminder.getId();
        boolean paid = reminder != null && reminder.getStatus() == ReminderStatus.COMPLETED;
        boolean urgent = reminder != null && reminder.isUrgent();
        return new RecurringTransactionStatusResponse(reminderId, now.getYear(), now.getMonthValue(), paid, urgent);
    }

    public ReminderPayDataResponse toPayDataResponse(TransactionReminder reminder) {
        RecurringTransaction recurringTransaction = reminder.getRecurringTransaction();
        Account account = resolveCompletionAccount(recurringTransaction);
        return new ReminderPayDataResponse(
                reminder.getId(),
                recurringTransaction.getId(),
                recurringTransaction.getCategory().getType(),
                recurringTransaction.getAmount(),
                recurringTransaction.getCategory().getId(),
                recurringTransaction.getCategory().getName(),
                account.getId(),
                account.getName(),
                recurringTransaction.getComment(),
                reminder.getDueDate()
        );
    }

    private CreateTransactionRequest buildPaymentRequest(TransactionReminder reminder) {
        RecurringTransaction recurringTransaction = reminder.getRecurringTransaction();
        Account account = resolveCompletionAccount(recurringTransaction);
        return switch (recurringTransaction.getCategory().getType()) {
            case INCOME -> new CreateTransactionRequest(
                    recurringTransaction.getAmount(),
                    TransactionType.INCOME,
                    null,
                    account.getId(),
                    null,
                    recurringTransaction.getCategory().getId(),
                    reminder.getDueDate(),
                    recurringTransaction.getComment(),
                    reminder.getId()
            );
            case EXPENSE -> new CreateTransactionRequest(
                    recurringTransaction.getAmount(),
                    TransactionType.EXPENSE,
                    account.getId(),
                    null,
                    null,
                    recurringTransaction.getCategory().getId(),
                    reminder.getDueDate(),
                    recurringTransaction.getComment(),
                    reminder.getId()
            );
            case TRANSFER -> throw new ApiException(HttpStatus.BAD_REQUEST, "Recurring transactions cannot use transfer categories");
        };
    }

    private Specification<RecurringTransaction> visibleRecurringTransactions(User currentUser) {
        return (root, query, cb) -> {
            if (query != null && !Long.class.equals(query.getResultType()) && !long.class.equals(query.getResultType())) {
                root.fetch("user", JoinType.LEFT);
                root.fetch("category", JoinType.LEFT);
                root.fetch("account", JoinType.LEFT);
                query.distinct(true);
            }
            if (currentUser.getRole() == Role.ADMIN) {
                return cb.conjunction();
            }
            return cb.equal(root.get("user").get("id"), currentUser.getId());
        };
    }
}
