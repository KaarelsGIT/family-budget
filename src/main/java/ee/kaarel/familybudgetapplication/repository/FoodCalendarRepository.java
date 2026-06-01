package ee.kaarel.familybudgetapplication.repository;

import ee.kaarel.familybudgetapplication.model.FoodCalendarEntry;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FoodCalendarRepository extends JpaRepository<FoodCalendarEntry, Long> {
    List<FoodCalendarEntry> findAllByDateBetweenOrderByDateAsc(LocalDate from, LocalDate to);
    void deleteByRecipeId(Long recipeId);
}
