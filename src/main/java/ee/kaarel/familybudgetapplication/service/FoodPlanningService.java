package ee.kaarel.familybudgetapplication.service;

import ee.kaarel.familybudgetapplication.appConfig.ApiException;
import ee.kaarel.familybudgetapplication.dto.food.CreateFoodCalendarRequest;
import ee.kaarel.familybudgetapplication.dto.food.CreateIngredientRequest;
import ee.kaarel.familybudgetapplication.dto.food.CreateRecipeRequest;
import ee.kaarel.familybudgetapplication.dto.food.FoodCalendarResponse;
import ee.kaarel.familybudgetapplication.dto.food.FoodIngredientResponse;
import ee.kaarel.familybudgetapplication.dto.food.FoodRatingResponse;
import ee.kaarel.familybudgetapplication.dto.food.FoodRecipeResponse;
import ee.kaarel.familybudgetapplication.dto.food.FoodWeekResponse;
import ee.kaarel.familybudgetapplication.dto.food.SaveFoodRatingRequest;
import ee.kaarel.familybudgetapplication.dto.food.UpdateFoodCalendarRequest;
import ee.kaarel.familybudgetapplication.dto.food.UpdateRecipeRequest;
import ee.kaarel.familybudgetapplication.model.FoodCalendarEntry;
import ee.kaarel.familybudgetapplication.model.FoodRating;
import ee.kaarel.familybudgetapplication.model.FoodRecipeType;
import ee.kaarel.familybudgetapplication.model.Ingredient;
import ee.kaarel.familybudgetapplication.model.Recipe;
import ee.kaarel.familybudgetapplication.model.Role;
import ee.kaarel.familybudgetapplication.model.User;
import ee.kaarel.familybudgetapplication.repository.FoodCalendarRepository;
import ee.kaarel.familybudgetapplication.repository.FoodRatingRepository;
import ee.kaarel.familybudgetapplication.repository.RecipeRepository;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FoodPlanningService {
    private final RecipeRepository recipeRepository;
    private final FoodCalendarRepository foodCalendarRepository;
    private final FoodRatingRepository foodRatingRepository;
    private final CurrentUserService currentUserService;

    public FoodPlanningService(
            RecipeRepository recipeRepository,
            FoodCalendarRepository foodCalendarRepository,
            FoodRatingRepository foodRatingRepository,
            CurrentUserService currentUserService
    ) {
        this.recipeRepository = recipeRepository;
        this.foodCalendarRepository = foodCalendarRepository;
        this.foodRatingRepository = foodRatingRepository;
        this.currentUserService = currentUserService;
    }

    @Transactional(readOnly = true)
    public List<FoodRecipeResponse> getRecipes(FoodRecipeType type, String search) {
        User currentUser = currentUserService.getCurrentUser();
        String normalizedSearch = search == null ? null : search.trim().toLowerCase(Locale.ROOT);
        return recipeRepository.findAll().stream()
                .filter(recipe -> type == null || recipe.getType() == type)
                .filter(recipe -> normalizedSearch == null || recipe.getName().toLowerCase(Locale.ROOT).contains(normalizedSearch))
                .sorted(Comparator.comparing(Recipe::getName, String.CASE_INSENSITIVE_ORDER))
                .map(recipe -> toRecipeResponse(recipe, currentUser.getId()))
                .toList();
    }

    @Transactional(readOnly = true)
    public FoodRecipeResponse getRecipe(Long id) {
        User currentUser = currentUserService.getCurrentUser();
        return toRecipeResponse(getRecipeEntity(id), currentUser.getId());
    }

    @Transactional
    public FoodRecipeResponse createRecipe(CreateRecipeRequest request) {
        Recipe recipe = new Recipe();
        applyRecipe(recipe, request.name(), request.type(), request.instructions(), request.baseServings(), request.cost(), request.ingredients());
        return toRecipeResponse(recipeRepository.save(recipe), currentUserService.getCurrentUser().getId());
    }

    @Transactional
    public FoodRecipeResponse updateRecipe(Long id, UpdateRecipeRequest request) {
        Recipe recipe = getRecipeEntity(id);
        applyRecipe(
                recipe,
                request.name() != null ? request.name() : recipe.getName(),
                request.type() != null ? request.type() : recipe.getType(),
                request.instructions() != null ? request.instructions() : recipe.getInstructions(),
                request.baseServings() != null ? request.baseServings() : recipe.getBaseServings(),
                request.cost() != null ? request.cost() : recipe.getCost(),
                request.ingredients() != null ? request.ingredients() : toIngredientRequests(recipe.getIngredients())
        );
        return toRecipeResponse(recipeRepository.save(recipe), currentUserService.getCurrentUser().getId());
    }

    @Transactional
    public void deleteRecipe(Long id) {
        recipeRepository.delete(getRecipeEntity(id));
    }

    @Transactional(readOnly = true)
    public FoodWeekResponse getWeek(LocalDate from) {
        LocalDate weekStart = from.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate weekEnd = weekStart.plusDays(6);
        User currentUser = currentUserService.getCurrentUser();
        List<FoodCalendarResponse> entries = foodCalendarRepository.findAllByDateBetweenOrderByDateAsc(weekStart, weekEnd).stream()
                .map(entry -> toCalendarResponse(entry, currentUser.getId()))
                .toList();
        return new FoodWeekResponse(weekStart, entries);
    }

    @Transactional
    public FoodCalendarResponse createCalendarEntry(CreateFoodCalendarRequest request) {
        FoodCalendarEntry entry = new FoodCalendarEntry();
        entry.setDate(request.date());
        entry.setRecipe(getRecipeEntity(request.recipeId()));
        return toCalendarResponse(foodCalendarRepository.save(entry), currentUserService.getCurrentUser().getId());
    }

    @Transactional
    public FoodCalendarResponse moveCalendarEntry(Long id, UpdateFoodCalendarRequest request) {
        FoodCalendarEntry entry = getCalendarEntry(id);
        entry.setDate(request.date());
        return toCalendarResponse(foodCalendarRepository.save(entry), currentUserService.getCurrentUser().getId());
    }

    @Transactional
    public void deleteCalendarEntry(Long id) {
        foodCalendarRepository.delete(getCalendarEntry(id));
    }

    @Transactional
    public FoodRatingResponse saveRating(Long recipeId, SaveFoodRatingRequest request) {
        User currentUser = currentUserService.getCurrentUser();
        Recipe recipe = getRecipeEntity(recipeId);
        FoodRating rating = foodRatingRepository.findByUserIdAndRecipeId(currentUser.getId(), recipeId)
                .orElseGet(FoodRating::new);
        rating.setUser(currentUser);
        rating.setRecipe(recipe);
        rating.setRating(request.rating());
        FoodRating saved = foodRatingRepository.save(rating);
        return new FoodRatingResponse(saved.getId(), recipe.getId(), currentUser.getId(), saved.getRating());
    }

    private void applyRecipe(Recipe recipe, String name, FoodRecipeType type, String instructions, Integer baseServings, java.math.BigDecimal cost, List<CreateIngredientRequest> ingredients) {
        recipe.setName(requireText(name, "Recipe name is required"));
        recipe.setType(type == null ? throwBadRequest("Recipe type is required") : type);
        recipe.setInstructions(requireText(instructions, "Instructions are required"));
        recipe.setBaseServings(baseServings == null || baseServings <= 0 ? throwBadRequest("Base servings must be positive") : baseServings);
        recipe.setCost(cost == null ? java.math.BigDecimal.ZERO : cost);
        recipe.getIngredients().clear();
        if (ingredients != null) {
            for (CreateIngredientRequest ingredientRequest : ingredients) {
                Ingredient ingredient = new Ingredient();
                ingredient.setRecipe(recipe);
                ingredient.setName(requireText(ingredientRequest.name(), "Ingredient name is required"));
                ingredient.setBaseAmount(ingredientRequest.baseAmount());
                ingredient.setUnit(requireText(ingredientRequest.unit(), "Ingredient unit is required"));
                recipe.getIngredients().add(ingredient);
            }
        }
    }

    private List<CreateIngredientRequest> toIngredientRequests(List<Ingredient> ingredients) {
        List<CreateIngredientRequest> requests = new ArrayList<>();
        for (Ingredient ingredient : ingredients) {
            requests.add(new CreateIngredientRequest(ingredient.getName(), ingredient.getBaseAmount(), ingredient.getUnit()));
        }
        return requests;
    }

    private FoodRecipeResponse toRecipeResponse(Recipe recipe, Long currentUserId) {
        var rating = foodRatingRepository.findByUserIdAndRecipeId(currentUserId, recipe.getId()).orElse(null);
        Double average = foodRatingRepository.getAverageRatingByRecipeId(recipe.getId());
        Integer count = Math.toIntExact(foodRatingRepository.countByRecipeId(recipe.getId()));
        return new FoodRecipeResponse(
                recipe.getId(),
                recipe.getName(),
                recipe.getType(),
                recipe.getInstructions(),
                recipe.getBaseServings(),
                recipe.getCost(),
                recipe.getIngredients().stream().map(this::toIngredientResponse).toList(),
                average,
                count,
                rating == null ? null : rating.getRating()
        );
    }

    private FoodIngredientResponse toIngredientResponse(Ingredient ingredient) {
        return new FoodIngredientResponse(ingredient.getId(), ingredient.getName(), ingredient.getBaseAmount(), ingredient.getUnit());
    }

    private FoodCalendarResponse toCalendarResponse(FoodCalendarEntry entry, Long currentUserId) {
        return new FoodCalendarResponse(entry.getId(), entry.getDate(), toRecipeResponse(entry.getRecipe(), currentUserId));
    }

    private Recipe getRecipeEntity(Long id) {
        return recipeRepository.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Recipe not found"));
    }

    private FoodCalendarEntry getCalendarEntry(Long id) {
        return foodCalendarRepository.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Calendar entry not found"));
    }

    private String requireText(String value, String message) {
        if (value == null || value.trim().isBlank()) {
            throwBadRequest(message);
        }
        return value.trim();
    }

    private <T> T throwBadRequest(String message) {
        throw new ApiException(HttpStatus.BAD_REQUEST, message);
    }
}
