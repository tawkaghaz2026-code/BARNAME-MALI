package ir.barnamemali.app.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import ir.barnamemali.app.data.Account
import ir.barnamemali.app.data.AccountType
import ir.barnamemali.app.data.Category
import ir.barnamemali.app.data.CategoryKind
import ir.barnamemali.app.data.Member
import ir.barnamemali.app.data.PendingSms
import ir.barnamemali.app.data.SmsBooking
import ir.barnamemali.app.sms.InboxImporter
import ir.barnamemali.core.Jalali
import ir.barnamemali.core.PersianText
import ir.barnamemali.core.sms.Banks
import kotlinx.coroutines.launch

@Composable
fun BackBar(title: String, nav: NavHostController, actions: @Composable () -> Unit = {}) {
    TopAppBar(
        windowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
        title = { Text(title) },
        navigationIcon = { IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.Default.ArrowBack, "بازگشت") } },
        actions = { actions() },
    )
}

@Composable
fun SmsReviewScreen(nav: NavHostController) {
    val r = repo()
    val scope = rememberCoroutineScope()
    val snack = remember { SnackbarHostState() }
    val pending by r.dao.pendingSms().collectAsStateWithLifecycle(emptyList())
    val accounts by r.dao.accountsWithBalance().collectAsStateWithLifecycle(emptyList())
    val cats by r.dao.categories().collectAsStateWithLifecycle(emptyList())
    val members by r.dao.members().collectAsStateWithLifecycle(emptyList())
    val ready = pending.count { it.accountId != null && it.amount != 0L }

    SubScaffold(
        topBar = {
            BackBar("پیامک‌های بانکی", nav) {
                if (ready > 1) TextButton(onClick = {
                    scope.launch {
                        val n = r.confirmAllSms()
                        snack.showSnackbar("${fa(n)} پیامک ثبت و مانده‌ها تطبیق داده شد")
                    }
                }) { Text("تأیید همه (${fa(ready)})") }
            }
        },
        snackbarHost = { SnackbarHost(snack) },
    ) { padding ->
        LazyColumn(
            Modifier.padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (pending.isEmpty()) item {
                Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SectionTitle("پیامکی منتظر تأیید نیست")
                    Hint("پیامک‌های بانکی جدید خودکار اینجا می‌آیند. اگر خواندن پیامک خاموش است، متن پیامک را از برنامه پیام‌ها «اشتراک‌گذاری» کنید و برنامه مالی را انتخاب کنید.")
                }
            }
            items(pending, key = { it.id }) { sms ->
                SmsCard(sms, accounts.map { it.account }, cats, members,
                    onConfirm = { accountId, signed, booking, cat, mem, note, remember, auto ->
                        scope.launch {
                            val msg = r.confirmSms(sms.id, accountId, signed, booking, cat, mem, note, remember, auto)
                            snack.showSnackbar(msg)
                        }
                    },
                    onIgnore = { scope.launch { r.ignoreSms(sms) } },
                )
            }
            item { Spacer(Modifier.height(40.dp)) }
        }
    }
}

