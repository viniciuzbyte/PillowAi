package com.faddlan.pillowai

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.faddlan.pillowai.databinding.ActivityProfileBinding
import com.faddlan.pillowai.databinding.ItemDeviceBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class ProfileActivity : AppCompatActivity() {

    private lateinit var b: ActivityProfileBinding
    private lateinit var store: SessionStore

    private val picker = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) applyPhoto(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = SessionStore(this)
        b = ActivityProfileBinding.inflate(layoutInflater)
        setContentView(b.root)
        lightBars()

        b.ivProfile.circle()
        b.btnBack.setOnClickListener { finish() }
        b.btnCamera.setOnClickListener { picker.launch("image/*") }
        b.btnChange.setOnClickListener { picker.launch("image/*") }
        b.btnRemove.setOnClickListener {
            store.setPhoto(null)
            refresh()
        }
        b.btnLogout.setOnClickListener {
            store.email = null
            startActivity(
                Intent(this, LoginActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            )
            finish()
        }

        store.registerDevice()
        refresh()
    }

    private fun applyPhoto(uri: Uri) {
        val dest = File(filesDir, "avatars/${store.accountKey}_${store.deviceId}_${System.currentTimeMillis()}.jpg")
        lifecycleScope.launch {
            val ok = withContext(Dispatchers.IO) { Avatar.saveFromUri(this@ProfileActivity, uri, dest) }
            if (ok) {
                store.setPhoto(dest.absolutePath)
                refresh()
            } else {
                Toast.makeText(this@ProfileActivity, R.string.photo_failed, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun refresh() {
        val email = store.email ?: return
        b.tvEmail.text = email
        b.tvDevice.text = "${store.deviceName} \u00B7 ${store.deviceId}"
        val photo = store.photoPath()
        b.ivProfile.setImageBitmap(Avatar.bitmap(photo, email, dp(320)))
        b.btnRemove.visibility = if (photo != null) View.VISIBLE else View.GONE

        b.deviceList.removeAllViews()
        for (d in store.devices()) {
            val row = ItemDeviceBinding.inflate(layoutInflater, b.deviceList, false)
            row.ivDev.circle()
            val path = d.photoPath?.takeIf { File(it).exists() }
            row.ivDev.setImageBitmap(Avatar.bitmap(path, email, dp(96)))
            row.tvDevName.text = d.name
            row.tvDevId.text = d.id
            row.tvThis.visibility = if (d.id == store.deviceId) View.VISIBLE else View.GONE
            b.deviceList.addView(row.root)
        }
    }
}
