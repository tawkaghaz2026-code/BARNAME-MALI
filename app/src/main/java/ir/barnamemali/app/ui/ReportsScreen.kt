package ir.barnamemali.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import ir.barnamemali.app.data.Times
import ir.barnamemali.core.Jalali
import ir.barnamemali.core.JalaliDate

private data class MonthPoint(val month: JalaliDate, val income: Long, val expense: Long)

@Composable
fun ReportsScreen(nav: NavHostController) {
    val r = repo()
    var month by remember { mutableStateOf(Jalali.today().firstOfMonth()) }
    val (from, to) = Times.monthRange(month)
    val byCategory by r.dao.expenseByCategory(from, to).collectAsStateWithLifecycle(emptyList())
    val byMember by r.dao.expenseByMember(from, to).collectAsStateWithLifecycle(emptyList())
    val cats by r.dao.categories().collectAsStateWithLifecycle(emptyList())
    val members by r.dao.members().collectAsStateWithLifecycle(emptyList())
    val txns by r.dao.txnsBetween(from, to).collectAsStateWithLifecycle(emptyList())
    var trend by remember { mutableStateOf<List<MonthPoint>>(emptyList()) }

    LaunchedEffect(month, txns) {
        trend = (5 downTo 0).map { i ->
            val m = month.plusMonths(-i)
            val (f, t) = Times.monthRange(m)
            MonthPoint(m, r.dao.incomeTotal(f, t), r.dao.expenseTotal(f, t))
        }
    }

    val income = trend.lastOrNull()?.income ?: 0L
    val expense = trend.lastOrNull()?.expense ?: 0L
    val saving = income - expense
    val savingPct = if (income > 0) saving * 100 / income else 0
    val catNames = cats.associate { it.id to it.name }
    val memberNames = members.associate { it.id to it.name }
    val adjustments = txns.filter { it.kind == ir.barnamemali.app.data.TxnKind.ADJUSTMENT }

    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { MonthSwitcher(month) { month = it } }
        item {
            SectionCard {
                KeyValueRow("درآمد", money(income), Brand)
                KeyValueRow("هزینه", money(expense))
                KeyValueRow("پس‌انداز", money(saving) + if (income > 0) " (${fa(savingPct)}٪ درآمد)" else "", if (saving >= 0) Brand else Danger)
            }
        }
        item {
            SectionCard {
                SectionTitle("خرج ۶ ماه اخیر")
                val max = (trend.maxOfOrNull { maxOf(it.expense, it.income) } ?: 0L).coerceAtLeast(1)
                Row(Modifier.fillMaxWidth().height(150.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Bottom) {
                    trend.forEach { p ->
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(money(p.expense, compact = true).replace(" م", ""), fontSize = 10.sp, color = Muted, maxLines = 1)
                            Row(Modifier.height(110.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                Bar(p.income, max, BrandSoft)
                                Bar(p.expense, max, if (p.month == month) Brand else Color(0xFF5FA79F))
                            }
                            Text(Jalali.MONTH_NAMES[p.month.month - 1], fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Clip)
                        }
                    }
                }
                Hint("ستون روشن: درآمد · ستون تیره: هزینه (اعداد به میلیون)")
            }
        }
        item {
            SectionCard {
                SectionTitle("بر اساس دسته")
                val total = byCategory.sumOf { it.total }.coerceAtLeast(1)
                if (byCategory.isEmpty()) Hint("هزینه‌ای در این ماه ثبت نشده")
                byCategory.sortedByDescending { it.total }.forEach { kt ->
                    ShareRow(kt.key?.let { catNames[it] } ?: "بدون دسته", kt.total, total)
                }
            }
        }
        item {
            SectionCard {
                SectionTitle("بر اساس عضو خانواده")
                val total = byMember.sumOf { it.total }.coerceAtLeast(1)
                byMember.sortedByDescending { it.total }.forEach { kt ->
                    ShareRow(kt.key?.let { memberNames[it] } ?: "نامشخص", kt.total, total)
                }
            }
        }
        if (adjustments.isNotEmpty()) item {
            SectionCard(onClick = { nav.navigate("adjustments") }) {
                SectionTitle("اصلاح‌های خودکار مانده این ماه")
                Hint("${fa(adjustments.size)} مورد، جمع ${money(adjustments.sumOf { it.amount })} · برای بازبینی بزنید")
            }
        }
        item { Spacer(Modifier.height(80.dp)) }
    }
}

@Composable
private fun Bar(value: Long, max: Long, color: Color) {
    val h = (110f * value / max).coerceAtLeast(if (value > 0) 2f else 0f)
    Box(Modifier.width(10.dp).height(h.dp).clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp)).background(color))
}

@Composable
private fun ShareRow(label: String, value: Long, total: Long) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, Modifier.width(110.dp), fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Box(Modifier.weight(1f).height(10.dp).clip(RoundedCornerShape(5.dp)).background(Color(0xFFEEF1EE))) {
            Box(Modifier.fillMaxWidth(value.toFloat() / total).height(10.dp).clip(RoundedCornerShape(5.dp)).background(Brand))
        }
        Text(money(value, compact = true), fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(70.dp))
    }
}
