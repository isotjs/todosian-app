package com.isotjs.todosian

import android.app.Application
import com.isotjs.todosian.data.FileRepository
import com.isotjs.todosian.data.PreferencesManager
import com.isotjs.todosian.data.SafFileRepository
import com.isotjs.todosian.data.settings.AppSettingsRepository
import com.isotjs.todosian.data.settings.SharedPrefsAppSettingsRepository

class TodosianApplication : Application() {
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
}
