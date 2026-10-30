package ir.barnamemali.app.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

data class AccountWithBalance(@Embedded val account: Account, val balance: Long)

data class PersonWithBalance(@Embedded val person: Person, val balance: Long)

data class KeyTotal(val key: Long?, val total: Long)

data class MonthTotal(val month: Long, val total: Long)

@Dao
interface FinanceDao {
    // Accounts
    @Query(
        """SELECT a.*, a.openingBalance + COALESCE((SELECT SUM(t.amount) FROM Txn t WHERE t.accountId = a.id), 0) AS balance
           FROM Account a WHERE a.archived = 0 ORDER BY a.id"""
    )
    fun accountsWithBalance(): Flow<List<AccountWithBalance>>

    @Query("SELECT * FROM Account WHERE archived = 0 ORDER BY id")
    suspend fun accounts(): List<Account>

    @Query("SELECT * FROM Account WHERE id = :id")
    suspend fun account(id: Long): Account?

    @Insert
    suspend fun insertAccount(a: Account): Long

    @Update
    suspend fun updateAccount(a: Account)

    @Query("SELECT COALESCE(SUM(amount), 0) FROM Txn WHERE accountId = :accountId AND time <= :time")
    suspend fun sumUntil(accountId: Long, time: Long): Long

    @Query("SELECT COUNT(*) FROM Txn WHERE accountId = :accountId AND time > :time AND smsBalance IS NOT NULL")
    suspend fun verifiedAfter(accountId: Long, time: Long): Int

    // Transactions
    @Insert
    suspend fun insertTxn(t: Txn): Long

    @Update
    suspend fun updateTxn(t: Txn)

    @Delete
    suspend fun deleteTxn(t: Txn)

    @Query("SELECT * FROM Txn WHERE id = :id")
    suspend fun txn(id: Long): Txn?

    @Query("SELECT * FROM Txn ORDER BY time DESC LIMIT :limit")
    fun recentTxns(limit: Int): Flow<List<Txn>>

    @Query("SELECT * FROM Txn WHERE time >= :from AND time < :to ORDER BY time DESC")
    fun txnsBetween(from: Long, to: Long): Flow<List<Txn>>

    @Query("SELECT * FROM Txn WHERE kind = 'ADJUSTMENT' ORDER BY time DESC")
    fun adjustments(): Flow<List<Txn>>

    @Query("SELECT amount FROM Txn WHERE accountId = :accountId AND source = 'MANUAL' AND time >= :from")
    suspend fun recentManualAmounts(accountId: Long, from: Long): List<Long>

    @Query(
        """SELECT memberId AS `key`, -SUM(amount) AS total FROM Txn
           WHERE kind = 'EXPENSE' AND time >= :from AND time < :to GROUP BY memberId"""
    )
    fun expenseByMember(from: Long, to: Long): Flow<List<KeyTotal>>

    @Query(
        """SELECT categoryId AS `key`, -SUM(amount) AS total FROM Txn
           WHERE kind = 'EXPENSE' AND time >= :from AND time < :to GROUP BY categoryId"""
    )
    fun expenseByCategory(from: Long, to: Long): Flow<List<KeyTotal>>

    @Query(
        """SELECT categoryId AS `key`, -SUM(amount) AS total FROM Txn
           WHERE kind = 'EXPENSE' AND time >= :from AND time < :to GROUP BY categoryId"""
    )
    suspend fun expenseByCategoryOnce(from: Long, to: Long): List<KeyTotal>

    @Query("SELECT COALESCE(-SUM(amount), 0) FROM Txn WHERE kind = 'EXPENSE' AND time >= :from AND time < :to")
    suspend fun expenseTotal(from: Long, to: Long): Long

    @Query("SELECT COALESCE(SUM(amount), 0) FROM Txn WHERE kind = 'INCOME' AND time >= :from AND time < :to")
    suspend fun incomeTotal(from: Long, to: Long): Long

    @Query(
        """SELECT COALESCE(-SUM(amount), 0) FROM Txn
           WHERE kind = 'EXPENSE' AND memberId = :memberId AND time >= :from AND time < :to"""
    )
    suspend fun memberExpense(memberId: Long, from: Long, to: Long): Long

    @Query(
        """SELECT COALESCE(-SUM(amount), 0) FROM Txn
           WHERE kind = 'EXPENSE' AND categoryId = :categoryId AND time >= :from AND time < :to"""
    )
    suspend fun categoryExpense(categoryId: Long, from: Long, to: Long): Long

    // Members and categories
    @Query("SELECT * FROM Member ORDER BY sortOrder, id")
    fun members(): Flow<List<Member>>

    @Query("SELECT * FROM Member ORDER BY sortOrder, id")
    suspend fun membersOnce(): List<Member>

    @Upsert
    suspend fun upsertMember(m: Member): Long

    @Delete
    suspend fun deleteMember(m: Member)

    @Query("SELECT * FROM Category ORDER BY sortOrder, id")
    fun categories(): Flow<List<Category>>

    @Query("SELECT * FROM Category ORDER BY sortOrder, id")
    suspend fun categoriesOnce(): List<Category>

    @Upsert
    suspend fun upsertCategory(c: Category): Long

    @Delete
    suspend fun deleteCategory(c: Category)

    @Query("SELECT COUNT(*) FROM Category")
    suspend fun categoryCount(): Int

    // People and debts
    @Query(
        """SELECT p.*, COALESCE((SELECT SUM(d.amount) FROM DebtEntry d WHERE d.personId = p.id), 0) AS balance
           FROM Person p ORDER BY p.name"""
    )
    fun peopleWithBalance(): Flow<List<PersonWithBalance>>

    @Query("SELECT * FROM Person ORDER BY name")
    suspend fun peopleOnce(): List<Person>

