package ee.kaarel.familybudgetapplication.dto.statistics;

import ee.kaarel.familybudgetapplication.model.AccountType;
import ee.kaarel.familybudgetapplication.model.TransactionType;
import java.math.BigDecimal;

public record YearlyStatisticsRow(
        Integer month,
        TransactionType type,
        Long fromAccountId,
        String fromAccountName,
        AccountType fromAccountType,
        Long toAccountId,
        String toAccountName,
        AccountType toAccountType,
        Long parentCategoryId,
        String parentCategoryName,
        Long categoryId,
        String categoryName,
        BigDecimal totalAmount,
        Long transactionCount
) {
}
