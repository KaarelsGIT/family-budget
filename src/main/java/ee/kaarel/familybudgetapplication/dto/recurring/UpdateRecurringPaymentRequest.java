package ee.kaarel.familybudgetapplication.dto.recurring;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record UpdateRecurringPaymentRequest(
        @Size(max = 120) String name,
        @DecimalMin(value = "0.01") BigDecimal amount,
        @Min(1) @Max(31) Integer dueDay,
        Long categoryId,
        Boolean active
) {
}
