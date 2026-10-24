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

// ---------- könyves gondolatok ----------

/** [userIndex] >= 0: saját bejegyzés; [builtinId] >= 0: beépített gondolat. */
data class BookNote(
    val text: String,
    val source: String,
    val builtinId: Int = -1,
    val userIndex: Int = -1,
)

object BookNotes {
    val SOURCES = listOf(
        "Henry Cloud · Határok a vezetésben",
        "James Clear · Atomi szokások",
        "André Kostolany · A befektetés művészete",
        "Stephen R. Covey · A kiemelkedően sikeres emberek 7 szokása",
        "Daniel Goleman · Érzelmi intelligencia",
        "Patrick Lencioni · The Five Dysfunctions of a Team",
        "Jocko Willink, Leif Babin · Extreme Ownership",
        "Kim Scott · Radical Candor",
        "Simon Sinek · Start with Why",
        "John C. Maxwell · A vezetés 21 megcáfolhatatlan törvénye",
        "Ken Blanchard · Az egyperces menedzser",
        "Carol S. Dweck · Mindset",
        "Cal Newport · Deep Work",
    )

    /** Beépített gondolatok: a könyvek fő gondolatai saját megfogalmazásban, nem szó szerinti idézetek. */
    private val BUILTIN: List<Pair<Int, String>> = listOf(
        0 to "Ami nincs kizárva a csapat figyelméből, az elveszi az energiát a fontostól. A vezető dolga a fókusz őrzése.",
        0 to "Félelemben senki nem gondolkodik jól. A biztonságos légkör nem puhaság, hanem a teljesítmény feltétele.",
        0 to "Azt kapod, amit létrehozol, és amit eltűrsz.",
        0 to "A kudarcot magyarázd konkrétan, behatároltan és átmenetiként, így marad mozgástér a következő lépésre.",
        0 to "Arra figyelj, amit irányítani tudsz: a tevékenységre, nem az eredményre.",
        1 to "A cél megadja az irányt, de a napi rendszer dönti el, hová érsz.",
        1 to "Minden apró cselekedet egy szavazat arra, kivé válsz.",
        1 to "Napi egy százalék javulás egy év alatt sokszoros különbséget jelent.",
        1 to "A jó szokás legyen nyilvánvaló, vonzó, könnyű és kielégítő. A rossznál fordítsd meg mind a négyet.",
        1 to "Ha nehéz elkezdeni, kicsinyítsd két percre. Előbb a megjelenés szokása, aztán a tökéletesítés.",
        2 to "A befektetésben a türelem többet fizet, mint a gyorsaság.",
        2 to "Vegyél jó minőséget, aztán hagyd dolgozni. A hosszú távú befektető ellensége a kapkodás.",
        2 to "Rövid távon a hangulat mozgatja az árakat, hosszú távon a tények.",
        3 to "Kezdd a végét szem előtt tartva: előbb döntsd el, hová mész, aztán indulj.",
        3 to "A sürgős ritkán fontos, a fontos ritkán sürgős. A fontosra szánt idő formálja a jövőt.",
        3 to "Először próbálj megérteni, csak utána megértetni magad.",
        4 to "A vezető hangulata ragályos: amit érzel, az a csapatban felerősödik.",
        4 to "Amit nem veszel észre magadban, azt nem tudod irányítani. Az önismeret az első lépés.",
        4 to "Az empátia nem egyetértés, hanem annak megértése, mit él át a másik.",
        5 to "Bizalom nélkül nincs őszinte vita, és őszinte vita nélkül nincs valódi elköteleződés.",
        5 to "A jó csapat nem kerüli a konfliktust: az ötletekről vitatkozik, nem a személyekről.",
        5 to "A közös eredmény előbbre való, mint az egyéni csillogás.",
        6 to "Ha a csapat elakad, a vezető először magába néz.",
        6 to "Ha te vállalod a hibát, a csapat a megoldásra figyelhet, nem a védekezésre.",
        6 to "Ha minden fontos, semmi sem az. Rangsorolj, és egyszerre egy dolgot vigyél végig.",
        7 to "Törődj személyesen, és mondd ki egyenesen. A kettő csak együtt működik.",
        7 to "A dicséret legyen konkrét és nyilvános, a kritika időben és négyszemközt.",
        7 to "A hallgatás nem kedvesség: amit nem mondasz ki, abból a másik nem tud fejlődni.",
        8 to "Az emberek nem azért csatlakoznak, amit csinálsz, hanem azért, amiért csinálod.",
        8 to "Előbb a miért, aztán a hogyan, végül a mit.",
        8 to "Aki ugyanabban hisz, mint te, az nem csak a pénzért marad.",
        9 to "A vezetés lényege a hatás, nem a pozíció.",
        9 to "Az emberek előbb a vezetőt fogadják el, csak utána az elképzelését.",
        9 to "A vezető szintje a csapat plafonja. Ha te nősz, nő vele a csapat is.",
        10 to "Kapd rajta az embereket, amikor valamit jól csinálnak.",
        10 to "A jó cél rövid: egy perc alatt átolvasható, és mindenki érti.",
        10 to "A visszajelzés legyen azonnali, és a viselkedésről szóljon, ne az emberről.",
        11 to "A képesség nem adottság, hanem fejleszthető. A kudarc erre tanít.",
        11 to "Az erőfeszítést és a módszert dicsérd, ne a tehetséget.",
        11 to "A „még nem” sokkal erősebb, mint a „nem megy”.",
        12 to "Az elmélyült munka egyre ritkább, ezért egyre értékesebb.",
        12 to "Ne csak a munkát ütemezd, hanem a figyelemelterelést is.",
        12 to "A sekély feladatok kitöltik a napot, ha nem véded meg a mély órákat.",
    )

