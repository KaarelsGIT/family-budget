package ee.kaarel.familybudgetapplication.dto.food;

import ee.kaarel.familybudgetapplication.model.FoodRecipeType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.List;

public record CreateRecipeRequest(
        @NotBlank String name,
        @NotNull FoodRecipeType type,
        @NotBlank String instructions,
        @NotNull Integer baseServings,
        @NotNull BigDecimal cost,
        @Valid List<CreateIngredientRequest> ingredients
) {
}
