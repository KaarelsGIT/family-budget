package ee.kaarel.familybudgetapplication.dto.recurring;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record CreateRecurringTransactionRequest(
        @NotBlank @Size(max = 120) String name,
        @NotNull Long categoryId,
        @NotNull @DecimalMin(value = "0.01") BigDecimal amount,
        @NotNull Integer dueDay,
        Boolean active,
        Long accountId
) {
}
