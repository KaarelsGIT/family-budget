package ee.kaarel.familybudgetapplication.dto.transaction;

import ee.kaarel.familybudgetapplication.dto.common.ListResponse;
import java.math.BigDecimal;
import java.util.List;

public record TransactionListResponse(
        List<TransactionResponse> data,
        long total,
        BigDecimal totalIncome,
        BigDecimal totalExpenses,
        BigDecimal totalTransfers
) {
    public TransactionListResponse(List<TransactionResponse> data, long total) {
        this(data, total, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
    }
}
