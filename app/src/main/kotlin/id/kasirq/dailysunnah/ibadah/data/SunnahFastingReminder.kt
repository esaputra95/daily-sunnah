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
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

class SunnahFastingReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_REMIND) showReminder(context)
        SunnahFastingReminderScheduler.schedule(context)
    }

    private fun showReminder(context: Context) {
        if (Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26 && manager.getNotificationChannel(CHANNEL_ID) == null) {
            manager.createNotificationChannel(NotificationChannel(CHANNEL_ID, "Pengingat puasa sunnah", NotificationManager.IMPORTANCE_DEFAULT))
        }
        val open = PendingIntent.getActivity(
            context, 7201, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = android.app.Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_my_calendar)
            .setContentTitle("Pengingat puasa sunnah")
            .setContentText("Besok Senin/Kamis. Periksa kalender dan kondisi pribadi sebelum berpuasa.")
            .setContentIntent(open).setAutoCancel(true).build()
        manager.notify(NOTIFICATION_ID, notification)
    }

    companion object {
        const val ACTION_REMIND = "id.kasirq.dailysunnah.SUNNAH_FASTING_REMINDER"
        const val CHANNEL_ID = "sunnah-fasting-reminder"
        const val REQUEST_CODE = 7201
        const val NOTIFICATION_ID = 7202
    }
}

class SunnahFastingReminderBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        SunnahFastingReminderScheduler.schedule(context)
    }
}

object SunnahFastingReminderScheduler {
    fun schedule(context: Context, now: LocalDateTime = LocalDateTime.now()) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pending = PendingIntent.getBroadcast(
            context, SunnahFastingReminderReceiver.REQUEST_CODE,
            Intent(context, SunnahFastingReminderReceiver::class.java).setAction(SunnahFastingReminderReceiver.ACTION_REMIND),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val prefs = context.getSharedPreferences("sunnah-fasting-reminder", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("enabled", false)) {
            alarmManager.cancel(pending)
            return
        }
        val timeParts = (prefs.getString("time", "17:00") ?: "17:00").split(':')
        val time = runCatching { LocalTime.of(timeParts[0].toInt(), timeParts[1].toInt()) }.getOrDefault(LocalTime.of(17, 0))
        val candidates = (0..14).asSequence().map { now.toLocalDate().plusDays(it.toLong()) }
            .filter { date -> date.dayOfWeek == DayOfWeek.SUNDAY || date.dayOfWeek == DayOfWeek.WEDNESDAY }
            .map { it.atTime(time) }
            .filter { it.isAfter(now) }
        val next = candidates.firstOrNull() ?: now.toLocalDate().plusDays(14).atTime(time)
        alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(), pending)
    }
}
