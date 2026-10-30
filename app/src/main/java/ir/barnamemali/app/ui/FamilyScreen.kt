package ir.barnamemali.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import ir.barnamemali.app.data.Member
import ir.barnamemali.app.data.Times
import ir.barnamemali.core.Jalali
import kotlinx.coroutines.launch

@Composable
fun FamilyScreen(nav: NavHostController) {
    val app = LocalApp.current
    val r = repo()
    var month by remember { mutableStateOf(Jalali.today().firstOfMonth()) }
    val (from, to) = Times.monthRange(month)
    val members by r.dao.members().collectAsStateWithLifecycle(emptyList())
    val byMember by r.dao.expenseByMember(from, to).collectAsStateWithLifecycle(emptyList())
    val byCategory by r.dao.expenseByCategory(from, to).collectAsStateWithLifecycle(emptyList())
    val cats by r.dao.categories().collectAsStateWithLifecycle(emptyList())
    var editing by remember { mutableStateOf<Member?>(null) }
    var insights by remember { mutableStateOf<List<String>>(emptyList()) }

    val spentBy = byMember.associate { it.key to it.total }
    val totalBudget = members.sumOf { it.monthlyBudget }
    val totalSpent = byMember.sumOf { it.total }

    LaunchedEffect(byCategory, month) {
        // Categories this month vs the average of the three months before.
        val out = mutableListOf<String>()
        val prev = (1..3).map { i -> Times.monthRange(month.plusMonths(-i)) }
            .map { (f, t) -> r.dao.expenseByCategoryOnce(f, t).associate { it.key to it.total } }
        val names = cats.associate { it.id to it.name }
        for (kt in byCategory) {
            val avg = prev.sumOf { it[kt.key] ?: 0L } / 3
            if (avg > 0 && kt.total > avg * 13 / 10) {
                val pct = (kt.total - avg) * 100 / avg
                out += "خرج «${names[kt.key] ?: "بدون دسته"}» ${fa(pct)}٪ بیشتر از میانگین سه ماه قبل است."
            }
        }
        insights = out
    }

    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { MonthSwitcher(month) { month = it } }
        item {
            SectionCard {
                Row {
                    Column(Modifier.weight(1f)) { Hint("بودجه کل"); Text(money(totalBudget, compact = true), fontWeight = FontWeight.Bold) }
                    Column(Modifier.weight(1f)) { Hint("خرج شده"); Text(money(totalSpent, compact = true), fontWeight = FontWeight.Bold) }
                    Column(Modifier.weight(1f)) {
                        Hint("باقی‌مانده")
                        Text(money(totalBudget - totalSpent, compact = true), fontWeight = FontWeight.Bold, color = if (totalBudget >= totalSpent) Brand else Danger)
                    }
                }
            }
        }
        items(members, key = { it.id }) { m ->
            val spent = spentBy[m.id] ?: 0L
            SectionCard(onClick = { editing = m }) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Avatar(m.name, m.color)
                    Column(Modifier.weight(1f)) {
                        Text(m.name, fontWeight = FontWeight.Bold)
                        if (m.role.isNotBlank()) Hint(m.role)
                    }
                    Text(if (m.monthlyBudget > 0) "${money(spent, compact = true)} از ${money(m.monthlyBudget, compact = true)}" else money(spent, compact = true), fontSize = 13.sp)
                }
                if (m.monthlyBudget > 0) {
                    BudgetBar(spent, m.monthlyBudget)
                    if (spent * 100 / m.monthlyBudget >= 80) Text(
                        if (spent >= m.monthlyBudget) "بودجه این ماه تمام شده" else "بیش از ۸۰٪ بودجه مصرف شده",
                        color = Warn, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
        (spentBy[null])?.takeIf { it > 0 }?.let { s ->
            item { SectionCard { KeyValueRow("بدون عضو مشخص", money(s)) } }
        }
        item {
            TextButton(onClick = { editing = Member(name = "", color = members.size) }) { Text("+ افزودن عضو خانواده") }
        }
        insights.forEach { line ->
            item { SectionCard { Text(line, color = Warn, fontSize = 13.sp) } }
        }
        item {
            SectionCard(onClick = { nav.navigate("categories") }) {
                SectionTitle("بودجه دسته‌ها")
                val withBudget = cats.filter { it.monthlyBudget > 0 }
                if (withBudget.isEmpty()) Hint("برای دسته‌هایی مثل خواربار یا رستوران هم می‌توانید سقف ماهانه بگذارید.")
                withBudget.forEach { c ->
                    val spent = byCategory.firstOrNull { it.key == c.id }?.total ?: 0L
                    Row { Text(c.name, Modifier.weight(1f), fontSize = 14.sp); Hint("${money(spent, compact = true)} از ${money(c.monthlyBudget, compact = true)}") }
                    BudgetBar(spent, c.monthlyBudget)
                }
            }
        }
        item { Spacer(Modifier.height(80.dp)) }
    }

    editing?.let { m ->
        MemberDialog(m, onDismiss = { editing = null }, onDelete = if (m.id != 0L && !m.shared) ({
            editing = null
            app.appScope.launch { r.dao.deleteMember(m) }
        }) else null) { saved ->
            editing = null
            app.appScope.launch { r.dao.upsertMember(saved) }
        }
    }
}

@Composable
private fun MemberDialog(m: Member, onDismiss: () -> Unit, onDelete: (() -> Unit)?, onSave: (Member) -> Unit) {
    var name by remember { mutableStateOf(m.name) }
    var role by remember { mutableStateOf(m.role) }
    var budget by remember { mutableStateOf<Long?>(m.monthlyBudget.takeIf { it > 0 }) }
    var shared by remember { mutableStateOf(m.shared) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (m.id == 0L) "عضو جدید" else m.name) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("نام") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(role, { role = it }, label = { Text("توضیح (مثلاً ۱۶ ساله · پول توجیبی)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                AmountField("بودجه ماهانه", budget, { budget = it })
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(shared, { shared = it })
                    Text("هزینه‌های مشترک خانواده", fontSize = 13.sp)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { if (name.isNotBlank()) onSave(m.copy(name = name.trim(), role = role.trim(), monthlyBudget = budget ?: 0, shared = shared)) }) { Text("ذخیره") }
        },
        dismissButton = {
            Row {
                onDelete?.let { TextButton(onClick = it) { Text("حذف", color = Danger) } }
                TextButton(onClick = onDismiss) { Text("انصراف") }
            }
        },
    )
}
