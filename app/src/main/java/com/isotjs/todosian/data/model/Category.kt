package com.isotjs.todosian.data.model

import android.net.Uri

data class Category(
    val fileName: String,
    val displayName: String,
    val uri: Uri,
    val todoCount: Int,
    val doneCount: Int,
)
