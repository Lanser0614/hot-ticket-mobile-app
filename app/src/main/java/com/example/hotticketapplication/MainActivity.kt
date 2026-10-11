package com.example.hotticketapplication

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.hotticketapplication.data.HotTicketRepository
import com.example.hotticketapplication.data.SessionStore
import com.example.hotticketapplication.ui.HotTicketApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val repository = HotTicketRepository(SessionStore(applicationContext))
        setContent { HotTicketApp(repository) }
    }
}
