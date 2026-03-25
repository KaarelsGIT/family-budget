package ee.kaarel.familybudgetapplication.dto.transaction;

import ee.kaarel.familybudgetapplication.model.TransactionType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record CreateTransactionRequest(
        @NotNull @DecimalMin(value = "0.01") BigDecimal amount,
        @NotNull TransactionType type,
        Long fromAccountId,
        Long toAccountId,
        Long categoryId,
        @Size(max = 500) String comment
) {
}
