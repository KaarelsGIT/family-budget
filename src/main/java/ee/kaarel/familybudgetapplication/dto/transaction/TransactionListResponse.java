package ee.kaarel.familybudgetapplication.dto.transaction;

import com.fasterxml.jackson.annotation.JsonFormat;
import ee.kaarel.familybudgetapplication.dto.common.ListResponse;
import java.math.BigDecimal;
import java.util.List;

public record TransactionListResponse(
        List<TransactionResponse> data,
        long total,
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        BigDecimal totalIncome,
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        BigDecimal totalExpenses,
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        BigDecimal totalTransfers
) {
    public TransactionListResponse(List<TransactionResponse> data, long total) {
        this(data, total, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
    }
}
