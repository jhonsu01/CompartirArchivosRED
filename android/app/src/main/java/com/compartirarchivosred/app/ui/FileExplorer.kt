package com.compartirarchivosred.app.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.compartirarchivosred.app.net.formatSize
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun FileExplorerScreen(
    peerName: String,
    onPick: (List<File>) -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(hasStorageAccess(context)) }

    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { ok -> granted = ok || hasStorageAccess(context) }

    val manageLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { granted = hasStorageAccess(context) }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
    ) {
        Text(
            "Explorador de archivos",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            "Enviar a: $peerName",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(12.dp))

        if (!granted) {
            Text(
                "Para explorar y enviar archivos, concede acceso al almacenamiento del dispositivo.",
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(Modifier.height(12.dp))
            Row {
                Button(onClick = {
                    try {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            try {
                                manageLauncher.launch(
                                    Intent(
                                        Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                                        Uri.parse("package:${context.packageName}")
                                    )
                                )
                            } catch (e: Exception) {
                                manageLauncher.launch(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
                            }
                        } else {
                            permLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
                        }
                    } catch (e: Exception) {
                        Toast.makeText(
                            context,
                            "Este dispositivo no permite abrir la pantalla de permisos de archivos.",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }) { Text("Conceder acceso") }
                Spacer(Modifier.width(8.dp))
                TextButton(onClick = onClose) { Text("Cancelar") }
            }
            return@Column
        }

        val root = remember { Environment.getExternalStorageDirectory() ?: context.filesDir }
        var currentDir by remember { mutableStateOf(root) }
        val selected = remember { mutableStateListOf<String>() }
        val entries = remember(currentDir) { listEntries(currentDir) }

        Text(
            currentDir.absolutePath,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )
        Spacer(Modifier.height(8.dp))

        LazyColumn(
            Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            if (currentDir.absolutePath != root.absolutePath && currentDir.parentFile != null) {
                item {
                    EntryRow(icon = "⬆", name = "..", subtitle = "Subir un nivel", selected = false) {
                        currentDir = currentDir.parentFile ?: root
                    }
                }
            }
            items(entries, key = { it.absolutePath }) { entry ->
                if (entry.isDirectory) {
                    EntryRow(icon = "📁", name = entry.name, subtitle = "Carpeta · ${fmtDate(entry)}", selected = false) {
                        currentDir = entry
                    }
                } else {
                    val isSel = selected.contains(entry.absolutePath)
                    EntryRow(
                        icon = if (isSel) "☑" else "📄",
                        name = entry.name,
                        subtitle = "${formatSize(entry.length())} · ${fmtDate(entry)}",
                        selected = isSel
                    ) {
                        if (isSel) selected.remove(entry.absolutePath) else selected.add(entry.absolutePath)
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onClose) { Text("Cancelar") }
            Spacer(Modifier.width(8.dp))
            OutlinedButton(
                onClick = { openFile(context, File(selected.first())) },
                enabled = selected.size == 1
            ) { Text("Abrir") }
            Spacer(Modifier.width(8.dp))
            Button(
                onClick = { onPick(selected.map { File(it) }) },
                enabled = selected.isNotEmpty()
            ) { Text("Enviar (${selected.size})") }
        }
    }
}

private fun fmtDate(file: File): String =
    SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(file.lastModified()))

private fun openFile(context: Context, file: File) {
    try {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val ext = file.name.substringAfterLast('.', "").lowercase()
        val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "*/*"
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mime)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Abrir con"))
    } catch (e: Exception) {
        Toast.makeText(context, "No hay una app para abrir este archivo.", Toast.LENGTH_LONG).show()
    }
}

@Composable
private fun EntryRow(icon: String, name: String, subtitle: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(icon, modifier = Modifier.padding(end = 12.dp))
        Column(Modifier.weight(1f)) {
            Text(name, color = MaterialTheme.colorScheme.onBackground, maxLines = 1)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun hasStorageAccess(context: Context): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        Environment.isExternalStorageManager()
    } else {
        ContextCompat.checkSelfPermission(
            context, Manifest.permission.READ_EXTERNAL_STORAGE
        ) == PackageManager.PERMISSION_GRANTED
    }
}

private fun listEntries(dir: File): List<File> {
    val files = dir.listFiles()?.toList() ?: emptyList()
    return files.sortedWith(
        compareByDescending<File> { it.isDirectory }.thenBy { it.name.lowercase() }
    )
}
