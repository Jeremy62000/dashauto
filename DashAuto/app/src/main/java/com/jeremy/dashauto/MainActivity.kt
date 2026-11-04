package com.jeremy.dashauto

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.media.AudioManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.util.Base64
import android.view.KeyEvent
import android.webkit.JavascriptInterface
import android.webkit.WebView
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream

class MainActivity : Activity() {

    private lateinit var web: WebView
    private var gpsOn = false

    // Sur Android < 11 toutes les méthodes de l'interface doivent être implémentées
    private val locListener = object : LocationListener {
        override fun onLocationChanged(loc: Location) {
            val speed = if (loc.hasSpeed()) loc.speed else 0f
            val js = "window.onLoc&&window.onLoc($speed,${loc.bearing},${loc.latitude},${loc.longitude},${loc.accuracy})"
            runOnUiThread { web.evaluateJavascript(js, null) }
        }

        @Deprecated("Deprecated in Java")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {
        }

        override fun onProviderEnabled(provider: String) {}
        override fun onProviderDisabled(provider: String) {}
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        web = WebView(this)
        web.setBackgroundColor(Color.BLACK)
        web.settings.javaScriptEnabled = true
        web.settings.domStorageEnabled = true
        web.isVerticalScrollBarEnabled = false
        web.isHorizontalScrollBarEnabled = false
        web.overScrollMode = WebView.OVER_SCROLL_NEVER
        web.addJavascriptInterface(Bridge(), "Android")
        setContentView(web)
        web.loadUrl("file:///android_asset/index.html")
    }

    override fun onResume() {
        super.onResume()
        startGps()
        web.evaluateJavascript("window.onResumeApp && window.onResumeApp()", null)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        web.evaluateJavascript("window.onResumeApp && window.onResumeApp()", null)
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            (getSystemService(Context.LOCATION_SERVICE) as LocationManager).removeUpdates(locListener)
        } catch (_: Exception) {
        }
    }

    @SuppressLint("MissingPermission")
    private fun startGps() {
        if (gpsOn) return
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 1)
            return
        }
        try {
            val lm = getSystemService(Context.LOCATION_SERVICE) as LocationManager
            lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 0f, locListener)
            gpsOn = true
        } catch (_: Exception) {
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        startGps()
    }

    // Launcher : le bouton retour ne quitte pas l'accueil
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        web.evaluateJavascript("window.onBack && window.onBack()", null)
    }

    inner class Bridge {

        @JavascriptInterface
        fun getApps(): String {
            val pm = packageManager
            val query = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            val arr = JSONArray()
            pm.queryIntentActivities(query, 0)
                .filter { it.activityInfo.packageName != packageName }
                .sortedBy { it.loadLabel(pm).toString().lowercase() }
                .forEach { ri ->
                    val o = JSONObject()
                    o.put("name", ri.loadLabel(pm).toString())
                    o.put("pkg", ri.activityInfo.packageName)
                    o.put("icon", iconToBase64(ri.loadIcon(pm)))
                    arr.put(o)
                }
            return arr.toString()
        }

        @JavascriptInterface
        fun launch(pkg: String) {
            runOnUiThread {
                val li = packageManager.getLaunchIntentForPackage(pkg)
                if (li != null) {
                    li.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    startActivity(li)
                }
            }
        }

        @JavascriptInterface
        fun openSettings() {
            runOnUiThread {
                val i = Intent(Settings.ACTION_SETTINGS)
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(i)
            }
        }

        // Ouvre la fiche Play Store d'une app absente de l'écran
        @JavascriptInterface
        fun openStore(pkg: String) {
            runOnUiThread {
                try {
                    startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$pkg"))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                } catch (_: Exception) {
                    startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$pkg"))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }
            }
        }

        // Touches média : 88 = précédent, 85 = lecture/pause, 87 = suivant
        @JavascriptInterface
        fun media(code: Int) {
            val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code))
            am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, code))
        }
    }

    private fun iconToBase64(d: Drawable): String {
        val size = 96
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        d.setBounds(0, 0, size, size)
        d.draw(canvas)
        val out = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
        return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    }
}
