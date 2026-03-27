package ee.kaarel.familybudgetapplication.dto.user;

import ee.kaarel.familybudgetapplication.model.Role;
import jakarta.validation.constraints.Size;

public record UpdateUserRequest(
        @Size(max = 100) String username,
        @Size(min = 4, max = 100) String password,
        Role role,
        @Size(min = 2, max = 5) String preferredLanguage
) {
}
