package ir.barnamemali.app.notify

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import ir.barnamemali.app.BarnameMaliApp
import ir.barnamemali.app.data.Times
import ir.barnamemali.core.Jalali
import ir.barnamemali.core.Money
import java.util.concurrent.TimeUnit

/** Once a day: reminds about cheques, installments, recurring bills and debts due in the next 3 days. */
class ReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as BarnameMaliApp
        val repo = app.repository
        val today = Times.todayEpochDay()
        val due = repo.upcoming(3)
        if (due.isEmpty()) return Result.success()
        val unit = repo.settings.unit.value
        val lines = due.take(6).joinToString("\n") { d ->
            val days = d.epochDay - today
            val whenText = when {
                days < 0 -> "گذشته"
                days == 0L -> "امروز"
                days == 1L -> "فردا"
                else -> Jalali.fromEpochDay(d.epochDay).formatShort()
            }
            "$whenText: ${d.title} · ${Money.format(d.amount, unit)}"
        }
        app.notifier.reminder("سررسیدهای نزدیک", lines)
        return Result.success()
    }

    companion object {
        fun schedule(context: Context) {
            val req = PeriodicWorkRequestBuilder<ReminderWorker>(1, TimeUnit.DAYS).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork("reminders", ExistingPeriodicWorkPolicy.KEEP, req)
        }
    }
}
