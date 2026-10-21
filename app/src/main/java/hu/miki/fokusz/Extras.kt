package hu.miki.fokusz

import android.app.AlarmManager
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.provider.CalendarContract
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

// ---------- fókusz-időzítő ----------

object Focus {
    private const val CHANNEL = "fokusz_vege"

    fun start(ctx: Context, minutes: Int) {
        val now = System.currentTimeMillis()
        val end = now + minutes * 60_000L
        ctx.getSharedPreferences("fokusz", Context.MODE_PRIVATE).edit()
            .putLong("focus_start", now).putLong("focus_end", end).apply()
        val am = ctx.getSystemService(AlarmManager::class.java)
        val pi = pending(ctx)
        try {
            if (am.canScheduleExactAlarms()) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, end, pi)
            else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, end, pi)
        } catch (_: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, end, pi)
        }
    }

    fun stop(ctx: Context) {
        ctx.getSharedPreferences("fokusz", Context.MODE_PRIVATE).edit()
            .remove("focus_start").remove("focus_end").apply()
        ctx.getSystemService(AlarmManager::class.java).cancel(pending(ctx))
    }

    fun end(ctx: Context): Long {
        val e = ctx.getSharedPreferences("fokusz", Context.MODE_PRIVATE).getLong("focus_end", 0L)
        return if (e > System.currentTimeMillis()) e else 0L
    }

    private fun pending(ctx: Context): PendingIntent = PendingIntent.getBroadcast(
        ctx, 7, Intent(ctx, FocusReceiver::class.java),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    fun notifyDone(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, "Fókuszblokk vége", NotificationManager.IMPORTANCE_HIGH).apply {
                    setSound(null, null)
                    enableVibration(true)
                    vibrationPattern = longArrayOf(0, 180, 120, 180)
                }
            )
        }
        val open = PendingIntent.getActivity(
            ctx, 8, Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val n = Notification.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle("Fókuszblokk vége")
            .setContentText("Szép munka. Tarts egy rövid szünetet.")
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        try { nm.notify(7, n) } catch (_: SecurityException) {}
    }
}

class FocusReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent?) {
        val p = ctx.getSharedPreferences("fokusz", Context.MODE_PRIVATE)
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        val days = (p.getStringSet("focus_days", emptySet()) ?: emptySet()).toMutableSet()
        days.add(today)
        // csak az utolsó ~40 napot őrizzük
        val keep = days.sorted().takeLast(40).toSet()
        p.edit().remove("focus_start").remove("focus_end").putStringSet("focus_days", keep).apply()
        Focus.notifyDone(ctx)
    }
}

object Chain {
    /** Hétfőtől vasárnapig: volt-e aznap befejezett fókuszblokk. */
    fun week(ctx: Context): Pair<BooleanArray, Int> {
        val days = ctx.getSharedPreferences("fokusz", Context.MODE_PRIVATE)
            .getStringSet("focus_days", emptySet()) ?: emptySet()
        val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val c = Calendar.getInstance()
        val todayIdx = (c.get(Calendar.DAY_OF_WEEK) + 5) % 7 // hétfő = 0
        c.add(Calendar.DAY_OF_YEAR, -todayIdx)
        val out = BooleanArray(7)
        for (i in 0 until 7) {
            out[i] = fmt.format(c.time) in days
            c.add(Calendar.DAY_OF_YEAR, 1)
        }
        return out to todayIdx
    }
}

// ---------- autós mód (Bluetooth) ----------

object Car {
    fun isOn(ctx: Context): Boolean {
        val p = ctx.getSharedPreferences("fokusz", Context.MODE_PRIVATE)
        if (p.getString("car_addr", null) == null) return false
        if (System.currentTimeMillis() < p.getLong("car_snooze_until", 0)) return false
        return p.getBoolean("car_connected", false)
    }
}

class CarReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val p = ctx.getSharedPreferences("fokusz", Context.MODE_PRIVATE)
        val addr = p.getString("car_addr", null) ?: return
        when (intent.action) {
            BluetoothAdapter.ACTION_STATE_CHANGED -> {
                if (intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, -1) == BluetoothAdapter.STATE_OFF)
                    p.edit().putBoolean("car_connected", false).apply()
            }
            else -> {
                @Suppress("DEPRECATION")
                val dev: BluetoothDevice? = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                if (dev?.address != addr) return
                val connected = intent.action == BluetoothDevice.ACTION_ACL_CONNECTED
                p.edit().putBoolean("car_connected", connected).remove("car_snooze_until").apply()
            }
        }
        ctx.sendBroadcast(Intent("hu.miki.fokusz.CAR_CHANGED").setPackage(ctx.packageName))
    }
}

