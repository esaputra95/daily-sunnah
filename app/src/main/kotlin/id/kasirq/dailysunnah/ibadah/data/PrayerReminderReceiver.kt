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
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import id.kasirq.dailysunnah.MainActivity
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class PrayerReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val isTest = intent.action == ACTION_TEST
        val settings = PrayerSettingsStore.read(context)
        if (isTest) {
            showNotification(context, settings, "Uji pengingat shalat", "Ini contoh notifikasi. Waktu shalat dihitung dari lokasi presisi.", TEST_NOTIFICATION_ID)
            return
        }

        val prayerName = intent.getStringExtra(EXTRA_PRAYER)?.let { runCatching { PrayerName.valueOf(it) }.getOrNull() }
        val preparation = intent.getBooleanExtra(EXTRA_PREPARATION, false)
        if (prayerName != null && settings.notificationFor(prayerName)) {
            val title = if (preparation) "Persiapan shalat ${prayerName.label}" else "Waktu shalat ${prayerName.label}"
            val body = if (preparation) "${settings.preparationMinutes} menit lagi menuju waktu ${prayerName.label}." else "Waktu ${prayerName.label} telah tiba."
            showNotification(context, settings, title, body, NOTIFICATION_ID + prayerName.ordinal)
        }
        PrayerReminderScheduler.scheduleNext(context)
    }

    private fun showNotification(context: Context, settings: PrayerSettings, title: String, message: String, notificationId: Int) {
        if (Build.VERSION.SDK_INT >= 33 &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = channelId(settings.soundMode)
        if (Build.VERSION.SDK_INT >= 26 && manager.getNotificationChannel(channelId) == null) {
            val channel = NotificationChannel(channelId, "Pengingat shalat · ${settings.soundMode.label}", NotificationManager.IMPORTANCE_DEFAULT)
                .apply {
                    description = "Jadwal shalat dan pengingat persiapan"
                    when (settings.soundMode) {
                        PrayerSoundMode.ADHAN -> setSound(
                            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
                            AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build(),
                        )
                        PrayerSoundMode.SHORT -> setSound(
                            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
                            AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION).build(),
                        )
                        PrayerSoundMode.SILENT -> setSound(null, null)
                    }
                }
            manager.createNotificationChannel(channel)
        }
        val openApp = PendingIntent.getActivity(
            context,
            notificationId,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = android.app.Notification.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(title)
            .setContentText(message)
            .setContentIntent(openApp)
            .setAutoCancel(true)
            .build()
        manager.notify(notificationId, notification)
    }

    companion object {
        const val ACTION_REMIND = "id.kasirq.dailysunnah.PRAYER_REMINDER"
        const val ACTION_TEST = "id.kasirq.dailysunnah.PRAYER_REMINDER_TEST"
        const val EXTRA_PRAYER = "prayer_name"
        const val EXTRA_PREPARATION = "is_preparation"
        const val NOTIFICATION_ID = 3100
        const val TEST_NOTIFICATION_ID = 3199
        const val REMINDER_REQUEST_CODE = 3101
        const val TEST_REQUEST_CODE = 3102
        fun channelId(mode: PrayerSoundMode) = "prayer-reminders-${mode.name.lowercase()}"
    }
}

object PrayerReminderScheduler {
    private data class Reminder(val at: LocalDateTime, val prayer: PrayerName, val preparation: Boolean)

    fun scheduleNext(context: Context, now: LocalDateTime = LocalDateTime.now()) {
        val manager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pending = reminderPendingIntent(context)
        val settings = PrayerSettingsStore.read(context)
        val coordinates = PrayerLocationStore.read(context)
        if (coordinates == null) {
            manager.cancel(pending)
            return
        }

        val next = (0..1).asSequence()
            .flatMap { dayOffset ->
                val date = now.toLocalDate().plusDays(dayOffset.toLong())
                PrayerTimesCalculator.calculate(
                    date, coordinates.latitude, coordinates.longitude,
                    correctionMinutes = settings.correctionMinutes,
                )?.events.orEmpty().asSequence()
                    .filter { it.name != PrayerName.TERBIT && settings.notificationFor(it.name) }
                    .flatMap { event ->
                        val prayerReminder = sequenceOf(Reminder(event.dateTime, event.name, preparation = false))
                        val prepReminder = if (settings.preparationMinutes > 0) {
                            sequenceOf(Reminder(event.dateTime.minusMinutes(settings.preparationMinutes.toLong()), event.name, preparation = true))
                        } else emptySequence()
                        prepReminder + prayerReminder
                    }
            }
            .filter { it.at.isAfter(now) }
            .minByOrNull { it.at }

        if (next == null) {
            manager.cancel(pending)
            return
        }
        val updatedPending = PendingIntent.getBroadcast(
            context,
            PrayerReminderReceiver.REMINDER_REQUEST_CODE,
            Intent(context, PrayerReminderReceiver::class.java)
                .setAction(PrayerReminderReceiver.ACTION_REMIND)
                .putExtra(PrayerReminderReceiver.EXTRA_PRAYER, next.prayer.name)
                .putExtra(PrayerReminderReceiver.EXTRA_PREPARATION, next.preparation),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        manager.setAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            next.at.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),
            updatedPending,
        )
    }

    fun scheduleTest(context: Context) {
        val manager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pending = PendingIntent.getBroadcast(
            context,
            PrayerReminderReceiver.TEST_REQUEST_CODE,
            Intent(context, PrayerReminderReceiver::class.java).setAction(PrayerReminderReceiver.ACTION_TEST),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        manager.setAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            System.currentTimeMillis() + 5_000L,
            pending,
        )
    }

    private fun reminderPendingIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context,
        PrayerReminderReceiver.REMINDER_REQUEST_CODE,
        Intent(context, PrayerReminderReceiver::class.java).setAction(PrayerReminderReceiver.ACTION_REMIND),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
}
