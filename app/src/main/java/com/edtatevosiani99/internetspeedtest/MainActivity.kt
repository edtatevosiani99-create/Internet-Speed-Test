package com.edtatevosiani99.internetspeedtest

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
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
    private val bg = Color.rgb(8, 13, 27)
    private val panel = Color.rgb(16, 25, 45)
    private val cyan = Color.rgb(42, 214, 255)
    private lateinit var status: TextView
    private lateinit var pingValue: TextView
    private lateinit var downloadValue: TextView
    private lateinit var uploadValue: TextView
    private lateinit var jitterValue: TextView
    private lateinit var networkInfo: TextView
    private lateinit var headerNetwork: TextView
    private lateinit var historyText: TextView
    private lateinit var startButton: Button
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
        requestNetworkDetailsPermissionIfNeeded()
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
            text = tr("INTERNET SPEED TEST"); textSize = 17f; setTextColor(Color.WHITE)
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

        content.addView(TextView(this).apply {
            text = "↯"; textSize = 58f; setTextColor(cyan); gravity = Gravity.CENTER
            background = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.OVAL
                setColor(panel); setStroke(dp(2), cyan)
            }
        }, LinearLayout.LayoutParams(dp(126), dp(126)).apply {
            gravity = Gravity.CENTER; topMargin = dp(18); bottomMargin = dp(8)
        })
        status = TextView(this).apply {
            text = tr("Ready to test"); textSize = 14f; setTextColor(Color.LTGRAY); gravity = Gravity.CENTER
        }
        content.addView(status, LinearLayout.LayoutParams(-1, dp(34)))

        val row1 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        pingValue = metric(row1, tr("PING"), "—", "ms")
        downloadValue = metric(row1, tr("DOWNLOAD"), "—", "Mbps")
        uploadValue = metric(row1, tr("UPLOAD"), "—", "Mbps")
        jitterValue = metric(row1, tr("JITTER"), "—", "ms")
        content.addView(row1, LinearLayout.LayoutParams(-1, dp(104)).apply { topMargin = dp(10) })

        startButton = Button(this).apply {
            text = tr("START TEST"); textSize = 16f; typeface = Typeface.DEFAULT_BOLD; setTextColor(bg)
            background = android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = dp(16).toFloat(); setColor(cyan)
            }
            setOnClickListener { runTest() }
        }
        content.addView(startButton, LinearLayout.LayoutParams(-1, dp(56)).apply { topMargin = dp(20) })
        shareButton = Button(this).apply {
            text = tr("SHARE RESULTS"); textSize = 14f; setTextColor(cyan); background = outlineBackground()
            isEnabled = lastResult.isNotBlank(); alpha = if (isEnabled) 1f else 0.55f
            setOnClickListener {
                if (lastResult.isNotBlank()) {
                    val send = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"; putExtra(Intent.EXTRA_TEXT, lastResult)
                    }
                    startActivity(Intent.createChooser(send, "Share speed test results"))
                }
            }
        }
        content.addView(shareButton, LinearLayout.LayoutParams(-1, dp(50)).apply { topMargin = dp(8) })

        networkInfo = TextView(this).apply {
            textSize = 13f; setTextColor(Color.rgb(205, 220, 238))
            setPadding(dp(14), dp(12), dp(14), dp(12)); background = panelBackground()
        }
        addSectionTitle(content, "RECENT TESTS")
        historyText = TextView(this).apply {
            textSize = 12f; setTextColor(Color.rgb(205, 220, 238))
            setPadding(dp(14), dp(12), dp(14), dp(12)); background = panelBackground()
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
                cornerRadius = dp(13).toFloat(); setColor(panel); setStroke(dp(1), Color.rgb(35, 64, 91))
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
        startButton.isEnabled = false; startButton.text = tr("TESTING...")
        shareButton.isEnabled = false
        status.text = "Checking latency (1/5)..."
        pingValue.text = "…"; downloadValue.text = "…"; uploadValue.text = "…"
        jitterValue.text = "…"
        thread {
            try {
                val samples = mutableListOf<Double>()
                for (i in 1..5) {
                    try {
                        val started = System.nanoTime()
                        val c = URL("https://speed.cloudflare.com/__down?bytes=1&check=$i").openConnection() as HttpURLConnection
                        c.connectTimeout = 7000; c.readTimeout = 7000; c.useCaches = false
                        c.setRequestProperty("Cache-Control", "no-cache")
                        c.inputStream.use { input -> while (input.read() != -1) { } }
                        c.disconnect()
                        samples.add((System.nanoTime() - started) / 1_000_000.0)
                    } catch (_: Exception) { /* Ignore failed latency sample. */ }
                    val n = i
                    runOnUiThread { status.text = "Checking latency ($n/5)..." }
                }
                if (samples.isEmpty()) throw IllegalStateException("Could not reach the test server")
                val ping = samples.average()
                val jitter = if (samples.size > 1) samples.zipWithNext { a, b -> abs(b - a) }.average() else 0.0

                runOnUiThread { status.text = "Measuring download..." }
                val startedDown = System.nanoTime()
                val down = URL("https://speed.cloudflare.com/__down?bytes=10000000").openConnection() as HttpURLConnection
                down.connectTimeout = 15000; down.readTimeout = 25000; down.useCaches = false
                down.setRequestProperty("Cache-Control", "no-cache")
                var received = 0L
                down.inputStream.use { input ->
                    val buffer = ByteArray(16384)
                    while (true) { val n = input.read(buffer); if (n < 0) break; received += n }
                }
                down.disconnect()
                val downSeconds = (System.nanoTime() - startedDown) / 1_000_000_000.0
                val downMbps = if (downSeconds > 0) received * 8.0 / downSeconds / 1_000_000.0 else 0.0

                runOnUiThread { status.text = "Measuring upload..." }
                val payload = ByteArray(1_000_000)
                val startedUp = System.nanoTime()
                val up = URL("https://speed.cloudflare.com/__up").openConnection() as HttpURLConnection
                up.requestMethod = "POST"; up.doOutput = true; up.connectTimeout = 15000; up.readTimeout = 20000
                up.setRequestProperty("Content-Type", "application/octet-stream")
                up.setFixedLengthStreamingMode(payload.size)
                up.outputStream.use { it.write(payload) }
                val response = up.responseCode
                try {
                    val stream = if (response in 200..299) up.inputStream else up.errorStream
                    stream?.use { it.readBytes() }
                } catch (_: Exception) { }
                val upSeconds = (System.nanoTime() - startedUp) / 1_000_000_000.0
                up.disconnect()
                val upMbps = if (upSeconds > 0) payload.size * 8.0 / upSeconds / 1_000_000.0 else 0.0
                val downText = String.format(java.util.Locale.US, "%.1f", downMbps)
                val upText = if (response in 200..299) String.format(java.util.Locale.US, "%.1f", upMbps) else "N/A"
                val result = "Internet Speed Test\nPing: ${ping.roundToInt()} ms\nJitter: ${jitter.roundToInt()} ms\nDownload: ${downText} Mbps\nUpload: ${upText} Mbps"
                val date = java.text.SimpleDateFormat("dd MMM, HH:mm", java.util.Locale.getDefault()).format(java.util.Date())
                runOnUiThread {
                    pingValue.text = ping.roundToInt().toString()
                    jitterValue.text = jitter.roundToInt().toString()
                    downloadValue.text = downText; uploadValue.text = upText
                    status.text = "Test complete"
                    lastResult = result
                    prefs.edit().putString("last_result", result).apply()
                    refreshNetworkInfo()
                    val historyLine = "$date  •  ↓$downText Mbps  ↑$upText Mbps  •  Ping ${ping.roundToInt()} ms  •  Jitter ${jitter.roundToInt()} ms  •  $currentNetworkLabel"
                    val history = prefs.getString("history", "") ?: ""
                    val updated = (listOf(historyLine) + history.split("\n").filter { it.isNotBlank() }).joinToString("\n")
                    prefs.edit().putString("history", updated).apply()
                    refreshHistory()
                    shareButton.isEnabled = true; shareButton.alpha = 1f
                    startButton.isEnabled = true; startButton.text = tr("TEST AGAIN")
                }
            } catch (e: Exception) {
                runOnUiThread {
                    status.text = "Test failed. Check internet and try again."
                    startButton.isEnabled = true; startButton.text = tr("TRY AGAIN")
                    shareButton.isEnabled = lastResult.isNotBlank()
                    Toast.makeText(this, e.localizedMessage ?: "Connection error", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun showAppMenu(anchor: View) {
        val menu = PopupMenu(this, anchor)
        menu.menu.add(0, 1, 0, "🕘  History")
        menu.menu.add(0, 2, 1, "ℹ️  About app")
        menu.menu.add(0, 3, 2, "✨  No Ads")
        menu.menu.add(0, 4, 3, "🌐  Language")
        menu.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                1 -> showHistoryDialog()
                2 -> AlertDialog.Builder(this)
                    .setTitle("Internet Speed Test")
                    .setMessage("Test download, upload, ping and jitter. View connection details and recent test history.\n\nVersion 1.0")
                    .setPositiveButton("OK", null)
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
            "ru" to mapOf("INTERNET SPEED TEST" to "ТЕСТ СКОРОСТИ ИНТЕРНЕТА", "MEASURE YOUR CONNECTION" to "ИЗМЕРЬТЕ СВОЁ СОЕДИНЕНИЕ", "Ready to test" to "Готово к проверке", "PING" to "ПИНГ", "DOWNLOAD" to "ЗАГРУЗКА", "UPLOAD" to "ОТДАЧА", "JITTER" to "ДЖИТТЕР", "START TEST" to "НАЧАТЬ ТЕСТ", "TEST AGAIN" to "ПОВТОРИТЬ ТЕСТ", "TRY AGAIN" to "ПОВТОРИТЬ", "TESTING..." to "ТЕСТИРОВАНИЕ...", "SHARE RESULTS" to "ПОДЕЛИТЬСЯ РЕЗУЛЬТАТАМИ", "RECENT TESTS" to "ПОСЛЕДНИЕ ТЕСТЫ", "No ads • No account required" to "Без рекламы • Без аккаунта"),
            "ka" to mapOf("INTERNET SPEED TEST" to "ინტერნეტის სიჩქარის ტესტი", "MEASURE YOUR CONNECTION" to "შეამოწმეთ კავშირი", "Ready to test" to "ტესტისთვის მზადაა", "PING" to "პინგი", "DOWNLOAD" to "ჩამოტვირთვა", "UPLOAD" to "ატვირთვა", "JITTER" to "ჯიტერი", "START TEST" to "ტესტის დაწყება", "TEST AGAIN" to "ხელახლა ტესტი", "TRY AGAIN" to "ხელახლა სცადეთ", "TESTING..." to "მოწმდება...", "SHARE RESULTS" to "შედეგების გაზიარება", "RECENT TESTS" to "ბოლო ტესტები", "No ads • No account required" to "რეკლამის გარეშე • ანგარიშის გარეშე"),
            "de" to mapOf("INTERNET SPEED TEST" to "INTERNET-GESCHWINDIGKEIT", "MEASURE YOUR CONNECTION" to "VERBINDUNG MESSEN", "Ready to test" to "Bereit zum Testen", "DOWNLOAD" to "DOWNLOAD", "UPLOAD" to "UPLOAD", "START TEST" to "TEST STARTEN", "TEST AGAIN" to "ERNEUT TESTEN", "TRY AGAIN" to "NOCHMALS VERSUCHEN", "TESTING..." to "TEST LÄUFT...", "SHARE RESULTS" to "ERGEBNISSE TEILEN", "RECENT TESTS" to "LETZTE TESTS", "No ads • No account required" to "Keine Werbung • Kein Konto nötig"),
            "es" to mapOf("INTERNET SPEED TEST" to "VELOCIDAD DE INTERNET", "MEASURE YOUR CONNECTION" to "MIDE TU CONEXIÓN", "Ready to test" to "Listo para probar", "DOWNLOAD" to "DESCARGA", "UPLOAD" to "SUBIDA", "START TEST" to "INICIAR PRUEBA", "TEST AGAIN" to "REPETIR PRUEBA", "SHARE RESULTS" to "COMPARTIR RESULTADOS", "RECENT TESTS" to "PRUEBAS RECIENTES"),
            "fr" to mapOf("INTERNET SPEED TEST" to "TEST DE VITESSE INTERNET", "MEASURE YOUR CONNECTION" to "MESUREZ VOTRE CONNEXION", "Ready to test" to "Prêt à tester", "DOWNLOAD" to "TÉLÉCHARGEMENT", "UPLOAD" to "ENVOI", "START TEST" to "LANCER LE TEST", "TEST AGAIN" to "REFAIRE LE TEST", "SHARE RESULTS" to "PARTAGER LES RÉSULTATS", "RECENT TESTS" to "TESTS RÉCENTS"),
            "it" to mapOf("INTERNET SPEED TEST" to "TEST VELOCITÀ INTERNET", "MEASURE YOUR CONNECTION" to "MISURA LA CONNESSIONE", "Ready to test" to "Pronto per il test", "DOWNLOAD" to "DOWNLOAD", "UPLOAD" to "UPLOAD", "START TEST" to "AVVIA TEST", "TEST AGAIN" to "RIPETI TEST", "SHARE RESULTS" to "CONDIVIDI RISULTATI", "RECENT TESTS" to "TEST RECENTI"),
            "tr" to mapOf("INTERNET SPEED TEST" to "İNTERNET HIZ TESTİ", "MEASURE YOUR CONNECTION" to "BAĞLANTI HIZINI ÖLÇ", "Ready to test" to "Teste hazır", "DOWNLOAD" to "İNDİRME", "UPLOAD" to "YÜKLEME", "START TEST" to "TESTİ BAŞLAT", "TEST AGAIN" to "TEKRAR TEST ET", "SHARE RESULTS" to "SONUÇLARI PAYLAŞ", "RECENT TESTS" to "SON TESTLER"),
            "uk" to mapOf("INTERNET SPEED TEST" to "ТЕСТ ШВИДКОСТІ ІНТЕРНЕТУ", "MEASURE YOUR CONNECTION" to "ВИМІРЮЙТЕ З’ЄДНАННЯ", "Ready to test" to "Готово до тесту", "DOWNLOAD" to "ЗАВАНТАЖЕННЯ", "UPLOAD" to "ВІДДАВАННЯ", "START TEST" to "ПОЧАТИ ТЕСТ", "TEST AGAIN" to "ПОВТОРИТИ ТЕСТ", "SHARE RESULTS" to "ПОДІЛИТИСЯ РЕЗУЛЬТАТАМИ", "RECENT TESTS" to "ОСТАННІ ТЕСТИ")
        )
        return dict[lang]?.get(text) ?: text
    }

    private fun showLanguageDialog() {
        val names = arrayOf("English", "Русский", "ქართული", "Deutsch", "Español", "Français", "Italiano", "Türkçe", "Українська")
        val codes = arrayOf("en", "ru", "ka", "de", "es", "fr", "it", "tr", "uk")
        val current = prefs.getString("language", "en") ?: "en"
        AlertDialog.Builder(this).setTitle("Language")
            .setSingleChoiceItems(names, codes.indexOf(current).coerceAtLeast(0)) { dialog, which ->
                prefs.edit().putString("language", codes[which]).apply()
                dialog.dismiss()
                buildScreen()
                refreshNetworkInfo()
                refreshHistory()
            }.setNegativeButton("Close", null).show()
    }

    private fun showNoAdsDialog() {
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(12), dp(20), dp(8))
            setBackgroundColor(bg)
        }
        content.addView(TextView(this).apply {
            text = "Enjoy a cleaner experience"; textSize = 20f
            typeface = Typeface.DEFAULT_BOLD; setTextColor(Color.WHITE); gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(-1, -2))
        content.addView(TextView(this).apply {
            text = "Choose an ad-free plan"; textSize = 13f
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
                AlertDialog.Builder(this@MainActivity).setTitle("Google Play purchase")
                    .setMessage("The $title plan is a design preview only. Google Play Billing and the matching product must be configured before payments can be accepted. This app currently contains no ads, so no purchase has been made.")
                    .setPositiveButton("OK", null).show()
            }
        }
        planCard("1 MONTH", "€0.99", "Ad-free for one month", false)
        planCard("1 YEAR", "€1.99", "Best value • Ad-free for one year", true)
        AlertDialog.Builder(this).setTitle("✨  No Ads").setView(content)
            .setNegativeButton("Close", null).show()
    }

    private fun showHistoryDialog() {
        val history = prefs.getString("history", "") ?: ""
        AlertDialog.Builder(this)
            .setTitle("Full Test History")
            .setMessage(if (history.isBlank()) "Your completed tests will appear here." else history.replace("\n", "\n\n"))
            .setNeutralButton("Clear history") { _, _ ->
                prefs.edit().remove("history").apply()
                refreshHistory()
                Toast.makeText(this, "History cleared", Toast.LENGTH_SHORT).show()
            }
            .setPositiveButton("Close", null)
            .show()
    }

    private val networkPermissionRequest = 7104

    private fun requestNetworkDetailsPermissionIfNeeded() {
        val needed = mutableListOf<String>()
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            needed.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        if (checkSelfPermission(Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
            needed.add(Manifest.permission.READ_PHONE_STATE)
        }
        if (needed.isEmpty()) return
        AlertDialog.Builder(this)
            .setTitle("Network details permission")
            .setMessage("Allow optional permissions to show your Wi-Fi network name and mobile operator/network details. Internet speed testing works even if you decline.")
            .setPositiveButton("Continue") { _, _ ->
                requestPermissions(needed.toTypedArray(), networkPermissionRequest)
            }
            .setNegativeButton("Not now") { _, _ -> refreshNetworkInfo() }
            .show()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == networkPermissionRequest) refreshNetworkInfo()
    }

    private fun refreshNetworkInfo() {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork
        val caps = if (network != null) cm.getNetworkCapabilities(network) else null
        val type = when {
            caps == null -> "Offline / no active connection"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Mobile data"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> "VPN"
            else -> "Connected network"
        }
        val metered = if (cm.isActiveNetworkMetered) "Metered connection: Yes" else "Metered connection: No"
        val details = mutableListOf<String>()
        if (caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true &&
            checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            try {
                val wifi = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
                val ssid = wifi.connectionInfo?.ssid?.trim('"')
                if (!ssid.isNullOrBlank() && ssid != "<unknown ssid>") details.add("Wi-Fi name: ${ssid}")
            } catch (_: Exception) { }
        }
        if (caps?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true &&
            checkSelfPermission(Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED) {
            try {
                val telephony = getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
                val operator = telephony.networkOperatorName
                if (operator.isNotBlank()) details.add("Operator: ${operator}")
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
                details.add("Network type: ${radio}")
            } catch (_: Exception) { }
        }
        networkInfo.text = "Connection: ${type}\n${metered}" +
            if (details.isEmpty()) "\nNetwork name/operator details unavailable or permission not granted." else "\n" + details.joinToString("\n")
        currentNetworkLabel = when {
            caps == null -> "Offline"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> {
                val name = details.firstOrNull { it.startsWith("Wi-Fi name:") }?.substringAfter("Wi-Fi name:")?.trim()
                if (!name.isNullOrBlank()) "Wi-Fi: $name" else "Wi-Fi (name unavailable)"
            }
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> {
                val operator = details.firstOrNull { it.startsWith("Operator:") }?.substringAfter("Operator:")?.trim()
                val radio = details.firstOrNull { it.startsWith("Network type:") }?.substringAfter("Network type:")?.trim()
                listOfNotNull(operator?.takeIf { it.isNotBlank() }, radio?.takeIf { it.isNotBlank() })
                    .joinToString(" • ").ifBlank { "Mobile data (network details unavailable)" }
            }
            else -> type
        }
        if (::headerNetwork.isInitialized) {
            headerNetwork.text = when {
                caps == null -> "○"
                caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
                caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "4G"
                caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "ETH"
                caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> "VPN"
                else -> "●"
            }
            headerNetwork.contentDescription = type
        }
    }

    private fun refreshHistory() {
        val history = prefs.getString("history", "") ?: ""
        val recent = history.split("\n").filter { it.isNotBlank() }.take(3)
        historyText.text = if (recent.isEmpty()) "Your completed tests will appear here." else recent.joinToString("\n\n")
    }

    private fun addSectionTitle(parent: LinearLayout, title: String) {
        parent.addView(TextView(this).apply {
            text = title; textSize = 12f; typeface = Typeface.DEFAULT_BOLD; letterSpacing = 0.12f; setTextColor(cyan)
        }, LinearLayout.LayoutParams(-1, dp(28)).apply { topMargin = dp(18) })
    }

    private fun panelBackground() = android.graphics.drawable.GradientDrawable().apply {
        cornerRadius = dp(13).toFloat(); setColor(panel); setStroke(dp(1), Color.rgb(35, 64, 91))
    }
    private fun outlineBackground() = android.graphics.drawable.GradientDrawable().apply {
        cornerRadius = dp(14).toFloat(); setColor(panel); setStroke(dp(1), cyan)
    }
    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).roundToInt()
    private fun matchWrap() = LinearLayout.LayoutParams(-1, -2)
}
