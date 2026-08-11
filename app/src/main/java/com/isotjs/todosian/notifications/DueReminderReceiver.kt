package com.isotjs.todosian.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class DueReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        scope.launch {
            try {
                when (intent.action) {
                    DueReminderScheduler.ACTION_DUE_REMINDER -> {
                        DueReminderNotifier.checkAndNotify(context)
                        DueReminderScheduler.reschedule(context)
                    }
                    Intent.ACTION_BOOT_COMPLETED -> DueReminderScheduler.reschedule(context)
                }
            } finally {
                pendingResult.finish()
                scope.cancel()
            }
        }
    }
}
