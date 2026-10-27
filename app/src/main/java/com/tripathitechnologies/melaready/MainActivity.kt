package com.tripathitechnologies.melaready

import android.Manifest
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.webkit.ValueCallback
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import com.tripathitechnologies.melaready.domain.GeoFix
import com.tripathitechnologies.melaready.ui.MelaApp

class MainActivity : ComponentActivity() {
    private val model: AppState by viewModels()
    private var geo by mutableStateOf<GeoFix?>(null)
    private var locating by mutableStateOf(false)
    private var locationMessage by mutableStateOf<String?>(null)
    private var microphoneGranted by mutableStateOf(false)
    private var fileCallback: ValueCallback<Array<Uri>>? = null
    private val handler = Handler(Looper.getMainLooper())
    private var listener: LocationListener? = null
    private val timeout = Runnable { finishLocation(null, message("No fix received. Check location settings and try outdoors.", "लोकेशन नहीं मिली। सेटिंग जाँचें और खुले स्थान में दोबारा कोशिश करें।")) }
    private val permissions = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        if (result.values.any { it }) requestFix() else locationMessage = message("Location permission was not granted. You can still use the other tools.", "लोकेशन की अनुमति नहीं मिली। बाकी सुविधाएँ उपलब्ध हैं।")
    }
    private val microphonePermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        microphoneGranted = it
    }
    private val profilePhotoPicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        fileCallback?.onReceiveValue(uri?.let { arrayOf(it) }); fileCallback = null
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState); enableEdgeToEdge()
        microphoneGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        setContent { MelaApp(model, geo, locating, locationMessage, microphoneGranted, ::requestFix, { geo = null; locationMessage = null }, ::requestMicrophone, ::pickProfilePhoto) }
    }
    private fun requestMicrophone() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) microphoneGranted = true
        else microphonePermission.launch(Manifest.permission.RECORD_AUDIO)
    }
    private fun pickProfilePhoto(callback: ValueCallback<Array<Uri>>) {
        fileCallback?.onReceiveValue(null); fileCallback = callback; profilePhotoPicker.launch(arrayOf("image/jpeg"))
    }
    private fun message(en: String, hi: String) = if (model.workspace.hindi) hi else en
    private fun requestFix() {
        if (locating) return
        val fine = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!fine && !coarse) { permissions.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)); return }
        val manager = getSystemService(LocationManager::class.java)
        val providers = manager.getProviders(true).filter { it == LocationManager.NETWORK_PROVIDER || (fine && it == LocationManager.GPS_PROVIDER) }
        if (providers.isEmpty()) { locationMessage = message("Enable device location, then try again.", "डिवाइस की लोकेशन चालू करके दोबारा कोशिश करें।"); return }
        locating = true; locationMessage = null
        val callback = object : LocationListener {
            override fun onLocationChanged(location: Location) { finishLocation(location, null) }
            @Deprecated("Legacy callback") override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
            override fun onProviderEnabled(provider: String) {}
            override fun onProviderDisabled(provider: String) {}
        }
        listener = callback
        try {
            providers.forEach { manager.requestLocationUpdates(it, 0L, 0f, callback, Looper.getMainLooper()) }
            handler.postDelayed(timeout, 20_000)
        } catch (_: SecurityException) { finishLocation(null, message("Permission changed. Please try again.", "अनुमति बदल गई। दोबारा कोशिश करें।")) }
        catch (_: Exception) { finishLocation(null, message("Location is unavailable on this device.", "इस डिवाइस पर लोकेशन उपलब्ध नहीं है।")) }
    }
    private fun finishLocation(location: Location?, error: String?) {
        handler.removeCallbacks(timeout)
        listener?.let { getSystemService(LocationManager::class.java).removeUpdates(it) }
        listener = null; locating = false
        if (location != null) geo = GeoFix(location.latitude, location.longitude, if (location.hasAccuracy()) location.accuracy else null, location.time, location.elapsedRealtimeNanos)
        if (error != null) locationMessage = error
    }
    override fun onStop() { finishLocation(null, null); super.onStop() }
    override fun onDestroy() { fileCallback?.onReceiveValue(null); fileCallback = null; finishLocation(null, null); super.onDestroy() }
}
