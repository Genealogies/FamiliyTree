package com.hannes.familytree.members

import android.util.AtomicFile
import android.provider.OpenableColumns
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.hannes.familytree.gedcom.FilePickerLauncher
import java.io.File
import java.io.IOException
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

@Composable
actual fun rememberMemberStorage(): MemberStorage {
    val context = LocalContext.current
    return remember(context) {
        object : MemberStorage {
            private val file = AtomicFile(File(context.filesDir, "members.json"))

            override fun read(): String? =
                if (file.baseFile.exists() || File("${file.baseFile}.bak").exists()) {
                    file.openRead().bufferedReader().use { it.readText() }
                }
                else null

            override fun write(content: String) {
                val output = file.startWrite()
                try {
                    output.write(content.toByteArray(Charsets.UTF_8))
                    file.finishWrite(output)
                } catch (e: Exception) {
                    file.failWrite(output)
                    throw e
                }
            }
        }
    }
}

@OptIn(ExperimentalEncodingApi::class)
@Composable
actual fun rememberMemberImagePicker(onPicked: (Result<MemberImage>?) -> Unit): FilePickerLauncher {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) {
            onPicked(null)
        } else {
            val result = runCatching {
                val bytes = context.contentResolver.openInputStream(uri)?.use {
                    it.readBytesLimited()
                } ?: throw IOException("Could not read the selected image")
                val name = context.contentResolver.query(
                    uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null
                )?.use { cursor ->
                    if (cursor.moveToFirst()) cursor.getString(0) else null
                } ?: "Image"
                MemberImage(name, Base64.encode(bytes))
            }
            onPicked(result)
        }
    }
    return remember(launcher) {
        object : FilePickerLauncher {
            override fun launch() = launcher.launch(arrayOf("image/*"))
        }
    }
}

@Composable
actual fun MemberEditorBackHandler(onBack: () -> Unit) {
    BackHandler(onBack = onBack)
}

private fun java.io.InputStream.readBytesLimited(): ByteArray {
    val output = java.io.ByteArrayOutputStream()
    val buffer = ByteArray(8192)
    while (true) {
        val count = read(buffer)
        if (count < 0) break
        require(output.size() + count <= 5 * 1024 * 1024) { "Select an image smaller than 5 MB" }
        output.write(buffer, 0, count)
    }
    return output.toByteArray()
}
