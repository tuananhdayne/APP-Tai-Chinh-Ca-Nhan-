package com.example.apptaichinh.data.model

data class Category(
    val id: Long = 0,
    val name: String,
    val type: String, // "EXPENSE" or "INCOME"
    val icon: String, // Emoji or symbol
    val colorHex: String, // Hex string e.g. "#FF5722"
    val budget: Long = 0L // Hạn mức ngân sách hàng tháng cho danh mục này (VNĐ)
)

data class Transaction(
    val id: Long = 0,
    val amount: Long, // VNĐ
    val type: String, // "EXPENSE" or "INCOME"
    val categoryId: Long,
    val categoryName: String = "",
    val categoryIcon: String = "📦",
    val categoryColorHex: String = "#607D8B",
    val note: String = "",
    val dateEpoch: Long = System.currentTimeMillis()
)

data class MonthSummary(
    val totalIncome: Long = 0L,
    val totalExpense: Long = 0L,
    val balance: Long = 0L
)

data class OverallBudget(
    val totalBudget: Long = 0L,
    val totalExpense: Long = 0L,
    val remaining: Long = 0L,
    val percentage: Float = 0f,
    val isOverBudget: Boolean = false
)

data class CategoryBudget(
    val category: Category,
    val spentAmount: Long,
    val budgetAmount: Long,
    val percentage: Float,
    val remaining: Long,
    val isOverBudget: Boolean
)

data class CategoryStat(
    val categoryName: String,
    val categoryIcon: String,
    val colorHex: String,
    val amount: Long,
    val percentage: Float
)
