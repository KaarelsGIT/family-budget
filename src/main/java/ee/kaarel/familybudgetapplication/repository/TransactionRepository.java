package ee.kaarel.familybudgetapplication.repository;

import ee.kaarel.familybudgetapplication.model.Account;
import ee.kaarel.familybudgetapplication.model.Category;
import ee.kaarel.familybudgetapplication.model.Transaction;
import ee.kaarel.familybudgetapplication.model.TransactionType;
import ee.kaarel.familybudgetapplication.dto.statistics.YearlyStatisticsRow;
import ee.kaarel.familybudgetapplication.model.User;
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
            and (:userId is null or t.createdBy.id = :userId)
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
            @Param("userId") Long userId,
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
}
