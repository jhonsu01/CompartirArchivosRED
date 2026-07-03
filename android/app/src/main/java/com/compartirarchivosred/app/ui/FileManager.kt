package com.compartirarchivosred.app.ui

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.compartirarchivosred.app.net.formatSize
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Administrador de archivos interno: navega libremente por el almacenamiento y
 * ofrece acciones Abrir / Mover a / Renombrar / Borrar. Arranca en la carpeta
 * de recepción y puede saltar a la raíz del almacenamiento.
 */
@Composable
fun FileManagerScreen(startDir: File) {
    val context = LocalContext.current
    val root = remember { Environment.getExternalStorageDirectory() ?: startDir }
    val home = remember { if (startDir.isDirectory) startDir else (startDir.parentFile ?: root) }

    var currentDir by remember { mutableStateOf(home) }
    var refreshKey by remember { mutableIntStateOf(0) }

    var actionFile by remember { mutableStateOf<File?>(null) }
    var renameFile by remember { mutableStateOf<File?>(null) }
    var deleteFile by remember { mutableStateOf<File?>(null) }
    var movingFile by remember { mutableStateOf<File?>(null) }

    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { refreshKey++ }
    val manageLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { refreshKey++ }

    val entries = remember(currentDir, refreshKey) { listChildren(currentDir) }
    val canRead = remember(currentDir, refreshKey) { currentDir.listFiles() != null }
    val canGoUp = currentDir.absolutePath != root.absolutePath && currentDir.parentFile != null

    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (movingFile != null) "Mover a…" else "Archivos",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = { currentDir = home }) { Text("Recibidos") }
            TextButton(onClick = { currentDir = root }) { Text("Almacenamiento") }
        }
        Text(
            currentDir.absolutePath,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )

        movingFile?.let { mf ->
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Moviendo «${mf.name}»",
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f),
                    maxLines = 1
                )
                TextButton(onClick = { movingFile = null }) { Text("Cancelar") }
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = {
                        val target = movingFile
                        if (target != null && moveInto(target, currentDir)) {
                            Toast.makeText(context, "Movido a ${currentDir.name}", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "No se pudo mover el archivo.", Toast.LENGTH_LONG).show()
                        }
                        movingFile = null
                        refreshKey++
                    },
                    enabled = canRead
                ) { Text("Mover aquí") }
            }
        }

        Spacer(Modifier.height(8.dp))

        LazyColumn(Modifier.fillMaxSize()) {
            if (canGoUp) {
                item {
                    ItemRow(icon = "⬆", name = "..", subtitle = "Subir un nivel") {
                        currentDir = currentDir.parentFile ?: root
                    }
                }
            }
            if (!canRead) {
                item {
                    Column(Modifier.fillMaxWidth().padding(12.dp)) {
                        Text(
                            "No se pueden leer los archivos de esta carpeta.",
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Spacer(Modifier.height(8.dp))
                        Row {
                            Button(onClick = {
                                requestStorage(context, manageLauncher, permLauncher)
                            }) { Text("Conceder acceso") }
                            Spacer(Modifier.width(8.dp))
                            TextButton(onClick = { currentDir = root }) { Text("Ir al almacenamiento") }
                        }
                    }
                }
            } else {
                items(entries, key = { it.absolutePath }) { entry ->
                    if (entry.isDirectory) {
                        val count = entry.listFiles()?.size
                        ItemRow(
                            icon = "📁",
                            name = entry.name,
                            subtitle = if (count != null) "$count elementos · ${fmtDate(entry)}" else fmtDate(entry)
                        ) { currentDir = entry }
                    } else {
                        ItemRow(
                            icon = "📄",
                            name = entry.name,
                            subtitle = "${formatSize(entry.length())} · ${fmtDate(entry)}"
                        ) { if (movingFile == null) actionFile = entry }
                    }
                }
            }
        }
    }

    // Diálogo de acciones
    actionFile?.let { f ->
        AlertDialog(
            onDismissRequest = { actionFile = null },
            title = { Text(f.name, maxLines = 1) },
            text = {
                Column {
                    ActionItem("Abrir") { openFile(context, f); actionFile = null }
                    ActionItem("Mover a") { movingFile = f; actionFile = null }
                    ActionItem("Renombrar") { renameFile = f; actionFile = null }
                    ActionItem("Borrar") { deleteFile = f; actionFile = null }
                }
            },
            confirmButton = { TextButton(onClick = { actionFile = null }) { Text("Cerrar") } }
        )
    }

    // Renombrar
    renameFile?.let { f ->
        var name by remember(f) { mutableStateOf(f.name) }
        AlertDialog(
            onDismissRequest = { renameFile = null },
            title = { Text("Renombrar") },
            text = { OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true) },
            confirmButton = {
                TextButton(onClick = {
                    val target = File(f.parentFile, name.trim())
                    if (name.isNotBlank() && f.renameTo(target)) {
                        Toast.makeText(context, "Renombrado", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "No se pudo renombrar.", Toast.LENGTH_LONG).show()
                    }
                    renameFile = null
                    refreshKey++
                }) { Text("Aceptar") }
            },
            dismissButton = { TextButton(onClick = { renameFile = null }) { Text("Cancelar") } }
        )
    }

    // Borrar
    deleteFile?.let { f ->
        AlertDialog(
            onDismissRequest = { deleteFile = null },
            title = { Text("Borrar") },
            text = { Text("¿Borrar «${f.name}»? Esta acción no se puede deshacer.") },
            confirmButton = {
                TextButton(onClick = {
                    if (f.deleteRecursively()) {
                        Toast.makeText(context, "Borrado", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "No se pudo borrar.", Toast.LENGTH_LONG).show()
                    }
                    deleteFile = null
                    refreshKey++
                }) { Text("Borrar") }
            },
            dismissButton = { TextButton(onClick = { deleteFile = null }) { Text("Cancelar") } }
        )
    }
}

