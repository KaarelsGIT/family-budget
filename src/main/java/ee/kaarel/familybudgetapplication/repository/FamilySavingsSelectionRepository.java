package ee.kaarel.familybudgetapplication.repository;

import ee.kaarel.familybudgetapplication.model.FamilySavingsSelection;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FamilySavingsSelectionRepository extends JpaRepository<FamilySavingsSelection, Long> {
    Optional<FamilySavingsSelection> findByFamilyId(Long familyId);
}
