package com.queukat.sbsgeorgia.data.importer

import android.content.Context
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PdfBoxInitializer
@Inject
constructor(@param:ApplicationContext private val context: Context) {
    @Volatile
    private var initialized = false

    fun ensureInitialized() {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            PDFBoxResourceLoader.init(context)
            initialized = true
        }
    }
}
