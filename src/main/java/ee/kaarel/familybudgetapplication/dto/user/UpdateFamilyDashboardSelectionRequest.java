package ee.kaarel.familybudgetapplication.dto.user;

import jakarta.validation.constraints.NotNull;
import java.util.List;

public record UpdateFamilyDashboardSelectionRequest(
        @NotNull List<Long> selectedUserIds
) {
}
