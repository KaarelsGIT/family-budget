package ee.kaarel.familybudgetapplication.repository;

import ee.kaarel.familybudgetapplication.model.FoodRating;
import java.util.Optional;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FoodRatingRepository extends JpaRepository<FoodRating, Long> {
    Optional<FoodRating> findByUserIdAndRecipeId(Long userId, Long recipeId);
    long countByRecipeId(Long recipeId);
    @Query("select coalesce(avg(r.rating), 0) from FoodRating r where r.recipe.id = :recipeId")
    Double getAverageRatingByRecipeId(@Param("recipeId") Long recipeId);
}
