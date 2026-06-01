package ee.kaarel.familybudgetapplication.repository;

import ee.kaarel.familybudgetapplication.model.Recipe;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RecipeRepository extends JpaRepository<Recipe, Long> {
}
