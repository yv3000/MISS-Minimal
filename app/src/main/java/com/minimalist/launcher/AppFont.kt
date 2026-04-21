package com.minimalist.launcher

import android.content.Context
import android.app.Activity
import android.view.View
import android.view.ViewGroup
import android.widget.TextView

object AppFont {
    fun get(context: Context): Float {
        return PrefsManager.getFontSize(context)
    }

    fun applyToActivity(activity: Activity) {
        val size = get(activity)
        applyToAllTextViews(activity.window.decorView, size)
    }

    fun applyToAllTextViews(view: View, size: Float) {
        if (view is TextView) {
            if (view.tag == "fixed_size") return
            view.textSize = size
        } else if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                applyToAllTextViews(view.getChildAt(i), size)
            }
        }
    }
}
