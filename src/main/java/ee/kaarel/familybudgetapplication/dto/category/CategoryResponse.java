package ee.kaarel.familybudgetapplication.dto.category;

import com.fasterxml.jackson.annotation.JsonFormat;
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
        @Deprecated
        boolean isRecurring,
        @Deprecated
        Integer dueDayOfMonth,
        @Deprecated
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        BigDecimal recurringAmount
) {
}
