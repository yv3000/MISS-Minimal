package com.minimalist.launcher

import android.app.AlertDialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.ContactsContract
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class PomodoroActivity : AppCompatActivity() {

    private lateinit var layoutSetup: LinearLayout
    private lateinit var layoutActive: LinearLayout

    private lateinit var btnDur25: TextView
    private lateinit var btnDur50: TextView
    private lateinit var btnDur75: TextView
    private lateinit var btnDur100: TextView

    private lateinit var slotApp1: TextView
    private lateinit var slotApp2: TextView
    private lateinit var slotApp3: TextView

    private lateinit var tvContactName: TextView
    private lateinit var btnRemoveContact: TextView
    private lateinit var layoutContact: LinearLayout
    private lateinit var btnStartPomodoro: TextView

    private lateinit var tvPhaseLabel: TextView
    private lateinit var tvCountdown: TextView
    private lateinit var tvSessionCount: TextView
    private lateinit var btnCallContact: TextView
    private lateinit var btnCancelBreak: TextView

    private var selectedDurationMins = 25
    private val selectedApps = mutableListOf<String>()
    
    // Contact state
    private var contactName: String? = null
    private var contactNumber: String? = null

    private lateinit var vibrator: Vibrator

    private val timerReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent == null) return
            if (intent.action == PomodoroTimerService.BROADCAST_TICK) {
                val remaining = intent.getIntExtra("remaining", 0)
                val isWork = intent.getBooleanExtra("is_work", true)
                updateTimerUI(remaining, isWork)
            } else if (intent.action == PomodoroTimerService.BROADCAST_PHASE_CHANGE) {
                val isWork = intent.getBooleanExtra("is_work", true)
                val session = intent.getIntExtra("session", 1)
                onPhaseChanged(isWork, session)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_pomodoro)

        vibrator = if (Build.VERSION.SDK_INT >= 31)
            getSystemService(VibratorManager::class.java).defaultVibrator
        else
            @Suppress("DEPRECATION") getSystemService(VIBRATOR_SERVICE) as Vibrator

        bindViews()
        setupListeners()
    }

    override fun onResume() {
        super.onResume()
        AppFont.applyToActivity(this)
        
        val filter = IntentFilter().apply {
            addAction(PomodoroTimerService.BROADCAST_TICK)
            addAction(PomodoroTimerService.BROADCAST_PHASE_CHANGE)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(timerReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(timerReceiver, filter)
        }

        if (PomodoroManager.isActive) {
            showActiveScreen()
        } else {
            showSetupScreen()
            updateDurationSelection(selectedDurationMins)
        }
    }

    override fun onPause() {
        super.onPause()
        try {
            unregisterReceiver(timerReceiver)
        } catch (e: Exception) {}
    }

    private fun bindViews() {
        layoutSetup = findViewById(R.id.layoutSetup)
        layoutActive = findViewById(R.id.layoutActive)

        btnDur25 = findViewById(R.id.btnDur25)
        btnDur50 = findViewById(R.id.btnDur50)
        btnDur75 = findViewById(R.id.btnDur75)
        btnDur100 = findViewById(R.id.btnDur100)

        slotApp1 = findViewById(R.id.slotApp1)
        slotApp2 = findViewById(R.id.slotApp2)
        slotApp3 = findViewById(R.id.slotApp3)

        tvContactName = findViewById(R.id.tvContactName)
        btnRemoveContact = findViewById(R.id.btnRemoveContact)
        layoutContact = findViewById(R.id.layoutContact)
        btnStartPomodoro = findViewById(R.id.btnStartPomodoro)

        tvPhaseLabel = findViewById(R.id.tvPhaseLabel)
        tvCountdown = findViewById(R.id.tvCountdown)
        tvSessionCount = findViewById(R.id.tvSessionCount)
        btnCallContact = findViewById(R.id.btnCallContact)
        btnCancelBreak = findViewById(R.id.btnCancelBreak)
    }

    private fun setupListeners() {
        btnDur25.setOnClickListener { updateDurationSelection(25) }
        btnDur50.setOnClickListener { updateDurationSelection(50) }
        btnDur75.setOnClickListener { updateDurationSelection(75) }
        btnDur100.setOnClickListener { updateDurationSelection(100) }

        slotApp1.setOnClickListener { handleAppSlotTap(0) }
        slotApp2.setOnClickListener { handleAppSlotTap(1) }
        slotApp3.setOnClickListener { handleAppSlotTap(2) }

        slotApp1.setOnLongClickListener { removeAppFromSlot(0); true }
        slotApp2.setOnLongClickListener { removeAppFromSlot(1); true }
        slotApp3.setOnLongClickListener { removeAppFromSlot(2); true }

        layoutContact.setOnClickListener { openContactPicker() }
        btnRemoveContact.setOnClickListener { 
            contactName = null
            contactNumber = null
            updateContactUI()
        }

        btnStartPomodoro.setOnClickListener { startPomodoro() }

        btnCallContact.setOnClickListener { callEmergencyContact() }
        btnCancelBreak.setOnClickListener {
            AlertDialog.Builder(this)
                .setMessage("End Pomodoro session?")
                .setPositiveButton("YES") { _, _ -> endPomodoroSession() }
                .setNegativeButton("NO", null)
                .show()
        }
    }

    private fun updateDurationSelection(mins: Int) {
        selectedDurationMins = mins
        val bgNormal = R.drawable.btn_border
        val bgActive = R.drawable.btn_border_active
        val cNormal = android.graphics.Color.parseColor("#666666")
        val cActive = android.graphics.Color.WHITE

        btnDur25.setBackgroundResource(if (mins == 25) bgActive else bgNormal)
        btnDur25.setTextColor(if (mins == 25) cActive else cNormal)
        
        btnDur50.setBackgroundResource(if (mins == 50) bgActive else bgNormal)
        btnDur50.setTextColor(if (mins == 50) cActive else cNormal)

        btnDur75.setBackgroundResource(if (mins == 75) bgActive else bgNormal)
        btnDur75.setTextColor(if (mins == 75) cActive else cNormal)

        btnDur100.setBackgroundResource(if (mins == 100) bgActive else bgNormal)
        btnDur100.setTextColor(if (mins == 100) cActive else cNormal)
    }

    private fun handleAppSlotTap(index: Int) {
        if (index < selectedApps.size) {
            // Already filled, hint to long press
            Toast.makeText(this, "Long press to remove app", Toast.LENGTH_SHORT).show()
        } else {
            // Open full screen app picker
            val intent = Intent(this, AppPickerActivity::class.java)
            intent.putExtra("pomodoro_mode", true)
            startActivityForResult(intent, 2001)
        }
    }

    private fun removeAppFromSlot(index: Int) {
        if (index < selectedApps.size) {
            selectedApps.removeAt(index)
            updateAppSlotsUI()
        }
    }

    private fun updateAppSlotsUI() {
        fun styleSlot(tv: TextView, pkg: String?) {
            if (pkg == null) {
                tv.text = "+ add"
                tv.setTextColor(android.graphics.Color.parseColor("#666666"))
            } else {
                val pm = packageManager
                try {
                    val info = pm.getApplicationInfo(pkg, 0)
                    var name = pm.getApplicationLabel(info).toString()
                    if (name.length > 8) name = name.substring(0, 8) + "…"
                    tv.text = name
                    tv.setTextColor(android.graphics.Color.WHITE)
                } catch (e: Exception) {
                    tv.text = "App"
                }
            }
        }

        styleSlot(slotApp1, selectedApps.getOrNull(0))
        styleSlot(slotApp2, selectedApps.getOrNull(1))
        styleSlot(slotApp3, selectedApps.getOrNull(2))
    }

    private fun openContactPicker() {
        if (checkSelfPermission(android.Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(android.Manifest.permission.READ_CONTACTS), 2002)
            return
        }
        val intent = Intent(Intent.ACTION_PICK, ContactsContract.Contacts.CONTENT_URI)
        startActivityForResult(intent, 2003)
    }

    private fun updateContactUI() {
        if (contactName == null) {
            tvContactName.text = "+ select contact"
            btnRemoveContact.visibility = View.GONE
        } else {
            tvContactName.text = contactName
            btnRemoveContact.visibility = View.VISIBLE
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 2001 && resultCode == RESULT_OK && data != null) {
            val pkg = data.getStringExtra("package_name") ?: return
            if (selectedApps.size < 3 && !selectedApps.contains(pkg)) {
                selectedApps.add(pkg)
                updateAppSlotsUI()
            }
        } else if (requestCode == 2003 && resultCode == RESULT_OK && data != null) {
            val contactUri = data.data ?: return
            val cursor = contentResolver.query(contactUri, null, null, null, null)
            if (cursor != null && cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME)
                val idIndex = cursor.getColumnIndex(ContactsContract.Contacts._ID)
                val hasPhoneIndex = cursor.getColumnIndex(ContactsContract.Contacts.HAS_PHONE_NUMBER)
                
                if (nameIndex != -1 && idIndex != -1 && hasPhoneIndex != -1) {
                    val name = cursor.getString(nameIndex)
                    val id = cursor.getString(idIndex)
                    val hasPhone = cursor.getString(hasPhoneIndex).toInt() > 0

                    if (hasPhone) {
                        val pCur = contentResolver.query(
                            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                            null,
                            ContactsContract.CommonDataKinds.Phone.CONTACT_ID + " = ?",
                            arrayOf(id), null
                        )
                        if (pCur != null && pCur.moveToFirst()) {
                            val numIndex = pCur.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                            if (numIndex != -1) {
                                contactNumber = pCur.getString(numIndex)
                                contactName = name
                                updateContactUI()
                            }
                            pCur.close()
                        }
                    } else {
                        Toast.makeText(this, "Contact has no phone number", Toast.LENGTH_SHORT).show()
                    }
                }
                cursor.close()
            }
        }
    }

    private fun startPomodoro() {
        // Start Manager
        PomodoroManager.start(this, selectedDurationMins, selectedApps, contactName)
        
        // Start Foreground Service Timer
        val intent = Intent(this, PomodoroTimerService::class.java)
        intent.action = PomodoroTimerService.ACTION_START
        intent.putExtra(PomodoroTimerService.EXTRA_DURATION, selectedDurationMins * 60)
        startService(intent)

        showActiveScreen()
    }

    private fun showSetupScreen() {
        layoutSetup.visibility = View.VISIBLE
        layoutActive.visibility = View.GONE
    }

    private fun showActiveScreen() {
        layoutSetup.visibility = View.GONE
        layoutActive.visibility = View.VISIBLE

        tvSessionCount.text = "session ${PomodoroManager.sessionCount} of ∞"
        
        if (contactNumber != null) {
            btnCallContact.text = "CALL $contactName"
            btnCallContact.visibility = View.VISIBLE
        } else {
            btnCallContact.visibility = View.GONE
        }

        updatePhaseUI()
    }

    private fun updatePhaseUI() {
        if (PomodoroManager.isWorkPhase) {
            tvPhaseLabel.text = "POMODORO"
            tvPhaseLabel.setTextColor(android.graphics.Color.parseColor("#333333"))
            btnCancelBreak.visibility = View.GONE
        } else {
            tvPhaseLabel.text = "BREAK"
            tvPhaseLabel.setTextColor(android.graphics.Color.parseColor("#333333"))
            btnCancelBreak.visibility = View.VISIBLE
        }
    }

    private fun updateTimerUI(remainingSecs: Int, isWork: Boolean) {
        val m = remainingSecs / 60
        val s = remainingSecs % 60
        tvCountdown.text = "%02d:%02d".format(m, s)
        
        // ensure correct phase UI without waiting for phase change event
        if (PomodoroManager.isWorkPhase != isWork) {
            PomodoroManager.isWorkPhase = isWork
            updatePhaseUI()
        }
    }

    private fun onPhaseChanged(isWork: Boolean, session: Int) {
        PomodoroManager.isWorkPhase = isWork
        PomodoroManager.sessionCount = session
        tvSessionCount.text = "session $session of ∞"
        updatePhaseUI()

        // Vibrate based on new phase
        if (!isWork) {
            // Work just ended -> Break started
            val pattern = longArrayOf(0, 200, 150, 200)
            if (Build.VERSION.SDK_INT >= 26) {
                vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
            } else {
                @Suppress("DEPRECATION") vibrator.vibrate(pattern, -1)
            }
        } else {
            // Break ended -> Work started
            val pattern = longArrayOf(0, 150, 100, 150, 100, 150)
            if (Build.VERSION.SDK_INT >= 26) {
                vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
            } else {
                @Suppress("DEPRECATION") vibrator.vibrate(pattern, -1)
            }
        }
    }

    private fun callEmergencyContact() {
        if (checkSelfPermission(android.Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(android.Manifest.permission.CALL_PHONE), 2004)
            return
        }
        contactNumber?.let {
            val intent = Intent(Intent.ACTION_CALL, Uri.parse("tel:$it"))
            startActivity(intent)
        }
    }

    private fun endPomodoroSession() {
        val totalMins = PomodoroManager.sessionCount * selectedDurationMins
        
        val intent = Intent(this, PomodoroTimerService::class.java)
        intent.action = PomodoroTimerService.ACTION_STOP
        startService(intent)

        PomodoroManager.stop(this)

        // Save stats using SotActivity utility if possible, 
        // passing mode "pomodoro"
        val prefs = getSharedPreferences("sot_prefs", Context.MODE_PRIVATE)
        val today = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date())
        val lastDate = prefs.getString("sot_last_reset_date", "")
        if (lastDate != today) {
            prefs.edit().clear().putString("sot_last_reset_date", today).apply()
        }
        val sKey = "pomodoro_sessions_today"
        val mKey = "pomodoro_minutes_today"
        prefs.edit()
            .putInt(sKey, prefs.getInt(sKey, 0) + PomodoroManager.sessionCount)
            .putInt(mKey, prefs.getInt(mKey, 0) + totalMins)
            .apply()

        showSetupScreen()
    }

    override fun onBackPressed() {
        if (PomodoroManager.isWorkSessionActive()) {
            return // blocked
        }
        super.onBackPressed()
    }
}
