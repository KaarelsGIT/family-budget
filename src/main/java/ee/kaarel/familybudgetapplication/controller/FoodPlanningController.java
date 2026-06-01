package ee.kaarel.familybudgetapplication.controller;

import ee.kaarel.familybudgetapplication.dto.common.ApiResponse;
import ee.kaarel.familybudgetapplication.dto.food.CreateFoodCalendarRequest;
import ee.kaarel.familybudgetapplication.dto.food.CreateRecipeRequest;
import ee.kaarel.familybudgetapplication.dto.food.FoodRecipeResponse;
import ee.kaarel.familybudgetapplication.dto.food.FoodWeekResponse;
import ee.kaarel.familybudgetapplication.dto.food.SaveFoodRatingRequest;
import ee.kaarel.familybudgetapplication.dto.food.UpdateFoodCalendarRequest;
import ee.kaarel.familybudgetapplication.dto.food.UpdateRecipeRequest;
import ee.kaarel.familybudgetapplication.model.FoodRecipeType;
import ee.kaarel.familybudgetapplication.service.FoodPlanningService;
import jakarta.validation.Valid;
import java.time.LocalDate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/food-plans")
@PreAuthorize("isAuthenticated()")
public class FoodPlanningController {
    private final FoodPlanningService foodPlanningService;

    public FoodPlanningController(FoodPlanningService foodPlanningService) {
        this.foodPlanningService = foodPlanningService;
    }

    @GetMapping("/recipes")
    public ApiResponse<java.util.List<FoodRecipeResponse>> getRecipes(
            @RequestParam(required = false) FoodRecipeType type,
            @RequestParam(required = false) String search
    ) {
        return new ApiResponse<>(foodPlanningService.getRecipes(type, search));
    }

    @GetMapping("/recipes/{id}")
    public ApiResponse<FoodRecipeResponse> getRecipe(@PathVariable Long id) {
        return new ApiResponse<>(foodPlanningService.getRecipe(id));
    }

    @PostMapping("/recipes")
    @PreAuthorize("hasAnyRole('ADMIN','PARENT')")
    public ApiResponse<FoodRecipeResponse> createRecipe(@Valid @RequestBody CreateRecipeRequest request) {
        return new ApiResponse<>(foodPlanningService.createRecipe(request));
    }

    @PutMapping("/recipes/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','PARENT')")
    public ApiResponse<FoodRecipeResponse> updateRecipe(@PathVariable Long id, @Valid @RequestBody UpdateRecipeRequest request) {
        return new ApiResponse<>(foodPlanningService.updateRecipe(id, request));
    }

    @DeleteMapping("/recipes/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','PARENT')")
    public ApiResponse<String> deleteRecipe(@PathVariable Long id) {
        foodPlanningService.deleteRecipe(id);
        return new ApiResponse<>("Recipe deleted");
    }

    @GetMapping("/calendar/week")
    public ApiResponse<FoodWeekResponse> getWeek(@RequestParam(required = false) LocalDate from) {
        return new ApiResponse<>(foodPlanningService.getWeek(from == null ? LocalDate.now() : from));
    }

    @PostMapping("/calendar")
    @PreAuthorize("hasAnyRole('ADMIN','PARENT')")
    public ApiResponse<?> createCalendarEntry(@Valid @RequestBody CreateFoodCalendarRequest request) {
        return new ApiResponse<>(foodPlanningService.createCalendarEntry(request));
    }

    @PatchMapping("/calendar/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','PARENT')")
    public ApiResponse<?> moveCalendarEntry(@PathVariable Long id, @Valid @RequestBody UpdateFoodCalendarRequest request) {
        return new ApiResponse<>(foodPlanningService.moveCalendarEntry(id, request));
    }

    @DeleteMapping("/calendar/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','PARENT')")
    public ApiResponse<?> deleteCalendarEntry(@PathVariable Long id) {
        foodPlanningService.deleteCalendarEntry(id);
        return new ApiResponse<>("Calendar entry deleted");
    }

    @PostMapping("/recipes/{id}/rating")
    public ApiResponse<?> saveRating(@PathVariable Long id, @Valid @RequestBody SaveFoodRatingRequest request) {
        return new ApiResponse<>(foodPlanningService.saveRating(id, request));
    }
}
