package ee.kaarel.familybudgetapplication.dto.transaction;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.math.BigDecimal;

public record TransactionCreateResponse(
        TransactionResponse expense,
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        BigDecimal microSavingsAmount
) {
}
