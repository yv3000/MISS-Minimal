package com.minimalist.launcher

import android.content.Context
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.TextView

object FontManager {
    private const val PREFS_NAME = "font_prefs"
    private const val KEY_SIZE = "font_size"
    private const val DEFAULT_SIZE = 14f

    fun save(context: Context, size: Float) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putFloat(KEY_SIZE, size).apply()
    }

    fun get(context: Context): Float {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getFloat(KEY_SIZE, DEFAULT_SIZE)
    }

    // Apply to a single TextView safely
    fun apply(view: TextView, context: Context) {
        if (view.tag == "fixed_size") return
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, get(context))
    }

    // Apply to entire view hierarchy
    fun applyToHierarchy(root: View, context: Context) {
        when {
            root.tag == "fixed_size" -> return
            root is TextView -> apply(root, context)
            root is ViewGroup -> {
                for (i in 0 until root.childCount) {
                    applyToHierarchy(root.getChildAt(i), context)
                }
            }
        }
    }

    // Apply to RecyclerView Adapter — call in onBindViewHolder
    fun applyToViewHolder(itemView: View, context: Context) {
        applyToHierarchy(itemView, context)
    }
}
