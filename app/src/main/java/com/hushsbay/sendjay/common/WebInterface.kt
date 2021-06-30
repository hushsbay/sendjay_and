package com.hushsbay.sendjay.common

import android.app.Activity
import android.net.ConnectivityManager
import android.webkit.JavascriptInterface
import com.hushsbay.sendjay.ChatService
import com.hushsbay.sendjay.data.RxEvent
import org.json.JSONObject

class WebInterface(private val curContext: Activity, private val connManager: ConnectivityManager) {

    //When you call these functions from javascript, you should be careful with argument's match for calling function.

    //data: JSONObject, Gson not worked => Java exception was raised during method invocation
    @JavascriptInterface //RxEvent(val ev: String, val data: Any, val returnTo: String?=null, val returnToAnother: String?=null) {
    fun send(ev: String, data: String, returnTo: String?=null, returnToAnother: String?=null, procMsg: Boolean) {
        val json = JSONObject(data) //results in "data":{"userkeys":["W__1",~ in server
        //val json = Gson().fromJson(data, JsonObject::class.java) => results in "data":"{\"userkeys\":[\"W__1\",~ in server
        RxToUp.post(RxEvent(ev, json, returnTo, returnToAnother, procMsg))
    }

    @JavascriptInterface
    fun reconnectDone() {
        ChatService.status_sock = Const.SockState.FIRST_DISCONNECTED
    }

}