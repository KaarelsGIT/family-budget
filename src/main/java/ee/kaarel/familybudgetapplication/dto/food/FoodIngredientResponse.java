package ee.kaarel.familybudgetapplication.dto.food;

public record FoodIngredientResponse(
        Long id,
        String name,
        Double baseAmount,
        String unit
) {
}
