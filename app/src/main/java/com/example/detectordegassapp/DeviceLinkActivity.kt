package com.example.detectordegassapp

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase

class DeviceLinkActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_device_link)

        auth = FirebaseAuth.getInstance()
        val user = auth.currentUser
        if (user == null) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        val etDeviceId = findViewById<EditText>(R.id.etDeviceId)
        val btnLinkDevice = findViewById<Button>(R.id.btnLinkDevice)

        btnLinkDevice.setOnClickListener {
            val deviceId = etDeviceId.text.toString().trim()
            if (deviceId.isEmpty()) {
                Toast.makeText(this, "Ingresa el ID de tu placa", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val ref = FirebaseDatabase.getInstance()
                .getReference("usuarios")
                .child(user.uid)
                .child("dispositivoId")
            ref.setValue(deviceId).addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    getSharedPreferences("app_prefs", MODE_PRIVATE)
                        .edit()
                        .putString("dispositivoId", deviceId)
                        .apply()
                    startActivity(Intent(this, MainActivity::class.java))
                    finish()
                } else {
                    Toast.makeText(this, "Error al vincular: ${task.exception?.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
}
