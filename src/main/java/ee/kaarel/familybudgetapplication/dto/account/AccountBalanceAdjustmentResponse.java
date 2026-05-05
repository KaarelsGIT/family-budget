package ee.kaarel.familybudgetapplication.dto.account;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record AccountBalanceAdjustmentResponse(
        Long id,
        BigDecimal amount,
        String comment,
        OffsetDateTime createdAt
) {
}
