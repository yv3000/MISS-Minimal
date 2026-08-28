package com.minimalist.launcher

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class TermsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_terms)

        // Linktree link click
        findViewById<TextView>(R.id.tvLinktree).setOnClickListener {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://linktr.ee/yv_3000")))
        }
    }

    override fun onBackPressed() {
        super.onBackPressed()
    }
}
