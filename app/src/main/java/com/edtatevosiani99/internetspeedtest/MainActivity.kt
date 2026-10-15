package com.edtatevosiani99.internetspeedtest

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread
import kotlin.math.roundToInt

class MainActivity : Activity() {
    private val bg = Color.rgb(8, 13, 27)
    private val panel = Color.rgb(16, 25, 45)
    private val cyan = Color.rgb(42, 214, 255)
    private lateinit var status: TextView
    private lateinit var pingValue: TextView
    private lateinit var downloadValue: TextView
    private lateinit var uploadValue: TextView
    private lateinit var startButton: Button
    private lateinit var shareButton: Button
    private var lastResult = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = bg
        window.navigationBarColor = bg
        window.decorView.systemUiVisibility = 0
        lastResult = getPreferences(MODE_PRIVATE).getString("last_result", "") ?: ""
        buildScreen()
    }

    private fun buildScreen() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(22), dp(28), dp(22), dp(20))
            setBackgroundColor(bg)
        }
        val title = TextView(this).apply {
            text = "INTERNET SPEED TEST"
            textSize = 22f
            setTextColor(Color.WHITE)
            typeface = Typeface.create("sans-serif-black", Typeface.BOLD)
            gravity = Gravity.CENTER
        }
        val subtitle = TextView(this).apply {
            text = "MEASURE YOUR CONNECTION"
            textSize = 11f
            letterSpacing = 0.18f
            setTextColor(cyan)
            gravity = Gravity.CENTER
        }
        root.addView(title, matchWrap())
        root.addView(subtitle, LinearLayout.LayoutParams(-1, dp(30)))

        val dial = TextView(this).apply {
            text = "↯"
            textSize = 58f
            setTextColor(cyan)
            gravity = Gravity.CENTER
            background = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.OVAL
                setColor(panel)
                setStroke(dp(2), cyan)
            }
        }
        val dialParams = LinearLayout.LayoutParams(dp(150), dp(150))
        dialParams.gravity = Gravity.CENTER
        dialParams.topMargin = dp(26)
        dialParams.bottomMargin = dp(12)
        root.addView(dial, dialParams)

        status = TextView(this).apply {
            text = "Ready to test"
            textSize = 14f
            setTextColor(Color.LTGRAY)
            gravity = Gravity.CENTER
        }
        root.addView(status, LinearLayout.LayoutParams(-1, dp(34)))

        val metrics = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        pingValue = metric(metrics, "PING", "—", "ms")
        downloadValue = metric(metrics, "DOWNLOAD", "—", "Mbps")
        uploadValue = metric(metrics, "UPLOAD", "—", "Mbps")
        val metricsParams = LinearLayout.LayoutParams(-1, dp(116))
        metricsParams.topMargin = dp(16)
        root.addView(metrics, metricsParams)

        startButton = Button(this).apply {
            text = "START TEST"
            textSize = 16f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(bg)
            background = android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = dp(16).toFloat()
                setColor(cyan)
            }
            setOnClickListener { runTest() }
        }
        val buttonParams = LinearLayout.LayoutParams(-1, dp(58))
        buttonParams.topMargin = dp(28)
        root.addView(startButton, buttonParams)

        shareButton = Button(this).apply {
            text = "SHARE RESULTS"
            textSize = 14f
            setTextColor(cyan)
            background = android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = dp(14).toFloat()
                setColor(panel)
                setStroke(dp(1), cyan)
            }
            isEnabled = lastResult.isNotBlank()
            alpha = if (isEnabled) 1f else 0.55f
            setOnClickListener {
                if (lastResult.isNotBlank()) {
                    val send = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, lastResult)
                    }
                    startActivity(Intent.createChooser(send, "Share speed test results"))
                }
            }
        }
        val shareParams = LinearLayout.LayoutParams(-1, dp(52))
        shareParams.topMargin = dp(10)
        root.addView(shareButton, shareParams)

        val footer = TextView(this).apply {
            text = "No ads • No account required"
            textSize = 12f
            setTextColor(Color.rgb(125, 145, 170))
            gravity = Gravity.CENTER
        }
        val footerParams = LinearLayout.LayoutParams(-1, dp(40))
        footerParams.topMargin = dp(12)
        root.addView(footer, footerParams)
        setContentView(root)
    }

    private fun metric(parent: LinearLayout, label: String, initial: String, unit: String): TextView {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(4), dp(10), dp(4), dp(10))
            background = android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = dp(14).toFloat()
                setColor(panel)
                setStroke(dp(1), Color.rgb(35, 64, 91))
            }
        }
        val labelView = TextView(this).apply {
            text = label
            textSize = 10f
            setTextColor(Color.LTGRAY)
            gravity = Gravity.CENTER
        }
        val valueView = TextView(this).apply {
            text = initial
            textSize = 23f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(cyan)
            gravity = Gravity.CENTER
        }
        val unitView = TextView(this).apply {
            text = unit
            textSize = 10f
            setTextColor(Color.LTGRAY)
            gravity = Gravity.CENTER
        }
        card.addView(labelView)
        card.addView(valueView)
        card.addView(unitView)
        val params = LinearLayout.LayoutParams(0, -1, 1f)
        params.setMargins(dp(4), 0, dp(4), 0)
        parent.addView(card, params)
        return valueView
    }

    private fun runTest() {
        startButton.isEnabled = false
        startButton.text = "TESTING..."
        status.text = "Checking your connection..."
        pingValue.text = "…"
        downloadValue.text = "…"
        uploadValue.text = "…"

        thread {
            try {
                val pingStart = System.nanoTime()
                val pingConn = (URL("https://speed.cloudflare.com/__down?bytes=1").openConnection() as HttpURLConnection)
                pingConn.connectTimeout = 10000
                pingConn.readTimeout = 10000
                pingConn.requestMethod = "GET"
                pingConn.inputStream.use { it.readBytes() }
                val ping = ((System.nanoTime() - pingStart) / 1_000_000.0).roundToInt()
                pingConn.disconnect()

                runOnUiThread {
                    pingValue.text = ping.toString()
                    status.text = "Measuring download..."
                }

                val downBytes = 5_000_000
                val downStart = System.nanoTime()
                val downConn = (URL("https://speed.cloudflare.com/__down?bytes=$downBytes").openConnection() as HttpURLConnection)
                downConn.connectTimeout = 15000
                downConn.readTimeout = 20000
                downConn.inputStream.use { input ->
                    val buffer = ByteArray(8192)
                    while (input.read(buffer) != -1) { }
                }
                val downSeconds = (System.nanoTime() - downStart) / 1_000_000_000.0
                downConn.disconnect()
                val downMbps = if (downSeconds > 0) (downBytes * 8.0 / downSeconds / 1_000_000.0) else 0.0

                runOnUiThread { status.text = "Measuring upload..." }

                val uploadBytes = ByteArray(1_000_000)
                val upStart = System.nanoTime()
                val upConn = (URL("https://speed.cloudflare.com/__up").openConnection() as HttpURLConnection)
                upConn.requestMethod = "POST"
                upConn.doOutput = true
                upConn.connectTimeout = 15000
                upConn.readTimeout = 20000
                upConn.setRequestProperty("Content-Type", "application/octet-stream")
                upConn.setFixedLengthStreamingMode(uploadBytes.size)
                upConn.outputStream.use { it.write(uploadBytes) }
                val response = upConn.responseCode
                val upSeconds = (System.nanoTime() - upStart) / 1_000_000_000.0
                if (response in 200..299) {
                    try { upConn.inputStream.close() } catch (_: Exception) { }
                }
                upConn.disconnect()
                val upMbps = if (upSeconds > 0) (uploadBytes.size * 8.0 / upSeconds / 1_000_000.0) else 0.0

                runOnUiThread {
                    downloadValue.text = String.format("%.1f", downMbps)
                    uploadValue.text = if (response in 200..299) String.format("%.1f", upMbps) else "N/A"
                    status.text = "Test complete"
                    lastResult = "Internet Speed Test results\\nPing: ${ping} ms\\nDownload: ${String.format("%.1f", downMbps)} Mbps\\nUpload: ${if (response in 200..299) String.format("%.1f", upMbps) else "N/A"} Mbps"
                    getPreferences(MODE_PRIVATE).edit().putString("last_result", lastResult).apply()
                    shareButton.isEnabled = true
                    shareButton.alpha = 1f
                    startButton.isEnabled = true
                    startButton.text = "TEST AGAIN"
                }
            } catch (e: Exception) {
                runOnUiThread {
                    status.text = "Test failed. Check internet and try again."
                    startButton.isEnabled = true
                    startButton.text = "TRY AGAIN"
                    Toast.makeText(this, e.localizedMessage ?: "Connection error", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).roundToInt()
    private fun matchWrap() = LinearLayout.LayoutParams(-1, -2)
}
