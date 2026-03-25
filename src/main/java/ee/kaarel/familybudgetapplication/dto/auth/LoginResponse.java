package ee.kaarel.familybudgetapplication.dto.auth;

import ee.kaarel.familybudgetapplication.model.Role;
import ee.kaarel.familybudgetapplication.model.UserStatus;

public record LoginResponse(
        Long id,
        String username,
        Role role,
        UserStatus status,
        String authType
) {
}
