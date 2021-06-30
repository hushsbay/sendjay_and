package com.hushsbay.sendjay.common

import android.content.Context
import com.google.gson.JsonObject

class UserInfo { //See Util.getStrObjFromUserInfo() also.

    var token: String
    var userid: String
    var userkey: String
    var usernm: String
    var passkey: String
    var orgcd: String
    var role: String

    var notioff: String
    var soundoff: String
    var fr: String
    var to: String
    var bodyoff: String
    var senderoff: String

    constructor(context: Context, json: JsonObject) { //gson (not org.json)
        this.token = json.get(Const.KC_TOKEN).asString
        this.userid = json.get(Const.KC_USERID).asString
        this.userkey = Const.M_KEY + this.userid
        this.usernm = json.get(Const.KC_USERNM).asString
        this.passkey = json.get(Const.KC_PASSKEY).asString
        this.orgcd = json.get(Const.KC_ORGCD).asString
        this.role = json.get(Const.KC_ROLE).asString
        this.notioff = json.get(Const.KC_NOTI_OFF).asString
        this.soundoff = json.get(Const.KC_SOUND_OFF).asString
        this.fr = json.get(Const.KC_TM_FR).asString
        this.to = json.get(Const.KC_TM_TO).asString
        this.bodyoff = json.get(Const.KC_BODY_OFF).asString
        this.senderoff = json.get(Const.KC_SENDER_OFF).asString
        KeyChain.set(context, Const.KC_TOKEN, this.token)
        KeyChain.set(context, Const.KC_USERID, this.userid)
        KeyChain.set(context, Const.KC_USERKEY, this.userkey)
        KeyChain.set(context, Const.KC_USERNM, this.usernm)
        KeyChain.set(context, Const.KC_PASSKEY, this.passkey)
        KeyChain.set(context, Const.KC_ORGCD, this.orgcd)
        KeyChain.set(context, Const.KC_ROLE, this.role)
        KeyChain.set(context, Const.KC_NOTI_OFF, this.notioff)
        KeyChain.set(context, Const.KC_SOUND_OFF, this.soundoff)
        KeyChain.set(context, Const.KC_TM_FR, this.fr)
        KeyChain.set(context, Const.KC_TM_TO, this.to)
        KeyChain.set(context, Const.KC_BODY_OFF, this.bodyoff)
        KeyChain.set(context, Const.KC_SENDER_OFF, this.senderoff)
    }

    constructor(context: Context) {
        this.token = KeyChain.get(context, Const.KC_TOKEN) ?: ""
        this.userid = KeyChain.get(context, Const.KC_USERID) ?: ""
        this.userkey = KeyChain.get(context, Const.KC_USERKEY) ?: ""
        this.usernm = KeyChain.get(context, Const.KC_USERNM) ?: ""
        this.passkey = KeyChain.get(context, Const.KC_PASSKEY) ?: ""
        this.orgcd = KeyChain.get(context, Const.KC_ORGCD) ?: ""
        this.role = KeyChain.get(context, Const.KC_ROLE) ?: ""
        this.notioff = KeyChain.get(context, Const.KC_NOTI_OFF) ?: ""
        this.soundoff = KeyChain.get(context, Const.KC_SOUND_OFF) ?: ""
        this.fr = KeyChain.get(context, Const.KC_TM_FR) ?: ""
        this.to = KeyChain.get(context, Const.KC_TM_TO) ?: ""
        this.bodyoff = KeyChain.get(context, Const.KC_BODY_OFF) ?: ""
        this.senderoff = KeyChain.get(context, Const.KC_SENDER_OFF) ?: ""
    }

}