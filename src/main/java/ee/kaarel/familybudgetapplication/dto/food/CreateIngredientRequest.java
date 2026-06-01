package ee.kaarel.familybudgetapplication.dto.food;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateIngredientRequest(
        @NotBlank String name,
        @NotNull Double baseAmount,
        @NotBlank String unit
) {
}
