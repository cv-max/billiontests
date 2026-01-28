package com.billiontests.app.utils

import java.io.IOException

object LogcatToFile {

    fun startLogging(saveFilePath:String) {
        val filePath = "${saveFilePath}/logcat.txt"
        try {
            Runtime.getRuntime().exec("logcat -f $saveFilePath")
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

}