package com.archive.tv

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.text.InputType
import android.view.InputDevice
import android.view.KeyEvent as KE
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import kotlin.math.max
import kotlin.math.min

class MainActivity : AppCompatActivity() {

    companion object {
        const val HOME = "https://archive.org/"
        // The "closed" part: only these sites can be opened.
        val ALLOWED_HOSTS = listOf("archive.org", "openlibrary.org")
    }

    private lateinit var webView: WebView
    private lateinit var cursor: CursorView
    private lateinit var progress: ProgressBar
    private lateinit var menuBar: View
    private lateinit var menuTitle: TextView
    private lateinit var fullscreenHost: FrameLayout

    private val handler = Handler(Looper.getMainLooper())
    private val prefs by lazy { getSharedPreferences("settings", MODE_PRIVATE) }

    private var cursorMode = true
    private var darkPages = true
    private var textZoom = 100
    private var lastUrl = HOME
    private var customView: View? = null
    private var customCallback: WebChromeClient.CustomViewCallback? = null
    private var lastBackPress = 0L
    private var centerLong = false
    private val longPress = Runnable { centerLong = true; showMenu() }

    // ---------------------------------------------------------------- setup

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContentView(R.layout.activity_main)

        webView = findViewById(R.id.webView)
        cursor = findViewById(R.id.cursor)
        progress = findViewById(R.id.progress)
        menuBar = findViewById(R.id.menuBar)
        menuTitle = findViewById(R.id.menuTitle)
        fullscreenHost = findViewById(R.id.fullscreenHost)

        cursorMode = prefs.getBoolean("cursor", true)
        darkPages = prefs.getBoolean("dark", true)
        textZoom = prefs.getInt("zoom", 100)

        setupWebView()
        setupMenu()
        webView.loadUrl(prefs.getString("last", HOME) ?: HOME)

        if (!prefs.getBoolean("hint_shown", false)) {
            toast("Hold OK (or press Menu) for the toolbar")
            prefs.edit().putBoolean("hint_shown", true).apply()
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        webView.setBackgroundColor(Color.parseColor("#0E0F13"))
        with(webView.settings) {
            javaScriptEnabled = true
            domStorageEnabled = true
            mediaPlaybackRequiresUserGesture = false
            useWideViewPort = true
            loadWithOverviewMode = true
            builtInZoomControls = false
            displayZoomControls = false
            setSupportMultipleWindows(false)
            allowFileAccess = false
            allowContentAccess = false
            textZoom = this@MainActivity.textZoom
            userAgentString = userAgentString.replace(" Mobile", "")
        }
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)
        applyDark()

