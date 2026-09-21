package com.example.apptaichinh.data.ai

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

class AiService(private val dbHelper: FinanceDatabaseHelper) {

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
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    Result.success("Kết nối thành công tới LM Studio!")
                } else {
                    Result.failure(IOException("Mã phản hồi từ máy chủ: ${response.code} (${response.message})"))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

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
                return@withContext AiResponse.Error("Vui lòng cài đặt URL Cloudflare Tunnel hoặc LM Studio IP trước khi sử dụng.")
            }

            val endpoint = "$cleanUrl/v1/chat/completions"

            // Xây dựng JSON Messages
            val messagesArray = JSONArray()

            // 1. System message kèm danh mục hiện tại của máy
            val systemMsg = JSONObject().apply {
                put("role", "system")
                put("content", OpenAiToolSchemas.buildSystemPrompt(categories))
            }
            messagesArray.put(systemMsg)

            // 2. Lấy tối đa 4 tin nhắn gần nhất trong lịch sử hội thoại để giữ ngữ cảnh ngắn gọn
            val recentHistory = conversationHistory.takeLast(4)
            for (msg in recentHistory) {
                if (msg.sender == MessageSender.SYSTEM) continue
                val role = if (msg.sender == MessageSender.USER) "user" else "assistant"
                messagesArray.put(JSONObject().apply {
                    put("role", role)
                    put("content", msg.text)
                })
            }

            // 3. Tin nhắn hiện tại của người dùng
            messagesArray.put(JSONObject().apply {
                put("role", "user")
                put("content", userMessage)
            })

            // Đóng gói Payload OpenAI
            val payload = JSONObject().apply {
                put("model", modelName.ifBlank { "qwen2.5-3b-instruct" })
                put("messages", messagesArray)
                put("tools", OpenAiToolSchemas.getToolsJsonArray())
                put("tool_choice", "auto")
                put("temperature", 0.1) // Nhiệt độ thấp giúp trích xuất chuẩn xác
            }

            val body = payload.toString().toRequestBody(jsonMediaType)
            val request = Request.Builder()
                .url(endpoint)
                .post(body)
                .build()

            client.newCall(request).execute().use { response ->
                val responseStr = response.body?.string() ?: ""
                if (!response.isSuccessful) {
                    return@withContext AiResponse.Error("Lỗi kết nối LM Studio (Mã ${response.code}): $responseStr")
                }

                val jsonResponse = JSONObject(responseStr)
                val choices = jsonResponse.optJSONArray("choices")
                if (choices == null || choices.length() == 0) {
                    return@withContext AiResponse.Error("LM Studio không trả về lựa chọn nào.")
                }

                val messageObj = choices.getJSONObject(0).getJSONObject("message")

                // Kiểm tra xem LLM có gọi Tool không
                val toolCalls = messageObj.optJSONArray("tool_calls")
                if (toolCalls != null && toolCalls.length() > 0) {
                    val firstTool = toolCalls.getJSONObject(0)
                    val functionObj = firstTool.getJSONObject("function")
                    val functionName = functionObj.getString("name")
                    val argumentsStr = functionObj.getString("arguments")
                    val argsJson = JSONObject(argumentsStr)

                    val textExplanation = messageObj.optString("content", "")

                    val toolAction = processToolCall(functionName, argsJson, categories)
                    return@withContext AiResponse.ToolCallReply(toolAction, textExplanation)
                }

                // Nếu là tin nhắn phản hồi dạng text thông thường
                val textContent = messageObj.optString("content", "Xin lỗi, mình chưa hiểu ý bạn.")
                return@withContext AiResponse.TextReply(textContent)
            }
        } catch (e: Exception) {
            AiResponse.Error("Không thể kết nối đến máy chủ AI: ${e.localizedMessage ?: e.message}")
        }
    }

    private fun processToolCall(
        functionName: String,
        args: JSONObject,
        categories: List<Category>
    ): ToolAction {
        return when (functionName) {
            "create_transaction" -> {
                val amount = args.optLong("amount", 0L)
                val type = args.optString("type", "EXPENSE")
                val catName = args.optString("category_name", "")
                val note = args.optString("note", "")

                // Tìm danh mục khớp nhất trong DB
                val filteredCats = categories.filter { it.type == type }
                val matchedCat = filteredCats.find { it.name.equals(catName, ignoreCase = true) }
                    ?: filteredCats.find { it.name.contains(catName, ignoreCase = true) || catName.contains(it.name, ignoreCase = true) }
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

                // Tìm giao dịch cũ khớp trong DB
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

                // Tìm giao dịch khớp để yêu cầu người dùng xác nhận xóa
                val foundList = dbHelper.searchTransactions(searchKeyword, amount)
                val targetTx = foundList.firstOrNull()

                ToolAction(
                    type = ToolActionType.DELETE,
                    targetTransaction = targetTx,
                    amount = amount ?: targetTx?.amount ?: 0L,
                    searchKeyword = searchKeyword
                )
            }

            else -> {
                ToolAction(type = ToolActionType.CREATE, note = "Hành động không xác định: $functionName")
            }
        }
    }
}
