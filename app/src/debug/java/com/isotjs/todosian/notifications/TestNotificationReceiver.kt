package com.isotjs.todosian.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import java.time.LocalDate

// adb shell am broadcast -n com.isotjs.todosian.debug/com.isotjs.todosian.notifications.TestNotificationReceiver

class TestNotificationReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        DueReminderNotifier.show(
            context,
            DueReminderNotifier.Payload(
                totalCount = 1,
                nextDueDate = LocalDate.now(),
                topTodoText = "Test due task",
            ),
        )
    }
}