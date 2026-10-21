package com.edtatevosiani99.internetspeedtest

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.app.AppOpsManager
import android.app.usage.NetworkStatsManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.telephony.TelephonyManager
import android.os.Build
import android.os.Process
import android.provider.Settings
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.widget.PopupMenu
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread
import kotlin.math.abs
import kotlin.math.roundToInt

class MainActivity : Activity() {
    private val bg = Color.rgb(5, 10, 23)
    private val panel = Color.rgb(12, 23, 43)
    private val cyan = Color.rgb(42, 214, 255)
    private val blue = Color.rgb(45, 111, 255)
    private val muted = Color.rgb(151, 174, 203)
    private lateinit var status: TextView
    private lateinit var pingValue: TextView
    private lateinit var downloadValue: TextView
    private lateinit var uploadValue: TextView
    private lateinit var jitterValue: TextView
    private lateinit var networkInfo: TextView
    private lateinit var headerNetwork: TextView
    private lateinit var historyText: TextView
    private lateinit var trafficUsageText: TextView
    private lateinit var startButton: Button
    private lateinit var globeView: AnimatedGlobeView
    private lateinit var shareButton: Button
    private var lastResult = ""
    private var currentNetworkLabel = "Unknown network"
    private val prefs by lazy { getSharedPreferences("speed_test", MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = bg
        window.navigationBarColor = bg
        lastResult = prefs.getString("last_result", "") ?: ""
        buildScreen()
        refreshNetworkInfo()
        refreshHistory()
    }

    private fun buildScreen() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(20), dp(22), dp(20), dp(24))
            setBackgroundColor(bg)
        }
        val scroll = ScrollView(this).apply { setBackgroundColor(bg); isFillViewport = true }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(0, 0, 0, dp(8))
            setBackgroundColor(bg)
        }
        // Respect system-bar insets so the app content starts below Android's notification/status bar.
        root.setOnApplyWindowInsetsListener { view, insets ->
            @Suppress("DEPRECATION")
            val topInset = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                insets.getInsets(WindowInsets.Type.statusBars()).top
            } else insets.systemWindowInsetTop
            @Suppress("DEPRECATION")
            val bottomInset = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                insets.getInsets(WindowInsets.Type.navigationBars()).bottom
            } else insets.systemWindowInsetBottom
            view.setPadding(dp(20), dp(12) + topInset, dp(20), dp(16) + bottomInset)
            insets
        }
        val header = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val menuButton = TextView(this).apply {
            text = "⋮"; textSize = 27f; typeface = Typeface.DEFAULT_BOLD
            setTextColor(cyan); gravity = Gravity.CENTER
            background = android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = dp(9).toFloat(); setColor(panel); setStroke(dp(1), cyan)
            }
            setOnClickListener { showAppMenu(this) }
        }
        header.addView(menuButton, LinearLayout.LayoutParams(dp(42), dp(42)).apply { rightMargin = dp(8) })
        header.addView(TextView(this).apply {
            text = "INTERNET SPEED TEST"; textSize = 16f; setTextColor(Color.WHITE)
            maxLines = 1; isSingleLine = true; includeFontPadding = false
            typeface = Typeface.create("sans-serif-black", Typeface.BOLD); gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(0, -2, 1f))
        headerNetwork = TextView(this).apply {
            text = "●"; textSize = 12f; setTextColor(cyan); gravity = Gravity.CENTER
            background = android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = dp(9).toFloat(); setColor(panel); setStroke(dp(1), Color.rgb(35, 64, 91))
            }
            contentDescription = "Current network connection"
            setOnClickListener {
                AlertDialog.Builder(this@MainActivity).setTitle("Network connection")
                    .setMessage(networkInfo.text).setPositiveButton("Refresh") { _, _ -> refreshNetworkInfo() }
                    .setNegativeButton("Close", null).show()
            }
        }
        header.addView(headerNetwork, LinearLayout.LayoutParams(dp(42), dp(42)))
        root.addView(header, LinearLayout.LayoutParams(-1, dp(46)))
        content.addView(TextView(this).apply {
            text = tr("MEASURE YOUR CONNECTION"); textSize = 11f; letterSpacing = 0.18f
            setTextColor(cyan); gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(-1, dp(28)))

        globeView = AnimatedGlobeView(this)
        content.addView(globeView, LinearLayout.LayoutParams(dp(368), dp(308)).apply {
            gravity = Gravity.CENTER; topMargin = dp(7); bottomMargin = dp(0)
        })
        status = TextView(this).apply {
            text = tr("Ready to test"); textSize = 14f; setTextColor(Color.LTGRAY); gravity = Gravity.CENTER
        }
        content.addView(status, LinearLayout.LayoutParams(-1, dp(34)))

        val row1 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        pingValue = metric(row1, tr("PING"), "—", "ms")
        downloadValue = metric(row1, tr("DOWNLOAD"), "—", "Mbps")
        content.addView(row1, LinearLayout.LayoutParams(-1, dp(88)).apply { topMargin = dp(10) })
        val row2 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        uploadValue = metric(row2, tr("UPLOAD"), "—", "Mbps")
        jitterValue = metric(row2, tr("JITTER"), "—", "ms")
        content.addView(row2, LinearLayout.LayoutParams(-1, dp(88)).apply { topMargin = dp(8) })

        startButton = Button(this).apply {
            text = tr("START TEST"); textSize = 15f; letterSpacing = 0.08f; typeface = Typeface.DEFAULT_BOLD; setTextColor(Color.rgb(4, 14, 29))
            elevation = dp(5).toFloat()
            background = android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = dp(16).toFloat()
                colors = intArrayOf(cyan, blue)
                orientation = android.graphics.drawable.GradientDrawable.Orientation.LEFT_RIGHT
            }
            setOnClickListener { runTest() }
        }
        content.addView(startButton, LinearLayout.LayoutParams(-1, dp(56)).apply { topMargin = dp(20) })
        shareButton = Button(this).apply {
            text = tr("SHARE RESULTS"); textSize = 13f; letterSpacing = 0.04f; setTextColor(cyan); background = outlineBackground()
            elevation = dp(2).toFloat()
            isEnabled = lastResult.isNotBlank(); alpha = if (isEnabled) 1f else 0.55f
            setOnClickListener {
                if (lastResult.isNotBlank()) {
                    val send = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"; putExtra(Intent.EXTRA_TEXT, lastResult)
                    }
                    startActivity(Intent.createChooser(send, tr("Share speed test results")))
                }
            }
        }
        content.addView(shareButton, LinearLayout.LayoutParams(-1, dp(50)).apply { topMargin = dp(8) })

        networkInfo = TextView(this).apply {
            textSize = 13f; setTextColor(Color.rgb(205, 220, 238))
            setPadding(dp(14), dp(12), dp(14), dp(12)); background = panelBackground()
            elevation = dp(3).toFloat()
        }
        addSectionTitle(content, "RECENT TESTS")
        historyText = TextView(this).apply {
            textSize = 12f; setTextColor(Color.rgb(205, 220, 238))
            setPadding(dp(14), dp(12), dp(14), dp(12)); background = panelBackground()
            elevation = dp(3).toFloat()
        }
        content.addView(historyText, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(6) })
                content.addView(TextView(this).apply {
            text = tr("No ads • No account required"); textSize = 12f
            setTextColor(Color.rgb(125, 145, 170)); gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(-1, dp(38)).apply { topMargin = dp(8) })
        scroll.addView(content)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
    }

    private fun metric(parent: LinearLayout, label: String, initial: String, unit: String): TextView {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; setPadding(dp(3), dp(7), dp(3), dp(7))
            background = android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = dp(16).toFloat()
        colors = intArrayOf(Color.rgb(16, 32, 56), Color.rgb(10, 20, 39))
        orientation = android.graphics.drawable.GradientDrawable.Orientation.TL_BR
        setStroke(dp(1), Color.rgb(36, 77, 117))
            }
        }
        card.addView(TextView(this).apply { text = label; textSize = 9f; setTextColor(Color.LTGRAY); gravity = Gravity.CENTER })
        val value = TextView(this).apply {
            text = initial; textSize = 21f; typeface = Typeface.DEFAULT_BOLD; setTextColor(cyan); gravity = Gravity.CENTER
        }
        card.addView(value)
        card.addView(TextView(this).apply { text = unit; textSize = 9f; setTextColor(Color.LTGRAY); gravity = Gravity.CENTER })
        parent.addView(card, LinearLayout.LayoutParams(0, -1, 1f).apply { setMargins(dp(4), 0, dp(4), 0) })
        return value
    }

    private fun runTest() {
        if (::globeView.isInitialized) globeView.setTesting(true)
        startButton.isEnabled = false; startButton.text = tr("TESTING...")
        shareButton.isEnabled = false
        status.text = tr("Checking latency") + " (1/20)..."
        pingValue.text = "…"; downloadValue.text = "…"; uploadValue.text = "…"; jitterValue.text = "…"
        thread {
            try {
                // Cloudflare's published engine uses 20 latency requests, median latency,
                // and increasing transfer sizes. Android HttpURLConnection cannot expose
                // browser PerformanceResourceTiming, so this is an explicitly adapted
                // native implementation, not a byte-for-byte port of their browser engine.
                val latencySamples = mutableListOf<Double>()
                for (i in 1..20) {
                    try {
                        val c = (URL("https://speed.cloudflare.com/__down?bytes=0&check=$i")
                            .openConnection() as HttpURLConnection).apply {
                            connectTimeout = 7000; readTimeout = 7000; useCaches = false
                            setRequestProperty("Cache-Control", "no-cache")
                            setRequestProperty("Connection", "keep-alive")
                        }
                        val started = System.nanoTime()
                        val code = c.responseCode
                        val elapsed = (System.nanoTime() - started) / 1_000_000.0
                        if (code in 200..299 && i > 2 && elapsed > 0.0) latencySamples.add(elapsed)
                        try { c.inputStream.use { it.readBytes() } } catch (_: Exception) { }
                    } catch (_: Exception) { }
                    val n = i
                    runOnUiThread { status.text = tr("Checking latency") + " ($n/20)..." }
                }
                if (latencySamples.size < 10) throw IllegalStateException("Not enough valid latency samples")
                val sortedLatency = latencySamples.sorted()
                val ping = if (sortedLatency.size % 2 == 0)
                    (sortedLatency[sortedLatency.size / 2 - 1] + sortedLatency[sortedLatency.size / 2]) / 2.0
                else sortedLatency[sortedLatency.size / 2]
                val jitter = if (latencySamples.size > 1)
                    latencySamples.zipWithNext { a, b -> abs(b - a) }.average() else 0.0
                // Publish latency as soon as it is measured instead of waiting for the whole test.
                runOnUiThread {
                    pingValue.text = ping.roundToInt().toString()
                    jitterValue.text = jitter.roundToInt().toString()
                    status.text = tr("Measuring download") + "..."
                }

                // Cloudflare's documented engine ramps through 100 KB, 1 MB, 10 MB,
                // 25 MB and larger payloads, stopping when a request set lasts >= 1 s.
                // To avoid excessive mobile-data use, this native adaptation uses at most
                // 3 samples per stage and caps each direction at the 10 MB stage.
                val sizes = longArrayOf(100_000L, 1_000_000L, 10_000_000L)
                val counts = intArrayOf(3, 3, 3)
                val minRequestDurationMs = 10.0
                val finishRequestDurationMs = 1000.0

                fun measureDownload(): Double {
                    val points = mutableListOf<Pair<Double, Double>>() // Mbps, duration ms
                    for (stage in sizes.indices) {
                        for (sample in 0 until counts[stage]) {
                            val url = URL("https://speed.cloudflare.com/__down?bytes=${sizes[stage]}&r=${stage}_${sample}_${System.nanoTime()}")
                            val c = (url.openConnection() as HttpURLConnection).apply {
                                connectTimeout = 15000; readTimeout = 30000; useCaches = false
                                setRequestProperty("Cache-Control", "no-cache")
                            }
                            var received = 0L
                            val code = c.responseCode
                            if (code !in 200..299) { c.disconnect(); throw IllegalStateException("Download server returned HTTP $code") }
                            val began = System.nanoTime()
                            c.inputStream.use { input ->
                                val buffer = ByteArray(32768)
                                while (true) { val n = input.read(buffer); if (n < 0) break; received += n }
                            }
                            val elapsedMs = (System.nanoTime() - began) / 1_000_000.0
                            c.disconnect()
                            if (elapsedMs >= minRequestDurationMs && received > 0) {
                                val currentMbps = received * 8.0 / (elapsedMs / 1000.0) / 1_000_000.0
                                points.add(Pair(currentMbps, elapsedMs))
                                val liveText = String.format(java.util.Locale.US, "%.1f", percentile(points.map { it.first }, 0.9))
                                runOnUiThread {
                                    downloadValue.text = liveText
                                    status.text = tr("Measuring download") + " • " + (sample + 1) + "/" + counts[stage]
                                }
                            }
                            if (elapsedMs >= finishRequestDurationMs) break
                        }
                        if (points.lastOrNull()?.second?.let { it >= finishRequestDurationMs } == true) break
                    }
                    if (points.isEmpty()) throw IllegalStateException("No valid download samples")
                    return percentile(points.map { it.first }, 0.9)
                }

                fun measureUpload(): Double {
                    val points = mutableListOf<Pair<Double, Double>>()
                    for (stage in sizes.indices) {
                        for (sample in 0 until counts[stage]) {
                            val payload = ByteArray(sizes[stage].toInt())
                            val c = (URL("https://speed.cloudflare.com/__up")
                                .openConnection() as HttpURLConnection).apply {
                                requestMethod = "POST"; doOutput = true
                                connectTimeout = 15000; readTimeout = 30000; useCaches = false
                                setRequestProperty("Content-Type", "application/octet-stream")
                                setFixedLengthStreamingMode(payload.size)
                            }
                            val output = c.outputStream // establish request before timing payload transfer
                            val began = System.nanoTime()
                            output.use { it.write(payload) }
                            val elapsedMs = (System.nanoTime() - began) / 1_000_000.0
                            val code = c.responseCode
                            try {
                                val stream = if (code in 200..299) c.inputStream else c.errorStream
                                stream?.use { it.readBytes() }
                            } catch (_: Exception) { }
                            c.disconnect()
                            if (code !in 200..299) throw IllegalStateException("Upload server returned HTTP $code")
                            if (elapsedMs >= minRequestDurationMs) {
                                val currentMbps = payload.size * 8.0 / (elapsedMs / 1000.0) / 1_000_000.0
                                points.add(Pair(currentMbps, elapsedMs))
                                val liveText = String.format(java.util.Locale.US, "%.1f", percentile(points.map { it.first }, 0.9))
                                runOnUiThread {
                                    uploadValue.text = liveText
                                    status.text = tr("Measuring upload") + " • " + (sample + 1) + "/" + counts[stage]
                                }
                            }
                            if (elapsedMs >= finishRequestDurationMs) break
                        }
                        if (points.lastOrNull()?.second?.let { it >= finishRequestDurationMs } == true) break
                    }
                    if (points.isEmpty()) throw IllegalStateException("No valid upload samples")
                    return percentile(points.map { it.first }, 0.9)
                }

                runOnUiThread { status.text = tr("Measuring download") + "..." }
                val downMbps = measureDownload()
                runOnUiThread {
                    downloadValue.text = String.format(java.util.Locale.US, "%.1f", downMbps)
                    status.text = tr("Measuring upload") + "..."
                }
                val upMbps = measureUpload()
                val downText = String.format(java.util.Locale.US, "%.1f", downMbps)
                val upText = String.format(java.util.Locale.US, "%.1f", upMbps)
                val result = "INTERNET SPEED TEST\n${tr("Ping: ")}${ping.roundToInt()} ms\n${tr("Jitter: ")}${jitter.roundToInt()} ms\n${tr("Download: ")}${downText} Mbps\n${tr("Upload: ")}${upText} Mbps"
                val date = java.text.SimpleDateFormat("dd MMM, HH:mm", java.util.Locale.getDefault()).format(java.util.Date())
                runOnUiThread {
                    pingValue.text = ping.roundToInt().toString()
                    jitterValue.text = jitter.roundToInt().toString()
                    downloadValue.text = downText; uploadValue.text = upText
                    status.text = tr("Test complete")
                    if (::globeView.isInitialized) globeView.setTesting(false)
                    lastResult = result
                    prefs.edit().putString("last_result", result).apply()
                    refreshNetworkInfo()
                    val historyLine = "$date  •  ↓$downText Mbps  ↑$upText Mbps  •  ${tr("Ping: ")}${ping.roundToInt()} ms  •  ${tr("Jitter: ")}${jitter.roundToInt()} ms  •  $currentNetworkLabel"
                    val history = prefs.getString("history", "") ?: ""
                    val updated = (listOf(historyLine) + history.split("\n").filter { it.isNotBlank() }).joinToString("\n")
                    prefs.edit().putString("history", updated).apply()
                    refreshHistory()
                    shareButton.isEnabled = true; shareButton.alpha = 1f
                    startButton.isEnabled = true; startButton.text = tr("TEST AGAIN")
                }
            } catch (e: Exception) {
                runOnUiThread {
                    status.text = tr("Test failed. Check internet and try again.")
                    if (::globeView.isInitialized) globeView.setTesting(false)
                    startButton.isEnabled = true; startButton.text = tr("TRY AGAIN")
                    shareButton.isEnabled = lastResult.isNotBlank()
                    Toast.makeText(this, e.localizedMessage ?: tr("Connection error"), Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun percentile(values: List<Double>, p: Double): Double {
        if (values.isEmpty()) return 0.0
        val sorted = values.sorted()
        val index = kotlin.math.ceil(p.coerceIn(0.0, 1.0) * sorted.size).toInt() - 1
        return sorted[index.coerceIn(0, sorted.lastIndex)]
    }

    private class NoAdsCircleSpan(
        private val diameterPx: Int,
        private val textSizePx: Int
    ) : android.text.style.ReplacementSpan() {
        override fun getSize(
            paint: android.graphics.Paint,
            text: CharSequence,
            start: Int,
            end: Int,
            fm: android.graphics.Paint.FontMetricsInt?
        ): Int = diameterPx

        override fun draw(
            canvas: android.graphics.Canvas,
            text: CharSequence,
            start: Int,
            end: Int,
            x: Float,
            top: Int,
            y: Int,
            bottom: Int,
            paint: android.graphics.Paint
        ) {
            val cx = x + diameterPx / 2f
            val cy = (top + bottom) / 2f
            val oldColor = paint.color
            val oldStyle = paint.style
            val oldTextSize = paint.textSize
            val oldTypeface = paint.typeface
            paint.color = Color.rgb(225, 25, 45)
            paint.style = android.graphics.Paint.Style.FILL
            canvas.drawCircle(cx, cy, diameterPx / 2f, paint)
            paint.color = Color.WHITE
            paint.textSize = textSizePx.toFloat()
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            paint.textAlign = android.graphics.Paint.Align.CENTER
            val label = "NO ADS"
            canvas.drawText(label, cx, cy - (paint.ascent() + paint.descent()) / 2f, paint)
            paint.color = oldColor
            paint.style = oldStyle
            paint.textSize = oldTextSize
            paint.typeface = oldTypeface
            paint.textAlign = android.graphics.Paint.Align.LEFT
        }
    }

    private fun showAppMenu(anchor: View) {
        val menu = PopupMenu(this, anchor)
        // Keep the menu in the requested order: Traffic, History, Language, About, No Ads.
        menu.menu.add(0, 5, 0, "📊  ${tr("Traffic")}")
        menu.menu.add(0, 1, 1, "🕘  ${tr("History")}")
        menu.menu.add(0, 4, 2, "🌐  ${tr("Language")}")
        menu.menu.add(0, 2, 3, "ℹ️  ${tr("About app")}")
        // Render NO ADS as bold white lettering centered inside a solid red circle.
        val noAdsTitle = android.text.SpannableString("NO ADS")
        noAdsTitle.setSpan(NoAdsCircleSpan(dp(44), dp(9)), 0, noAdsTitle.length, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        menu.menu.add(0, 3, 4, noAdsTitle)
        menu.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                1 -> showHistoryDialog()
                5 -> startActivity(Intent(this, TrafficActivity::class.java))
                2 -> AlertDialog.Builder(this)
                    .setTitle(tr("About app"))
                    .setMessage(tr("about_message"))
                    .setPositiveButton(tr("OK"), null)
                    .show()
                3 -> showNoAdsDialog()
                4 -> showLanguageDialog()
                else -> return@setOnMenuItemClickListener false
            }
            true
        }
        menu.show()
    }


    private fun tr(text: String): String {
        val lang = prefs.getString("language", "en") ?: "en"
        val dict = mapOf(
            "ru" to mapOf("Traffic" to "Трафик", "DATA USAGE" to "РАСХОД ТРАФИКА", "TODAY" to "СЕГОДНЯ", "THIS MONTH" to "ЗА МЕСЯЦ", "Wi-Fi" to "Wi-Fi", "Mobile data" to "Мобильный интернет", "Tap to refresh traffic statistics" to "Нажмите, чтобы обновить статистику", "Tap to view traffic history" to "Нажмите, чтобы открыть историю трафика", "Traffic history" to "История трафика", "DAILY HISTORY" to "ИСТОРИЯ ПО ДНЯМ", "MONTHLY HISTORY" to "ИСТОРИЯ ПО МЕСЯЦАМ", "History starts after Usage access is enabled." to "История начнётся после разрешения доступа к статистике.", "History is saved on this phone and cannot be deleted from the app." to "История сохраняется на телефоне, удалить её из приложения нельзя.", "Refresh" to "Обновить", "Close" to "Закрыть", "Track all phone traffic separately for Wi-Fi and mobile data. Tap here to allow Usage access." to "Учитывайте весь трафик телефона отдельно для Wi-Fi и мобильной сети. Нажмите, чтобы разрешить доступ к статистике.", "Loading traffic statistics…" to "Загрузка статистики трафика…", "Unavailable" to "Недоступно", "Traffic statistics refreshed" to "Статистика трафика обновлена", "Open Android Settings and allow Usage access" to "Откройте настройки Android и разрешите доступ к статистике", "INTERNET SPEED TEST" to "ТЕСТ СКОРОСТИ ИНТЕРНЕТА", "MEASURE YOUR CONNECTION" to "ИЗМЕРЬТЕ СВОЁ СОЕДИНЕНИЕ", "Ready to test" to "Готово к проверке", "PING" to "ПИНГ", "DOWNLOAD" to "ЗАГРУЗКА", "UPLOAD" to "ОТДАЧА", "JITTER" to "ДЖИТТЕР", "START TEST" to "НАЧАТЬ ТЕСТ", "TEST AGAIN" to "ПОВТОРИТЬ ТЕСТ", "TRY AGAIN" to "ПОВТОРИТЬ", "TESTING..." to "ТЕСТИРОВАНИЕ...", "SHARE RESULTS" to "ПОДЕЛИТЬСЯ РЕЗУЛЬТАТАМИ", "RECENT TESTS" to "ПОСЛЕДНИЕ ТЕСТЫ", "No ads • No account required" to "Без рекламы • Без аккаунта"),
            "ka" to mapOf("DATA USAGE" to "ტრაფიკის გამოყენება", "TODAY" to "დღეს", "THIS MONTH" to "ამ თვეში", "Wi-Fi" to "Wi-Fi", "Mobile data" to "მობილური ინტერნეტი", "Tap to refresh traffic statistics" to "შეეხეთ სტატისტიკის განახლებას", "Tap to view traffic history" to "შეეხეთ ტრაფიკის ისტორიის სანახავად", "Traffic history" to "ტრაფიკის ისტორია", "DAILY HISTORY" to "დღიური ისტორია", "MONTHLY HISTORY" to "თვიური ისტორია", "History starts after Usage access is enabled." to "ისტორია დაიწყება სტატისტიკაზე წვდომის ჩართვის შემდეგ.", "History is saved on this phone and cannot be deleted from the app." to "ისტორია ტელეფონში ინახება და აპიდან ვერ წაიშლება.", "Refresh" to "განახლება", "Close" to "დახურვა", "Track all phone traffic separately for Wi-Fi and mobile data. Tap here to allow Usage access." to "ტელეფონის მთელი ტრაფიკის აღრიცხვა Wi-Fi-სა და მობილურ ინტერნეტზე ცალ-ცალკე. შეეხეთ სტატისტიკაზე წვდომის დასაშვებად.", "Loading traffic statistics…" to "ტრაფიკის სტატისტიკის ჩატვირთვა…", "Unavailable" to "მიუწვდომელია", "Traffic statistics refreshed" to "ტრაფიკის სტატისტიკა განახლდა", "Open Android Settings and allow Usage access" to "გახსენით Android-ის პარამეტრები და დაუშვით სტატისტიკაზე წვდომა", "INTERNET SPEED TEST" to "ინტერნეტის სიჩქარის ტესტი", "MEASURE YOUR CONNECTION" to "შეამოწმეთ კავშირი", "Ready to test" to "ტესტისთვის მზადაა", "PING" to "პინგი", "DOWNLOAD" to "ჩამოტვირთვა", "UPLOAD" to "ატვირთვა", "JITTER" to "ჯიტერი", "START TEST" to "ტესტის დაწყება", "TEST AGAIN" to "ხელახლა ტესტი", "TRY AGAIN" to "ხელახლა სცადეთ", "TESTING..." to "მოწმდება...", "SHARE RESULTS" to "შედეგების გაზიარება", "RECENT TESTS" to "ბოლო ტესტები", "No ads • No account required" to "რეკლამის გარეშე • ანგარიშის გარეშე"),
            "de" to mapOf("DATA USAGE" to "DATENVERBRAUCH", "TODAY" to "HEUTE", "THIS MONTH" to "DIESEN MONAT", "Wi-Fi" to "WLAN", "Mobile data" to "Mobile Daten", "Tap to refresh traffic statistics" to "Tippen, um die Statistik zu aktualisieren", "Tap to view traffic history" to "Tippen, um den Datenverlauf zu öffnen", "Traffic history" to "Datenverlauf", "DAILY HISTORY" to "TAGESVERLAUF", "MONTHLY HISTORY" to "MONATSVERLAUF", "History starts after Usage access is enabled." to "Der Verlauf beginnt, sobald der Statistikzugriff erlaubt ist.", "History is saved on this phone and cannot be deleted from the app." to "Der Verlauf wird auf diesem Telefon gespeichert und kann nicht in der App gelöscht werden.", "Refresh" to "Aktualisieren", "Close" to "Schließen", "Track all phone traffic separately for Wi-Fi and mobile data. Tap here to allow Usage access." to "Gesamten Datenverbrauch getrennt für WLAN und mobile Daten erfassen. Tippen, um den Zugriff zu erlauben.", "Loading traffic statistics…" to "Datenstatistik wird geladen…", "Unavailable" to "Nicht verfügbar", "Traffic statistics refreshed" to "Datenstatistik aktualisiert", "Open Android Settings and allow Usage access" to "Öffne die Android-Einstellungen und erlaube den Nutzungszugriff", "INTERNET SPEED TEST" to "INTERNET-GESCHWINDIGKEIT", "MEASURE YOUR CONNECTION" to "VERBINDUNG MESSEN", "Ready to test" to "Bereit zum Testen", "DOWNLOAD" to "DOWNLOAD", "UPLOAD" to "UPLOAD", "START TEST" to "TEST STARTEN", "TEST AGAIN" to "ERNEUT TESTEN", "TRY AGAIN" to "NOCHMALS VERSUCHEN", "TESTING..." to "TEST LÄUFT...", "SHARE RESULTS" to "ERGEBNISSE TEILEN", "RECENT TESTS" to "LETZTE TESTS", "No ads • No account required" to "Keine Werbung • Kein Konto nötig"),
            "es" to mapOf("INTERNET SPEED TEST" to "VELOCIDAD DE INTERNET", "MEASURE YOUR CONNECTION" to "MIDE TU CONEXIÓN", "Ready to test" to "Listo para probar", "DOWNLOAD" to "DESCARGA", "UPLOAD" to "SUBIDA", "START TEST" to "INICIAR PRUEBA", "TEST AGAIN" to "REPETIR PRUEBA", "SHARE RESULTS" to "COMPARTIR RESULTADOS", "RECENT TESTS" to "PRUEBAS RECIENTES"),
            "fr" to mapOf("INTERNET SPEED TEST" to "TEST DE VITESSE INTERNET", "MEASURE YOUR CONNECTION" to "MESUREZ VOTRE CONNEXION", "Ready to test" to "Prêt à tester", "DOWNLOAD" to "TÉLÉCHARGEMENT", "UPLOAD" to "ENVOI", "START TEST" to "LANCER LE TEST", "TEST AGAIN" to "REFAIRE LE TEST", "SHARE RESULTS" to "PARTAGER LES RÉSULTATS", "RECENT TESTS" to "TESTS RÉCENTS"),
            "it" to mapOf("INTERNET SPEED TEST" to "TEST VELOCITÀ INTERNET", "MEASURE YOUR CONNECTION" to "MISURA LA CONNESSIONE", "Ready to test" to "Pronto per il test", "DOWNLOAD" to "DOWNLOAD", "UPLOAD" to "UPLOAD", "START TEST" to "AVVIA TEST", "TEST AGAIN" to "RIPETI TEST", "SHARE RESULTS" to "CONDIVIDI RISULTATI", "RECENT TESTS" to "TEST RECENTI"),
            "tr" to mapOf("INTERNET SPEED TEST" to "İNTERNET HIZ TESTİ", "MEASURE YOUR CONNECTION" to "BAĞLANTI HIZINI ÖLÇ", "Ready to test" to "Teste hazır", "DOWNLOAD" to "İNDİRME", "UPLOAD" to "YÜKLEME", "START TEST" to "TESTİ BAŞLAT", "TEST AGAIN" to "TEKRAR TEST ET", "SHARE RESULTS" to "SONUÇLARI PAYLAŞ", "RECENT TESTS" to "SON TESTLER"),
            "uk" to mapOf("INTERNET SPEED TEST" to "ТЕСТ ШВИДКОСТІ ІНТЕРНЕТУ", "MEASURE YOUR CONNECTION" to "ВИМІРЮЙТЕ З’ЄДНАННЯ", "Ready to test" to "Готово до тесту", "DOWNLOAD" to "ЗАВАНТАЖЕННЯ", "UPLOAD" to "ВІДДАВАННЯ", "START TEST" to "ПОЧАТИ ТЕСТ", "TEST AGAIN" to "ПОВТОРИТИ ТЕСТ", "SHARE RESULTS" to "ПОДІЛИТИСЯ РЕЗУЛЬТАТАМИ", "RECENT TESTS" to "ОСТАННІ ТЕСТИ")
        )

        val extra = mapOf(
            "ru" to mapOf(
                "History" to "История", "About app" to "О приложении", "Language" to "Язык", "OK" to "ОК",
                "about_message" to "Проверяйте скорость загрузки и отдачи, пинг и джиттер. Просматривайте сведения о соединении и последние результаты.\n\nВерсия 1.0",
                "Enjoy a cleaner experience" to "Пользуйтесь приложением без лишнего",
                "Choose an ad-free plan" to "Выберите вариант без рекламы", "1 MONTH" to "1 МЕСЯЦ",
                "1 YEAR" to "1 ГОД", "Ad-free for one month" to "Без рекламы в течение месяца",
                "Best value • Ad-free for one year" to "Выгодно • Без рекламы в течение года",
                "Google Play purchase" to "Покупка в Google Play",
                "purchase_message" to "План «{plan}» пока является только макетом. Для приёма платежей необходимо настроить Google Play Billing и соответствующий продукт. Сейчас в приложении нет рекламы, покупка не совершена.",
                "Full Test History" to "Полная история тестов", "Your completed tests will appear here." to "Здесь будут отображаться завершённые тесты.",
                "Clear history" to "Очистить историю", "History cleared" to "История очищена",
                "Network details permission" to "Разрешение на сведения о сети",
                "permission_message" to "Разрешите необязательные разрешения, чтобы показывать имя Wi-Fi и сведения о мобильном операторе и сети. Проверка скорости работает и без них.",
                "Continue" to "Продолжить", "Not now" to "Не сейчас", "Checking latency" to "Проверка задержки",
                "Measuring download" to "Измерение скорости загрузки", "Measuring upload" to "Измерение скорости отдачи",
                "Test complete" to "Проверка завершена", "Test failed. Check internet and try again." to "Не удалось выполнить тест. Проверьте интернет и попробуйте снова.",
                "Connection error" to "Ошибка соединения", "Share speed test results" to "Поделиться результатами теста",
                "Offline / no active connection" to "Нет активного соединения", "Mobile data" to "Мобильный интернет",
                "Connected network" to "Подключённая сеть",
                "Metered connection: Yes" to "Лимитное соединение: да", "Metered connection: No" to "Лимитное соединение: нет",
                "Wi-Fi name: " to "Имя Wi-Fi: ", "Operator: " to "Оператор: ", "Network type: " to "Тип сети: ",
                "Network name/operator details unavailable or permission not granted." to "Имя сети или сведения об операторе недоступны либо нет разрешения.",
                "Connection: " to "Соединение: ", "Offline" to "Нет соединения", "Wi-Fi (name unavailable)" to "Wi-Fi (имя недоступно)",
                "Mobile data (network details unavailable)" to "Мобильная сеть (сведения недоступны)",
                "Ping: " to "Пинг: ", "Jitter: " to "Джиттер: ", "Download: " to "Загрузка: ", "Upload: " to "Отдача: "
            ),
            "ka" to mapOf(
                "History" to "ისტორია", "About app" to "აპლიკაციის შესახებ", "Language" to "ენა", "OK" to "კარგი",
                "about_message" to "შეამოწმეთ ჩამოტვირთვისა და ატვირთვის სიჩქარე, პინგი და ჯიტერი. ნახეთ კავშირის დეტალები და ბოლო შედეგები.\n\nვერსია 1.0",
                "Enjoy a cleaner experience" to "ისიამოვნეთ უფრო სუფთა გამოცდილებით", "Choose an ad-free plan" to "აირჩიეთ რეკლამის გარეშე გეგმა",
                "1 MONTH" to "1 თვე", "1 YEAR" to "1 წელი", "Ad-free for one month" to "რეკლამის გარეშე ერთი თვის განმავლობაში",
                "Best value • Ad-free for one year" to "საუკეთესო არჩევანი • რეკლამის გარეშე ერთი წელი",
                "Google Play purchase" to "Google Play-ის შეძენა",
                "purchase_message" to "გეგმა „{plan}“ ამჟამად მხოლოდ დიზაინის ნიმუშია. გადახდების მისაღებად საჭიროა Google Play Billing-ისა და შესაბამისი პროდუქტის კონფიგურაცია. აპლიკაციაში ამჟამად რეკლამები არ არის და შეძენა არ განხორციელებულა.",
                "Full Test History" to "ტესტების სრული ისტორია", "Your completed tests will appear here." to "აქ გამოჩნდება დასრულებული ტესტები.",
                "Clear history" to "ისტორიის გასუფთავება", "History cleared" to "ისტორია გასუფთავდა",
                "Network details permission" to "ქსელის დეტალების ნებართვა",
                "permission_message" to "დაუშვით არასავალდებულო ნებართვები Wi-Fi-ის სახელისა და მობილური ოპერატორის/ქსელის დეტალების საჩვენებლად. სიჩქარის ტესტი მათ გარეშეც მუშაობს.",
                "Continue" to "გაგრძელება", "Not now" to "ახლა არა", "Checking latency" to "დაყოვნების შემოწმება",
                "Measuring download" to "ჩამოტვირთვის სიჩქარის გაზომვა", "Measuring upload" to "ატვირთვის სიჩქარის გაზომვა",
                "Test complete" to "ტესტი დასრულდა", "Test failed. Check internet and try again." to "ტესტი ვერ შესრულდა. შეამოწმეთ ინტერნეტი და სცადეთ თავიდან.",
                "Connection error" to "კავშირის შეცდომა", "Share speed test results" to "ტესტის შედეგების გაზიარება",
                "Offline / no active connection" to "აქტიური კავშირი არ არის", "Mobile data" to "მობილური ინტერნეტი",
                "Connected network" to "დაკავშირებული ქსელი",
                "Metered connection: Yes" to "ლიმიტირებული კავშირი: დიახ", "Metered connection: No" to "ლიმიტირებული კავშირი: არა",
                "Wi-Fi name: " to "Wi-Fi-ის სახელი: ", "Operator: " to "ოპერატორი: ", "Network type: " to "ქსელის ტიპი: ",
                "Network name/operator details unavailable or permission not granted." to "ქსელის სახელი ან ოპერატორის დეტალები მიუწვდომელია ან ნებართვა არ არის გაცემული.",
                "Connection: " to "კავშირი: ", "Offline" to "კავშირი არ არის", "Wi-Fi (name unavailable)" to "Wi-Fi (სახელი მიუწვდომელია)",
                "Mobile data (network details unavailable)" to "მობილური ქსელი (დეტალები მიუწვდომელია)",
                "Ping: " to "პინგი: ", "Jitter: " to "ჯიტერი: ", "Download: " to "ჩამოტვირთვა: ", "Upload: " to "ატვირთვა: "
            ),
            "de" to mapOf(
                "History" to "Verlauf", "About app" to "Über die App", "Language" to "Sprache", "OK" to "OK",
                "about_message" to "Messen Sie Download, Upload, Ping und Jitter. Sehen Sie Verbindungsdetails und letzte Testergebnisse.\n\nVersion 1.0",
                "Enjoy a cleaner experience" to "Genießen Sie eine aufgeräumte Nutzung", "Choose an ad-free plan" to "Wählen Sie einen werbefreien Tarif",
                "1 MONTH" to "1 MONAT", "1 YEAR" to "1 JAHR", "Ad-free for one month" to "Einen Monat werbefrei",
                "Best value • Ad-free for one year" to "Bestes Angebot • Ein Jahr werbefrei", "Google Play purchase" to "Google-Play-Kauf",
                "purchase_message" to "Der Tarif „{plan}“ ist derzeit nur eine Designvorschau. Für Zahlungen müssen Google Play Billing und das passende Produkt eingerichtet werden. Diese App enthält derzeit keine Werbung; es wurde nichts gekauft.",
                "Full Test History" to "Vollständiger Testverlauf", "Your completed tests will appear here." to "Hier erscheinen abgeschlossene Tests.",
                "Clear history" to "Verlauf löschen", "History cleared" to "Verlauf gelöscht", "Network details permission" to "Berechtigung für Netzwerkdetails",
                "permission_message" to "Erlauben Sie optionale Berechtigungen, um WLAN-Name und Mobilfunkanbieter/Netzwerkdetails anzuzeigen. Der Geschwindigkeitstest funktioniert auch ohne diese Berechtigungen.",
                "Continue" to "Weiter", "Not now" to "Jetzt nicht", "Checking latency" to "Latenz wird geprüft",
                "Measuring download" to "Downloadgeschwindigkeit wird gemessen", "Measuring upload" to "Uploadgeschwindigkeit wird gemessen",
                "Test complete" to "Test abgeschlossen", "Test failed. Check internet and try again." to "Test fehlgeschlagen. Prüfen Sie die Internetverbindung und versuchen Sie es erneut.",
                "Connection error" to "Verbindungsfehler", "Share speed test results" to "Testergebnisse teilen",
                "Offline / no active connection" to "Offline / keine aktive Verbindung", "Mobile data" to "Mobile Daten",
                "Connected network" to "Verbundenes Netzwerk",
                "Metered connection: Yes" to "Getaktete Verbindung: Ja", "Metered connection: No" to "Getaktete Verbindung: Nein",
                "Wi-Fi name: " to "WLAN-Name: ", "Operator: " to "Anbieter: ", "Network type: " to "Netzwerktyp: ",
                "Network name/operator details unavailable or permission not granted." to "Netzwerkname oder Anbieterdetails nicht verfügbar oder Berechtigung fehlt.",
                "Connection: " to "Verbindung: ", "Offline" to "Offline", "Wi-Fi (name unavailable)" to "WLAN (Name nicht verfügbar)",
                "Mobile data (network details unavailable)" to "Mobile Daten (Netzwerkdetails nicht verfügbar)",
                "Ping: " to "Ping: ", "Jitter: " to "Jitter: ", "Download: " to "Download: ", "Upload: " to "Upload: "
            ),
            "es" to mapOf(
                "History" to "Historial", "About app" to "Acerca de la app", "Language" to "Idioma", "OK" to "Aceptar",
                "about_message" to "Mide la descarga, la subida, el ping y el jitter. Consulta los detalles de conexión y los resultados recientes.\n\nVersión 1.0",
                "Enjoy a cleaner experience" to "Disfruta de una experiencia más limpia", "Choose an ad-free plan" to "Elige un plan sin anuncios",
                "1 MONTH" to "1 MES", "1 YEAR" to "1 AÑO", "Ad-free for one month" to "Sin anuncios durante un mes",
                "Best value • Ad-free for one year" to "Mejor opción • Sin anuncios durante un año", "Google Play purchase" to "Compra de Google Play",
                "purchase_message" to "El plan «{plan}» es solo una vista previa. Para aceptar pagos, hay que configurar Google Play Billing y el producto correspondiente. Esta app no tiene anuncios actualmente y no se ha realizado ninguna compra.",
                "Full Test History" to "Historial completo de pruebas", "Your completed tests will appear here." to "Aquí aparecerán las pruebas completadas.",
                "Clear history" to "Borrar historial", "History cleared" to "Historial borrado", "Network details permission" to "Permiso para detalles de red",
                "permission_message" to "Permite permisos opcionales para mostrar el nombre de Wi-Fi y los detalles del operador/red móvil. La prueba de velocidad funciona aunque los rechaces.",
                "Continue" to "Continuar", "Not now" to "Ahora no", "Checking latency" to "Comprobando latencia",
                "Measuring download" to "Midiendo la descarga", "Measuring upload" to "Midiendo la subida",
                "Test complete" to "Prueba completada", "Test failed. Check internet and try again." to "La prueba ha fallado. Comprueba internet e inténtalo de nuevo.",
                "Connection error" to "Error de conexión", "Share speed test results" to "Compartir resultados",
                "Offline / no active connection" to "Sin conexión activa", "Mobile data" to "Datos móviles",
                "Connected network" to "Red conectada",
                "Metered connection: Yes" to "Conexión de uso medido: Sí", "Metered connection: No" to "Conexión de uso medido: No",
                "Wi-Fi name: " to "Nombre de Wi-Fi: ", "Operator: " to "Operador: ", "Network type: " to "Tipo de red: ",
                "Network name/operator details unavailable or permission not granted." to "Nombre de red o datos del operador no disponibles o permiso no concedido.",
                "Connection: " to "Conexión: ", "Offline" to "Sin conexión", "Wi-Fi (name unavailable)" to "Wi-Fi (nombre no disponible)",
                "Mobile data (network details unavailable)" to "Datos móviles (detalles no disponibles)",
                "Ping: " to "Ping: ", "Jitter: " to "Jitter: ", "Download: " to "Descarga: ", "Upload: " to "Subida: "
            ),
            "fr" to mapOf(
                "History" to "Historique", "About app" to "À propos", "Language" to "Langue", "OK" to "OK",
                "about_message" to "Mesurez le téléchargement, l’envoi, le ping et la gigue. Consultez les détails de connexion et les résultats récents.\n\nVersion 1.0",
                "Enjoy a cleaner experience" to "Profitez d’une expérience plus épurée", "Choose an ad-free plan" to "Choisissez une formule sans publicité",
                "1 MONTH" to "1 MOIS", "1 YEAR" to "1 AN", "Ad-free for one month" to "Sans publicité pendant un mois",
                "Best value • Ad-free for one year" to "Meilleure offre • Sans publicité pendant un an", "Google Play purchase" to "Achat Google Play",
                "purchase_message" to "La formule « {plan} » est une maquette uniquement. Google Play Billing et le produit correspondant doivent être configurés pour accepter les paiements. Cette application n’affiche actuellement aucune publicité et aucun achat n’a été effectué.",
                "Full Test History" to "Historique complet des tests", "Your completed tests will appear here." to "Vos tests terminés apparaîtront ici.",
                "Clear history" to "Effacer l’historique", "History cleared" to "Historique effacé", "Network details permission" to "Autorisation des détails réseau",
                "permission_message" to "Autorisez les permissions facultatives pour afficher le nom du Wi-Fi et les détails de l’opérateur/réseau mobile. Le test fonctionne même sans ces autorisations.",
                "Continue" to "Continuer", "Not now" to "Pas maintenant", "Checking latency" to "Vérification de la latence",
                "Measuring download" to "Mesure du téléchargement", "Measuring upload" to "Mesure de l’envoi",
                "Test complete" to "Test terminé", "Test failed. Check internet and try again." to "Échec du test. Vérifiez Internet et réessayez.",
                "Connection error" to "Erreur de connexion", "Share speed test results" to "Partager les résultats",
                "Offline / no active connection" to "Hors ligne / aucune connexion active", "Mobile data" to "Données mobiles",
                "Connected network" to "Réseau connecté",
                "Metered connection: Yes" to "Connexion limitée : oui", "Metered connection: No" to "Connexion limitée : non",
                "Wi-Fi name: " to "Nom du Wi-Fi : ", "Operator: " to "Opérateur : ", "Network type: " to "Type de réseau : ",
                "Network name/operator details unavailable or permission not granted." to "Nom du réseau ou détails opérateur indisponibles, ou autorisation refusée.",
                "Connection: " to "Connexion : ", "Offline" to "Hors ligne", "Wi-Fi (name unavailable)" to "Wi-Fi (nom indisponible)",
                "Mobile data (network details unavailable)" to "Données mobiles (détails indisponibles)",
                "Ping: " to "Ping : ", "Jitter: " to "Gigue : ", "Download: " to "Téléchargement : ", "Upload: " to "Envoi : "
            ),
            "it" to mapOf(
                "History" to "Cronologia", "About app" to "Informazioni", "Language" to "Lingua", "OK" to "OK",
                "about_message" to "Misura download, upload, ping e jitter. Consulta i dettagli della connessione e i risultati recenti.\n\nVersione 1.0",
                "Enjoy a cleaner experience" to "Goditi un’esperienza più pulita", "Choose an ad-free plan" to "Scegli un piano senza pubblicità",
                "1 MONTH" to "1 MESE", "1 YEAR" to "1 ANNO", "Ad-free for one month" to "Senza pubblicità per un mese",
                "Best value • Ad-free for one year" to "Più conveniente • Senza pubblicità per un anno", "Google Play purchase" to "Acquisto Google Play",
                "purchase_message" to "Il piano «{plan}» è solo un’anteprima grafica. Per accettare pagamenti bisogna configurare Google Play Billing e il prodotto corrispondente. L’app al momento non contiene pubblicità e non è stato effettuato alcun acquisto.",
                "Full Test History" to "Cronologia completa dei test", "Your completed tests will appear here." to "Qui appariranno i test completati.",
                "Clear history" to "Cancella cronologia", "History cleared" to "Cronologia cancellata", "Network details permission" to "Autorizzazione dettagli rete",
                "permission_message" to "Consenti le autorizzazioni facoltative per mostrare il nome Wi-Fi e i dettagli dell’operatore/rete mobile. Il test funziona anche senza.",
                "Continue" to "Continua", "Not now" to "Non ora", "Checking latency" to "Controllo latenza",
                "Measuring download" to "Misurazione download", "Measuring upload" to "Misurazione upload",
                "Test complete" to "Test completato", "Test failed. Check internet and try again." to "Test non riuscito. Controlla Internet e riprova.",
                "Connection error" to "Errore di connessione", "Share speed test results" to "Condividi i risultati",
                "Offline / no active connection" to "Offline / nessuna connessione attiva", "Mobile data" to "Dati mobili",
                "Connected network" to "Rete connessa",
                "Metered connection: Yes" to "Connessione a consumo: sì", "Metered connection: No" to "Connessione a consumo: no",
                "Wi-Fi name: " to "Nome Wi-Fi: ", "Operator: " to "Operatore: ", "Network type: " to "Tipo di rete: ",
                "Network name/operator details unavailable or permission not granted." to "Nome rete o dettagli operatore non disponibili oppure autorizzazione non concessa.",
                "Connection: " to "Connessione: ", "Offline" to "Offline", "Wi-Fi (name unavailable)" to "Wi-Fi (nome non disponibile)",
                "Mobile data (network details unavailable)" to "Dati mobili (dettagli rete non disponibili)",
                "Ping: " to "Ping: ", "Jitter: " to "Jitter: ", "Download: " to "Download: ", "Upload: " to "Upload: "
            ),
            "tr" to mapOf(
                "History" to "Geçmiş", "About app" to "Uygulama hakkında", "Language" to "Dil", "OK" to "Tamam",
                "about_message" to "İndirme, yükleme, ping ve jitter değerlerini ölçün. Bağlantı ayrıntılarını ve son test sonuçlarını görüntüleyin.\n\nSürüm 1.0",
                "Enjoy a cleaner experience" to "Daha sade bir deneyimin keyfini çıkarın", "Choose an ad-free plan" to "Reklamsız bir plan seçin",
                "1 MONTH" to "1 AY", "1 YEAR" to "1 YIL", "Ad-free for one month" to "Bir ay boyunca reklamsız",
                "Best value • Ad-free for one year" to "En avantajlı • Bir yıl reklamsız", "Google Play purchase" to "Google Play satın alımı",
                "purchase_message" to "“{plan}” planı şimdilik yalnızca tasarım önizlemesidir. Ödeme almak için Google Play Billing ve ilgili ürün yapılandırılmalıdır. Uygulamada şu anda reklam yoktur ve satın alma yapılmamıştır.",
                "Full Test History" to "Tüm test geçmişi", "Your completed tests will appear here." to "Tamamlanan testler burada görünür.",
                "Clear history" to "Geçmişi temizle", "History cleared" to "Geçmiş temizlendi", "Network details permission" to "Ağ ayrıntıları izni",
                "permission_message" to "Wi-Fi adını ve mobil operatör/ağ ayrıntılarını göstermek için isteğe bağlı izinleri verin. Hız testi bu izinler olmadan da çalışır.",
                "Continue" to "Devam", "Not now" to "Şimdi değil", "Checking latency" to "Gecikme ölçülüyor",
                "Measuring download" to "İndirme hızı ölçülüyor", "Measuring upload" to "Yükleme hızı ölçülüyor",
                "Test complete" to "Test tamamlandı", "Test failed. Check internet and try again." to "Test başarısız. İnterneti kontrol edip tekrar deneyin.",
                "Connection error" to "Bağlantı hatası", "Share speed test results" to "Test sonuçlarını paylaş",
                "Offline / no active connection" to "Çevrimdışı / etkin bağlantı yok", "Mobile data" to "Mobil veri",
                "Connected network" to "Bağlı ağ",
                "Metered connection: Yes" to "Kotalı bağlantı: Evet", "Metered connection: No" to "Kotalı bağlantı: Hayır",
                "Wi-Fi name: " to "Wi-Fi adı: ", "Operator: " to "Operatör: ", "Network type: " to "Ağ türü: ",
                "Network name/operator details unavailable or permission not granted." to "Ağ adı veya operatör bilgileri kullanılamıyor ya da izin verilmedi.",
                "Connection: " to "Bağlantı: ", "Offline" to "Çevrimdışı", "Wi-Fi (name unavailable)" to "Wi-Fi (ad kullanılamıyor)",
                "Mobile data (network details unavailable)" to "Mobil veri (ağ ayrıntıları yok)",
                "Ping: " to "Ping: ", "Jitter: " to "Jitter: ", "Download: " to "İndirme: ", "Upload: " to "Yükleme: "
            ),
            "uk" to mapOf(
                "History" to "Історія", "About app" to "Про застосунок", "Language" to "Мова", "OK" to "Гаразд",
                "about_message" to "Вимірюйте швидкість завантаження й передавання, пінг і джиттер. Переглядайте відомості про з’єднання та останні результати.\n\nВерсія 1.0",
                "Enjoy a cleaner experience" to "Користуйтеся застосунком без зайвого", "Choose an ad-free plan" to "Оберіть тариф без реклами",
                "1 MONTH" to "1 МІСЯЦЬ", "1 YEAR" to "1 РІК", "Ad-free for one month" to "Без реклами протягом місяця",
                "Best value • Ad-free for one year" to "Вигідно • Без реклами протягом року", "Google Play purchase" to "Покупка Google Play",
                "purchase_message" to "Тариф «{plan}» наразі є лише макетом. Для приймання платежів потрібно налаштувати Google Play Billing і відповідний продукт. Зараз у застосунку немає реклами, покупку не здійснено.",
                "Full Test History" to "Повна історія тестів", "Your completed tests will appear here." to "Тут відображатимуться завершені тести.",
                "Clear history" to "Очистити історію", "History cleared" to "Історію очищено", "Network details permission" to "Дозвіл на відомості про мережу",
                "permission_message" to "Дозвольте необов’язкові дозволи, щоб показувати назву Wi-Fi та відомості про мобільного оператора/мережу. Тест швидкості працює і без них.",
                "Continue" to "Продовжити", "Not now" to "Не зараз", "Checking latency" to "Перевірка затримки",
                "Measuring download" to "Вимірювання завантаження", "Measuring upload" to "Вимірювання передавання",
                "Test complete" to "Тест завершено", "Test failed. Check internet and try again." to "Не вдалося виконати тест. Перевірте інтернет і спробуйте ще раз.",
                "Connection error" to "Помилка з’єднання", "Share speed test results" to "Поділитися результатами",
                "Offline / no active connection" to "Немає активного з’єднання", "Mobile data" to "Мобільні дані",
                "Connected network" to "Підключена мережа",
                "Metered connection: Yes" to "Тарифне з’єднання: так", "Metered connection: No" to "Тарифне з’єднання: ні",
                "Wi-Fi name: " to "Назва Wi-Fi: ", "Operator: " to "Оператор: ", "Network type: " to "Тип мережі: ",
                "Network name/operator details unavailable or permission not granted." to "Назва мережі або відомості оператора недоступні чи дозвіл не надано.",
                "Connection: " to "З’єднання: ", "Offline" to "Офлайн", "Wi-Fi (name unavailable)" to "Wi-Fi (назва недоступна)",
                "Mobile data (network details unavailable)" to "Мобільні дані (відомості недоступні)",
                "Ping: " to "Пінг: ", "Jitter: " to "Джиттер: ", "Download: " to "Завантаження: ", "Upload: " to "Передавання: "
            )
        )

        val commonFallback = mapOf(
            "ru" to mapOf("Traffic" to "Трафик"),
            "ka" to mapOf("Traffic" to "ტრაფიკი"),
            "de" to mapOf("Traffic" to "Datenverbrauch"),
            "es" to mapOf("Traffic" to "Tráfico", "Close" to "Cerrar", "Refresh" to "Actualizar", "Wi-Fi" to "Wi-Fi", "Mobile data" to "Datos móviles", "Ethernet" to "Ethernet", "VPN" to "VPN", "Connected network" to "Red conectada", "Metered connection: Yes" to "Conexión de uso medido: Sí", "Metered connection: No" to "Conexión de uso medido: No", "Wi-Fi name: " to "Nombre de Wi-Fi: ", "Operator: " to "Operador: ", "Network type: " to "Tipo de red: ", "Connection: " to "Conexión: ", "Offline" to "Sin conexión", "Wi-Fi (name unavailable)" to "Wi-Fi (nombre no disponible)", "Mobile data (network details unavailable)" to "Datos móviles (detalles no disponibles)", "No ads • No account required" to "Sin anuncios • Sin cuenta", "Ping: " to "Ping: ", "Jitter: " to "Jitter: ", "Download: " to "Descarga: ", "Upload: " to "Subida: "),
            "fr" to mapOf("Traffic" to "Trafic", "Close" to "Fermer", "Refresh" to "Actualiser", "Wi-Fi" to "Wi-Fi", "Mobile data" to "Données mobiles", "Ethernet" to "Ethernet", "VPN" to "VPN", "Connected network" to "Réseau connecté", "Metered connection: Yes" to "Connexion limitée : oui", "Metered connection: No" to "Connexion limitée : non", "Wi-Fi name: " to "Nom du Wi-Fi : ", "Operator: " to "Opérateur : ", "Network type: " to "Type de réseau : ", "Connection: " to "Connexion : ", "Offline" to "Hors ligne", "Wi-Fi (name unavailable)" to "Wi-Fi (nom indisponible)", "Mobile data (network details unavailable)" to "Données mobiles (détails indisponibles)", "No ads • No account required" to "Sans publicité • Sans compte", "Ping: " to "Ping : ", "Jitter: " to "Gigue : ", "Download: " to "Téléchargement : ", "Upload: " to "Envoi : "),
            "it" to mapOf("Traffic" to "Traffico", "Close" to "Chiudi", "Refresh" to "Aggiorna", "Wi-Fi" to "Wi-Fi", "Mobile data" to "Dati mobili", "Ethernet" to "Ethernet", "VPN" to "VPN", "Connected network" to "Rete connessa", "Metered connection: Yes" to "Connessione a consumo: sì", "Metered connection: No" to "Connessione a consumo: no", "Wi-Fi name: " to "Nome Wi-Fi: ", "Operator: " to "Operatore: ", "Network type: " to "Tipo di rete: ", "Connection: " to "Connessione: ", "Offline" to "Offline", "Wi-Fi (name unavailable)" to "Wi-Fi (nome non disponibile)", "Mobile data (network details unavailable)" to "Dati mobili (dettagli non disponibili)", "No ads • No account required" to "Senza pubblicità • Senza account", "Ping: " to "Ping: ", "Jitter: " to "Jitter: ", "Download: " to "Download: ", "Upload: " to "Upload: "),
            "tr" to mapOf("Traffic" to "Trafik", "Close" to "Kapat", "Refresh" to "Yenile", "Wi-Fi" to "Wi-Fi", "Mobile data" to "Mobil veri", "Ethernet" to "Ethernet", "VPN" to "VPN", "Connected network" to "Bağlı ağ", "Metered connection: Yes" to "Kotalı bağlantı: Evet", "Metered connection: No" to "Kotalı bağlantı: Hayır", "Wi-Fi name: " to "Wi-Fi adı: ", "Operator: " to "Operatör: ", "Network type: " to "Ağ türü: ", "Connection: " to "Bağlantı: ", "Offline" to "Çevrimdışı", "Wi-Fi (name unavailable)" to "Wi-Fi (ad kullanılamıyor)", "Mobile data (network details unavailable)" to "Mobil veri (ağ ayrıntıları yok)", "No ads • No account required" to "Reklamsız • Hesapsız", "Ping: " to "Ping: ", "Jitter: " to "Jitter: ", "Download: " to "İndirme: ", "Upload: " to "Yükleme: "),
            "uk" to mapOf("Traffic" to "Трафік", "Close" to "Закрити", "Refresh" to "Оновити", "Wi-Fi" to "Wi-Fi", "Mobile data" to "Мобільні дані", "Ethernet" to "Ethernet", "VPN" to "VPN", "Connected network" to "Підключена мережа", "Metered connection: Yes" to "Тарифне з’єднання: так", "Metered connection: No" to "Тарифне з’єднання: ні", "Wi-Fi name: " to "Назва Wi-Fi: ", "Operator: " to "Оператор: ", "Network type: " to "Тип мережі: ", "Connection: " to "З’єднання: ", "Offline" to "Офлайн", "Wi-Fi (name unavailable)" to "Wi-Fi (назва недоступна)", "Mobile data (network details unavailable)" to "Мобільні дані (відомості недоступні)", "No ads • No account required" to "Без реклами • Без облікового запису", "Ping: " to "Пінг: ", "Jitter: " to "Джиттер: ", "Download: " to "Завантаження: ", "Upload: " to "Передавання: ")
        )
        return dict[lang]?.get(text) ?: extra[lang]?.get(text) ?: commonFallback[lang]?.get(text) ?: text
    }

    private fun showLanguageDialog() {
        val names = arrayOf("English", "Русский", "ქართული", "Deutsch", "Español", "Français", "Italiano", "Türkçe", "Українська")
        val codes = arrayOf("en", "ru", "ka", "de", "es", "fr", "it", "tr", "uk")
        val current = prefs.getString("language", "en") ?: "en"
        AlertDialog.Builder(this).setTitle(tr("Language"))
            .setSingleChoiceItems(names, codes.indexOf(current).coerceAtLeast(0)) { dialog, which ->
                prefs.edit().putString("language", codes[which]).apply()
                dialog.dismiss()
                buildScreen()
                refreshNetworkInfo()
                refreshHistory()
            }.setNegativeButton(tr("Close"), null).show()
    }

    private fun showNoAdsDialog() {
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(12), dp(20), dp(8))
            setBackgroundColor(bg)
        }
        content.addView(TextView(this).apply {
            text = tr("Enjoy a cleaner experience"); textSize = 20f
            typeface = Typeface.DEFAULT_BOLD; setTextColor(Color.WHITE); gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(-1, -2))
        content.addView(TextView(this).apply {
            text = tr("Choose an ad-free plan"); textSize = 13f
            setTextColor(Color.rgb(165, 190, 215)); gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(5); bottomMargin = dp(14) })

        fun planCard(title: String, price: String, detail: String, recommended: Boolean) {
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
                setPadding(dp(14), dp(13), dp(14), dp(13))
                background = android.graphics.drawable.GradientDrawable().apply {
                    cornerRadius = dp(15).toFloat(); setColor(panel)
                    setStroke(dp(if (recommended) 2 else 1), if (recommended) cyan else Color.rgb(45, 70, 98))
                }
            }
            card.addView(TextView(this@MainActivity).apply {
                text = if (recommended) "★  $title" else title
                textSize = 15f; typeface = Typeface.DEFAULT_BOLD
                setTextColor(if (recommended) cyan else Color.WHITE); gravity = Gravity.CENTER
            })
            card.addView(TextView(this@MainActivity).apply {
                text = price; textSize = 27f; typeface = Typeface.DEFAULT_BOLD
                setTextColor(Color.WHITE); gravity = Gravity.CENTER
            }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(3) })
            card.addView(TextView(this@MainActivity).apply {
                text = detail; textSize = 12f; setTextColor(Color.LTGRAY); gravity = Gravity.CENTER
            })
            content.addView(card, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(10) })
            card.setOnClickListener {
                AlertDialog.Builder(this@MainActivity).setTitle(tr("Google Play purchase"))
                    .setMessage(tr("purchase_message").replace("{plan}", title))
                    .setPositiveButton(tr("OK"), null).show()
            }
        }
        planCard(tr("1 MONTH"), "€0.99", tr("Ad-free for one month"), false)
        planCard(tr("1 YEAR"), "€1.99", tr("Best value • Ad-free for one year"), true)
        AlertDialog.Builder(this).setTitle("✨  No Ads").setView(content)
            .setNegativeButton(tr("Close"), null).show()
    }

    private fun showHistoryDialog() {
        val history = prefs.getString("history", "") ?: ""
        AlertDialog.Builder(this)
            .setTitle(tr("Full Test History"))
            .setMessage(if (history.isBlank()) tr("Your completed tests will appear here.") else history.replace("\n", "\n\n"))
            .setNeutralButton(tr("Clear history")) { _, _ ->
                prefs.edit().remove("history").apply()
                refreshHistory()
                Toast.makeText(this, tr("History cleared"), Toast.LENGTH_SHORT).show()
            }
            .setPositiveButton(tr("Close"), null)
            .show()
    }

    private fun refreshNetworkInfo() {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork
        val caps = if (network != null) cm.getNetworkCapabilities(network) else null
        val type = when {
            caps == null -> tr("Offline / no active connection")
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> tr("Wi-Fi")
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> tr("Mobile data")
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> tr("Ethernet")
            caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> tr("VPN")
            else -> tr("Connected network")
        }
        val metered = if (cm.isActiveNetworkMetered) tr("Metered connection: Yes") else tr("Metered connection: No")
        val details = mutableListOf<String>()
        if (caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true &&
            checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            try {
                val wifi = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
                val ssid = wifi.connectionInfo?.ssid?.trim('"')
                if (!ssid.isNullOrBlank() && ssid != "<unknown ssid>") details.add(tr("Wi-Fi name: ") + ssid)
            } catch (_: Exception) { }
        }
        if (caps?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true &&
            checkSelfPermission(Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED) {
            try {
                val telephony = getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
                val operator = telephony.networkOperatorName
                if (operator.isNotBlank()) details.add(tr("Operator: ") + operator)
                val radio = when (telephony.dataNetworkType) {
                    TelephonyManager.NETWORK_TYPE_NR -> "5G"
                    TelephonyManager.NETWORK_TYPE_LTE -> "4G LTE"
                    TelephonyManager.NETWORK_TYPE_HSPAP, TelephonyManager.NETWORK_TYPE_HSPA,
                    TelephonyManager.NETWORK_TYPE_HSDPA, TelephonyManager.NETWORK_TYPE_HSUPA -> "3G / HSPA"
                    TelephonyManager.NETWORK_TYPE_UMTS, TelephonyManager.NETWORK_TYPE_CDMA,
                    TelephonyManager.NETWORK_TYPE_EVDO_0, TelephonyManager.NETWORK_TYPE_EVDO_A,
                    TelephonyManager.NETWORK_TYPE_EVDO_B -> "3G"
                    TelephonyManager.NETWORK_TYPE_GSM, TelephonyManager.NETWORK_TYPE_GPRS,
                    TelephonyManager.NETWORK_TYPE_EDGE -> "2G"
                    else -> "Mobile network type unavailable"
                }
                details.add(tr("Network type: ") + radio)
            } catch (_: Exception) { }
        }
        networkInfo.text = tr("Connection: ") + "${type}\n${metered}" +
            if (details.isEmpty()) "\n" + tr("Network name/operator details unavailable or permission not granted.") else "\n" + details.joinToString("\n")
        currentNetworkLabel = when {
            caps == null -> tr("Offline")
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> {
                val name = details.firstOrNull { it.startsWith(tr("Wi-Fi name: ")) }?.substringAfter(tr("Wi-Fi name: "))?.trim()
                if (!name.isNullOrBlank()) "${tr("Wi-Fi")}: $name" else tr("Wi-Fi (name unavailable)")
            }
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> {
                val operator = details.firstOrNull { it.startsWith(tr("Operator: ")) }?.substringAfter(tr("Operator: "))?.trim()
                val radio = details.firstOrNull { it.startsWith(tr("Network type: ")) }?.substringAfter(tr("Network type: "))?.trim()
                listOfNotNull(operator?.takeIf { it.isNotBlank() }, radio?.takeIf { it.isNotBlank() })
                    .joinToString(" • ").ifBlank { tr("Mobile data (network details unavailable)") }
            }
            else -> type
        }
        if (::headerNetwork.isInitialized) {
            headerNetwork.text = when {
                caps == null -> "○"
                caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> tr("Wi-Fi")
                caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "4G"
                caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "ETH"
                caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> tr("VPN")
                else -> "●"
            }
            headerNetwork.contentDescription = type
        }
    }

    override fun onResume() {
        super.onResume()
        if (::trafficUsageText.isInitialized) refreshTrafficUsage()
    }

    private fun hasUsageAccess(): Boolean {
        val appOps = getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        @Suppress("DEPRECATION")
        val mode = appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), packageName)
        return mode == AppOpsManager.MODE_ALLOWED
    }

    private fun openUsageAccessSettings() {
        if (hasUsageAccess()) {
            refreshTrafficUsage()
            Toast.makeText(this, tr("Traffic statistics refreshed"), Toast.LENGTH_SHORT).show()
            return
        }
        try {
            startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
        } catch (_: Exception) {
            Toast.makeText(this, tr("Open Android Settings and allow Usage access"), Toast.LENGTH_LONG).show()
        }
    }

    private fun refreshTrafficUsage() {
        if (!::trafficUsageText.isInitialized) return
        if (!hasUsageAccess()) {
            trafficUsageText.text = tr("Track all phone traffic separately for Wi-Fi and mobile data. Tap here to allow Usage access.")
            return
        }
        trafficUsageText.text = tr("Loading traffic statistics…")
        thread {
            val now = System.currentTimeMillis()
            val todayStart = startOfDay(now)
            val monthCalendar = java.util.Calendar.getInstance().apply { timeInMillis = now; set(java.util.Calendar.DAY_OF_MONTH, 1) }
            val monthStart = startOfDay(monthCalendar.timeInMillis)
            val today = readTrafficForRange(todayStart, now)
            val month = readTrafficForRange(monthStart, now)

            // Store daily totals privately. Values are only increased, never erased or reduced by this app.
            val dailyPrefs = getSharedPreferences("traffic_history_locked", MODE_PRIVATE)
            val editor = dailyPrefs.edit()
            val cursor = java.util.Calendar.getInstance().apply {
                timeInMillis = todayStart
                add(java.util.Calendar.DAY_OF_YEAR, -89)
            }
            while (cursor.timeInMillis <= todayStart) {
                val dayStart = cursor.timeInMillis
                val nextDay = java.util.Calendar.getInstance().apply {
                    timeInMillis = dayStart
                    add(java.util.Calendar.DAY_OF_YEAR, 1)
                }.timeInMillis
                val dateKey = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date(dayStart))
                val usage = if (dayStart == todayStart) today else readTrafficForRange(dayStart, nextDay)
                saveMaxTraffic(editor, dailyPrefs, dateKey, "wifi", usage.first)
                saveMaxTraffic(editor, dailyPrefs, dateKey, "mobile", usage.second)
                cursor.add(java.util.Calendar.DAY_OF_YEAR, 1)
            }
            editor.apply()

            runOnUiThread {
                if (!::trafficUsageText.isInitialized) return@runOnUiThread
                trafficUsageText.text = tr("TODAY") + "\n" +
                    tr("Wi-Fi") + ": " + formatBytes(today.first) + "   •   " +
                    tr("Mobile data") + ": " + formatBytes(today.second) + "\n\n" +
                    tr("THIS MONTH") + "\n" +
                    tr("Wi-Fi") + ": " + formatBytes(month.first) + "   •   " +
                    tr("Mobile data") + ": " + formatBytes(month.second) + "\n\n" +
                    tr("Tap to view traffic history")
            }
        }
    }

    private fun startOfDay(time: Long): Long {
        val c = java.util.Calendar.getInstance().apply { timeInMillis = time }
        c.set(java.util.Calendar.HOUR_OF_DAY, 0)
        c.set(java.util.Calendar.MINUTE, 0)
        c.set(java.util.Calendar.SECOND, 0)
        c.set(java.util.Calendar.MILLISECOND, 0)
        return c.timeInMillis
    }

    private fun readTrafficForRange(start: Long, end: Long): Pair<Long, Long> {
        fun totalBytes(type: Int): Long = try {
            val manager = getSystemService(Context.NETWORK_STATS_SERVICE) as NetworkStatsManager
            val bucket = manager.querySummaryForDevice(type, null, start, end)
            bucket.rxBytes + bucket.txBytes
        } catch (_: Exception) { -1L }
        return Pair(totalBytes(ConnectivityManager.TYPE_WIFI), totalBytes(ConnectivityManager.TYPE_MOBILE))
    }

    private fun saveMaxTraffic(
        editor: android.content.SharedPreferences.Editor,
        prefs: android.content.SharedPreferences,
        date: String,
        network: String,
        value: Long
    ) {
        if (value < 0L) return
        val key = "${date}_${network}"
        if (value > prefs.getLong(key, -1L)) editor.putLong(key, value)
    }

    private fun showTrafficHistoryDialog() {
        if (!hasUsageAccess()) {
            openUsageAccessSettings()
            return
        }
        val historyPrefs = getSharedPreferences("traffic_history_locked", MODE_PRIVATE)
        val dates = historyPrefs.all.keys
            .mapNotNull { key ->
                key.substringBeforeLast("_").takeIf { it.matches(Regex("\\d{4}-\\d{2}-\\d{2}")) }
            }.distinct().sortedDescending()
        val dailyLines = dates.take(30).map { date ->
            val wifi = historyPrefs.getLong("${date}_wifi", -1L)
            val mobile = historyPrefs.getLong("${date}_mobile", -1L)
            "$date\n  Wi-Fi: ${formatBytes(wifi)}   •   ${tr("Mobile data")}: ${formatBytes(mobile)}"
        }
        val months = dates.map { it.substring(0, 7) }.distinct().sortedDescending().take(12)
        val monthlyLines = months.map { month ->
            var wifiTotal = 0L
            var mobileTotal = 0L
            dates.filter { it.startsWith(month) }.forEach { date ->
                val wifi = historyPrefs.getLong("${date}_wifi", -1L)
                val mobile = historyPrefs.getLong("${date}_mobile", -1L)
                if (wifi >= 0L) wifiTotal += wifi
                if (mobile >= 0L) mobileTotal += mobile
            }
            "$month\n  Wi-Fi: ${formatBytes(wifiTotal)}   •   ${tr("Mobile data")}: ${formatBytes(mobileTotal)}"
        }
        val message = buildString {
            append(tr("DAILY HISTORY")); append("\n")
            append(if (dailyLines.isEmpty()) tr("History starts after Usage access is enabled.") else dailyLines.joinToString("\n\n"))
            append("\n\n"); append(tr("MONTHLY HISTORY")); append("\n")
            append(if (monthlyLines.isEmpty()) tr("History starts after Usage access is enabled.") else monthlyLines.joinToString("\n\n"))
            append("\n\n"); append(tr("History is saved on this phone and cannot be deleted from the app."))
        }
        AlertDialog.Builder(this)
            .setTitle(tr("Traffic history"))
            .setMessage(message)
            .setPositiveButton(tr("Refresh")) { _, _ -> refreshTrafficUsage() }
            .setNegativeButton(tr("Close"), null)
            .show()
    }

    private fun formatBytes(bytes: Long): String {
        if (bytes < 0L) return tr("Unavailable")
        if (bytes < 1024L) return "$bytes B"
        val kb = bytes / 1024.0
        if (kb < 1024.0) return String.format(java.util.Locale.US, "%.1f KB", kb)
        val mb = kb / 1024.0
        if (mb < 1024.0) return String.format(java.util.Locale.US, "%.1f MB", mb)
        return String.format(java.util.Locale.US, "%.2f GB", mb / 1024.0)
    }

    private fun refreshHistory() {
        val history = prefs.getString("history", "") ?: ""
        val recent = history.split("\n").filter { it.isNotBlank() }.take(3)
        historyText.text = if (recent.isEmpty()) tr("Your completed tests will appear here.") else recent.joinToString("\n\n")
    }

    private fun addSectionTitle(parent: LinearLayout, title: String) {
        parent.addView(TextView(this).apply {
            text = "▰  " + title; textSize = 12f; typeface = Typeface.DEFAULT_BOLD; letterSpacing = 0.14f; setTextColor(cyan)
        }, LinearLayout.LayoutParams(-1, dp(28)).apply { topMargin = dp(18) })
    }

    private fun panelBackground() = android.graphics.drawable.GradientDrawable().apply {
        cornerRadius = dp(13).toFloat(); setColor(panel); setStroke(dp(1), Color.rgb(35, 64, 91))
    }
    private fun outlineBackground() = android.graphics.drawable.GradientDrawable().apply {
        cornerRadius = dp(14).toFloat()
        colors = intArrayOf(Color.rgb(17, 35, 61), Color.rgb(10, 22, 42))
        orientation = android.graphics.drawable.GradientDrawable.Orientation.LEFT_RIGHT
        setStroke(dp(1), cyan)
    }
    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).roundToInt()
    private fun matchWrap() = LinearLayout.LayoutParams(-1, -2)
}
