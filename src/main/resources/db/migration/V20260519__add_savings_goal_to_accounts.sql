ALTER TABLE accounts
    ADD COLUMN target_amount DECIMAL(19,4) NULL,
    ADD COLUMN target_date VARCHAR(10) NULL;
