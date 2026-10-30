package ir.barnamemali.app.ui

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import ir.barnamemali.app.data.Category
import ir.barnamemali.app.data.CategoryKind
import ir.barnamemali.app.data.Recurring
import ir.barnamemali.app.data.Times
import ir.barnamemali.core.Jalali
import ir.barnamemali.core.MoneyUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs

@Composable
fun SettingsScreen(nav: NavHostController) {
    val app = LocalApp.current
    val r = repo()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val unit by r.settings.unit.collectAsStateCompat()
    var lock by remember { mutableStateOf(r.settings.lockEnabled) }
    var passwordFor by remember { mutableStateOf<String?>(null) } // "export" or "import"
    var pendingImport by remember { mutableStateOf<android.net.Uri?>(null) }

    fun toast(s: String) = Toast.makeText(context, s, Toast.LENGTH_LONG).show()

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        val pw = exportPassword
        if (uri == null || pw == null) return@rememberLauncherForActivityResult
        scope.launch {
            runCatching {
                val bytes = app.backup.export(pw.toCharArray())
                withContext(Dispatchers.IO) { context.contentResolver.openOutputStream(uri)!!.use { it.write(bytes) } }
            }.onSuccess { toast("پشتیبان ذخیره شد") }.onFailure { toast("خطا: ${it.message}") }
            exportPassword = null
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) { pendingImport = uri; passwordFor = "import" }
    }
    val csvLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            runCatching {
                val bytes = app.backup.csv()
                withContext(Dispatchers.IO) { context.contentResolver.openOutputStream(uri)!!.use { it.write(bytes) } }
            }.onSuccess { toast("فایل اکسل (CSV) ذخیره شد") }.onFailure { toast("خطا: ${it.message}") }
        }
    }

    Scaffold(topBar = { BackBar("تنظیمات", nav) }) { padding ->
        LazyColumn(Modifier.padding(padding).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                SectionCard {
                    SectionTitle("واحد پول")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(unit == MoneyUnit.TOMAN, { r.settings.setUnit(MoneyUnit.TOMAN) }, { Text("تومان") })
                        FilterChip(unit == MoneyUnit.RIAL, { r.settings.setUnit(MoneyUnit.RIAL) }, { Text("ریال") })
                    }
                }
            }
            item {
                SectionCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            SectionTitle("قفل برنامه")
                            Hint("با اثر انگشت یا رمز گوشی باز شود")
                        }
                        Switch(lock, { on ->
                            val can = BiometricManager.from(context).canAuthenticate(
                                BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL
                            ) == BiometricManager.BIOMETRIC_SUCCESS
                            if (on && !can) toast("اول برای گوشی قفل صفحه یا اثر انگشت تعریف کنید")
                            else { lock = on; r.settings.lockEnabled = on }
                        })
                    }
                }
            }
            item { NavRow("حساب‌ها و خواندن پیامک بانک") { nav.navigate("accounts") } }
            item { NavRow("دسته‌ها و بودجه دسته‌ها") { nav.navigate("categories") } }
            item { NavRow("پرداخت‌های تکراری (اجاره، شارژ، قبوض…)") { nav.navigate("recurring") } }
            item { NavRow("اصلاح‌های خودکار مانده") { nav.navigate("adjustments") } }
            item {
                SectionCard {
                    SectionTitle("پشتیبان‌گیری")
                    Hint("همه اطلاعات در یک فایل رمزدار ذخیره می‌شود. فایل را در جای امن (مثلاً گوگل درایو یا فلش) نگه دارید. بدون رمز قابل بازکردن نیست.")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { passwordFor = "export" }, Modifier.weight(1f)) { Text("تهیه پشتیبان") }
                        OutlinedButton(onClick = { importLauncher.launch(arrayOf("*/*")) }, Modifier.weight(1f)) { Text("بازگردانی") }
                    }
                    OutlinedButton(onClick = {
                        csvLauncher.launch("transactions-${Jalali.today().let { "${it.year}-${it.month}-${it.day}" }}.csv")
                    }, Modifier.fillMaxWidth()) { Text("خروجی اکسل همه تراکنش‌ها (CSV)") }
                }
            }
            item { Hint("همه اطلاعات فقط روی همین گوشی و به‌صورت رمزشده نگهداری می‌شود.", Modifier.padding(8.dp)) }
            item { Spacer(Modifier.height(40.dp)) }
        }
    }

    passwordFor?.let { mode ->
        var pw by remember { mutableStateOf("") }
        var pw2 by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { passwordFor = null },
            title = { Text(if (mode == "export") "رمز فایل پشتیبان" else "رمز فایل پشتیبان را وارد کنید") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(pw, { pw = it }, label = { Text("رمز") }, singleLine = true,
                        visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
                    if (mode == "export") OutlinedTextField(pw2, { pw2 = it }, label = { Text("تکرار رمز") }, singleLine = true,
                        visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
                    if (mode == "import") Text("همه اطلاعات فعلی با محتوای پشتیبان جایگزین می‌شود.", color = Danger, fontSize = 13.sp)
                }
            },
            confirmButton = {
                TextButton(enabled = pw.length >= 4 && (mode == "import" || pw == pw2), onClick = {
                    passwordFor = null
                    if (mode == "export") {
                        exportPassword = pw
                        exportLauncher.launch("barname-mali-${Jalali.today().let { "${it.year}-${it.month}-${it.day}" }}.bmb")
                    } else pendingImport?.let { uri ->
                        scope.launch {
                            runCatching {
                                val bytes = withContext(Dispatchers.IO) { context.contentResolver.openInputStream(uri)!!.use { it.readBytes() } }
                                withContext(Dispatchers.IO) { app.backup.restore(bytes, pw.toCharArray()) }
                            }.onSuccess { toast("اطلاعات بازگردانی شد") }.onFailure { toast(it.message ?: "خطا در بازگردانی") }
                        }
                    }
                }) { Text("ادامه") }
            },
            dismissButton = { TextButton(onClick = { passwordFor = null }) { Text("انصراف") } },
        )
    }
}

