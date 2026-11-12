package com.moviebox.app.ui.admin

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.moviebox.app.data.repository.AdminAuthState
import com.moviebox.app.ui.components.MovieBoxLogo
import com.moviebox.app.ui.theme.CrimsonRed
import com.moviebox.app.ui.theme.StatusAmber
import com.moviebox.app.ui.theme.StatusRed
import com.moviebox.app.ui.theme.TextSecondary
import com.moviebox.app.ui.theme.TextWhite

@Composable
fun AdminLoginScreen(
    authState: AdminAuthState,
    onSignIn: (email: String, password: String) -> Unit,
    onSignOut: () -> Unit,
    onBackToUserApp: () -> Unit
) {
    BackHandler { onBackToUserApp() }
    val context = LocalContext.current
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 460.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.surface)
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(20.dp))
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                MovieBoxLogo(subtitle = "Admin Panel Security")
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Filled.Security, contentDescription = null, tint = CrimsonRed, modifier = Modifier.size(20.dp))
                Text(
                    text = "Firebase Admin Authentication",
                    style = MaterialTheme.typography.titleMedium,
                    color = TextWhite,
                    fontWeight = FontWeight.Bold
                )
            }

            Text(
                text = "Sign in with your Firebase Authentication admin email and password. Write access is enforced on the server by Firebase Realtime Database rules checking /adminRoles/{uid} == true.",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )

            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("Admin Email") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Password") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth()
            )

            val busy = authState is AdminAuthState.Verifying
            Button(
                onClick = { onSignIn(email, password) },
                enabled = !busy && email.isNotBlank() && password.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = CrimsonRed),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                if (busy) {
                    CircularProgressIndicator(
                        color = Color.White,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(20.dp)
                    )
                } else {
                    Icon(Icons.Filled.Lock, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Sign In & Verify Admin Role", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }

            when (authState) {
                is AdminAuthState.Error -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(StatusRed.copy(alpha = 0.14f))
                            .border(1.dp, StatusRed.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                            .padding(12.dp)
                    ) {
                        Text(
                            text = authState.message,
                            style = MaterialTheme.typography.bodySmall,
                            color = StatusRed
                        )
                    }
                }

                is AdminAuthState.AccessDeniedNotAdmin -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(StatusAmber.copy(alpha = 0.14f))
                            .border(1.dp, StatusAmber.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Authenticated, but Admin Role Not Granted",
                            style = MaterialTheme.typography.titleSmall,
                            color = StatusAmber,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Account ${authState.email} is signed in, but /adminRoles/${authState.uid} is not set to true in Firebase Realtime Database. Non-admin users cannot modify catalog data.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextWhite
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "UID: ${authState.uid}",
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = FontFamily.Monospace,
                                color = TextSecondary,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(
                                onClick = {
                                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    cm.setPrimaryClip(ClipData.newPlainText("Admin UID", authState.uid))
                                }
                            ) {
                                Icon(Icons.Filled.ContentCopy, contentDescription = "Copy UID", tint = TextWhite, modifier = Modifier.size(18.dp))
                            }
                        }
                        OutlinedButton(
                            onClick = onSignOut,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Sign Out")
                        }
                    }
                }

                else -> {}
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "How to Configure Your Admin Account:",
                    style = MaterialTheme.typography.labelMedium,
                    color = TextWhite,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "1. Replace app/google-services.json with your Firebase project config.\n" +
                        "2. Enable Email/Password in Firebase Console > Authentication.\n" +
                        "3. Create your admin user in Firebase Authentication and copy its UID.\n" +
                        "4. In Realtime Database > Data, add /adminRoles/<YOUR_UID> = true.\n" +
                        "5. Deploy database.rules.json to Realtime Database > Rules.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }
        }
    }
}
