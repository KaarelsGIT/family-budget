-- Increase money precision so backend calculations can preserve intermediate precision.
ALTER TABLE transactions MODIFY amount DECIMAL(19,4) NOT NULL;
ALTER TABLE account_balance_adjustments MODIFY amount DECIMAL(19,4) NOT NULL;
ALTER TABLE accounts MODIFY initial_balance DECIMAL(19,4) NULL;
ALTER TABLE recurring_transactions MODIFY amount DECIMAL(19,4) NOT NULL;
