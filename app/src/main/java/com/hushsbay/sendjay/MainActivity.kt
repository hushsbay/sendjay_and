package com.hushsbay.sendjay

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Uri
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.webkit.*
import android.webkit.WebView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import com.google.gson.JsonObject
import com.hushsbay.sendjay.common.*
import com.hushsbay.sendjay.data.RxEvent
import io.reactivex.disposables.Disposable
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.dialog_login.view.*
import kotlinx.coroutines.*
import org.apache.log4j.Logger
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.net.URL

//socket.io uses json(org.json.JSONObect). Fuel uses gson(com.google.gson.JsonObject)
//onCreate -> onStart -> onResume -> onPause -> onStop -> onDestroy
class MainActivity : AppCompatActivity() {

    companion object { //See ChatService.kt
        var isOnTop = true
        var stopServiceByLogout = false
    }

    private lateinit var curContext: Activity
    private lateinit var logger: Logger
    private lateinit var pm: PowerManager
    private lateinit var connManager: ConnectivityManager
    private lateinit var uInfo: UserInfo
    private var disposableMsg: Disposable? = null
    private var disposableMain: Disposable? = null
    private var disposableRoom: Disposable? = null
    private lateinit var imm: InputMethodManager
    private lateinit var keyboardVisibilityChecker: KeyboardVisibilityChecker

    private lateinit var authJson: JsonObject //Gson

    private var filePathCallbackMain: ValueCallback<Array<Uri?>>? = null //webview file chooser
    private var filePathCallbackRoom: ValueCallback<Array<Uri?>>? = null //webview file chooser
    private val FILE_RESULT_MAIN = 100 //webview file chooser
    private val FILE_RESULT_ROOM = 101 //webview file chooser

    private val MEMBER_RESULT = 300
    private val INVITE_RESULT = 400

    private val RETRY_DELAY = 3000L
    private val RETRY_TIMEOUT = 30000L

