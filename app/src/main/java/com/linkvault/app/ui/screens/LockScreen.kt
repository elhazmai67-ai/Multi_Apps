package com.linkvault.app.ui.screens

import com.linkvault.app.ui.components.NeuButton as Button
import com.linkvault.app.ui.components.NeuTonalButton as FilledTonalButton
import com.linkvault.app.ui.components.NeuOutlinedButton as OutlinedButton
import com.linkvault.app.ui.components.NeuIconButton as IconButton
import com.linkvault.app.ui.components.NeuSwitch as Switch
import com.linkvault.app.ui.components.NeuCheckbox as Checkbox
import com.linkvault.app.ui.components.neuRaised
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import com.linkvault.app.ui.components.AppMark
import com.linkvault.app.ui.theme.Neon
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.automirrored.outlined.Backspace
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.linkvault.app.MainViewModel
import com.linkvault.app.ui.theme.Radius
import com.linkvault.app.util.Biometrics
import com.linkvault.app.util.findFragmentActivity
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun LockScreen(vm: MainViewModel) {
    val context = LocalContext.current
    val settings by vm.settings.collectAsState()
    val scope = rememberCoroutineScope()
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var failures by remember { mutableIntStateOf(0) }
    var lockedSeconds by remember { mutableIntStateOf(0) }
    val useBio = settings?.biometricEnabled == true && Biometrics.isAvailable(context)

    fun promptBio() {
        context.findFragmentActivity()?.let { act ->
            Biometrics.prompt(act, "Unlock LinkVault", onSuccess = { vm.unlocked = true })
        }
    }

    LaunchedEffect(useBio) { if (useBio) promptBio() }
    LaunchedEffect(lockedSeconds) {
        if (lockedSeconds > 0) { delay(1000); lockedSeconds -= 1 }
    }

    fun submit() {
        if (lockedSeconds > 0 || pin.length < 4) return
        scope.launch {
            if (vm.verifyPin(pin)) { vm.unlocked = true }
            else {
                failures++
                pin = ""
                if (failures % 5 == 0) { lockedSeconds = 30; error = null } else error = "Incorrect PIN. Try again."
            }
        }
    }

    Column(
        Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 32.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center
    ) {
        AppMark(size = 64.dp)
        Spacer(Modifier.height(16.dp))
        Text("LinkVault is locked", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(4.dp))
        Text(
            when {
                lockedSeconds > 0 -> "Too many attempts. Try again in ${lockedSeconds}s."
                error != null -> error!!
                else -> "Enter your PIN to continue"
            },
            style = MaterialTheme.typography.bodyMedium,
            color = if (error != null || lockedSeconds > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(24.dp))
        // PIN dots: four placeholders, growing as more digits are typed.
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.height(16.dp)) {
            repeat(maxOf(4, pin.length)) { i ->
                Box(
                    Modifier.size(14.dp).clip(CircleShape)
                        .background(if (i < pin.length) Neon.lime else MaterialTheme.colorScheme.outline.copy(alpha = 0.45f))
                )
            }
        }
        Spacer(Modifier.height(28.dp))
        val enabled = lockedSeconds == 0
        fun press(d: String) { if (enabled && pin.length < 8) { pin += d; error = null } }
        listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9")).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp), modifier = Modifier.padding(vertical = 8.dp)) {
                row.forEach { d -> KeyButton(onClick = { press(d) }, enabled = enabled) { Text(d, style = MaterialTheme.typography.titleLarge) } }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(20.dp), modifier = Modifier.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (useBio) {
                // Biometric key: outlined in the accent colour so it reads as "different from the digits".
                Box(
                    Modifier.size(72.dp).clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f))
                        .border(1.5.dp, MaterialTheme.colorScheme.primary, CircleShape)
                        .clickable(onClick = { promptBio() }),
                    contentAlignment = Alignment.Center
                ) { Icon(Icons.Outlined.Fingerprint, "Use biometrics", Modifier.size(32.dp), tint = MaterialTheme.colorScheme.primary) }
            }
            else Spacer(Modifier.size(72.dp))
            KeyButton(onClick = { press("0") }, enabled = enabled) { Text("0", style = MaterialTheme.typography.titleLarge) }
            KeyButton(onClick = { if (pin.isNotEmpty()) pin = pin.dropLast(1) }, plain = true, enabled = enabled) {
                Icon(Icons.AutoMirrored.Outlined.Backspace, "Delete digit", Modifier.size(26.dp))
            }
        }
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = { submit() }, enabled = pin.length >= 4 && enabled,
            modifier = Modifier.widthIn(max = 300.dp).fillMaxWidth().height(52.dp), shape = Radius.pill
        ) { Text("Unlock", style = MaterialTheme.typography.titleSmall) }
    }
}

@Composable
private fun KeyButton(onClick: () -> Unit, modifier: Modifier = Modifier, plain: Boolean = false, enabled: Boolean = true, content: @Composable () -> Unit) {
    Box(
        modifier.size(72.dp).clip(CircleShape)
            .background(if (plain) Color.Transparent else MaterialTheme.colorScheme.surfaceContainerHighest)
            .then(if (plain) Modifier else Modifier.border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f), CircleShape))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) { content() }
    }
}
