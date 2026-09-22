package com.example.apptaichinh.data.ai

import com.example.apptaichinh.data.ai.prompts.AgentPrompts
import com.example.apptaichinh.data.ai.tools.LocalToolExecutor
import com.example.apptaichinh.data.ai.tools.ToolDefinitions
import com.example.apptaichinh.data.db.FinanceDatabaseHelper
import com.example.apptaichinh.data.model.Category
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

sealed class AiResponse {
    data class TextReply(val text: String) : AiResponse()
    data class ToolCallReply(val toolAction: ToolAction, val assistantExplanation: String) : AiResponse()
    data class Error(val message: String) : AiResponse()
}

/**
 * Service điều phối AI Đa Tác Tử (Multi-Agent System) với vòng lặp ReAct tự trị:
 * - Bước 1: Phân tích ý định & lập kế hoạch tuần tự.
 * - Bước 2: Tự động gọi và thực thi các Query Tools từ SQLite DB cục bộ.
 * - Bước 3: Đề xuất Action Tools (kèm Preview Card an toàn cho người dùng duyệt).
 * - Bước 4: Tổng hợp câu trả lời hoàn chỉnh cuối cùng (Final Synthesis).
 */
class AiService(private val dbHelper: FinanceDatabaseHelper) {

    private val localToolExecutor = LocalToolExecutor(dbHelper)

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    suspend fun pingServer(serverUrl: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val cleanUrl = serverUrl.trim().trimEnd('/')
            val endpoint = "$cleanUrl/v1/models"
            val request = Request.Builder()
                .url(endpoint)
                .addHeader("ngrok-skip-browser-warning", "true")
                .addHeader("User-Agent", "AppTaiChinh-Android/1.0")
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                val bodyStr = response.body?.string() ?: ""
                if (response.isSuccessful) {
                    Result.success("Kết nối thành công tới LM Studio!")
                } else if (response.code == 404 && bodyStr.contains("ngrok")) {
                    Result.failure(IOException("Ngrok đang offline hoặc chưa bật domain. Hãy chạy: ngrok http 1234 --url=$cleanUrl"))
                } else {
                    Result.failure(IOException("Mã phản hồi từ máy chủ: ${response.code} (${response.message})"))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Vòng lặp điều phối Đa Tác Tử (Multi-turn ReAct Loop).
     * Cho phép LLM tự động suy luận qua nhiều bước tra cứu dữ liệu trước khi kết luận.
     */
    suspend fun sendMessage(
        userMessage: String,
        conversationHistory: List<ChatMessage>,
        categories: List<Category>,
        serverUrl: String,
        modelName: String
    ): AiResponse = withContext(Dispatchers.IO) {
        try {
            val cleanUrl = serverUrl.trim().trimEnd('/')
            if (cleanUrl.isBlank()) {
                return@withContext AiResponse.Error("Vui lòng cài đặt URL ngrok hoặc LM Studio IP trước khi sử dụng.")
            }

            val endpoint = "$cleanUrl/v1/chat/completions"

            // 1. Khởi tạo mảng tin nhắn với System Prompt từ file AgentPrompts riêng biệt
            val messagesArray = JSONArray()
            val systemMsg = JSONObject().apply {
                put("role", "system")
                put("content", AgentPrompts.buildOrchestratorPrompt(categories))
            }
            messagesArray.put(systemMsg)

            // 2. Nạp ngữ cảnh gần nhất của cuộc trò chuyện
            val recentHistory = conversationHistory.takeLast(4)
            for (msg in recentHistory) {
                if (msg.sender == MessageSender.SYSTEM) continue
                val role = if (msg.sender == MessageSender.USER) "user" else "assistant"
                messagesArray.put(JSONObject().apply {
                    put("role", role)
                    put("content", msg.text)
                })
            }

            // 3. Nạp tin nhắn hiện tại của người dùng
            messagesArray.put(JSONObject().apply {
                put("role", "user")
                put("content", userMessage)
            })

            var pendingToolAction: ToolAction? = null
            var finalExplanation = ""
            val maxIterations = 4 // Giới hạn tối đa 4 bước suy luận để đảm bảo tốc độ

            // --- VÒNG LẶP ĐA TÁC TỬ (MULTI-STEP REACT LOOP) ---
            for (iteration in 0 until maxIterations) {
                val payload = JSONObject().apply {
                    put("model", modelName.ifBlank { "qwen2.5-3b-instruct" })
                    put("messages", messagesArray)
                    put("tools", ToolDefinitions.getAllTools())
                    put("tool_choice", "auto")
                    put("temperature", 0.1) // Nhiệt độ thấp giúp trích xuất chuẩn xác và ổn định
                }

                val body = payload.toString().toRequestBody(jsonMediaType)
                val request = Request.Builder()
                    .url(endpoint)
                    .addHeader("ngrok-skip-browser-warning", "true")
                    .addHeader("User-Agent", "AppTaiChinh-Android/1.0")
                    .post(body)
                    .build()

                val responseStr: String
                client.newCall(request).execute().use { response ->
                    responseStr = response.body?.string() ?: ""
                    if (!response.isSuccessful) {
                        return@withContext AiResponse.Error("Lỗi kết nối LM Studio (Mã ${response.code}): $responseStr")
                    }
                }

                val jsonResponse = JSONObject(responseStr)
                val choices = jsonResponse.optJSONArray("choices")
                if (choices == null || choices.length() == 0) {
                    break
                }

                val choiceObj = choices.getJSONObject(0)
                val messageObj = choiceObj.getJSONObject("message")
                val contentText = messageObj.optString("content", "")
                if (contentText.isNotBlank()) {
                    finalExplanation = contentText
                }

                val toolCalls = messageObj.optJSONArray("tool_calls")

                // Nếu mô hình không gọi thêm tool nào -> Đã hoàn tất suy luận!
                if (toolCalls == null || toolCalls.length() == 0) {
                    break
                }

                // Lưu lại tin nhắn assistant gọi tool vào messagesArray theo chuẩn OpenAI
                messagesArray.put(messageObj)

                var hasQueryToolExecuted = false

                // Duyệt qua các tool_calls mà mô hình yêu cầu thực thi
                for (i in 0 until toolCalls.length()) {
                    val toolCall = toolCalls.getJSONObject(i)
                    val toolId = toolCall.optString("id", "call_${System.currentTimeMillis()}_$i")
                    val functionObj = toolCall.getJSONObject("function")
                    val functionName = functionObj.getString("name")
                    val argumentsStr = functionObj.getString("arguments")
                    val argsJson = try {
                        JSONObject(argumentsStr)
                    } catch (e: Exception) {
                        JSONObject()
                    }

                    if (localToolExecutor.isQueryTool(functionName)) {
                        // A. Thực thi Query Tool trực tiếp từ SQLite cục bộ
                        val queryResultJson = localToolExecutor.executeQueryTool(functionName, argsJson, categories)

                        // Nạp kết quả tool vào hội thoại để LLM tiếp tục đọc
                        messagesArray.put(JSONObject().apply {
                            put("role", "tool")
                            put("tool_call_id", toolId)
                            put("content", queryResultJson)
                        })
                        hasQueryToolExecuted = true
                    } else {
                        // B. Action Tool (create / update / delete)
                        val action = processToolAction(functionName, argsJson, categories, userMessage)
                        pendingToolAction = action

                        // Báo cho LLM biết phiếu đã được tạo để LLM sinh câu trả lời hoàn thiện
                        messagesArray.put(JSONObject().apply {
                            put("role", "tool")
                            put("tool_call_id", toolId)
                            put("content", JSONObject().apply {
                                put("status", "SUCCESS_PROPOSED")
                                put("message", "Đã soạn phiếu xác nhận giao dịch thành công để người dùng duyệt an toàn trên màn hình.")
                            }.toString())
                        })
                    }
                }

                // Nếu chỉ là action tool mà không có query nào cần chạy tiếp -> Vòng lặp sau sẽ lấy câu kết luận
            }

            // Chống Hallucination của LLM:
            // Nếu người dùng nhập câu thu/chi rõ ràng (có số tiền phát hiện được) nhưng LLM "chém gió" bằng text
            // (VD: "Đã thêm khoản chi tiêu vào sổ...") mà quên gọi create_transaction:
            // Hệ thống tự động phục hồi hành động (Auto-Recovery) để luôn có Phiếu Xem Trước cho người dùng bấm Lưu!
            if (pendingToolAction == null) {
                val detectedAmount = LocalToolExecutor.extractAmountFromText(userMessage)
                val lowerMsg = userMessage.lowercase()
                val isQueryIntent = lowerMsg.contains("hết bao nhiêu") || lowerMsg.contains("bao nhiêu tiền") ||
                        lowerMsg.contains("vượt chưa") || lowerMsg.contains("còn bao nhiêu") ||
                        lowerMsg.contains("xem lại") || lowerMsg.contains("tìm") || lowerMsg.contains("kiểm tra")

                if (detectedAmount != null && detectedAmount > 0 && !isQueryIntent) {
                    val isIncome = LocalToolExecutor.isIncomeIntent(userMessage)
                    val type = if (isIncome) "INCOME" else "EXPENSE"
                    val (matchedCat, _) = LocalToolExecutor.matchBestCategory(userMessage, type, categories)
                    val cat = matchedCat ?: categories.find { it.type == type } ?: Category(0, "Khác", type, "📦", "#607D8B", 0L)

                    pendingToolAction = ToolAction(
                        type = ToolActionType.CREATE,
                        amount = detectedAmount,
                        transactionType = type,
                        categoryId = cat.id,
                        categoryName = cat.name,
                        categoryIcon = cat.icon,
                        categoryColorHex = cat.colorHex,
                        note = userMessage.take(50)
                    )
                }
            }

            // Trả về kết quả sau khi hoàn tất toàn bộ chuỗi suy luận
            // Khi có pendingToolAction (tạo/sửa giao dịch), luôn dùng câu thông báo chuẩn xác đồng bộ với phiếu xem trước
            val textOutput = if (pendingToolAction != null) {
                val action = pendingToolAction
                when (action.type) {
                    ToolActionType.CREATE_CATEGORY -> {
                        val typeName = if (action.transactionType == "INCOME") "Thu nhập" else "Chi tiêu"
                        "Đã nhận diện đề xuất tạo danh mục $typeName mới:\n• Danh mục: ${action.categoryIcon} ${action.categoryName}${if (action.categoryBudget > 0) "\n• Hạn mức: ${com.example.apptaichinh.ui.components.Formatters.formatVnd(action.categoryBudget)}" else ""}\n\nMình đã soạn sẵn phiếu đề xuất bên dưới, bạn hãy kiểm tra và nhấn 'Xác Nhận Tạo' nhé!"
                    }
                    ToolActionType.UPDATE -> {
                        "Đã soạn phiếu chỉnh sửa giao dịch. Bạn có thể kiểm tra và nhấn 'Xác Nhận Sửa' bên dưới nhé!"
                    }
                    ToolActionType.DELETE -> {
                        "Đã soạn phiếu xóa giao dịch. Bạn hãy nhấn 'Xác Nhận Xóa' bên dưới nếu muốn xóa bỏ khoản này nhé!"
                    }
                    else -> {
                        val typeName = if (action.transactionType == "INCOME") "Thu nhập" else "Chi tiêu"
                        val amountStr = com.example.apptaichinh.ui.components.Formatters.formatVnd(action.amount)
                        "Đã nhận diện khoản $typeName:\n• Danh mục: ${action.categoryIcon} ${action.categoryName}\n• Số tiền: $amountStr${if (action.note.isNotBlank()) "\n• Ghi chú: ${action.note}" else ""}\n\nMình đã soạn sẵn phiếu bên dưới, bạn có thể bấm 'Sửa' (✏️) để chỉnh lại thông tin hoặc nhấn 'Xác Nhận Lưu' nhé!"
                    }
                }
            } else if (finalExplanation.isNotBlank()) {
                var cleanExplanation = finalExplanation
                // Guardrail: Xử lý lỗi dịch ngược của mô hình LLM nhỏ khi dịch "remaining" thành "còn thiếu"
                // Khi đang trong ngữ cảnh chi tiêu vượt ngân sách, không để câu "còn thiếu [X] đ" gây hiểu lầm
                if (cleanExplanation.contains("còn thiếu", ignoreCase = true)) {
                    if (cleanExplanation.contains("vượt", ignoreCase = true) || 
                        cleanExplanation.contains("lố", ignoreCase = true) || 
                        cleanExplanation.contains("ngân sách", ignoreCase = true)) {
                        cleanExplanation = cleanExplanation
                            .replace(Regex("và còn thiếu\\s+[0-9.,]+(đ| đồng| ₫)?", RegexOption.IGNORE_CASE), "")
                            .replace(Regex(",?\\s*còn thiếu\\s+[0-9.,]+(đ| đồng| ₫)?", RegexOption.IGNORE_CASE), "")
                    }
                }
                // Khử sạch dấu markdown in đậm '**' hoặc '`' quanh số tiền và dữ liệu (VD: **1800000** -> 1800000)
                cleanExplanation = cleanExplanation
                    .replace("**", "")
                    .replace("`", "")
                    .replace("###", "")
                    .replace("##", "")
                cleanExplanation
            } else {
                "Xin chào! Mình đã tiếp nhận thông tin của bạn."
            }

            return@withContext if (pendingToolAction != null) {
                AiResponse.ToolCallReply(pendingToolAction, textOutput)
            } else {
                AiResponse.TextReply(textOutput)
            }

        } catch (e: Exception) {
            AiResponse.Error("Không thể kết nối đến máy chủ AI: ${e.localizedMessage ?: e.message}")
        }
    }

    private fun processToolAction(
        functionName: String,
        args: JSONObject,
        categories: List<Category>,
        userMessage: String = ""
    ): ToolAction {
        return when (functionName) {
            "create_transaction" -> {
                val amountFromArgs = args.optLong("amount", 0L)
                val detectedAmount = LocalToolExecutor.extractAmountFromText(userMessage)
                // Khắc phục triệt để lỗi tính nhẩm của LLM nhỏ (như 100k thành 10k):
                // Nếu người dùng có nêu rõ số tiền trong câu nói thì ưu tiên giá trị trích xuất chuẩn xác tuyệt đối
                val amount = if (detectedAmount != null && detectedAmount > 0) detectedAmount else amountFromArgs
                val type = args.optString("type", "EXPENSE")
                val catName = args.optString("category_name", "")
                val noteArg = args.optString("note", "").trim()
                val note = if (noteArg.isNotBlank() && !noteArg.equals("chi tiêu", ignoreCase = true)) noteArg else userMessage.take(50)

                // Tìm danh mục khớp nhất trong DB
                val filteredCats = categories.filter { it.type == type }
                val matchedCat = filteredCats.find { it.name.equals(catName, ignoreCase = true) }
                    ?: filteredCats.find { it.name.contains(catName, ignoreCase = true) || catName.contains(it.name, ignoreCase = true) }
                    ?: LocalToolExecutor.matchBestCategory(catName, type, filteredCats).first
                    ?: filteredCats.firstOrNull()
                    ?: Category(0, catName, type, "📦", "#607D8B", 0L)

                ToolAction(
                    type = ToolActionType.CREATE,
                    amount = amount,
                    transactionType = type,
                    categoryId = matchedCat.id,
                    categoryName = matchedCat.name,
                    categoryIcon = matchedCat.icon,
                    categoryColorHex = matchedCat.colorHex,
                    note = note
                )
            }

            "update_transaction" -> {
                val searchKeyword = args.optString("search_keyword", "")
                val oldAmount = if (args.has("old_amount")) args.optLong("old_amount") else null
                val newAmount = if (args.has("new_amount")) args.optLong("new_amount") else null
                val newCatName = if (args.has("new_category_name")) args.optString("new_category_name") else null
                val newNote = if (args.has("new_note")) args.optString("new_note") else null

                val foundList = dbHelper.searchTransactions(searchKeyword, oldAmount)
                val targetTx = foundList.firstOrNull()

                val newCat = if (!newCatName.isNullOrBlank()) {
                    categories.find { it.name.contains(newCatName, ignoreCase = true) }
                } else null

                ToolAction(
                    type = ToolActionType.UPDATE,
                    targetTransaction = targetTx,
                    newAmount = newAmount,
                    newCategory = newCat,
                    newNote = newNote,
                    searchKeyword = searchKeyword
                )
            }

            "delete_transaction" -> {
                val searchKeyword = args.optString("search_keyword", "")
                val amount = if (args.has("amount")) args.optLong("amount") else null

                val foundList = dbHelper.searchTransactions(searchKeyword, amount)
                val targetTx = foundList.firstOrNull()

                ToolAction(
                    type = ToolActionType.DELETE,
                    targetTransaction = targetTx,
                    amount = amount ?: targetTx?.amount ?: 0L,
                    searchKeyword = searchKeyword
                )
            }

            "create_category" -> {
                val catName = args.optString("name", "Mới").trim()
                val type = args.optString("type", "EXPENSE")
                val icon = args.optString("icon", if (type == "INCOME") "💰" else "📦").trim()
                val budget = args.optLong("budget", 0L)
                val defaultColor = if (type == "INCOME") "#10B981" else "#8B5CF6"

                ToolAction(
                    type = ToolActionType.CREATE_CATEGORY,
                    transactionType = type,
                    categoryName = catName,
                    categoryIcon = icon,
                    categoryColorHex = defaultColor,
                    categoryBudget = budget,
                    note = "Tạo danh mục $catName"
                )
            }

            else -> ToolAction(
                type = ToolActionType.CREATE,
                note = args.optString("note", "")
            )
        }
    }
}
