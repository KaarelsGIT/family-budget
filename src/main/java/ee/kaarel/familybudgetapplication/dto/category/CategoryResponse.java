package ee.kaarel.familybudgetapplication.dto.category;

import ee.kaarel.familybudgetapplication.model.CategoryGroup;
import ee.kaarel.familybudgetapplication.model.TransactionType;

public record CategoryResponse(
        Long id,
        String name,
        TransactionType type,
        Long parentCategoryId,
        String parentCategoryName,
        CategoryGroup group
) {
}
