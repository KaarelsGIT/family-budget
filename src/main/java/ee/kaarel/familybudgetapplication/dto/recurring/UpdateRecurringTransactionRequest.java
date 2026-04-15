package ee.kaarel.familybudgetapplication.dto.recurring;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record UpdateRecurringTransactionRequest(
        @Size(max = 120) String name,
        Long categoryId,
        @DecimalMin(value = "0.01") BigDecimal amount,
        Integer dueDay,
        Boolean active,
        Long accountId
) {
}
