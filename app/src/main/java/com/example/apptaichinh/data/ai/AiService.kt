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
    data class ToolCallReply(
        val toolAction: ToolAction,
        val assistantExplanation: String,
        val toolActions: List<ToolAction> = listOf(toolAction)
    ) : AiResponse()
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

            // 2. Nạp ngữ cảnh cuộc trò chuyện kèm TRẠNG THÁI DUYỆT THỰC TẾ (CONFIRMED / CANCELLED / PENDING)
            // Loại bỏ trùng lặp tin nhắn cuối của user (nếu đã nằm trong conversationHistory)
            val historyWithoutCurrent = if (conversationHistory.isNotEmpty() &&
                conversationHistory.last().sender == MessageSender.USER &&
                conversationHistory.last().text == userMessage
            ) {
                conversationHistory.dropLast(1)
            } else {
                conversationHistory
            }

            // Tăng lịch sử hội thoại lên 8 tin gần nhất để AI nhớ sâu ngữ cảnh
            val recentHistory = historyWithoutCurrent.takeLast(8)
            for (msg in recentHistory) {
                when (msg.sender) {
                    MessageSender.USER -> {
                        messagesArray.put(JSONObject().apply {
                            put("role", "user")
                            put("content", msg.text)
                        })
                    }

                    MessageSender.ASSISTANT -> {
                        // Bổ sung trạng thái thực tế của thẻ (Đã duyệt / Đã hủy / Đang chờ) vào ngữ cảnh AI
                        val actions = msg.allToolActions
                        val statusContext = if (actions.isNotEmpty()) {
                            val confirmed = actions.filter { it.status == CardStatus.CONFIRMED }
                            val cancelled = actions.filter { it.status == CardStatus.CANCELLED }
                            val pending = actions.filter { it.status == CardStatus.PENDING }

                            val sb = StringBuilder("\n[HỆ THỐNG TRẠNG THÁI GIAO DỊCH:")
                            if (confirmed.isNotEmpty()) {
                                val list = confirmed.joinToString("; ") { act ->
                                    val sign = if (act.transactionType == "INCOME") "+" else "-"
                                    "${act.categoryIcon} ${act.categoryName} (${act.note}): $sign${com.example.apptaichinh.ui.components.Formatters.formatVnd(act.amount)}"
                                }
                                sb.append(" ĐÃ LƯU VÀO SỔ: $list.")
                            }
                            if (cancelled.isNotEmpty()) {
                                val list = cancelled.joinToString("; ") { act ->
                                    "${act.categoryIcon} ${act.categoryName} (${act.note})"
                                }
                                sb.append(" ĐÃ HỦY: $list.")
                            }
                            if (pending.isNotEmpty()) {
                                val list = pending.joinToString("; ") { act ->
                                    "${act.categoryIcon} ${act.categoryName} (${act.note})"
                                }
                                sb.append(" ĐANG CHỜ DUYỆT: $list.")
                            }
                            sb.append("]")
                            sb.toString()
                        } else ""

                        messagesArray.put(JSONObject().apply {
                            put("role", "assistant")
                            put("content", msg.text + statusContext)
                        })
                    }

                    MessageSender.SYSTEM -> {
                        // Nạp cả thông báo xác nhận hệ thống vào ngữ cảnh để AI biết giao dịch đã thành công
                        messagesArray.put(JSONObject().apply {
                            put("role", "user")
                            put("content", "[HỆ THỐNG]: ${msg.text}")
                        })
                    }
                }
            }

            // 3. Nạp tin nhắn hiện tại của người dùng
            messagesArray.put(JSONObject().apply {
                put("role", "user")
                put("content", userMessage)
            })

            val pendingToolActions = mutableListOf<ToolAction>()
            var finalExplanation = ""
            val maxIterations = 6 // Tăng lên tối đa 6 bước suy luận (hỗ trợ tối đa 6 action)

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
                        // B. Action Tool (create / update / delete) - hỗ trợ tối đa 6 action
                        if (pendingToolActions.size < 6) {
                            val action = processToolAction(functionName, argsJson, categories, userMessage)
                            pendingToolActions.add(action)
                        }

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
            // Hệ thống tự động phục hồi hành động (Auto-Recovery đa mệnh đề tối đa 6 khoản) để luôn có Phiếu Xem Trước cho người dùng bấm Lưu!
            if (pendingToolActions.isEmpty()) {
                val clauses = LocalToolExecutor.splitMultiItemText(userMessage)
                val lowerMsg = userMessage.lowercase()
                val isQueryIntent = lowerMsg.contains("hết bao nhiêu") || lowerMsg.contains("bao nhiêu tiền") ||
                        lowerMsg.contains("vượt chưa") || lowerMsg.contains("còn bao nhiêu") ||
                        lowerMsg.contains("xem lại") || lowerMsg.contains("tìm") || lowerMsg.contains("kiểm tra")

                if (!isQueryIntent) {
                    for (clause in clauses.take(6)) {
                        val detectedAmount = LocalToolExecutor.extractAmountFromText(clause)
                        if (detectedAmount != null && detectedAmount > 0) {
                            val isIncome = LocalToolExecutor.isIncomeIntent(clause)
                            val type = if (isIncome) "INCOME" else "EXPENSE"
                            val (matchedCat, _) = LocalToolExecutor.matchBestCategory(clause, type, categories)
                            val cat = matchedCat ?: categories.find { it.type == type } ?: Category(0, "Khác", type, "📦", "#607D8B", 0L)

                            pendingToolActions.add(
                                ToolAction(
                                    type = ToolActionType.CREATE,
                                    amount = detectedAmount,
                                    transactionType = type,
                                    categoryId = cat.id,
                                    categoryName = cat.name,
                                    categoryIcon = cat.icon,
                                    categoryColorHex = cat.colorHex,
                                    note = cleanNote("", clause, cat.name)
                                )
                            )
                        }
                    }
                }
            }

            // Trả về kết quả sau khi hoàn tất toàn bộ chuỗi suy luận
            // Khi có pendingToolActions (tạo/sửa giao dịch), luôn dùng câu thông báo chuẩn xác đồng bộ với phiếu xem trước
            val textOutput = if (pendingToolActions.isNotEmpty()) {
                if (pendingToolActions.size == 1) {
                    val action = pendingToolActions.first()
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
                } else {
                    val sb = StringBuilder()
                    val totalAmount = pendingToolActions.filter { it.type == ToolActionType.CREATE }.sumOf { it.amount }
                    sb.append("Mình đã nhận diện ${pendingToolActions.size} khoản và chuẩn bị sẵn các phiếu bên dưới:\n")
                    pendingToolActions.forEachIndexed { index, act ->
                        val sign = if (act.transactionType == "INCOME") "+" else "-"
                        sb.append("${index + 1}. ${act.categoryIcon} ${act.categoryName} (${act.note}): $sign${com.example.apptaichinh.ui.components.Formatters.formatVnd(act.amount)}\n")
                    }
                    if (totalAmount > 0) {
                        sb.append("\n• Tổng cộng: ${com.example.apptaichinh.ui.components.Formatters.formatVnd(totalAmount)}\n")
                    }
                    sb.append("\nBạn có thể kiểm tra từng thẻ hoặc nhấn 'Xác Nhận Lưu Tất Cả' để ghi vào sổ nhé!")
                    sb.toString()
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

            return@withContext if (pendingToolActions.isNotEmpty()) {
                AiResponse.ToolCallReply(
                    toolAction = pendingToolActions.first(),
                    assistantExplanation = textOutput,
                    toolActions = pendingToolActions.take(6)
                )
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
                val catName = args.optString("category_name", "")
                val noteArg = args.optString("note", "").trim()
                val type = args.optString("type", "EXPENSE")

                // Xử lý số tiền chuẩn xác trong ngữ cảnh đơn lẻ hoặc đa khoản
                val clauses = LocalToolExecutor.splitMultiItemText(userMessage)
                val matchingClause = clauses.find { cl ->
                    val clAmount = LocalToolExecutor.extractAmountFromText(cl)
                    (amountFromArgs > 0 && clAmount == amountFromArgs) ||
                            (catName.isNotBlank() && cl.contains(catName, ignoreCase = true)) ||
                            (noteArg.isNotBlank() && cl.contains(noteArg, ignoreCase = true))
                }

                val amount = if (amountFromArgs > 0) {
                    val detectedInClause = matchingClause?.let { LocalToolExecutor.extractAmountFromText(it) }
                    detectedInClause ?: amountFromArgs
                } else {
                    LocalToolExecutor.extractAmountFromText(noteArg)
                        ?: matchingClause?.let { LocalToolExecutor.extractAmountFromText(it) }
                        ?: LocalToolExecutor.extractAmountFromText(userMessage)
                        ?: 0L
                }

                // Trích xuất note từ matchingClause nếu noteArg trống hoặc trùng toàn bộ userMessage
                val sourceForNote = if (noteArg.isNotBlank() && !noteArg.equals(userMessage.trim(), ignoreCase = true)) {
                    noteArg
                } else {
                    matchingClause ?: userMessage
                }
                val note = cleanNote(noteArg, sourceForNote, catName)

                // Tìm danh mục khớp nhất trong DB
                val filteredCats = categories.filter { it.type == type }
                val matchedCat = filteredCats.find { it.name.equals(catName, ignoreCase = true) }
                    ?: filteredCats.find { it.name.contains(catName, ignoreCase = true) || catName.contains(it.name, ignoreCase = true) }
                    ?: LocalToolExecutor.matchBestCategory(catName.ifBlank { note }, type, filteredCats).first
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
                val newNote = if (args.has("new_note")) cleanNote(args.optString("new_note"), userMessage, "") else null

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

    companion object {
        /**
         * Rút gọn và làm sạch ghi chú (note) ngắn gọn, súc tích (2-4 từ),
         * loại bỏ các từ đệm, từ xưng hô, câu dài dòng và số tiền thừa thãi.
         */
        fun cleanNote(rawNote: String, userMessage: String, categoryName: String): String {
            var text = if (rawNote.isNotBlank() &&
                !rawNote.equals("chi tiêu", ignoreCase = true) &&
                !rawNote.equals("thu nhập", ignoreCase = true) &&
                !rawNote.equals(userMessage.trim(), ignoreCase = true)
            ) {
                rawNote.trim()
            } else {
                userMessage.trim()
            }

            // 1. Loại bỏ các từ biểu thị số tiền và đơn vị tiền tệ (VD: "hết 45k", "mất 50.000", "2tr", "45k", "1 triệu")
            // Sắp xếp đơn vị dài trước đơn vị ngắn để không khớp nhầm ("triệu" trước "tr", "nghìn" trước "ng")
            // Dùng (?![a-zA-ZÀ-ỹ0-9]) để không ăn lẹm vào chữ cái tiếp theo (VD: "1 tr" trong "1 triệu" gây thừa chữ "iệu")
            val currencyRegex = Regex(
                """(?i)(hết|mất|tốn|khoảng|tầm|chi|thu)?\s*([0-9]+(?:\s*tr\s*[0-9]+|[.,][0-9]+)?)\s*(triệu|trieu|nghìn|nghin|ngàn|ngan|cành|đồng|vnd|củ|lít|lit|tr|k|m|đ)?(?![a-zA-ZÀ-ỹ0-9])"""
            )
            text = text.replace(currencyRegex, " ").trim()
            text = text.replace(Regex("([0-9]{1,3}(?:[.,][0-9]{3})+|[0-9]+)"), " ").trim()

            // 2. Loại bỏ các cụm từ đệm xưng hô / thời gian mở đầu câu
            val prefixRegex = Regex("(?i)^(hôm nay mình|hôm nay|bữa nay mình|bữa nay|ngày nay|nay mình|nay|trưa nay mình|trưa nay|sáng nay mình|sáng nay|tối nay mình|tối nay|chiều nay mình|chiều nay|vừa mới|vừa|mới|mình vừa|mình mới|mình|em vừa|em mới|em|anh vừa|anh mới|anh|tôi vừa|tôi mới|tôi|tớ vừa|tớ mới|tớ|đi|hãy ghi|ghi chép|ghi hộ|ghi cho|thêm|tạo khoản|khoản|tiền)\\s+")
            var prev = ""
            while (prev != text) {
                prev = text
                text = text.replace(prefixRegex, "").trim()
            }

            // 3. Loại bỏ các từ đệm kết thúc câu
            val suffixRegex = Regex("(?i)\\s+(xong|rồi|nhé|nhá|ạ|nha|nhen|đấy|hộ mình|giùm mình|giùm em|nha bạn|nha bot|với)$")
            prev = ""
            while (prev != text) {
                prev = text
                text = text.replace(suffixRegex, "").trim()
            }

            // 4. Loại bỏ các từ nối và dấu câu ở đầu/cuối
            text = text.replace(Regex("^(?:và|với|rồi|kèm theo|sau đó|tiếp|tiếp theo)\\s+", RegexOption.IGNORE_CASE), "").trim()
            text = text.replace(Regex("\\s+"), " ")
            text = text.replace(Regex("^[\\s,.-]+"), "").replace(Regex("[\\s,.-]+$"), "").trim()

            // 5. Nếu sau khi rút gọn quá ngắn hoặc rỗng, dùng tên danh mục
            if (text.isBlank() || text.length < 2) {
                return categoryName.ifBlank { "Chi tiêu" }
            }

            // 6. Viết hoa chữ cái đầu và giới hạn tối đa 40 ký tự
            return text.take(40).replaceFirstChar { it.uppercase() }
        }
    }
}
