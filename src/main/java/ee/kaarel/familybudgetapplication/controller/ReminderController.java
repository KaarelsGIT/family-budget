package ee.kaarel.familybudgetapplication.controller;

import ee.kaarel.familybudgetapplication.dto.common.ApiResponse;
import ee.kaarel.familybudgetapplication.dto.common.ListResponse;
import ee.kaarel.familybudgetapplication.dto.reminder.ReminderResponse;
import ee.kaarel.familybudgetapplication.dto.transaction.TransactionResponse;
import ee.kaarel.familybudgetapplication.service.RecurringTransactionService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping({"/api/reminders", "/api/recurring-reminders"})
public class ReminderController {

    private final RecurringTransactionService recurringTransactionService;

    public ReminderController(RecurringTransactionService recurringTransactionService) {
        this.recurringTransactionService = recurringTransactionService;
    }

    @GetMapping
    public ListResponse<ReminderResponse> getPendingReminders() {
        return recurringTransactionService.getPendingReminders();
    }

    @GetMapping("/{id}")
    public ApiResponse<ReminderResponse> getReminder(@PathVariable Long id) {
        return new ApiResponse<>(recurringTransactionService.toResponse(recurringTransactionService.getReminder(id)));
    }

    @PostMapping("/{id}/complete")
    public ApiResponse<TransactionResponse> completeReminder(@PathVariable Long id) {
        return new ApiResponse<>(recurringTransactionService.completeReminder(id));
    }

    @PostMapping("/{id}/skip")
    public ApiResponse<ReminderResponse> skipReminder(@PathVariable Long id) {
        return new ApiResponse<>(recurringTransactionService.skipReminder(id));
    }
}
