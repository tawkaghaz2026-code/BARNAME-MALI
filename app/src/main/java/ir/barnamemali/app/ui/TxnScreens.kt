package ir.barnamemali.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
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
import ir.barnamemali.app.data.CategoryKind
import ir.barnamemali.app.data.Times
import ir.barnamemali.app.data.Txn
import ir.barnamemali.app.data.TxnKind
import ir.barnamemali.app.data.TxnSource
import ir.barnamemali.core.Jalali
import ir.barnamemali.core.JalaliDate
import kotlinx.coroutines.launch

fun kindLabel(k: TxnKind) = when (k) {
    TxnKind.EXPENSE -> "هزینه"
    TxnKind.INCOME -> "درآمد"
    TxnKind.TRANSFER_OUT -> "انتقال به حساب دیگر"
    TxnKind.TRANSFER_IN -> "انتقال از حساب دیگر"
    TxnKind.ADJUSTMENT -> "اصلاح مانده"
    TxnKind.DEBT -> "طلب و بدهی"
    TxnKind.LOAN -> "وام"
    TxnKind.INSTALLMENT -> "قسط"
    TxnKind.CHEQUE -> "چک"
}

@Composable
fun TxnRow(t: Txn, cats: Map<Long, String>, members: Map<Long, String>, accounts: Map<Long, String>, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        androidx.compose.foundation.layout.Box(
            Modifier.size(8.dp).clip(CircleShape).background(if (t.amount > 0) Brand else Warn)
        )
        Column(Modifier.weight(1f)) {
            val title = t.categoryId?.let { cats[it] } ?: kindLabel(t.kind)
            Text(
                title + (t.note.takeIf { it.isNotBlank() && it != title }?.let { " · $it" } ?: ""),
                fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            val sub = listOfNotNull(
                Jalali.fromMillis(t.time).formatShort(),
                accounts[t.accountId],
                t.memberId?.let { members[it] },
                if (t.source == TxnSource.SMS) "پیامک" else if (t.source == TxnSource.AUTO) "خودکار" else null,
            ).joinToString(" · ")
            Hint(sub)
        }
        Text(
            (if (t.amount > 0) "+" else "") + money(t.amount, compact = true),
            fontWeight = FontWeight.Bold,
            color = when {
                t.kind == TxnKind.ADJUSTMENT -> Warn
                t.amount > 0 -> Brand
                else -> Color.Unspecified
            },
        )
    }
}

@Composable
fun TxnsScreen(nav: NavHostController) {
    val r = repo()
    var month by remember { mutableStateOf(Jalali.today().firstOfMonth()) }
    val (from, to) = Times.monthRange(month)
    val txns by r.dao.txnsBetween(from, to).collectAsStateWithLifecycle(emptyList())
    val cats by r.dao.categories().collectAsStateWithLifecycle(emptyList())
    val members by r.dao.members().collectAsStateWithLifecycle(emptyList())
    val accounts by r.dao.accountsWithBalance().collectAsStateWithLifecycle(emptyList())
    val catNames = cats.associate { it.id to it.name }
    val memberNames = members.associate { it.id to it.name }
    val accNames = accounts.associate { it.account.id to it.account.name }
    val income = txns.filter { it.kind == TxnKind.INCOME }.sumOf { it.amount }
    val expense = -txns.filter { it.kind == TxnKind.EXPENSE }.sumOf { it.amount }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        MonthSwitcher(month) { month = it }
        SectionCard {
            KeyValueRow("درآمد", money(income), Brand)
            KeyValueRow("هزینه", money(expense))
            KeyValueRow("مانده ماه", money(income - expense), if (income >= expense) Brand else Danger)
        }
        Spacer(Modifier.height(8.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            val byDay = txns.groupBy { Jalali.fromMillis(it.time) }
            byDay.forEach { (day, list) ->
                item(key = "d$day") {
                    Text(Jalali.weekdayName(day) + " " + day.formatLong(), color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 10.dp))
                }
                items(list, key = { it.id }) { t -> TxnRow(t, catNames, memberNames, accNames) { nav.navigate("add?id=${t.id}") } }
            }
            if (txns.isEmpty()) item { Hint("در این ماه تراکنشی نیست", Modifier.padding(16.dp)) }
            item { Spacer(Modifier.height(80.dp)) }
        }
    }
}

@Composable
fun MonthSwitcher(month: JalaliDate, onChange: (JalaliDate) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { onChange(month.plusMonths(-1)) }) { Icon(Icons.Default.KeyboardArrowRight, "ماه قبل") }
        Text(month.monthTitle(), Modifier.weight(1f), fontWeight = FontWeight.Bold, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        IconButton(onClick = { onChange(month.plusMonths(1)) }) { Icon(Icons.Default.KeyboardArrowLeft, "ماه بعد") }
    }
}

private enum class Mode(val label: String) { EXPENSE("هزینه"), INCOME("درآمد"), TRANSFER("انتقال") }

