package com.example.habit_streak_tracker

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.SharedPreferences
import android.widget.RemoteViews
import es.antonborri.home_widget.HomeWidgetProvider
import org.json.JSONObject

class HabitAppWidgetProvider : HomeWidgetProvider() {
    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
        widgetData: SharedPreferences
    ) {
        for (appWidgetId in appWidgetIds) {
            val views = RemoteViews(context.packageName, R.layout.habit_widget_layout)

            val rawJson = widgetData.getString("habit_widget_payload", null)
            if (rawJson != null) {
                try {
                    val json = JSONObject(rawJson)
                    val completed = json.optInt("completedCount", 0)
                    val total = json.optInt("totalCount", 0)
                    views.setTextViewText(R.id.widget_progress, "$completed/$total")

                    val habitsArray = json.optJSONArray("habits")
                    if (habitsArray != null && habitsArray.length() > 0) {
                        val first = habitsArray.getJSONObject(0)
                        val title = first.optString("title", "")
                        val streak = first.optInt("streak", 0)
                        views.setTextViewText(R.id.widget_habit_summary, "🔥 $streak days: $title")
                    }
                } catch (e: Exception) {
                    views.setTextViewText(R.id.widget_habit_summary, "Keep your streak going!")
                }
            }

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }
}
