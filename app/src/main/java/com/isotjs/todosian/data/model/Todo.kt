package com.isotjs.todosian.data.model

enum class TasksPriority {
    LOWEST,
    LOW,
    NONE,
    MEDIUM,
    HIGH,
    HIGHEST,
}

data class Todo(
    val id: String,
    val text: String,
    val isDone: Boolean,
    val lineIndex: Int,
    val indentPrefix: String = "",
    val indentLevel: Int = 0,

    // Obsidian Tasks plugin metadata (Tasks Emoji Format)
    val dueDate: String? = null, // 📅 YYYY-MM-DD
    val startDate: String? = null, // 🛫 YYYY-MM-DD
    val scheduledDate: String? = null, // ⏳ YYYY-MM-DD
    val completionDate: String? = null, // ✅ YYYY-MM-DD
    val createdDate: String? = null, // ➕ YYYY-MM-DD
    val priority: TasksPriority? = null,
    val recurrence: String? = null, // 🔁 <text>
)
