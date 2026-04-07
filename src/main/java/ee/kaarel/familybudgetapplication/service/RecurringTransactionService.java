package ee.kaarel.familybudgetapplication.service;

import ee.kaarel.familybudgetapplication.appConfig.ApiException;
import ee.kaarel.familybudgetapplication.dto.common.ListResponse;
import ee.kaarel.familybudgetapplication.dto.reminder.ReminderResponse;
import ee.kaarel.familybudgetapplication.dto.transaction.CreateTransactionRequest;
import ee.kaarel.familybudgetapplication.dto.transaction.TransactionResponse;
import ee.kaarel.familybudgetapplication.model.Category;
import ee.kaarel.familybudgetapplication.model.RecurringTransaction;
import ee.kaarel.familybudgetapplication.model.ReminderStatus;
import ee.kaarel.familybudgetapplication.model.Role;
import ee.kaarel.familybudgetapplication.model.TransactionReminder;
import ee.kaarel.familybudgetapplication.model.TransactionType;
import ee.kaarel.familybudgetapplication.model.User;
import ee.kaarel.familybudgetapplication.repository.RecurringTransactionRepository;
import ee.kaarel.familybudgetapplication.repository.TransactionReminderRepository;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.YearMonth;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RecurringTransactionService {

    private static final ZoneId TALLINN = ZoneId.of("Europe/Tallinn");

    private final RecurringTransactionRepository recurringTransactionRepository;
    private final TransactionReminderRepository transactionReminderRepository;
    private final CurrentUserService currentUserService;
    private final TransactionService transactionService;
    private final NotificationService notificationService;

    public RecurringTransactionService(
            RecurringTransactionRepository recurringTransactionRepository,
            TransactionReminderRepository transactionReminderRepository,
            CurrentUserService currentUserService,
            TransactionService transactionService,
            NotificationService notificationService
    ) {
        this.recurringTransactionRepository = recurringTransactionRepository;
        this.transactionReminderRepository = transactionReminderRepository;
        this.currentUserService = currentUserService;
        this.transactionService = transactionService;
        this.notificationService = notificationService;
    }

    @Scheduled(cron = "0 0 0 * * *", zone = "Europe/Tallinn")
    @Transactional
    public void generateDueReminders() {
        processDueRecurringTransactions(LocalDate.now(TALLINN));
    }

    @Scheduled(cron = "0 0 8 * * *", zone = "Europe/Tallinn")
    @Transactional
    public void notifyUpcomingRecurringReminders() {
        // Send the reminder a day early so "tomorrow" entries are visible in the UI today.
        processUpcomingRecurringTransactions(LocalDate.now(TALLINN));
    }

    @Transactional
    public ListResponse<ReminderResponse> getPendingReminders() {
        // Backfill newly due reminders before we read the list so the UI does not
        // wait for the nightly scheduler if a recurring item was created during the day.
        processDueRecurringTransactions(LocalDate.now(TALLINN));

        User currentUser = currentUserService.getCurrentUser();
        List<TransactionReminder> reminders = currentUser.getRole() == Role.ADMIN
                ? transactionReminderRepository.findAllByStatusOrderByDueDateAsc(ReminderStatus.PENDING)
                : transactionReminderRepository.findAllByUserAndStatusOrderByDueDateAsc(currentUser, ReminderStatus.PENDING);

        return new ListResponse<>(reminders.stream().map(this::toResponse).toList(), reminders.size());
    }

    @Transactional
    public TransactionResponse completeReminder(Long id) {
        User currentUser = currentUserService.getCurrentUser();
        TransactionReminder reminder = getReminder(id);
        ensureAccess(currentUser, reminder);

        if (reminder.getStatus() != ReminderStatus.PENDING) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Reminder is not pending");
        }

        RecurringTransaction recurringTransaction = reminder.getRecurringTransaction();
        if (recurringTransaction.getAmount() == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Recurring transaction amount is required");
        }
        if (recurringTransaction.getAccount() == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Recurring transaction account is required");
        }

        Category category = recurringTransaction.getCategory();
        if (category.getType() == TransactionType.TRANSFER) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Recurring transactions cannot use transfer categories");
        }

        CreateTransactionRequest request = switch (category.getType()) {
            case INCOME -> new CreateTransactionRequest(
                    recurringTransaction.getAmount(),
                    TransactionType.INCOME,
                    null,
                    recurringTransaction.getAccount().getId(),
                    null,
                    category.getId(),
                    reminder.getDueDate(),
                    recurringTransaction.getComment()
            );
            case EXPENSE -> new CreateTransactionRequest(
                    recurringTransaction.getAmount(),
                    TransactionType.EXPENSE,
                    recurringTransaction.getAccount().getId(),
                    null,
                    null,
                    category.getId(),
                    reminder.getDueDate(),
                    recurringTransaction.getComment()
            );
            case TRANSFER -> throw new ApiException(HttpStatus.BAD_REQUEST, "Recurring transactions cannot use transfer categories");
        };

        // The generated transaction belongs to the recurring transaction owner.
        TransactionResponse transaction = transactionService.createForUser(recurringTransaction.getUser(), request);
        reminder.setStatus(ReminderStatus.COMPLETED);
        transactionReminderRepository.save(reminder);
        return transaction;
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
    public TransactionReminder getReminder(Long id) {
        return transactionReminderRepository.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Reminder not found"));
    }

    private void ensureAccess(User currentUser, TransactionReminder reminder) {
        if (currentUser.getRole() == Role.ADMIN) {
            return;
        }

        if (!reminder.getUser().getId().equals(currentUser.getId())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "You cannot access this reminder");
        }
    }

    private void processDueRecurringTransactions(LocalDate today) {
        List<RecurringTransaction> dueTransactions = recurringTransactionRepository.findAllByActiveTrueAndNextDueDateLessThanEqual(today);

        for (RecurringTransaction recurringTransaction : dueTransactions) {
            LocalDate dueDate = recurringTransaction.getNextDueDate();
            createReminderIfAbsent(recurringTransaction, dueDate);

            // Advance to the next calendar month and clamp the day if the month is shorter.
            recurringTransaction.setNextDueDate(nextDueDate(dueDate, recurringTransaction.getDueDayOfMonth()));
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
        if (transactionReminderRepository.existsByRecurringTransactionAndDueDate(recurringTransaction, dueDate)) {
            return;
        }

        TransactionReminder reminder = new TransactionReminder();
        reminder.setRecurringTransaction(recurringTransaction);
        reminder.setUser(recurringTransaction.getUser());
        reminder.setDueDate(dueDate);
        reminder.setStatus(ReminderStatus.PENDING);
        reminder = transactionReminderRepository.save(reminder);
        notificationService.notifyRecurringPaymentDue(recurringTransaction.getUser(), recurringTransaction, reminder);
    }

    public ReminderResponse toResponse(TransactionReminder reminder) {
        RecurringTransaction recurringTransaction = reminder.getRecurringTransaction();
        return new ReminderResponse(
                reminder.getId(),
                recurringTransaction.getId(),
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
                recurringTransaction.getCategory().getType()
        );
    }

    private LocalDate nextDueDate(LocalDate currentDueDate, Integer dueDayOfMonth) {
        if (dueDayOfMonth == null || dueDayOfMonth < 1 || dueDayOfMonth > 31) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Recurring transaction due day must be between 1 and 31");
        }

        YearMonth nextMonth = YearMonth.from(currentDueDate).plusMonths(1);
        return nextMonth.atDay(Math.min(dueDayOfMonth, nextMonth.lengthOfMonth()));
    }
}
