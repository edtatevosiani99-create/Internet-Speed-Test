package com.edtatevosiani99.internetspeedtest

import android.app.Activity
import android.app.AppOpsManager
import android.app.usage.NetworkStatsManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.net.ConnectivityManager
import android.os.Bundle
import android.os.Process
import android.provider.Settings
import android.view.Gravity
import android.view.WindowInsets
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import android.os.Build
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

class TrafficActivity : Activity() {
    private val bg = Color.rgb(8, 13, 27)
    private val panel = Color.rgb(16, 25, 45)
    private val cyan = Color.rgb(42, 214, 255)
    private val historyPrefs by lazy { getSharedPreferences("traffic_history_locked", MODE_PRIVATE) }
    private lateinit var content: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = bg
        window.navigationBarColor = bg
        buildPage()
        if (hasUsageAccess()) loadAndRender() else renderPermissionNeeded()
    }

    override fun onResume() {
        super.onResume()
        if (::content.isInitialized) {
            if (hasUsageAccess()) loadAndRender() else renderPermissionNeeded()
        }
    }

    private fun buildPage() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(bg)
            setPadding(dp(16), dp(8), dp(16), dp(12))
        }
        root.setOnApplyWindowInsetsListener { view, insets ->
            @Suppress("DEPRECATION")
            val top = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) insets.getInsets(WindowInsets.Type.statusBars()).top else insets.systemWindowInsetTop
            @Suppress("DEPRECATION")
            val bottom = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) insets.getInsets(WindowInsets.Type.navigationBars()).bottom else insets.systemWindowInsetBottom
            view.setPadding(dp(16), dp(8) + top, dp(16), dp(8) + bottom)
            insets
        }
        val header = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val back = TextView(this).apply {
            text = "‹"; textSize = 36f; setTextColor(cyan); gravity = Gravity.CENTER
            background = panelBackground(); setOnClickListener { finish() }
        }
        header.addView(back, LinearLayout.LayoutParams(dp(46), dp(46)).apply { rightMargin = dp(10) })
        header.addView(TextView(this).apply {
            text = "INTERNET TRAFFIC"; textSize = 19f; typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE); gravity = Gravity.CENTER_VERTICAL
        }, LinearLayout.LayoutParams(0, -2, 1f))
        val refresh = TextView(this).apply {
            text = "↻"; textSize = 27f; setTextColor(cyan); gravity = Gravity.CENTER
            background = panelBackground(); setOnClickListener { loadAndRender() }
        }
        header.addView(refresh, LinearLayout.LayoutParams(dp(46), dp(46)))
        root.addView(header, LinearLayout.LayoutParams(-1, dp(52)))
        root.addView(TextView(this).apply {
            text = "Daily mobile and Wi-Fi usage"; textSize = 13f; setTextColor(Color.LTGRAY)
        }, LinearLayout.LayoutParams(-1, dp(30)).apply { topMargin = dp(5) })

        val scroll = ScrollView(this).apply { isFillViewport = true; setBackgroundColor(bg) }
        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(bg)
            setPadding(0, dp(6), 0, dp(18))
        }
        scroll.addView(content)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
    }

    private fun loadAndRender() {
        if (!::content.isInitialized) return
        if (!hasUsageAccess()) {
            renderPermissionNeeded()
            return
        }
        content.removeAllViews()
        content.addView(label("Updating traffic history…", 14f, Color.LTGRAY), matchWrap().apply { bottomMargin = dp(12) })
        Thread {
            val now = System.currentTimeMillis()
            val todayStart = startOfDay(now)
            val cursor = Calendar.getInstance().apply { timeInMillis = todayStart; add(Calendar.DAY_OF_YEAR, -89) }
            val editor = historyPrefs.edit()
            while (cursor.timeInMillis <= todayStart) {
                val dayStart = cursor.timeInMillis
                val nextDay = Calendar.getInstance().apply { timeInMillis = dayStart; add(Calendar.DAY_OF_YEAR, 1) }.timeInMillis
                val key = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(dayStart))
                val usage = if (dayStart == todayStart) readTrafficForRange(dayStart, now) else readTrafficForRange(dayStart, nextDay)
                saveMax(editor, key + "_wifi", usage.first)
                saveMax(editor, key + "_mobile", usage.second)
                cursor.add(Calendar.DAY_OF_YEAR, 1)
            }
            editor.apply()
            runOnUiThread { renderRows() }
        }.start()
    }

    private fun renderPermissionNeeded() {
        if (!::content.isInitialized) return
        content.removeAllViews()
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = panelBackground()
            setPadding(dp(16), dp(18), dp(16), dp(18))
        }
        card.addView(label("Traffic history", 19f, Color.WHITE, true), matchWrap())
        card.addView(label(
            "This page shows daily and monthly phone traffic, separated into mobile data and Wi-Fi. Android requires you to allow Usage access first. Your traffic page will stay here; it will not redirect you automatically.",
            14f, Color.rgb(205, 220, 238)
        ), matchWrap().apply { topMargin = dp(12) })
        val allow = TextView(this).apply {
            text = "ALLOW USAGE ACCESS"
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(bg)
            gravity = Gravity.CENTER
            setPadding(dp(12), dp(14), dp(12), dp(14))
            background = android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = dp(12).toFloat()
                setColor(cyan)
            }
            setOnClickListener {
                try {
                    startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                } catch (_: Exception) {
                    Toast.makeText(this@TrafficActivity, "Open Android Settings and allow Usage access", Toast.LENGTH_LONG).show()
                }
            }
        }
        card.addView(allow, matchWrap().apply { topMargin = dp(16) })
        content.addView(card, matchWrap().apply { topMargin = dp(10) })
        content.addView(label(
            "After enabling access, return to this page. Statistics will load automatically. The app does not provide a button to erase traffic history.",
            12f, Color.rgb(125, 145, 170)
        ), matchWrap().apply { topMargin = dp(14) })
    }

    private fun renderRows() {
        content.removeAllViews()
        val allDates = historyPrefs.all.keys.mapNotNull { key ->
            key.substringBeforeLast("_").takeIf { it.matches(Regex("\\d{4}-\\d{2}-\\d{2}")) }
        }.distinct().sortedDescending()
        val todayKey = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        if (allDates.none { it == todayKey }) {
            content.addView(label("No traffic data available yet. Check Usage access in Android Settings.", 13f, Color.LTGRAY), matchWrap())
        }

        val table = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = panelBackground()
            setPadding(dp(8), dp(8), dp(8), dp(8))
        }
        table.addView(tableRow("Date", "Mob", "WiFi", "Total", true))
        allDates.take(90).forEach { date ->
            val wifi = historyPrefs.getLong(date + "_wifi", -1L)
            val mobile = historyPrefs.getLong(date + "_mobile", -1L)
            val total = if (wifi >= 0L && mobile >= 0L) wifi + mobile else -1L
            val displayDate = try { SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(date)!!) } catch (_: Exception) { date }
            table.addView(tableRow(displayDate, formatBytes(mobile), formatBytes(wifi), formatBytes(total), date == todayKey))
            if (date != allDates.take(90).lastOrNull()) table.addView(LinearLayout(this).apply { setBackgroundColor(Color.rgb(35, 55, 80)) }, LinearLayout.LayoutParams(-1, dp(1)))
        }
        content.addView(table, matchWrap())

        content.addView(label("За месяц", 16f, cyan, true), matchWrap().apply { topMargin = dp(22); bottomMargin = dp(8) })
        val monthly = allDates.map { it.substring(0, 7) }.distinct().sortedDescending().take(12)
        monthly.forEach { month ->
            var wifi = 0L
            var mobile = 0L
            var wifiKnown = false
            var mobileKnown = false
            allDates.filter { it.startsWith(month) }.forEach { date ->
                val w = historyPrefs.getLong(date + "_wifi", -1L)
                val m = historyPrefs.getLong(date + "_mobile", -1L)
                if (w >= 0L) { wifi += w; wifiKnown = true }
                if (m >= 0L) { mobile += m; mobileKnown = true }
            }
            val total = if (wifiKnown && mobileKnown) wifi + mobile else -1L
            val monthName = try { SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(SimpleDateFormat("yyyy-MM", Locale.US).parse(month)!!) } catch (_: Exception) { month }
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL; background = panelBackground()
                setPadding(dp(12), dp(10), dp(12), dp(10))
            }
            card.addView(label(monthName, 14f, Color.WHITE, true), matchWrap())
            card.addView(label("Mob: " + if (mobileKnown) formatBytes(mobile) else "—", 12f, Color.rgb(205, 220, 238)), matchWrap().apply { topMargin = dp(4) })
            card.addView(label("WiFi: " + if (wifiKnown) formatBytes(wifi) else "—", 12f, Color.rgb(205, 220, 238)), matchWrap().apply { topMargin = dp(2) })
            card.addView(label("Total: " + formatBytes(total), 13f, cyan, true), matchWrap().apply { topMargin = dp(4) })
            content.addView(card, matchWrap().apply { bottomMargin = dp(8) })
        }
        content.addView(label("History is stored on this phone and the app does not provide a delete button.", 11f, Color.rgb(125, 145, 170)), matchWrap().apply { topMargin = dp(10) })
    }

    private fun tableRow(date: String, mobile: String, wifi: String, total: String, highlighted: Boolean): LinearLayout {
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val color = if (highlighted) cyan else Color.rgb(205, 220, 238)
        row.addView(cell(date, color, 1.2f, highlighted), LinearLayout.LayoutParams(0, dp(42), 1.2f))
        row.addView(cell(mobile, color, 1f, highlighted), LinearLayout.LayoutParams(0, dp(42), 1f))
        row.addView(cell(wifi, color, 1f, highlighted), LinearLayout.LayoutParams(0, dp(42), 1f))
        row.addView(cell(total, color, 1f, highlighted), LinearLayout.LayoutParams(0, dp(42), 1f))
        return row
    }

    private fun cell(value: String, color: Int, weight: Float, bold: Boolean): TextView = TextView(this).apply {
        text = value; textSize = 10f; setTextColor(color); gravity = Gravity.CENTER
        maxLines = 2; includeFontPadding = false
        if (bold) typeface = Typeface.DEFAULT_BOLD
    }

    private fun label(value: String, size: Float, color: Int, bold: Boolean = false) = TextView(this).apply {
        text = value; textSize = size; setTextColor(color)
        if (bold) typeface = Typeface.DEFAULT_BOLD
    }

    private fun hasUsageAccess(): Boolean {
        val appOps = getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        @Suppress("DEPRECATION")
        val mode = appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), packageName)
        return mode == AppOpsManager.MODE_ALLOWED
    }

    private fun startOfDay(time: Long): Long = Calendar.getInstance().apply {
        timeInMillis = time
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    private fun readTrafficForRange(start: Long, end: Long): Pair<Long, Long> {
        fun totalBytes(type: Int): Long = try {
            val manager = getSystemService(Context.NETWORK_STATS_SERVICE) as NetworkStatsManager
            val bucket = manager.querySummaryForDevice(type, null, start, end)
            bucket.rxBytes + bucket.txBytes
        } catch (_: Exception) { -1L }
        return Pair(totalBytes(ConnectivityManager.TYPE_WIFI), totalBytes(ConnectivityManager.TYPE_MOBILE))
    }

    private fun saveMax(editor: android.content.SharedPreferences.Editor, key: String, value: Long) {
        if (value >= 0L && value > historyPrefs.getLong(key, -1L)) editor.putLong(key, value)
    }

    private fun formatBytes(bytes: Long): String {
        if (bytes < 0L) return "Unavailable"
        if (bytes < 1024L) return "$bytes B"
        val kb = bytes / 1024.0
        if (kb < 1024.0) return String.format(Locale.US, "%.1f KB", kb)
        val mb = kb / 1024.0
        if (mb < 1024.0) return String.format(Locale.US, "%.1f MB", mb)
        return String.format(Locale.US, "%.2f GB", mb / 1024.0)
    }

    private fun panelBackground() = android.graphics.drawable.GradientDrawable().apply {
        cornerRadius = dp(13).toFloat(); setColor(panel); setStroke(dp(1), Color.rgb(35, 64, 91))
    }
    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).roundToInt()
    private fun matchWrap() = LinearLayout.LayoutParams(-1, -2)
}
