package com.minimalist.launcher

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager

object GameDetector {
    @Suppress("DEPRECATION")
    fun isLikelyGame(pm: PackageManager, app: ApplicationInfo): Boolean {
        if (app.category == ApplicationInfo.CATEGORY_GAME ||
            app.flags and ApplicationInfo.FLAG_IS_GAME != 0
        ) return true

        val gameIntent = Intent(Intent.ACTION_MAIN)
            .addCategory("android.intent.category.APP_GAME")
            .setPackage(app.packageName)
        if (pm.queryIntentActivities(gameIntent, PackageManager.GET_RESOLVED_FILTER).isNotEmpty()) return true

        val packageName = app.packageName.lowercase()
        return KNOWN_GAME_PACKAGES.any { packageName == it || packageName.startsWith("$it.") }
    }

    // ponytail: conservative publisher prefixes; expand only from confirmed false negatives.
    private val KNOWN_GAME_PACKAGES = setOf(
        "com.activision", "com.ea.gp", "com.electronicarts", "com.gameloft",
        "com.king", "com.miniclip", "com.mojang", "com.nianticlabs", "com.playrix",
        "com.roblox", "com.rovio", "com.scopely", "com.supercell", "com.ubisoft", "com.zynga"
    )
}
