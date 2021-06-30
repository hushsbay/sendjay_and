package com.hushsbay.sendjay.common

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class KeyChainDbHelper(context: Context) : SQLiteOpenHelper(context, KeyChainProvider.DB_NAME, null, 1) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("create table " + KeyChainProvider.TABLE_NAME +
                        " (" + KeyChainProvider.COL_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " + KeyChainProvider.COL_NAME + " TEXT, " + KeyChainProvider.COL_VALUE+ " TEXT)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("drop table if exists " + KeyChainProvider.TABLE_NAME)
        onCreate(db)
    }

}