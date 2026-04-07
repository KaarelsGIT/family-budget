package ee.kaarel.familybudgetapplication.controller;

import ee.kaarel.familybudgetapplication.dto.common.ApiResponse;
import ee.kaarel.familybudgetapplication.service.UserService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/transfers")
public class TransferController {

    private final UserService userService;

    public TransferController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/targets")
    public ApiResponse<?> getTransferTargets() {
        return new ApiResponse<>(userService.getTransferTargets());
    }
}
