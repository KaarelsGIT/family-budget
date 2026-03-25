package ee.kaarel.familybudgetapplication.dto.transaction;

import ee.kaarel.familybudgetapplication.model.TransactionType;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record TransactionResponse(
        Long id,
        BigDecimal amount,
        TransactionType type,
        Long fromAccountId,
        String fromAccountName,
        Long toAccountId,
        String toAccountName,
        Long categoryId,
        String categoryName,
        Long createdById,
        String createdByUsername,
        OffsetDateTime createdAt,
        String comment
) {
}
