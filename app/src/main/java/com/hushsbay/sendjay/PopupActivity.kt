package com.hushsbay.sendjay

import android.app.Activity
import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Uri
import android.net.UrlQuerySanitizer
import android.os.Bundle
import android.os.Environment
import android.view.*
import android.webkit.*
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.gson.JsonObject
import com.hushsbay.sendjay.data.RxEvent
import com.hushsbay.sendjay.common.*
import com.hushsbay.sendjay.common.UserInfo
import com.hushsbay.sendjay.common.Util
import io.reactivex.disposables.Disposable
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.activity_popup.*
import kotlinx.android.synthetic.main.activity_popup.btnRetry
import kotlinx.android.synthetic.main.activity_popup.txtRmks
import kotlinx.android.synthetic.main.activity_popup.txtUrl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.apache.log4j.Logger
import org.json.JSONObject
import java.net.URLDecoder

class PopupActivity : AppCompatActivity() {

    private lateinit var curContext: Activity
    private lateinit var logger: Logger
    private lateinit var connManager: ConnectivityManager
    private lateinit var uInfo: UserInfo
    private var disposableMsg: Disposable? = null
    private var disposableMain: Disposable? = null

    private lateinit var authJson: JsonObject //Gson

    var gOrigin = ""
    var gObjStr = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_popup)
        logger = LogHelper.getLogger(applicationContext, this::class.simpleName)
        WebView.setWebContentsDebuggingEnabled(true)
        curContext = this@PopupActivity
        connManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        uInfo = UserInfo(curContext)
        //1) origin = "/popup?type=image&msgid=" + msgid + "&body=" + body, objStr = "" : from jay_chat.js
        gOrigin = intent.getStringExtra("origin")!!
        gObjStr = intent.getStringExtra("objStr")!!
        var popup_version = KeyChain.get(curContext, Const.KC_WEBVIEW_POPUP_VERSION) ?: ""
        if (popup_version.startsWith("clear_cache")) {
            wvPopup.clearCache(true)
            wvPopup.clearHistory()
            popup_version = popup_version.replace("clear_cache", "")
            KeyChain.set(curContext, Const.KC_WEBVIEW_POPUP_VERSION, popup_version)
        }
        btnRetry.setOnClickListener {
            procAutoLogin() { setupWebViewPopup(gOrigin) }
        }
        btnSave.setOnClickListener {
            Util.loadUrl(wvPopup, "save")
        }
        procAutoLogin() { setupWebViewPopup(gOrigin) }
    }

    override fun onDestroy() {
        super.onDestroy()
        disposableMsg?.dispose()
        disposableMain?.dispose()
    }

    private fun procAutoLogin(callback: () -> Unit = {}) {
        val logTitle = object{}.javaClass.enclosingMethod?.name!!
        CoroutineScope(Dispatchers.Main).launch {
            try {
                val autoLogin = KeyChain.get(curContext, Const.KC_AUTOLOGIN) ?: ""
                if (autoLogin == "Y") {
                    authJson = HttpFuel.get(curContext, "${Const.DIR_ROUTE}/login/verify").await()
                    if (authJson.get("code").asString == Const.RESULT_OK) {
                        uInfo = UserInfo(curContext, authJson)
                        callback()
                    } else {
                        Util.alert(curContext, "Login Error : ${authJson.get("msg").asString}", logTitle)
                    }
                } else {
                    Util.alert(curContext, "Login needed", logTitle)
                }
            } catch (e: Exception) {
                logger.error("$logTitle: ${e.toString()}")
                Util.procException(curContext, e, logTitle)
            }
        }
    }

    private fun toggleDispRetry(show: Boolean) {
        if (show) {
            wvPopup.visibility = View.GONE
            btnRetry.visibility = View.VISIBLE
            txtRmks.visibility = View.VISIBLE
            txtUrl.visibility = View.VISIBLE
        } else {
            wvPopup.visibility = View.VISIBLE
            btnRetry.visibility = View.GONE
            txtRmks.visibility = View.GONE
            txtUrl.visibility = View.GONE
        }
    }

    private fun setupWebViewPopup(urlStr: String?=null) {
        Util.setupWebView(curContext, connManager, wvPopup)
        toggleDispRetry(false)
        wvPopup.settings.supportZoom() //meta name="viewport" content=~ setting needed
        wvPopup.settings.builtInZoomControls = true
        wvPopup.addJavascriptInterface(WebInterfacePopup(), "AndroidPopup")
        if (urlStr == null) return
        wvPopup.webChromeClient = object: WebChromeClient() {
            override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean { //return super.onConsoleMessage(consoleMessage)
                consoleMessage?.apply {
                    Util.procConsoleMsg(curContext, message() + "\n" + sourceId(), "wvPopup")
                }
                return true
            }
            //Belows are settings for fullscreen video in webview.
            //android:configChanges="keyboardHidden|orientation|screenSize" needed in AndroidManifest.xml
            //https://kutar37.tistory.com/entry/Android-webview%EC%97%90%EC%84%9C-HTML-video-%EC%A0%84%EC%B2%B4%ED%99%94%EB%A9%B4-%EC%9E%AC%EC%83%9D
            private var mCustomView: View? = null
            private var mCustomViewCallback: CustomViewCallback? = null
            private var mOriginalOrientation = 0
            private var mFullscreenContainer: FrameLayout? = null
            private val COVER_SCREEN_PARAMS = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            override fun onShowCustomView(view: View?, callback: CustomViewCallback) {
                if (mCustomView != null) {
                    callback.onCustomViewHidden()
                    return
                }
                mOriginalOrientation = curContext.requestedOrientation
                val decor = curContext.window.decorView as FrameLayout
                mFullscreenContainer = FullscreenHolder(curContext)
                (mFullscreenContainer as FullscreenHolder).addView(view, COVER_SCREEN_PARAMS)
                decor.addView(mFullscreenContainer, COVER_SCREEN_PARAMS)
                mCustomView = view
                setFullscreen(true)
                mCustomViewCallback = callback
                super.onShowCustomView(view, callback)
            }
            override fun onShowCustomView(view: View?, requestedOrientation: Int, callback: CustomViewCallback) {
                this.onShowCustomView(view, callback)
            }
            override fun onHideCustomView() {
                if (mCustomView == null) return
                setFullscreen(false)
                val decor = curContext.window.decorView as FrameLayout
                decor.removeView(mFullscreenContainer)
                mFullscreenContainer = null
                mCustomView = null
                mCustomViewCallback!!.onCustomViewHidden()
                curContext.requestedOrientation = mOriginalOrientation
            }
            private fun setFullscreen(enabled: Boolean) {
                val win: Window = curContext.window
                val winParams: WindowManager.LayoutParams = win.getAttributes()
                val bits = WindowManager.LayoutParams.FLAG_FULLSCREEN
                if (enabled) {
                    winParams.flags = winParams.flags or bits
                } else {
                    winParams.flags = winParams.flags and bits.inv()
                    mCustomView?.setSystemUiVisibility(View.SYSTEM_UI_FLAG_VISIBLE)
                }
                win.attributes = winParams
            }
            inner class FullscreenHolder(ctx: Context) : FrameLayout(ctx) {
                override fun onTouchEvent(evt: MotionEvent): Boolean {
                    return true
                }
                init {
                    setBackgroundColor(ContextCompat.getColor(ctx, android.R.color.black))
                }
            }
        }
        wvPopup.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val urlStr = request!!.url.toString() //if (!urlStr.contains(Const.PAGE_MAIN)) return false //ignore dummy page
                view!!.loadUrl(urlStr)
                return true //return super.shouldOverrideUrlLoading(view, request)
            }
        }
        Util.setDownloadListener(curContext, wvPopup)
        val urlStr1 = if (urlStr.startsWith(Const.DIR_PUBLIC)) urlStr.substring(Const.DIR_PUBLIC.length) else urlStr //check /jay~
        wvPopup.loadUrl(KeyChain.get(curContext, Const.KC_MODE_PUBLIC) + urlStr1)
    }

    inner class WebInterfacePopup {

        @JavascriptInterface
        fun procAfterOpenPopup() {
            val logTitle = object{}.javaClass.enclosingMethod?.name!!
            CoroutineScope(Dispatchers.Main).launch {
                try {
                    disposableMsg?.dispose()
                    disposableMsg = Util.procRxMsg(curContext)
                    disposableMain?.dispose()
                    disposableMain = RxToDown.subscribe<RxEvent>().subscribe { //disposable = RxBus.subscribe<RxEvent>().observeOn(AndroidSchedulers.mainThread()) //to receive the event on main thread
                        CoroutineScope(Dispatchers.Main).launch {
                            var param: JSONObject?= null
                            try {
                                param = it.data as JSONObject
                                Util.loadUrlJson(wvPopup, "getFromWebViewSocket", param)
                            } catch (e: Exception) {
                                val msg = "${e.toString()}\n${it.ev}\n${param.toString()}"
                                logger.error("$logTitle: $msg")
                                Util.procExceptionStr(curContext, msg, logTitle)
                            }
                        }
                    }
                    val obj = Util.getStrObjFromUserInfo(uInfo)
                    val objStr = """{
                        'added' : '${Util.decodeUrl(gObjStr)}'
                    }"""
                    Util.loadUrl(wvPopup, "startFromWebView", obj, objStr)
                } catch (e1: Exception) {
                    logger.error("$logTitle: ${e1.toString()}")
                    Util.procException(curContext, e1, logTitle)
                }
            }
        }

        @JavascriptInterface
        fun close() { //from index.html (for inviting)
            CoroutineScope(Dispatchers.Main).launch {
                setResult(RESULT_CANCELED)
                finish()
            }
        }

        @JavascriptInterface
        fun invite(useridArr: String, usernmArr: String) {
            val logTitle = object{}.javaClass.enclosingMethod?.name!!
            CoroutineScope(Dispatchers.Main).launch {
                try {
                    val rIntent = Intent()
                    rIntent.putExtra("userids", useridArr)
                    rIntent.putExtra("usernms", usernmArr)
                    setResult(RESULT_OK, rIntent)
                    finish()
                } catch (e: Exception) {
                    logger.error("$logTitle: ${e.toString()}")
                    Util.procException(curContext, e, logTitle)
                }
            }
        }

        @JavascriptInterface
        fun toggleSaveVisible(show: Boolean) { //from popup.html
            CoroutineScope(Dispatchers.Main).launch {
                if (show) {
                    btnSave.visibility = View.VISIBLE
                } else {
                    btnSave.visibility = View.GONE
                }
            }
        }

    }

}