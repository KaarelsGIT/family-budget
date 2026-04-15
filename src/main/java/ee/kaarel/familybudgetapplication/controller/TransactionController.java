package ee.kaarel.familybudgetapplication.controller;

import ee.kaarel.familybudgetapplication.dto.common.ApiResponse;
import ee.kaarel.familybudgetapplication.dto.common.ListResponse;
import ee.kaarel.familybudgetapplication.dto.transaction.CreateTransactionRequest;
import ee.kaarel.familybudgetapplication.dto.transaction.UpdateTransactionRequest;
import ee.kaarel.familybudgetapplication.model.TransactionType;
import ee.kaarel.familybudgetapplication.service.TransactionService;
import jakarta.validation.Valid;
import java.time.LocalDate;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/transactions")
public class TransactionController {

    private final TransactionService transactionService;

    public TransactionController(TransactionService transactionService) {
        this.transactionService = transactionService;
    }

    @GetMapping
    public ListResponse<?> getTransactions(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) Long mainCategoryId,
            @RequestParam(required = false) Long subCategoryId,
            @RequestParam(required = false) TransactionType type,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        Pageable pageable = PageRequest.of(page, size);
        return transactionService.getTransactions(pageable, sort, userId, categoryId, mainCategoryId, subCategoryId, type, from, to);
    }

    @PostMapping
    public ApiResponse<?> createTransaction(@Valid @RequestBody CreateTransactionRequest request) {
        return new ApiResponse<>(transactionService.create(request));
    }

    @PutMapping("/{id}")
    public ApiResponse<?> updateTransaction(@PathVariable Long id, @Valid @RequestBody UpdateTransactionRequest request) {
        return new ApiResponse<>(transactionService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<?> deleteTransaction(@PathVariable Long id) {
        transactionService.delete(id);
        return new ApiResponse<>("Transaction deleted successfully");
    }
}