/** Held between the password dialog and the file picker result. */
private var exportPassword: String? = null

@Composable
private fun NavRow(title: String, onClick: () -> Unit) {
    SectionCard(onClick = onClick) { Text(title, fontWeight = FontWeight.Bold) }
}

@Composable
fun AdjustmentsScreen(nav: NavHostController) {
    val r = repo()
    val app = LocalApp.current
    val list by r.dao.adjustments().collectAsStateWithLifecycle(emptyList())
    val accounts by r.dao.accountsWithBalance().collectAsStateWithLifecycle(emptyList())
    val accNames = accounts.associate { it.account.id to it.account.name }
    Scaffold(topBar = { BackBar("اصلاح‌های خودکار مانده", nav) }) { padding ->
        LazyColumn(Modifier.padding(padding).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                Hint("هر بار مانده پیامک بانک با مانده برنامه فرق داشت و علتش پیدا نشد، این‌جا یک اصلاح ثبت می‌شود تا مانده‌ها یکی شوند. می‌توانید برای هر کدام دسته و توضیح بگذارید یا حذفش کنید.")
            }
            if (list.isEmpty()) item { Hint("هنوز اصلاحی لازم نشده است.", Modifier.padding(16.dp)) }
            items(list, key = { it.id }) { t ->
                SectionCard(onClick = { nav.navigate("add?id=${t.id}") }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(t.note, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Hint("${accNames[t.accountId] ?: ""} · ${Jalali.fromMillis(t.time).formatLong()}" + if (!t.reviewed) " · بازبینی نشده" else "")
                        }
                        Text((if (t.amount > 0) "+" else "") + money(t.amount, compact = true), fontWeight = FontWeight.Bold, color = if (t.amount > 0) Brand else Warn)
                    }
                    if (!t.reviewed) TextButton(onClick = { app.appScope.launch { r.dao.updateTxn(t.copy(reviewed = true)) } }) { Text("درست است") }
                }
            }
            item { Spacer(Modifier.height(40.dp)) }
        }
    }
}

