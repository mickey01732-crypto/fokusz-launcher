package hu.miki.fokusz

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

// ---------- napi piac (Yahoo Finance napi záró/aktuális ár) ----------

data class Ticker(val label: String, val symbol: String)
data class Quote(val label: String, val price: Double, val changePct: Double?, val decimals: Int)

object Market {
    private const val DEFAULT = "EUR/HUF|EURHUF=X;USD/HUF|USDHUF=X;BUX|^BUX.BD"

    fun tickers(ctx: Context): List<Ticker> =
        prefs(ctx).getString("mkt_list", DEFAULT)!!.split(';')
            .mapNotNull { it.split('|').takeIf { p -> p.size == 2 }?.let { p -> Ticker(p[0], p[1]) } }

    fun saveTickers(ctx: Context, list: List<Ticker>) {
        prefs(ctx).edit().putString("mkt_list", list.joinToString(";") { "${it.label}|${it.symbol}" })
            .remove("mkt_time").apply()
    }

    fun cached(ctx: Context): Pair<List<Quote>, Long> {
        val p = prefs(ctx)
        val arr = try { JSONArray(p.getString("mkt_data", null) ?: "[]") } catch (_: Exception) { JSONArray() }
        val out = (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            Quote(
                o.getString("l"), o.getDouble("p"),
                if (o.has("c")) o.getDouble("c") else null, o.getInt("d"),
            )
        }
        return out to p.getLong("mkt_time", 0)
    }

    /** Naponta egyszer frissít (vagy ha [force]). */
    fun refresh(ctx: Context, force: Boolean, done: () -> Unit) {
        val p = prefs(ctx)
        val last = p.getLong("mkt_time", 0)
        val day = SimpleDateFormat("yyyyMMdd", Locale.US)
        if (!force && last > 0 && day.format(Date(last)) == day.format(Date())) return
        val list = tickers(ctx)
        Thread {
            val arr = JSONArray()
            for (t in list) {
                fetch(t)?.let { q ->
                    arr.put(JSONObject().put("l", q.label).put("p", q.price).put("d", q.decimals).apply {
                        q.changePct?.let { put("c", it) }
                    })
                }
            }
            if (arr.length() > 0) {
                p.edit().putString("mkt_data", arr.toString()).putLong("mkt_time", System.currentTimeMillis()).apply()
            }
            done()
        }.start()
    }

    private fun fetch(t: Ticker): Quote? {
        val sym = java.net.URLEncoder.encode(t.symbol, "UTF-8")
        for (host in listOf("query1", "query2")) {
            try {
                val c = URL("https://$host.finance.yahoo.com/v8/finance/chart/$sym?range=5d&interval=1d")
                    .openConnection() as HttpURLConnection
                c.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14)")
                c.connectTimeout = 8000
                c.readTimeout = 8000
                if (c.responseCode != 200) { c.disconnect(); continue }
                val body = c.inputStream.bufferedReader().use { it.readText() }
                c.disconnect()
                val meta = JSONObject(body).getJSONObject("chart").getJSONArray("result")
                    .getJSONObject(0).getJSONObject("meta")
                val price = meta.getDouble("regularMarketPrice")
                val prev = meta.optDouble("chartPreviousClose", Double.NaN)
                    .takeIf { !it.isNaN() } ?: meta.optDouble("previousClose", Double.NaN)
                val chg = if (!prev.isNaN() && prev != 0.0) (price - prev) / prev * 100 else null
                val dec = if (price >= 1000) 0 else 2
                return Quote(t.label, price, chg, dec)
            } catch (_: Exception) {
            }
        }
        return null
    }

    fun fmt(q: Quote): String {
        val nf = java.text.NumberFormat.getNumberInstance(Locale("hu"))
        nf.minimumFractionDigits = q.decimals
        nf.maximumFractionDigits = q.decimals
        return nf.format(q.price)
    }

    fun fmtChange(q: Quote): String? = q.changePct?.let {
        val sign = if (it > 0) "+" else if (it < 0) "−" else ""
        sign + String.format(Locale("hu"), "%.1f%%", kotlin.math.abs(it))
    }

    private fun prefs(ctx: Context) = ctx.getSharedPreferences("fokusz", Context.MODE_PRIVATE)
}

// ---------- könyves gondolatok (a felhasználó saját jegyzetei) ----------

data class BookNote(val text: String, val source: String)

object BookNotes {
    val SOURCES = listOf("Határok a vezetésben", "Atomi szokások", "A befektetés művészete")

    fun all(ctx: Context): List<BookNote> {
        val arr = try { JSONArray(prefs(ctx).getString("book_notes", null) ?: "[]") } catch (_: Exception) { JSONArray() }
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            BookNote(o.getString("t"), o.optString("s"))
        }
    }

    private fun save(ctx: Context, list: List<BookNote>) {
        val arr = JSONArray()
        list.forEach { arr.put(JSONObject().put("t", it.text).put("s", it.source)) }
        prefs(ctx).edit().putString("book_notes", arr.toString()).apply()
    }

    fun add(ctx: Context, n: BookNote) {
        save(ctx, all(ctx) + n)
        prefs(ctx).edit().putString("book_last_source", n.source).apply()
    }

    fun remove(ctx: Context, index: Int) {
        val l = all(ctx).toMutableList()
        if (index in l.indices) l.removeAt(index)
        save(ctx, l)
    }

    fun lastSource(ctx: Context): String = prefs(ctx).getString("book_last_source", SOURCES[1])!!

    fun today(ctx: Context): BookNote? {
        val l = all(ctx)
        if (l.isEmpty()) return null
        return l[Calendar.getInstance().get(Calendar.DAY_OF_YEAR) % l.size]
    }

    private fun prefs(ctx: Context) = ctx.getSharedPreferences("fokusz", Context.MODE_PRIVATE)
}
