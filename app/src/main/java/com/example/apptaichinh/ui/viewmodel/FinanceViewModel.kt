package com.example.apptaichinh.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.apptaichinh.data.ai.AiResponse
import com.example.apptaichinh.data.ai.CardStatus
import com.example.apptaichinh.data.ai.ChatMessage
import com.example.apptaichinh.data.ai.MessageSender
import com.example.apptaichinh.data.ai.ToolAction
import com.example.apptaichinh.data.ai.ToolActionType
import com.example.apptaichinh.data.model.Category
import com.example.apptaichinh.data.model.CategoryBudget
import com.example.apptaichinh.data.model.CategoryStat
import com.example.apptaichinh.data.model.MonthSummary
import com.example.apptaichinh.data.model.OverallBudget
import com.example.apptaichinh.data.model.Transaction
import com.example.apptaichinh.data.repository.FinanceRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.regex.Pattern

data class ParsedTransaction(
    val amount: Long,
    val type: String,
    val categoryId: Long,
    val categoryName: String,
    val note: String,
    val isValid: Boolean
)

class FinanceViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = FinanceRepository.getInstance(application)

    val selectedYear: StateFlow<Int> = repository.selectedYear
    val selectedMonth: StateFlow<Int> = repository.selectedMonth
    val typeFilter: StateFlow<String> = repository.typeFilter
    val searchQuery: StateFlow<String> = repository.searchQuery
    val transactions: StateFlow<List<Transaction>> = repository.transactions
    val monthSummary: StateFlow<MonthSummary> = repository.monthSummary
    val overallBudget: StateFlow<OverallBudget> = repository.overallBudget
    val categoryBudgets: StateFlow<List<CategoryBudget>> = repository.categoryBudgets
    val categoryStats: StateFlow<List<CategoryStat>> = repository.categoryStats
    val categories: StateFlow<List<Category>> = repository.categories

    // AI Configuration & State
    val aiServerUrl: StateFlow<String> = repository.aiServerUrl
    val aiModelName: StateFlow<String> = repository.aiModelName

    fun setAiServerUrl(url: String) = repository.setAiServerUrl(url)
    fun setAiModelName(model: String) = repository.setAiModelName(model)

    // Chat Assistant Messages & Loading State
    private val _chatMessages = MutableStateFlow<List<ChatMessage>>(
        listOf(
            ChatMessage(
                sender = MessageSender.ASSISTANT,
                text = "Xin chào! Mình là Trợ Lý AI. Bạn có thể trò chuyện tự nhiên để thêm, sửa hoặc xóa chi tiêu (ví dụ: 'Ăn phở 45k', 'Sửa tiền ăn phở thành 50k', 'Xóa khoản ăn phở 45k trưa nay') nhé!"
            )
        )
    )
    val chatMessages: StateFlow<List<ChatMessage>> = _chatMessages.asStateFlow()

    private val _isAiThinking = MutableStateFlow(false)
    val isAiThinking: StateFlow<Boolean> = _isAiThinking.asStateFlow()

    fun pingAiServer(url: String, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            val res = repository.aiService.pingServer(url)
            res.onSuccess { onResult(true, it) }
                .onFailure { onResult(false, it.localizedMessage ?: "Lỗi kết nối") }
        }
    }

    fun clearChat() {
        _chatMessages.value = listOf(
            ChatMessage(
                sender = MessageSender.ASSISTANT,
                text = "Xin chào! Mình là Trợ Lý AI. Bạn có thể trò chuyện tự nhiên để thêm, sửa hoặc xóa chi tiêu (ví dụ: 'Ăn phở 45k', 'Sửa tiền ăn phở thành 50k', 'Xóa khoản ăn phở 45k trưa nay') nhé!"
            )
        )
    }

    fun sendAiChatMessage(userText: String) {
        val trimmed = userText.trim()
        if (trimmed.isEmpty()) return

        val userMsg = ChatMessage(
            sender = MessageSender.USER,
            text = trimmed
        )
        _chatMessages.value = _chatMessages.value + userMsg
        _isAiThinking.value = true

        viewModelScope.launch {
            val response = repository.aiService.sendMessage(
                userMessage = trimmed,
                conversationHistory = _chatMessages.value,
                categories = categories.value,
                serverUrl = aiServerUrl.value,
                modelName = aiModelName.value
            )

            _isAiThinking.value = false

            when (response) {
                is AiResponse.TextReply -> {
                    val botMsg = ChatMessage(
                        sender = MessageSender.ASSISTANT,
                        text = response.text
                    )
                    _chatMessages.value = _chatMessages.value + botMsg
                }

                is AiResponse.ToolCallReply -> {
                    val action = response.toolAction
                    if ((action.type == ToolActionType.UPDATE || action.type == ToolActionType.DELETE) && action.targetTransaction == null) {
                        // Không tìm thấy giao dịch khớp trong database
                        val notFoundMsg = ChatMessage(
                            sender = MessageSender.ASSISTANT,
                            text = "⚠️ Không tìm thấy giao dịch nào khớp với từ khóa \"${action.searchKeyword}\" trong sổ để thực hiện ${if (action.type == ToolActionType.UPDATE) "chỉnh sửa" else "xóa"}."
                        )
                        _chatMessages.value = _chatMessages.value + notFoundMsg
                    } else {
                        val botMsg = ChatMessage(
                            sender = MessageSender.ASSISTANT,
                            text = response.assistantExplanation.ifBlank {
                                when (action.type) {
                                    ToolActionType.CREATE -> "Mình đã chuẩn bị thẻ giao dịch mới. Bạn hãy kiểm tra và xác nhận lưu nhé!"
                                    ToolActionType.UPDATE -> "Mình tìm thấy giao dịch cần sửa. Bạn hãy kiểm tra thông tin thay đổi bên dưới:"
                                    ToolActionType.DELETE -> "Cảnh báo: Bạn có chắc chắn muốn xóa giao dịch sau không?"
                                }
                            },
                            toolAction = action,
                            cardStatus = CardStatus.PENDING
                        )
                        _chatMessages.value = _chatMessages.value + botMsg
                    }
                }

                is AiResponse.Error -> {
                    val errMsg = ChatMessage(
                        sender = MessageSender.ASSISTANT,
                        text = response.message,
                        isErrorMessage = true
                    )
                    _chatMessages.value = _chatMessages.value + errMsg
                }
            }
        }
    }

    // Xác nhận thực thi Tool Action trên Database (Safety-First)
    fun confirmToolAction(messageId: String) {
        val targetMsg = _chatMessages.value.find { it.id == messageId } ?: return
        val action = targetMsg.toolAction ?: return

        // 1. Đổi trạng thái card sang CONFIRMED
        _chatMessages.value = _chatMessages.value.map { msg ->
            if (msg.id == messageId) msg.copy(cardStatus = CardStatus.CONFIRMED) else msg
        }

        // 2. Thực thi thay đổi trên DB
        viewModelScope.launch {
            when (action.type) {
                ToolActionType.CREATE -> {
                    val newTx = Transaction(
                        id = 0L,
                        amount = action.amount,
                        type = action.transactionType,
                        categoryId = action.categoryId,
                        note = action.note,
                        dateEpoch = System.currentTimeMillis()
                    )
                    repository.addTransaction(newTx)
                    val confirmNotice = ChatMessage(
                        sender = MessageSender.SYSTEM,
                        text = "✓ Đã ghi vào sổ: ${action.categoryIcon} ${action.categoryName} (${action.note}) - ${com.example.apptaichinh.ui.components.Formatters.formatVnd(action.amount)}"
                    )
                    _chatMessages.value = _chatMessages.value + confirmNotice
                }

                ToolActionType.UPDATE -> {
                    val oldTx = action.targetTransaction ?: return@launch
                    val updatedTx = oldTx.copy(
                        amount = action.newAmount ?: oldTx.amount,
                        categoryId = action.newCategory?.id ?: oldTx.categoryId,
                        note = action.newNote ?: oldTx.note
                    )
                    repository.updateTransaction(updatedTx)
                    val confirmNotice = ChatMessage(
                        sender = MessageSender.SYSTEM,
                        text = "✓ Đã cập nhật thành công giao dịch!"
                    )
                    _chatMessages.value = _chatMessages.value + confirmNotice
                }

                ToolActionType.DELETE -> {
                    val targetTx = action.targetTransaction ?: return@launch
                    repository.deleteTransaction(targetTx.id)
                    val confirmNotice = ChatMessage(
                        sender = MessageSender.SYSTEM,
                        text = "✓ Đã xóa giao dịch (${targetTx.categoryIcon} ${targetTx.note}) khỏi sổ!"
                    )
                    _chatMessages.value = _chatMessages.value + confirmNotice
                }
            }
        }
    }

    // Hủy bỏ Tool Action (Safety-First: Không có gì thay đổi trong DB)
    fun cancelToolAction(messageId: String) {
        _chatMessages.value = _chatMessages.value.map { msg ->
            if (msg.id == messageId) msg.copy(cardStatus = CardStatus.CANCELLED) else msg
        }
    }

    // Navigation tab: 0 = Dashboard, 1 = Ngân Sách, 2 = Thống Kê, 3 = Danh Mục, 4 = Chat Assistant
    private val _currentTab = MutableStateFlow(0)
    val currentTab: StateFlow<Int> = _currentTab.asStateFlow()

    fun setCurrentTab(tab: Int) {
        _currentTab.value = tab
    }

    fun prevMonth() = repository.prevMonth()
    fun nextMonth() = repository.nextMonth()
    fun setMonth(year: Int, month: Int) = repository.setMonth(year, month)
    fun setFilter(type: String) = repository.setFilter(type)
    fun setSearchQuery(query: String) = repository.setSearchQuery(query)

    fun addTransaction(tx: Transaction, onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            repository.addTransaction(tx)
            onComplete()
        }
    }

    fun updateTransaction(tx: Transaction, onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            repository.updateTransaction(tx)
            onComplete()
        }
    }

    fun deleteTransaction(id: Long, onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            repository.deleteTransaction(id)
            onComplete()
        }
    }

    fun updateOverallBudget(amount: Long) {
        viewModelScope.launch {
            repository.updateOverallBudget(amount)
        }
    }

    fun updateCategoryBudget(categoryId: Long, budget: Long) {
        viewModelScope.launch {
            repository.updateCategoryBudget(categoryId, budget)
        }
    }

    fun addCategory(category: Category, onComplete: (Category) -> Unit = {}) {
        viewModelScope.launch {
            val id = repository.addCategory(category)
            val created = repository.categories.value.find { it.id == id } ?: category.copy(id = id)
            onComplete(created)
        }
    }

    fun updateCategory(category: Category, onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            repository.updateCategory(category)
            onComplete()
        }
    }

    fun deleteCategory(id: Long, onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            repository.deleteCategory(id)
            onComplete()
        }
    }

    // --- Smart NLP Parser tiếng Việt ---
    fun parseQuickText(input: String): ParsedTransaction? {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return null

        val lower = trimmed.lowercase()

        // 1. Nhận diện số tiền
        var amount: Long = 0L

        // Regex patterns:
        // ví dụ: "45k", "45.000", "45000", "2tr", "2.5tr", "500 nghìn", "1 triệu", "1,5 triệu"
        val trPattern = Pattern.compile("([0-9]+[.,]?[0-9]*)\\s*(tr|triệu|trieu|m)")
        val kPattern = Pattern.compile("([0-9]+[.,]?[0-9]*)\\s*(k|nghìn|nghin|ngàn|ngan)")
        val numberPattern = Pattern.compile("([0-9]{1,3}(?:[.,][0-9]{3})+|[0-9]+)")

        val trMatcher = trPattern.matcher(lower)
        val kMatcher = kPattern.matcher(lower)
        val numMatcher = numberPattern.matcher(lower)

        if (trMatcher.find()) {
            val numStr = trMatcher.group(1)?.replace(',', '.') ?: "0"
            val mult = (numStr.toDoubleOrNull() ?: 0.0) * 1_000_000
            amount = mult.toLong()
        } else if (kMatcher.find()) {
            val numStr = kMatcher.group(1)?.replace(',', '.') ?: "0"
            val mult = (numStr.toDoubleOrNull() ?: 0.0) * 1_000
            amount = mult.toLong()
        } else if (numMatcher.find()) {
            val clean = numMatcher.group(1)?.replace(".", "")?.replace(",", "") ?: "0"
            amount = clean.toLongOrNull() ?: 0L
        }

        if (amount <= 0) return null

        // 2. Nhận diện loại thu nhập hay chi tiêu
        val isIncome = lower.contains("lương") || lower.contains("thưởng") ||
                lower.contains("tiền vào") || lower.contains("thu nhập") ||
                lower.contains("freelance") || lower.contains("làm thêm") ||
                lower.contains("bán được") || lower.contains("cổ tức") ||
                lower.contains("lãi") || lower.contains("đầu tư")

        val type = if (isIncome) "INCOME" else "EXPENSE"

        // 3. Ghép danh mục phù hợp nhất từ danh sách hiện có
        val allCats = categories.value.filter { it.type == type }
        var matchedCat: Category? = null

        if (isIncome) {
            when {
                lower.contains("thưởng") || lower.contains("bonus") || lower.contains("kpi") ->
                    allCats.find { it.name.contains("thưởng", true) }?.let { matchedCat = it }

                lower.contains("lương") || lower.contains("salary") ->
                    allCats.find { it.name.contains("lương", true) }?.let { matchedCat = it }

                lower.contains("làm thêm") || lower.contains("freelance") || lower.contains("ot") ||
                lower.contains("part-time") || lower.contains("tăng ca") || lower.contains("dự án") ->
                    allCats.find { it.name.contains("làm thêm", true) }?.let { matchedCat = it }

                lower.contains("đầu tư") || lower.contains("cổ tức") || lower.contains("chứng khoán") ||
                lower.contains("tiền lãi") || lower.contains("lãi") || lower.contains("crypto") ->
                    allCats.find { it.name.contains("đầu tư", true) }?.let { matchedCat = it }

                else ->
                    allCats.find { it.name.contains("khác", true) } ?: allCats.firstOrNull()
            }
        } else {
            when {
                lower.contains("ăn") || lower.contains("uống") || lower.contains("phở") ||
                lower.contains("bún") || lower.contains("cơm") || lower.contains("bánh") ||
                lower.contains("lẩu") || lower.contains("trưa") || lower.contains("sáng") ||
                lower.contains("tối") || lower.contains("thịt") || lower.contains("chợ") ||
                lower.contains("cà phê") || lower.contains("cafe") || lower.contains("trà") ->
                    allCats.find { it.name.contains("ăn uống", true) }?.let { matchedCat = it }

                lower.contains("nhà") || lower.contains("phòng") || lower.contains("thuê nhà") ||
                lower.contains("điện") || lower.contains("nước") || lower.contains("wifi") ||
                lower.contains("mạng") || lower.contains("chung cư") || lower.contains("tiền phòng") ->
                    allCats.find { it.name.contains("nhà ở", true) }?.let { matchedCat = it }

                lower.contains("xăng") || lower.contains("xe") || lower.contains("grab") ||
                lower.contains("taxi") || lower.contains("vé") || lower.contains("bus") ||
                lower.contains("gửi xe") || lower.contains("đi lại") || lower.contains("sửa xe") ->
                    allCats.find { it.name.contains("đi lại", true) }?.let { matchedCat = it }

                lower.contains("mua") || lower.contains("áo") || lower.contains("quần") ||
                lower.contains("giày") || lower.contains("shopee") || lower.contains("lazada") ||
                lower.contains("tiki") || lower.contains("sắm") || lower.contains("đồ") ->
                    allCats.find { it.name.contains("mua sắm", true) }?.let { matchedCat = it }

                lower.contains("giải trí") || lower.contains("phim") || lower.contains("game") ||
                lower.contains("nhậu") || lower.contains("karaoke") || lower.contains("du lịch") ||
                lower.contains("hát") || lower.contains("xem phim") ->
                    allCats.find { it.name.contains("giải trí", true) }?.let { matchedCat = it }

                lower.contains("học") || lower.contains("sách") || lower.contains("khóa học") ||
                lower.contains("giáo dục") || lower.contains("học phí") || lower.contains("trường") ->
                    allCats.find { it.name.contains("giáo dục", true) }?.let { matchedCat = it }

                lower.contains("thuốc") || lower.contains("khám") || lower.contains("bệnh") ||
                lower.contains("sức khỏe") || lower.contains("bác sĩ") || lower.contains("nha khoa") ||
                lower.contains("vitamin") || lower.contains("y tế") ->
                    allCats.find { it.name.contains("sức khỏe", true) }?.let { matchedCat = it }

                lower.contains("cưới") || lower.contains("hỉ") || lower.contains("hiếu") ||
                lower.contains("đám ma") || lower.contains("tang") || lower.contains("sinh nhật") ||
                lower.contains("thôi nôi") || lower.contains("mừng") || lower.contains("phong bì") ->
                    allCats.find { it.name.contains("hiếu hỉ", true) }?.let { matchedCat = it }

                lower.contains("tiết kiệm") || lower.contains("gửi tiết kiệm") || lower.contains("bỏ lợn") ||
                lower.contains("tích lũy") || lower.contains("heo đất") ->
                    allCats.find { it.name.contains("tiết kiệm", true) }?.let { matchedCat = it }

                lower.contains("nợ") || lower.contains("trả góp") || lower.contains("vay") ||
                lower.contains("thẻ tín dụng") || lower.contains("trả nợ") || lower.contains("lãi vay") ->
                    allCats.find { it.name.contains("trả nợ", true) }?.let { matchedCat = it }

                else ->
                    allCats.firstOrNull()
            }
        }

        val catId = matchedCat?.id ?: 0L
        val catName = matchedCat?.name ?: (if (type == "EXPENSE") "Chi tiêu khác" else "Thu nhập khác")

        return ParsedTransaction(
            amount = amount,
            type = type,
            categoryId = catId,
            categoryName = catName,
            note = trimmed,
            isValid = true
        )
    }
}
