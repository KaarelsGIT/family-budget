package ee.kaarel.familybudgetapplication.dto.transaction;

import java.math.BigDecimal;

public record TransactionCreateResponse(
        TransactionResponse expense,
        BigDecimal microSavingsAmount
) {
}
