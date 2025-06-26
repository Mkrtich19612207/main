package com.example.myapplication

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

class PaymentActivity : AppCompatActivity() {
    private lateinit var btnActivateCode: Button
    private lateinit var etCode: EditText
    private lateinit var btnLifetime: Button
    private lateinit var btnSubscribe: Button
    private lateinit var btnReset: Button
    private lateinit var btnFaq: Button
    private lateinit var btnExit: Button
    private lateinit var progressBar: ProgressBar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_payment)

        // --- Toolbar с иконкой языка ---
        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(false)

        etCode = findViewById(R.id.etCode)
        btnActivateCode = findViewById(R.id.btnActivateCode)
        btnLifetime = findViewById(R.id.btnLifetime)
        btnSubscribe = findViewById(R.id.btnSubscribe)
        btnReset = findViewById(R.id.btnReset)
        btnFaq = findViewById(R.id.btnFaq)
        btnExit = findViewById(R.id.btnExit)
        progressBar = findViewById(R.id.progressBar)

        btnLifetime.setOnClickListener { showPaymentDialog() }
        btnSubscribe.setOnClickListener { showPaymentDialog() }
        btnReset.setOnClickListener { showResetDialog() }
        btnExit.setOnClickListener { finishAffinity() }

        btnActivateCode.setOnClickListener {
            val code = etCode.text.toString().trim()
            if (code.isEmpty()) {
                Toast.makeText(this, getString(R.string.enter_code), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            progressBar.visibility = android.view.View.VISIBLE
            btnActivateCode.isEnabled = false

            validateCodeOnline(code) { isValid, type ->
                progressBar.visibility = android.view.View.GONE
                btnActivateCode.isEnabled = true

                if (isValid) {
                    when (type) {
                        "month" -> { saveSubscription(30) }
                        "3month" -> { saveSubscription(90) }
                        "year" -> { saveSubscription(365) }
                        "lifetime" -> { saveLifetimePurchase() }
                        else -> Toast.makeText(this, getString(R.string.code_unknown), Toast.LENGTH_LONG).show()
                    }
                    etCode.setText("")
                    goToMain()
                } else {
                    Toast.makeText(this, getString(R.string.code_invalid), Toast.LENGTH_LONG).show()
                }
            }
        }

        btnFaq.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle(getString(R.string.faq_title))
                .setMessage(getString(R.string.faq_message))
                .setPositiveButton(android.R.string.ok, null)
                .show()
        }
    }

    // --- Добавляем меню с языками ---
    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_language, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.action_language) {
            showLanguageDialog()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    private fun showLanguageDialog() {
        val languages = arrayOf("Русский", "English", "Հայերեն")
        val langCodes = arrayOf("ru", "en", "hy")
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.choose_language))
            .setItems(languages) { _, which ->
                setLocale(langCodes[which])
            }
            .show()
    }

    private fun goToMain() {
        val intent = Intent(this, MapsActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    // --- Проверка кода онлайн ---
    private fun validateCodeOnline(code: String, onResult: (Boolean, String) -> Unit) {
        Thread {
            try {
                val url = "https://raw.githubusercontent.com/Mkrtich19612207/smeta-codes/refs/heads/main/valid_codes_10000.json"
                val client = OkHttpClient()
                val request = Request.Builder().url(url).build()
                val response = client.newCall(request).execute()
                val body = response.body?.string()
                val json = JSONObject(body)
                val codesArr = json.getJSONArray("codes")

                var found = false
                var foundType = "unknown"
                for (i in 0 until codesArr.length()) {
                    val item = codesArr.get(i)
                    if (item is JSONObject) {
                        val codeStr = item.getString("code")
                        val type = item.optString("type", "month")
                        if (codeStr.equals(code, ignoreCase = true)) {
                            found = true
                            foundType = type
                            break
                        }
                    } else if (item is String && item.equals(code, ignoreCase = true)) {
                        found = true
                        foundType = codeTypeByPrefix(code)
                        break
                    }
                }
                runOnUiThread { onResult(found, foundType) }
            } catch (e: Exception) {
                e.printStackTrace()
                runOnUiThread { onResult(false, "error") }
            }
        }.start()
    }

    private fun codeTypeByPrefix(code: String): String {
        return when {
            code.startsWith("SMT-0") -> "month"
            code.startsWith("SMT-1") -> "3month"
            code.startsWith("SMT-2") -> "year"
            code.equals("NAVSEGDA2024", ignoreCase = true) -> "lifetime"
            else -> "unknown"
        }
    }

    private fun saveLifetimePurchase() {
        val prefs = getSharedPreferences("user_prefs", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("isLifetime", true)
            .remove("subscriptionEndDate")
            .apply()
    }

    private fun saveSubscription(days: Int) {
        val prefs = getSharedPreferences("user_prefs", Context.MODE_PRIVATE)
        val endMillis = System.currentTimeMillis() + days * 24 * 60 * 60 * 1000L
        prefs.edit()
            .putBoolean("isLifetime", false)
            .putLong("subscriptionEndDate", endMillis)
            .apply()
    }

    private fun showResetDialog() {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.reset_subscription_title))
            .setMessage(getString(R.string.reset_subscription_message))
            .setPositiveButton(getString(R.string.reset_subscription_yes)) { _, _ ->
                resetSubscription()
                Toast.makeText(this, getString(R.string.reset_subscription_success), Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(getString(R.string.reset_subscription_no), null)
            .show()
    }

    private fun resetSubscription() {
        val prefs = getSharedPreferences("user_prefs", Context.MODE_PRIVATE)
        prefs.edit().clear().apply()
    }

    private fun setLocale(language: String) {
        val locale = java.util.Locale(language)
        java.util.Locale.setDefault(locale)
        val config = resources.configuration
        config.setLocale(locale)
        @Suppress("DEPRECATION")
        resources.updateConfiguration(config, resources.displayMetrics)
        recreate()
    }

    // Новый диалог оплаты (ПЛАТЁЖНЫЙ ДИАЛОГ)
    private fun showPaymentDialog() {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.lifetime_purchase))
            .setMessage(
                getString(R.string.payment_instructions,
                    "💳 4318 2700 0068 5212",
                    "nikanikogosan@gmail.com",
                    "@smeta_payment_bot"
                )
            )
            .setPositiveButton(getString(R.string.pay_in_telegram)) { _, _ ->
                val tgIntent = Intent(Intent.ACTION_VIEW)
                tgIntent.data = Uri.parse("https://t.me/smeta_payment_bot")
                startActivity(tgIntent)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }
}
