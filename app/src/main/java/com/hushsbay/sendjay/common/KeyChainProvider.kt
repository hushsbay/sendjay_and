package com.hushsbay.sendjay.common

import android.content.ContentProvider
import android.content.ContentUris
import android.content.ContentValues
import android.content.UriMatcher
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.net.Uri

class KeyChainProvider : ContentProvider() {

    companion object {
        const val DB_NAME = "${Const.APP_NAME}.db"
        const val TABLE_NAME = "keychain"
        const val COL_ID = "_id"
        const val COL_NAME = "name"
        const val COL_VALUE = "value"
        const val AUTHORITY = Const.PROVIDER_AUTHORITY
        const val URI = "content://$AUTHORITY/$TABLE_NAME"
        const val ALL = 1
        const val BYID = 2
        const val BYNAME = 3
        val matcher = UriMatcher(UriMatcher.NO_MATCH)
    }

    private var mDB: SQLiteDatabase? = null

    init {
        matcher.addURI(AUTHORITY, TABLE_NAME, ALL)
        matcher.addURI(AUTHORITY, "$TABLE_NAME/#", BYID) //# for Integer
        matcher.addURI(AUTHORITY, "$TABLE_NAME/$COL_NAME/*", BYNAME) //* for String
    }

    override fun onCreate(): Boolean {
        val helper = KeyChainDbHelper(context!!)
        mDB = helper.writableDatabase
        return true
    }

    override fun getType(uri: Uri): String? {
        if (matcher.match(uri) == ALL) {
            return "vnd.com.${Const.APP_NAME}.cursor.dir/item"
        } else if (matcher.match(uri) == BYID) {
            return "vnd.com.${Const.APP_NAME}.cursor.item/item"
        }
        return null
    }

    override fun query(uri: Uri, projection: Array<String?>?, selection: String?, selectionArgs: Array<String?>?, sortOrder: String?): Cursor? {
        var sql = "select $COL_NAME, $COL_VALUE from $TABLE_NAME"
        if (matcher.match(uri) == BYID) {
            sql += " where " + COL_ID + " = '" + uri.pathSegments[1] + "'"
        } else if (matcher.match(uri) == BYNAME) {
            sql += " where " + COL_NAME + " = '" + uri.pathSegments[2] + "'"
        }
        return mDB!!.rawQuery(sql, null)
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? {
        if (matcher.match(uri) != ALL) return null
        val row = mDB!!.insert(TABLE_NAME, null, values)
        if (row > 0) {
            val notiUri: Uri = ContentUris.withAppendedId(Uri.parse(URI), row)
            context!!.contentResolver.notifyChange(notiUri, null)
            return notiUri
        } else {
            return null
        }
    }

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<String?>?): Int {
        var count = 0
        count = when (matcher.match(uri)) {
            ALL -> {
                mDB!!.delete(TABLE_NAME, selection, selectionArgs)
            }
            BYNAME -> {
                val where: String = COL_NAME + " = '" + uri.pathSegments[2] + "'"
                mDB!!.delete(TABLE_NAME, where, selectionArgs)
            }
            else -> return count
        }
        context!!.contentResolver.notifyChange(uri, null)
        return count
    }

    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<String?>?): Int {
        var count = 0
        count = when (matcher.match(uri)) {
            BYNAME -> {
                val where: String = COL_NAME + " = '" + uri.pathSegments[2] + "'"
                //if (TextUtils.isEmpty(selection) == false) where += " and " + selection
                mDB!!.update(TABLE_NAME, values, where, selectionArgs)
            }
            else -> return count
        }
        context!!.contentResolver.notifyChange(uri, null)
        return count
    }

}