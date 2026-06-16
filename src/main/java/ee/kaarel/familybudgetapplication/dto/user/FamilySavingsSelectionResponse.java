package ee.kaarel.familybudgetapplication.dto.user;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record FamilySavingsSelectionResponse(
        List<Long> selectedAccountIds,
        BigDecimal targetAmount,
        LocalDate targetDate
) {
}
