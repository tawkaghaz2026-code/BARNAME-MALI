package ir.barnamemali.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ir.barnamemali.app.BarnameMaliApp
import ir.barnamemali.app.data.Repository
import ir.barnamemali.core.Jalali
import ir.barnamemali.core.JalaliDate
import ir.barnamemali.core.Money
import ir.barnamemali.core.MoneyUnit
import ir.barnamemali.core.PersianText

val LocalApp = compositionLocalOf<BarnameMaliApp> { error("no app") }

val Brand = Color(0xFF0F766E)
val BrandSoft = Color(0xFFE6F4F1)
val Warn = Color(0xFFB45309)
val WarnSoft = Color(0xFFFFF7ED)
val Danger = Color(0xFFB91C1C)
val Muted = Color(0xFF5B6661)
val Ground = Color(0xFFF4F6F3)

val AppColors = lightColorScheme(
    primary = Brand,
    onPrimary = Color.White,
    primaryContainer = BrandSoft,
    onPrimaryContainer = Color(0xFF115E59),
    secondary = Warn,
    background = Ground,
    surface = Color.White,
    onSurface = Color(0xFF16211D),
    onBackground = Color(0xFF16211D),
    surfaceVariant = Color(0xFFE6EBE6),
    onSurfaceVariant = Muted,
    error = Danger,
)

@Composable
fun repo(): Repository = LocalApp.current.repository

/** Formats an amount using the user's unit; hidden as •••• when privacy mode is on. */
@Composable
fun money(rial: Long, compact: Boolean = false, withUnit: Boolean = true): String {
    val r = repo()
    val unit by r.settings.unit.collectAsStateCompat()
    val hide by r.settings.hideAmounts.collectAsStateCompat()
    if (hide) return "••••"
    return if (compact) Money.compact(rial, unit) else Money.format(rial, unit, withUnit)
}

@Composable
fun <T> kotlinx.coroutines.flow.StateFlow<T>.collectAsStateCompat() = collectAsState()

@Composable
fun SectionCard(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    val m = modifier.fillMaxWidth()
    if (onClick != null) {
        Card(onClick = onClick, modifier = m, shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
        }
    } else {
        Card(modifier = m, shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
        }
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) =
    Text(text, modifier = modifier, fontWeight = FontWeight.Bold, fontSize = 15.sp)

@Composable
fun Hint(text: String, modifier: Modifier = Modifier) =
    Text(text, modifier = modifier, color = Muted, fontSize = 13.sp)

@Composable
fun BudgetBar(spent: Long, budget: Long) {
    val pct = if (budget > 0) (spent.toFloat() / budget).coerceIn(0f, 1f) else 0f
    LinearProgressIndicator(
        progress = { pct },
        modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
        color = if (budget > 0 && spent * 100 / budget >= 80) Warn else Brand,
        trackColor = Color(0xFFE6EBE6),
    )
}

@Composable
fun Avatar(name: String, color: Int) {
    val palette = listOf(0xFFDBEAFE, 0xFFFCE7F3, 0xFFEDE9FE, 0xFFFEF3C7, 0xFFCCFBF1, 0xFFE0E7FF, 0xFFFFE4E6)
    Box(
        Modifier.size(40.dp).clip(CircleShape).background(Color(palette[Math.floorMod(color, palette.size)])),
        contentAlignment = Alignment.Center,
    ) { Text(name.take(1), fontWeight = FontWeight.Bold) }
}

@Composable
fun Pill(text: String, bg: Color, fg: Color) {
    Text(
        text, color = fg, fontSize = 12.sp, fontWeight = FontWeight.Bold,
        modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(bg).padding(horizontal = 8.dp, vertical = 2.dp),
    )
}

/** Chip row for picking one item (category, member…). */
@Composable
fun <T> ChipPicker(items: List<T>, selected: T?, label: (T) -> String, onSelect: (T?) -> Unit, allowNone: Boolean = true) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        items.forEach { item ->
            FilterChip(
                selected = item == selected,
                onClick = { onSelect(if (item == selected && allowNone) null else item) },
                label = { Text(label(item)) },
            )
        }
    }
}

/** A simple dropdown field. */
@Composable
fun <T> Dropdown(label: String, items: List<T>, selected: T?, text: (T) -> String, onSelect: (T) -> Unit, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
            Column(Modifier.weight(1f)) {
                Text(label, fontSize = 11.sp, color = Muted)
                Text(selected?.let(text) ?: "انتخاب کنید", color = MaterialTheme.colorScheme.onSurface)
            }
            Icon(Icons.Default.ArrowDropDown, contentDescription = null)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            items.forEach { item ->
                DropdownMenuItem(text = { Text(text(item)) }, onClick = { onSelect(item); open = false })
            }
        }
    }
}

