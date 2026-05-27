package ee.kaarel.familybudgetapplication.dto.transaction;

import java.util.List;

public record TransactionDuplicateCheckResponse(
        boolean duplicate,
        List<TransactionResponse> matches
) {
}
