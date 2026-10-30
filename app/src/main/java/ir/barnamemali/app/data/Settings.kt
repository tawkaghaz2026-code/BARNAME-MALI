package ir.barnamemali.app.data

import android.content.Context
import ir.barnamemali.core.Jalali
import ir.barnamemali.core.JalaliDate
import ir.barnamemali.core.MoneyUnit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.time.LocalTime

class Settings(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    private val _unit = MutableStateFlow(MoneyUnit.valueOf(prefs.getString("unit", MoneyUnit.TOMAN.name)!!))
    val unit: StateFlow<MoneyUnit> = _unit

    fun setUnit(u: MoneyUnit) {
        prefs.edit().putString("unit", u.name).apply()
        _unit.value = u
    }

    var lockEnabled: Boolean
        get() = prefs.getBoolean("lock", false)
        set(v) = prefs.edit().putBoolean("lock", v).apply()

    private val _hideAmounts = MutableStateFlow(prefs.getBoolean("hide", false))
    val hideAmounts: StateFlow<Boolean> = _hideAmounts

    fun setHideAmounts(v: Boolean) {
        prefs.edit().putBoolean("hide", v).apply()
        _hideAmounts.value = v
    }

    /** Remembers which budget alerts were already sent, e.g. "m3-1405-8-80". */
    fun alertSent(key: String): Boolean = prefs.getBoolean("alert_$key", false)

    fun markAlertSent(key: String) = prefs.edit().putBoolean("alert_$key", true).apply()
}

/** Millisecond ranges for Shamsi days and months in Tehran time. */
object Times {
    fun startOfDay(d: JalaliDate): Long = d.toLocalDate().atStartOfDay(Jalali.TEHRAN).toInstant().toEpochMilli()

    fun monthRange(d: JalaliDate): Pair<Long, Long> =
        startOfDay(d.firstOfMonth()) to startOfDay(d.firstOfMonth().plusMonths(1))

    fun at(d: JalaliDate, hour: Int, minute: Int): Long =
        d.toLocalDate().atTime(LocalTime.of(hour, minute)).atZone(Jalali.TEHRAN).toInstant().toEpochMilli()

    fun todayEpochDay(): Long = Jalali.today().toEpochDay()
}
