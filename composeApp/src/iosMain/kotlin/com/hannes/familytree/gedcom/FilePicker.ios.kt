package com.hannes.familytree.gedcom

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.cinterop.ExperimentalForeignApi
import platform.UIKit.*
import platform.Foundation.*
import platform.UniformTypeIdentifiers.*
import platform.darwin.NSObject

@Composable
actual fun rememberFilePicker(onFilePicked: (Result<String>?) -> Unit): FilePickerLauncher {
    val currentOnFilePicked by rememberUpdatedState(onFilePicked)
    val launcher = remember {
        FilePickerLauncherIOS { currentOnFilePicked(it) }
    }
    return launcher
}

@OptIn(ExperimentalForeignApi::class)
class FilePickerLauncherIOS(
    private val onFilePicked: (Result<String>?) -> Unit
) : FilePickerLauncher {

    private val delegate = object : NSObject(), UIDocumentPickerDelegateProtocol {
        override fun documentPicker(controller: UIDocumentPickerViewController, didPickDocumentsAtURLs: List<*>) {
            val url = didPickDocumentsAtURLs.firstOrNull() as? NSURL
            if (url != null) {
                // Security scoped resource access might be needed depending on where the file is
                val requestAccess = url.startAccessingSecurityScopedResource()
                val result = try {
                    val content = NSString.stringWithContentsOfURL(url, encoding = NSUTF8StringEncoding, error = null)
                        ?: throw IllegalStateException("Could not read the selected file as UTF-8")
                    Result.success(content)
                } catch (e: Exception) {
                    Result.failure(e)
                } finally {
                    if (requestAccess) {
                        url.stopAccessingSecurityScopedResource()
                    }
                }
                onFilePicked(result)
            } else {
                onFilePicked(Result.failure(IllegalStateException("No file URL received")))
            }
        }

        override fun documentPickerWasCancelled(controller: UIDocumentPickerViewController) {
            onFilePicked(null)
        }
    }

    override fun launch() {
        val picker = UIDocumentPickerViewController(
            forOpeningContentTypes = listOf(UTTypeContent), // Generic content type
            asCopy = true
        )
        picker.delegate = delegate

        val window = UIApplication.sharedApplication.keyWindow
        val rootViewController = window?.rootViewController

        if (rootViewController == null) {
            onFilePicked(Result.failure(IllegalStateException("Could not open the file picker")))
        } else {
            rootViewController.presentViewController(picker, animated = true, completion = null)
        }
    }
}
