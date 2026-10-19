package com.example.detectordegassapp

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

class MainActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        auth = FirebaseAuth.getInstance()

        val tvNivelGas = findViewById<TextView>(R.id.tvNivelGas)
        val tvEstadoAlerta = findViewById<TextView>(R.id.tvEstadoAlerta)
        val btnLogout = findViewById<Button>(R.id.btnLogout)

        // Referencia a la base de datos de Firebase
        val database = FirebaseDatabase.getInstance().getReference("deteccion_gas")

        // Escuchar cambios en tiempo real
        database.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.exists()) {
                    val nivel = snapshot.child("nivel").getValue(Int::class.java) ?: 0
                    val fuga = snapshot.child("fuga").getValue(Boolean::class.java) ?: false

                    tvNivelGas.text = "Nivel de Gas: $nivel PPM"

                    if (fuga) {
                        tvEstadoAlerta.text = "¡ALERTA: FUGA DE GAS DETECTADA!"
                        tvEstadoAlerta.setTextColor(Color.RED)
                    } else {
                        tvEstadoAlerta.text = "Estado: Normal (Sin fuga)"
                        tvEstadoAlerta.setTextColor(Color.GREEN)
                    }
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(applicationContext, "Error al leer Firebase: ${error.message}", Toast.LENGTH_SHORT).show()
            }
        })

        // Cerrar sesión
        btnLogout.setOnClickListener {
            auth.signOut()
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }
    }
}