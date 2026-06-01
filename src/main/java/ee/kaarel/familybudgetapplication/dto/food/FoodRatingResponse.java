package ee.kaarel.familybudgetapplication.dto.food;

public record FoodRatingResponse(
        Long id,
        Long recipeId,
        Long userId,
        Integer rating
) {
}
