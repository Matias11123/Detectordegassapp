package com.example.detectordegassapp

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.widget.Button
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import com.google.firebase.database.ValueEventListener

class MainActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private var deviceRef: DatabaseReference? = null
    private var actuadorListener: ValueEventListener? = null
    private var lecturaListener: ValueEventListener? = null
    private var connectedListener: ValueEventListener? = null
    private var updatingSwitch = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        auth = FirebaseAuth.getInstance()
        val user = auth.currentUser
        if (user == null) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        val tvNivelGas = findViewById<TextView>(R.id.tvNivelGas)
        val tvEstadoAlerta = findViewById<TextView>(R.id.tvEstadoAlerta)
        val tvConnectionStatus = findViewById<TextView>(R.id.tvConnectionStatus)
        val switchActuador = findViewById<Switch>(R.id.switchActuador)
        val btnLogout = findViewById<Button>(R.id.btnLogout)

        val prefs = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        val dispositivoId = prefs.getString("dispositivoId", null)

        if (dispositivoId.isNullOrBlank()) {
            FirebaseDatabase.getInstance().getReference("usuarios").child(user.uid).child("dispositivoId")
                .get().addOnCompleteListener { task ->
                    val id = if (task.isSuccessful) task.result.getValue(String::class.java) else null
                    if (!id.isNullOrBlank()) {
                        prefs.edit().putString("dispositivoId", id).apply()
                        setupDevice(id, tvNivelGas, tvEstadoAlerta, switchActuador, prefs)
                    } else {
                        startActivity(Intent(this, DeviceLinkActivity::class.java))
                        finish()
                    }
                }
        } else {
            setupDevice(dispositivoId, tvNivelGas, tvEstadoAlerta, switchActuador, prefs)
        }

        val connectedRef = FirebaseDatabase.getInstance().getReference(".info/connected")
        connectedListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val connected = snapshot.getValue(Boolean::class.java) ?: false
                if (connected) {
                    tvConnectionStatus.text = "En línea - Sincronizado"
                    tvConnectionStatus.setTextColor(Color.parseColor("#2E7D32"))
                } else {
                    tvConnectionStatus.text = "Sin red - Almacenamiento temporal activo"
                    tvConnectionStatus.setTextColor(Color.parseColor("#EF6C00"))
                }
            }

            override fun onCancelled(error: DatabaseError) {}
        }
        connectedRef.addValueEventListener(connectedListener as ValueEventListener)

        switchActuador.setOnCheckedChangeListener { _, isChecked ->
            if (updatingSwitch) return@setOnCheckedChangeListener
            val ref = deviceRef ?: return@setOnCheckedChangeListener
            val payload = mapOf(
                "estado" to isChecked,
                "timestamp" to ServerValue.TIMESTAMP,
                "usuarioUid" to user.uid
            )
            ref.child("actuador").setValue(payload).addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    prefs.edit().putBoolean("actuador_estado", isChecked).apply()
                } else {
                    Toast.makeText(this, "No se pudo actualizar actuador: ${task.exception?.message}", Toast.LENGTH_SHORT).show()
                    updatingSwitch = true
                    switchActuador.isChecked = !isChecked
                    updatingSwitch = false
                }
            }
        }

        btnLogout.setOnClickListener {
            auth.signOut()
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }
    }

    private fun setupDevice(
        dispositivoId: String,
        tvNivelGas: TextView,
        tvEstadoAlerta: TextView,
        switchActuador: Switch,
        prefs: android.content.SharedPreferences
    ) {
        deviceRef = FirebaseDatabase.getInstance().getReference("dispositivos").child(dispositivoId)
        deviceRef?.keepSynced(true)

        val lastNivel = prefs.getInt("ultimo_nivel", -1)
        val lastFuga = prefs.getBoolean("ultima_fuga", false)
        val lastActuador = prefs.getBoolean("actuador_estado", false)
        if (lastNivel >= 0) {
            tvNivelGas.text = "Nivel de Gas: $lastNivel PPM"
            if (lastFuga) {
                tvEstadoAlerta.text = "¡ALERTA: FUGA DE GAS DETECTADA!"
                tvEstadoAlerta.setTextColor(Color.RED)
            } else {
                tvEstadoAlerta.text = "Estado: Normal (Sin fuga)"
                tvEstadoAlerta.setTextColor(Color.parseColor("#2E7D32"))
            }
        }
        updatingSwitch = true
        switchActuador.isChecked = lastActuador
        updatingSwitch = false

        lecturaListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (!snapshot.exists()) return
                val nivel = snapshot.child("nivel").getValue(Int::class.java) ?: 0
                val fuga = snapshot.child("fuga").getValue(Boolean::class.java) ?: false
                tvNivelGas.text = "Nivel de Gas: $nivel PPM"
                if (fuga) {
                    tvEstadoAlerta.text = "¡ALERTA: FUGA DE GAS DETECTADA!"
                    tvEstadoAlerta.setTextColor(Color.RED)
                } else {
                    tvEstadoAlerta.text = "Estado: Normal (Sin fuga)"
                    tvEstadoAlerta.setTextColor(Color.parseColor("#2E7D32"))
                }
                prefs.edit()
                    .putInt("ultimo_nivel", nivel)
                    .putBoolean("ultima_fuga", fuga)
                    .apply()
            }

            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(applicationContext, "Error al leer dispositivo: ${error.message}", Toast.LENGTH_SHORT).show()
            }
        }
        deviceRef?.child("lectura")?.addValueEventListener(lecturaListener as ValueEventListener)

        actuadorListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val estado = snapshot.child("estado").getValue(Boolean::class.java) ?: return
                prefs.edit().putBoolean("actuador_estado", estado).apply()
                if (switchActuador.isChecked != estado) {
                    updatingSwitch = true
                    switchActuador.isChecked = estado
                    updatingSwitch = false
                }
            }

            override fun onCancelled(error: DatabaseError) {}
        }
        deviceRef?.child("actuador")?.addValueEventListener(actuadorListener as ValueEventListener)
    }

    override fun onDestroy() {
        super.onDestroy()
        lecturaListener?.let { deviceRef?.child("lectura")?.removeEventListener(it) }
        actuadorListener?.let { deviceRef?.child("actuador")?.removeEventListener(it) }
        connectedListener?.let { FirebaseDatabase.getInstance().getReference(".info/connected").removeEventListener(it) }
    }
}