@Composable
private fun ActionItem(label: String, onClick: () -> Unit) {
    Text(
        label,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp)
    )
}

@Composable
private fun ItemRow(icon: String, name: String, subtitle: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .clip(RoundedCornerShape(8.dp))
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

private fun listChildren(dir: File): List<File> {
    val files = dir.listFiles()?.toList() ?: emptyList()
    return files.sortedWith(
        compareByDescending<File> { it.isDirectory }.thenBy { it.name.lowercase() }
    )
}

private fun moveInto(src: File, destDir: File): Boolean {
    val dest = File(destDir, src.name)
    if (dest.absolutePath == src.absolutePath) return true
    return try {
        if (src.renameTo(dest)) return true
        if (src.isDirectory) {
            src.copyRecursively(dest, overwrite = false)
            src.deleteRecursively()
        } else {
            src.copyTo(dest, overwrite = false)
            src.delete()
        }
        true
    } catch (e: Exception) {
        false
    }
}

private fun fmtDate(file: File): String =
    SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(file.lastModified()))

/** Abre un archivo con la app adecuada según su tipo (APK -> instalador de paquetes). */
private fun openFile(context: Context, file: File) {
    try {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val ext = file.name.substringAfterLast('.', "").lowercase()
        val mime = when (ext) {
            "apk" -> "application/vnd.android.package-archive"
            else -> MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "*/*"
        }
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mime)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(
            context,
            "No hay una app instalada para abrir este tipo de archivo.",
            Toast.LENGTH_LONG
        ).show()
    } catch (e: Exception) {
        Toast.makeText(context, "No se pudo abrir el archivo.", Toast.LENGTH_LONG).show()
    }
}

private fun requestStorage(
    context: Context,
    manageLauncher: androidx.activity.result.ActivityResultLauncher<Intent>,
    permLauncher: androidx.activity.result.ActivityResultLauncher<String>
) {
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
        Toast.makeText(context, "No se pudo abrir la pantalla de permisos.", Toast.LENGTH_LONG).show()
    }
}
