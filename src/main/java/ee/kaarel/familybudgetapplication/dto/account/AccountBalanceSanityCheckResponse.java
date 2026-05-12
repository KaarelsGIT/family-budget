package ee.kaarel.familybudgetapplication.dto.account;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record AccountBalanceSanityCheckResponse(
        Long accountId,
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        BigDecimal databaseBalance,
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        BigDecimal recalculatedBalance,
        boolean matches,
        Long divergenceEventId,
        String divergenceEventKind,
        OffsetDateTime divergenceEventCreatedAt,
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        BigDecimal divergenceExpectedBalance,
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        BigDecimal divergenceObservedBalance
) {
}
