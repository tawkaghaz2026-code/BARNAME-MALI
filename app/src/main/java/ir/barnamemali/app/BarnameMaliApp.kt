package ir.barnamemali.app

import android.app.Application
import ir.barnamemali.app.backup.Backup
import ir.barnamemali.app.data.AppDatabase
import ir.barnamemali.app.data.Repository
import ir.barnamemali.app.data.Settings
import ir.barnamemali.app.notify.Notifier
import ir.barnamemali.app.notify.ReminderWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class BarnameMaliApp : Application() {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    lateinit var database: AppDatabase
        private set
    lateinit var repository: Repository
        private set
    lateinit var notifier: Notifier
        private set
    lateinit var backup: Backup
        private set

    override fun onCreate() {
        super.onCreate()
        database = AppDatabase.open(this)
        notifier = Notifier(this).also { it.createChannels() }
        repository = Repository(database, Settings(this), notifier)
        backup = Backup(database)
        appScope.launch {
            repository.seedDefaults()
            repository.recheckPendingSms()
        }
        ReminderWorker.schedule(this)
    }
}
