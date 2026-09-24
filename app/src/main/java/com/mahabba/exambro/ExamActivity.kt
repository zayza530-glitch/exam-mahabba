package com.mahabba.exambro

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.Ringtone
import android.media.RingtoneManager
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.view.WindowManager
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class ExamActivity : AppCompatActivity() {

    private lateinit var webViewExam: WebView
    private lateinit var overlayWarning: FrameLayout
    private lateinit var tvStrikeCount: TextView
    private lateinit var tvWarnTitle: TextView
    private lateinit var tvWarnMessage: TextView
    private lateinit var btnContinueExam: Button
    private lateinit var btnUnlockEmergency: Button
    private lateinit var tvBatteryStatus: TextView

    private var strikeCount = 0
    private val maxStrikes = 3
    private val supervisorPin = "2222" // PIN Darurat Pengawas
    private var isExamFinished = false
    private var ringtone: Ringtone? = null

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
            if (level >= 0 && scale > 0) {
                val batteryPct = (level * 100) / scale
                tvBatteryStatus.text = "🔋 $batteryPct%"
            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 1. Anti-Screenshot & Screen Recording
        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE
        )

        // 2. Keep screen on (Layar tidak mati saat siswa baca soal)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // 3. Masuk Fullscreen Immersive Mode
        hideSystemUI()

        setContentView(R.layout.activity_exam)

        // Bind Views
        webViewExam = findViewById(R.id.webViewExam)
        overlayWarning = findViewById(R.id.overlayWarning)
        tvStrikeCount = findViewById(R.id.tvStrikeCount)
        tvWarnTitle = findViewById(R.id.tvWarnTitle)
        tvWarnMessage = findViewById(R.id.tvWarnMessage)
        btnContinueExam = findViewById(R.id.btnContinueExam)
        btnUnlockEmergency = findViewById(R.id.btnUnlockEmergency)
        tvBatteryStatus = findViewById(R.id.tvBatteryStatus)

        val examUrl = intent.getStringExtra("EXAM_URL") ?: "https://docs.google.com"

        // Inisialisasi Suara Alarm Sirine
        initAlarmSound()

        // Setup WebView Khusus Ujian (Hemat RAM untuk HP Kentang)
        setupLightweightWebView(examUrl)

        // Listener Tombol Buka Kunci Guru di Pojok Atas
        findViewById<View>(R.id.btnUnlockTeacher).setOnClickListener {
            showPinDialog()
        }

        btnContinueExam.setOnClickListener {
            stopAlarmSound()
            overlayWarning.visibility = View.GONE
            hideSystemUI()
        }

        btnUnlockEmergency.setOnClickListener {
            showPinDialog()
        }

        // Mulai Lock Task Mode (Pinning Layar Android) jika didukung
        try {
            startLockTask()
        } catch (_: Exception) {}

        // Pantau Baterai
        registerReceiver(batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
    }

    private fun setupLightweightWebView(url: String) {
        val settings = webViewExam.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.cacheMode = WebSettings.LOAD_DEFAULT
        settings.setSupportMultipleWindows(false)
        settings.allowFileAccess = false
        settings.displayZoomControls = false
        settings.builtInZoomControls = true

        webViewExam.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val target = request?.url?.toString() ?: ""
                // Cegah siswa melompat ke luar halaman form
                return !target.contains("forms.gle") && !target.contains("google.com")
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                // Blokir klik kanan / long-press copy teks
                view?.loadUrl("javascript:(function() { " +
                        "document.body.style.webkitUserSelect='none'; " +
                        "document.body.style.userSelect='none'; " +
                        "})()")
            }
        }

        webViewExam.loadUrl(url)
    }

    // --- DETEKSI KECURANGAN ---

    override fun onWindowFocusLost(hasFocus: Boolean) {
        super.onWindowFocusLost(hasFocus)
        if (!hasFocus && !isExamFinished) {
            handleCheatAttempt("Terdeteksi membuka panel notifikasi atau aplikasi melayang!")
        } else if (hasFocus) {
            hideSystemUI()
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (!isExamFinished) {
            handleCheatAttempt("Terdeteksi menekan tombol Home atau berpindah aplikasi!")
        }
    }

    override fun onMultiWindowModeChanged(isInMultiWindowMode: Boolean) {
        super.onMultiWindowModeChanged(isInMultiWindowMode)
        if (isInMultiWindowMode && !isExamFinished) {
            handleCheatAttempt("Terdeteksi menggunakan fitur Split-Screen / Layar Ganda!")
        }
    }

    private fun handleCheatAttempt(reason: String) {
        strikeCount++
        playAlarmSound()

        overlayWarning.visibility = View.VISIBLE
        tvWarnMessage.text = reason
        tvStrikeCount.text = "Pelanggaran: $strikeCount / $maxStrikes"

        if (strikeCount >= maxStrikes) {
            // Lockdown Total: Tombol lanjut disembunyikan
            tvWarnTitle.text = getString(R.string.lockdown_title)
            tvWarnMessage.text = getString(R.string.lockdown_msg)
            btnContinueExam.visibility = View.GONE
            btnUnlockEmergency.visibility = View.VISIBLE
        }
    }

    // --- ALARM BYPASS SILENT MODE ---

    private fun initAlarmSound() {
        try {
            val alertUri: Uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            ringtone = RingtoneManager.getRingtone(applicationContext, alertUri)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                ringtone?.audioAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            }
        } catch (_: Exception) {}
    }

    private fun playAlarmSound() {
        try {
            if (ringtone?.isPlaying == false) {
                ringtone?.play()
            }
        } catch (_: Exception) {}
    }

    private fun stopAlarmSound() {
        try {
            if (ringtone?.isPlaying == true) {
                ringtone?.stop()
            }
        } catch (_: Exception) {}
    }

    // --- DIALOG PIN PENGAWAS ---

    private fun showPinDialog() {
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
            hint = "PIN 4 Digit"
            textSize = 18f
        }

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.pin_dialog_title))
            .setMessage(getString(R.string.pin_dialog_desc))
            .setView(input)
            .setPositiveButton("Buka Kunci") { _, _ ->
                val enteredPin = input.text.toString().trim()
                if (enteredPin == supervisorPin) {
                    stopAlarmSound()
                    isExamFinished = true
                    try {
                        stopLockTask()
                    } catch (_: Exception) {}
                    Toast.makeText(this, "Aplikasi berhasil dibuka oleh pengawas.", Toast.LENGTH_SHORT).show()
                    finish()
                } else {
                    Toast.makeText(this, "PIN Pengawas Salah!", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun hideSystemUI() {
        window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        or View.SYSTEM_UI_FLAG_FULLSCREEN
                )
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        // Blokir tombol Back sepenuhnya
        Toast.makeText(this, "Tombol Kembali dinonaktifkan demi keamanan ujian.", Toast.LENGTH_SHORT).show()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopAlarmSound()
        try {
            unregisterReceiver(batteryReceiver)
        } catch (_: Exception) {}
    }
}
