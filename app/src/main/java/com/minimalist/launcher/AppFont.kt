package com.minimalist.launcher

import android.app.Activity
import android.content.Context
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.TextView

object AppFont {

    fun get(context: Context): Float = PrefsManager.getFontSize(context)

    /**
     * Screens that own their typography and must NOT follow the global "Font Size" setting:
     * Home (clock/app list), Quick Settings panel, Focus screen.
     */
    private fun isExcluded(activity: Activity) =
        activity is MainActivity || activity is QuickSettingsActivity || activity is FocusActivity

    fun applyToActivity(activity: Activity) {
        if (isExcluded(activity)) return
        applyToAllTextViews(activity.window.decorView, get(activity))
    }

    /**
     * Sets every TextView (incl. Button/EditText subclasses) to the exact size the user picked.
     * Opt out of a single view with android:tag="fixed_size".
     */
    fun applyToAllTextViews(view: View, selectedSize: Float) {
        if (view.tag == "fixed_size") return
        if (view is TextView) {
            view.setTextSize(TypedValue.COMPLEX_UNIT_SP, selectedSize)
        } else if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                applyToAllTextViews(view.getChildAt(i), selectedSize)
            }
        }
    }

    /** For RecyclerView rows — call from onBindViewHolder, rows inflate after onResume. */
    fun applyToRow(itemView: View) {
        applyToAllTextViews(itemView, get(itemView.context))
    }
}
