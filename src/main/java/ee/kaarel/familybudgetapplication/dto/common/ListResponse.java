package ee.kaarel.familybudgetapplication.dto.common;

import java.util.List;

public record ListResponse<T>(List<T> data, long total) {
}
