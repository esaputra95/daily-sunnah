package id.kasirq.dailysunnah.home

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import id.kasirq.dailysunnah.MainActivity
import id.kasirq.dailysunnah.R
import id.kasirq.dailysunnah.ibadah.data.HijriCalendarPreferences
import id.kasirq.dailysunnah.ibadah.data.PrayerLocationStore
import id.kasirq.dailysunnah.ibadah.data.PrayerName
import id.kasirq.dailysunnah.ibadah.data.PrayerSettingsStore
import id.kasirq.dailysunnah.ibadah.data.PrayerTimesCalculator
import id.kasirq.dailysunnah.ibadah.data.formatTimeUntil
import id.kasirq.dailysunnah.ibadah.data.formatPrayerTime
import id.kasirq.dailysunnah.ibadah.data.hijriLabel
import id.kasirq.dailysunnah.other.AppLanguagePreference
import id.kasirq.dailysunnah.other.AppSettingsStore
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

class DailySunnahWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetIds.forEach { id -> manager.updateAppWidget(id, createViews(context)) }
    }

    companion object {
        fun updateAll(context: Context) {
            val appContext = context.applicationContext
            val manager = AppWidgetManager.getInstance(appContext)
            val component = ComponentName(appContext, DailySunnahWidgetProvider::class.java)
            val ids = manager.getAppWidgetIds(component)
            if (ids.isNotEmpty()) DailySunnahWidgetProvider().onUpdate(appContext, manager, ids)
        }

        private fun createViews(context: Context): RemoteViews {
            val english = AppSettingsStore(context).language() == AppLanguagePreference.ENGLISH
            val now = LocalDateTime.now()
            val coordinates = PrayerLocationStore.read(context)
            val settings = PrayerSettingsStore.read(context)
            val prayerDay = coordinates?.let {
                PrayerTimesCalculator.calculate(now.toLocalDate(), it.latitude, it.longitude, correctionMinutes = settings.correctionMinutes)
            }
            val nextPrayer = coordinates?.let {
                PrayerTimesCalculator.nextPrayer(now, it.latitude, it.longitude, correctionMinutes = settings.correctionMinutes)
            }
            val currentPrayer = prayerDay?.events.orEmpty()
                .filter { it.name != PrayerName.TERBIT && !it.dateTime.isAfter(now) }
                .maxByOrNull { it.dateTime }
            val afterMaghrib = prayerDay?.event(PrayerName.MAGHRIB)?.dateTime?.let { now >= it } == true
            val hijriDate = now.toLocalDate().plusDays(if (afterMaghrib) 1 else 0)
            val hijri = hijriLabel(hijriDate, HijriCalendarPreferences.offsetDays(context), english)
            val locale = Locale.forLanguageTag(if (english) "en-US" else "id-ID")
            val dateLabel = now.toLocalDate()
                .format(DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", locale))
                .replaceFirstChar { it.uppercase(locale) }
            val views = RemoteViews(context.packageName, R.layout.widget_daily_sunnah).apply {
                setTextViewText(R.id.widget_date, dateLabel)
                setTextViewText(R.id.widget_hijri_day, hijri.substringBefore(' '))
                setTextViewText(R.id.widget_hijri_month, hijri.substringAfter(' '))
                setTextViewText(R.id.widget_next_label, if (english) "NEXT PRAYER" else "SHALAT BERIKUTNYA")
                setTextViewText(R.id.widget_prayer_name, nextPrayer?.name?.let { if (english) it.englishLabel else it.label }
                    ?: if (english) "Set prayer location" else "Atur lokasi shalat")
                setTextViewText(
                    R.id.widget_countdown,
                    nextPrayer?.let { formatTimeUntil(it.dateTime, now) }
                        ?: if (english) "Enable precise location to view prayer times" else "Aktifkan lokasi presisi untuk melihat jadwal",
                )
                setTextViewText(R.id.widget_prayer_time, nextPrayer?.let { formatPrayerTime(it.dateTime) } ?: "--:--")
                setTextViewText(
                    R.id.widget_current_prayer,
                    if (english) "${currentPrayer?.name?.englishLabel ?: "Today"} now"
                    else "${currentPrayer?.name?.label ?: "Hari ini"} sekarang",
                )
            }
            val openApp = PendingIntent.getActivity(
                context,
                8001,
                Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            views.setOnClickPendingIntent(R.id.widget_root, openApp)
            return views
        }
    }
}
