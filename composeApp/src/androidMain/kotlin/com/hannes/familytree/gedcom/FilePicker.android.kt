package com.hannes.familytree.gedcom

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import java.io.IOException

@Composable
actual fun rememberFilePicker(onFilePicked: (Result<String>?) -> Unit): FilePickerLauncher {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri ->
            if (uri == null) {
                onFilePicked(null)
                return@rememberLauncherForActivityResult
            }

            val result = try {
                val content = context.contentResolver.openInputStream(uri)
                    ?.bufferedReader()?.use { it.readText() }
                    ?: throw IOException("Could not open the selected file")
                Result.success(content)
            } catch (e: Exception) {
                Result.failure(e)
            }
            onFilePicked(result)
        }
    )

    return remember {
        object : FilePickerLauncher {
            override fun launch() {
                launcher.launch(arrayOf("*/*")) // Allow all files, GEDCOMs usually have .ged but MIME type varies
            }
        }
    }
}
