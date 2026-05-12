package ee.kaarel.familybudgetapplication.dto.account;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record AccountBalanceAdjustmentResponse(
        Long id,
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        BigDecimal amount,
        String comment,
        OffsetDateTime createdAt
) {
}
