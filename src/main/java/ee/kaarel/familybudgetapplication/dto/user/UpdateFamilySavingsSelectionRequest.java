package ee.kaarel.familybudgetapplication.dto.user;

import jakarta.validation.constraints.NotNull;
import java.util.List;

public record UpdateFamilySavingsSelectionRequest(
        @NotNull List<Long> selectedAccountIds
) {
}
