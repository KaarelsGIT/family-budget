package ee.kaarel.familybudgetapplication.repository;

import ee.kaarel.familybudgetapplication.model.Account;
import ee.kaarel.familybudgetapplication.model.Category;
import ee.kaarel.familybudgetapplication.model.Transaction;
import ee.kaarel.familybudgetapplication.model.TransactionType;
import ee.kaarel.familybudgetapplication.dto.statistics.YearlyStatisticsRow;
import ee.kaarel.familybudgetapplication.model.User;
import ee.kaarel.familybudgetapplication.model.Role;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TransactionRepository extends JpaRepository<Transaction, Long>, JpaSpecificationExecutor<Transaction> {

    boolean existsByCategory(Category category);

    @Query("""
            select new ee.kaarel.familybudgetapplication.dto.statistics.YearlyStatisticsRow(
                month(t.transactionDate),
                t.type,
                fromAccount.id,
                fromAccount.name,
                fromAccount.type,
                toAccount.id,
                toAccount.name,
                toAccount.type,
                parentCategory.id,
                parentCategory.name,
                category.id,
                category.name,
                sum(t.amount),
                count(t)
            )
            from Transaction t
            left join t.fromAccount fromAccount
            left join t.toAccount toAccount
            left join t.category category
            left join category.parentCategory parentCategory
            where year(t.transactionDate) = :year
            and (:month is null or month(t.transactionDate) = :month)
            and (:userId is null or t.createdBy.id = :userId)
            and (:userType is null or t.createdBy.role = :userType)
            and (:accountId is null or fromAccount.id = :accountId or toAccount.id = :accountId)
            and (fromAccount.id in :visibleAccountIds or toAccount.id in :visibleAccountIds)
            group by
                month(t.transactionDate),
                t.type,
                fromAccount.id,
                fromAccount.name,
                fromAccount.type,
                toAccount.id,
                toAccount.name,
                toAccount.type,
                parentCategory.id,
                parentCategory.name,
                category.id,
                category.name
            """)
    List<YearlyStatisticsRow> findYearlyStatisticsRows(
            @Param("year") int year,
            @Param("month") Integer month,
            @Param("userId") Long userId,
            @Param("userType") Role userType,
            @Param("accountId") Long accountId,
            @Param("visibleAccountIds") Collection<Long> visibleAccountIds
    );

    @Query("""
            select coalesce(sum(
                case
                    when t.toAccount = :account then t.amount
                    when t.fromAccount = :account then -t.amount
                    else 0
                end
            ), 0)
            from Transaction t
            where t.toAccount = :account or t.fromAccount = :account
            """)
    BigDecimal calculateBalance(@Param("account") Account account);

    @Query(value = """
            select cast(coalesce(a.initial_balance, cast(0 as decimal(19,4))) as decimal(19,4))
                 + coalesce((
                    select cast(sum(
                        case
                            when t.to_account_id = a.id then t.amount
                            when t.from_account_id = a.id then -t.amount
                            else 0
                        end
                    ) as decimal(19,4))
                    from transactions t
                    where t.to_account_id = a.id or t.from_account_id = a.id
                 ), cast(0 as decimal(19,4)))
                 + coalesce((
                    select cast(sum(ad.amount) as decimal(19,4))
                    from account_balance_adjustments ad
                    where ad.account_id = a.id
                 ), cast(0 as decimal(19,4)))
            from accounts a
            where a.id = :accountId
            """, nativeQuery = true)
    BigDecimal calculateAccurateBalance(@Param("accountId") Long accountId);

    @Query(value = """
            select
                cast(coalesce(a.initial_balance, cast(0 as decimal(19,4))) as decimal(19,4)) as initial_balance,
                t.id as event_id,
                t.created_at as event_created_at,
                t.transaction_date as event_transaction_date,
                t.type as event_type,
                t.amount as amount,
                cast(case
                    when t.to_account_id = a.id then t.amount
                    when t.from_account_id = a.id then -t.amount
                    else 0
                end as decimal(19,4)) as delta,
                'TRANSACTION' as event_kind
            from accounts a
            join transactions t on t.to_account_id = a.id or t.from_account_id = a.id
            where a.id = :accountId
            union all
            select
                cast(coalesce(a.initial_balance, cast(0 as decimal(19,4))) as decimal(19,4)) as initial_balance,
                ad.id as event_id,
                ad.created_at as event_created_at,
                null as event_transaction_date,
                'ADJUSTMENT' as event_type,
                ad.amount as amount,
                cast(ad.amount as decimal(19,4)) as delta,
                'ADJUSTMENT' as event_kind
            from accounts a
            join account_balance_adjustments ad on ad.account_id = a.id
            where a.id = :accountId
            order by event_created_at, event_id
            """, nativeQuery = true)
    List<BalanceAuditRow> findBalanceAuditRows(@Param("accountId") Long accountId);

    @Query(value = """
            select cast(coalesce(a.initial_balance, cast(0 as decimal(19,4))) as decimal(19,4))
                 + coalesce((
                    select cast(sum(x.delta) as decimal(19,4))
                    from (
                        select
                            t.created_at as created_at,
                            t.id as event_id,
                            cast(case
                                when t.to_account_id = a.id then t.amount
                                when t.from_account_id = a.id then -t.amount
                                else 0
                            end as decimal(19,4)) as delta
                        from transactions t
                        where t.to_account_id = a.id or t.from_account_id = a.id
                        union all
                        select
                            ad.created_at as created_at,
                            ad.id as event_id,
                            cast(ad.amount as decimal(19,4)) as delta
                        from account_balance_adjustments ad
                        where ad.account_id = a.id
                    ) x
                    where x.created_at < :createdAt
                       or (x.created_at = :createdAt and x.event_id <= :eventId)
                 ), cast(0 as decimal(19,4)))
            from accounts a
            where a.id = :accountId
            """, nativeQuery = true)
    BigDecimal calculateBalanceUpTo(
            @Param("accountId") Long accountId,
            @Param("createdAt") OffsetDateTime createdAt,
            @Param("eventId") Long eventId
    );

    List<Transaction> findAllByFromAccountOrToAccount(Account fromAccount, Account toAccount);

    @Modifying
    @Query("""
            delete from Transaction t
            where t.createdBy = :user
            or t.fromAccount.id in (
                select a.id
                from Account a
                where a.owner = :user
            )
            or t.toAccount.id in (
                select a.id
                from Account a
                where a.owner = :user
            )
            """)
    void deleteAllLinkedToUser(@Param("user") User user);

    @Query("""
            select distinct t.category.id
            from Transaction t
            where t.category is not null
            and t.category.id in :categoryIds
            and year(t.createdAt) = :year
            and month(t.createdAt) = :month
            """)
    List<Long> findPaidCategoryIdsForMonth(
            @Param("categoryIds") Collection<Long> categoryIds,
            @Param("year") int year,
            @Param("month") int month
    );

    @Query("""
            select distinct t.category.id
            from Transaction t
            where t.category is not null
            and t.category.id in :categoryIds
            and t.transactionDate is not null
            and year(t.transactionDate) = :year
            and month(t.transactionDate) = :month
            """)
    List<Long> findPaidCategoryIdsByTransactionDateForMonth(
            @Param("categoryIds") Collection<Long> categoryIds,
            @Param("year") int year,
            @Param("month") int month
    );

    @Query("""
            select count(t) > 0
            from Transaction t
            where t.category.id = :categoryId
            and t.transactionDate is not null
            and year(t.transactionDate) = :year
            and month(t.transactionDate) = :month
            and t.createdAt >= :createdAfter
            """)
    boolean existsPaidCategoryAfterCreationInMonth(
            @Param("categoryId") Long categoryId,
            @Param("year") int year,
            @Param("month") int month,
            @Param("createdAfter") OffsetDateTime createdAfter
    );

    List<Transaction> findAllByCreatedByAndCategoryAndTypeAndTransactionDateBetween(
            User createdBy,
            Category category,
            TransactionType type,
            LocalDate from,
            LocalDate to
    );

    List<Transaction> findAllByCreatedByAndCreatedAtAfterOrderByCreatedAtDesc(
            User createdBy,
            OffsetDateTime createdAt
    );

    interface BalanceAuditRow {
        BigDecimal getInitialBalance();
        Long getEventId();
        OffsetDateTime getEventCreatedAt();
        LocalDate getEventTransactionDate();
        String getEventType();
        BigDecimal getAmount();
        BigDecimal getDelta();
        String getEventKind();
    }
}
