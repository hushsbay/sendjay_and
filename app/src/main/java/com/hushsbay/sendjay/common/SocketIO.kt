package com.hushsbay.sendjay.common

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.hushsbay.sendjay.ChatService
import io.socket.client.IO
import io.socket.client.Socket
import kotlinx.coroutines.*

object SocketIO { //https://socketio.github.io/socket.io-client-java/initialization.html

    var sock: Socket? = null

    //SupervisorJob is used to prevent Error (Parent Job is cancelled)
    //Parent Job is cancelled 라는 오류 방지를 위해 SupervisorJob으로 처리
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private const val DELAY = 500L
    private const val SEC = 3000L

    operator fun invoke(context: Context, uInfo: UserInfo, winid: String, userip: String) {
        val option: IO.Options = IO.Options().apply {
            forceNew = false //default=false //See 'disconnect_prev_sock' in pmessage.js (on server)
            reconnection = false
            query = "${Const.KC_TOKEN}=${uInfo.token}&${Const.KC_USERID}=${uInfo.userid}&${Const.KC_USERKEY}=${uInfo.userkey}&winid=${winid}&userip=${userip}"
        }
        sock = IO.socket(KeyChain.get(context, Const.KC_MODE_SOCK).toString(), option)
    }

    fun connect(context: Context, connManager: ConnectivityManager): Deferred<JsonObject> {
        return scope.async {
            try {
                var code = Const.RESULT_OK
                var msg = ""
                val nwCapa = connManager.getNetworkCapabilities(connManager.activeNetwork)
                if (nwCapa != null && nwCapa.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) {
                    if (ChatService.state == Const.ServiceState.LOGOUTED) {
                        code = Const.RESULT_ERR
                        msg = "App logout."
                    } else if (ChatService.state == Const.ServiceState.STOPPED) {
                        val intentNew = Intent(context, ChatService::class.java)
                        context.startForegroundService(intentNew)
                        val result = chkConnected().await()
                        if (result == null) {
                            code = Const.RESULT_ERR
                            msg = "Service started but socket connect timeout."
                        }
                    } else { //Const.ServiceState.RUNNING
                        if (sock == null) {
                            code = Const.RESULT_ERR
                            msg = "Socket not ready yet."
                        } else if (!sock!!.connected()) {
                            Util.log("SocketIO", "reconnecting..")
                            SocketIO.sock!!.connect() //cannot connect on doze mode
                            val result = chkConnected().await()
                            if (result == null) {
                                code = Const.RESULT_ERR
                                msg = "Unable to connect to socket server."
                            }
                        }
                    }
                } else {
                    code = Const.RESULT_ERR
                    msg = "Network not available."
                }
                if (msg != "") msg += "\n${Const.WAIT_FOR_RECONNECT}"
                val jsonStr = """{ code : '$code', msg : '${Const.TITLE}: $msg' }"""
                Gson().fromJson(jsonStr, JsonObject::class.java)
            } catch (e: Exception) {
                val jsonStr = """{ code : '${Const.RESULT_ERR}', msg : '${Const.TITLE}:SocketIO:connect: ${e.message}' }"""
                Gson().fromJson(jsonStr, JsonObject::class.java)
            }
        }
    }

    private fun chkConnected(): Deferred<String?> {
        return CoroutineScope(Dispatchers.Default).async {
            val result = withTimeoutOrNull(SEC) {
                while (true) {
                    if (sock!!.connected()) break
                    delay(DELAY)
                }
                "Done"
            }
            result
        }
    }

}