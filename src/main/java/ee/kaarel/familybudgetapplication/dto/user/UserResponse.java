package ee.kaarel.familybudgetapplication.dto.user;

import ee.kaarel.familybudgetapplication.model.Role;
import ee.kaarel.familybudgetapplication.model.UserStatus;

public record UserResponse(
        Long id,
        String username,
        Role role,
        UserStatus status,
        String preferredLanguage,
        Long defaultMainAccountId
) {
}
