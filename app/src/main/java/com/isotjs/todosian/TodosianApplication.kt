package com.isotjs.todosian

import android.app.Application
import com.isotjs.todosian.data.FileRepository
import com.isotjs.todosian.data.PreferencesManager
import com.isotjs.todosian.data.SafFileRepository
import com.isotjs.todosian.data.settings.AppSettingsRepository
import com.isotjs.todosian.data.settings.SharedPrefsAppSettingsRepository
import com.isotjs.todosian.notifications.DueReminderScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class TodosianApplication : Application() {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val preferencesManager: PreferencesManager by lazy {
        PreferencesManager(applicationContext)
    }

    val fileRepository: FileRepository by lazy {
        SafFileRepository(
            appContext = applicationContext,
            preferencesManager = preferencesManager,
        )
    }

    val appSettingsRepository: AppSettingsRepository by lazy {
        SharedPrefsAppSettingsRepository(applicationContext)
    }

    override fun onCreate() {
        super.onCreate()

        appScope.launch {
            appSettingsRepository.settings
                .map { it.enableTasksPluginSupport }
                .distinctUntilChanged()
                .collect { enabled ->
                    DueReminderScheduler.sync(applicationContext, enabled)
                }
        }
    }

    override fun onTerminate() {
        super.onTerminate()
        appScope.cancel()
    }
}
