package com.ezygo.attendance

import android.app.Application
import com.ezygo.attendance.notifications.Reminders
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class EzygoApp : Application() {

    override fun onCreate() {
        super.onCreate()
        // PdfBox keeps its font metrics in the APK's assets and has to be pointed at
        // them once per process before any document is opened.
        PDFBoxResourceLoader.init(this)
        Reminders.ensureChannel(this)
    }
}
