package hu.miki.fokusz

import android.app.Activity
import android.app.AlertDialog
import android.app.TimePickerDialog
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view.WindowInsets
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.text.Collator
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class AppEntry(val label: String, val pkg: String, val component: ComponentName)

private data class Slot(val name: String, val packages: List<String>, val labelMatch: String? = null)

class MainActivity : Activity() {

    companion object {
        val INK = Color.parseColor("#EDE6D8")
        val MUTED = Color.parseColor("#A8A193")
        val MUTED2 = Color.parseColor("#8E887C")
        val GOLD = Color.parseColor("#B89A5E")
        val LINE = Color.parseColor("#4A463F")
        val NIGHT = Color.parseColor("#0E0E0E")
        val NIGHT_INK = Color.parseColor("#D8D3CA")
        val NIGHT_MUTED = Color.parseColor("#9A958D")

        private val SLOTS = listOf(
            Slot("Telefon", listOf("com.samsung.android.dialer", "com.google.android.dialer", "com.android.dialer")),
            Slot("Üzenetek", listOf("com.samsung.android.messaging", "com.google.android.apps.messaging")),
            Slot("Naptár", listOf("com.samsung.android.calendar", "com.google.android.calendar")),
            Slot("Horizont", emptyList(), "horizont"),
            Slot("Waze", listOf("com.waze")),
            Slot("Keep", listOf("com.google.android.keep")),
        )

        private val DEFAULT_RESTRICTED = setOf(
            "com.instagram.android",
            "com.facebook.katana",
            "com.google.android.youtube",
            "com.zhiliaoapp.musically",
        )
        private const val PAUSE_SECONDS = 10
    }

    private lateinit var prefs: SharedPreferences
    private lateinit var fMed: Typeface
    private lateinit var fSemi: Typeface
    private lateinit var fItal: Typeface

    private lateinit var root: SwipeFrame
    private lateinit var home: LinearLayout
    private lateinit var drawer: LinearLayout
    private lateinit var drawerScroll: ScrollView
    private lateinit var drawerList: LinearLayout
    private lateinit var search: EditText
    private lateinit var csend: LinearLayout
    private lateinit var csendTime: TextView
    private lateinit var pause: LinearLayout
    private lateinit var pauseName: TextView
    private lateinit var pauseCount: TextView
    private lateinit var pauseOpen: TextView

    private lateinit var clock: ClockView
    private lateinit var dateText: TextView
    private lateinit var slotViews: List<TextView>

    private var apps: List<AppEntry> = emptyList()
    private var drawerOpen = false
    private val handler = Handler(Looper.getMainLooper())
    private var pauseTarget: AppEntry? = null
    private var pauseLeft = 0

