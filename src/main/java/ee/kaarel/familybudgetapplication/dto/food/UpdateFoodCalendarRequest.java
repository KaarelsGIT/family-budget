package ee.kaarel.familybudgetapplication.dto.food;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

public record UpdateFoodCalendarRequest(
        @NotNull LocalDate date
) {
}
