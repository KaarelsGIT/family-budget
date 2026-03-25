package ee.kaarel.familybudgetapplication.dto.recurring;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record CreateRecurringPaymentRequest(
        @NotBlank @Size(max = 120) String name,
        @NotNull @DecimalMin(value = "0.01") BigDecimal amount,
        @NotNull @Min(1) @Max(31) Integer dueDay,
        @NotNull Long categoryId,
        Boolean active
) {
}
