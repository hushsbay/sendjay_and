package com.hushsbay.sendjay.common

import android.content.Context
import org.apache.log4j.DailyRollingFileAppender
import org.apache.log4j.Level
import org.apache.log4j.Logger
import org.apache.log4j.PatternLayout

object LogHelper {

    //https://velog.io/@hanna2100/Log4j%EB%A1%9C-%EC%95%88%EB%93%9C%EB%A1%9C%EC%9D%B4%EB%93%9C%EC%97%90-%EB%82%A0%EC%A7%9C%EB%B3%84%EB%A1%9C-%EB%A1%9C%EA%B7%B8%ED%8C%8C%EC%9D%BC-%EC%83%9D%EC%84%B1

    private fun configuration(context: Context) {
        val patternLayout = createPatternLayout(context)
        val rollingAppender = createDailyRollingLogFileAppender(context, patternLayout)
        setAppenderWithRootLogger(rollingAppender)
    }

    private fun createPatternLayout(context: Context): PatternLayout {
        val patternLayout = PatternLayout()
        val conversionPattern = "[%d] %c %M - [%p] %m%n"
        patternLayout.conversionPattern = conversionPattern
        return patternLayout
    }

    private fun createDailyRollingLogFileAppender(context: Context, patternLayout: PatternLayout): DailyRollingFileAppender {
        val rollingAppender = DailyRollingFileAppender()
        val path = context.filesDir.toString() ///data/user/0/com.hushsbay.sendjay/files
        //val path = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS).toString() ///storage/emulated/0/Android/data/com.hushsbay.sendjay/files/Documents
        val fileName = "$path/LogFile"
        rollingAppender.file = fileName
        rollingAppender.datePattern = "'.'yyyy-MM-dd" //"'.'yyyy-MM-dd-HH-mm'.txt'" when you check the log files right away
        rollingAppender.layout = patternLayout
        rollingAppender.activateOptions()
        return rollingAppender
    }

    private fun setAppenderWithRootLogger(rollingAppender: DailyRollingFileAppender) {
        val rootLogger = Logger.getRootLogger()
        rootLogger.level = Level.DEBUG
        rootLogger.addAppender(rollingAppender)
    }

    fun getLogger(context: Context, name: String?): Logger {
        configuration(context)
        return Logger.getLogger(name)
    }

}