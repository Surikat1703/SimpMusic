package com.maxrave.simpmusic.service.mymix

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.maxrave.domain.manager.DataStoreManager
import com.maxrave.logger.Logger
import com.maxrave.simpmusic.ui.screen.library.MyMixPrefs
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

/**
 * Fork: keeps the cached "My Mix" set fresh.
 *
 * Mirrors [com.maxrave.simpmusic.service.backup.AutoBackupScheduler]: one periodic job whose
 * interval follows the user's setting, cancelled the moment auto-cache is turned off, plus a
 * separate one-shot job behind the "Refresh cache now" row.
 */
class MyMixCacheScheduler(
    private val context: Context,
    private val dataStoreManager: DataStoreManager,
) {
    private val workManager = WorkManager.getInstance(context)

    suspend fun observeAndSchedule() =
        coroutineScope {
            launch {
                combine(
                    dataStoreManager.getString(MyMixPrefs.AUTO_CACHE),
                    dataStoreManager.getString(MyMixPrefs.CACHE_INTERVAL_DAYS),
                ) { enabled, interval ->
                    (enabled == DataStoreManager.TRUE) to
                        (interval?.toIntOrNull() ?: MyMixPrefs.DEFAULT_INTERVAL_DAYS)
                }.distinctUntilChanged().collect { (enabled, intervalDays) ->
                    if (enabled) {
                        schedulePeriodic(intervalDays)
                    } else {
                        cancelPeriodic()
                    }
                }
            }
            launch {
                // The screen writes a unique value per tap, so every tap reaches here. The stored
                // value re-emits once on app start, which `drop(1)` swallows — otherwise merely
                // opening the app would re-cache the mix.
                dataStoreManager
                    .getString(MyMixPrefs.CACHE_REQUEST)
                    .drop(1)
                    .collect { request ->
                        if (!request.isNullOrEmpty()) {
                            enqueueNow()
                        }
                    }
            }
        }

    private fun schedulePeriodic(intervalDays: Int) {
        val constraints =
            Constraints
                .Builder()
                // Mobile and Wi-Fi both count as CONNECTED — the user asked for either.
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(true)
                .build()
        val request =
            PeriodicWorkRequestBuilder<MyMixCacheWorker>(
                intervalDays.coerceAtLeast(1).toLong(),
                TimeUnit.DAYS,
            ).setConstraints(constraints)
                .addTag(WORK_TAG)
                .build()
        Logger.d(TAG, "Scheduling My Mix cache every $intervalDays day(s)")
        workManager.enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    private fun cancelPeriodic() {
        Logger.d(TAG, "Cancelling My Mix cache")
        workManager.cancelUniqueWork(WORK_NAME)
    }

    private fun enqueueNow() {
        val constraints =
            Constraints
                .Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
        val request =
            OneTimeWorkRequestBuilder<MyMixCacheWorker>()
                .setConstraints(constraints)
                .setInputData(workDataOf(MyMixCacheWorker.KEY_FORCE to true))
                .addTag(WORK_TAG)
                .build()
        Logger.d(TAG, "My Mix cache requested now")
        workManager.enqueueUniqueWork(NOW_WORK_NAME, ExistingWorkPolicy.REPLACE, request)
    }

    companion object {
        private const val TAG = "MyMixCacheScheduler"
        private const val WORK_NAME = "my_mix_cache"
        private const val NOW_WORK_NAME = "my_mix_cache_now"
        private const val WORK_TAG = "my_mix_cache"
    }
}
