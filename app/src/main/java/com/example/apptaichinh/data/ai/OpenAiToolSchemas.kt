package com.example.apptaichinh.data.ai

import com.example.apptaichinh.data.model.Category
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object OpenAiToolSchemas {

    fun buildSystemPrompt(categories: List<Category>): String {
        val expenseCats = categories.filter { it.type == "EXPENSE" }.joinToString(", ") { "${it.icon} ${it.name}" }
        val incomeCats = categories.filter { it.type == "INCOME" }.joinToString(", ") { "${it.icon} ${it.name}" }
        val currentDate = SimpleDateFormat("EEEE, dd/MM/yyyy HH:mm", Locale("vi", "VN")).format(Date())

        return """
            Bạn là trợ lý tài chính cá nhân thông minh trong ứng dụng sổ tay chi tiêu.
            Thời gian hiện tại: $currentDate.
            
            Danh sách danh mục CHI TIÊU có sẵn trong ứng dụng:
            $expenseCats
            
            Danh sách danh mục THU NHẬP có sẵn trong ứng dụng:
            $incomeCats
            
            NHIỆM VỤ CỦA BẠN:
            Phân tích câu nói tiếng Việt của người dùng và gọi MỘT TRONG BA CÔNG CỤ (tools) phù hợp:
            1. create_transaction: Khi người dùng muốn ghi chép một khoản chi tiêu hoặc thu nhập mới.
               - Hãy chọn tên danh mục chính xác nhất trong danh sách trên.
               - Xác định đúng type: 'EXPENSE' (chi tiêu) hoặc 'INCOME' (thu nhập).
               - Quy đổi số tiền sang số nguyên VNĐ (VD: 45k -> 45000, 2tr -> 2000000, 1.5 triệu -> 1500000).
            2. update_transaction: Khi người dùng muốn sửa/chỉnh lại một giao dịch cũ (VD: 'Đổi tiền ăn phở thành 50k', 'Sửa khoản đổ xăng thành 90k').
            3. delete_transaction: Khi người dùng muốn xóa bỏ một giao dịch đã nhập (VD: 'Xóa khoản ăn phở 45k', 'Xóa tiền cafe sáng nay').
            
            Nếu câu nói của người dùng là chào hỏi thông thường hoặc không liên quan đến thu chi, hãy trả lời ngắn gọn, thân thiện bằng văn bản và không gọi tool nào.
        """.trimIndent()
    }

    fun getToolsJsonArray(): JSONArray {
        val tools = JSONArray()

        // 1. Tool create_transaction
        val createTool = JSONObject().apply {
            put("type", "function")
            put("function", JSONObject().apply {
                put("name", "create_transaction")
                put("description", "Ghi chép một khoản chi tiêu hoặc thu nhập mới vào sổ tài chính")
                put("parameters", JSONObject().apply {
                    put("type", "object")
                    val props = JSONObject().apply {
                        put("amount", JSONObject().apply {
                            put("type", "integer")
                            put("description", "Số tiền bằng số nguyên VNĐ (VD: 45000, 2000000)")
                        })
                        put("type", JSONObject().apply {
                            put("type", "string")
                            put("enum", JSONArray().put("EXPENSE").put("INCOME"))
                            put("description", "Loại: EXPENSE (Chi tiêu) hoặc INCOME (Thu nhập)")
                        })
                        put("category_name", JSONObject().apply {
                            put("type", "string")
                            put("description", "Tên danh mục phù hợp nhất trong danh mục có sẵn của app")
                        })
                        put("note", JSONObject().apply {
                            put("type", "string")
                            put("description", "Ghi chú tóm tắt nội dung giao dịch (VD: Ăn phở bò, Đổ xăng)")
                        })
                    }
                    put("properties", props)
                    put("required", JSONArray().put("amount").put("type").put("category_name").put("note"))
                })
            })
        }
        tools.put(createTool)

        // 2. Tool update_transaction
        val updateTool = JSONObject().apply {
            put("type", "function")
            put("function", JSONObject().apply {
                put("name", "update_transaction")
                put("description", "Chỉnh sửa số tiền, danh mục hoặc ghi chú của một giao dịch đã ghi chép")
                put("parameters", JSONObject().apply {
                    put("type", "object")
                    val props = JSONObject().apply {
                        put("search_keyword", JSONObject().apply {
                            put("type", "string")
                            put("description", "Từ khóa để tìm giao dịch cần sửa (VD: 'ăn phở', 'xăng')")
                        })
                        put("old_amount", JSONObject().apply {
                            put("type", "integer")
                            put("description", "Số tiền cũ nếu người dùng có nhắc đến (VD: 45000)")
                        })
                        put("new_amount", JSONObject().apply {
                            put("type", "integer")
                            put("description", "Số tiền mới cần sửa thành (VD: 50000)")
                        })
                        put("new_category_name", JSONObject().apply {
                            put("type", "string")
                            put("description", "Tên danh mục mới nếu cần đổi danh mục")
                        })
                        put("new_note", JSONObject().apply {
                            put("type", "string")
                            put("description", "Ghi chú mới nếu người dùng muốn đổi ghi chú")
                        })
                    }
                    put("properties", props)
                    put("required", JSONArray().put("search_keyword"))
                })
            })
        }
        tools.put(updateTool)

        // 3. Tool delete_transaction
        val deleteTool = JSONObject().apply {
            put("type", "function")
            put("function", JSONObject().apply {
                put("name", "delete_transaction")
                put("description", "Xóa bỏ một giao dịch đã ghi chép trong sổ")
                put("parameters", JSONObject().apply {
                    put("type", "object")
                    val props = JSONObject().apply {
                        put("search_keyword", JSONObject().apply {
                            put("type", "string")
                            put("description", "Từ khóa để tìm kiếm giao dịch cần xóa (VD: 'ăn phở', 'xăng', 'tiền điện')")
                        })
                        put("amount", JSONObject().apply {
                            put("type", "integer")
                            put("description", "Số tiền của giao dịch cần xóa (VD: 45000)")
                        })
                    }
                    put("properties", props)
                    put("required", JSONArray().put("search_keyword"))
                })
            })
        }
        tools.put(deleteTool)

        return tools
    }
}
