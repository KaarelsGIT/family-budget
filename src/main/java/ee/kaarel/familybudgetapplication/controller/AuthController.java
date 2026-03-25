package ee.kaarel.familybudgetapplication.controller;

import ee.kaarel.familybudgetapplication.dto.auth.ActivateUserRequest;
import ee.kaarel.familybudgetapplication.dto.auth.LoginRequest;
import ee.kaarel.familybudgetapplication.dto.common.ApiResponse;
import ee.kaarel.familybudgetapplication.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public ApiResponse<?> login(@Valid @RequestBody LoginRequest request) {
        return new ApiResponse<>(authService.login(request));
    }

    @PostMapping("/activate")
    public ApiResponse<?> activate(@Valid @RequestBody ActivateUserRequest request) {
        return new ApiResponse<>(authService.activate(request));
    }
}