@Composable
private fun SmsCard(
    sms: PendingSms,
    accounts: List<Account>,
    cats: List<Category>,
    members: List<Member>,
    onConfirm: (Long, Long, SmsBooking, Long?, Long?, String, Boolean, Boolean) -> Unit,
    onIgnore: () -> Unit,
) {
    var accountId by remember(sms.id) { mutableStateOf(sms.accountId) }
    var negative by remember(sms.id) { mutableStateOf(if (sms.amount == 0L) null else sms.amount < 0) }
    var booking by remember(sms.id) { mutableStateOf(SmsBooking.EXPENSE_OR_INCOME) }
    var categoryId by remember(sms.id) { mutableStateOf(sms.suggestedCategoryId) }
    var memberId by remember(sms.id) { mutableStateOf(sms.suggestedMemberId) }
    var rememberRule by remember(sms.id) { mutableStateOf(true) }
    var autoNext by remember(sms.id) { mutableStateOf(false) }
    var note by remember(sms.id) { mutableStateOf("") }

    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(Banks.byId(sms.bankId)?.name ?: sms.sender, Modifier.weight(1f), fontWeight = FontWeight.Bold)
            Hint(Jalali.fromMillis(sms.time).formatShort())
        }
        Text(
            sms.body, fontSize = 13.sp, color = Muted,
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Ground).padding(10.dp),
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                (if (negative == true) "برداشت " else if (negative == false) "واریز " else "") + money(sms.absAmount),
                fontSize = 22.sp, fontWeight = FontWeight.Bold, color = if (negative == false) Brand else Warn,
                modifier = Modifier.weight(1f),
            )
        }
        if (negative == null) {
            Hint("نوع تراکنش از پیامک معلوم نیست:")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = false, onClick = { negative = true }, label = { Text("برداشت") })
                FilterChip(selected = false, onClick = { negative = false }, label = { Text("واریز") })
            }
        }
        sms.balance?.let { Hint("مانده طبق پیامک: ${money(it)} · بعد از تأیید با مانده اپ تطبیق و در صورت اختلاف خودکار اصلاح می‌شود") }
        Dropdown("حساب", accounts, accounts.firstOrNull { it.id == accountId }, { a -> a.name + (a.digits?.let { " ··" + PersianText.toPersianDigits(it) } ?: "") }, { accountId = it.id })
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(booking == SmsBooking.EXPENSE_OR_INCOME, { booking = SmsBooking.EXPENSE_OR_INCOME }, { Text(if (negative == false) "درآمد" else "هزینه") })
            FilterChip(booking == SmsBooking.TRANSFER, { booking = SmsBooking.TRANSFER }, { Text("جابه‌جایی بین حساب‌های خودم") })
        }
        if (booking == SmsBooking.EXPENSE_OR_INCOME) {
            val kind = if (negative == false) CategoryKind.INCOME else CategoryKind.EXPENSE
            ChipPicker(cats.filter { it.kind == kind }, cats.firstOrNull { it.id == categoryId }, { it.name }, { categoryId = it?.id })
        }
        Hint("برای چه کسی؟")
        ChipPicker(members, members.firstOrNull { it.id == memberId }, { it.name }, { memberId = it?.id })
        OutlinedTextField(note, { note = it }, label = { Text("یادداشت (اختیاری)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        if (sms.description.isNotBlank()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(rememberRule, { rememberRule = it })
                Text("دفعه بعد «${sms.description.take(30)}» همین دسته و عضو را بگیرد", fontSize = 13.sp)
            }
            if (rememberRule) Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(autoNext, { autoNext = it })
                Text("از این به بعد بدون پرسیدن ثبت شود", fontSize = 13.sp)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = {
                    val acc = accountId ?: return@Button
                    val signed = if (negative == true) -sms.absAmount else sms.absAmount
                    onConfirm(acc, signed, booking, categoryId, memberId, note, rememberRule, autoNext)
                },
                enabled = accountId != null && negative != null,
                modifier = Modifier.weight(2f).height(48.dp),
            ) { Text("تأیید") }
            OutlinedButton(onClick = onIgnore, modifier = Modifier.weight(1f).height(48.dp)) { Text("نادیده بگیر") }
        }
    }
}

// ---------------- Accounts ----------------

fun accountTypeLabel(t: AccountType) = when (t) {
    AccountType.BANK -> "حساب بانکی"
    AccountType.CARD -> "کارت"
    AccountType.CASH -> "نقد / کیف پول"
    AccountType.FUND -> "صندوق قرض‌الحسنه"
    AccountType.GOLD -> "طلا و سکه"
    AccountType.FOREIGN_CURRENCY -> "ارز"
    AccountType.OTHER -> "سایر"
}

@Composable
fun AccountsScreen(nav: NavHostController) {
    val app = LocalApp.current
    val r = repo()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snack = remember { SnackbarHostState() }
    val accounts by r.dao.accountsWithBalance().collectAsStateWithLifecycle(emptyList())
    val members by r.dao.members().collectAsStateWithLifecycle(emptyList())
    var editing by remember { mutableStateOf<Account?>(null) }
    var showImport by remember { mutableStateOf(false) }

    val smsPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
        if (granted.values.all { it }) showImport = true
    }

    SubScaffold(
        topBar = {
            BackBar("حساب‌ها", nav) {
                IconButton(onClick = { editing = Account(name = "", balanceKnown = true) }) { Icon(Icons.Default.Add, "حساب جدید") }
            }
        },
        snackbarHost = { SnackbarHost(snack) },
    ) { padding ->
        LazyColumn(Modifier.padding(padding).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(accounts, key = { it.account.id }) { a ->
                SectionCard(onClick = { editing = a.account }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(a.account.name, fontWeight = FontWeight.Bold)
                            Hint(
                                listOfNotNull(
                                    accountTypeLabel(a.account.type),
                                    Banks.byId(a.account.bankId)?.name,
                                    a.account.digits?.let { "··" + PersianText.toPersianDigits(it) },
                                    a.account.ownerMemberId?.let { id -> members.firstOrNull { it.id == id }?.name },
                                ).joinToString(" · ")
                            )
                        }
                        Text(if (a.account.balanceKnown) money(a.balance) else "منتظر اولین پیامک", fontWeight = FontWeight.Bold)
                    }
                }
            }
            item {
                SectionCard {
                    SectionTitle("خواندن پیامک‌های بانکی")
                    val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED
                    Hint(
                        if (granted) "روشن است. پیامک‌های جدید بانک خودکار خوانده می‌شوند و فقط پیامک‌های تراکنش نگه داشته می‌شوند."
                        else "با اجازه خواندن پیامک، تراکنش‌ها از پیامک بانک ساخته می‌شوند. هیچ پیامکی از گوشی خارج نمی‌شود."
                    )
                    Button(onClick = {
                        if (granted) showImport = true
                        else smsPermission.launch(arrayOf(Manifest.permission.RECEIVE_SMS, Manifest.permission.READ_SMS))
                    }) { Text(if (granted) "خواندن پیامک‌های قبلی" else "اجازه خواندن پیامک") }
                }
            }
            item { Spacer(Modifier.height(40.dp)) }
        }
    }

    if (showImport) {
        var days by remember { mutableStateOf(30) }
        AlertDialog(
            onDismissRequest = { showImport = false },
            title = { Text("خواندن پیامک‌های قبلی") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Hint("پیامک‌های بانکی چند روز گذشته خوانده و برای تأیید آماده می‌شوند. برای حساب‌هایی که مانده‌شان را وارد کرده‌اید فقط پیامک‌های بعد از تعریف حساب خوانده می‌شود.")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(7, 30, 90).forEach { d -> FilterChip(days == d, { days = d }, { Text("${fa(d)} روز") }) }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showImport = false
                    scope.launch {
                        val n = InboxImporter.import(context, r, System.currentTimeMillis() - days * 86_400_000L)
                        snack.showSnackbar("${fa(n)} پیامک بانکی پیدا شد")
                        if (n > 0) nav.navigate("sms")
                    }
                }) { Text("شروع") }
            },
            dismissButton = { TextButton(onClick = { showImport = false }) { Text("انصراف") } },
        )
    }

    editing?.let { acc ->
        AccountDialog(acc, members, onDismiss = { editing = null }) { saved ->
            editing = null
            app.appScope.launch {
                if (saved.id == 0L) {
                    val id = r.dao.insertAccount(saved)
                    r.attachPendingSms(saved.copy(id = id))
                } else r.dao.updateAccount(saved)
            }
        }
    }
}

