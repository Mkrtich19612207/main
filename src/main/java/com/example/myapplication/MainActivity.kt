package com.example.myapplication

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.Button
import android.widget.Toast // <-- Можно удалить потом
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import java.util.*

class MainActivity : AppCompatActivity() {

    private val languages = linkedMapOf(
        "ru" to "Русский",
        "en" to "English",
        "hy" to "Հայերեն"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        applySavedLocale()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(false)

        findViewById<Button>(R.id.buttonOpenMap).setOnClickListener {
            if (hasAccess()) {
                startActivity(Intent(this, MapsActivity::class.java))
            } else {
                startActivity(Intent(this, PaymentActivity::class.java))
            }
        }
        findViewById<Button>(R.id.buttonOpenChat).setOnClickListener {
            startActivity(Intent(this, ChatActivity::class.java))
        }
        findViewById<Button>(R.id.buttonOpenPayment).setOnClickListener {
            startActivity(Intent(this, PaymentActivity::class.java))
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_language, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.action_language) {
            // Для проверки, вызывается ли обработчик
            // Toast.makeText(this, "Нажал на иконку языка", Toast.LENGTH_SHORT).show()
            showLanguageDialog()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    private fun showLanguageDialog() {
        val currentLang = getCurrentLanguage()
        val langCodes = languages.keys.toTypedArray()
        val langNames = languages.values.toTypedArray()
        val checkedItem = langCodes.indexOf(currentLang)
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.choose_language))
            .setSingleChoiceItems(langNames, checkedItem) { dialog, which ->
                val selectedLang = langCodes[which]
                setLanguage(selectedLang)
                dialog.dismiss()
                recreate()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun setLanguage(langCode: String) {
        val prefs = getSharedPreferences("user_prefs", Context.MODE_PRIVATE)
        prefs.edit().putString("app_language", langCode).apply()
        applyLocale(langCode)
    }

    private fun getCurrentLanguage(): String {
        val prefs = getSharedPreferences("user_prefs", Context.MODE_PRIVATE)
        return prefs.getString("app_language", Locale.getDefault().language) ?: "ru"
    }

    private fun applyLocale(langCode: String) {
        val locale = Locale(langCode)
        Locale.setDefault(locale)
        val config = resources.configuration
        config.setLocale(locale)
        @Suppress("DEPRECATION")
        resources.updateConfiguration(config, resources.displayMetrics)
    }

    private fun applySavedLocale() {
        val langCode = getCurrentLanguage()
        applyLocale(langCode)
    }

    private fun hasAccess(): Boolean {
        val prefs = getSharedPreferences("user_prefs", Context.MODE_PRIVATE)
        val isLifetime = prefs.getBoolean("isLifetime", false)
        val subscriptionEnd = prefs.getLong("subscriptionEndDate", 0L)
        return isLifetime || (subscriptionEnd > System.currentTimeMillis())
    }
}
