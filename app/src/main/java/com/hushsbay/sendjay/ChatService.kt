package com.hushsbay.sendjay

import android.app.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.net.ConnectivityManager
import android.os.IBinder
import android.os.PowerManager
import android.os.Vibrator
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.hushsbay.sendjay.data.RxEvent
import com.hushsbay.sendjay.data.RxMsg
import com.hushsbay.sendjay.common.*
import io.reactivex.disposables.Disposable
import io.socket.client.Socket
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.apache.log4j.Logger
import org.json.JSONObject
import java.util.*
import java.util.concurrent.TimeUnit

class ChatService : Service() {

    //foregroundservice and notification icon => https://beehoneylife.tistory.com/5
    //https://www.spiria.com/en/blog/mobile-development/hiding-foreground-services-notifications-in-android/

    companion object {
        var state = Const.ServiceState.STOPPED //See FcmReceiver.kt
        var serviceIntent: Intent? = null //See MainActivity.kt
        var status_sock = Const.SockState.BEFORE_CONNECT
        var curState_sock = false
        var isBeingSockChecked = false
        var gapScreenOffOnDualMode = "10000"
        var gapScreenOnOnDualMode = "3000"
    }

    private var SEC_DURING_DAEMON: Long = 5000 //try connecting every 5 second only in case of disconnection
    private var SEC_DURING_RESTART = 5 //try restarting after 5 seconds (just once) when service killed (see another periodic trying with SimpleWorker.kt)

    private lateinit var logger: Logger
    private lateinit var screenReceiver: BroadcastReceiver
    private var pwrManager: PowerManager? = null
    private var connManager: ConnectivityManager? = null
    private var disposable: Disposable? = null
    private var thread: Thread? = null
    private lateinit var uInfo: UserInfo

    private var shouldThreadStop = false
    private var cut_mobile = false

    private inner class mainTask : TimerTask() {
        override fun run() { //android.app.RemoteServiceException: Bad notification for startForeground: java.lang.RuntimeException: invalid channel for service notification: null
            //notiManager!!.cancel(Const.NOTI_ID_FOREGROUND_SERVICE)
            //notiManager!!.deleteNotificationChannel(Const.APP_NAME) //android.app.RemoteServiceException: Bad notification for startForeground: java.lang.IllegalArgumentException: Channel does not exist
            this.cancel()
        }
    }

