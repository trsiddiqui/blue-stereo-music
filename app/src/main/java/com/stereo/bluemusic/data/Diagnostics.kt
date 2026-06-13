package com.stereo.bluemusic.data

data class HarnessDiagnosticUiState(
    val liveValues: Map<String, String> = emptyMap(),
    val resultTitle: String = "",
    val resultBody: String = "",
    val resultOpen: Boolean = false
)
