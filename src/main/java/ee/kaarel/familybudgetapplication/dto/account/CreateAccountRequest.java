package ee.kaarel.familybudgetapplication.dto.account;

import ee.kaarel.familybudgetapplication.model.AccountType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record CreateAccountRequest(
        @NotBlank @Size(max = 120) String name,
        @NotNull AccountType type,
        @DecimalMin(value = "0.01", message = "Target amount must be greater than zero")
        BigDecimal targetAmount,
        String targetDate
) {
}
