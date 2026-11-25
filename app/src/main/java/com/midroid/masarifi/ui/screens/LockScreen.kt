package com.midroid.masarifi.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.midroid.masarifi.R
import com.midroid.masarifi.data.AppLock

/** Full-screen cover drawn over the app while it is locked (the app stays composed underneath, so pickers/camera results survive). */
@Composable
fun LockScreen(checkPin: (String) -> Boolean, onBiometric: (() -> Unit)?, onUnlocked: () -> Unit) {
    var pin by remember { mutableStateOf("") }
    var wrong by remember { mutableStateOf(false) }
    val tryPin = { if (checkPin(pin)) onUnlocked() else { wrong = true; pin = "" } }
    androidx.compose.material3.Surface(Modifier.fillMaxSize().testTag("lock_screen").clickable(remember { MutableInteractionSource() }, null) {}) { // swallows touches meant for the app below
    Column(
        Modifier.fillMaxSize().imePadding().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
    ) {
        Icon(Icons.Filled.Lock, null, Modifier.size(56.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(16.dp))
        Text(stringResource(R.string.lock_locked), style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(24.dp))
        OutlinedTextField(
            pin, { v -> pin = v.filter { it.isDigit() }.take(8); wrong = false }, singleLine = true,
            label = { Text(stringResource(R.string.lock_pin)) },
            isError = wrong, supportingText = if (wrong) { { Text(stringResource(R.string.lock_wrong_pin)) } } else null,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { tryPin() }),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))
        Button(onClick = tryPin, enabled = pin.length >= 4, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.lock_unlock)) }
        if (onBiometric != null) TextButton(onClick = onBiometric) { Text(stringResource(R.string.lock_use_biometric)) }
    }
    }
}

/** Asks for a new PIN twice before the lock is turned on. */
@Composable
fun SetPinDialog(onDismiss: () -> Unit, onSet: (String) -> Unit) {
    var pin by remember { mutableStateOf("") }
    var again by remember { mutableStateOf("") }
    val mismatch = again.length >= 4 && again != pin
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.lock_set_pin)) },
        text = {
            Column {
                Text(stringResource(R.string.lock_pin_hint), style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(8.dp))
                listOf(pin to R.string.lock_pin, again to R.string.lock_pin_confirm).forEachIndexed { i, (v, label) ->
                    OutlinedTextField(
                        v, { x -> val d = x.filter { it.isDigit() }.take(8); if (i == 0) pin = d else again = d }, singleLine = true,
                        label = { Text(stringResource(label)) }, visualTransformation = PasswordVisualTransformation(),
                        isError = i == 1 && mismatch,
                        supportingText = if (i == 1 && mismatch) { { Text(stringResource(R.string.lock_pin_mismatch)) } } else null,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSet(pin) }, enabled = AppLock.validPin(pin) && pin == again) { Text(stringResource(R.string.ok)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
