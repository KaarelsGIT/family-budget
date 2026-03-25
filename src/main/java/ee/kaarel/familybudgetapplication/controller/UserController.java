package ee.kaarel.familybudgetapplication.controller;

import ee.kaarel.familybudgetapplication.dto.common.ApiResponse;
import ee.kaarel.familybudgetapplication.dto.user.CreateUserRequest;
import ee.kaarel.familybudgetapplication.dto.user.UpdateUserRequest;
import ee.kaarel.familybudgetapplication.service.UserService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping
    public ApiResponse<?> createUser(@Valid @RequestBody CreateUserRequest request) {
        return new ApiResponse<>(userService.createUser(request));
    }

    @PutMapping("/{id}")
    public ApiResponse<?> updateUser(@PathVariable Long id, @Valid @RequestBody UpdateUserRequest request) {
        return new ApiResponse<>(userService.updateUser(id, request));
    }
}
