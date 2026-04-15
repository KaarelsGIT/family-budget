ALTER TABLE transaction_reminders
    ADD COLUMN IF NOT EXISTS transaction_id BIGINT NULL;

ALTER TABLE transaction_reminders
    ADD CONSTRAINT fk_transaction_reminders_transaction
        FOREIGN KEY (transaction_id) REFERENCES transactions (id);

CREATE UNIQUE INDEX ux_transaction_reminders_transaction_id
    ON transaction_reminders (transaction_id);
