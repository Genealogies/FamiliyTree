package com.hannes.familytree.gedcom

import androidx.compose.runtime.Composable

interface FilePickerLauncher {
    fun launch()
}

@Composable
expect fun rememberFilePicker(onFilePicked: (String?) -> Unit): FilePickerLauncher
