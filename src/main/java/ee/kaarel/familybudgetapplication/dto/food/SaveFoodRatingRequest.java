package ee.kaarel.familybudgetapplication.dto.food;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record SaveFoodRatingRequest(
        @NotNull @Min(1) @Max(5) Integer rating
) {
}
