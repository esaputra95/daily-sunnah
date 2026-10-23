package id.kasirq.dailysunnah.ibadah.data

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import id.kasirq.dailysunnah.MainActivity
import id.kasirq.dailysunnah.other.AppLanguagePreference
import id.kasirq.dailysunnah.other.AppSettingsStore
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.chrono.HijrahDate
import java.time.temporal.ChronoField

data class RamadanReminderSettings(
    val sahurEnabled: Boolean = false,
    val iftarEnabled: Boolean = false,
)

object RamadanReminderPreferences {
    private const val FILE_NAME = "ramadan-reminders"

    fun read(context: Context) = context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE).let { preferences ->
        RamadanReminderSettings(
            sahurEnabled = preferences.getBoolean("sahur", false),
            iftarEnabled = preferences.getBoolean("iftar", false),
        )
    }

    fun save(context: Context, settings: RamadanReminderSettings) {
        context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE).edit()
            .putBoolean("sahur", settings.sahurEnabled)
            .putBoolean("iftar", settings.iftarEnabled)
            .apply()
    }
}

object RamadanReminderScheduler {
    fun scheduleNext(context: Context, now: LocalDateTime = LocalDateTime.now()) {
        val settings = RamadanReminderPreferences.read(context)
        schedule(context, ReminderKind.SAHUR, settings.sahurEnabled, now)
        schedule(context, ReminderKind.IFTAR, settings.iftarEnabled, now)
    }

    private fun schedule(context: Context, kind: ReminderKind, enabled: Boolean, now: LocalDateTime) {
        val manager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pendingIntent = pendingIntent(context, kind)
        if (!enabled) {
            manager.cancel(pendingIntent)
            return
        }
        val coordinates = PrayerLocationStore.read(context) ?: run {
            manager.cancel(pendingIntent)
            return
        }
        val corrections = PrayerSettingsStore.read(context).correctionMinutes
        val offset = HijriCalendarPreferences.offsetDays(context)
        val next = (0L..370L).asSequence().mapNotNull { delta ->
            val date = now.toLocalDate().plusDays(delta)
            val hijri = HijrahDate.from(date.plusDays(offset.toLong()))
            if (hijri.get(ChronoField.MONTH_OF_YEAR) != 9) return@mapNotNull null
            val prayers = PrayerTimesCalculator.calculate(date, coordinates.latitude, coordinates.longitude, correctionMinutes = corrections)
                ?: return@mapNotNull null
            val prayer = if (kind == ReminderKind.SAHUR) prayers.event(PrayerName.SUBUH)?.dateTime?.minusMinutes(10)?.minusMinutes(20)
                else prayers.event(PrayerName.MAGHRIB)?.dateTime?.minusMinutes(10)
            prayer?.takeIf { it.isAfter(now) }
        }.firstOrNull()
        if (next == null) {
            manager.cancel(pendingIntent)
            return
        }
        manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli(), pendingIntent)
    }

    private fun pendingIntent(context: Context, kind: ReminderKind): PendingIntent = PendingIntent.getBroadcast(
        context,
        if (kind == ReminderKind.SAHUR) REQUEST_SAHUR else REQUEST_IFTAR,
        Intent(context, RamadanReminderReceiver::class.java)
            .setAction(ACTION_REMIND)
            .putExtra(EXTRA_KIND, kind.name),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private enum class ReminderKind { SAHUR, IFTAR }

    private const val ACTION_REMIND = "id.kasirq.dailysunnah.RAMADAN_REMINDER"
    private const val EXTRA_KIND = "reminder-kind"
    private const val REQUEST_SAHUR = 7301
    private const val REQUEST_IFTAR = 7302
    private const val CHANNEL_ID = "ramadan-reminders"

    class RamadanReminderReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val kind = intent.getStringExtra(EXTRA_KIND)?.let { runCatching { ReminderKind.valueOf(it) }.getOrNull() }
            if (kind != null) showReminder(context, kind)
            scheduleNext(context)
        }

        private fun showReminder(context: Context, kind: ReminderKind) {
            if (Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
            val english = AppSettingsStore(context).language() == AppLanguagePreference.ENGLISH
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(NotificationChannel(
                CHANNEL_ID,
                if (english) "Ramadan reminders" else "Pengingat Ramadan",
                NotificationManager.IMPORTANCE_DEFAULT,
            ))
            val openApp = PendingIntent.getActivity(
                context,
                if (kind == ReminderKind.SAHUR) REQUEST_SAHUR else REQUEST_IFTAR,
                Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val sahur = kind == ReminderKind.SAHUR
            val title = when {
                english && sahur -> "Sahur reminder"
                english -> "Time to prepare for iftar"
                sahur -> "Pengingat sahur"
                else -> "Waktunya bersiap berbuka"
            }
            val body = when {
                english && sahur -> "Estimated imsak is approaching. Check your local prayer schedule."
                english -> "Maghrib is approaching. Check your local prayer schedule."
                sahur -> "Perkiraan imsak segera tiba. Sesuaikan dengan jadwal shalat setempat."
                else -> "Waktu Maghrib segera tiba. Sesuaikan dengan jadwal shalat setempat."
            }
            manager.notify(if (sahur) REQUEST_SAHUR else REQUEST_IFTAR, android.app.Notification.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle(title)
                .setContentText(body)
                .setContentIntent(openApp)
                .setAutoCancel(true)
                .build())
        }
    }

    class RamadanReminderBootReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action in setOf(Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED, Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED)) {
                scheduleNext(context)
            }
        }
    }
}