    private val tick = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, i: Intent?) = refreshTime()
    }

    // ---------- életciklus ----------

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences("fokusz", MODE_PRIVATE)
        fMed = resources.getFont(R.font.cormorant_medium)
        fSemi = resources.getFont(R.font.cormorant_semibold)
        fItal = resources.getFont(R.font.cormorant_italic)

        window.setDecorFitsSystemWindows(false)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT

        root = SwipeFrame(this)
        buildHome()
        buildDrawer()
        buildCsend()
        buildPause()
        val full = FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT)
        root.addView(home, full)
        root.addView(drawer, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        root.addView(csend, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        root.addView(pause, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))

        root.canSwipeUp = { !drawerOpen && csend.visibility != View.VISIBLE && pause.visibility != View.VISIBLE }
        root.canSwipeDown = { !drawerOpen && pause.visibility != View.VISIBLE }
        root.onSwipeUp = { openDrawer() }
        root.onSwipeDown = { expandNotifications() }

        root.setOnApplyWindowInsetsListener { _, insets ->
            val bars = insets.getInsets(WindowInsets.Type.systemBars())
            val ime = insets.getInsets(WindowInsets.Type.ime())
            home.setPadding(dp(36), bars.top + dp(72), dp(36), bars.bottom + dp(40))
            drawer.setPadding(dp(36), bars.top + dp(40), dp(36), maxOf(bars.bottom, ime.bottom) + dp(20))
            csend.setPadding(dp(36), bars.top + dp(96), dp(36), bars.bottom + dp(48))
            pause.setPadding(dp(36), bars.top + dp(48), dp(36), bars.bottom + dp(48))
            insets
        }
        setContentView(root)
    }

    override fun onResume() {
        super.onResume()
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_TIME_TICK)
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
        }
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(tick, filter, Context.RECEIVER_NOT_EXPORTED)
        else registerReceiver(tick, filter)
        loadApps()
        refreshSlots()
        refreshTime()
        if (drawerOpen) renderDrawer()
    }

    override fun onPause() {
        super.onPause()
        try { unregisterReceiver(tick) } catch (_: Exception) {}
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        // Home gomb: vissza a tiszta kezdőképernyőre
        hidePause()
        closeDrawer()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        when {
            pause.visibility == View.VISIBLE -> hidePause()
            drawerOpen -> closeDrawer()
            else -> Unit // a kezdőképernyőről nincs hova visszalépni
        }
    }

    // ---------- kezdőképernyő ----------

    private fun buildHome() {
        home = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            isClickable = true
            setOnLongClickListener { showSettings(); true }
        }

        clock = ClockView(this, GOLD).apply {
            setOnClickListener { openClockApp() }
            setOnLongClickListener { showSettings(); true }
        }
        home.addView(clock, LinearLayout.LayoutParams(dp(168), dp(168)).apply { leftMargin = -dp(4) })

        dateText = label("", 19f, fItal, MUTED).apply { letterSpacing = 0.02f }
        home.addView(dateText, lp().apply { topMargin = dp(18) })

        home.addView(View(this).apply { setBackgroundColor(GOLD) },
            LinearLayout.LayoutParams(dp(40), dp(1)).apply { topMargin = dp(36); bottomMargin = dp(28) })

        slotViews = SLOTS.indices.map { i ->
            label("", 31f, fMed, INK).apply {
                minHeight = dp(50)
                gravity = Gravity.CENTER_VERTICAL
                setOnClickListener { launchSlot(i) }
                setOnLongClickListener { pickForSlot(i); true }
            }
        }
        slotViews.forEach { home.addView(it, lp()) }

        home.addView(View(this), LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))

        home.addView(label("FÓKUSZ · BE", 13f, fSemi, GOLD).apply { letterSpacing = 0.23f }, lp())
        home.addView(label("Egyszerre egy dolog.", 16f, fItal, MUTED), lp().apply { topMargin = dp(6) })
    }

    private fun refreshTime() {
        clock.invalidate()
        dateText.text = SimpleDateFormat("EEEE · MMMM d.", Locale("hu")).format(Date())
        updateCsend()
    }

    private fun refreshSlots() {
        SLOTS.forEachIndexed { i, slot ->
            val pkg = slotPackage(i)
            val v = slotViews[i]
            v.text = prefs.getString("slot_${i}_label", null) ?: slot.name
            v.alpha = if (pkg != null || i == 0) 1f else 0.4f
        }
    }

    private fun slotPackage(i: Int): String? {
        prefs.getString("slot_$i", null)?.let { saved ->
            if (apps.any { it.pkg == saved }) return saved
        }
        val slot = SLOTS[i]
        slot.packages.firstOrNull { p -> apps.any { it.pkg == p } }?.let { return it }
        slot.labelMatch?.let { m -> apps.firstOrNull { it.label.lowercase().contains(m) }?.let { return it.pkg } }
        return null
    }

    private fun launchSlot(i: Int) {
        val pkg = slotPackage(i)
        if (pkg == null) {
            if (i == 0) { safeStart(Intent(Intent.ACTION_DIAL)); return }
            pickForSlot(i); return
        }
        val entry = apps.firstOrNull { it.pkg == pkg }
        if (entry != null) launch(entry) else pickForSlot(i)
    }

    private fun pickForSlot(i: Int) {
        if (apps.isEmpty()) loadApps()
        val labels = (listOf("↺ Alapértelmezett (${SLOTS[i].name})") + apps.map { it.label }).toTypedArray()
        AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle("Mi kerüljön ide?")
            .setItems(labels) { _, which ->
                if (which == 0) {
                    prefs.edit().remove("slot_$i").remove("slot_${i}_label").apply()
                } else {
                    val a = apps[which - 1]
                    prefs.edit().putString("slot_$i", a.pkg).putString("slot_${i}_label", a.label).apply()
                }
                refreshSlots()
            }
            .show()
    }

    private fun openClockApp() {
        listOf("com.sec.android.app.clockpackage", "com.google.android.deskclock")
            .firstNotNullOfOrNull { p -> apps.firstOrNull { it.pkg == p } }
            ?.let { launch(it) }
    }

    // ---------- összes app ----------

    private fun buildDrawer() {
        drawer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = ColorDrawable(Color.parseColor("#F2121214"))
            visibility = View.GONE
            isClickable = true
        }
        drawer.addView(label("KERESÉS", 13f, fSemi, GOLD).apply { letterSpacing = 0.23f }, lp())

        search = EditText(this).apply {
            background = null
            setPadding(0, dp(8), 0, dp(10))
            textSize = 26f
            typeface = fItal
            setTextColor(INK)
            setHintTextColor(Color.parseColor("#6E685E"))
            hint = "Írd be, mit keresel…"
            isSingleLine = true
            imeOptions = EditorInfo.IME_ACTION_GO
            setOnEditorActionListener { _, _, _ ->
                visibleApps().firstOrNull()?.let { launch(it) }
                true
            }
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun afterTextChanged(s: Editable?) = renderDrawer()
            })
        }
        drawer.addView(search, lp(MATCH_PARENT).apply { topMargin = dp(4) })
        drawer.addView(View(this).apply { setBackgroundColor(LINE) },
            LinearLayout.LayoutParams(MATCH_PARENT, dp(1)).apply { bottomMargin = dp(16) })

        drawerList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        drawerScroll = ScrollView(this).apply {
            isVerticalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            addView(drawerList, FrameLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        }
        drawer.addView(drawerScroll, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))
    }

    private fun query() = search.text.toString().trim().lowercase()

    private fun visibleApps(): List<AppEntry> {
        val q = query()
        val r = restricted()
        return apps.filter { it.pkg !in r && (q.isEmpty() || it.label.lowercase().contains(q)) }
    }

    private fun renderDrawer() {
        drawerList.removeAllViews()
        val q = query()
        var last = ""
        for (a in visibleApps()) {
            val letter = a.label.take(1).uppercase()
            val first = letter != last
            last = letter
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                minimumHeight = dp(46)
                isClickable = true
                setOnClickListener { launch(a) }
                setOnLongClickListener { appMenu(a); true }
            }
            row.addView(label(if (first) letter else "", 15f, fSemi, GOLD), LinearLayout.LayoutParams(dp(28), WRAP_CONTENT))
            row.addView(label(a.label, 23f, fMed, INK), LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
            drawerList.addView(row, lp(MATCH_PARENT))
        }

        val r = restricted()
        val limited = apps.filter { it.pkg in r && (q.isEmpty() || it.label.lowercase().contains(q)) }
        if (limited.isNotEmpty()) {
            drawerList.addView(View(this).apply { setBackgroundColor(Color.parseColor("#2E2B27")) },
                LinearLayout.LayoutParams(MATCH_PARENT, dp(1)).apply { topMargin = dp(28); bottomMargin = dp(18) })
            drawerList.addView(label("KORLÁTOZVA", 13f, fSemi, MUTED2).apply { letterSpacing = 0.23f },
                lp().apply { bottomMargin = dp(4) })
            for (a in limited) {
                val row = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    minimumHeight = dp(44)
                    isClickable = true
                    setOnClickListener { showPause(a) }
                    setOnLongClickListener { appMenu(a); true }
                }
                row.addView(label(a.label, 21f, fMed, MUTED2), LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
                row.addView(label("$PAUSE_SECONDS mp szünet", 15f, fItal, MUTED2), lp())
                drawerList.addView(row, lp(MATCH_PARENT))
            }
        }
        drawerList.addView(View(this), LinearLayout.LayoutParams(MATCH_PARENT, dp(40)))
    }

    private fun openDrawer() {
        drawerOpen = true
        search.setText("")
        renderDrawer()
        drawerScroll.scrollTo(0, 0)
        drawer.alpha = 0f
        drawer.translationY = dp(24).toFloat()
        drawer.visibility = View.VISIBLE
        drawer.animate().alpha(1f).translationY(0f).setDuration(180).start()
        home.animate().alpha(0f).setDuration(150).start()
    }

    private fun closeDrawer() {
        if (!drawerOpen) { home.alpha = 1f; return }
        drawerOpen = false
        hideKeyboard()
        search.clearFocus()
        drawer.animate().alpha(0f).setDuration(150).withEndAction { drawer.visibility = View.GONE }.start()
        home.animate().alpha(1f).setDuration(180).start()
    }

    private fun appMenu(a: AppEntry) {
        val isLimited = a.pkg in restricted()
        val items = arrayOf(
            if (isLimited) "Korlátozás feloldása" else "Korlátozás ($PAUSE_SECONDS mp szünet indítás előtt)",
            "Alkalmazás infó",
            "Eltávolítás",
        )
        AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle(a.label)
            .setItems(items) { _, which ->
                when (which) {
                    0 -> {
                        val set = restricted().toMutableSet()
                        if (isLimited) set.remove(a.pkg) else set.add(a.pkg)
                        prefs.edit().putStringSet("restricted", set).apply()
                        renderDrawer()
                    }
                    1 -> safeStart(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${a.pkg}")))
                    2 -> safeStart(Intent(Intent.ACTION_DELETE, Uri.parse("package:${a.pkg}")))
                }
            }
            .show()
    }

    private fun restricted(): Set<String> =
        prefs.getStringSet("restricted", null) ?: DEFAULT_RESTRICTED

    // ---------- lassított indítás ----------

    private fun buildPause() {
        pause = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setBackgroundColor(NIGHT)
            visibility = View.GONE
            isClickable = true
        }
        pause.addView(View(this), LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))
        pause.addView(label("Lélegezz.", 34f, fItal, NIGHT_INK), lp())
        pauseName = label("", 20f, fMed, NIGHT_MUTED).apply { gravity = Gravity.CENTER }
        pause.addView(pauseName, lp().apply { topMargin = dp(16) })
        pauseCount = label("", 88f, fMed, GOLD)
        pause.addView(pauseCount, lp().apply { topMargin = dp(40) })
        pause.addView(View(this), LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))

        pauseOpen = outlineButton("Megnyitás").apply {
            setOnClickListener {
                if (pauseLeft <= 0) {
                    val t = pauseTarget
                    hidePause()
                    t?.let { launch(it, bypassPause = true) }
                }
            }
        }
        pause.addView(pauseOpen, lp())
        pause.addView(label("Mégse", 18f, fMed, NIGHT_MUTED).apply {
            minHeight = dp(48)
            gravity = Gravity.CENTER
            setPadding(dp(24), 0, dp(24), 0)
            setOnClickListener { hidePause() }
        }, lp().apply { topMargin = dp(8) })
    }

    private val countdown = object : Runnable {
        override fun run() {
            pauseLeft -= 1
            renderPause()
            if (pauseLeft > 0) handler.postDelayed(this, 1000)
        }
    }

    private fun showPause(a: AppEntry) {
        hideKeyboard()
        pauseTarget = a
        pauseLeft = PAUSE_SECONDS
        pauseName.text = "${a.label}\nTényleg erre van most szükséged?"
        renderPause()
        pause.visibility = View.VISIBLE
        handler.removeCallbacks(countdown)
        handler.postDelayed(countdown, 1000)
    }

    private fun renderPause() {
        pauseCount.text = if (pauseLeft > 0) pauseLeft.toString() else "·"
        pauseOpen.alpha = if (pauseLeft > 0) 0.3f else 1f
    }

    private fun hidePause() {
        handler.removeCallbacks(countdown)
        pause.visibility = View.GONE
        pauseTarget = null
    }

    // ---------- csend mód ----------

    private fun buildCsend() {
        csend = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setBackgroundColor(NIGHT)
            visibility = View.GONE
            isClickable = true
            setOnLongClickListener { showSettings(); true }
        }
        csendTime = label("", 13f, fSemi, NIGHT_MUTED).apply { letterSpacing = 0.23f }
        csend.addView(csendTime, lp())
        csend.addView(label("静", 160f, Typeface.create(Typeface.SERIF, Typeface.NORMAL), NIGHT_INK),
            lp().apply { topMargin = dp(64) })
        csend.addView(label("Csend", 34f, fItal, NIGHT_INK), lp().apply { topMargin = dp(24) })
        csend.addView(View(this).apply { setBackgroundColor(Color.parseColor("#4A4A4A")) },
            LinearLayout.LayoutParams(dp(32), dp(1)).apply { topMargin = dp(28); bottomMargin = dp(28) })
        csend.addView(label("Most pihenj.\nHolnap folytatod.", 20f, fMed, NIGHT_MUTED).apply {
            gravity = Gravity.CENTER
            setLineSpacing(0f, 1.25f)
        }, lp())
        csend.addView(View(this), LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))
        csend.addView(label("Telefon", 20f, fMed, NIGHT_INK).apply {
            minHeight = dp(48)
            gravity = Gravity.CENTER
            setPadding(dp(24), 0, dp(24), 0)
            setOnClickListener { launchSlot(0) }
        }, lp().apply { bottomMargin = dp(12) })
        csend.addView(outlineButton("Feloldás 10 percre").apply {
            setOnClickListener {
                prefs.edit().putLong("unlock_until", System.currentTimeMillis() + 10 * 60 * 1000).apply()
                updateCsend()
                handler.postDelayed({ updateCsend() }, 10 * 60 * 1000L + 500)
            }
        }, lp())
    }

    private fun updateCsend() {
        val on = prefs.getBoolean("csend_on", true)
        val start = prefs.getInt("csend_start", 22 * 60)
        val end = prefs.getInt("csend_end", 6 * 60 + 30)
        val cal = Calendar.getInstance()
        val now = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
        val inWindow = if (start <= end) now in start until end else (now >= start || now < end)
        val unlocked = System.currentTimeMillis() < prefs.getLong("unlock_until", 0)
        val show = on && inWindow && !unlocked
        csendTime.text = "${hm(start)} – ${hm(end)}"
        if (show && csend.visibility != View.VISIBLE) {
            closeDrawer()
            hidePause()
            hideKeyboard()
        }
        csend.visibility = if (show) View.VISIBLE else View.GONE
    }

    // ---------- beállítások ----------

    private fun showSettings() {
        val on = prefs.getBoolean("csend_on", true)
        val start = prefs.getInt("csend_start", 22 * 60)
        val end = prefs.getInt("csend_end", 6 * 60 + 30)
        val items = arrayOf(
            "Csend mód: ${if (on) "be" else "ki"}",
            "Csend kezdete: ${hm(start)}",
            "Csend vége: ${hm(end)}",
            "Háttérkép beállítása",
            "Kezdőképernyő-app választása",
        )
        AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle("Fókusz")
            .setItems(items) { _, which ->
                when (which) {
                    0 -> { prefs.edit().putBoolean("csend_on", !on).apply(); updateCsend() }
                    1 -> pickTime(start) { prefs.edit().putInt("csend_start", it).apply(); updateCsend() }
                    2 -> pickTime(end) { prefs.edit().putInt("csend_end", it).apply(); updateCsend() }
                    3 -> safeStart(Intent.createChooser(Intent(Intent.ACTION_SET_WALLPAPER), "Háttérkép"))
                    4 -> safeStart(Intent(Settings.ACTION_HOME_SETTINGS))
                }
            }
            .show()
    }

    private fun pickTime(current: Int, done: (Int) -> Unit) {
        TimePickerDialog(this, android.R.style.Theme_DeviceDefault_Dialog_Alert,
            { _, h, m -> done(h * 60 + m) }, current / 60, current % 60, true).show()
    }

    // ---------- segédek ----------

    private fun loadApps() {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val coll = Collator.getInstance(Locale("hu"))
        apps = packageManager.queryIntentActivities(intent, 0)
            .filter { it.activityInfo.packageName != packageName }
            .map {
                AppEntry(
                    it.loadLabel(packageManager).toString(),
                    it.activityInfo.packageName,
                    ComponentName(it.activityInfo.packageName, it.activityInfo.name),
                )
            }
            .sortedWith { a, b -> coll.compare(a.label, b.label) }
    }

    private fun launch(a: AppEntry, bypassPause: Boolean = false) {
        if (!bypassPause && a.pkg in restricted()) {
            showPause(a); return
        }
        val i = Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LAUNCHER)
            .setComponent(a.component)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        safeStart(i)
        if (drawerOpen) handler.postDelayed({ closeDrawer() }, 400)
    }

    private fun safeStart(i: Intent) {
        try { startActivity(i) } catch (_: Exception) {}
    }

    private fun expandNotifications() {
        try {
            val sbs = getSystemService("statusbar")
            Class.forName("android.app.StatusBarManager").getMethod("expandNotificationsPanel").invoke(sbs)
        } catch (_: Exception) {}
    }

    private fun hideKeyboard() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(root.windowToken, 0)
    }

    private fun outlineButton(text: String) = label(text, 18f, fMed, Color.parseColor("#CFCAC2")).apply {
        minHeight = dp(48)
        gravity = Gravity.CENTER
        letterSpacing = 0.03f
        setPadding(dp(28), 0, dp(28), 0)
        background = GradientDrawable().apply {
            cornerRadius = dp(24).toFloat()
            setStroke(dp(1), Color.parseColor("#4A4A4A"))
            setColor(Color.TRANSPARENT)
        }
    }

    private fun label(s: String, sizeSp: Float, tf: Typeface, color: Int) = TextView(this).apply {
        text = s
        textSize = sizeSp
        typeface = tf
        setTextColor(color)
        includeFontPadding = false
    }

    private fun hm(m: Int) = String.format("%d:%02d", m / 60, m % 60)

    private fun lp(w: Int = WRAP_CONTENT) = LinearLayout.LayoutParams(w, WRAP_CONTENT)

    private fun dp(v: Int): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), resources.displayMetrics).toInt()
}
