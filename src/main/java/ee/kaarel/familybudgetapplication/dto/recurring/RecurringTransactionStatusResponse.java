package ee.kaarel.familybudgetapplication.dto.recurring;

public record RecurringTransactionStatusResponse(
        Long id,
        Integer year,
        Integer month,
        boolean paid,
        boolean urgent
) {
}
