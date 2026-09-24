package com.mahabba.exambro

import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Anti Screenshot & Anti Screen Recording di layar login
        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE
        )

        setContentView(R.layout.activity_main)

        val etStudentName = findViewById<EditText>(R.id.etStudentName)
        val etStudentClass = findViewById<EditText>(R.id.etStudentClass)
        val etExamToken = findViewById<EditText>(R.id.etExamToken)
        val btnStartExam = findViewById<Button>(R.id.btnStartExam)

        btnStartExam.setOnClickListener {
            val name = etStudentName.text.toString().trim()
            val studentClass = etStudentClass.text.toString().trim()
            val tokenOrUrl = etExamToken.text.toString().trim()

            if (name.isEmpty() || studentClass.isEmpty() || tokenOrUrl.isEmpty()) {
                Toast.makeText(this, "Harap lengkapi semua kolom!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Normalisasi URL jika siswa memasukkan link Google Form langsung
            val finalUrl = when {
                tokenOrUrl.startsWith("http://") || tokenOrUrl.startsWith("https://") -> tokenOrUrl
                tokenOrUrl.contains("forms.gle") || tokenOrUrl.contains("docs.google.com") -> "https://$tokenOrUrl"
                else -> {
                    // Fallback URL jika menggunakan format token
                    "https://docs.google.com/forms/d/e/$tokenOrUrl/viewform"
                }
            }

            val intent = Intent(this, ExamActivity::class.java).apply {
                putExtra("STUDENT_NAME", name)
                putExtra("STUDENT_CLASS", studentClass)
                putExtra("EXAM_URL", finalUrl)
            }
            startActivity(intent)
            finish()
        }
    }
}
