package com.hushsbay.sendjay.common

import android.content.Context
import com.github.kittinunf.fuel.Fuel
import com.github.kittinunf.fuel.core.Headers
import com.github.kittinunf.fuel.coroutines.awaitString
import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.*

object HttpFuel { //Fuel is single instance and uses gson(com.google.gson.JsonObject).

    //SupervisorJob is used to prevent Error (Parent Job is cancelled)
    //Parent Job is cancelled 라는 오류 방지를 위해 SupervisorJob으로 처리
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    fun get(context: Context, url: String, param: List<Pair<String, Any?>>?=null): Deferred<JsonObject> {
        return scope.async {
            try {
                var url = if (url.startsWith("http")) url else KeyChain.get(context, Const.KC_MODE_SERVER).toString() + url
                val token = KeyChain.get(context, Const.KC_TOKEN) ?: ""
                val uid = KeyChain.get(context, Const.KC_USERID) ?: ""
                val userkey = KeyChain.get(context, Const.KC_USERKEY) ?: ""
                val passkey = KeyChain.get(context, Const.KC_PASSKEY) ?: ""
                val cookieStr = "${Const.KC_USERID}=$uid; ${Const.KC_USERKEY}=$userkey; ${Const.KC_TOKEN}=$token; ${Const.KC_PASSKEY}=$passkey"
                val paramAuth = arrayOf(Headers.COOKIE to cookieStr)
                val noCache = listOf("noCache" to Util.getRnd().toString())
                val paramNoCache = if (param == null) noCache else param.plus(noCache)
                val jsonStr = Fuel.get(url, paramNoCache).appendHeader(*paramAuth).timeout(Const.RESTFUL_TIMEOUT).awaitString() //*(spread) for vararg
                val json = Gson().fromJson(jsonStr, JsonObject::class.java) //val jsonStr = Gson().toJson(jsonObject)
                json
            } catch (e: Exception) {
                val jsonStr = """{ code : '${Const.RESULT_ERR_HTTPFUEL}', msg : 'HttpFuel:get: ${e.message}' }"""
                Gson().fromJson(jsonStr, JsonObject::class.java)
            }
        }
    }

    //val param = """{ svc_id : 'chk_version', id : '${Proj.APP_CHKID}', ver : '${BuildConfig.VERSION_NAME}' }""" => not worked
    //val param = "{ \"type\" : \"M\" }" //val param = """{ type : 'M' }""" => worked but never ok with jsonBody(param)
    fun post(context: Context, url: String, param: String): Deferred<JsonObject> {
        return scope.async {
            try {
                var url = if (url.startsWith("http")) url else KeyChain.get(context, Const.KC_MODE_SERVER).toString() + url
                val token = KeyChain.get(context, Const.KC_TOKEN) ?: ""
                val uid = KeyChain.get(context, Const.KC_USERID) ?: ""
                val userkey = KeyChain.get(context, Const.KC_USERKEY) ?: ""
                val passkey = KeyChain.get(context, Const.KC_PASSKEY) ?: ""
                val cookieStr = "${Const.KC_USERID}=$uid; ${Const.KC_USERKEY}=$userkey; ${Const.KC_TOKEN}=$token; ${Const.KC_PASSKEY}=$passkey"
                val paramAuth = arrayOf(Headers.CONTENT_TYPE to "application/json", Headers.COOKIE to cookieStr) //application/x-www-form-urlencoded (when CORS needed)
                val jsonStr = Fuel.post(url).body(param).appendHeader(*paramAuth).timeout(Const.RESTFUL_TIMEOUT).awaitString()
                val json = Gson().fromJson(jsonStr, JsonObject::class.java)
                json
            } catch (e: Exception) {
                val jsonStr = """{ code : '${Const.RESULT_ERR_HTTPFUEL}', msg : 'HttpFuel:post: ${e.message}' }"""
                Gson().fromJson(jsonStr, JsonObject::class.java)
            }
        }
    }

}