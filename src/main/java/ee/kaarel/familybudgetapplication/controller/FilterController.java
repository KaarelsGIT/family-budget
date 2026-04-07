package ee.kaarel.familybudgetapplication.controller;

import ee.kaarel.familybudgetapplication.dto.common.ApiResponse;
import ee.kaarel.familybudgetapplication.service.UserService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/filters")
public class FilterController {

    private final UserService userService;

    public FilterController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/users")
    public ApiResponse<?> getFilterUsers() {
        return new ApiResponse<>(userService.getFilterUsers());
    }
}
