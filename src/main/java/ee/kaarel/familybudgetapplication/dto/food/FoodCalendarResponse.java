package ee.kaarel.familybudgetapplication.dto.food;

import java.time.LocalDate;

public record FoodCalendarResponse(
        Long id,
        LocalDate date,
        FoodRecipeResponse recipe
) {
}