/** Shows digits grouped with ٬ while typing. */
private object GroupingTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val raw = text.text
        if (raw.isEmpty()) return TransformedText(text, OffsetMapping.Identity)
        val grouped = PersianText.groupDigits(raw.toLong())
        val originalToTransformed = IntArray(raw.length + 1)
        for (i in 0..raw.length) {
            // count separators before position i
            val sepsBefore = if (i == 0) 0 else (raw.length - 1) / 3 - (raw.length - i) / 3
            originalToTransformed[i] = i + sepsBefore
        }
        val mapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int) = originalToTransformed[offset.coerceIn(0, raw.length)].coerceAtMost(grouped.length)
            override fun transformedToOriginal(offset: Int): Int {
                for (i in raw.length downTo 0) if (originalToTransformed[i] <= offset) return i
                return 0
            }
        }
        return TransformedText(AnnotatedString(grouped), mapping)
    }
}

/** Amount input in the user's unit. [valueRial] is null when empty. */
@Composable
fun AmountField(label: String, valueRial: Long?, onChange: (Long?) -> Unit, modifier: Modifier = Modifier) {
    val unit by repo().settings.unit.collectAsStateCompat()
    val shown = valueRial?.let { Money.toDisplay(it, unit).toString() } ?: ""
    OutlinedTextField(
        value = shown,
        onValueChange = { s ->
            val n = PersianText.parseNumber(s)
            onChange(n?.let { Money.fromInput(it, unit) })
        },
        label = { Text("$label (${Money.unitLabel(unit)})") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        visualTransformation = GroupingTransformation,
        singleLine = true,
        modifier = modifier.fillMaxWidth(),
    )
}

/** Shamsi date picker: year, month and day dropdowns. */
@Composable
fun JalaliDateField(label: String, date: JalaliDate, onChange: (JalaliDate) -> Unit, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    OutlinedButton(onClick = { open = true }, modifier = modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
        Column(Modifier.weight(1f)) {
            Text(label, fontSize = 11.sp, color = Muted)
            Text(Jalali.weekdayName(date) + " " + date.formatLong(), color = MaterialTheme.colorScheme.onSurface)
        }
    }
    if (open) {
        var y by remember { mutableStateOf(date.year) }
        var m by remember { mutableStateOf(date.month) }
        var d by remember { mutableStateOf(date.day) }
        val maxDay = Jalali.monthLength(y, m)
        if (d > maxDay) d = maxDay
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text(label) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { val t = Jalali.today(); y = t.year; m = t.month; d = t.day }) { Text("امروز") }
                        TextButton(onClick = { val t = Jalali.today().plusDays(-1); y = t.year; m = t.month; d = t.day }) { Text("دیروز") }
                    }
                    Dropdown("سال", (Jalali.today().year - 5..Jalali.today().year + 10).toList(), y, { PersianText.toPersianDigits(it.toString()) }, { y = it })
                    Dropdown("ماه", (1..12).toList(), m, { Jalali.MONTH_NAMES[it - 1] }, { m = it })
                    Dropdown("روز", (1..maxDay).toList(), d, { PersianText.toPersianDigits(it.toString()) }, { d = it })
                }
            },
            confirmButton = { TextButton(onClick = { onChange(JalaliDate(y, m, d)); open = false }) { Text("تأیید") } },
            dismissButton = { TextButton(onClick = { open = false }) { Text("انصراف") } },
        )
    }
}

@Composable
fun ConfirmDialog(title: String, text: String, confirm: String = "حذف", onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = { TextButton(onClick = { onConfirm(); onDismiss() }) { Text(confirm, color = Danger) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("انصراف") } },
    )
}

@Composable
fun KeyValueRow(key: String, value: String, valueColor: Color = Color.Unspecified, onClick: (() -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().let { if (onClick != null) it.clickable(onClick = onClick) else it }.padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(key, Modifier.weight(1f))
        Spacer(Modifier.width(8.dp))
        Text(value, fontWeight = FontWeight.Bold, color = valueColor)
    }
}

fun fa(n: Number) = PersianText.toPersianDigits(n.toString())

fun daysLabel(epochDay: Long, today: Long): String {
    val d = epochDay - today
    return when {
        d < 0 -> "${fa(-d)} روز گذشته"
        d == 0L -> "امروز"
        d == 1L -> "فردا"
        else -> "${fa(d)} روز"
    }
}

fun unitOf(u: MoneyUnit) = Money.unitLabel(u)
