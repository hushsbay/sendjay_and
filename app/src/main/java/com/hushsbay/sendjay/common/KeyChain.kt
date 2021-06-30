package com.hushsbay.sendjay.common

import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.net.Uri

object KeyChain {

    private const val URISTR_NAME = KeyChainProvider.URI + "/" + KeyChainProvider.COL_NAME + "/"
    private var cr: ContentResolver? = null

    fun get(context: Context, name: String): String? {
        if (cr == null) cr = context.contentResolver
        var value: String? = null
        val cursor: Cursor? = cr!!.query(Uri.parse(URISTR_NAME + name), null, null, null, null)
        if (cursor != null && cursor.count > 0) {
            cursor.moveToNext()
            value = cursor.getString(1) //name:0, value:1
        }
        cursor?.close()
        return value
    }

    fun set(context: Context, name: String, value: String?): Boolean {
        if (cr == null) cr = context.contentResolver
        val cursor: Cursor? = cr!!.query(Uri.parse(URISTR_NAME + name), null, null, null, null)
        val row = ContentValues()
        if (cursor != null && cursor.count > 0) {
            row.put(KeyChainProvider.COL_VALUE, value)
            val result = cr!!.update(Uri.parse(URISTR_NAME + name), row, null, null)
            if (result == 0) return false
        } else {
            row.put(KeyChainProvider.COL_NAME, name)
            row.put(KeyChainProvider.COL_VALUE, value)
            val uri = cr!!.insert(Uri.parse(KeyChainProvider.URI), row) ?: return false
        }
        cursor?.close()
        return true
    }

    fun delete(context: Context, name: String): Boolean {
        if (cr == null) cr = context.contentResolver
        val result = cr!!.delete(Uri.parse(URISTR_NAME + name), null, null)
        return result != 0 //if (result == 0) false else true
    }

    fun deleteAll(context: Context): Boolean {
        if (cr == null) cr = context.getContentResolver()
        val result = cr!!.delete(Uri.parse(KeyChainProvider.URI), null, null)
        return result != 0
    }

}