package com.hushsbay.sendjay

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import androidx.core.content.ContextCompat.getSystemService
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.hushsbay.sendjay.common.*
import org.apache.log4j.Logger

class SimpleWorker(context: Context, workerParameters: WorkerParameters) : Worker(context, workerParameters) {

    private val logger: Logger = LogHelper.getLogger(context, this::class.simpleName)
    private val connManager = applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private var context = context

    override fun doWork(): Result {
        val logTitle = object{}.javaClass.enclosingMethod?.name!!
        try {
            val autoLogin = KeyChain.get(applicationContext, Const.KC_AUTOLOGIN) ?: ""
            if (autoLogin == "Y") { //Do not use Util.connectSockWithCallback().
                if (ChatService.state == Const.ServiceState.STOPPED) { //Just restart ChatService.kt
                    val intentNew = Intent(context, ChatService::class.java)
                    context.startForegroundService(intentNew)
                }
            } else {
                val intent = Intent(context, ChatService::class.java)
                context.stopService(intent)
            }
        } catch (e: Exception) {
            logger.error("$logTitle: ${e.toString()}")
            Util.log(logTitle, "${e.toString()}")
        }
        return Result.success()
    }

}