package ee.kaarel.familybudgetapplication.dto.account;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Pattern;
import java.math.BigDecimal;

public record UpdateSavingsGoalRequest(
        @DecimalMin(value = "0.01")
        BigDecimal targetAmount,
        @Pattern(regexp = "^\\d{4}-\\d{2}-\\d{2}$")
        String targetDate
) {
}
