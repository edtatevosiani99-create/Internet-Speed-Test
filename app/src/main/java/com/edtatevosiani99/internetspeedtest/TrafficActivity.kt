package com.edtatevosiani99.internetspeedtest

import android.app.Activity
import android.app.AppOpsManager
import android.app.usage.NetworkStats
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
    @Volatile private var isLoading = false

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



    private fun selectedLocale(): Locale {
        val code = getSharedPreferences("speed_test", MODE_PRIVATE).getString("language", "en") ?: "en"
        return when (code) {
            "ru" -> Locale("ru")
            "ka" -> Locale("ka")
            "de" -> Locale.GERMAN
            "es" -> Locale("es")
            "fr" -> Locale.FRENCH
            "it" -> Locale.ITALIAN
            "tr" -> Locale("tr")
            "uk" -> Locale("uk")
            else -> Locale.ENGLISH
        }
    }

    private fun tr(value: String): String {
        val lang = getSharedPreferences("speed_test", MODE_PRIVATE).getString("language", "en") ?: "en"
        val common = mapOf(
            "ru" to mapOf(
                "INTERNET TRAFFIC" to "ИНТЕРНЕТ-ТРАФИК", "Daily mobile and Wi-Fi usage" to "Ежедневный расход мобильного интернета и Wi-Fi",
                "Updating traffic history…" to "Обновление истории трафика…", "Traffic history" to "История трафика",
                "This page shows daily and monthly phone traffic, separated into mobile data and Wi-Fi. Android requires you to allow Usage access first. Your traffic page will stay here; it will not redirect you automatically." to "На этой странице показан ежедневный и ежемесячный расход мобильного интернета и Wi-Fi отдельно. Сначала Android требует разрешить доступ к статистике. Эта страница останется открытой и не будет перенаправлять вас автоматически.",
                "ALLOW USAGE ACCESS" to "РАЗРЕШИТЬ ДОСТУП К СТАТИСТИКЕ",
                "Open Android Settings and allow Usage access" to "Откройте настройки Android и разрешите доступ к статистике",
                "After enabling access, return to this page. Statistics will load automatically. The app does not provide a button to erase traffic history." to "После разрешения доступа вернитесь на эту страницу. Статистика загрузится автоматически. В приложении нет кнопки удаления истории трафика.",
                "No traffic data available yet. Check Usage access in Android Settings." to "Данные о трафике пока недоступны. Проверьте доступ к статистике в настройках Android.",
                "Date" to "Дата", "Mob" to "Моб.", "WiFi" to "Wi-Fi", "Total" to "Всего", "This month history" to "По месяцам",
                "Mob" to "Моб.", "History is stored on this phone. Recent daily totals refresh from Android system statistics; older saved days remain available. The app does not provide a delete button." to "История хранится на этом телефоне. Данные за последние дни обновляются по статистике Android; более ранние сохранённые дни доступны. В приложении нет кнопки удаления.",
                "Unavailable" to "Недоступно"
            ),
            "ka" to mapOf(
                "INTERNET TRAFFIC" to "ინტერნეტ ტრაფიკი", "Daily mobile and Wi-Fi usage" to "მობილური ინტერნეტისა და Wi-Fi-ის ყოველდღიური გამოყენება",
                "Updating traffic history…" to "ტრაფიკის ისტორია ახლდება…", "Traffic history" to "ტრაფიკის ისტორია",
                "This page shows daily and monthly phone traffic, separated into mobile data and Wi-Fi. Android requires you to allow Usage access first. Your traffic page will stay here; it will not redirect you automatically." to "ამ გვერდზე ნაჩვენებია ტელეფონის ყოველდღიური და თვიური ტრაფიკი ცალ-ცალკე მობილური ინტერნეტისა და Wi-Fi-ისთვის. Android მოითხოვს სტატისტიკაზე წვდომის ნებართვას. ეს გვერდი ღია დარჩება და ავტომატურად არ გადაგამისამართებთ.",
                "ALLOW USAGE ACCESS" to "სტატისტიკაზე წვდომის დაშვება",
                "Open Android Settings and allow Usage access" to "გახსენით Android-ის პარამეტრები და დაუშვით სტატისტიკაზე წვდომა",
                "After enabling access, return to this page. Statistics will load automatically. The app does not provide a button to erase traffic history." to "წვდომის ჩართვის შემდეგ დაბრუნდით ამ გვერდზე. სტატისტიკა ავტომატურად ჩაიტვირთება. აპლიკაციაში ტრაფიკის ისტორიის წაშლის ღილაკი არ არის.",
                "No traffic data available yet. Check Usage access in Android Settings." to "ტრაფიკის მონაცემები ჯერ არ არის. შეამოწმეთ სტატისტიკაზე წვდომა Android-ის პარამეტრებში.",
                "Date" to "თარიღი", "Mob" to "მობ.", "WiFi" to "Wi-Fi", "Total" to "ჯამი", "This month history" to "თვეების მიხედვით",
                "History is stored on this phone. Recent daily totals refresh from Android system statistics; older saved days remain available. The app does not provide a delete button." to "ისტორია ამ ტელეფონში ინახება. ბოლო დღეების მონაცემები Android-ის სტატისტიკის მიხედვით ახლდება; ძველი დღეებიც ხელმისაწვდომია. აპლიკაციაში წაშლის ღილაკი არ არის.",
                "Unavailable" to "მიუწვდომელია"
            ),
            "de" to mapOf(
                "INTERNET TRAFFIC" to "INTERNETDATENVERBRAUCH", "Daily mobile and Wi-Fi usage" to "Täglicher Verbrauch von mobilen Daten und WLAN",
                "Updating traffic history…" to "Datenverlauf wird aktualisiert…", "Traffic history" to "Datenverlauf",
                "This page shows daily and monthly phone traffic, separated into mobile data and Wi-Fi. Android requires you to allow Usage access first. Your traffic page will stay here; it will not redirect you automatically." to "Diese Seite zeigt den täglichen und monatlichen Datenverbrauch getrennt nach mobilen Daten und WLAN. Android benötigt zuerst die Berechtigung für den Nutzungszugriff. Diese Seite bleibt geöffnet und leitet Sie nicht automatisch weiter.",
                "ALLOW USAGE ACCESS" to "NUTZUNGSZUGRIFF ERLAUBEN",
                "Open Android Settings and allow Usage access" to "Öffnen Sie die Android-Einstellungen und erlauben Sie den Nutzungszugriff",
                "After enabling access, return to this page. Statistics will load automatically. The app does not provide a button to erase traffic history." to "Kehren Sie nach der Freigabe zu dieser Seite zurück. Die Statistik wird automatisch geladen. Die App bietet keine Schaltfläche zum Löschen des Datenverlaufs.",
                "No traffic data available yet. Check Usage access in Android Settings." to "Noch keine Daten verfügbar. Prüfen Sie den Nutzungszugriff in den Android-Einstellungen.",
                "Date" to "Datum", "Mob" to "Mobil", "WiFi" to "WLAN", "Total" to "Gesamt", "This month history" to "Monatsübersicht",
                "History is stored on this phone. Recent daily totals refresh from Android system statistics; older saved days remain available. The app does not provide a delete button." to "Der Verlauf wird auf diesem Telefon gespeichert. Aktuelle Tageswerte werden anhand der Android-Statistik aktualisiert; ältere gespeicherte Tage bleiben verfügbar. Es gibt keine Löschtaste.",
                "Unavailable" to "Nicht verfügbar"
            ),
            "es" to mapOf(
                "INTERNET TRAFFIC" to "TRÁFICO DE INTERNET", "Daily mobile and Wi-Fi usage" to "Uso diario de datos móviles y Wi-Fi",
                "Updating traffic history…" to "Actualizando el historial de tráfico…", "Traffic history" to "Historial de tráfico",
                "This page shows daily and monthly phone traffic, separated into mobile data and Wi-Fi. Android requires you to allow Usage access first. Your traffic page will stay here; it will not redirect you automatically." to "Esta página muestra el tráfico diario y mensual del teléfono, separado entre datos móviles y Wi-Fi. Android requiere permitir primero el acceso al uso. Esta página permanecerá abierta y no te redirigirá automáticamente.",
                "ALLOW USAGE ACCESS" to "PERMITIR ACCESO DE USO", "Open Android Settings and allow Usage access" to "Abre Ajustes de Android y permite el acceso al uso",
                "After enabling access, return to this page. Statistics will load automatically. The app does not provide a button to erase traffic history." to "Después de habilitar el acceso, vuelve a esta página. Las estadísticas se cargarán automáticamente. La app no ofrece un botón para borrar el historial.",
                "No traffic data available yet. Check Usage access in Android Settings." to "Aún no hay datos de tráfico. Comprueba el acceso al uso en Ajustes de Android.",
                "Date" to "Fecha", "Mob" to "Móvil", "WiFi" to "Wi-Fi", "Total" to "Total", "This month history" to "Historial mensual",
                "History is stored on this phone. Recent daily totals refresh from Android system statistics; older saved days remain available. The app does not provide a delete button." to "El historial se guarda en este teléfono. Los totales recientes se actualizan con las estadísticas de Android; los días anteriores siguen disponibles. La app no tiene botón para borrar.",
                "Unavailable" to "No disponible"
            ),
            "fr" to mapOf(
                "INTERNET TRAFFIC" to "TRAFIC INTERNET", "Daily mobile and Wi-Fi usage" to "Utilisation quotidienne des données mobiles et du Wi-Fi",
                "Updating traffic history…" to "Mise à jour de l’historique du trafic…", "Traffic history" to "Historique du trafic",
                "This page shows daily and monthly phone traffic, separated into mobile data and Wi-Fi. Android requires you to allow Usage access first. Your traffic page will stay here; it will not redirect you automatically." to "Cette page affiche le trafic quotidien et mensuel du téléphone, séparé entre données mobiles et Wi-Fi. Android exige d’autoriser l’accès à l’utilisation. Cette page restera ouverte sans redirection automatique.",
                "ALLOW USAGE ACCESS" to "AUTORISER L’ACCÈS À L’UTILISATION", "Open Android Settings and allow Usage access" to "Ouvrez les paramètres Android et autorisez l’accès à l’utilisation",
                "After enabling access, return to this page. Statistics will load automatically. The app does not provide a button to erase traffic history." to "Après avoir autorisé l’accès, revenez à cette page. Les statistiques se chargeront automatiquement. L’application ne propose pas de bouton pour effacer l’historique.",
                "No traffic data available yet. Check Usage access in Android Settings." to "Aucune donnée de trafic pour le moment. Vérifiez l’accès à l’utilisation dans les paramètres Android.",
                "Date" to "Date", "Mob" to "Mobile", "WiFi" to "Wi-Fi", "Total" to "Total", "This month history" to "Historique mensuel",
                "History is stored on this phone. Recent daily totals refresh from Android system statistics; older saved days remain available. The app does not provide a delete button." to "L’historique est enregistré sur ce téléphone. Les totaux récents sont actualisés depuis les statistiques Android ; les jours plus anciens restent disponibles. Aucun bouton de suppression n’est proposé.",
                "Unavailable" to "Indisponible"
            ),
            "it" to mapOf(
                "INTERNET TRAFFIC" to "TRAFFICO INTERNET", "Daily mobile and Wi-Fi usage" to "Utilizzo giornaliero di dati mobili e Wi-Fi",
                "Updating traffic history…" to "Aggiornamento cronologia traffico…", "Traffic history" to "Cronologia traffico",
                "This page shows daily and monthly phone traffic, separated into mobile data and Wi-Fi. Android requires you to allow Usage access first. Your traffic page will stay here; it will not redirect you automatically." to "Questa pagina mostra il traffico giornaliero e mensile del telefono, separato tra dati mobili e Wi-Fi. Android richiede prima l’autorizzazione all’accesso utilizzo. La pagina resterà aperta senza reindirizzamento automatico.",
                "ALLOW USAGE ACCESS" to "CONSENTI ACCESSO UTILIZZO", "Open Android Settings and allow Usage access" to "Apri le impostazioni Android e consenti l’accesso utilizzo",
                "After enabling access, return to this page. Statistics will load automatically. The app does not provide a button to erase traffic history." to "Dopo aver abilitato l’accesso, torna a questa pagina. Le statistiche si caricheranno automaticamente. L’app non offre un pulsante per cancellare la cronologia.",
                "No traffic data available yet. Check Usage access in Android Settings." to "Nessun dato sul traffico disponibile. Controlla l’accesso utilizzo nelle impostazioni Android.",
                "Date" to "Data", "Mob" to "Mobile", "WiFi" to "Wi-Fi", "Total" to "Totale", "This month history" to "Riepilogo mensile",
                "History is stored on this phone. Recent daily totals refresh from Android system statistics; older saved days remain available. The app does not provide a delete button." to "La cronologia è salvata su questo telefono. I totali recenti vengono aggiornati dalle statistiche Android; i giorni precedenti restano disponibili. Non è presente un pulsante di eliminazione.",
                "Unavailable" to "Non disponibile"
            ),
            "tr" to mapOf(
                "INTERNET TRAFFIC" to "İNTERNET TRAFİĞİ", "Daily mobile and Wi-Fi usage" to "Günlük mobil veri ve Wi-Fi kullanımı",
                "Updating traffic history…" to "Trafik geçmişi güncelleniyor…", "Traffic history" to "Trafik geçmişi",
                "This page shows daily and monthly phone traffic, separated into mobile data and Wi-Fi. Android requires you to allow Usage access first. Your traffic page will stay here; it will not redirect you automatically." to "Bu sayfa telefonun günlük ve aylık trafiğini mobil veri ve Wi-Fi olarak ayrı gösterir. Android önce kullanım erişimine izin verilmesini ister. Sayfa açık kalır ve otomatik yönlendirme yapmaz.",
                "ALLOW USAGE ACCESS" to "KULLANIM ERİŞİMİNE İZİN VER", "Open Android Settings and allow Usage access" to "Android Ayarları'nı açın ve kullanım erişimine izin verin",
                "After enabling access, return to this page. Statistics will load automatically. The app does not provide a button to erase traffic history." to "Erişimi açtıktan sonra bu sayfaya dönün. İstatistikler otomatik yüklenir. Uygulamada trafik geçmişini silme düğmesi yoktur.",
                "No traffic data available yet. Check Usage access in Android Settings." to "Henüz trafik verisi yok. Android Ayarları'ndaki kullanım erişimini kontrol edin.",
                "Date" to "Tarih", "Mob" to "Mobil", "WiFi" to "Wi-Fi", "Total" to "Toplam", "This month history" to "Aylık geçmiş",
                "History is stored on this phone. Recent daily totals refresh from Android system statistics; older saved days remain available. The app does not provide a delete button." to "Geçmiş bu telefonda saklanır. Son günlerin toplamları Android istatistiklerinden yenilenir; eski günler de kullanılabilir. Silme düğmesi yoktur.",
                "Unavailable" to "Kullanılamıyor"
            ),
            "uk" to mapOf(
                "INTERNET TRAFFIC" to "ІНТЕРНЕТ-ТРАФІК", "Daily mobile and Wi-Fi usage" to "Щоденне використання мобільних даних і Wi-Fi",
                "Updating traffic history…" to "Оновлення історії трафіку…", "Traffic history" to "Історія трафіку",
                "This page shows daily and monthly phone traffic, separated into mobile data and Wi-Fi. Android requires you to allow Usage access first. Your traffic page will stay here; it will not redirect you automatically." to "На цій сторінці показано щоденний і місячний трафік телефону окремо для мобільних даних і Wi-Fi. Android спочатку потребує дозволу на доступ до статистики. Сторінка залишиться відкритою без автоматичного перенаправлення.",
                "ALLOW USAGE ACCESS" to "ДОЗВОЛИТИ ДОСТУП ДО СТАТИСТИКИ", "Open Android Settings and allow Usage access" to "Відкрийте налаштування Android і дозвольте доступ до статистики",
                "After enabling access, return to this page. Statistics will load automatically. The app does not provide a button to erase traffic history." to "Після надання доступу поверніться на цю сторінку. Статистика завантажиться автоматично. У застосунку немає кнопки видалення історії трафіку.",
                "No traffic data available yet. Check Usage access in Android Settings." to "Дані про трафік поки недоступні. Перевірте доступ до статистики в налаштуваннях Android.",
                "Date" to "Дата", "Mob" to "Моб.", "WiFi" to "Wi-Fi", "Total" to "Усього", "This month history" to "За місяцями",
                "History is stored on this phone. Recent daily totals refresh from Android system statistics; older saved days remain available. The app does not provide a delete button." to "Історія зберігається на цьому телефоні. Дані за останні дні оновлюються за статистикою Android; старіші дні залишаються доступними. Кнопки видалення немає.",
                "Unavailable" to "Недоступно"
            )
        )
        return common[lang]?.get(value) ?: value
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
            text = tr("INTERNET TRAFFIC"); textSize = 19f; typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE); gravity = Gravity.CENTER_VERTICAL
        }, LinearLayout.LayoutParams(0, -2, 1f))
        val refresh = TextView(this).apply {
            text = "↻"; textSize = 27f; setTextColor(cyan); gravity = Gravity.CENTER
            background = panelBackground(); setOnClickListener { loadAndRender() }
        }
        header.addView(refresh, LinearLayout.LayoutParams(dp(46), dp(46)))
        root.addView(header, LinearLayout.LayoutParams(-1, dp(52)))
        root.addView(TextView(this).apply {
            text = tr("Daily mobile and Wi-Fi usage"); textSize = 13f; setTextColor(Color.LTGRAY)
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
        if (isLoading) return
        isLoading = true
        // Render the table immediately, before Android finishes querying statistics.
        renderRows()
        Thread {
            val editor = historyPrefs.edit()
            try {
                val now = System.currentTimeMillis()
                val todayStart = startOfDay(now)
                val cursor = Calendar.getInstance().apply { timeInMillis = todayStart; add(Calendar.DAY_OF_YEAR, -89) }
                while (cursor.timeInMillis <= todayStart) {
                    val dayStart = cursor.timeInMillis
                    val nextDay = Calendar.getInstance().apply { timeInMillis = dayStart; add(Calendar.DAY_OF_YEAR, 1) }.timeInMillis
                    val key = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(dayStart))
                    try {
                        val usage = if (dayStart == todayStart) readTrafficForRange(dayStart, now) else readTrafficForRange(dayStart, nextDay)
                        saveLatest(editor, key + "_wifi", usage.first)
                        saveLatest(editor, key + "_mobile", usage.second)
                    } catch (_: Exception) {
                        // Keep previously saved values for this date and continue.
                    }
                    cursor.add(Calendar.DAY_OF_YEAR, 1)
                }
                editor.apply()
            } catch (_: Exception) {
                // Keep the saved table visible if Android statistics fail.
            } finally {
                runOnUiThread {
                    isLoading = false
                    renderRows()
                }
            }
        }.start()
    }

    private fun renderPermissionNeeded() {
        if (!::content.isInitialized) return
        // Always render the traffic table first, even before Usage access is granted.
        // This ensures the table is visible immediately; permission help follows it.
        renderRows()
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = panelBackground()
            setPadding(dp(16), dp(18), dp(16), dp(18))
        }
        card.addView(label(tr("Traffic history"), 19f, Color.WHITE, true), matchWrap())
        card.addView(label(
            tr("This page shows daily and monthly phone traffic, separated into mobile data and Wi-Fi. Android requires you to allow Usage access first. Your traffic page will stay here; it will not redirect you automatically."),
            14f, Color.rgb(205, 220, 238)
        ), matchWrap().apply { topMargin = dp(12) })
        val allow = TextView(this).apply {
            text = tr("ALLOW USAGE ACCESS")
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
                    Toast.makeText(this@TrafficActivity, tr("Open Android Settings and allow Usage access"), Toast.LENGTH_LONG).show()
                }
            }
        }
        card.addView(allow, matchWrap().apply { topMargin = dp(16) })
        // Place the permission card directly after the table, not above it.
        content.addView(card, 1, matchWrap().apply { topMargin = dp(10) })
    }

    private fun renderRows() {
        content.removeAllViews()
        val allDates = historyPrefs.all.keys.mapNotNull { key ->
            key.substringBeforeLast("_").takeIf { it.matches(Regex("\\d{4}-\\d{2}-\\d{2}")) }
        }.distinct().sortedDescending()
        val todayKey = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        val monthKey = todayKey.substring(0, 7)

        // Put the monthly summary and daily table at the top so they are visible
        // immediately on opening the traffic page, without scrolling past the chart.
        val table = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = panelBackground()
            setPadding(dp(8), dp(8), dp(8), dp(8))
        }
        table.addView(tableRow(tr("Date"), tr("Mob"), tr("WiFi"), tr("Total"), true))

        val todayWifi = historyPrefs.getLong(todayKey + "_wifi", -1L)
        val todayMobile = historyPrefs.getLong(todayKey + "_mobile", -1L)
        val todayTotal = if (todayWifi >= 0L && todayMobile >= 0L) todayWifi + todayMobile else -1L
        var monthWifi = 0L
        var monthMobile = 0L
        var monthWifiKnown = false
        var monthMobileKnown = false
        allDates.filter { it.startsWith(monthKey) }.forEach { date ->
            val w = historyPrefs.getLong(date + "_wifi", -1L)
            val m = historyPrefs.getLong(date + "_mobile", -1L)
            if (w >= 0L) { monthWifi += w; monthWifiKnown = true }
            if (m >= 0L) { monthMobile += m; monthMobileKnown = true }
        }
        val monthWifiValue = if (monthWifiKnown) monthWifi else -1L
        val monthMobileValue = if (monthMobileKnown) monthMobile else -1L
        val monthTotal = if (monthWifiKnown && monthMobileKnown) monthWifi + monthMobile else -1L
        // Monthly total first, followed by today's total and the scrollable date history.
        table.addView(tableRow(tr("This month"), formatBytes(monthMobileValue), formatBytes(monthWifiValue), formatBytes(monthTotal), true))
        table.addView(LinearLayout(this).apply { setBackgroundColor(cyan) }, LinearLayout.LayoutParams(-1, dp(1)))
        table.addView(tableRow(tr("Today"), formatBytes(todayMobile), formatBytes(todayWifi), formatBytes(todayTotal), true))
        table.addView(LinearLayout(this).apply { setBackgroundColor(Color.rgb(35, 55, 80)) }, LinearLayout.LayoutParams(-1, dp(1)))

        allDates.filter { it != todayKey }.take(89).forEachIndexed { index, date ->
            val wifi = historyPrefs.getLong(date + "_wifi", -1L)
            val mobile = historyPrefs.getLong(date + "_mobile", -1L)
            val total = if (wifi >= 0L && mobile >= 0L) wifi + mobile else -1L
            val displayDate = try {
                SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(
                    SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(date)!!
                )
            } catch (_: Exception) { date }
            table.addView(tableRow(displayDate, formatBytes(mobile), formatBytes(wifi), formatBytes(total), false))
            if (index < allDates.filter { it != todayKey }.take(89).size - 1) {
                table.addView(LinearLayout(this).apply { setBackgroundColor(Color.rgb(35, 55, 80)) }, LinearLayout.LayoutParams(-1, dp(1)))
            }
        }
        content.addView(table, matchWrap())
        // Keep the visual daily chart, but below the requested table and monthly summary.
        addDailyScale(todayKey)
        content.addView(label(
            tr("History is stored on this phone. Recent daily totals refresh from Android system statistics; older saved days remain available. The app does not provide a delete button."),
            11f, Color.rgb(125, 145, 170)
        ), matchWrap().apply { topMargin = dp(10) })
    }

    private fun addDailyScale(todayKey: String) {
        val mobile = historyPrefs.getLong(todayKey + "_mobile", -1L)
        val wifi = historyPrefs.getLong(todayKey + "_wifi", -1L)
        val total = if (mobile >= 0L && wifi >= 0L) mobile + wifi else -1L

        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = panelBackground()
            setPadding(dp(14), dp(14), dp(14), dp(14))
        }
        val dateTitle = try {
            SimpleDateFormat("EEE, dd MMM", selectedLocale()).format(Date())
        } catch (_: Exception) { todayKey }
        card.addView(label(dateTitle, 15f, Color.WHITE, true), matchWrap().apply { bottomMargin = dp(12) })

        val scaleMax = maxOf(total, mobile, wifi, 1L)
        fun addBar(title: String, bytes: Long, color: Int) {
            val row = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            val top = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            top.addView(label(title, 12f, Color.rgb(205, 220, 238)), LinearLayout.LayoutParams(0, -2, 1f))
            top.addView(label(formatBytes(bytes), 12f, color, true), matchWrap())
            row.addView(top, matchWrap())
            val track = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                background = android.graphics.drawable.GradientDrawable().apply {
                    cornerRadius = dp(5).toFloat()
                    setColor(Color.rgb(30, 43, 65))
                }
                clipToOutline = true
            }
            val fraction = if (bytes < 0L) 0f else (bytes.toDouble() / scaleMax.toDouble()).toFloat().coerceIn(0f, 1f)
            val fill = TextView(this).apply {
                background = android.graphics.drawable.GradientDrawable().apply {
                    cornerRadius = dp(5).toFloat()
                    setColor(color)
                }
            }
            track.addView(fill, LinearLayout.LayoutParams(0, dp(9), fraction.coerceAtLeast(0.001f)))
            if (fraction < 1f) track.addView(TextView(this), LinearLayout.LayoutParams(0, dp(9), (1f - fraction).coerceAtLeast(0.001f)))
            row.addView(track, matchWrap().apply { topMargin = dp(6) })
            card.addView(row, matchWrap().apply { bottomMargin = dp(12) })
        }

        addBar(tr("Mob"), mobile, cyan)
        addBar(tr("WiFi"), wifi, Color.rgb(93, 148, 255))
        addBar(tr("Total"), total, Color.rgb(96, 235, 177))

        val scale = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        listOf("0%", "25%", "50%", "75%", "100%").forEach { tick ->
            scale.addView(TextView(this).apply {
                text = tick
                textSize = 10f
                setTextColor(Color.rgb(125, 145, 170))
                gravity = Gravity.CENTER
            }, LinearLayout.LayoutParams(0, -2, 1f))
        }
        card.addView(scale, matchWrap())
        content.addView(card, matchWrap().apply { bottomMargin = dp(14) })
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
        val manager = getSystemService(Context.NETWORK_STATS_SERVICE) as NetworkStatsManager

        fun totalBytes(type: Int): Long {
            var deviceTotal = -1L
            try {
                val bucket = manager.querySummaryForDevice(type, null, start, end)
                deviceTotal = (bucket.rxBytes + bucket.txBytes).coerceAtLeast(0L)
            } catch (_: Exception) {
                // Some Android builds do not return device summaries reliably.
            }

            // Fallback: sum the network's UID buckets when device summary is unavailable
            // or reports zero despite available per-app network statistics.
            var uidTotal = -1L
            try {
                val stats = manager.querySummary(type, null, start, end)
                var sum = 0L
                val bucket = NetworkStats.Bucket()
                while (stats.hasNextBucket()) {
                    if (stats.getNextBucket(bucket)) {
                        sum += bucket.rxBytes + bucket.txBytes
                    }
                }
                stats.close()
                uidTotal = sum.coerceAtLeast(0L)
            } catch (_: Exception) {
                // Keep whichever source Android allowed us to read.
            }

            return when {
                deviceTotal > 0L && uidTotal > 0L -> maxOf(deviceTotal, uidTotal)
                deviceTotal >= 0L && uidTotal > 0L -> uidTotal
                deviceTotal >= 0L -> deviceTotal
                uidTotal >= 0L -> uidTotal
                else -> -1L
            }
        }

        return Pair(totalBytes(ConnectivityManager.TYPE_WIFI), totalBytes(ConnectivityManager.TYPE_MOBILE))
    }

    // Use the latest successful system reading so delayed Android corrections
    // are reflected instead of permanently keeping a potentially inflated maximum.
    private fun saveLatest(editor: android.content.SharedPreferences.Editor, key: String, value: Long) {
        if (value >= 0L) editor.putLong(key, value)
    }

    private fun formatBytes(bytes: Long): String {
        if (bytes < 0L) return tr("Unavailable")
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
