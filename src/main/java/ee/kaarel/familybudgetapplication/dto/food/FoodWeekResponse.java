package ee.kaarel.familybudgetapplication.dto.food;

import java.time.LocalDate;
import java.util.List;

public record FoodWeekResponse(
        LocalDate weekStart,
        List<FoodCalendarResponse> entries
) {
}
