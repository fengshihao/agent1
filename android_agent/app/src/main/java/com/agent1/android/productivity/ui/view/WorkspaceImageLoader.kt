package com.agent1.android.productivity.ui.view

import android.content.Context
import coil.ImageLoader

fun workspaceImageLoader(context: Context): ImageLoader {
    return ImageLoader.Builder(context)
        .components {
            add(WorkspaceBase64ImageDecoder.Factory())
        }
        .build()
}
