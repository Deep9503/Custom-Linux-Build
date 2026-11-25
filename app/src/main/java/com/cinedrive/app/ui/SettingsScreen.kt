package com.cinedrive.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.cinedrive.app.BuildConfigInfo
import com.cinedrive.app.CineDriveApp
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen() {
    val app = CineDriveApp.instance
    val scope = rememberCoroutineScope()
    var account by remember { mutableStateOf(app.settings.jellyfin) }
    var server by remember { mutableStateOf("") }
    var user by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    Column(Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Text("Settings", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(20.dp))

        Section("Your library (Jellyfin)") {
            val a = account
            if (a != null) {
                Text("Signed in as ${a.userName}", style = MaterialTheme.typography.bodyMedium)
                Text(a.server, style = MaterialTheme.typography.labelMedium, color = Palette.TextSecondary)
                Spacer(Modifier.height(10.dp))
                OutlinedButton(onClick = {
                    app.settings.jellyfin = null
                    app.catalog.invalidate()
                    account = null
                }) { Text("Sign out") }
            } else {
                Text(
                    "Stream your own movies and shows from a Jellyfin server at home. " +
                            "Your password is only used to sign in; it isn't saved.",
                    style = MaterialTheme.typography.labelMedium, color = Palette.TextSecondary,
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(server, { server = it }, Modifier.fillMaxWidth(), singleLine = true,
                    label = { Text("Server address") }, placeholder = { Text("http://192.168.1.20:8096") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri))
                OutlinedTextField(user, { user = it }, Modifier.fillMaxWidth(), singleLine = true,
                    label = { Text("Username") })
                OutlinedTextField(pass, { pass = it }, Modifier.fillMaxWidth(), singleLine = true,
                    label = { Text("Password") }, visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
                Spacer(Modifier.height(10.dp))
                Button(
                    enabled = !busy && server.isNotBlank() && user.isNotBlank(),
                    onClick = {
                        busy = true; message = null
                        scope.launch {
                            try {
                                account = app.jellyfin.signIn(server, user, pass)
                                app.catalog.invalidate()
                                pass = ""
                                message = "Signed in. Your library now appears on Home."
                            } catch (e: Exception) {
                                message = "Couldn't sign in: ${e.message ?: "check the address"}"
                            } finally { busy = false }
                        }
                    },
                ) {
                    if (busy) CircularProgressIndicator(Modifier.height(18.dp), strokeWidth = 2.dp)
                    else Text("Sign in")
                }
            }
            message?.let { Text(it, Modifier.padding(top = 8.dp), style = MaterialTheme.typography.labelMedium) }
        }

        Section("Watch history") {
            OutlinedButton(onClick = {
                scope.launch { app.db.dao().clearHistory(); message = "Watch history cleared." }
            }) { Text("Clear Continue Watching") }
        }

        Section("In the car") {
            Text(
                "On Android Auto, CineDrive is an audio app: continue what you were watching as audio, " +
                        "old-time radio, audiobooks and your Jellyfin music. Video is never shown on the car screen.",
                style = MaterialTheme.typography.labelMedium, color = Palette.TextSecondary,
            )
        }

        Section("About") {
            Text("CineDrive ${BuildConfigInfo.VERSION}", style = MaterialTheme.typography.bodyMedium)
            Text(
                "Free Classics come from the Internet Archive's public-domain collections. " +
                        "Only add sources you have the right to watch.",
                style = MaterialTheme.typography.labelMedium, color = Palette.TextSecondary,
            )
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(bottom = 16.dp)
            .background(Palette.Surface, RoundedCornerShape(12.dp)).padding(16.dp)
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        content()
    }
}
