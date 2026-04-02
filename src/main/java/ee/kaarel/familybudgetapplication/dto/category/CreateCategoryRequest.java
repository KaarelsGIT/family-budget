package ee.kaarel.familybudgetapplication.dto.category;

import ee.kaarel.familybudgetapplication.model.CategoryGroup;
import ee.kaarel.familybudgetapplication.model.TransactionType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record CreateCategoryRequest(
        @NotBlank @Size(max = 120) String name,
        @NotNull TransactionType type,
        Long parentCategoryId,
        @NotNull CategoryGroup group,
        Boolean isRecurring,
        Integer dueDayOfMonth,
        BigDecimal recurringAmount
) {
}
