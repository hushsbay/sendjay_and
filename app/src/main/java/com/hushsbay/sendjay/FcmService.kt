package com.hushsbay.sendjay

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.hushsbay.sendjay.common.*

class FcmService : FirebaseMessagingService() {

    private var uInfo: UserInfo? = null

    //onNewToken is executed during installation so KeyChain.get() should be used carefully.
    //onNewToken은 앱 설치시 실행되므로 KeyChain.get() 사용에 유의.
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Util.log("FcmService", "new Token: $token")
        KeyChain.set(applicationContext, Const.KC_PUSHTOKEN, token)
    }

    //https://stackoverflow.com/questions/60781038/android-notification-using-fcm/60781500#60781500 (not receiver but service)
    //BroadcastReciver not needed (This method worked when background, foreground, dozemode, socket_disconnected, app killed)
    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        Util.log("FcmService", "onMessageReceived")
        try {
            //Const.KC_AUTOLOGIN!=Y means logout and push messages are igonred when logout.
            //Const.KC_AUTOLOGIN!=Y는 로그아웃 상태이며 Push 메시지 무시함.
            val autoLogin = KeyChain.get(applicationContext, Const.KC_AUTOLOGIN) ?: ""
            if (autoLogin != "Y") return
            if (uInfo == null) uInfo = UserInfo(applicationContext)
            if (remoteMessage.data.isNotEmpty()) {
                val msgid = remoteMessage.data["msgid"].toString()
                val senderkey = remoteMessage.data["senderkey"].toString()
                val senderid = remoteMessage.data["senderid"].toString()
                val body = remoteMessage.data["body"].toString()
                val type = remoteMessage.data["type"].toString()
                val userkeyArrStr = remoteMessage.data["userkeyArr"].toString()
                val roomid = remoteMessage.data["roomid"].toString()
                val cdt = remoteMessage.data["cdt"].toString()
                if (senderid == uInfo!!.userid && senderkey != uInfo!!.userkey) return
                val webConnectedAlso = userkeyArrStr.contains(Const.W_KEY + uInfo!!.userid + "\"") //["W__userid1","W__userid2"]
                val body1 = Util.getTalkBodyCustom(type, body)
                NotiCenter.notiByRoom(applicationContext, uInfo!!, roomid, body1, webConnectedAlso, msgid, cdt)
            }
        } catch (e: Exception) {
            Util.log("FcmService", e.toString())
            e.printStackTrace()
        }
    }

}