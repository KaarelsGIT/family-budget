package ee.kaarel.familybudgetapplication.controller;

import ee.kaarel.familybudgetapplication.dto.common.ApiResponse;
import ee.kaarel.familybudgetapplication.dto.common.ListResponse;
import ee.kaarel.familybudgetapplication.dto.recurring.CreateRecurringPaymentRequest;
import ee.kaarel.familybudgetapplication.dto.recurring.UpdateRecurringPaymentRequest;
import ee.kaarel.familybudgetapplication.service.RecurringPaymentService;
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
public class RecurringPaymentController {

    private final RecurringPaymentService recurringPaymentService;

    public RecurringPaymentController(RecurringPaymentService recurringPaymentService) {
        this.recurringPaymentService = recurringPaymentService;
    }

    @GetMapping
    public ListResponse<?> getRecurringPayments(Pageable pageable) {
        return recurringPaymentService.getRecurringPayments(pageable);
    }

    @PostMapping
    public ApiResponse<?> createRecurring(@Valid @RequestBody CreateRecurringPaymentRequest request) {
        return new ApiResponse<>(recurringPaymentService.create(request));
    }

    @PutMapping("/{id}")
    public ApiResponse<?> updateRecurring(@PathVariable Long id, @Valid @RequestBody UpdateRecurringPaymentRequest request) {
        return new ApiResponse<>(recurringPaymentService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<?> deleteRecurring(@PathVariable Long id) {
        recurringPaymentService.delete(id);
        return new ApiResponse<>("Recurring payment deleted");
    }
}
