-- Safe one-way migration from legacy recurring_payments to recurring_transactions.
-- Run after the application code has been deployed with the new RecurringTransaction-based flow.

START TRANSACTION;

SET @today := CURRENT_DATE;

INSERT INTO recurring_transactions (
    user_id,
    category_id,
    account_id,
    amount,
    comment,
    due_day_of_month,
    next_due_date,
    active
)
SELECT
    rp.owner_id AS user_id,
    rp.category_id,
    NULL AS account_id,
    rp.amount,
    rp.name AS comment,
    rp.due_day AS due_day_of_month,
    CASE
        WHEN LEAST(rp.due_day, DAY(LAST_DAY(@today))) >= DAY(@today) THEN
            DATE_ADD(DATE_FORMAT(@today, '%Y-%m-01'), INTERVAL LEAST(rp.due_day, DAY(LAST_DAY(@today))) - 1 DAY)
        ELSE
            DATE_ADD(
                DATE_FORMAT(DATE_ADD(@today, INTERVAL 1 MONTH), '%Y-%m-01'),
                INTERVAL LEAST(rp.due_day, DAY(LAST_DAY(DATE_ADD(@today, INTERVAL 1 MONTH)))) - 1 DAY
            )
    END AS next_due_date,
    rp.active AS active
FROM recurring_payments rp
JOIN categories c ON c.id = rp.category_id
WHERE rp.amount > 0
  AND rp.due_day BETWEEN 1 AND 31
  AND rp.category_id IS NOT NULL
  AND NOT EXISTS (
      SELECT 1
      FROM recurring_transactions rt
      WHERE rt.user_id = rp.owner_id
        AND rt.category_id = rp.category_id
        AND rt.active = 1
  );

COMMIT;

DROP TABLE IF EXISTS recurring_payment_statuses;
DROP TABLE IF EXISTS recurring_payments;
ALTER TABLE categories DROP COLUMN IF EXISTS is_recurring;
ALTER TABLE categories DROP COLUMN IF EXISTS due_day_of_month;
