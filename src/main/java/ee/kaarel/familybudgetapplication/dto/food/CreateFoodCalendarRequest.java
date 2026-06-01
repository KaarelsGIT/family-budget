package ee.kaarel.familybudgetapplication.dto.food;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

public record CreateFoodCalendarRequest(
        @NotNull LocalDate date,
        @NotNull Long recipeId
) {
}
