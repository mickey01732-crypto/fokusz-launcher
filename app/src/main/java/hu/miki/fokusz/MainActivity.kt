package hu.miki.fokusz

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.app.TimePickerDialog
import android.bluetooth.BluetoothManager
import android.os.BatteryManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ContentUris
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.animation.ValueAnimator
import android.provider.CalendarContract
import android.provider.ContactsContract
import android.view.animation.AccelerateDecelerateInterpolator
import android.provider.Settings
import android.text.Editable
import android.text.TextUtils
import android.text.TextWatcher
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
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
            Slot("Naptár", listOf("com.google.android.calendar", "com.samsung.android.calendar")),
            Slot("Horizont", emptyList(), "horizont"),
            Slot("Waze", listOf("com.waze")),
            Slot("Claude", listOf("com.anthropic.claude"), "claude"),
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
    private lateinit var eventText: TextView
    private lateinit var focusLabel: TextView
    private lateinit var sayingJp: TextView
    private lateinit var sayingHu: TextView
    private lateinit var note: LinearLayout
    private lateinit var noteInput: EditText
    private var nextEvent: NextEvent? = null
    private lateinit var seasonText: TextView
    private lateinit var dots: DotsView
    private lateinit var batteryText: TextView
    private lateinit var car: LinearLayout
    private lateinit var greet: LinearLayout
    private lateinit var greetBox: LinearLayout
    private lateinit var breath: LinearLayout
    private lateinit var breathView: BreathView
    private lateinit var breathText: TextView
    private lateinit var breathCount: TextView
    private var breathAnim: ValueAnimator? = null
    private var breathLeft = 0
    private var breathThenFocus = 0

    private var apps: List<AppEntry> = emptyList()
    private var drawerOpen = false
    private val handler = Handler(Looper.getMainLooper())
    private var pauseTarget: AppEntry? = null
    private var pauseLeft = 0

    private val focusTick = object : Runnable {
        override fun run() {
            refreshFocus()
            if (Focus.end(this@MainActivity) > 0) handler.postDelayed(this, 15_000)
        }
    }

    private val tick = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, i: Intent?) = refreshTime()
    }
    private val carChanged = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, i: Intent?) = updateCar()
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
        buildNote()
        buildCar()
        buildGreet()
        buildBreath()
        val full = FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT)
        root.addView(home, full)
        root.addView(drawer, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        root.addView(csend, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        root.addView(note, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        root.addView(car, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        root.addView(pause, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        root.addView(breath, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        root.addView(greet, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))

        root.canSwipeUp = { homeIdle() }
        root.canSwipeDown = { !drawerOpen && pause.visibility != View.VISIBLE && note.visibility != View.VISIBLE }
        root.canSwipeLeft = { homeIdle() }
        root.onSwipeUp = { openDrawer() }
        root.onSwipeDown = { expandNotifications() }
        root.onSwipeLeft = { openNote() }

        root.setOnApplyWindowInsetsListener { _, insets ->
            val bars = insets.getInsets(WindowInsets.Type.systemBars())
            val ime = insets.getInsets(WindowInsets.Type.ime())
            home.setPadding(dp(36), bars.top + dp(44), dp(36), bars.bottom + dp(32))
            car.setPadding(dp(36), bars.top + dp(56), dp(36), bars.bottom + dp(40))
            drawer.setPadding(dp(36), bars.top + dp(40), dp(36), maxOf(bars.bottom, ime.bottom) + dp(20))
            csend.setPadding(dp(36), bars.top + dp(96), dp(36), bars.bottom + dp(48))
            pause.setPadding(dp(36), bars.top + dp(48), dp(36), bars.bottom + dp(48))
            note.setPadding(dp(36), bars.top + dp(56), dp(36), maxOf(bars.bottom, ime.bottom) + dp(24))
            greet.setPadding(dp(36), bars.top + dp(56), dp(36), bars.bottom + dp(40))
            breath.setPadding(dp(36), bars.top + dp(56), dp(36), bars.bottom + dp(40))
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
        val carFilter = IntentFilter("hu.miki.fokusz.CAR_CHANGED")
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(carChanged, carFilter, Context.RECEIVER_NOT_EXPORTED)
        else registerReceiver(carChanged, carFilter)
        loadApps()
        refreshSlots()
        refreshTime()
        refreshSaying()
        if (drawerOpen) renderDrawer()
        Weather.refreshIfStale(this) { runOnUiThread { refreshTime() } }
        askCalendarOnce()
        handler.removeCallbacks(focusTick)
        handler.post(focusTick)
        maybeGreet()
    }

    override fun onPause() {
        super.onPause()
        try { unregisterReceiver(tick) } catch (_: Exception) {}
        try { unregisterReceiver(carChanged) } catch (_: Exception) {}
        handler.removeCallbacks(focusTick)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        refreshEvent()
        if (requestCode == 3 && grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) pickCar()
        if (requestCode == 4 && grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) showFavorites()
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        // Home gomb: vissza a tiszta kezdőképernyőre
        hidePause()
        hideNote()
        hideGreet()
        closeDrawer()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        when {
            greet.visibility == View.VISIBLE -> hideGreet()
            breath.visibility == View.VISIBLE -> stopBreath(startFocus = false)
            pause.visibility == View.VISIBLE -> hidePause()
            car.visibility == View.VISIBLE -> Unit
            note.visibility == View.VISIBLE -> hideNote()
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

        seasonText = label("", 15f, fItal, MUTED2).apply {
            letterSpacing = 0.02f
            minHeight = dp(28)
            gravity = Gravity.CENTER_VERTICAL
            setOnClickListener { showSeason() }
        }
        home.addView(seasonText, lp().apply { bottomMargin = dp(10) })

        clock = ClockView(this, GOLD).apply {
            setOnClickListener { openClockApp() }
            setOnLongClickListener { showSettings(); true }
        }
        home.addView(clock, LinearLayout.LayoutParams(dp(168), dp(168)).apply { leftMargin = -dp(4) })

        dots = DotsView(this, GOLD).apply {
            contentDescription = "Fókusz-lánc a héten"
            setOnClickListener { showChain() }
        }
        home.addView(dots, LinearLayout.LayoutParams(dp(104), dp(20)).apply { topMargin = dp(14) })

        dateText = label("", 19f, fItal, MUTED).apply { letterSpacing = 0.02f }
        home.addView(dateText, lp().apply { topMargin = dp(10) })

        eventText = label("", 17f, fItal, MUTED2).apply {
            isSingleLine = true
            ellipsize = TextUtils.TruncateAt.END
            minHeight = dp(32)
            gravity = Gravity.CENTER_VERTICAL
            visibility = View.GONE
            setOnClickListener { openEvent() }
        }
        home.addView(eventText, lp(MATCH_PARENT).apply { topMargin = dp(2) })

        batteryText = label("", 16f, fItal, Color.parseColor("#B5606B")).apply { visibility = View.GONE }
        home.addView(batteryText, lp().apply { topMargin = dp(2) })

        home.addView(View(this).apply { setBackgroundColor(GOLD) },
            LinearLayout.LayoutParams(dp(40), dp(1)).apply { topMargin = dp(24); bottomMargin = dp(18) })

        slotViews = SLOTS.indices.map { i ->
            label("", 31f, fMed, INK).apply {
                minHeight = dp(48)
                gravity = Gravity.CENTER_VERTICAL
                setOnClickListener { launchSlot(i) }
                setOnLongClickListener { if (i == 0) showFavorites() else pickForSlot(i); true }
            }
        }
        slotViews.forEach { home.addView(it, lp()) }

        home.addView(View(this), LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))

        focusLabel = label("FÓKUSZ · BE", 13f, fSemi, GOLD).apply {
            letterSpacing = 0.23f
            minHeight = dp(40)
            gravity = Gravity.CENTER_VERTICAL
            setOnClickListener { focusMenu() }
        }
        home.addView(focusLabel, lp())

        val saying = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            isClickable = true
            setOnClickListener { showSaying() }
        }
        sayingJp = label("", 17f, Typeface.create(Typeface.SERIF, Typeface.NORMAL), GOLD)
        sayingHu = label("", 16f, fItal, MUTED)
        saying.addView(sayingJp, lp())
        saying.addView(sayingHu, lp().apply { topMargin = dp(4) })
        home.addView(saying, lp(MATCH_PARENT).apply { topMargin = dp(2) })

    }

    private fun homeIdle() = !drawerOpen && csend.visibility != View.VISIBLE && car.visibility != View.VISIBLE &&
        greet.visibility != View.VISIBLE && breath.visibility != View.VISIBLE &&
        pause.visibility != View.VISIBLE && note.visibility != View.VISIBLE

    // ---------- naptár ----------

    private fun askCalendarOnce() {
        if (checkSelfPermission(Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED) return
        if (prefs.getBoolean("cal_asked", false)) return
        prefs.edit().putBoolean("cal_asked", true).apply()
        requestPermissions(arrayOf(Manifest.permission.READ_CALENDAR), 1)
    }

    private fun refreshEvent() {
        val ok = checkSelfPermission(Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED
        nextEvent = if (ok) Agenda.next(this) else null
        val e = nextEvent
        if (e == null) {
            eventText.visibility = View.GONE
        } else {
            eventText.text = Agenda.format(e, prefs.getBoolean("cal_discreet", false))
            eventText.visibility = View.VISIBLE
        }
    }

    private fun openEvent() {
        val e = nextEvent ?: return
        val i = Intent(Intent.ACTION_VIEW, ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, e.id))
            .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, e.begin)
            .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, e.end)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (apps.any { it.pkg == "com.google.android.calendar" }) i.setPackage("com.google.android.calendar")
        try { startActivity(i) } catch (_: Exception) { i.setPackage(null); safeStart(i) }
    }

    // ---------- japán gondolat ----------

    private fun refreshSaying() {
        val s = Sayings.today()
        sayingJp.text = s.jp
        sayingHu.text = s.hu
    }

    private fun showSaying() {
        val s = Sayings.today()
        AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle("${s.jp}  ·  ${s.reading}")
            .setMessage("${s.hu}\n\n${s.note}")
            .setPositiveButton("Rendben", null)
            .show()
    }

    // ---------- fókusz-időzítő ----------

    private fun refreshFocus() {
        val end = Focus.end(this)
        clock.focusEnd = end
        focusLabel.text = if (end > 0) {
            val left = ((end - System.currentTimeMillis() + 59_999) / 60_000).toInt()
            "FÓKUSZ · $left PERC"
        } else "FÓKUSZ · BE"
    }

    private fun focusMenu() {
        val end = Focus.end(this)
        val dlg = AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
        if (end > 0) {
            val left = ((end - System.currentTimeMillis() + 59_999) / 60_000).toInt()
            dlg.setTitle("Még $left perc")
                .setItems(arrayOf("Folytatom", "Befejezem most")) { _, which ->
                    if (which == 1) { Focus.stop(this); refreshFocus() }
                }
        } else {
            dlg.setTitle("Mélymunka-blokk")
                .setItems(arrayOf("25 perc", "50 perc", "1 perc légzés, utána 25 perc", "Csak 1 perc légzés")) { _, which ->
                    when (which) {
                        0 -> startFocus(25)
                        1 -> startFocus(50)
                        2 -> startBreath(thenFocus = 25)
                        3 -> startBreath(thenFocus = 0)
                    }
                }
        }
        dlg.show()
    }

    private fun startFocus(minutes: Int) {
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 2)
        Focus.start(this, minutes)
        handler.removeCallbacks(focusTick)
        handler.post(focusTick)
    }

    // ---------- egy perc légzés ----------

    private fun buildBreath() {
        breath = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setBackgroundColor(NIGHT)
            visibility = View.GONE
            isClickable = true
        }
        breath.addView(label("LÉGZÉS", 13f, fSemi, GOLD).apply { letterSpacing = 0.23f }, lp())
        breath.addView(View(this), LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))
        breathView = BreathView(this, GOLD)
        breath.addView(breathView, LinearLayout.LayoutParams(dp(260), dp(260)))
        breathText = label("", 30f, fItal, NIGHT_INK)
        breath.addView(breathText, lp().apply { topMargin = dp(36) })
        breathCount = label("", 18f, fMed, NIGHT_MUTED)
        breath.addView(breathCount, lp().apply { topMargin = dp(10) })
        breath.addView(View(this), LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))
        breath.addView(label("Kihagyom", 18f, fMed, NIGHT_MUTED).apply {
            minHeight = dp(48)
            gravity = Gravity.CENTER
            setPadding(dp(24), 0, dp(24), 0)
            setOnClickListener { stopBreath(startFocus = true) }
        }, lp())
    }

    private val breathTick = object : Runnable {
        override fun run() {
            breathLeft -= 1
            breathCount.text = "$breathLeft mp"
            if (breathLeft <= 0) stopBreath(startFocus = true) else handler.postDelayed(this, 1000)
        }
    }

    private fun startBreath(thenFocus: Int) {
        breathThenFocus = thenFocus
        breathLeft = 60
        breathCount.text = "60 mp"
        breathText.text = "Belégzés…"
        breath.visibility = View.VISIBLE
        breathAnim?.cancel()
        breathAnim = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 4000
            repeatMode = ValueAnimator.REVERSE
            repeatCount = ValueAnimator.INFINITE
            interpolator = AccelerateDecelerateInterpolator()
            var inhale = true
            addUpdateListener { breathView.phase = it.animatedValue as Float }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationRepeat(animation: android.animation.Animator) {
                    inhale = !inhale
                    breathText.text = if (inhale) "Belégzés…" else "Kilégzés…"
                }
            })
            start()
        }
        handler.removeCallbacks(breathTick)
        handler.postDelayed(breathTick, 1000)
    }

    private fun stopBreath(startFocus: Boolean) {
        if (breath.visibility != View.VISIBLE) return
        handler.removeCallbacks(breathTick)
        breathAnim?.cancel()
        breathAnim = null
        breath.visibility = View.GONE
        if (startFocus && breathThenFocus > 0) startFocus(breathThenFocus)
        breathThenFocus = 0
    }

    // ---------- reggeli és esti kép ----------

    private fun buildGreet() {
        greet = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#F50E0E0E"))
            visibility = View.GONE
            isClickable = true
            setOnClickListener { hideGreet() }
        }
        greet.addView(View(this), LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))
        greetBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        greet.addView(greetBox, lp(MATCH_PARENT))
        greet.addView(View(this), LinearLayout.LayoutParams(MATCH_PARENT, 0, 1.4f))
        greet.addView(label("Koppints a folytatáshoz", 15f, fItal, MUTED2), lp())
    }

    private fun greetLine(text: String, size: Float, tf: Typeface, color: Int, top: Int) {
        greetBox.addView(label(text, size, tf, color).apply { setLineSpacing(0f, 1.15f) }, lp(MATCH_PARENT).apply { topMargin = dp(top) })
    }

    private fun maybeGreet() {
        if (greet.visibility == View.VISIBLE) return
        if (csend.visibility == View.VISIBLE || car.visibility == View.VISIBLE) return
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        when {
            hour in 5..10 && prefs.getString("greet_morning", null) != today -> {
                prefs.edit().putString("greet_morning", today).apply()
                showGreet(evening = false)
            }
            hour in 20..23 && prefs.getString("greet_evening", null) != today -> {
                prefs.edit().putString("greet_evening", today).apply()
                showGreet(evening = true)
            }
        }
    }

    private fun showGreet(evening: Boolean) {
        greetBox.removeAllViews()
        val discreet = prefs.getBoolean("cal_discreet", false)
        val calOk = checkSelfPermission(Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED
        val (season, _) = Season.now()
        val saying = Sayings.today()
        greetLine("${season.jp} · ${season.hu}", 15f, fItal, MUTED2, 0)
        if (!evening) {
            greetLine("Jó reggelt, Miki.", 40f, fItal, INK, 14)
            Weather.cached(this)?.let { greetLine("Most $it", 20f, fMed, MUTED, 12) }
            greetBox.addView(View(this).apply { setBackgroundColor(GOLD) },
                LinearLayout.LayoutParams(dp(40), dp(1)).apply { topMargin = dp(28); bottomMargin = dp(24) })
            val (_, end) = Agenda.dayBounds(0)
            val first = if (calOk) Agenda.between(this, System.currentTimeMillis(), end) else null
            greetLine(first?.let { "Első: ${Agenda.short(it, discreet)}" } ?: "Ma nincs naptárbejegyzés.", 21f, fMed, INK, 0)
        } else {
            greetLine("Jó estét, Miki.", 40f, fItal, INK, 14)
            val (week, todayIdx) = Chain.week(this)
            greetLine(
                if (week[todayIdx]) "A mai pötty arany. Szép munka." else "A mai pötty üres maradt. Holnap újra.",
                20f, fMed, MUTED, 12,
            )
            greetBox.addView(View(this).apply { setBackgroundColor(GOLD) },
                LinearLayout.LayoutParams(dp(40), dp(1)).apply { topMargin = dp(28); bottomMargin = dp(24) })
            val (start, end) = Agenda.dayBounds(1)
            val first = if (calOk) Agenda.between(this, start, end) else null
            greetLine(first?.let { "Holnap első: ${Agenda.short(it, discreet)}" } ?: "Holnapra nincs naptárbejegyzés.", 21f, fMed, INK, 0)
            Weather.tomorrow(this)?.let { greetLine("Holnap: $it", 20f, fMed, MUTED, 10) }
        }
        greetLine(saying.jp, 18f, Typeface.create(Typeface.SERIF, Typeface.NORMAL), GOLD, 36)
        greetLine(saying.hu, 18f, fItal, MUTED, 4)

        greet.alpha = 0f
        greet.visibility = View.VISIBLE
        greet.animate().alpha(1f).setDuration(300).start()
        handler.removeCallbacks(autoHideGreet)
        handler.postDelayed(autoHideGreet, 10_000)
    }

    private val autoHideGreet = Runnable { hideGreet() }

    private fun hideGreet() {
        handler.removeCallbacks(autoHideGreet)
        if (greet.visibility != View.VISIBLE) return
        greet.animate().alpha(0f).setDuration(400).withEndAction { greet.visibility = View.GONE }.start()
    }

    // ---------- kedvenc hívások ----------

    private fun showFavorites() {
        if (checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.READ_CONTACTS, Manifest.permission.CALL_PHONE), 4)
            return
        }
        data class Fav(val id: Long, val name: String, val number: String, val primary: Boolean)
        val favs = mutableListOf<Fav>()
        try {
            contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(
                    ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                    ContactsContract.CommonDataKinds.Phone.NUMBER,
                    ContactsContract.CommonDataKinds.Phone.IS_SUPER_PRIMARY,
                ),
                "${ContactsContract.CommonDataKinds.Phone.STARRED} = 1",
                null,
                "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} ASC",
            )?.use { c ->
                while (c.moveToNext()) {
                    favs.add(Fav(c.getLong(0), c.getString(1) ?: "", c.getString(2) ?: "", c.getInt(3) == 1))
                }
            }
        } catch (_: Exception) {}
        val unique = favs.filter { it.number.isNotBlank() }
            .groupBy { it.id }
            .map { (_, list) -> list.firstOrNull { it.primary } ?: list.first() }
            .take(8)

        if (unique.isEmpty()) {
            AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("Nincs kedvenc névjegy")
                .setMessage("A Névjegyek appban jelöld csillaggal azokat, akiket gyakran hívsz, és itt megjelennek.")
                .setPositiveButton("Telefon") { _, _ -> launchSlot(0) }
                .setNeutralButton("Más app ide…") { _, _ -> pickForSlot(0) }
                .setNegativeButton("Rendben", null)
                .show()
            return
        }
        val names = unique.map { it.name }
        val items = (names + "Telefon megnyitása" + "Más app erre a helyre…").toTypedArray()
        AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle("Kedvencek")
            .setItems(items) { _, which ->
                when {
                    which < unique.size -> call(unique[which].number)
                    which == unique.size -> launchSlot(0)
                    else -> pickForSlot(0)
                }
            }
            .show()
    }

    private fun call(number: String) {
        val uri = Uri.parse("tel:" + Uri.encode(number))
        val canCall = checkSelfPermission(Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED
        safeStart(Intent(if (canCall) Intent.ACTION_CALL else Intent.ACTION_DIAL, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    // ---------- gyorsjegyzet ----------

    private fun buildNote() {
        note = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = ColorDrawable(Color.parseColor("#F2121214"))
            visibility = View.GONE
            isClickable = true
        }
        note.addView(label("JEGYZET", 13f, fSemi, GOLD).apply { letterSpacing = 0.23f }, lp())
        noteInput = EditText(this).apply {
            background = null
            setPadding(0, dp(12), 0, dp(12))
            textSize = 24f
            typeface = fItal
            setTextColor(INK)
            setHintTextColor(Color.parseColor("#6E685E"))
            hint = "Mit ne felejts el?"
            gravity = Gravity.TOP or Gravity.START
            minLines = 3
        }
        note.addView(noteInput, lp(MATCH_PARENT))
        note.addView(View(this).apply { setBackgroundColor(LINE) },
            LinearLayout.LayoutParams(MATCH_PARENT, dp(1)).apply { bottomMargin = dp(24) })
        note.addView(View(this), LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))
        note.addView(outlineButton("Mentés a Keepbe").apply { setOnClickListener { saveNote() } }, lp())
        note.addView(label("Mégse", 18f, fMed, MUTED).apply {
            minHeight = dp(48)
            gravity = Gravity.CENTER_VERTICAL
            setOnClickListener { hideNote() }
        }, lp().apply { topMargin = dp(8) })
    }

    private fun openNote() {
        note.alpha = 0f
        note.translationX = dp(32).toFloat()
        note.visibility = View.VISIBLE
        note.animate().alpha(1f).translationX(0f).setDuration(180).start()
        noteInput.requestFocus()
        handler.postDelayed({
            (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
                .showSoftInput(noteInput, InputMethodManager.SHOW_IMPLICIT)
        }, 200)
    }

    private fun hideNote() {
        if (note.visibility != View.VISIBLE) return
        hideKeyboard()
        noteInput.clearFocus()
        note.visibility = View.GONE
    }

    private fun saveNote() {
        val text = noteInput.text.toString().trim()
        if (text.isEmpty()) { hideNote(); return }
        val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
        val keep = "com.google.android.keep"
        val hasKeep = try { packageManager.getPackageInfo(keep, 0); true } catch (_: Exception) { false }
        if (hasKeep) send.setPackage(keep)
        safeStart(if (hasKeep) send else Intent.createChooser(send, "Jegyzet mentése"))
        noteInput.setText("")
        hideNote()
    }

    private fun refreshTime() {
        clock.invalidate()
        val date = SimpleDateFormat("EEEE · MMMM d.", Locale("hu")).format(Date())
        dateText.text = Weather.cached(this)?.let { "$date · $it" } ?: date
        refreshEvent()
        refreshFocus()
        refreshSaying()
        refreshSeason()
        refreshChain()
        refreshBattery()
        updateCsend()
        updateCar()
    }

    // ---------- mikroévszak ----------

    private fun refreshSeason() {
        val (cur, _) = Season.now()
        seasonText.text = "${cur.jp} · ${cur.hu}"
    }

    private fun showSeason() {
        val (cur, next) = Season.now()
        AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle("${cur.jp}  ·  ${cur.reading}")
            .setMessage("${cur.hu}\n\nA japán naptár 24 évszakának egyike, nagyjából két hétig tart.\n\n" +
                "Következik (${next.month}. ${next.day}.): ${next.jp} · ${next.hu}")
            .setPositiveButton("Rendben", null)
            .show()
    }

    // ---------- fókusz-lánc ----------

    private fun refreshChain() {
        val (week, todayIdx) = Chain.week(this)
        dots.done = week
        dots.todayIndex = todayIdx
    }

    private fun showChain() {
        val (week, _) = Chain.week(this)
        val n = week.count { it }
        AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle("Fókusz-lánc")
            .setMessage(
                "A héten $n napon fejeztél be legalább egy fókuszblokkot.\n\n" +
                    "Arany pötty: kész nap. Új blokkot a FÓKUSZ · BE feliratra koppintva indíthatsz."
            )
            .setPositiveButton("Rendben", null)
            .show()
    }

    // ---------- akku ----------

    private fun refreshBattery() {
        val b = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = b?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = b?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        val plugged = (b?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0) != 0
        val pct = if (level >= 0 && scale > 0) level * 100 / scale else 100
        if (pct <= 20 && !plugged) {
            batteryText.text = "akku $pct%"
            batteryText.visibility = View.VISIBLE
        } else batteryText.visibility = View.GONE
    }

    // ---------- autós mód ----------

    private fun buildCar() {
        car = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#F00E0E0E"))
            visibility = View.GONE
            isClickable = true
        }
        car.addView(label("AUTÓ", 13f, fSemi, GOLD).apply { letterSpacing = 0.23f }, lp())
        car.addView(View(this), LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))
        car.addView(label("Waze", 64f, fMed, INK).apply {
            minHeight = dp(110)
            gravity = Gravity.CENTER_VERTICAL
            setOnClickListener { launchSlotOrPkg(4, "com.waze") }
        }, lp(MATCH_PARENT))
        car.addView(View(this).apply { setBackgroundColor(Color.parseColor("#2E2B27")) },
            LinearLayout.LayoutParams(MATCH_PARENT, dp(1)).apply { topMargin = dp(8); bottomMargin = dp(8) })
        car.addView(label("Telefon", 64f, fMed, INK).apply {
            minHeight = dp(110)
            gravity = Gravity.CENTER_VERTICAL
            setOnClickListener { launchSlot(0) }
        }, lp(MATCH_PARENT))
        car.addView(View(this), LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))
        car.addView(label("Nem vezetek", 18f, fMed, MUTED).apply {
            minHeight = dp(48)
            gravity = Gravity.CENTER_VERTICAL
            setOnClickListener {
                prefs.edit().putLong("car_snooze_until", System.currentTimeMillis() + 2 * 60 * 60_000L).apply()
                updateCar()
            }
        }, lp())
    }

    private fun launchSlotOrPkg(slot: Int, pkg: String) {
        val entry = apps.firstOrNull { it.pkg == pkg }
        if (entry != null) launch(entry, bypassPause = true) else launchSlot(slot)
    }

    private fun updateCar() {
        val on = Car.isOn(this)
        if (on && car.visibility != View.VISIBLE) {
            closeDrawer(); hidePause(); hideNote(); hideKeyboard()
        }
        car.visibility = if (on) View.VISIBLE else View.GONE
    }

    private fun pickCar() {
        if (checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.BLUETOOTH_CONNECT), 3)
            return
        }
        val bonded = try {
            getSystemService(BluetoothManager::class.java)?.adapter?.bondedDevices?.toList().orEmpty()
        } catch (_: SecurityException) { emptyList() }
        val names = bonded.map { d -> try { d.name ?: d.address } catch (_: SecurityException) { d.address } }
        val items = (names + "Autós mód kikapcsolása").toTypedArray()
        AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle(if (bonded.isEmpty()) "Nincs párosított eszköz" else "Melyik az autód?")
            .setItems(items) { _, which ->
                if (which < bonded.size) {
                    prefs.edit().putString("car_addr", bonded[which].address)
                        .putString("car_name", names[which]).putBoolean("car_connected", false).apply()
                } else {
                    prefs.edit().remove("car_addr").remove("car_name").putBoolean("car_connected", false).apply()
                }
                updateCar()
            }
            .show()
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
            hideNote()
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
            "Naptár: ${if (prefs.getBoolean("cal_discreet", false)) "diszkrét (csak idő és hely)" else "teljes"}",
            "Naptár hozzáférés",
            "Autós mód: ${prefs.getString("car_name", null) ?: "nincs beállítva"}",
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
                    3 -> {
                        prefs.edit().putBoolean("cal_discreet", !prefs.getBoolean("cal_discreet", false)).apply()
                        refreshEvent()
                    }
                    4 -> if (checkSelfPermission(Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED)
                        safeStart(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
                    else requestPermissions(arrayOf(Manifest.permission.READ_CALENDAR), 1)
                    5 -> pickCar()
                    6 -> safeStart(Intent.createChooser(Intent(Intent.ACTION_SET_WALLPAPER), "Háttérkép"))
                    7 -> safeStart(Intent(Settings.ACTION_HOME_SETTINGS))
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