    override fun onCreate() {
        super.onCreate()
        try {
            status_sock = Const.SockState.BEFORE_CONNECT
            state = Const.ServiceState.RUNNING
            logger = LogHelper.getLogger(applicationContext, this::class.simpleName)
            NotiCenter(applicationContext, packageName) //kotlin invoke method : NotiCenter.invoke() //see MainActivity.kt also
            connManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            pwrManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            screenReceiver = object : BroadcastReceiver() {
                override fun onReceive(contxt: Context?, intent: Intent?) { //when (intent?.action) {
                    intent?.let {
                        if (it.action == Intent.ACTION_SCREEN_ON) {
                            KeyChain.set(applicationContext, Const.KC_SCREEN_STATE, "on")
                            Util.connectSockWithCallback(applicationContext, connManager!!)
                        } else if (it.action == Intent.ACTION_SCREEN_OFF) {
                            KeyChain.set(applicationContext, Const.KC_SCREEN_STATE, "off")
                        }
                    }
                }
            }
            val filter1 = IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_SCREEN_OFF)
            }
            registerReceiver(screenReceiver, filter1)
            uInfo = UserInfo(applicationContext)
            val winid = KeyChain.get(applicationContext, Const.KC_WINID)
            val userip = KeyChain.get(applicationContext, Const.KC_USERIP)
            SocketIO(applicationContext, uInfo, winid!!, userip!!) //kotlin invoke method : SocketIO.invoke()
            initNotification()
            initDeamon()
        } catch (e: Exception) {
            logger.error("onCreate: ${e.toString()}")
            e.printStackTrace()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int { //onCreate -> onStartCommand
        serviceIntent = intent
        return START_STICKY //https://stackoverflow.com/questions/25716864/why-is-my-service-started-twice
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null;
    }

    override fun onTaskRemoved(rootIntent: Intent?) { //See android:stopWithTask="false" in AndroidManifest.xml
        super.onTaskRemoved(rootIntent)
        stopSelf() //and restartChatService()
    }

    //Service is destroyed when its explicitly stopped, or when the system needs resources.
    //To explicitly stop a service, call stopService from any Context, or stopSelf from the Service.
    override fun onDestroy() {
        super.onDestroy()
        state = Const.ServiceState.STOPPED
        logger.error("onDestroy")
        restartChatService()
        unregisterReceiver(screenReceiver)
    }

    private fun initNotification() {
        val logTitle = object{}.javaClass.enclosingMethod?.name!!
        try {
            shouldThreadStop = false
            var manager: NotificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            var channel: NotificationChannel = NotificationChannel(Const.NOTICHANID_FOREGROUND, Const.NOTICHANID_FOREGROUND, NotificationManager.IMPORTANCE_LOW)
            manager.createNotificationChannel(channel)
            val builder = NotificationCompat.Builder(this, Const.NOTICHANID_FOREGROUND)
            builder.setSmallIcon(R.mipmap.ic_launcher) //If not, 2 lines of verbose explanation shows.
            val style = NotificationCompat.BigTextStyle()
            style.bigText(null)
            style.setBigContentTitle(null)
            style.setSummaryText(null)
            builder.setStyle(style)
            builder.setContentTitle("Drag to one side for hiding app icon.")
            builder.setContentText(null)
            builder.setOngoing(true)
            builder.setWhen(0)
            builder.setShowWhen(false)
            val notificationIntent = Intent(this, MainActivity::class.java)
            val pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, 0)
            builder.setContentIntent(pendingIntent)
            val notification = builder.build()
            startForeground(Const.NOTI_ID_FOREGROUND_SERVICE, notification) //id should not be zero
            //Timer().schedule(mainTask(), 1000) //or postDelayed
        } catch (e: Exception) {
            logger.error("$logTitle: ${e.toString()}")
            Util.log(logTitle, e.toString())
            e.printStackTrace()
            RxToDown.post(RxMsg(Const.SOCK_EV_ALERT, JSONObject().put("msg", "$logTitle: ${e.toString()}")))
        }
    }

    private fun initDeamon() {
        val logTitle = object{}.javaClass.enclosingMethod?.name!!
        try {
            val workManager = WorkManager.getInstance(applicationContext) //https://tristan91.tistory.com/480
            workManager.cancelAllWork()
            val periodicRequest = PeriodicWorkRequest.Builder(SimpleWorker::class.java, 15, TimeUnit.MINUTES).build() //minimum 15 minutes
            workManager.enqueue(periodicRequest)
            val r: Runnable = Daemon()
            thread = Thread(r)
            thread!!.setDaemon(true)
            thread!!.start()
            procSocketOn()
            procSocketEmit()
        } catch (e: Exception) {
            logger.error("$logTitle: ${e.toString()}")
            Util.log(logTitle, e.toString())
            e.printStackTrace()
            RxToDown.post(RxMsg(Const.SOCK_EV_TOAST, JSONObject().put("msg", "$logTitle: ${e.toString()}")))
        }
     }

    private fun restartChatService() {
        val logTitle = object{}.javaClass.enclosingMethod?.name!!
        try {
            logger.error("restartChatService0")
            if (!thread!!.isInterrupted || thread!!.isAlive) thread!!.interrupt()
            shouldThreadStop = true
            disposable?.dispose()
            if (SocketIO.sock != null && SocketIO.sock!!.connected()) SocketIO.sock!!.disconnect()
            logger.error("restartChatService1")
            if (MainActivity.stopServiceByLogout || cut_mobile) {
                serviceIntent = null
                state = Const.ServiceState.LOGOUTED
                cut_mobile = false
                logger.error("restartChatService2")
                return
            }
            logger.error("restartChatService3")
            curState_sock = false
            val calendar = Calendar.getInstance()
            calendar.timeInMillis = System.currentTimeMillis()
            calendar.add(Calendar.SECOND, SEC_DURING_RESTART)
            val intent = Intent(this, AlarmReceiver::class.java)
            val sender = PendingIntent.getBroadcast(this, 0, intent, 0)
            val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
            alarmManager[AlarmManager.RTC_WAKEUP, calendar.timeInMillis] = sender
            logger.error("restartChatService4")
        } catch (e: Exception) {
            logger.error("$logTitle: ${e.toString()}")
            Util.log(logTitle, e.toString())
            e.printStackTrace()
            RxToDown.post(RxMsg(Const.SOCK_EV_TOAST, JSONObject().put("msg", "$logTitle: ${e.toString()}")))
        }
    }

    private fun setRoomInfo(json: JSONObject, logTitle:  String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val data = json.getJSONObject("data")
                val roomid = data.getString("roomid")
                val jsonRI = HttpFuel.get(applicationContext, "${Const.DIR_ROUTE}/get_roominfo", listOf("roomid" to roomid)).await()
                if (jsonRI.get("code").asString == Const.RESULT_OK) {
                    NotiCenter.getRoomInfo(jsonRI, roomid)
                } else {
                    RxToDown.post(RxMsg(Const.SOCK_EV_ALERT, JSONObject().put("msg", "$logTitle:setRoomInfo: ${jsonRI.get("msg").asString}")))
                }
            } catch (e: Exception) {
                logger.error("$logTitle: ${e.toString()}")
                Util.log(logTitle, e.toString())
                e.printStackTrace()
                RxToDown.post(RxMsg(Const.SOCK_EV_ALERT, JSONObject().put("msg", "$logTitle:setRoomInfo: ${e.toString()}")))
            }
        }
    }

    private fun procSocketEmit() {
        val logTitle = object{}.javaClass.enclosingMethod?.name!!
        disposable?.dispose()
        disposable = RxToUp.subscribe<RxEvent>().subscribe {
            try {
                Util.log("$logTitle(out)", it.toString())
                val json = JSONObject()
                json.put("ev", it.ev)
                json.putOpt("data", it.data)
                json.put("returnTo", it.returnTo ?: "parent")
                json.put("returnToAnother", it.returnToAnother)
                val procMsg = it.procMsg
                Util.connectSockWithCallback(applicationContext, connManager!!) {
                    if (procMsg == true && it.get("code").asString != Const.RESULT_OK) {
                        Toast.makeText(applicationContext, Const.TITLE + ": " + it.get("msg").asString, Toast.LENGTH_LONG).show()
                        return@connectSockWithCallback
                    }
                    SocketIO.sock!!.emit(Const.SOCK_EV_COMMON, json)
                }
            } catch (e: Exception) {
                logger.error("$logTitle: ${e.toString()}")
                Util.log(logTitle, e.toString())
                e.printStackTrace()
                RxToDown.post(RxMsg(Const.SOCK_EV_TOAST, JSONObject().put("msg", "$logTitle: ${e.toString()}")))
            }
        }
    }

    private fun procSocketOn() {
        val logTitle = object{}.javaClass.enclosingMethod?.name!!
        SocketIO.sock!!.off(Socket.EVENT_CONNECT).on(Socket.EVENT_CONNECT) {
            try {
                Util.log(logTitle, Socket.EVENT_CONNECT)
                curState_sock = true
                if (status_sock == Const.SockState.FIRST_DISCONNECTED) {
                    status_sock = Const.SockState.RECONNECTED
                    Util.sendToDownWhenConnDisconn(applicationContext, Socket.EVENT_CONNECT)
                }
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val json = HttpFuel.get(applicationContext, "${Const.DIR_ROUTE}/qry_unread", null).await()
                        if (json.get("code").asString == Const.RESULT_OK) {
                            val list = json.getAsJsonArray("list")
                            if (list.size() == 0) return@launch
                            for (i in 0 until list.size()) {
                                val item = list[i].asJsonObject
                                val roomid = item.get("ROOMID").asString
                                val addinfo = item.get("ADDINFO").asString //for mobile only
                                val arr = addinfo.split(Const.DELI)
                                val msgid = arr[0]
                                val cdt = arr[1]
                                val type = arr[2]
                                val body = arr[3]
                                val body1 = "qry_unread) " + Util.getTalkBodyCustom(type, body)
                                NotiCenter.notiByRoom(applicationContext, uInfo, roomid, body1, false, msgid, cdt)
                            }
                        } else {
                            RxToDown.post(RxMsg(Const.SOCK_EV_ALERT, JSONObject().put("msg", "$logTitle:qry_unread: ${json.get("msg").asString}")))
                        }
                    } catch (e: Exception) {
                        logger.error("$logTitle: EVENT_CONNECT ${e.toString()}")
                        Util.log(logTitle, e.toString())
                        e.printStackTrace()
                        RxToDown.post(RxMsg(Const.SOCK_EV_ALERT, JSONObject().put("msg", "$logTitle:qry_unread: ${e.toString()}")))
                    }
                }
            } catch (e1: Exception) {
                logger.error("$logTitle: EVENT_CONNECT1 ${e1.toString()}")
                Util.log(logTitle, e1.toString())
                e1.printStackTrace()
                RxToDown.post(RxMsg(Const.SOCK_EV_TOAST, JSONObject().put("msg", "$logTitle: ${e1.toString()}")))
            }
        }.off(Socket.EVENT_DISCONNECT).on(Socket.EVENT_DISCONNECT) {
            try {
                Util.log(logTitle, Socket.EVENT_DISCONNECT)
                curState_sock = false
                if (status_sock == Const.SockState.BEFORE_CONNECT) status_sock = Const.SockState.FIRST_DISCONNECTED
                Util.sendToDownWhenConnDisconn(applicationContext, Socket.EVENT_DISCONNECT)
            } catch (e: Exception) {
                logger.error("$logTitle: EVENT_DISCONNECT ${e.toString()}")
                Util.log(logTitle, e.toString())
                e.printStackTrace()
                RxToDown.post(RxMsg(Const.SOCK_EV_TOAST, JSONObject().put("msg", "$logTitle: ${e.toString()}")))
            }
        }.off(Const.SOCK_EV_ALERT).on(Const.SOCK_EV_ALERT) {
            try {
                if (it[0] is String) { //check type (java.lang.String cannot be cast to org.json.JSONObject)
                    Util.log(logTitle + ": " + Const.SOCK_EV_ALERT, it[0].toString() + ": " + it[1].toString())
                } else {
                    val jsonObj = it[0] as JSONObject
                    Util.log(logTitle, jsonObj.toString())
                    RxToDown.post(RxMsg(Const.SOCK_EV_ALERT, jsonObj))
                }
            } catch (e: Exception) {
                logger.error("$logTitle: SOCK_EV_ALERT ${e.toString()}")
                Util.log(logTitle + ": " + Const.SOCK_EV_ALERT, e.toString())
                e.printStackTrace()
            }
        }.off(Const.SOCK_EV_TOAST).on(Const.SOCK_EV_TOAST) {
            try {
                if (it[0] is String) { //check type (java.lang.String cannot be cast to org.json.JSONObject)
                    Util.log(logTitle + ": " + Const.SOCK_EV_TOAST, it[0].toString() + ": " + it[1].toString())
                } else {
                    val jsonObj = it[0] as JSONObject
                    RxToDown.post(RxMsg(Const.SOCK_EV_TOAST, jsonObj))
                }
            } catch (e: Exception) {
                logger.error("$logTitle: SOCK_EV_TOAST ${e.toString()}")
                Util.log(logTitle + ": " + Const.SOCK_EV_TOAST, e.toString())
                e.printStackTrace()
            }
        }.off(Const.SOCK_EV_COMMON).on(Const.SOCK_EV_COMMON) { it ->
            try {
                val json = it[0] as JSONObject
                val jsonStr = it[0].toString() //it.get(0).toString()
                val gson = Gson().fromJson(jsonStr, JsonObject::class.java)
                Util.log("$logTitle(in)", jsonStr, it[0].javaClass.kotlin.qualifiedName!!)
                val ev = gson.get("ev").asString
                val returnTo = gson.get("returnTo").asString
                val returnToAnother = gson.get("returnToAnother")?.asString
                var needNoti = true
                val noti_off = KeyChain.get(applicationContext, Const.KC_NOTI_OFF) ?: ""
                if (noti_off == "Y") needNoti = false
                //'returnToAnother' is brought with gson since using org.json.JSONObject needs 'try catch' uncomfortably
                //Check out that using json (not 'json.get("data")'). Using gson.toString() is more inconvenient.
                //아래 returnToAnother는 org.json.JSONObject로 가져오면 try catch 필요하게 되어 번거로움 (gson으로 가져옴)
                //json.get("data")가 아닌 넘어온 객체 전체인 json임을 유의. json으로 넘기지 않고 gson.toString()에서 변환한 json시 처리가 더 불편함
                RxToDown.post(RxEvent(ev, json, returnTo, returnToAnother))
                val roomidForService = KeyChain.get(applicationContext, Const.KC_ROOMID_FOR_CHATSERVICE)
                if (returnTo != "" && returnTo == roomidForService) {
                    RxToRoom.post(RxEvent(ev, json, returnTo, returnToAnother))
                    val screenState = KeyChain.get(applicationContext, Const.KC_SCREEN_STATE) ?: ""
                    if (screenState == "on" && MainActivity.isOnTop) needNoti = false
                } else if (returnTo == "all") {
                    RxToRoom.post(RxEvent(ev, json, returnTo, returnToAnother))
                }
                if (ev == Const.SOCK_EV_SEND_MSG) {
                    if (needNoti) {
                        val data = json.getJSONObject("data")
                        val senderid = data.getString("senderid")
                        if (senderid == uInfo.userid) return@on //Util.log("@@@@@@@@@@", senderid + "====" + uInfo.userid + "====" + uInfo.userkey)
                        val msgid = data.getString("msgid")
                        val body = data.getString("body")
                        val type = data.getString("type")
                        val userkeyArr = data.getJSONArray("userkeyArr")
                        val cdt = data.getString("cdt")
                        val webConnectedAlso = userkeyArr.toString().contains(Const.W_KEY + uInfo.userid + "\"") //["W__userid1","W__userid2"]
                        val body1 = Util.getTalkBodyCustom(type, body)
                        NotiCenter.notiByRoom(applicationContext, uInfo, returnTo, body1, webConnectedAlso, msgid, cdt)
                    }
                    CoroutineScope(Dispatchers.IO).launch {
                        try {
                            val param = listOf("type" to "U")
                            HttpFuel.get(applicationContext, "${Const.DIR_ROUTE}/qry_unread", param).await()
                        } catch (e: Exception) {
                            //do nothing
                        }
                    }
                } else if (ev == Const.SOCK_EV_READ_MSG) {
                    val data = json.getJSONObject("data")
                    val type = data.getString("type")
                    if (type == "updateall" || type == "update") {
                        val userid = data.getString("userid")
                        if (userid == uInfo.userid) {
                            NotiCenter.mapRoomid[returnTo]?.let {
                                NotiCenter.manager!!.cancel(it)
                                NotiCenter.mapRoomid.remove(returnTo)
                            }
                            if (NotiCenter.mapRoomid.isEmpty()) NotiCenter.manager!!.cancel(Const.NOTI_ID_SUMMARY)
                        }
                    }
                } else if (ev == Const.SOCK_EV_SET_ENV) {
                    val data = json.getJSONObject("data")
                    val kind = data.getString("kind")
                    if (kind == "userinfo") {
                        val userid = data.getString("userid")
                        if (userid == uInfo.userid) {
                            KeyChain.set(applicationContext, Const.KC_NOTI_OFF, data.getString("notioff"))
                            KeyChain.set(applicationContext, Const.KC_SOUND_OFF, data.getString("soundoff"))
                            KeyChain.set(applicationContext, Const.KC_TM_FR, data.getString("fr"))
                            KeyChain.set(applicationContext, Const.KC_TM_TO, data.getString("to"))
                            KeyChain.set(applicationContext, Const.KC_BODY_OFF, data.getString("bodyoff"))
                            KeyChain.set(applicationContext, Const.KC_SENDER_OFF, data.getString("senderoff"))
                            uInfo = UserInfo(applicationContext) //org.json not gson
                        }
                    } else if (kind == "noti") {
                        setRoomInfo(json, ev)
                    }
                } else if (ev == Const.SOCK_EV_RENAME_ROOM) {
                    setRoomInfo(json, ev)
                } else if (ev == Const.SOCK_EV_CUT_MOBILE) {
                    val data = json.getJSONObject("data")
                    val userid = data.getString("userid")
                    if (userid == uInfo.userid) { //Util.log(userid, uInfo.userid)
                        KeyChain.set(applicationContext, Const.KC_AUTOLOGIN, "")
                        cut_mobile = true
                        stopSelf()
                        logger.debug("stopSelf..SOCK_EV_CUT_MOBILE")
                    }
                }
            } catch (e: Exception) {
                logger.error("$logTitle: SOCK_EV_COMMON ${e.toString()}")
                Util.log(logTitle + ": " + Const.SOCK_EV_COMMON, e.toString())
                e.printStackTrace()
                RxToDown.post(RxMsg(Const.SOCK_EV_ALERT, JSONObject().put("msg", "$logTitle: ${e.toString()}")))
            }
        }
    }

    inner class Daemon : Runnable { //thread!!.state => https://developer.android.com/reference/kotlin/java/lang/Thread.State
        override fun run() { //thread.stop() deprecated. thread.interrupt() used instead.
            val logTitle = object{}.javaClass.enclosingMethod?.name!!
            try {
                while (!shouldThreadStop) {
                    synchronized(this) {
                        try { //Util.log(logTitle, "client_socket_connected_check : ${SocketIO.sock!!.connected()}")
                            val screenState = KeyChain.get(applicationContext, Const.KC_SCREEN_STATE) ?: ""
                            if (screenState == "on") {
                                val autoLogin = KeyChain.get(applicationContext, Const.KC_AUTOLOGIN) ?: ""
                                if (autoLogin == "Y") {
                                    if (!isBeingSockChecked) Util.connectSockWithCallback(applicationContext, connManager!!)
                                }
                            }
                        } catch (e: InterruptedException) {
                            logger.error("$logTitle: e ${e.toString()}")
                            Util.log(logTitle, "(OK) thread interrupted")
                        } catch (e1: Exception) {
                            logger.error("$logTitle: e1 ${e1.toString()}")
                            Util.log(logTitle, "e1 ${e1.toString()}")
                        } finally {
                            try {
                                Thread.sleep(SEC_DURING_DAEMON) //Thread.sleep(sleep_sec)
                            } catch (ex11: InterruptedException) {
                                logger.error("$logTitle: ex11 ${ex11.toString()}")
                                Util.log(logTitle, "${ex11.toString()} (OK) thread interrupted finally")
                            } catch (ex12: Exception) {
                                logger.error("$logTitle: ex12 ${ex12.toString()}")
                                Util.log(logTitle, "${ex12.toString()} finally")
                            }
                        }
                    }
                }
            } catch (ex: InterruptedException) {
                logger.error("$logTitle: ex ${ex.toString()}")
                Util.log(logTitle, "${ex.toString()} (OK) thread interrupted last finally")
            } catch (ex1: Exception) {
                logger.error("$logTitle: ex1 ${ex1.toString()}")
                Util.log(logTitle, "${ex1.toString()} last finally")
            }
        }
    }

}