@Composable
fun RecurringScreen(nav: NavHostController) {
    val app = LocalApp.current
    val r = repo()
    val list by r.dao.recurring().collectAsStateWithLifecycle(emptyList())
    val accounts by r.dao.accountsWithBalance().collectAsStateWithLifecycle(emptyList())
    val cats by r.dao.categories().collectAsStateWithLifecycle(emptyList())
    val members by r.dao.members().collectAsStateWithLifecycle(emptyList())
    var editing by remember { mutableStateOf<Recurring?>(null) }
    var paying by remember { mutableStateOf<Recurring?>(null) }
    val today = Times.todayEpochDay()

    Scaffold(topBar = {
        BackBar("پرداخت‌های تکراری", nav) {
            TextButton(onClick = { editing = Recurring(title = "", amount = 0, nextDueEpochDay = today) }) { Text("+ جدید") }
        }
    }) { padding ->
        LazyColumn(Modifier.padding(padding).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item { Hint("اجاره، شارژ، قبوض، اشتراک‌ها و حقوق را یک بار تعریف کنید؛ سررسیدشان یادآوری می‌شود و با یک لمس ثبت می‌شوند.") }
            items(list, key = { it.id }) { rec ->
                SectionCard(onClick = { editing = rec }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(rec.title, fontWeight = FontWeight.Bold)
                            Hint("ماهانه · بعدی ${Jalali.fromEpochDay(rec.nextDueEpochDay).formatLong()} (${daysLabel(rec.nextDueEpochDay, today)})")
                        }
                        Text(money(abs(rec.amount), compact = true), fontWeight = FontWeight.Bold, color = if (rec.amount > 0) Brand else androidx.compose.ui.graphics.Color.Unspecified)
                    }
                    if (rec.nextDueEpochDay <= today + 3) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { paying = rec }) { Text(if (rec.amount < 0) "پرداخت شد" else "دریافت شد") }
                        OutlinedButton(onClick = { app.appScope.launch { r.skipRecurring(rec) } }) { Text("این ماه نه") }
                    }
                }
            }
            item { Spacer(Modifier.height(40.dp)) }
        }
    }

    editing?.let { rec ->
        var title by remember { mutableStateOf(rec.title) }
        var amount by remember { mutableStateOf<Long?>(abs(rec.amount).takeIf { it > 0 }) }
        var income by remember { mutableStateOf(rec.amount > 0) }
        var due by remember { mutableStateOf(Jalali.fromEpochDay(rec.nextDueEpochDay)) }
        var accountId by remember { mutableStateOf(rec.accountId) }
        var categoryId by remember { mutableStateOf(rec.categoryId) }
        var memberId by remember { mutableStateOf(rec.memberId) }
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text(if (rec.id == 0L) "پرداخت تکراری جدید" else rec.title) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(!income, { income = false }, { Text("هزینه") })
                        FilterChip(income, { income = true }, { Text("درآمد") })
                    }
                    OutlinedTextField(title, { title = it }, label = { Text("عنوان") }, singleLine = true)
                    AmountField("مبلغ", amount, { amount = it })
                    JalaliDateField("سررسید بعدی", due, { due = it })
                    Dropdown("حساب", accounts.map { it.account }, accounts.map { it.account }.firstOrNull { it.id == accountId }, { it.name }, { accountId = it.id })
                    val kind = if (income) CategoryKind.INCOME else CategoryKind.EXPENSE
                    Dropdown("دسته", cats.filter { it.kind == kind }, cats.firstOrNull { it.id == categoryId }, { it.name }, { categoryId = it.id })
                    Dropdown("عضو", members, members.firstOrNull { it.id == memberId }, { it.name }, { memberId = it.id })
                }
            },
            confirmButton = {
                TextButton(enabled = title.isNotBlank() && amount != null, onClick = {
                    val a = amount!!
                    app.appScope.launch {
                        r.dao.upsertRecurring(rec.copy(title = title.trim(), amount = if (income) a else -a, nextDueEpochDay = due.toEpochDay(),
                            accountId = accountId, categoryId = categoryId, memberId = memberId))
                    }
                    editing = null
                }) { Text("ذخیره") }
            },
            dismissButton = {
                Row {
                    if (rec.id != 0L) TextButton(onClick = { app.appScope.launch { r.dao.deleteRecurring(rec) }; editing = null }) { Text("حذف", color = Danger) }
                    TextButton(onClick = { editing = null }) { Text("انصراف") }
                }
            },
        )
    }
    paying?.let { rec ->
        var accountId by remember { mutableStateOf(rec.accountId ?: accounts.firstOrNull()?.account?.id) }
        AlertDialog(
            onDismissRequest = { paying = null },
            title = { Text(rec.title) },
            text = { Dropdown("حساب", accounts.map { it.account }, accounts.map { it.account }.firstOrNull { it.id == accountId }, { it.name }, { accountId = it.id }) },
            confirmButton = {
                TextButton(enabled = accountId != null, onClick = {
                    app.appScope.launch { r.markRecurringDone(rec, accountId!!) }
                    paying = null
                }) { Text("ثبت") }
            },
            dismissButton = { TextButton(onClick = { paying = null }) { Text("انصراف") } },
        )
    }
}

