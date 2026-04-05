package ee.kaarel.familybudgetapplication.controller;

import ee.kaarel.familybudgetapplication.appConfig.ApiException;
import ee.kaarel.familybudgetapplication.dto.statistics.YearlyStatisticsResponse;
import ee.kaarel.familybudgetapplication.service.StatisticsService;
import java.time.LocalDate;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/statistics")
public class StatisticsController {

    private final StatisticsService statisticsService;

    public StatisticsController(StatisticsService statisticsService) {
        this.statisticsService = statisticsService;
    }

    @GetMapping("/yearly")
    public YearlyStatisticsResponse getYearly(
            @RequestParam Map<String, String> params
    ) {
        Integer year = parseInteger(firstNonNull(params, "year"));
        Long userId = parseLong(firstNonNull(params, "user_id", "userId"));
        Long accountId = parseLong(firstNonNull(params, "account_id", "accountId"));
        return statisticsService.getYearly(year == null ? LocalDate.now().getYear() : year, userId, accountId);
    }

    private String firstNonNull(Map<String, String> params, String... keys) {
        for (String key : keys) {
            String value = params.get(key);
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private Integer parseInteger(String value) {
        if (value == null) {
            return null;
        }
        try {
            return Integer.valueOf(value);
        } catch (NumberFormatException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Invalid year");
        }
    }

    private Long parseLong(String value) {
        if (value == null) {
            return null;
        }
        try {
            return Long.valueOf(value);
        } catch (NumberFormatException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Invalid filter value");
        }
    }
}
