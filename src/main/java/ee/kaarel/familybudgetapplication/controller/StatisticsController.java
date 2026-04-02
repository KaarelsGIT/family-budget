package ee.kaarel.familybudgetapplication.controller;

import ee.kaarel.familybudgetapplication.dto.statistics.YearlyStatisticsResponse;
import ee.kaarel.familybudgetapplication.service.StatisticsService;
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
            @RequestParam int year,
            @RequestParam(required = false) Long accountId
    ) {
        return statisticsService.getYearly(year, accountId);
    }
}
