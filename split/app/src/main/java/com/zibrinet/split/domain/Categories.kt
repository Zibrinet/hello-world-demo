package com.zibrinet.split.domain

/** Small fixed category set; emoji keeps it colorful without icon assets. */
data class Category(val id: String, val emoji: String, val label: String)

val Categories: List<Category> = listOf(
    Category("food", "🍜", "Food"),          // 🍜
    Category("groceries", "🛒", "Groceries"), // 🛒
    Category("transport", "🚕", "Transport"), // 🚕
    Category("home", "🏠", "Home"),           // 🏠
    Category("travel", "✈️", "Travel"),       // ✈️
    Category("fun", "🎬", "Fun"),             // 🎬
    Category("health", "💊", "Health"),       // 💊
    Category("other", "📦", "Other"),         // 📦
)

fun categoryById(id: String?): Category? = Categories.firstOrNull { it.id == id }
