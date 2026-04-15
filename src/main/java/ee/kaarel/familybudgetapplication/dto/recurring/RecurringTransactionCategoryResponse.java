package ee.kaarel.familybudgetapplication.dto.recurring;

import ee.kaarel.familybudgetapplication.model.CategoryGroup;
import ee.kaarel.familybudgetapplication.model.TransactionType;

public record RecurringTransactionCategoryResponse(
        Long id,
        String name,
        String displayName,
        Long parentCategoryId,
        String parentCategoryName,
        TransactionType type,
        CategoryGroup group
) {
}