@Composable
fun CategoriesScreen(nav: NavHostController) {
    val app = LocalApp.current
    val r = repo()
    val cats by r.dao.categories().collectAsStateWithLifecycle(emptyList())
    var editing by remember { mutableStateOf<Category?>(null) }
    Scaffold(topBar = {
        BackBar("دسته‌ها", nav) { TextButton(onClick = { editing = Category(name = "", sortOrder = cats.size) }) { Text("+ جدید") } }
    }) { padding ->
        LazyColumn(Modifier.padding(padding).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(CategoryKind.EXPENSE to "هزینه", CategoryKind.INCOME to "درآمد").forEach { (kind, label) ->
                item { SectionTitle(label, Modifier.padding(top = 8.dp)) }
                items(cats.filter { it.kind == kind }, key = { it.id }) { c ->
                    SectionCard(onClick = { editing = c }) {
                        KeyValueRow(c.name, if (c.monthlyBudget > 0) "سقف ${money(c.monthlyBudget, compact = true)}" else "")
                    }
                }
            }
            item { Spacer(Modifier.height(40.dp)) }
        }
    }
    editing?.let { c ->
        var name by remember { mutableStateOf(c.name) }
        var kind by remember { mutableStateOf(c.kind) }
        var budget by remember { mutableStateOf<Long?>(c.monthlyBudget.takeIf { it > 0 }) }
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text(if (c.id == 0L) "دسته جدید" else c.name) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(kind == CategoryKind.EXPENSE, { kind = CategoryKind.EXPENSE }, { Text("هزینه") })
                        FilterChip(kind == CategoryKind.INCOME, { kind = CategoryKind.INCOME }, { Text("درآمد") })
                    }
                    OutlinedTextField(name, { name = it }, label = { Text("نام") }, singleLine = true)
                    if (kind == CategoryKind.EXPENSE) AmountField("سقف ماهانه (اختیاری)", budget, { budget = it })
                }
            },
            confirmButton = {
                TextButton(enabled = name.isNotBlank(), onClick = {
                    app.appScope.launch { r.dao.upsertCategory(c.copy(name = name.trim(), kind = kind, monthlyBudget = budget ?: 0)) }
                    editing = null
                }) { Text("ذخیره") }
            },
            dismissButton = {
                Row {
                    if (c.id != 0L) TextButton(onClick = { app.appScope.launch { r.dao.deleteCategory(c) }; editing = null }) { Text("حذف", color = Danger) }
                    TextButton(onClick = { editing = null }) { Text("انصراف") }
                }
            },
        )
    }
}
