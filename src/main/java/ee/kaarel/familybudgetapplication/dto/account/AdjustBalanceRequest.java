package ee.kaarel.familybudgetapplication.dto.account;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record AdjustBalanceRequest(
        @NotNull BigDecimal amount,
        @NotBlank @Size(max = 500) String comment
) {
}
