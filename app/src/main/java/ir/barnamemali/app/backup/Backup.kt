package ir.barnamemali.app.backup

import androidx.room.withTransaction
import ir.barnamemali.app.data.Account
import ir.barnamemali.app.data.AppDatabase
import ir.barnamemali.app.data.Category
import ir.barnamemali.app.data.Cheque
import ir.barnamemali.app.data.DebtEntry
import ir.barnamemali.app.data.Installment
import ir.barnamemali.app.data.Loan
import ir.barnamemali.app.data.Member
import ir.barnamemali.app.data.MerchantRule
import ir.barnamemali.app.data.PendingSms
import ir.barnamemali.app.data.Person
import ir.barnamemali.app.data.Recurring
import ir.barnamemali.app.data.Txn
import ir.barnamemali.core.Jalali
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

@Serializable
data class BackupData(
    val version: Int = 1,
    val accounts: List<Account>,
    val members: List<Member>,
    val categories: List<Category>,
    val txns: List<Txn>,
    val people: List<Person>,
    val debts: List<DebtEntry>,
    val loans: List<Loan>,
    val installments: List<Installment>,
    val cheques: List<Cheque>,
    val sms: List<PendingSms>,
    val rules: List<MerchantRule>,
    val recurring: List<Recurring>,
)

/** Password-protected backup file (AES-GCM, key from PBKDF2) and CSV export. */
class Backup(private val db: AppDatabase) {
    private val dao = db.dao()
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun export(password: CharArray): ByteArray {
        val data = BackupData(
            accounts = dao.allAccounts(), members = dao.allMembers(), categories = dao.allCategories(),
            txns = dao.allTxns(), people = dao.allPeople(), debts = dao.allDebts(), loans = dao.allLoans(),
            installments = dao.allInstallments(), cheques = dao.allCheques(), sms = dao.allSms(),
            rules = dao.allRules(), recurring = dao.allRecurring(),
        )
        val plain = json.encodeToString(BackupData.serializer(), data).toByteArray()
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key(password, salt))
        return MAGIC + salt + cipher.iv + cipher.doFinal(plain)
    }

    /** Replaces everything in the app with the backup. Throws on a wrong password or a damaged file. */
    suspend fun restore(bytes: ByteArray, password: CharArray) {
        require(bytes.size > 32 && bytes.copyOfRange(0, 4).contentEquals(MAGIC)) { "فایل پشتیبان معتبر نیست" }
        val salt = bytes.copyOfRange(4, 20)
        val iv = bytes.copyOfRange(20, 32)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(password, salt), GCMParameterSpec(128, iv))
        val plain = try {
            cipher.doFinal(bytes, 32, bytes.size - 32)
        } catch (e: Exception) {
            throw IllegalArgumentException("رمز پشتیبان اشتباه است")
        }
        val data = json.decodeFromString(BackupData.serializer(), String(plain))
        db.clearAllTables()
        db.withTransaction {
            dao.insertAccounts(data.accounts)
            dao.insertMembers(data.members)
            dao.insertCategories(data.categories)
            dao.insertTxns(data.txns)
            dao.insertPeople(data.people)
            dao.insertDebts(data.debts)
            dao.insertLoans(data.loans)
            dao.insertInstallments(data.installments)
            dao.insertCheques(data.cheques)
            dao.insertSmsList(data.sms)
            dao.insertRules(data.rules)
            dao.insertRecurringList(data.recurring)
        }
    }

    /** All transactions as CSV (UTF-8 with BOM so Excel shows Persian correctly). Amounts in rial. */
    suspend fun csv(): ByteArray {
        val accounts = dao.allAccounts().associateBy { it.id }
        val members = dao.allMembers().associateBy { it.id }
        val categories = dao.allCategories().associateBy { it.id }
        val sb = StringBuilder("﻿تاریخ,ساعت,حساب,نوع,مبلغ (ریال),دسته,عضو,شرح\n")
        for (t in dao.allTxns()) {
            val d = Jalali.fromMillis(t.time)
            val time = java.time.Instant.ofEpochMilli(t.time).atZone(Jalali.TEHRAN).toLocalTime()
            sb.append("%04d/%02d/%02d".format(d.year, d.month, d.day)).append(',')
                .append("%02d:%02d".format(time.hour, time.minute)).append(',')
                .append(esc(accounts[t.accountId]?.name)).append(',')
                .append(t.kind.name).append(',')
                .append(t.amount).append(',')
                .append(esc(t.categoryId?.let { categories[it]?.name })).append(',')
                .append(esc(t.memberId?.let { members[it]?.name })).append(',')
                .append(esc(t.note)).append('\n')
        }
        return sb.toString().toByteArray()
    }

    private fun esc(s: String?): String {
        val v = s ?: ""
        return if (v.any { it == ',' || it == '"' || it == '\n' }) "\"" + v.replace("\"", "\"\"") + "\"" else v
    }

    private fun key(password: CharArray, salt: ByteArray): SecretKeySpec {
        val f = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val k = f.generateSecret(PBEKeySpec(password, salt, 120_000, 256)).encoded
        return SecretKeySpec(k, "AES")
    }

    companion object {
        private val MAGIC = "BMB1".toByteArray()
    }
}
