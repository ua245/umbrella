package com.umbrella.parent

import android.app.Application
import com.google.firebase.FirebaseApp

class UmbrellaParentApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Initialize Firebase
        FirebaseApp.initializeApp(this)
    }
}