    fun user(ctx: Context): List<BookNote> {
        val arr = try { JSONArray(prefs(ctx).getString("book_notes", null) ?: "[]") } catch (_: Exception) { JSONArray() }
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            BookNote(o.getString("t"), o.optString("s"), userIndex = i)
        }
    }

    private fun saveUser(ctx: Context, list: List<BookNote>) {
        val arr = JSONArray()
        list.forEach { arr.put(JSONObject().put("t", it.text).put("s", it.source)) }
        prefs(ctx).edit().putString("book_notes", arr.toString()).apply()
    }

    private fun hidden(ctx: Context): Set<String> = prefs(ctx).getStringSet("book_hidden", emptySet()) ?: emptySet()

    /** Minden megjeleníthető gondolat: a nem elrejtett beépítettek és a saját bejegyzések. */
    fun pool(ctx: Context): List<BookNote> {
        val h = hidden(ctx)
        val built = BUILTIN.mapIndexedNotNull { id, (src, text) ->
            if (id.toString() in h) null else BookNote(text, SOURCES[src], builtinId = id)
        }
        return built + user(ctx)
    }

    fun add(ctx: Context, n: BookNote) {
        saveUser(ctx, user(ctx) + BookNote(n.text, n.source))
        prefs(ctx).edit().putString("book_last_source", n.source).apply()
    }

    /** Saját bejegyzés törlése, vagy beépített gondolat elrejtése. */
    fun remove(ctx: Context, n: BookNote) {
        if (n.userIndex >= 0) {
            val l = user(ctx).toMutableList()
            if (n.userIndex in l.indices) l.removeAt(n.userIndex)
            saveUser(ctx, l)
        } else if (n.builtinId >= 0) {
            prefs(ctx).edit().putStringSet("book_hidden", hidden(ctx) + n.builtinId.toString()).apply()
        }
    }

    fun restoreHidden(ctx: Context) = prefs(ctx).edit().remove("book_hidden").apply()

    fun hiddenCount(ctx: Context) = hidden(ctx).size

    fun lastSource(ctx: Context): String = prefs(ctx).getString("book_last_source", null) ?: SOURCES[1]

    fun pick(ctx: Context, offset: Int): BookNote? {
        val l = pool(ctx)
        if (l.isEmpty()) return null
        val d = Calendar.getInstance().get(Calendar.DAY_OF_YEAR)
        // ugyanaz a sorrend egész nap, de ne mindig a lista elején kezdjen
        return l[Math.floorMod(d * 7 + offset, l.size)]
    }

    private fun prefs(ctx: Context) = ctx.getSharedPreferences("fokusz", Context.MODE_PRIVATE)
}
