package com.minimalist.launcher

import android.content.Context

object AppFont {
    fun get(context: Context): Float {
        return PrefsManager.getFontSize(context)
    }
}
