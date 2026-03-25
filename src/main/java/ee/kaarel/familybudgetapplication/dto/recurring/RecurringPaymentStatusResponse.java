package ee.kaarel.familybudgetapplication.dto.recurring;

public record RecurringPaymentStatusResponse(
        Long id,
        Integer year,
        Integer month,
        boolean paid
) {
}
