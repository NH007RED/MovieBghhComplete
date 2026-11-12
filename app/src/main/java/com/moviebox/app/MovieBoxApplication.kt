package com.moviebox.app

import android.app.Application
import com.google.firebase.database.FirebaseDatabase
import com.moviebox.app.data.repository.UserPresenceManager
import com.moviebox.app.security.NhNativeSecurityVault

class MovieBoxApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        runCatching {
            NhNativeSecurityVault.ensureFirebaseInitialized(this)
            val dbUrl = NhNativeSecurityVault.getDeveloperInfo(this).firebaseDatabaseUrl
            val db = if (dbUrl.isNotBlank()) FirebaseDatabase.getInstance(dbUrl) else FirebaseDatabase.getInstance()
            db.setPersistenceEnabled(true)
        }
        // Registers this device under /users so Admin -> User Management can list it
        runCatching { UserPresenceManager.start(this) }
    }
}