// ---------- időjárás (Békéscsaba, Open-Meteo) ----------

object Weather {
    private const val URL_STR =
        "https://api.open-meteo.com/v1/forecast?latitude=46.68&longitude=21.09" +
            "&current=temperature_2m,weather_code&timezone=Europe%2FBudapest"

    fun cached(ctx: Context): String? {
        val p = ctx.getSharedPreferences("fokusz", Context.MODE_PRIVATE)
        val age = System.currentTimeMillis() - p.getLong("wx_time", 0)
        return if (age < 3 * 60 * 60_000L) p.getString("wx_text", null) else null
    }

    fun refreshIfStale(ctx: Context, done: (String?) -> Unit) {
        val p = ctx.getSharedPreferences("fokusz", Context.MODE_PRIVATE)
        if (System.currentTimeMillis() - p.getLong("wx_time", 0) < 30 * 60_000L) return
        Thread {
            try {
                val c = URL(URL_STR).openConnection() as HttpURLConnection
                c.connectTimeout = 8000
                c.readTimeout = 8000
                val body = c.inputStream.bufferedReader().use { it.readText() }
                c.disconnect()
                val cur = JSONObject(body).getJSONObject("current")
                val t = cur.getDouble("temperature_2m").roundToInt()
                val text = "$t° ${describe(cur.getInt("weather_code"))}"
                p.edit().putString("wx_text", text).putLong("wx_time", System.currentTimeMillis()).apply()
                done(text)
            } catch (_: Exception) {
                done(null)
            }
        }.start()
    }

    private fun describe(code: Int) = when (code) {
        0 -> "derült"
        1 -> "többnyire derült"
        2 -> "részben felhős"
        3 -> "borús"
        45, 48 -> "ködös"
        51, 53, 55 -> "szitálás"
        56, 57, 66, 67 -> "ónos eső"
        61, 63 -> "eső"
        65 -> "erős eső"
        71, 73, 75, 77 -> "havazás"
        80, 81, 82 -> "zápor"
        85, 86 -> "hózápor"
        95 -> "zivatar"
        96, 99 -> "jégeső"
        else -> ""
    }
}

// ---------- következő naptáresemény ----------

data class NextEvent(val id: Long, val begin: Long, val end: Long, val title: String, val location: String?)

object Agenda {
    fun next(ctx: Context): NextEvent? {
        val now = System.currentTimeMillis()
        val b = CalendarContract.Instances.CONTENT_URI.buildUpon()
        ContentUris.appendId(b, now - 12 * 60 * 60_000L)
        ContentUris.appendId(b, now + 24 * 60 * 60_000L)
        val proj = arrayOf(
            CalendarContract.Instances.EVENT_ID,
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.END,
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.EVENT_LOCATION,
        )
        val sel = "${CalendarContract.Instances.ALL_DAY} = 0 AND " +
            "${CalendarContract.Instances.VISIBLE} = 1 AND " +
            "${CalendarContract.Instances.END} > ? AND " +
            "${CalendarContract.Instances.BEGIN} < ? AND " +
            "${CalendarContract.Instances.SELF_ATTENDEE_STATUS} != ${CalendarContract.Attendees.ATTENDEE_STATUS_DECLINED}"
        val args = arrayOf(now.toString(), (now + 24 * 60 * 60_000L).toString())
        return try {
            ctx.contentResolver.query(
                b.build(), proj, sel, args, "${CalendarContract.Instances.BEGIN} ASC",
            )?.use { c ->
                if (!c.moveToFirst()) null
                else NextEvent(
                    c.getLong(0), c.getLong(1), c.getLong(2),
                    c.getString(3)?.trim().orEmpty().ifEmpty { "Esemény" },
                    c.getString(4)?.trim()?.takeIf { it.isNotEmpty() },
                )
            }
        } catch (_: Exception) {
            null
        }
    }

    fun format(e: NextEvent, discreet: Boolean): String {
        val now = System.currentTimeMillis()
        val hm = SimpleDateFormat("H:mm", Locale("hu")).format(Date(e.begin))
        val today = Calendar.getInstance()
        val day = Calendar.getInstance().apply { timeInMillis = e.begin }
        val time = when {
            e.begin <= now -> "Most"
            day.get(Calendar.DAY_OF_YEAR) != today.get(Calendar.DAY_OF_YEAR) -> "Holnap $hm"
            else -> hm
        }
        val place = e.location?.substringBefore(',')?.trim()
        return if (discreet) "$time · ${place ?: "esemény"}"
        else "$time · ${e.title}" + (place?.let { ", $it" } ?: "")
    }
}
