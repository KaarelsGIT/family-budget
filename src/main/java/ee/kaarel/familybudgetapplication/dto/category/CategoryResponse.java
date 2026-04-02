package ee.kaarel.familybudgetapplication.dto.category;

import ee.kaarel.familybudgetapplication.model.CategoryGroup;
import ee.kaarel.familybudgetapplication.model.TransactionType;
import java.math.BigDecimal;

public record CategoryResponse(
        Long id,
        Long userId,
        String name,
        TransactionType type,
        Long parentCategoryId,
        String parentCategoryName,
        CategoryGroup group,
        boolean isRecurring,
        Integer dueDayOfMonth,
        BigDecimal recurringAmount
) {
}
