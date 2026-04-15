package ee.kaarel.familybudgetapplication.controller;

import ee.kaarel.familybudgetapplication.dto.common.ApiResponse;
import ee.kaarel.familybudgetapplication.dto.common.ListResponse;
import ee.kaarel.familybudgetapplication.dto.reminder.ReminderPayDataResponse;
import ee.kaarel.familybudgetapplication.dto.reminder.ReminderResponse;
import ee.kaarel.familybudgetapplication.dto.transaction.TransactionResponse;
import ee.kaarel.familybudgetapplication.service.RecurringTransactionService;
import ee.kaarel.familybudgetapplication.service.TransactionService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping({"/api/reminders", "/api/recurring-reminders"})
public class ReminderController {

    private final RecurringTransactionService recurringTransactionService;
    private final TransactionService transactionService;

    public ReminderController(RecurringTransactionService recurringTransactionService, TransactionService transactionService) {
        this.recurringTransactionService = recurringTransactionService;
        this.transactionService = transactionService;
    }

    @GetMapping
    public ListResponse<ReminderResponse> getPendingReminders() {
        return recurringTransactionService.getPendingReminders();
    }

    @GetMapping("/{id}")
    public ApiResponse<ReminderResponse> getReminder(@PathVariable Long id) {
        return new ApiResponse<>(recurringTransactionService.toResponse(recurringTransactionService.getReminder(id)));
    }

    @GetMapping("/match")
    public ApiResponse<ReminderResponse> findMatchingReminder(@RequestParam Long categoryId, @RequestParam java.time.LocalDate transactionDate) {
        return new ApiResponse<>(recurringTransactionService.findMatchingPendingReminder(categoryId, transactionDate));
    }

    @GetMapping("/{id}/pay-data")
    public ApiResponse<ReminderPayDataResponse> getPayData(@PathVariable Long id) {
        return new ApiResponse<>(recurringTransactionService.getPayData(id));
    }

    @PostMapping("/{id}/complete")
    public ApiResponse<TransactionResponse> completeReminder(@PathVariable Long id) {
        return new ApiResponse<>(recurringTransactionService.completeReminder(id));
    }

    @PostMapping("/{id}/link/{transactionId}")
    public ApiResponse<ReminderResponse> linkReminderToTransaction(@PathVariable Long id, @PathVariable Long transactionId) {
        return new ApiResponse<>(recurringTransactionService.linkReminderToTransaction(id, transactionService.getTransaction(transactionId)));
    }

    @PostMapping("/{id}/skip")
    public ApiResponse<ReminderResponse> skipReminder(@PathVariable Long id) {
        return new ApiResponse<>(recurringTransactionService.skipReminder(id));
    }
}