    private var isFromNoti = false
    private var isOnCreate = true
    private var mainLoaded = false
    private var roomLoaded = false
    private var retried = false
    private var gType = "" //for wvRoom
    private var gRoomid = "" //for wvRoom
    private var gOrigin = "" //for wvRoom
    private var gObjStr = "" //for wvRoom
    private var roomidForChatService = "" //for wvRoom
    private var msgidCopied = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        logger = LogHelper.getLogger(applicationContext, this::class.simpleName)
        wvMain.setBackgroundColor(0) //make it transparent (if not, white background will be shown)
        WebView.setWebContentsDebuggingEnabled(true) //for debug
        curContext = this@MainActivity
        NotiCenter(curContext, packageName) //kotlin invoke method : NotiCenter.invoke() //see ChatService.kt also
        isOnCreate = true
        stopServiceByLogout = false
        roomidForChatService = ""
        KeyChain.set(curContext, Const.KC_ROOMID_FOR_CHATSERVICE, "")
        KeyChain.set(curContext, Const.KC_SCREEN_STATE, "on")
        pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        connManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        keyboardVisibilityChecker = KeyboardVisibilityChecker(window, onShowKeyboard = { keyboardHeight ->
            if (roomidForChatService != "") Util.loadUrl(wvRoom, "scrollToBottomFromWebView")
        })
        keyboardVisibilityChecker = KeyboardVisibilityChecker(window, onHideKeyboard = { })
        btnRetry.setOnClickListener {
            if (ChatService.state != Const.ServiceState.RUNNING) {
                start()
            } else {
                CoroutineScope(Dispatchers.Main).launch {
                    procLogin(true) { //related with Reset Authentication
                        Util.connectSockWithCallback(curContext, connManager) {
                            if (it.get("code").asString != Const.RESULT_OK) {
                                Util.toast(curContext, it.get("msg").asString)
                                return@connectSockWithCallback
                            }
                            retried = true
                            if (roomidForChatService != "") { //When wvRoom shown
                                setupWebViewRoom(true)
                            } else {
                                if (mainLoaded) {
                                    toggleDispRetry(false, "Main")
                                } else {
                                    setupWebViewMain()
                                }
                            }
                        }
                    }
                }
            }
        }
        //In case of Battery Optimization (with socket.io), intermittent disconnection with transport close occurs on doze mode or idle time.
        //Nevertheless, you can maintain sendjay app as instant (=immediate) messenger with FCM. (You can do with start() function as above)
        //However, FCM does not guarantee 100% delivery rate and no delay according to Google and it really happens.
        //Battery Optimization (with socket.io)의 경우, 절전모드나 대기모드에서 간헐적인 disconnection이 발생함.
        //그럼에도 불구하고, FCM을 이용하면 instant messeging을 구현 가능함. 그러나, FCM은 100% 성공적이고 지연없는 배달을 보장해 주지 않음.
        start() //with Battery Optimization
    }

    override fun onNewIntent(intent: Intent?) { //onNewIntent (from Notification) -> onResume (no onCreate)
        super.onNewIntent(intent)
        try {
            Util.connectSockWithCallback(curContext, connManager) {
                if (it.get("code").asString != Const.RESULT_OK) {
                    Util.toast(curContext, it.get("msg").asString)
                    return@connectSockWithCallback
                }
                intent?.let {
                    val type = it.getStringExtra("type") ?: return@connectSockWithCallback //open
                    val roomid = it.getStringExtra("roomid") //roomid
                    val origin = it.getStringExtra("origin") //noti
                    val objStr = it.getStringExtra("objStr") //""
                    isFromNoti = true
                    procOpenRoom(type, roomid!!, origin!!, objStr!!)
                }
            }
        } catch (e: Exception) {
            logger.error("onNewIntent: ${e.toString()}")
            Util.procException(curContext, e, "onNewIntent")
        }
    }

    override fun onBackPressed() { //super.onBackPressed()
        if (roomidForChatService != "") { //When wvRoom shown
            procCloseRoom()
        } else {
            if (wvRoom.visibility == View.VISIBLE) {
                procCloseRoom()
            } else {
                moveTaskToBack(true)
            }
        }
    }

    override fun onResume() { //onCreate -> onResume
        super.onResume()
        try {
            isOnTop = true
            if (roomidForChatService != "") {
                Util.loadUrl(wvRoom, "setFocusFromWebView", isOnTop.toString())
                updateAllUnreads(false, isFromNoti)
                if (isFromNoti) isFromNoti = false
                Util.connectSockWithCallback(curContext, connManager)
            } else {
                cancelUnreadNoti()
                if (!isOnCreate) {
                    CoroutineScope(Dispatchers.Main).launch {
                        if (!chkUpdate(false)) return@launch
                        procLogin(true) { //related with Reset Authentication
                            Util.connectSockWithCallback(curContext, connManager)
                        }
                    }
                }
            }
            if (isOnCreate) isOnCreate = false
        } catch (e: Exception) {
            logger.error("onResume: ${e.toString()}")
            Util.procException(curContext, e, "onResume")
        }
    }

    override fun onPause() {
        super.onPause()
        isOnTop = false
        if (roomidForChatService != "") Util.loadUrl(wvRoom, "setFocusFromWebView", isOnTop.toString())
    }

    override fun onDestroy() {
        super.onDestroy()
        roomidForChatService = ""
        KeyChain.set(curContext, Const.KC_ROOMID_FOR_CHATSERVICE, "")
        disposableMsg?.dispose()
        disposableMain?.dispose()
        disposableRoom?.dispose()
        keyboardVisibilityChecker.detachKeyboardListeners()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        //if (resultCode != RESULT_OK) return //Do not uncomment this line (eg : filePathCallbackMain?.onReceiveValue should be executed all the time)
        try {
            if (requestCode == FILE_RESULT_MAIN) { //webview file chooser
                filePathCallbackMain?.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(resultCode, data))
                filePathCallbackMain = null
            } else if (requestCode == FILE_RESULT_ROOM) { //webview file chooser
                if (data != null) {
                    var list: Array<Uri?>? = null
                    if (data.clipData != null) { //handle multiple-selected files
                        list = data.clipData?.itemCount?.let { arrayOfNulls(it) } //val list = mutableListOf<Uri>()
                        val numSelectedFiles = data.clipData!!.itemCount
                        for (i in 0 until numSelectedFiles) {
                            list?.set(i, data.clipData!!.getItemAt(i).uri)
                        }
                        filePathCallbackRoom?.onReceiveValue(list)
                    } else { //if (data.data != null) { //handle single-selected file
                        filePathCallbackRoom?.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(resultCode, data))
                    }
                } else {
                    filePathCallbackRoom?.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(resultCode, data))
                }
                filePathCallbackRoom = null
            } else if (requestCode == MEMBER_RESULT || requestCode == INVITE_RESULT) {
                data?.apply {
                    val userids = this.getStringExtra("userids")
                    val usernms = this.getStringExtra("usernms")
                    val obj = """{
                        'userids' : '$userids', 'usernms' : '$usernms'
                    }"""
                    if (requestCode == MEMBER_RESULT) {
                        Util.loadUrl(wvMain, "newchat", obj)
                    } else {
                        Util.loadUrl(wvRoom, "invite", obj)
                    }
                }
            } else {
                Util.log("onActivityResult", "Wrong result.")
            }
        } catch (e: Exception) {
            logger.error("onActivityResult: ${e.toString()}")
            Util.procException(curContext, e, "onActivityResult")
        }
    }

    private fun start() {
        val logTitle = object{}.javaClass.enclosingMethod?.name!!
        if (!packageManager.canRequestPackageInstalls()) {
            Util.alert(curContext, "This is InHouse App (not from PlayStore). So, please accept unknown source for ${Const.TITLE} on next step and restart app. Thank you.", Const.TITLE, {
                startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:$packageName")))
            })
        } else {
            CoroutineScope(Dispatchers.Main).launch {
                if (!chkUpdate(true)) return@launch
                procLogin(false) {
                    CoroutineScope(Dispatchers.Main).launch {
                        try {
                            val winid1 = Util.getCurDateTimeStr() //Mobile
                            val param = listOf("type" to "set_new", "userkey" to uInfo.userkey, "winid" to winid1)
                            val json = HttpFuel.get(curContext, "${Const.DIR_ROUTE}/chk_redis", param).await()
                            if (json.get("code").asString != Const.RESULT_OK) {
                                Util.alert(curContext, json.get("msg").asString, logTitle)
                            } else {
                                KeyChain.set(curContext, Const.KC_WINID, winid1)
                                KeyChain.set(curContext, Const.KC_USERIP, json.get("userip").asString)
                                if (ChatService.serviceIntent == null) {
                                    val intentNew = Intent(curContext, ChatService::class.java)
                                    startForegroundService(intentNew)
                                }
                                setupWebViewMain()
                            }
                        } catch (e: Exception) {
                            logger.error("$logTitle: ${e.toString()}")
                            Util.procException(curContext, e, logTitle)
                        }
                    }
                }
            }
        }
    }

    private fun logoutApp() {
        KeyChain.set(curContext, Const.KC_AUTOLOGIN, "")
        stopServiceByLogout = true
        curContext.stopService(Intent(curContext, ChatService::class.java))
        curContext.finish()
    }

    //App update check when wvMain Webview shown not on wvRoom. Only if (roomidForChatService == "") onResume()
    //채팅방(wvRoom)에서는 앱 업데이트 체크하지 않고 목록(wvMain) 화면에서만 하기 : onResume()에서 if (roomidForChatService == "") 일 때만
    private suspend fun chkUpdate(onStatusCreate: Boolean): Boolean {
        val logTitle = object{}.javaClass.enclosingMethod?.name!!
        try {
            if (!Util.chkIfNetworkAvailable(curContext, connManager, "")) {
                if (onStatusCreate) toggleDispRetry(true, "Main", logTitle, "", true)
                return false
            }
            val json = HttpFuel.get(curContext, "${Const.URL_SERVER}/applist.json", null).await() //notice that host is not URL_SERVER_DEV but URL_SERVER
            if (json.get("code").asString != Const.RESULT_OK) {
                Util.alert(curContext, json.get("code").asString + "\n" + json.get("msg").asString, logTitle)
                return false
            }
            val jsonApp = json.getAsJsonObject(Const.VERSIONCHK_APP) //Util.log(json1.get("version").asString,"=====", BuildConfig.VERSION_NAME)
            if (jsonApp.get("version").asString == BuildConfig.VERSION_NAME) {
                val jsonEtc = json.getAsJsonObject(Const.VERSIONCHK_ETC)
                ChatService.gapScreenOffOnDualMode = jsonEtc.get("screenoff").asString //Dual means socket on both PC Web and Mobile
                ChatService.gapScreenOnOnDualMode = jsonEtc.get("screenon").asString
                val main_version = json.get(Const.KC_WEBVIEW_MAIN_VERSION).asString
                val chat_version = json.get(Const.KC_WEBVIEW_CHAT_VERSION).asString
                val popup_version = json.get(Const.KC_WEBVIEW_POPUP_VERSION).asString
                val kc_main_version = KeyChain.get(curContext, Const.KC_WEBVIEW_MAIN_VERSION) ?: ""
                val kc_chat_version = KeyChain.get(curContext, Const.KC_WEBVIEW_CHAT_VERSION) ?: ""
                val kc_popup_version = KeyChain.get(curContext, Const.KC_WEBVIEW_POPUP_VERSION) ?: ""
                if (main_version != kc_main_version) {
                    wvMain.clearCache(true)
                    wvMain.clearHistory()
                    KeyChain.set(curContext, Const.KC_WEBVIEW_MAIN_VERSION, main_version)
                    setupWebViewMain()
                }
                if (chat_version != kc_chat_version) {
                    wvRoom.clearCache(true)
                    wvRoom.clearHistory()
                    KeyChain.set(curContext, Const.KC_WEBVIEW_CHAT_VERSION, chat_version)
                }
                if (popup_version != kc_popup_version) { //See PopupActivity.
                    KeyChain.set(curContext, Const.KC_WEBVIEW_POPUP_VERSION, "clear_cache" + popup_version)
                }
                return true
            } else {
                Util.toast(curContext, "App downloading for update.")
                CoroutineScope(Dispatchers.IO).launch {
                    val filename = jsonApp.get("filename").asString
                    val path = jsonApp.get("path").asString //Util.log("@@@@", Const.URL_SERVER + path + filename)
                    URL(Const.URL_SERVER + path + filename).openStream().use { input ->
                        //Util.log(logTitle, input.readBytes().size.toString()) //주의 : readBytes로 먼저 읽고 아래에서 읽으면 0 byte 나옴
                        val file = File(curContext.filesDir, filename) //scoped storage internal(filesDir)
                        FileOutputStream(file).use { output ->
                            try {
                                input.copyTo(output)
                                val apkUri = FileProvider.getUriForFile(curContext, "$packageName.provider", file) //provider : See AndroidManifest.xml
                                val intent = Intent(Intent.ACTION_VIEW)
                                intent.setDataAndType(apkUri, "application/vnd.android.package-archive")
                                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP) //NOT NEW_TASK. intent.putExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE, false) was useless
                                curContext.startActivity(intent)
                            } catch (e1: Exception) {
                                Util.procException(curContext, e1, "App downloading")
                            }
                        }
                    }
                }
                return false
            }
        } catch (e: Exception) {
            logger.error("$logTitle: e ${e.toString()}")
            Util.procException(curContext, e, logTitle)
            return false
        }
    }

    private fun cancelUnreadNoti() {
        NotiCenter.manager?.let {
            if (NotiCenter.notiFound(Const.NOTI_ID_CHK_UNREAD)) it.cancel(Const.NOTI_ID_CHK_UNREAD)
        }
    }

    private fun procCloseRoom() {
        val logTitle = object{}.javaClass.enclosingMethod?.name!!
        try {
            roomidForChatService = ""
            KeyChain.set(curContext, Const.KC_ROOMID_FOR_CHATSERVICE, "")
            wvRoom.visibility = View.GONE
            wvRoom.loadUrl(KeyChain.get(curContext, Const.KC_MODE_PUBLIC) + Const.PAGE_DUMMY)
            cancelUnreadNoti()
        } catch (e: Exception) {
            logger.error("$logTitle: ${e.toString()}")
            Util.procException(curContext, e, logTitle)
        }
    }

    private fun procOpenRoom(type: String, roomid: String, origin: String, objStr: String) {
        //type = "newFromMain" or "open" from javascript and
        //origin = "portal" or "" from javascript and
        gType = type
        gRoomid = roomid
        gOrigin = origin
        gObjStr = objStr
        setupWebViewRoom(false) //Util.log("procOpenRoom", gType+"==="+gRoomid+"==="+gOrigin+"==="+gObjStr)
    }

    private suspend fun procLogin(chkAuth: Boolean, callback: () -> Unit = {}) { //chkAuth=true : related with Reset Authentication
        val logTitle = object{}.javaClass.enclosingMethod?.name!!
        try {
            var loginNeeded = false
            val pushtoken = KeyChain.get(curContext, Const.KC_PUSHTOKEN) ?: "" //from onNewToken() FcmService.kt
            if (pushtoken == "") {
                Util.alert(curContext, "FCM Token is blank. Sorry, You need to uninstall and install app again.", logTitle)
                return
            }
            val autoLogin = KeyChain.get(curContext, Const.KC_AUTOLOGIN) ?: ""
            if (autoLogin == "Y") {
                val param = listOf("os" to "and", "push_and" to pushtoken)
                authJson = HttpFuel.get(curContext, "${Const.DIR_ROUTE}/login/verify", param).await()
                if (authJson.get("code").asString == Const.RESULT_OK) {
                    uInfo = UserInfo(curContext, authJson)
                } else if (authJson.get("code").asString == Const.RESULT_ERR_HTTPFUEL) {
                    toggleDispRetry(true, "Main", logTitle, authJson.get("msg").asString, true)
                    return
                } else {
                    loginNeeded = true
                }
            } else {
                loginNeeded = true
            }
            if (loginNeeded) {
                if (chkAuth) {
                    logoutApp()
                    return
                }
                val mDialogView = layoutInflater.inflate(R.layout.dialog_login, null) //val mDialogView = LayoutInflater.from(this).inflate(R.layout.dialog_login, null)
                val mBuilder = AlertDialog.Builder(curContext).setView(mDialogView).setTitle("Starting ${Const.TITLE}").setCancelable(false)
                val mAlertDialog = mBuilder.show()
                mDialogView.login.setOnClickListener {
                    CoroutineScope(Dispatchers.Main).launch {
                        try {
                            var uidStr = mDialogView.userid.text.toString().trim()
                            if (uidStr.endsWith(Const.SUFFIX_DEV)) {
                                KeyChain.set(curContext, Const.KC_MODE_SERVER, Const.URL_SERVER_DEV)
                                KeyChain.set(curContext, Const.KC_MODE_SOCK, Const.URL_SOCK_DEV)
                                KeyChain.set(curContext, Const.KC_MODE_PUBLIC, Const.URL_PUBLIC_DEV)
                                uidStr = uidStr.replace(Const.SUFFIX_DEV, "")
                            } else {
                                KeyChain.set(curContext, Const.KC_MODE_SERVER, Const.URL_SERVER)
                                KeyChain.set(curContext, Const.KC_MODE_SOCK, Const.URL_SOCK)
                                KeyChain.set(curContext, Const.KC_MODE_PUBLIC, Const.URL_PUBLIC)
                            }
                            val param = listOf(Const.KC_USERID to uidStr, "pwd" to mDialogView.pwd.text.toString().trim(), "os" to "and", "push_and" to pushtoken)
                            authJson = HttpFuel.get(curContext, "${Const.DIR_ROUTE}/login", param).await()
                            if (authJson.get("code").asString != Const.RESULT_OK) {
                                Util.alert(curContext, authJson.get("msg").asString, logTitle)
                            } else {
                                KeyChain.set(curContext, Const.KC_AUTOLOGIN, "Y")
                                uInfo = UserInfo(curContext, authJson)
                                mAlertDialog.dismiss()
                                callback()
                            }
                        } catch (e1: Exception) {
                            logger.error("$logTitle: e1 ${e1.toString()}")
                            Util.procException(curContext, e1, logTitle)
                        }
                    }
                }
            } else {
                callback()
            }
        } catch (e: Exception) {
            logger.error("$logTitle: ${e.toString()}")
            Util.procException(curContext, e, logTitle)
        }
    }

    private fun toggleDispRetry(show: Boolean, webview: String, urlStr: String? = null, errMsg: String? = null, noAutoRetry: Boolean? = null) {
        val logTitle = object{}.javaClass.enclosingMethod?.name!!
        if (show) {
            if (noAutoRetry == true) {
                txtAuto.visibility = View.GONE
            } else {
                txtAuto.visibility = View.VISIBLE
                CoroutineScope(Dispatchers.Main).launch {
                    withTimeout(RETRY_TIMEOUT) {
                        while (true) {
                            delay(RETRY_DELAY)
                            try {
                                var json = SocketIO.connect(curContext, connManager).await()
                                if (json.get("code").asString == Const.RESULT_OK) {
                                    if (btnRetry.visibility == View.VISIBLE) btnRetry.performClick()
                                    break
                                }
                            } catch (e: Exception) {
                                Util.toast(curContext, logTitle + ": " + e.toString())
                            }
                        }
                    }
                }
            }
            wvRoom.visibility = View.GONE //if (webview == "Room") wvRoom.visibility = View.GONE
            wvMain.visibility = View.GONE
            btnRetry.visibility = View.VISIBLE
            txtRmks.visibility = View.VISIBLE
            txtUrl.visibility = View.VISIBLE
            txtUrl.text = urlStr + "\n" + errMsg //container.background = null //container.setBackgroundColor(Color.WHITE)
        } else {
            if (webview == "Room") wvRoom.visibility = View.VISIBLE
            wvMain.visibility = View.VISIBLE
            btnRetry.visibility = View.GONE
            txtRmks.visibility = View.GONE
            txtUrl.visibility = View.GONE
            txtUrl.text = ""
            txtAuto.visibility = View.GONE
        }
    }

    private fun setupWebViewMain() {
        val logTitle = object{}.javaClass.enclosingMethod?.name!!
        Util.setupWebView(curContext, connManager, wvMain) //Util.log("###", wvMain.settings.userAgentString)
        wvMain.addJavascriptInterface(WebInterfaceMain(), "AndroidMain") //Util.log("@@@@@@@@@@", wvMain.settings.cacheMode.toString())
        toggleDispRetry(false, "Main")
        wvMain.webChromeClient = object : WebChromeClient() {
            override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean { //return super.onConsoleMessage(consoleMessage)
                consoleMessage?.apply {
                    Util.procConsoleMsg(curContext, message() + "\n" + sourceId(), "wvMain")
                }
                return true
            }
            override fun onShowFileChooser(webView: WebView, filePathCallback: ValueCallback<Array<Uri?>>, fileChooserParams: FileChooserParams): Boolean {
                filePathCallbackMain = filePathCallback
                val intent = fileChooserParams.createIntent()
                intent.action = Intent.ACTION_GET_CONTENT
                intent.addCategory(Intent.CATEGORY_OPENABLE)
                intent.type = "image/*"
                intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, false)
                startActivityForResult(Intent.createChooser(intent, "Image Browser"), FILE_RESULT_MAIN)
                return true
            }
        }
        wvMain.webViewClient = object : WebViewClient() {
            override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                super.onReceivedError(view, request, error) //https://gist.github.com/seongchan/752db643377f823950648d0bc80599c1
                val urlStr = if (request != null) request.url.toString() else "" //request?.url.toString() //Multiple request shown like jquery module.
                if (error != null && error.description != "" && urlStr.contains(Const.URL_JAY) && urlStr.contains(Const.PAGE_MAIN)) {
                    val errMsg = "${error.errorCode}/${error.description}"
                    toggleDispRetry(true, "Main", urlStr, errMsg)
                } else { //ajax : ex) -2/net::ERR_INTERNET_DISCONNECTED : Network not available
                    //Util.toast(curContext, "wvMain/${error?.errorCode}/${error?.description}/${urlStr}") //Multiple toast shown because of Multiple request
                }
            }
        } //Util.log("@@@@@@@@@@@", KeyChain.get(curContext, Const.KC_MODE_PUBLIC) + "${Const.PAGE_MAIN}?webview=and")
        wvMain.loadUrl(KeyChain.get(curContext, Const.KC_MODE_PUBLIC) + "${Const.PAGE_MAIN}?webview=and") //not ios
        CoroutineScope(Dispatchers.Main).launch {
            mainLoaded = false
            retried = false
            delay(Const.RESTFUL_TIMEOUT.toLong())
            if (!mainLoaded && !retried) toggleDispRetry(true, "Main", logTitle, "RESTFUL_TIMEOUT")
        }
    }

    private fun setupWebViewRoom(refresh: Boolean) {
        val logTitle = object{}.javaClass.enclosingMethod?.name!!
        Util.setupWebView(curContext, connManager, wvRoom)
        wvRoom.addJavascriptInterface(WebInterfaceRoom(), "AndroidRoom")
        toggleDispRetry(false, "Room") //Util.log(refresh.toString()+"==="+gRoomid+"==="+roomidForChatService)
        if (!refresh && gRoomid != "" && gRoomid == roomidForChatService) return
        wvRoom.webChromeClient = object : WebChromeClient() {
            override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean { //return super.onConsoleMessage(consoleMessage)
                consoleMessage?.apply {
                    Util.procConsoleMsg(curContext, message() + "\n" + sourceId(), "wvRoom")
                }
                return true
            }
            override fun onShowFileChooser(webView: WebView, filePathCallback: ValueCallback<Array<Uri?>>, fileChooserParams: FileChooserParams): Boolean {
                filePathCallbackRoom = filePathCallback
                val intent = fileChooserParams.createIntent()
                intent.action = Intent.ACTION_GET_CONTENT
                intent.addCategory(Intent.CATEGORY_OPENABLE)
                intent.type = "*/*"
                intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
                startActivityForResult(Intent.createChooser(intent, "File Browser"), FILE_RESULT_ROOM)
                return true
            }
        }
        wvRoom.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val urlStr = request!!.url.toString()
                if (urlStr.startsWith(Const.URL_HOST)) {
                    if (!urlStr.contains(Const.PAGE_ROOM)) return false //ignore dummy page
                    view!!.loadUrl(urlStr)
                } else { //ex) https://socket.io, https://naver.com ..
                    view!!.context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(urlStr)))
                }
                return true //return super.shouldOverrideUrlLoading(view, request)
            }
            override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                super.onReceivedError(view, request, error)
                val urlStr = if (request != null) request.url.toString() else "" //request?.url.toString() //Multiple request might be seen like jquery module.
                if (error != null && error.description != "" && urlStr.contains(Const.URL_JAY) && urlStr.contains(Const.PAGE_ROOM)) {
                    val errMsg = "${error.errorCode}/${error.description}"
                    toggleDispRetry(true, "Room", urlStr, errMsg)
                } else { //ajax : ex) -2/net::ERR_INTERNET_DISCONNECTED : Network not available
                    //Util.toast(curContext, "wvRoom/${error?.errorCode}/${error?.description}/${urlStr}") //Multiple toast shown because of Multiple request
                }
            }
        }
        Util.setDownloadListener(curContext, wvRoom)
        wvRoom.loadUrl(KeyChain.get(curContext, Const.KC_MODE_PUBLIC) + "${Const.PAGE_ROOM}?webview=and&type=$gType&roomid=$gRoomid&origin=$gOrigin")
        CoroutineScope(Dispatchers.Main).launch {
            roomLoaded = false
            retried = false
            delay(Const.RESTFUL_TIMEOUT.toLong())
            if (!roomLoaded && !retried) toggleDispRetry(true, "Room", logTitle, "RESTFUL_TIMEOUT")
        }
    }

    private fun updateAllUnreads(init: Boolean, isFromNoti: Boolean) { //for room only
        val logTitle = object{}.javaClass.enclosingMethod?.name!!
        try {
            NotiCenter.mapRoomid[gRoomid]?.let { NotiCenter.manager!!.cancel(it) }
            NotiCenter.mapRoomid.remove(gRoomid)
            if (NotiCenter.mapRoomid.isEmpty()) NotiCenter.manager!!.cancel(Const.NOTI_ID_SUMMARY)
            if (!init) Util.loadUrl(wvRoom, "updateAllUnreadsFromWebView", isFromNoti.toString())
        } catch (e: Exception) {
            logger.error("$logTitle: ${e.toString()}")
            Util.procException(curContext, e, logTitle)
        }
    }

    inner class WebInterfaceMain {

        @JavascriptInterface
        fun procAfterOpenMain() {
            val logTitle = object{}.javaClass.enclosingMethod?.name!!
            CoroutineScope(Dispatchers.Main).launch {
                try {
                    disposableMsg?.dispose()
                    disposableMsg = Util.procRxMsg(curContext)
                    disposableMain?.dispose()
                    disposableMain = RxToDown.subscribe<RxEvent>().subscribe { //RxToDown.subscribe<RxEvent>().observeOn(AndroidSchedulers.mainThread()) {//to receive the event on main thread
                        CoroutineScope(Dispatchers.Main).launch {
                            var param: JSONObject?= null
                            try {
                                param = it.data as JSONObject //Util.log("@@Main", param.toString())
                                Util.loadUrlJson(wvMain, "getFromWebViewSocket", param)
                                //Error('getFromWebViewSocket is not defined') is not handled here but at onConsoleMessage().
                                //The reason is that getFromWebViewSocket is function from javascript.
                                //This procAfterOpenMain() calls getFromWebViewSocket for sure from index.html after its jquery's document ready,
                                //and this error should be occured only when RxEvent data is received and page not loaded completely like page refresh.
                                //But the RxEvent data not received at that moment can be brought to right after refreshing which means no problem.
                                //Error('getFromWebViewSocket is not defined')는 여기서 catch되지 않고 위의 onConsoleMessage()에서 처리됩니다.
                                //getFromWebViewSocket가 javascript에 있는 함수이기 때문입니다.
                                //여기 procAfterOpenMain()은 index.html안의 jquery document.ready후에 getFromWebViewSocket 호출은 보장되지만
                                //RxEvent 데이터가 들어올 때 웹페이지 새로고침 등이 일어나면 그 순간에 getFromWebViewSocket이 없으므로 발생하는 오류입니다.
                                //하지만, 그 순간에 받지 못한 RxEvent 데이터는 새로고침후에 바로 가져오는 것이므로 문제되지 않을 것이므로 onConsoleMessage()에서 예외로 처리합니다.
                            } catch (e: Exception) {
                                val msg = "${e.toString()}\n${it.ev}\n${param.toString()}"
                                logger.error("$logTitle: $msg")
                                Util.procExceptionStr(curContext, msg, logTitle)
                            }
                        }
                    }
                    val obj = Util.getStrObjFromUserInfo(uInfo) //Util.log("@@@@@@@@@@@", obj.toString()+"==="+authJson.toString())
                    Util.loadUrl(wvMain, "startFromWebView", obj, authJson.toString())
                } catch (e1: Exception) {
                    logger.error("$logTitle: e1 ${e1.toString()}")
                    Util.procException(curContext, e1, logTitle)
                }
            }
        }

        @JavascriptInterface
        fun doneLoad() {
            mainLoaded = true
        }

        @JavascriptInterface
        fun reload() {
            CoroutineScope(Dispatchers.Main).launch {
                setupWebViewMain()
            }
        }

        @JavascriptInterface
        fun logout() {
            CoroutineScope(Dispatchers.Main).launch {
                logoutApp()
            }
        }

        @JavascriptInterface
        fun openRoom(type: String, roomid: String, origin: String, objStr: String) {
            val logTitle = object{}.javaClass.enclosingMethod?.name!!
            CoroutineScope(Dispatchers.Main).launch {
                try {
                    Util.connectSockWithCallback(curContext, connManager) {
                        if (it.get("code").asString != Const.RESULT_OK) {
                            Util.toast(curContext, it.get("msg").asString)
                            return@connectSockWithCallback
                        }
                       procOpenRoom(type, roomid, origin, objStr)
                    }
                } catch (e: Exception) {
                    logger.error("$logTitle: ${e.toString()}")
                    Util.procException(curContext, e, logTitle)
                }
            }
        }

        @JavascriptInterface
        fun openPopup(origin: String, objStr: String) {
            val logTitle = object{}.javaClass.enclosingMethod?.name!!
            CoroutineScope(Dispatchers.Main).launch {
                try {
                    val pIntent = Intent(curContext, PopupActivity::class.java)
                    pIntent.putExtra("origin", origin)
                    pIntent.putExtra("objStr", objStr)
                    startActivityForResult(pIntent, MEMBER_RESULT)
                } catch (e: Exception) {
                    logger.error("$logTitle: ${e.toString()}")
                    Util.procException(curContext, e, logTitle)
                }
            }
        }

        @JavascriptInterface
        fun showLog(num: Int) {
            val logTitle = object{}.javaClass.enclosingMethod?.name!!
            CoroutineScope(Dispatchers.Main).launch {
                try { //File(path).walkTopDown().forEach { Util.log("=====", it.toString()) }
                    curContext.filesDir.let { it ->
                        val listFile = Util.getFiles(it)
                        if (listFile == null || listFile.size == 0) {
                            Util.toast(curContext, "Log files not exists.")
                            return@let
                        }
                        if (listFile.size - 1 < num) {
                            Util.toast(curContext, "listFile.size - 1 < num")
                            return@let
                        } //for (i in listFile.indices) { var name = listFile[i] }
                        val path = curContext.filesDir.toString() //https://stackoverflow.com/questions/55182578/how-to-read-plain-text-file-in-kotlin
                        val bufferedReader: BufferedReader = File(path + "/" + listFile[num]).bufferedReader()
                        val inputString = bufferedReader.use { it.readText() }
                        println(inputString)
                    }
                } catch (e: Exception) {
                    logger.error("$logTitle: ${e.toString()}")
                    Util.procException(curContext, e, logTitle)
                }
            }
        }

        @JavascriptInterface
        fun deleteLog() {
            val logTitle = object{}.javaClass.enclosingMethod?.name!!
            CoroutineScope(Dispatchers.Main).launch {
                try {
                    curContext.filesDir.let { it ->
                        val listFile = Util.getFiles(it)
                        if (listFile == null || listFile.size == 0) {
                            Util.toast(curContext, "Log files not exists.")
                            return@let
                        }
                        val path = curContext.filesDir.toString()
                        for (i in listFile.indices) File(path + "/" + listFile[i]).delete()
                        Util.toast(curContext, "deleteLog done.")
                    }
                } catch (e: Exception) {
                    logger.error("$logTitle: ${e.toString()}")
                    Util.procException(curContext, e, logTitle)
                }
            }
        }

    }

    inner class WebInterfaceRoom {

        @JavascriptInterface
        fun procAfterOpenRoom() {
            val logTitle = object{}.javaClass.enclosingMethod?.name!!
            CoroutineScope(Dispatchers.Main).launch {
                try {
                    if (gObjStr == "") {
                        gObjStr = """{ 'msgidCopied' : '${msgidCopied}' }"""
                    } else {
                        gObjStr = gObjStr.replace("}", "")
                        gObjStr += """, 'msgidCopied' : '${msgidCopied}' }"""
                    }
                    val obj = Util.getStrObjFromUserInfo(uInfo)
                    Util.loadUrl(wvRoom, "startFromWebView", obj, gObjStr)
                    disposableRoom?.dispose()
                    disposableRoom = RxToRoom.subscribe<RxEvent>().subscribe { //disposable = RxBus.subscribe<RxEvent>().observeOn(AndroidSchedulers.mainThread()) //to receive the event on main thread
                        CoroutineScope(Dispatchers.Main).launch {
                            var param: JSONObject?= null
                            try {
                                param = it.data as JSONObject //Util.log("@@Room", param.toString())
                                if (roomidForChatService != "") Util.loadUrlJson(wvRoom, "getFromWebViewSocket", param)
                            } catch (e: Exception) {
                                val msg = "${e.toString()}\n${it.ev}\n${param.toString()}"
                                logger.error("$logTitle: $msg")
                                Util.procExceptionStr(curContext, msg, logTitle)
                            }
                        }
                    }
                    roomidForChatService = gRoomid
                    KeyChain.set(curContext, Const.KC_ROOMID_FOR_CHATSERVICE, gRoomid)
                    updateAllUnreads(true, false)
                } catch (e1: Exception) {
                    logger.error("$logTitle: ${e1.toString()}")
                    Util.procException(curContext, e1, logTitle)
                }
            }
        }

        @JavascriptInterface
        fun doneLoad() {
            roomLoaded = true
        }

        @JavascriptInterface
        fun putData(data: String) {
            val logTitle = object{}.javaClass.enclosingMethod?.name!!
            CoroutineScope(Dispatchers.Main).launch {
                try {
                    val json = JSONObject(data)
                    val type = json.getString("type")
                    if (type == "set_roomid") { //see sock_ev_create_room(dupchk) in jay_chat.js
                        gType = "open"
                        gRoomid = json.getString("roomid") //new roomid
                        roomidForChatService = gRoomid
                        KeyChain.set(curContext, Const.KC_ROOMID_FOR_CHATSERVICE, gRoomid)
                        gOrigin = ""
                        gObjStr = ""
                    }
                } catch (e: Exception) {
                    logger.error("$logTitle: ${e.toString()}")
                    Util.procException(curContext, e, logTitle)
                }
            }
        }

        @JavascriptInterface
        fun openPopup(origin: String, objStr: String) { //origin = popup.html or index.html
            val logTitle = object{}.javaClass.enclosingMethod?.name!!
            CoroutineScope(Dispatchers.Main).launch {
                try {
                    val pIntent = Intent(curContext, PopupActivity::class.java)
                    pIntent.putExtra("origin", origin)
                    pIntent.putExtra("objStr", objStr)
                    if (origin.contains(Const.PAGE_MAIN)) { //index.html
                        startActivityForResult(pIntent, INVITE_RESULT)
                    } else { //popup.html
                        startActivity(pIntent)
                    }
                } catch (e: Exception) {
                    logger.error("$logTitle: ${e.toString()}")
                    Util.procException(curContext, e, logTitle)
                }
            }
        }

        @JavascriptInterface
        fun closeRoom() {
            CoroutineScope(Dispatchers.Main).launch {
                if (roomidForChatService != "") procCloseRoom()
            }
        }

        @JavascriptInterface
        fun copy(msgid: String) {
            msgidCopied = msgid
        }

        @JavascriptInterface
        fun paste() {
            val logTitle = object{}.javaClass.enclosingMethod?.name!!
            CoroutineScope(Dispatchers.Main).launch {
                try { //Util.log("@@@@", MainActivity.msgidCopied)
                    val json = org.json.JSONObject()
                    json.put("msgidCopied", msgidCopied)
                    Util.loadUrlJson(wvRoom, "pasteFromWebView", json)
                } catch (e: Exception) {
                    logger.error("$logTitle: ${e.toString()}")
                    Util.procException(curContext, e, logTitle)
                }
            }
        }

    }

}