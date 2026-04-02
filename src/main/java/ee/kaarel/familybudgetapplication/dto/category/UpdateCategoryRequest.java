package ee.kaarel.familybudgetapplication.dto.category;

import ee.kaarel.familybudgetapplication.model.CategoryGroup;
import ee.kaarel.familybudgetapplication.model.TransactionType;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record UpdateCategoryRequest(
        @Size(max = 120) String name,
        TransactionType type,
        Long parentCategoryId,
        CategoryGroup group,
        Boolean isRecurring,
        Integer dueDayOfMonth,
        BigDecimal recurringAmount
) {
}
