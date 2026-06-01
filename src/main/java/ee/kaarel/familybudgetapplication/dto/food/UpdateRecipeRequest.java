package ee.kaarel.familybudgetapplication.dto.food;

import ee.kaarel.familybudgetapplication.model.FoodRecipeType;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.util.List;

public record UpdateRecipeRequest(
        String name,
        FoodRecipeType type,
        String instructions,
        Integer baseServings,
        BigDecimal cost,
        @Valid List<CreateIngredientRequest> ingredients
) {
}
