package ee.kaarel.familybudgetapplication.dto.transaction;

import com.fasterxml.jackson.annotation.JsonFormat;
import ee.kaarel.familybudgetapplication.model.TransactionType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

public record TransactionResponse(
        Long id,
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        BigDecimal amount,
        String transferId,
        TransactionType type,
        Long fromAccountId,
        String fromAccountName,
        Long toAccountId,
        String toAccountName,
        Long categoryId,
        String categoryName,
        Long createdById,
        String createdByUsername,
        LocalDate transactionDate,
        OffsetDateTime createdAt,
        String comment
) {
}
