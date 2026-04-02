package ee.kaarel.familybudgetapplication.dto.statistics;

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
            BigDecimal income,
            BigDecimal expenses,
            BigDecimal net,
            BigDecimal savings,
            BigDecimal savingsRateYear
    ) {
    }

    public record MonthlyEntry(
            int month,
            BigDecimal income,
            BigDecimal expenses,
            BigDecimal savings,
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
            BigDecimal total,
            Map<Integer, BigDecimal> monthly,
            List<SubcategoryEntry> subcategories
    ) {
    }

    public record SubcategoryEntry(
            String name,
            BigDecimal total,
            Map<Integer, BigDecimal> monthly
    ) {
    }

    public record AccountEntry(
            Long accountId,
            String name,
            BigDecimal income,
            BigDecimal expenses,
            BigDecimal balanceChange
    ) {
    }

    public record Transfers(
            BigDecimal totalAmount,
            long count,
            List<MonthlyTransferEntry> monthly
    ) {
    }

    public record MonthlyTransferEntry(
            int month,
            BigDecimal totalAmount,
            long count
    ) {
    }
}
