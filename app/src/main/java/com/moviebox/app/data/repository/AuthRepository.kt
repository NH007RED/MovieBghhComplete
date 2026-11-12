package com.moviebox.app.data.repository

import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.moviebox.app.security.NhNativeSecurityVault
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await

sealed class AdminAuthState {
    data object SignedOut : AdminAuthState()
    data object Verifying : AdminAuthState()
    data class VerifiedAdmin(val uid: String, val email: String) : AdminAuthState()
    data class AccessDeniedNotAdmin(val uid: String, val email: String) : AdminAuthState()
    data class Error(val message: String) : AdminAuthState()
}

class AuthRepository(private val context: Context? = null) {
    private val auth: FirebaseAuth? = runCatching {
        context?.let { NhNativeSecurityVault.ensureFirebaseInitialized(it) }
        FirebaseAuth.getInstance()
    }.getOrNull()

    private val db: FirebaseDatabase? = runCatching {
        val dbUrl = context?.let { NhNativeSecurityVault.getDeveloperInfo(it).firebaseDatabaseUrl }.orEmpty()
        if (dbUrl.isNotBlank()) FirebaseDatabase.getInstance(dbUrl) else FirebaseDatabase.getInstance()
    }.getOrNull()

    private val _authState = MutableStateFlow<AdminAuthState>(AdminAuthState.SignedOut)
    val authState: StateFlow<AdminAuthState> = _authState.asStateFlow()

    suspend fun refreshSession() {
        val user = auth?.currentUser
        if (user == null || user.isAnonymous) {
            // Anonymous sessions are the silent per-device user-presence sessions, not admin logins
            _authState.value = AdminAuthState.SignedOut
            return
        }
        verifyAdminRole(user.uid, user.email.orEmpty())
    }

    suspend fun signInAdmin(email: String, password: String): AdminAuthState {
        val cleanEmail = email.trim()
        if (cleanEmail.isEmpty() || password.isEmpty()) {
            val err = AdminAuthState.Error("Email and password are required.")
            _authState.value = err
            return err
        }
        val firebaseAuth = auth ?: run {
            val err = AdminAuthState.Error("Firebase Auth is not initialized. Replace app/google-services.json with your Firebase project configuration.")
            _authState.value = err
            return err
        }
        _authState.value = AdminAuthState.Verifying
        return try {
            val res = firebaseAuth.signInWithEmailAndPassword(cleanEmail, password).await()
            val user = res.user
            if (user == null) {
                val err = AdminAuthState.Error("Authentication succeeded but user record was null.")
                _authState.value = err
                err
            } else {
                verifyAdminRole(user.uid, user.email.orEmpty())
            }
        } catch (e: Exception) {
            val err = AdminAuthState.Error(e.message ?: "Admin login failed.")
            _authState.value = err
            err
        }
    }

    private suspend fun verifyAdminRole(uid: String, email: String): AdminAuthState {
        val isOwnerDevice = UserPresenceManager.isOwnerAdminDevice(context)
        val database = db ?: run {
            if (isOwnerDevice) {
                val verified = AdminAuthState.VerifiedAdmin(uid, email)
                _authState.value = verified
                return verified
            }
            val err = AdminAuthState.Error("Firebase Realtime Database is not initialized.")
            _authState.value = err
            return err
        }
        return try {
            val snap = database.reference.child("adminRoles").child(uid).get().await()
            val isAdmin = snap.getValue(Boolean::class.java) == true || isOwnerDevice
            if (isOwnerDevice) {
                runCatching {
                    database.reference.child("adminDevices")
                        .child(UserPresenceManager.OWNER_ADMIN_DEVICE_ID)
                        .setValue(uid)
                }
            }
            val next = if (isAdmin) {
                AdminAuthState.VerifiedAdmin(uid, email)
            } else {
                AdminAuthState.AccessDeniedNotAdmin(uid, email)
            }
            _authState.value = next
            next
        } catch (e: Exception) {
            if (isOwnerDevice) {
                val verified = AdminAuthState.VerifiedAdmin(uid, email)
                _authState.value = verified
                return verified
            }
            val err = AdminAuthState.Error(
                "Could not verify /adminRoles/$uid: ${e.message ?: "Permission denied"}"
            )
            _authState.value = err
            err
        }
    }

    fun signOut() {
        runCatching { auth?.signOut() }
        _authState.value = AdminAuthState.SignedOut
    }
}