@Composable
fun AddTxnScreen(nav: NavHostController, id: Long) {
    val app = LocalApp.current
    val r = repo()
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val accounts by r.dao.accountsWithBalance().collectAsStateWithLifecycle(emptyList())
    val cats by r.dao.categories().collectAsStateWithLifecycle(emptyList())
    val members by r.dao.members().collectAsStateWithLifecycle(emptyList())

    var existing by remember { mutableStateOf<Txn?>(null) }
    var mode by remember { mutableStateOf(Mode.EXPENSE) }
    var amount by remember { mutableStateOf<Long?>(null) }
    var accountId by remember { mutableStateOf<Long?>(null) }
    var toAccountId by remember { mutableStateOf<Long?>(null) }
    var categoryId by remember { mutableStateOf<Long?>(null) }
    var memberId by remember { mutableStateOf<Long?>(null) }
    var date by remember { mutableStateOf(Jalali.today()) }
    var note by remember { mutableStateOf("") }
    var confirmDelete by remember { mutableStateOf(false) }

    LaunchedEffect(id) {
        if (id > 0) r.dao.txn(id)?.let { t ->
            existing = t
            mode = if (t.amount > 0) Mode.INCOME else Mode.EXPENSE
            amount = kotlin.math.abs(t.amount)
            accountId = t.accountId
            categoryId = t.categoryId
            memberId = t.memberId
            date = Jalali.fromMillis(t.time)
            note = t.note
        }
    }
    LaunchedEffect(accounts, members) {
        if (accountId == null) accountId = accounts.firstOrNull()?.account?.id
        if (memberId == null && id == 0L) memberId = members.firstOrNull { it.shared }?.id
    }
    val editingSpecial = existing?.let { it.kind != TxnKind.EXPENSE && it.kind != TxnKind.INCOME } == true

    SubScaffold(topBar = {
        TopAppBar(
            windowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
            title = { Text(if (id > 0) "ویرایش تراکنش" else "ثبت تراکنش") },
            navigationIcon = { IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.Default.ArrowBack, "بازگشت") } },
            actions = {
                if (existing != null) IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Default.Delete, "حذف") }
            },
        )
    }) { padding ->
        Column(
            Modifier.padding(padding).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (editingSpecial) Hint("نوع: ${kindLabel(existing!!.kind)}")
            else if (existing == null || existing!!.peerTxnId == null) {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    Mode.entries.forEachIndexed { i, m ->
                        if (existing != null && m == Mode.TRANSFER) return@forEachIndexed
                        SegmentedButton(
                            selected = mode == m,
                            onClick = { mode = m; categoryId = null },
                            shape = SegmentedButtonDefaults.itemShape(i, Mode.entries.size),
                        ) { Text(m.label) }
                    }
                }
            }
            AmountField("مبلغ", amount, { amount = it })
            if (accounts.isEmpty()) Hint("اول یک حساب تعریف کنید (تنظیمات › حساب‌ها)")
            Dropdown(if (mode == Mode.TRANSFER) "از حساب" else "حساب", accounts.map { it.account }, accounts.map { it.account }.firstOrNull { it.id == accountId }, { it.name }, { accountId = it.id })
            if (mode == Mode.TRANSFER) {
                Dropdown("به حساب", accounts.map { it.account }.filter { it.id != accountId }, accounts.map { it.account }.firstOrNull { it.id == toAccountId }, { it.name }, { toAccountId = it.id })
            } else if (!editingSpecial || existing?.kind == TxnKind.ADJUSTMENT) {
                SectionTitle("دسته")
                val kind = if (mode == Mode.INCOME) CategoryKind.INCOME else CategoryKind.EXPENSE
                ChipPicker(cats.filter { it.kind == kind }, cats.firstOrNull { it.id == categoryId }, { it.name }, { categoryId = it?.id })
            }
            if (mode != Mode.TRANSFER) {
                SectionTitle("برای چه کسی؟")
                ChipPicker(members, members.firstOrNull { it.id == memberId }, { it.name }, { memberId = it?.id })
            }
            JalaliDateField("تاریخ", date, { date = it })
            OutlinedTextField(note, { note = it }, label = { Text("توضیح (اختیاری)") }, modifier = Modifier.fillMaxWidth())
            Button(
                onClick = {
                    val a = amount ?: return@Button
                    val acc = accountId ?: return@Button
                    val time = existing?.let { e ->
                        if (Jalali.fromMillis(e.time) == date) e.time else Times.at(date, 12, 0)
                    } ?: if (date == Jalali.today()) System.currentTimeMillis() else Times.at(date, 12, 0)
                    app.appScope.launch {
                        when {
                            mode == Mode.TRANSFER -> toAccountId?.let { r.transfer(acc, it, a, time, note) }
                            editingSpecial -> r.saveTxn(
                                existing!!.copy(accountId = acc, amount = if (existing!!.amount < 0) -a else a, time = time, note = note, memberId = memberId, categoryId = categoryId, reviewed = true)
                            )
                            else -> r.saveTxn(
                                (existing ?: Txn(accountId = acc, amount = 0, kind = TxnKind.EXPENSE, time = time)).copy(
                                    accountId = acc,
                                    amount = if (mode == Mode.EXPENSE) -a else a,
                                    kind = if (mode == Mode.EXPENSE) TxnKind.EXPENSE else TxnKind.INCOME,
                                    time = time, categoryId = categoryId, memberId = memberId, note = note,
                                )
                            )
                        }
                    }
                    nav.popBackStack()
                },
                enabled = amount != null && amount!! > 0 && accountId != null && (mode != Mode.TRANSFER || toAccountId != null),
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) { Text("ذخیره") }
            Spacer(Modifier.height(40.dp))
        }
    }
    if (confirmDelete) ConfirmDialog("حذف تراکنش", "این تراکنش حذف شود؟", onConfirm = {
        existing?.let { t -> scope.launch { r.deleteTxn(t); nav.popBackStack() } }
    }, onDismiss = { confirmDelete = false })
}
