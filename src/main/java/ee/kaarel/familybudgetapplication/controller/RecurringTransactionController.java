package ee.kaarel.familybudgetapplication.controller;

import ee.kaarel.familybudgetapplication.dto.common.ApiResponse;
import ee.kaarel.familybudgetapplication.dto.common.ListResponse;
import ee.kaarel.familybudgetapplication.dto.recurring.CreateRecurringTransactionRequest;
import ee.kaarel.familybudgetapplication.dto.recurring.RecurringTransactionCategoryResponse;
import ee.kaarel.familybudgetapplication.dto.recurring.RecurringTransactionResponse;
import ee.kaarel.familybudgetapplication.dto.recurring.UpdateRecurringTransactionRequest;
import ee.kaarel.familybudgetapplication.service.RecurringTransactionService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/recurring")
public class RecurringTransactionController {

    private final RecurringTransactionService recurringTransactionService;

    public RecurringTransactionController(RecurringTransactionService recurringTransactionService) {
        this.recurringTransactionService = recurringTransactionService;
    }

    @GetMapping
    public ListResponse<RecurringTransactionResponse> getRecurringTransactions(Pageable pageable) {
        return recurringTransactionService.getRecurringTransactions(pageable);
    }

    @GetMapping("/categories")
    public ListResponse<RecurringTransactionCategoryResponse> getRecurringCategories() {
        return recurringTransactionService.getRecurringCategories();
    }

    @PostMapping
    public ApiResponse<RecurringTransactionResponse> createRecurring(@Valid @RequestBody CreateRecurringTransactionRequest request) {
        return new ApiResponse<>(recurringTransactionService.create(request));
    }

    @PutMapping("/{id}")
    public ApiResponse<RecurringTransactionResponse> updateRecurring(@PathVariable Long id, @Valid @RequestBody UpdateRecurringTransactionRequest request) {
        return new ApiResponse<>(recurringTransactionService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<String> deleteRecurring(@PathVariable Long id) {
        recurringTransactionService.delete(id);
        return new ApiResponse<>("Recurring transaction deleted");
    }
}
