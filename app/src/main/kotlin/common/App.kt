package common

import android.app.Application
import android.content.Intent
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import api.NewsApiSwitcher
import co.appreactor.news.BuildConfig
import com.squareup.picasso.OkHttp3Downloader
import com.squareup.picasso.Picasso
import injections.appModule
import kotlinx.coroutines.runBlocking
import org.koin.android.ext.android.get
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import sync.SyncWorker
import timber.log.Timber
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.util.concurrent.TimeUnit
import kotlin.system.exitProcess

class App : Application() {

    override fun onCreate() {
        super.onCreate()

        startKoin {
            androidContext(this@App)
            modules(appModule)
        }

        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }

        runBlocking {
            val authType = get<ConfRepository>().get().authType

            if (authType.isNotBlank()) {
                get<NewsApiSwitcher>().switch(authType)
                setupBackgroundSync(override = false)
            }
        }

        val picasso = Picasso.Builder(this)
            .downloader(OkHttp3Downloader(File(externalCacheDir, "images")))
            .build()

        Picasso.setSingletonInstance(picasso)

        if (BuildConfig.DEBUG) {
            val oldHandler = Thread.getDefaultUncaughtExceptionHandler()

            Thread.setDefaultUncaughtExceptionHandler { t, e ->
                runCatching {
                    val sw = StringWriter()
                    e.printStackTrace(PrintWriter(sw))

                    val intent = Intent(Intent.ACTION_SEND).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        putExtra(Intent.EXTRA_TEXT, sw.toString())
                        type = "text/plain"
                    }

                    startActivity(intent)
                }.onFailure {
                    it.printStackTrace()
                }

                if (oldHandler != null) {
                    oldHandler.uncaughtException(t, e)
                } else {
                    exitProcess(1)
                }
            }
        }
    }

    fun setupBackgroundSync(override: Boolean) {
        val workManager = WorkManager.getInstance(this)
        val prefs = runBlocking { get<ConfRepository>().get() }

        if (!prefs.syncInBackground) {
            workManager.cancelUniqueWork(SYNC_WORK_NAME)
            return
        }

        if (prefs.syncInBackground) {
            val policy = if (override) {
                ExistingPeriodicWorkPolicy.REPLACE
            } else {
                ExistingPeriodicWorkPolicy.KEEP
            }

            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.UNMETERED)
                .build()

            val periodicSyncRequest = PeriodicWorkRequestBuilder<SyncWorker>(
                repeatInterval = prefs.backgroundSyncIntervalMillis,
                repeatIntervalTimeUnit = TimeUnit.MILLISECONDS,
            )
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(this).enqueueUniquePeriodicWork(
                SYNC_WORK_NAME,
                policy,
                periodicSyncRequest,
            )
        }
    }

    companion object {
        const val DB_FILE_NAME = "news-v2.db"

        private const val SYNC_WORK_NAME = "sync"
    }
}