package ee.kaarel.familybudgetapplication.dto.food;

import ee.kaarel.familybudgetapplication.model.FoodRecipeType;
import java.math.BigDecimal;
import java.util.List;

public record FoodRecipeResponse(
        Long id,
        String name,
        FoodRecipeType type,
        String instructions,
        Integer baseServings,
        BigDecimal cost,
        List<FoodIngredientResponse> ingredients,
        Double averageRating,
        Integer ratingCount,
        Integer myRating
) {
}
