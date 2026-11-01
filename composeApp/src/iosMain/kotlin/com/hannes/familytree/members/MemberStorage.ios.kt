package com.hannes.familytree.members

import androidx.compose.runtime.*
import com.hannes.familytree.gedcom.FilePickerLauncher
import kotlinx.cinterop.*
import platform.Foundation.*
import platform.UIKit.*
import platform.UniformTypeIdentifiers.UTTypeImage
import platform.darwin.NSObject
import platform.posix.memcpy
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

@Composable
actual fun MemberEditorBackHandler(onBack: () -> Unit) = Unit

@Composable
actual fun rememberMemberStorage(): MemberStorage = remember {
    object : MemberStorage {
        private val path = NSSearchPathForDirectoriesInDomains(
            NSDocumentDirectory, NSUserDomainMask, true
        ).first() as String + "/members.json"

        @OptIn(ExperimentalForeignApi::class)
        override fun read(): String? {
            if (!NSFileManager.defaultManager.fileExistsAtPath(path)) return null
            return NSString.stringWithContentsOfFile(path, NSUTF8StringEncoding, null)
                ?: error("Could not read saved members")
        }

        @OptIn(ExperimentalForeignApi::class)
        override fun write(content: String) {
            check((content as NSString).writeToFile(path, true, NSUTF8StringEncoding, null)) {
                "Could not save members"
            }
        }
    }
}

@Composable
actual fun rememberMemberImagePicker(onPicked: (Result<MemberImage>?) -> Unit): FilePickerLauncher {
    val currentOnPicked by rememberUpdatedState(onPicked)
    return remember { MemberImagePicker { currentOnPicked(it) } }
}

@OptIn(ExperimentalForeignApi::class, ExperimentalEncodingApi::class)
private class MemberImagePicker(
    private val onPicked: (Result<MemberImage>?) -> Unit
) : FilePickerLauncher {
    private val delegate = object : NSObject(), UIDocumentPickerDelegateProtocol {
        override fun documentPicker(
            controller: UIDocumentPickerViewController,
            didPickDocumentsAtURLs: List<*>
        ) {
            val result = runCatching {
                val url = didPickDocumentsAtURLs.firstOrNull() as? NSURL
                    ?: error("No image selected")
                val scoped = url.startAccessingSecurityScopedResource()
                try {
                    val data = NSData.dataWithContentsOfURL(url) ?: error("Could not read image")
                    require(data.length > 0u && data.length <= 5u * 1024u * 1024u) {
                        "Select an image smaller than 5 MB"
                    }
                    val bytes = ByteArray(data.length.toInt())
                    bytes.usePinned { memcpy(it.addressOf(0), data.bytes, data.length) }
                    MemberImage(url.lastPathComponent ?: "Image", Base64.encode(bytes))
                } finally {
                    if (scoped) url.stopAccessingSecurityScopedResource()
                }
            }
            onPicked(result)
        }

        override fun documentPickerWasCancelled(controller: UIDocumentPickerViewController) {
            onPicked(null)
        }
    }

    override fun launch() {
        val root = UIApplication.sharedApplication.keyWindow?.rootViewController
        if (root == null) {
            onPicked(Result.failure(IllegalStateException("Could not open image picker")))
            return
        }
        val picker = UIDocumentPickerViewController(
            forOpeningContentTypes = listOf(UTTypeImage), asCopy = true
        )
        picker.delegate = delegate
        root.presentViewController(picker, true, null)
    }
}
