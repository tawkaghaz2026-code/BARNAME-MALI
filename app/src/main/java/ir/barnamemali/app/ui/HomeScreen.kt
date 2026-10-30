package ir.barnamemali.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import ir.barnamemali.app.switchTab
import ir.barnamemali.app.data.ChequeDirection
import ir.barnamemali.app.data.ChequeStatus
import ir.barnamemali.app.data.LoanDirection
import ir.barnamemali.app.data.Repository
import ir.barnamemali.app.data.Times
import ir.barnamemali.core.Jalali

@Composable
fun HomeScreen(nav: NavHostController) {
    val r = repo()
    val accounts by r.dao.accountsWithBalance().collectAsStateWithLifecycle(emptyList())
    val people by r.dao.peopleWithBalance().collectAsStateWithLifecycle(emptyList())
    val installments by r.dao.installments().collectAsStateWithLifecycle(emptyList())
    val loans by r.dao.loans().collectAsStateWithLifecycle(emptyList())
    val cheques by r.dao.cheques().collectAsStateWithLifecycle(emptyList())
    val pending by r.dao.pendingSms().collectAsStateWithLifecycle(emptyList())
    val members by r.dao.members().collectAsStateWithLifecycle(emptyList())
    val categories by r.dao.categories().collectAsStateWithLifecycle(emptyList())
    val recent by r.dao.recentTxns(6).collectAsStateWithLifecycle(emptyList())
    val hide by r.settings.hideAmounts.collectAsStateCompat()

    val today = Jalali.today()
    val (from, to) = Times.monthRange(today)
    val byMember by r.dao.expenseByMember(from, to).collectAsStateWithLifecycle(emptyList())
    var upcoming by remember { mutableStateOf<List<Repository.Due>>(emptyList()) }
    LaunchedEffect(installments, cheques, people) { upcoming = r.upcoming(30) }

    val loanById = loans.associateBy { it.id }
    val cash = accounts.sumOf { it.balance }
    val owedToUs = people.filter { it.balance > 0 }.sumOf { it.balance } +
        cheques.filter { it.status == ChequeStatus.PENDING && it.direction == ChequeDirection.RECEIVED }.sumOf { it.amount } +
        installments.filter { it.paidTxnId == null && loanById[it.loanId]?.direction == LoanDirection.GIVEN }.sumOf { it.amount }
    val weOwe = -people.filter { it.balance < 0 }.sumOf { it.balance } +
        cheques.filter { it.status == ChequeStatus.PENDING && it.direction == ChequeDirection.ISSUED }.sumOf { it.amount } +
        installments.filter { it.paidTxnId == null && loanById[it.loanId]?.direction == LoanDirection.TAKEN }.sumOf { it.amount }
    val spent = byMember.sumOf { it.total }
    val budget = members.sumOf { it.monthlyBudget }
    val daysLeft = today.lastOfMonth().day - today.day + 1

    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Hint(Jalali.weekdayName(today) + " " + today.formatLong())
                    Text("خانواده من", fontWeight = FontWeight.Bold, fontSize = 20.sp)
                }
                IconButton(onClick = { r.settings.setHideAmounts(!hide) }) {
                    Icon(if (hide) Icons.Default.Lock else Icons.Default.Info, contentDescription = "مخفی کردن مبالغ")
                }
                IconButton(onClick = { nav.navigate("settings") }) { Icon(Icons.Default.Settings, contentDescription = "تنظیمات") }
            }
        }
        item {
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Brand).clickable { nav.navigate("accounts") }.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text("ارزش خالص دارایی", color = Color.White, fontSize = 13.sp)
                Text(money(cash + owedToUs - weOwe), color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    MiniStat("موجودی حساب‌ها", money(cash, compact = true), Modifier.weight(1f))
                    MiniStat("طلب", money(owedToUs, compact = true), Modifier.weight(1f))
                    MiniStat("بدهی و تعهد", money(weOwe, compact = true), Modifier.weight(1f))
                }
            }
        }
        if (accounts.isEmpty()) item {
            SectionCard(onClick = { nav.navigate("accounts") }) {
                SectionTitle("شروع کنید: حساب‌های بانکی‌تان را تعریف کنید")
                Hint("بانک و چهار رقم آخر حساب یا کارت را وارد کنید تا پیامک‌های بانک خودکار خوانده شوند.")
            }
        }
        if (pending.isNotEmpty()) item {
            SectionCard(onClick = { nav.navigate("sms") }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("${fa(pending.size)} پیامک بانکی منتظر تأیید", fontWeight = FontWeight.Bold, color = Warn)
                        Hint("هر کدام با یک لمس ثبت می‌شود")
                    }
                    Text("بررسی", color = Warn, fontWeight = FontWeight.Bold)
                }
            }
        }
        item {
            SectionCard(onClick = { nav.navigate("family") }) {
                Row {
                    SectionTitle("خرج ${Jalali.MONTH_NAMES[today.month - 1]}", Modifier.weight(1f))
                    Hint(if (budget > 0) "${money(spent, compact = true)} از ${money(budget, compact = true)}" else money(spent, compact = true))
                }
                if (budget > 0) {
                    BudgetBar(spent, budget)
                    val left = (budget - spent).coerceAtLeast(0)
                    Hint("${fa(daysLeft)} روز مانده · روزانه تا ${money(left / daysLeft, compact = true)} می‌توانید خرج کنید")
                } else Hint("برای هر عضو خانواده بودجه ماهانه تعیین کنید")
            }
        }
        if (upcoming.isNotEmpty()) item {
            SectionCard(onClick = { nav.switchTab("obligations") }) {
                SectionTitle("سررسیدهای ۳۰ روز آینده")
                val todayDay = Times.todayEpochDay()
                upcoming.take(5).forEach { d ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        val days = d.epochDay - todayDay
                        Pill(
                            daysLabel(d.epochDay, todayDay),
                            if (days <= 3) Color(0xFFFEE2E2) else if (days <= 10) Color(0xFFFEF3C7) else Color(0xFFE6EBE6),
                            if (days <= 3) Color(0xFF991B1B) else if (days <= 10) Color(0xFF92400E) else Color(0xFF3F4A45),
                        )
                        Text(d.title, Modifier.weight(1f), fontSize = 14.sp)
                        Text(money(d.amount, compact = true), fontWeight = FontWeight.Bold, color = if (d.outgoing) Color.Unspecified else Brand)
                    }
                }
            }
        }
        item {
            SectionCard(onClick = { nav.switchTab("txns") }) {
                SectionTitle("آخرین تراکنش‌ها")
                if (recent.isEmpty()) Hint("هنوز تراکنشی ثبت نشده. دکمه + را بزنید.")
                val catNames = categories.associate { it.id to it.name }
                val memberNames = members.associate { it.id to it.name }
                recent.forEach { t ->
                    TxnRow(t, catNames, memberNames, accounts.associate { it.account.id to it.account.name }) { nav.navigate("add?id=${t.id}") }
                }
            }
        }
        item { androidx.compose.foundation.layout.Spacer(Modifier.padding(40.dp)) }
    }
}

@Composable
private fun MiniStat(label: String, value: String, modifier: Modifier) {
    Column(modifier.clip(RoundedCornerShape(12.dp)).background(Color(0x24FFFFFF)).padding(10.dp)) {
        Text(label, color = Color.White, fontSize = 11.sp)
        Text(value, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
    }
}

