package com.example.myapplication

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.speech.tts.TextToSpeech
import android.text.TextUtils
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.widget.Autocomplete
import com.google.android.libraries.places.widget.model.AutocompleteActivityMode
import java.io.File
import java.io.FileOutputStream
import java.util.*

class MapsActivity : AppCompatActivity(), OnMapReadyCallback, TextToSpeech.OnInitListener {

    private lateinit var mMap: GoogleMap
    private val LOCATION_PERMISSION_REQUEST_CODE = 1
    private val PREFS_NAME = "map_prefs"
    private val MARKERS_KEY = "markers"
    private val PHOTOS_KEY = "photos"

    private val markersList = mutableListOf<LatLng>()
    private val markerObjects = mutableListOf<Marker>()
    private val markerPhotos = mutableMapOf<String, String>()

    private var tts: TextToSpeech? = null
    private var lastMarkerForPhoto: Marker? = null

    private val REQUEST_PHOTO = 1001
    private val AUTOCOMPLETE_REQUEST_CODE = 1010

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_maps)

        // Инициализация Places API, если не инициализирована
        if (!Places.isInitialized()) {
            Places.initialize(applicationContext, getString(R.string.google_maps_key))
        }

        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)

        tts = TextToSpeech(this, this)

        findViewById<FloatingActionButton>(R.id.btnLanguage).setOnClickListener {
            showLanguageDialog()
        }
        findViewById<FloatingActionButton>(R.id.btnAssistant).setOnClickListener {
            startActivity(Intent(this, ChatActivity::class.java))
        }
        findViewById<FloatingActionButton>(R.id.btnExit).setOnClickListener {
            finish()
        }

        val etSearch = findViewById<EditText>(R.id.etSearch)
        etSearch.setOnEditorActionListener { v, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH || actionId == EditorInfo.IME_ACTION_DONE) {
                val query = v.text.toString()
                searchAndMove(query)
                true
            } else false
        }

        etSearch.setOnClickListener {
            launchAutocomplete()
        }
    }

    // Функция выбора языка с 8 языками
    private fun showLanguageDialog() {
        val languages = arrayOf(
            "Русский", "English", "Українська",
            "Deutsch", "Français", "Español", "Polski", "Português"
        )
        val codes = arrayOf("ru", "en", "uk", "de", "fr", "es", "pl", "pt")
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.choose_language))
            .setItems(languages) { _, which ->
                setLocale(codes[which])
            }
            .show()
    }

    private fun setLocale(language: String) {
        val locale = Locale(language)
        Locale.setDefault(locale)
        val config = resources.configuration
        config.setLocale(locale)
        @Suppress("DEPRECATION")
        resources.updateConfiguration(config, resources.displayMetrics)
        recreate()
    }

    override fun onDestroy() {
        tts?.stop()
        tts?.shutdown()
        super.onDestroy()
    }

    override fun onInit(status: Int) {
        tts?.language = Locale.getDefault()
    }

    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap
        val yerevan = LatLng(40.1792, 44.4991)
        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(yerevan, 12f))
        enableMyLocation()

        val savedMarkers = loadMarkers()
        markersList.clear()
        markersList.addAll(savedMarkers)
        markerObjects.clear()
        markerPhotos.clear()
        markerPhotos.putAll(loadPhotos())

        for (latLng in savedMarkers) {
            val marker = mMap.addMarker(
                MarkerOptions()
                    .position(latLng)
                    .title(getString(R.string.new_marker_title))
            )
            marker?.let { markerObjects.add(it) }
        }

        mMap.setOnMapClickListener { latLng ->
            var newLatLng = latLng
            while (isMarkerNear(newLatLng)) {
                newLatLng = LatLng(newLatLng.latitude + 0.00015, newLatLng.longitude)
            }
            val marker = mMap.addMarker(
                MarkerOptions()
                    .position(newLatLng)
                    .title(getString(R.string.new_marker_title))
            )
            markersList.add(newLatLng)
            marker?.let { markerObjects.add(it) }
            saveMarkers(markersList)
        }

        mMap.setOnMarkerClickListener { marker ->
            val markerTitle = marker.title ?: getString(R.string.new_marker_title)
            val description = getString(R.string.new_marker_title)
            val markerKey = "${marker.position.latitude},${marker.position.longitude}"
            val photoPath = markerPhotos[markerKey]
            val photoBitmap = if (photoPath != null && File(photoPath).exists())
                BitmapFactory.decodeFile(photoPath) else null

            val dialogView = layoutInflater.inflate(R.layout.dialog_marker_info, null)
            val ivPhoto = dialogView.findViewById<ImageView>(R.id.ivMarkerPhoto)
            val tvMsg = dialogView.findViewById<TextView>(R.id.tvMarkerMsg)
            tvMsg.text = getString(R.string.delete_marker_confirm)

            if (photoBitmap != null) {
                ivPhoto.setImageBitmap(photoBitmap)
                ivPhoto.visibility = View.VISIBLE
            } else {
                ivPhoto.visibility = View.GONE
            }

            val dialog = AlertDialog.Builder(this)
                .setTitle(markerTitle)
                .setView(dialogView)
                .setPositiveButton(getString(R.string.listen_marker)) { _, _ ->
                    val text = "$markerTitle. $description"
                    tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "marker_tts")
                }
                .setNegativeButton(getString(R.string.add_photo)) { _, _ ->
                    lastMarkerForPhoto = marker
                    val pickIntent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
                    startActivityForResult(pickIntent, REQUEST_PHOTO)
                }
                .setNeutralButton(getString(R.string.navigate)) { _, _ ->
                    openNavigationTo(marker.position.latitude, marker.position.longitude)
                }
                .setCancelable(true)
                .create()

            dialog.show()
            true
        }

        mMap.setOnMapLongClickListener { latLng ->
            val markerToRemove = markerObjects.find { it.position == latLng }
            if (markerToRemove != null) {
                markerToRemove.remove()
                markersList.removeAll { it.latitude == latLng.latitude && it.longitude == latLng.longitude }
                markerObjects.remove(markerToRemove)
                markerPhotos.remove("${latLng.latitude},${latLng.longitude}")
                saveMarkers(markersList)
                savePhotos(markerPhotos)
                Toast.makeText(this, getString(R.string.marker_deleted), Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun openNavigationTo(lat: Double, lng: Double) {
        try {
            val gmmIntentUri = Uri.parse("google.navigation:q=$lat,$lng")
            val mapIntent = Intent(Intent.ACTION_VIEW, gmmIntentUri)
            mapIntent.setPackage("com.google.android.apps.maps")
            startActivity(mapIntent)
        } catch (e: Exception) {
            val geoIntent = Intent(
                Intent.ACTION_VIEW,
                Uri.parse("geo:$lat,$lng?q=$lat,$lng")
            )
            startActivity(geoIntent)
        }
    }

    // --- Places Autocomplete (Google Places API) ---
    private fun launchAutocomplete() {
        val fields = listOf(Place.Field.ID, Place.Field.NAME, Place.Field.LAT_LNG)
        val intent = Autocomplete.IntentBuilder(
            AutocompleteActivityMode.OVERLAY, fields
        ).build(this)
        startActivityForResult(intent, AUTOCOMPLETE_REQUEST_CODE)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == AUTOCOMPLETE_REQUEST_CODE) {
            if (resultCode == Activity.RESULT_OK && data != null) {
                val place = Autocomplete.getPlaceFromIntent(data)
                place.latLng?.let { latLng ->
                    mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, 16f))
                    val marker = mMap.addMarker(
                        MarkerOptions().position(latLng).title(place.name ?: getString(R.string.new_marker_title))
                    )
                    markersList.add(latLng)
                    marker?.let { markerObjects.add(it) }
                    saveMarkers(markersList)
                }
                val etSearch = findViewById<EditText>(R.id.etSearch)
                etSearch.setText(place.name)
            }
        }
        if (requestCode == REQUEST_PHOTO && resultCode == Activity.RESULT_OK && data != null) {
            val selectedImage: Uri? = data.data
            val marker = lastMarkerForPhoto ?: return
            val markerKey = "${marker.position.latitude},${marker.position.longitude}"
            if (selectedImage != null) {
                val inputStream = contentResolver.openInputStream(selectedImage)
                val file = File(cacheDir, "marker_${markerKey.hashCode()}.jpg")
                val outputStream = FileOutputStream(file)
                inputStream?.copyTo(outputStream)
                inputStream?.close()
                outputStream.close()
                markerPhotos[markerKey] = file.absolutePath
                savePhotos(markerPhotos)
                Toast.makeText(this, getString(R.string.photo_attached), Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun searchAndMove(query: String) {
        if (TextUtils.isEmpty(query)) return
        val geocoder = android.location.Geocoder(this)
        val results = geocoder.getFromLocationName(query, 1)
        if (!results.isNullOrEmpty()) {
            val address = results[0]
            val latLng = LatLng(address.latitude, address.longitude)
            mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, 16f))
            val marker = mMap.addMarker(
                MarkerOptions().position(latLng).title(query)
            )
            markersList.add(latLng)
            marker?.let { markerObjects.add(it) }
            saveMarkers(markersList)
        } else {
            Toast.makeText(this, getString(R.string.place_not_found), Toast.LENGTH_SHORT).show()
        }
    }

    private fun enableMyLocation() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
            && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                LOCATION_PERMISSION_REQUEST_CODE
            )
        } else {
            mMap.isMyLocationEnabled = true
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                    mMap.isMyLocationEnabled = true
                }
            }
        }
    }

    private fun isMarkerNear(latLng: LatLng, thresholdMeters: Double = 15.0): Boolean {
        for (marker in markersList) {
            val results = FloatArray(1)
            android.location.Location.distanceBetween(
                latLng.latitude, latLng.longitude,
                marker.latitude, marker.longitude,
                results
            )
            if (results[0] < thresholdMeters) return true
        }
        return false
    }

    private fun saveMarkers(markers: List<LatLng>) {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = Gson().toJson(markers)
        prefs.edit().putString(MARKERS_KEY, json).apply()
    }

    private fun loadMarkers(): List<LatLng> {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = prefs.getString(MARKERS_KEY, null)
        return if (json != null) {
            val type = object : TypeToken<List<LatLng>>() {}.type
            Gson().fromJson(json, type)
        } else {
            emptyList()
        }
    }

    private fun savePhotos(photos: Map<String, String>) {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = Gson().toJson(photos)
        prefs.edit().putString(PHOTOS_KEY, json).apply()
    }

    private fun loadPhotos(): Map<String, String> {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = prefs.getString(PHOTOS_KEY, null)
        return if (json != null) {
            val type = object : TypeToken<Map<String, String>>() {}.type
            Gson().fromJson(json, type)
        } else {
            emptyMap()
        }
    }
}
