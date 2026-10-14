package id.kasirq.dailysunnah.quran.data

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
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

class ReadingGoalReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val snapshot = ReadingGoalStore(context).load()
        try {
            if (!snapshot.isComplete && snapshot.reminderMinutes.isNotEmpty()) {
                showReminder(context, snapshot.completedCount, snapshot.targetPages)
            }
        } finally {
            ReadingGoalReminderScheduler.scheduleNext(context)
        }
    }

    private fun showReminder(context: Context, completed: Int, target: Int) {
        if (Build.VERSION.SDK_INT >= 33 &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Target bacaan Qur’an", NotificationManager.IMPORTANCE_DEFAULT)
                .apply { description = "Pengingat target bacaan harian yang kamu atur" },
        )
        val openApp = PendingIntent.getActivity(
            context,
            NOTIFICATION_ID,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = android.app.Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_my_calendar)
            .setContentTitle("Target bacaan hari ini belum tercapai")
            .setContentText("$completed dari $target halaman selesai. Lanjutkan bacaanmu.")
            .setContentIntent(openApp)
            .setAutoCancel(true)
            .build()
        manager.notify(NOTIFICATION_ID, notification)
    }

    companion object {
        const val CHANNEL_ID = "quran-reading-goal"
        const val NOTIFICATION_ID = 2401
        const val REQUEST_CODE = 2402
    }
}

class ReadingGoalBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == Intent.ACTION_MY_PACKAGE_REPLACED ||
            intent.action == Intent.ACTION_TIME_CHANGED ||
            intent.action == Intent.ACTION_TIMEZONE_CHANGED
        ) {
            ReadingGoalReminderScheduler.scheduleNext(context)
        }
    }
}

object ReadingGoalReminderScheduler {
    fun scheduleNext(context: Context, now: LocalDateTime = LocalDateTime.now()) {
        val manager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pendingIntent = pendingIntent(context)
        val snapshot = ReadingGoalStore(context).load(now.toLocalDate())
        if (snapshot.isComplete || snapshot.reminderMinutes.isEmpty()) {
            manager.cancel(pendingIntent)
            return
        }

        val next = ReadingGoalSchedule.nextOccurrence(now.toLocalDate(), now.toLocalTime(), snapshot.reminderMinutes)
            ?: run {
                manager.cancel(pendingIntent)
                return
            }
        manager.setAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            next.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),
            pendingIntent,
        )
    }

    fun cancel(context: Context) {
        val manager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        manager.cancel(pendingIntent(context))
    }

    private fun pendingIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context,
        ReadingGoalReminderReceiver.REQUEST_CODE,
        Intent(context, ReadingGoalReminderReceiver::class.java).setAction(ACTION_REMIND),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private const val ACTION_REMIND = "id.kasirq.dailysunnah.QURAN_READING_GOAL_REMINDER"
}

object ReadingGoalSchedule {
    fun nextOccurrence(date: LocalDate, time: LocalTime, reminderMinutes: List<Int>): LocalDateTime? {
        val now = date.atTime(time)
        val valid = reminderMinutes.distinct().filter { it in 0..1439 }.sorted()
        return valid.asSequence()
            .map { minute -> date.atTime(minute / 60, minute % 60) }
            .firstOrNull { it.isAfter(now) }
            ?: valid.firstOrNull()?.let { date.plusDays(1).atTime(it / 60, it % 60) }
    }
}
