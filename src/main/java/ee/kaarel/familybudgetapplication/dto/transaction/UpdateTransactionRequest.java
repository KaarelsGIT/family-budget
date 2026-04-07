package ee.kaarel.familybudgetapplication.dto.transaction;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;

public record UpdateTransactionRequest(
        @NotNull @DecimalMin(value = "0.01") BigDecimal amount,
        Long fromAccountId,
        Long toAccountId,
        Long targetUserId,
        @NotNull LocalDate transactionDate,
        @Size(max = 500) String comment
) {
}
