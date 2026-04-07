package ee.kaarel.familybudgetapplication.dto.transaction;

import ee.kaarel.familybudgetapplication.model.TransactionType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;

public record CreateTransactionRequest(
        @NotNull @DecimalMin(value = "0.01") BigDecimal amount,
        TransactionType type,
        Long fromAccountId,
        Long toAccountId,
        Long targetUserId,
        Long categoryId,
        LocalDate transactionDate,
        @Size(max = 500) String comment
) {
}
