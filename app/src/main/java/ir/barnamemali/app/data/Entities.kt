package ir.barnamemali.app.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

// All amounts are stored in rial. Dates are epoch milliseconds (moments) or epoch days (due dates).

enum class AccountType { BANK, CARD, CASH, FUND, GOLD, FOREIGN_CURRENCY, OTHER }

@Serializable
@Entity
data class Account(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val type: AccountType = AccountType.BANK,
    val bankId: String? = null,
    /** Last 4 digits of the account or card number, used to match bank SMS. */
    val digits: String? = null,
    val ownerMemberId: Long? = null,
    val openingBalance: Long = 0,
    /** False until the user enters a balance or the first bank SMS reports one. */
    val balanceKnown: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val archived: Boolean = false,
)

@Serializable
@Entity
data class Member(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val role: String = "",
    /** The household's shared spending (groceries, bills…) is a member too. */
    val shared: Boolean = false,
    val monthlyBudget: Long = 0,
    val color: Int = 0,
    val sortOrder: Int = 0,
)

enum class CategoryKind { EXPENSE, INCOME }

@Serializable
@Entity
data class Category(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val kind: CategoryKind = CategoryKind.EXPENSE,
    val monthlyBudget: Long = 0,
    val sortOrder: Int = 0,
)

enum class TxnKind {
    EXPENSE, INCOME, TRANSFER_OUT, TRANSFER_IN,
    /** Automatic balance correction after comparing with a bank SMS. */
    ADJUSTMENT,
    /** Money lent to or borrowed from a person, and its repayments. */
    DEBT,
    /** A loan received into an account. */
    LOAN,
    INSTALLMENT,
    CHEQUE,
}

enum class TxnSource { MANUAL, SMS, AUTO }

@Serializable
@Entity(indices = [Index("accountId"), Index("time"), Index("memberId"), Index("categoryId")])
data class Txn(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val accountId: Long,
    /** Signed: negative leaves the account, positive enters it. */
    val amount: Long,
    val kind: TxnKind,
    val time: Long,
    val categoryId: Long? = null,
    val memberId: Long? = null,
    val personId: Long? = null,
    val loanId: Long? = null,
    val chequeId: Long? = null,
    /** For transfers: the id of the matching entry on the other account. */
    val peerTxnId: Long? = null,
    val note: String = "",
    val source: TxnSource = TxnSource.MANUAL,
    /** Balance printed in the bank SMS this entry came from, if any. */
    val smsBalance: Long? = null,
    val reviewed: Boolean = true,
)

@Serializable
@Entity
data class Person(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val phone: String = "",
    val note: String = "",
)

/** One change in what a person owes us. Positive: they owe us more (we lent). Negative: we owe them more. */
@Serializable
@Entity(indices = [Index("personId")])
data class DebtEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val personId: Long,
    val amount: Long,
    val time: Long,
    val dueEpochDay: Long? = null,
    val note: String = "",
    val txnId: Long? = null,
)

enum class LoanDirection { TAKEN, GIVEN }

@Serializable
@Entity
data class Loan(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val direction: LoanDirection = LoanDirection.TAKEN,
    val principal: Long,
    val annualRate: Double = 0.0,
    val months: Int,
    val firstDueEpochDay: Long,
    val accountId: Long? = null,
    val memberId: Long? = null,
    val note: String = "",
    val closed: Boolean = false,
)

@Serializable
@Entity(indices = [Index("loanId")])
data class Installment(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val loanId: Long,
    val number: Int,
    val dueEpochDay: Long,
    val amount: Long,
    val paidTxnId: Long? = null,
    val paidAt: Long? = null,
)

enum class ChequeDirection { RECEIVED, ISSUED }
enum class ChequeStatus { PENDING, CLEARED, BOUNCED, ENDORSED, CANCELLED }

@Serializable
@Entity
data class Cheque(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val direction: ChequeDirection,
    val amount: Long,
    val dueEpochDay: Long,
    val counterparty: String,
    val bankId: String? = null,
    val serial: String = "",
    val accountId: Long? = null,
    val status: ChequeStatus = ChequeStatus.PENDING,
    val note: String = "",
    val txnId: Long? = null,
)

enum class SmsStatus { PENDING, CONFIRMED, IGNORED }

/** A bank SMS waiting for one-tap confirmation. Only SMS that look like transactions are stored. */
@Serializable
@Entity(indices = [Index(value = ["fingerprint"], unique = true)])
data class PendingSms(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sender: String,
    val body: String,
    val receivedAt: Long,
    /** When the transaction happened, from the SMS text or the arrival time. */
    val time: Long,
    /** Signed amount as it affects the account; 0 when the direction is unknown. */
    val amount: Long,
    val absAmount: Long,
    val balance: Long? = null,
    val accountId: Long? = null,
    val bankId: String? = null,
    val digits: String? = null,
    val description: String = "",
    val suggestedCategoryId: Long? = null,
    val suggestedMemberId: Long? = null,
    val status: SmsStatus = SmsStatus.PENDING,
    val txnId: Long? = null,
    val fingerprint: String,
)

/** Learned from confirmations: merchant text -> category and member. */
@Serializable
@Entity
data class MerchantRule(
    @PrimaryKey val key: String,
    val categoryId: Long?,
    val memberId: Long?,
    val autoConfirm: Boolean = false,
    val hits: Int = 1,
)

@Serializable
@Entity
data class Recurring(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    /** Signed like [Txn.amount]. */
    val amount: Long,
    val accountId: Long? = null,
    val categoryId: Long? = null,
    val memberId: Long? = null,
    val nextDueEpochDay: Long,
    val active: Boolean = true,
)
