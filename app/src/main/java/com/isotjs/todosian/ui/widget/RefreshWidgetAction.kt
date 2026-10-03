package com.isotjs.todosian.ui.widget

import android.content.Context
import android.util.Log
import androidx.annotation.Keep
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback

@Keep
class RefreshWidgetAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        Log.d(TAG, "Manual refresh requested for glanceId=$glanceId")
        WidgetUpdater.updateAllWidgets(context)
    }

    companion object {
        private const val TAG = "RefreshWidgetAction"
    }
}