    @Upsert
    suspend fun upsertPerson(p: Person): Long

    @Delete
    suspend fun deletePerson(p: Person)

    @Query("SELECT * FROM DebtEntry WHERE personId = :personId ORDER BY time DESC")
    fun debtEntries(personId: Long): Flow<List<DebtEntry>>

    @Query("SELECT * FROM DebtEntry WHERE dueEpochDay IS NOT NULL")
    suspend fun debtEntriesWithDue(): List<DebtEntry>

    @Insert
    suspend fun insertDebt(d: DebtEntry): Long

    @Delete
    suspend fun deleteDebt(d: DebtEntry)

    // Loans
    @Query("SELECT * FROM Loan ORDER BY closed, id DESC")
    fun loans(): Flow<List<Loan>>

    @Query("SELECT * FROM Loan WHERE id = :id")
    suspend fun loan(id: Long): Loan?

    @Insert
    suspend fun insertLoan(l: Loan): Long

    @Update
    suspend fun updateLoan(l: Loan)

    @Delete
    suspend fun deleteLoan(l: Loan)

    @Insert
    suspend fun insertInstallments(items: List<Installment>)

    @Update
    suspend fun updateInstallment(i: Installment)

    @Query("DELETE FROM Installment WHERE loanId = :loanId")
    suspend fun deleteInstallments(loanId: Long)

    @Query("SELECT * FROM Installment ORDER BY dueEpochDay")
    fun installments(): Flow<List<Installment>>

    @Query("SELECT * FROM Installment WHERE paidTxnId IS NULL AND dueEpochDay <= :untilDay ORDER BY dueEpochDay")
    suspend fun unpaidInstallmentsUntil(untilDay: Long): List<Installment>

    // Cheques
    @Query("SELECT * FROM Cheque ORDER BY CASE status WHEN 'PENDING' THEN 0 ELSE 1 END, dueEpochDay")
    fun cheques(): Flow<List<Cheque>>

    @Query("SELECT * FROM Cheque WHERE status = 'PENDING' AND dueEpochDay <= :untilDay ORDER BY dueEpochDay")
    suspend fun pendingChequesUntil(untilDay: Long): List<Cheque>

    @Insert
    suspend fun insertCheque(c: Cheque): Long

    @Update
    suspend fun updateCheque(c: Cheque)

    @Delete
    suspend fun deleteCheque(c: Cheque)

    // Bank SMS
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertSms(s: PendingSms): Long

    @Update
    suspend fun updateSms(s: PendingSms)

    @Query("SELECT * FROM PendingSms WHERE status = 'PENDING' ORDER BY time")
    fun pendingSms(): Flow<List<PendingSms>>

    @Query("SELECT * FROM PendingSms WHERE status = 'PENDING' ORDER BY time")
    suspend fun pendingSmsOnce(): List<PendingSms>

    @Query("SELECT * FROM PendingSms WHERE id = :id")
    suspend fun sms(id: Long): PendingSms?

    @Query("SELECT * FROM PendingSms WHERE status = 'PENDING' AND accountId = :accountId AND time < :time")
    suspend fun olderPendingSms(accountId: Long, time: Long): List<PendingSms>

    @Query("SELECT * FROM MerchantRule WHERE `key` = :key")
    suspend fun rule(key: String): MerchantRule?

    @Upsert
    suspend fun upsertRule(r: MerchantRule)

    // Recurring
    @Query("SELECT * FROM Recurring ORDER BY nextDueEpochDay")
    fun recurring(): Flow<List<Recurring>>

    @Query("SELECT * FROM Recurring WHERE active = 1 AND nextDueEpochDay <= :untilDay")
    suspend fun recurringDueUntil(untilDay: Long): List<Recurring>

    @Upsert
    suspend fun upsertRecurring(r: Recurring): Long

    @Delete
    suspend fun deleteRecurring(r: Recurring)

    // Backup
    @Query("SELECT * FROM Account") suspend fun allAccounts(): List<Account>
    @Query("SELECT * FROM Member") suspend fun allMembers(): List<Member>
    @Query("SELECT * FROM Category") suspend fun allCategories(): List<Category>
    @Query("SELECT * FROM Txn ORDER BY time") suspend fun allTxns(): List<Txn>
    @Query("SELECT * FROM Person") suspend fun allPeople(): List<Person>
    @Query("SELECT * FROM DebtEntry") suspend fun allDebts(): List<DebtEntry>
    @Query("SELECT * FROM Loan") suspend fun allLoans(): List<Loan>
    @Query("SELECT * FROM Installment") suspend fun allInstallments(): List<Installment>
    @Query("SELECT * FROM Cheque") suspend fun allCheques(): List<Cheque>
    @Query("SELECT * FROM PendingSms") suspend fun allSms(): List<PendingSms>
    @Query("SELECT * FROM MerchantRule") suspend fun allRules(): List<MerchantRule>
    @Query("SELECT * FROM Recurring") suspend fun allRecurring(): List<Recurring>

    @Insert suspend fun insertAccounts(x: List<Account>)
    @Insert suspend fun insertMembers(x: List<Member>)
    @Insert suspend fun insertCategories(x: List<Category>)
    @Insert suspend fun insertTxns(x: List<Txn>)
    @Insert suspend fun insertPeople(x: List<Person>)
    @Insert suspend fun insertDebts(x: List<DebtEntry>)
    @Insert suspend fun insertLoans(x: List<Loan>)
    @Insert suspend fun insertSmsList(x: List<PendingSms>)
    @Insert suspend fun insertRules(x: List<MerchantRule>)
    @Insert suspend fun insertRecurringList(x: List<Recurring>)
    @Insert suspend fun insertCheques(x: List<Cheque>)
}
