package com.hushsbay.sendjay.data

data class RxEvent(val ev: String, val data: Any, val returnTo: String?=null, val returnToAnother: String?=null, val procMsg: Boolean?=null) {
    //procMsg is used for RxToUp (procSocketEmit() in ChatService.kt only)
}