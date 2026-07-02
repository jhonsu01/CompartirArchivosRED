package com.compartirarchivosred.app

import android.Manifest
import android.app.UiModeManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.compartirarchivosred.app.model.IncomingInfo
import com.compartirarchivosred.app.model.Peer
import com.compartirarchivosred.app.net.formatSize
import com.compartirarchivosred.app.ui.FileExplorerScreen
import com.compartirarchivosred.app.ui.theme.AppTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Mantener la pantalla encendida mientras la app está abierta (evita que el
        // dispositivo entre en reposo y se desconecte de la red).
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContent {
            AppTheme {
                AppScreen(viewModel())
            }
        }
    }
}

@Composable
fun AppScreen(vm: MainViewModel) {
    val context = LocalContext.current
    var selectedId by remember { mutableStateOf<String?>(null) }
    val selectedPeer = vm.peers.firstOrNull { it.id == selectedId }
    var explorerMode by remember { mutableStateOf<String?>(null) } // null | "send" | "folder"
    var logExpanded by remember { mutableStateOf(false) }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        val peer = vm.peers.firstOrNull { it.id == selectedId }
        if (peer != null && uris.isNotEmpty()) vm.send(peer, uris)
    }

    val folderPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri -> if (uri != null) vm.setReceiveFolder(uri) }

    // Permiso de notificaciones (Android 13+).
    val notifPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    if (explorerMode != null && (explorerMode == "folder" || selectedPeer != null)) {
        val folderMode = explorerMode == "folder"
        FileExplorerScreen(
            title = if (folderMode) "Elegir carpeta de recepción"
            else "Enviar a ${selectedPeer?.name ?: ""}",
            pickFolder = folderMode,
            onPickFiles = { files ->
                selectedPeer?.let { vm.sendLocal(it, files) }
                explorerMode = null
            },
            onPickFolder = { dir -> vm.setReceiveDir(dir); explorerMode = null },
            onClose = { explorerMode = null }
        )
        return
    }

    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        Column(
            Modifier
                .padding(padding)
                .padding(16.dp)
                .fillMaxSize()
        ) {
            Text(
                "📡 Compartir Archivos RED",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                "Tu dispositivo: ${vm.selfName}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "Carpeta de recepción",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            vm.receiveFolder,
                            maxLines = 1,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    OutlinedButton(onClick = {
                        val tree = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
                        if (!isTv(context) && tree.resolveActivity(context.packageManager) != null)
                            folderPicker.launch(null)
                        else explorerMode = "folder"
                    }) { Text("Cambiar") }
                }
            }

            Spacer(Modifier.height(12.dp))

            vm.incoming?.let { IncomingBanner(it) }

            Text(
                "Dispositivos en tu red",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(vertical = 8.dp)
            )

            Card(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                if (vm.peers.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            "Buscando dispositivos…",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    LazyColumn(Modifier.fillMaxSize().padding(8.dp)) {
                        items(vm.peers, key = { it.id }) { peer ->
                            PeerRow(peer, peer.id == selectedId) { selectedId = peer.id }
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            LinearProgressIndicator(
                progress = { vm.progress },
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )

            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    vm.log.lastOrNull() ?: "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = { logExpanded = !logExpanded }) {
                    Text(if (logExpanded) "Ocultar registro" else "Ver registro")
                }
            }
            if (logExpanded) {
                Card(
                    modifier = Modifier
                        .height(140.dp)
                        .fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().padding(8.dp),
                        reverseLayout = true
                    ) {
                        items(vm.log.asReversed()) { line ->
                            Text(
                                line,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                OutlinedButton(
                    onClick = { explorerMode = "send" },
                    enabled = selectedPeer != null
                ) { Text("📁 Explorador") }
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = {
                        // Si el dispositivo no tiene selector de documentos (Android TV),
                        // se abre el explorador interno.
                        val getContent = Intent(Intent.ACTION_GET_CONTENT).apply {
                            type = "*/*"
                            addCategory(Intent.CATEGORY_OPENABLE)
                        }
                        if (!isTv(context) && getContent.resolveActivity(context.packageManager) != null)
                            picker.launch("*/*")
                        else explorerMode = "send"
                    },
                    enabled = selectedPeer != null
                ) {
                    Text(if (selectedPeer != null) "📤 Enviar a ${selectedPeer.name}" else "📤 Enviar")
                }
            }
        }
    }

    vm.pinRequestPeer?.let { peerName ->
        PinDialog(
            peerName = peerName,
            onSubmit = { vm.submitPin(it) },
            onCancel = { vm.cancelPin() }
        )
    }
}

@Composable
private fun IncomingBanner(info: IncomingInfo) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                "«${info.senderName}» quiere enviarte ${info.fileCount} archivo(s) (${formatSize(info.totalSize)}).",
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                "Comparte este PIN con quien te envía:",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp)
            )
            Text(
                info.pin,
                fontSize = 34.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun PeerRow(peer: Peer, selected: Boolean, onClick: () -> Unit) {
    val icon = when (peer.platform) {
        "windows" -> "🖥️"
        "android" -> "📱"
        else -> "💻"
    }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(
                if (selected) MaterialTheme.colorScheme.surfaceVariant
                else androidx.compose.ui.graphics.Color.Transparent
            )
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(icon, modifier = Modifier.padding(end = 12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                peer.name,
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                peer.display,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (selected) {
            Text("✓", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun PinDialog(peerName: String, onSubmit: (String) -> Unit, onCancel: () -> Unit) {
    var pin by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("PIN de emparejamiento") },
        text = {
            Column {
                Text("Introduce el PIN de 6 dígitos que aparece en «$peerName»:")
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = pin,
                    onValueChange = { if (it.length <= 6 && it.all { c -> c.isDigit() }) pin = it },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
            }
        },
        confirmButton = { TextButton(onClick = { onSubmit(pin) }) { Text("Aceptar") } },
        dismissButton = { TextButton(onClick = onCancel) { Text("Cancelar") } }
    )
}

/** True si el dispositivo es una Android TV (no tiene selector de documentos del sistema). */
private fun isTv(context: Context): Boolean {
    val ui = context.getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager
    return ui?.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION
}
