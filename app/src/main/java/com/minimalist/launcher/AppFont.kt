package com.minimalist.launcher

import android.content.Context
import android.app.Activity
import android.view.View
import android.view.ViewGroup
import android.widget.TextView

import android.util.TypedValue

object AppFont {
    private const val BASE_SIZE = 12f

    fun get(context: Context): Float {
        return PrefsManager.getFontSize(context)
    }

    fun applyToActivity(activity: Activity) {
        if (activity is MainActivity) return
        val selectedSize = get(activity)
        applyToAllTextViews(activity.window.decorView, selectedSize)
    }

    fun applyToAllTextViews(view: View, selectedSize: Float) {
        val scale = selectedSize / BASE_SIZE
        if (view is TextView) {
            if (view.tag == "fixed_size") return
            
            var originalSize = view.getTag(R.id.original_text_size) as? Float
            if (originalSize == null) {
                // Get current size in SP
                originalSize = view.textSize / view.resources.displayMetrics.scaledDensity
                view.setTag(R.id.original_text_size, originalSize)
            }
            
            view.setTextSize(TypedValue.COMPLEX_UNIT_SP, originalSize * scale)
        } else if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                applyToAllTextViews(view.getChildAt(i), selectedSize)
            }
        }
    }
}