@Composable
private fun AccountDialog(acc: Account, members: List<Member>, onDismiss: () -> Unit, onSave: (Account) -> Unit) {
    var name by remember { mutableStateOf(acc.name) }
    var type by remember { mutableStateOf(acc.type) }
    var bankId by remember { mutableStateOf(acc.bankId) }
    var digits by remember { mutableStateOf(acc.digits ?: "") }
    var owner by remember { mutableStateOf(acc.ownerMemberId) }
    var balance by remember { mutableStateOf<Long?>(if (acc.id == 0L) null else acc.openingBalance) }
    var fromSms by remember { mutableStateOf(acc.id == 0L) }
    val isNew = acc.id == 0L
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isNew) "حساب جدید" else "ویرایش حساب") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Dropdown("نوع", AccountType.entries, type, ::accountTypeLabel, { type = it })
                if (type == AccountType.BANK || type == AccountType.CARD || type == AccountType.FUND) {
                    Dropdown("بانک", Banks.ALL, Banks.byId(bankId), { it.name }, {
                        bankId = it.id
                        if (name.isBlank()) name = it.name
                    })
                    OutlinedTextField(
                        digits, { v -> digits = PersianText.normalize(v).filter { it.isDigit() }.takeLast(4) },
                        label = { Text("۴ رقم آخر حساب یا کارت") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true,
                    )
                }
                OutlinedTextField(name, { name = it }, label = { Text("نام") }, singleLine = true)
                Dropdown("صاحب حساب", members, members.firstOrNull { it.id == owner }, { it.name }, { owner = it.id })
                if (isNew && (type == AccountType.BANK || type == AccountType.CARD)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(fromSms, { fromSms = it })
                        Text("مانده را از اولین پیامک بانک بردار", fontSize = 13.sp)
                    }
                }
                if (!(isNew && fromSms && (type == AccountType.BANK || type == AccountType.CARD))) {
                    AmountField(if (isNew) "موجودی فعلی" else "مانده اولیه", balance, { balance = it })
                    if (!isNew) Hint("مانده اولیه، موجودی قبل از اولین تراکنش ثبت‌شده است.")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val usesSms = isNew && fromSms && (type == AccountType.BANK || type == AccountType.CARD)
                onSave(
                    acc.copy(
                        name = name.ifBlank { accountTypeLabel(type) }, type = type, bankId = bankId,
                        digits = digits.ifBlank { null }, ownerMemberId = owner,
                        openingBalance = if (usesSms) 0 else balance ?: 0,
                        balanceKnown = if (isNew) !usesSms else acc.balanceKnown || balance != null,
                    )
                )
            }) { Text("ذخیره") }
        },
        dismissButton = {
            Row {
                if (!isNew) TextButton(onClick = { onSave(acc.copy(archived = true)) }) { Text("بایگانی", color = Danger) }
                TextButton(onClick = onDismiss) { Text("انصراف") }
            }
        },
    )
}
