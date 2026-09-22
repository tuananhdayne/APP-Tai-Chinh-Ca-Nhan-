package com.example.apptaichinh.data.ai

import com.example.apptaichinh.data.model.Category
import com.example.apptaichinh.data.model.Transaction
import java.util.UUID

enum class MessageSender {
    USER,
    ASSISTANT,
    SYSTEM
}

enum class ToolActionType {
    CREATE,
    UPDATE,
    DELETE,
    CREATE_CATEGORY
}

enum class CardStatus {
    PENDING,    // Đang chờ người dùng bấm duyệt
    CONFIRMED,  // Đã bấm xác nhận và lưu vào DB thành công
    CANCELLED   // Người dùng đã bấm hủy bỏ
}

/**
 * Đại diện cho hành động trích xuất được từ Tool Call của LLM
 */
data class ToolAction(
    val type: ToolActionType,
    // Thông tin cho CREATE hoặc CREATE_CATEGORY
    val amount: Long = 0L,
    val transactionType: String = "EXPENSE", // EXPENSE hoặc INCOME
    val categoryId: Long = 0L,
    val categoryName: String = "",
    val categoryIcon: String = "📦",
    val categoryColorHex: String = "#607D8B",
    val categoryBudget: Long = 0L,
    val note: String = "",

    // Thông tin cho UPDATE & DELETE
    val targetTransaction: Transaction? = null,
    val newAmount: Long? = null,
    val newCategory: Category? = null,
    val newNote: String? = null,

    // Từ khóa tìm kiếm nếu không tìm thấy bản ghi khớp
    val searchKeyword: String = "",

    // Trạng thái độc lập của từng thẻ trong chuỗi đa hành động (PENDING, CONFIRMED, CANCELLED)
    val status: CardStatus = CardStatus.PENDING
)

/**
 * Đại diện cho một tin nhắn trong cuộc hội thoại với Trợ Lý AI
 */
data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val sender: MessageSender,
    val text: String,
    val timestamp: Long = System.currentTimeMillis(),
    val toolAction: ToolAction? = null,
    val toolActions: List<ToolAction> = emptyList(), // Hỗ trợ danh sách tối đa 6 action
    val cardStatus: CardStatus = CardStatus.PENDING,
    val isErrorMessage: Boolean = false
) {
    /**
     * Danh sách toàn bộ tool actions (tương thích ngược với cả toolAction đơn lẻ)
     */
    val allToolActions: List<ToolAction>
        get() = if (toolActions.isNotEmpty()) toolActions else listOfNotNull(toolAction)

    /**
     * Trạng thái tổng thể tự động:
     * - Nếu còn ít nhất 1 action PENDING -> PENDING
     * - Nếu toàn bộ action đã CANCELLED -> CANCELLED
     * - Nếu có ít nhất 1 CONFIRMED và không còn PENDING -> CONFIRMED
     */
    val effectiveStatus: CardStatus
        get() {
            val actions = allToolActions
            if (actions.isEmpty()) return cardStatus
            if (actions.any { it.status == CardStatus.PENDING }) return CardStatus.PENDING
            if (actions.all { it.status == CardStatus.CANCELLED }) return CardStatus.CANCELLED
            return CardStatus.CONFIRMED
        }
}
