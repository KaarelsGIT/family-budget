package ee.kaarel.familybudgetapplication.dto.statistics;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.math.BigDecimal;
import java.util.Map;
import java.util.List;

public record YearlyStatisticsResponse(
        int year,
        Totals totals,
        List<MonthlyEntry> monthly,
        Categories categories,
        List<AccountEntry> accounts,
        Transfers transfers
) {
    public record Totals(
            @JsonFormat(shape = JsonFormat.Shape.STRING)
            BigDecimal income,
            @JsonFormat(shape = JsonFormat.Shape.STRING)
            BigDecimal expenses,
            @JsonFormat(shape = JsonFormat.Shape.STRING)
            BigDecimal net,
            @JsonFormat(shape = JsonFormat.Shape.STRING)
            BigDecimal savings,
            @JsonFormat(shape = JsonFormat.Shape.STRING)
            BigDecimal savingsRateYear
    ) {
    }

    public record MonthlyEntry(
            int month,
            @JsonFormat(shape = JsonFormat.Shape.STRING)
            BigDecimal income,
            @JsonFormat(shape = JsonFormat.Shape.STRING)
            BigDecimal expenses,
            @JsonFormat(shape = JsonFormat.Shape.STRING)
            BigDecimal savings,
            @JsonFormat(shape = JsonFormat.Shape.STRING)
            BigDecimal savingsRate
    ) {
    }

    public record Categories(
            List<CategoryEntry> income,
            List<CategoryEntry> expenses
    ) {
    }

    public record CategoryEntry(
            String parentCategory,
            @JsonFormat(shape = JsonFormat.Shape.STRING)
            BigDecimal total,
            Map<Integer, BigDecimal> monthly,
            List<SubcategoryEntry> subcategories
    ) {
    }

    public record SubcategoryEntry(
            String name,
            @JsonFormat(shape = JsonFormat.Shape.STRING)
            BigDecimal total,
            Map<Integer, BigDecimal> monthly
    ) {
    }

    public record AccountEntry(
            Long accountId,
            String name,
            @JsonFormat(shape = JsonFormat.Shape.STRING)
            BigDecimal income,
            @JsonFormat(shape = JsonFormat.Shape.STRING)
            BigDecimal expenses,
            @JsonFormat(shape = JsonFormat.Shape.STRING)
            BigDecimal balanceChange
    ) {
    }

    public record Transfers(
            @JsonFormat(shape = JsonFormat.Shape.STRING)
            BigDecimal totalAmount,
            long count,
            List<MonthlyTransferEntry> monthly
    ) {
    }

    public record MonthlyTransferEntry(
            int month,
            @JsonFormat(shape = JsonFormat.Shape.STRING)
            BigDecimal totalAmount,
            long count
    ) {
    }
}