        webView.setDownloadListener { _, _, _, _, _ -> toast("Downloads are disabled") }

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                if (!request.isForMainFrame || isAllowed(request.url)) return false
                toast("Blocked: ${request.url.host ?: "external link"}")
                return true
            }

            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                if (request.isForMainFrame) showError(request.url.toString())
            }

            override fun onPageFinished(view: WebView, url: String?) {
                if (url != null && url.startsWith("http")) lastUrl = url
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView, newProgress: Int) {
                progress.progress = newProgress
                progress.visibility = if (newProgress < 100) View.VISIBLE else View.GONE
            }

            override fun onShowCustomView(view: View, callback: CustomViewCallback) {
                if (customView != null) { callback.onCustomViewHidden(); return }
                hideMenu()
                customView = view
                customCallback = callback
                fullscreenHost.addView(view, FrameLayout.LayoutParams(-1, -1))
                fullscreenHost.visibility = View.VISIBLE
                webView.visibility = View.INVISIBLE
                updateCursorVisibility()
            }

            override fun onHideCustomView() = exitFullscreen()

            override fun getDefaultVideoPoster(): Bitmap? =
                Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        }
    }

    private fun setupMenu() {
        fun bind(id: Int, keepOpen: Boolean = false, action: () -> Unit) {
            findViewById<Button>(id).setOnClickListener { action(); if (!keepOpen) hideMenu() }
        }
        bind(R.id.btnHome) { webView.loadUrl(HOME) }
        bind(R.id.btnBack) { if (webView.canGoBack()) webView.goBack() }
        bind(R.id.btnForward) { if (webView.canGoForward()) webView.goForward() }
        bind(R.id.btnReload) { reload() }
        bind(R.id.btnSearch) { showSearch() }
        bind(R.id.btnTextMinus, true) { zoomText(-10) }
        bind(R.id.btnTextPlus, true) { zoomText(10) }
        bind(R.id.btnCursor, true) { toggleCursor() }
        bind(R.id.btnDark, true) { toggleDark() }
        bind(R.id.btnExit) { finish() }
        updateToggleLabels()
        updateCursorVisibility()
    }

    // ---------------------------------------------------------- remote keys

    override fun dispatchKeyEvent(e: KE): Boolean {
        val code = e.keyCode
        val down = e.action == KE.ACTION_DOWN
        val first = down && e.repeatCount == 0

        if (handleMediaKey(e)) return true
        if (customView != null) return fullscreenKey(e) || super.dispatchKeyEvent(e)

        if (menuBar.visibility == View.VISIBLE) {
            if (code == KE.KEYCODE_BACK || code == KE.KEYCODE_MENU) {
                if (!down) hideMenu()
                return true
            }
            return super.dispatchKeyEvent(e)
        }

        when (code) {
            KE.KEYCODE_BACK -> { if (!down) onBackAction(); return true }
            KE.KEYCODE_MENU, KE.KEYCODE_SETTINGS, KE.KEYCODE_INFO,
            KE.KEYCODE_GUIDE, KE.KEYCODE_BUTTON_START -> { if (first) showMenu(); return true }
            KE.KEYCODE_PROG_RED -> { if (first) webView.loadUrl(HOME); return true }
            KE.KEYCODE_PROG_GREEN -> { if (first) reload(); return true }
            KE.KEYCODE_PROG_YELLOW -> { if (first) toggleCursor(); return true }
            KE.KEYCODE_PROG_BLUE, KE.KEYCODE_SEARCH -> { if (first) showSearch(); return true }
            KE.KEYCODE_CHANNEL_UP, KE.KEYCODE_PAGE_UP -> { if (down) webView.pageUp(false); return true }
            KE.KEYCODE_CHANNEL_DOWN, KE.KEYCODE_PAGE_DOWN -> { if (down) webView.pageDown(false); return true }
            KE.KEYCODE_DPAD_UP, KE.KEYCODE_DPAD_DOWN, KE.KEYCODE_DPAD_LEFT, KE.KEYCODE_DPAD_RIGHT -> {
                if (!cursorMode) return super.dispatchKeyEvent(e)
                if (down) moveCursor(code, e.repeatCount)
                return true
            }
            KE.KEYCODE_DPAD_CENTER, KE.KEYCODE_ENTER, KE.KEYCODE_NUMPAD_ENTER, KE.KEYCODE_BUTTON_A -> {
                if (centerKey(e)) return true
            }
        }
        return super.dispatchKeyEvent(e)
    }

    /** OK button: tap = click, long press = toolbar. Returns true if consumed. */
    private fun centerKey(e: KE): Boolean {
        if (e.action == KE.ACTION_DOWN) {
            if (e.repeatCount == 0) { centerLong = false; handler.postDelayed(longPress, 650) }
            return cursorMode
        }
        handler.removeCallbacks(longPress)
        if (centerLong) { centerLong = false; return true }
        if (cursorMode) { clickAtCursor(); return true }
        return false
    }

    private fun handleMediaKey(e: KE): Boolean {
        val js = when (e.keyCode) {
            KE.KEYCODE_MEDIA_PLAY_PAUSE -> "if(m.paused)m.play();else m.pause();"
            KE.KEYCODE_MEDIA_PLAY -> "m.play();"
            KE.KEYCODE_MEDIA_PAUSE, KE.KEYCODE_MEDIA_STOP -> "m.pause();"
            KE.KEYCODE_MEDIA_FAST_FORWARD, KE.KEYCODE_MEDIA_STEP_FORWARD -> seekJs(10)
            KE.KEYCODE_MEDIA_NEXT -> seekJs(30)
            KE.KEYCODE_MEDIA_REWIND, KE.KEYCODE_MEDIA_STEP_BACKWARD -> seekJs(-10)
            KE.KEYCODE_MEDIA_PREVIOUS -> seekJs(-30)
            else -> return false
        }
        if (e.action == KE.ACTION_DOWN) media(js)
        return true
    }

    private fun fullscreenKey(e: KE): Boolean {
        val down = e.action == KE.ACTION_DOWN
        when (e.keyCode) {
            KE.KEYCODE_BACK -> { if (!down) exitFullscreen(); return true }
            KE.KEYCODE_DPAD_LEFT -> { if (down) media(seekJs(-10)); return true }
            KE.KEYCODE_DPAD_RIGHT -> { if (down) media(seekJs(10)); return true }
            KE.KEYCODE_DPAD_CENTER, KE.KEYCODE_ENTER, KE.KEYCODE_NUMPAD_ENTER -> {
                if (down && e.repeatCount == 0) media("if(m.paused)m.play();else m.pause();")
                return true
            }
        }
        return false
    }

    private fun onBackAction() {
        if (webView.canGoBack()) {
            webView.goBack()
        } else {
            val now = SystemClock.uptimeMillis()
            if (now - lastBackPress < 2000) finish() else toast("Press Back again to exit")
            lastBackPress = now
        }
    }

    // --------------------------------------------------------------- cursor

    private fun moveCursor(code: Int, repeat: Int) {
        val base = max(webView.width, 1) / 150f
        val step = base * (1f + min(repeat, 16) * 0.35f)
        var dx = 0f
        var dy = 0f
        when (code) {
            KE.KEYCODE_DPAD_LEFT -> dx = -step
            KE.KEYCODE_DPAD_RIGHT -> dx = step
            KE.KEYCODE_DPAD_UP -> dy = -step
            KE.KEYCODE_DPAD_DOWN -> dy = step
        }
        cursor.moveBy(dx, dy)
        // Pushing against a screen edge scrolls the page.
        val edge = base * 2
        if (dy < 0 && cursor.cy <= edge) webView.scrollBy(0, (dy * 2).toInt())
        if (dy > 0 && cursor.cy >= webView.height - edge) webView.scrollBy(0, (dy * 2).toInt())
        if (dx < 0 && cursor.cx <= edge) webView.scrollBy((dx * 2).toInt(), 0)
        if (dx > 0 && cursor.cx >= webView.width - edge) webView.scrollBy((dx * 2).toInt(), 0)
    }

    private fun clickAtCursor() {
        val x = cursor.cx
        val y = cursor.cy
        val t = SystemClock.uptimeMillis()
        val down = MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, x, y, 0)
        down.source = InputDevice.SOURCE_TOUCHSCREEN
        webView.dispatchTouchEvent(down)
        down.recycle()
        handler.postDelayed({
            val up = MotionEvent.obtain(t, SystemClock.uptimeMillis(), MotionEvent.ACTION_UP, x, y, 0)
            up.source = InputDevice.SOURCE_TOUCHSCREEN
            webView.dispatchTouchEvent(up)
            up.recycle()
        }, 60)
        cursor.pulse()
    }

    private fun updateCursorVisibility() {
        val show = cursorMode && menuBar.visibility != View.VISIBLE && customView == null
        cursor.visibility = if (show) View.VISIBLE else View.GONE
    }

    // ----------------------------------------------------------------- menu

    private fun showMenu() {
        menuTitle.text = webView.title?.takeIf { it.isNotBlank() } ?: webView.url ?: ""
        menuBar.visibility = View.VISIBLE
        updateCursorVisibility()
        findViewById<Button>(R.id.btnHome).requestFocus()
    }

    private fun hideMenu() {
        menuBar.visibility = View.GONE
        updateCursorVisibility()
        webView.requestFocus()
    }

    private fun toggleCursor() {
        cursorMode = !cursorMode
        prefs.edit().putBoolean("cursor", cursorMode).apply()
        updateToggleLabels()
        updateCursorVisibility()
        toast(if (cursorMode) "Cursor mode on (D-pad moves a pointer)" else "Cursor mode off (D-pad jumps between links)")
    }

    private fun toggleDark() {
        darkPages = !darkPages
        prefs.edit().putBoolean("dark", darkPages).apply()
        updateToggleLabels()
        applyDark()
        reload()
    }

    @Suppress("DEPRECATION")
    private fun applyDark() {
        if (Build.VERSION.SDK_INT >= 29) {
            webView.settings.forceDark =
                if (darkPages) WebSettings.FORCE_DARK_ON else WebSettings.FORCE_DARK_OFF
        }
    }

    private fun updateToggleLabels() {
        findViewById<Button>(R.id.btnCursor).text = "Cursor: " + if (cursorMode) "ON" else "OFF"
        findViewById<Button>(R.id.btnDark).text = "Dark: " + if (darkPages) "ON" else "OFF"
    }

    private fun zoomText(delta: Int) {
        textZoom = (textZoom + delta).coerceIn(60, 220)
        webView.settings.textZoom = textZoom
        prefs.edit().putInt("zoom", textZoom).apply()
        toast("Text size $textZoom%")
    }

    private fun reload() = webView.loadUrl(webView.url ?: HOME)

    private fun showSearch() {
        val pad = (24 * resources.displayMetrics.density).toInt()
        val input = EditText(this).apply {
            hint = "Search the Internet Archive"
            setSingleLine()
            inputType = InputType.TYPE_CLASS_TEXT
            imeOptions = EditorInfo.IME_ACTION_SEARCH
            setTextColor(Color.WHITE)
            setHintTextColor(Color.parseColor("#9AA0B4"))
        }
        val box = FrameLayout(this).apply {
            setPadding(pad, pad / 2, pad, 0)
            addView(input)
        }
        val dialog = AlertDialog.Builder(this, R.style.DarkDialog)
            .setTitle("Search")
            .setView(box)
            .setPositiveButton("Search") { _, _ -> search(input.text.toString()) }
            .setNegativeButton("Cancel", null)
            .create()
        input.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                search(input.text.toString()); dialog.dismiss(); true
            } else false
        }
        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
        dialog.show()
    }

    private fun search(q: String) {
        if (q.isBlank()) return
        webView.loadUrl("https://archive.org/search?query=" + Uri.encode(q.trim()))
    }

    // ------------------------------------------------------------- helpers

    private fun isAllowed(uri: Uri): Boolean {
        val scheme = uri.scheme?.lowercase() ?: return false
        if (scheme == "data" || scheme == "about" || scheme == "blob") return true
        if (scheme != "https" && scheme != "http") return false
        val host = uri.host?.lowercase() ?: return false
        return ALLOWED_HOSTS.any { host == it || host.endsWith(".$it") }
    }

    private fun seekJs(seconds: Int) = "m.currentTime=Math.max(0,m.currentTime+($seconds));"

    /** Runs [js] against the first video/audio element found (including same-origin iframes). */
    private fun media(js: String) {
        webView.evaluateJavascript(
            "(function(){function f(d){var m=d.querySelector('video,audio');if(m)return m;" +
                "var s=d.querySelectorAll('iframe');for(var i=0;i<s.length;i++){try{var r=f(s[i].contentDocument);" +
                "if(r)return r}catch(e){}}return null}var m=f(document);if(!m)return;$js})()", null
        )
    }

    private fun exitFullscreen() {
        val cv = customView ?: return
        fullscreenHost.removeView(cv)
        fullscreenHost.visibility = View.GONE
        webView.visibility = View.VISIBLE
        customCallback?.onCustomViewHidden()
        customView = null
        customCallback = null
        updateCursorVisibility()
    }

    private fun showError(url: String) {
        val html = """<html><body style="background:#0E0F13;color:#E8EAF0;font-family:sans-serif;text-align:center;padding-top:18%">
            <h1>Can't reach the Internet Archive</h1>
            <p style="color:#9AA0B4;font-size:22px">Check your connection, then try again.</p>
            <a href="$url" style="display:inline-block;margin-top:24px;padding:16px 44px;border-radius:12px;background:#5B7CFF;color:#fff;font-size:26px;text-decoration:none">Try again</a>
            </body></html>"""
        webView.loadDataWithBaseURL(HOME, html, "text/html", "utf-8", url)
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    // ------------------------------------------------------------ lifecycle

    override fun onResume() { super.onResume(); webView.onResume() }

    override fun onPause() {
        prefs.edit().putString("last", lastUrl).apply()
        webView.onPause()
        super.onPause()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        webView.destroy()
        super.onDestroy()
    }
}
