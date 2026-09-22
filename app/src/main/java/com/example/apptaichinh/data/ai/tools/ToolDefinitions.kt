package com.example.apptaichinh.data.ai.tools

import org.json.JSONArray
import org.json.JSONObject

/**
 * Quản lý định nghĩa JSON Schemas cho toàn bộ 16 Tools (Function Calling) theo chuẩn OpenAI.
 * Tách biệt hoàn toàn thành một file chuyên biệt dễ bảo trì và mở rộng.
 */
object ToolDefinitions {

    fun getAllTools(): JSONArray {
        val tools = JSONArray()

        // ==========================================
        // NHÓM 1: CÔNG CỤ TRUY VẤN DỮ LIỆU NỘI BỘ (QUERY TOOLS)
        // ==========================================

        tools.put(createTool(
            name = "query_balance_summary",
            description = "Tra cứu tổng thu nhập, tổng chi tiêu, số dư và ngân sách tổng thể của một tháng bất kỳ. Dùng khi người dùng hỏi về tổng quan tài chính (vd: 'tháng này tiêu bao nhiêu', 'số dư còn bao nhiêu').",
            properties = JSONObject().apply {
                put("month_offset", JSONObject().apply {
                    put("type", "integer")
                    put("description", "0 = tháng hiện tại, -1 = tháng trước, 1 = tháng sau (mặc định 0)")
                })
            },
            required = emptyList()
        ))

        tools.put(createTool(
            name = "query_category_budget",
            description = "Tra cứu hạn mức ngân sách, số tiền đã chi tiêu và số tiền còn lại của một danh mục cụ thể hoặc toàn bộ danh mục. Dùng khi hỏi 'danh mục Ăn uống còn bao nhiêu tiền' hoặc 'đã vượt hạn mức chưa'.",
            properties = JSONObject().apply {
                put("category_name", JSONObject().apply {
                    put("type", "string")
                    put("description", "Tên danh mục cần tra cứu (VD: 'Ăn uống', 'Đi lại', 'Mua sắm', hoặc để trống để xem tất cả)")
                })
                put("month_offset", JSONObject().apply {
                    put("type", "integer")
                    put("description", "0 = tháng hiện tại, -1 = tháng trước (mặc định 0)")
                })
            },
            required = emptyList()
        ))

        tools.put(createTool(
            name = "find_transactions",
            description = "Tìm kiếm các giao dịch gần đây trong lịch sử. Dùng khi người dùng muốn tra cứu giao dịch cũ (vd: 'tìm các khoản ăn phở', 'khoản tiền điện tháng trước').",
            properties = JSONObject().apply {
                put("keyword", JSONObject().apply {
                    put("type", "string")
                    put("description", "Từ khóa tìm kiếm (VD: 'phở', 'xăng', 'tiền điện', 'lương')")
                })
                put("type", JSONObject().apply {
                    put("type", "string")
                    put("enum", JSONArray().put("ALL").put("EXPENSE").put("INCOME"))
                    put("description", "Phân loại giao dịch cần tìm: ALL, EXPENSE, hoặc INCOME")
                })
                put("limit", JSONObject().apply {
                    put("type", "integer")
                    put("description", "Số lượng kết quả trả về tối đa (mặc định 5)")
                })
            },
            required = emptyList()
        ))

        tools.put(createTool(
            name = "query_categories",
            description = "Tra cứu và đối chiếu câu nói của người dùng với danh sách danh mục có sẵn trong Database để chọn ra danh mục chuẩn xác nhất (Tránh ảo giác tạo danh mục trùng lặp). BẮT BUỘC gọi tool này trước khi gọi 'create_transaction'.",
            properties = JSONObject().apply {
                put("type", JSONObject().apply {
                    put("type", "string")
                    put("enum", JSONArray().put("EXPENSE").put("INCOME"))
                    put("description", "EXPENSE (chi tiêu) hoặc INCOME (thu nhập)")
                })
                put("user_text", JSONObject().apply {
                    put("type", "string")
                    put("description", "Nguyên văn câu nói hoặc mô tả chi tiết của người dùng để hệ thống đối chiếu ngữ nghĩa (VD: 'ăn bát phở 45k')")
                })
            },
            required = listOf("type", "user_text")
        ))

        tools.put(createTool(
            name = "query_daily_summary",
            description = "Tra cứu tổng thu chi và số dư chỉ tính trong 1 ngày cụ thể. Dùng khi người dùng hỏi 'hôm nay tôi tiêu bao nhiêu', 'hôm qua tiêu gì'.",
            properties = JSONObject().apply {
                put("date_offset", JSONObject().apply {
                    put("type", "integer")
                    put("description", "0 = hôm nay, -1 = hôm qua, -2 = 2 ngày trước (mặc định 0)")
                })
            },
            required = emptyList()
        ))

        tools.put(createTool(
            name = "query_top_expenses",
            description = "Lấy danh sách các khoản chi tiêu lớn nhất trong tháng hoặc tuần. Dùng khi hỏi 'tôi tiêu nhiều nhất vào đâu', 'khoản nào tốn nhất'.",
            properties = JSONObject().apply {
                put("limit", JSONObject().apply {
                    put("type", "integer")
                    put("description", "Số lượng khoản chi muốn xem (mặc định 5, tối đa 10)")
                })
                put("month_offset", JSONObject().apply {
                    put("type", "integer")
                    put("description", "0 = tháng này, -1 = tháng trước (mặc định 0)")
                })
            },
            required = emptyList()
        ))

        tools.put(createTool(
            name = "query_spending_trend",
            description = "Phân tích xu hướng chi tiêu so với tháng trước (Tăng/Giảm bao nhiêu, thay đổi ở danh mục nào). Dùng khi hỏi 'tháng này tiêu nhiều hơn tháng trước không'.",
            properties = JSONObject().apply {
                put("compare_months", JSONObject().apply {
                    put("type", "integer")
                    put("description", "Số tháng lùi về để so sánh (mặc định 1 = so với tháng trước)")
                })
            },
            required = emptyList()
        ))

        // ==========================================
        // NHÓM 2: CÔNG CỤ QUẢN LÝ GIAO DỊCH (TRANSACTION ACTION TOOLS)
        // ==========================================

        tools.put(createTool(
            name = "create_transaction",
            description = "Soạn thẻ Xem Trước (Preview Card) để ghi nhận một giao dịch Thu/Chi mới. BẮT BUỘC gọi 'query_categories' trước khi gọi tool này để lấy tên danh mục chuẩn xác.",
            properties = JSONObject().apply {
                put("amount", JSONObject().apply {
                    put("type", "integer")
                    put("description", "Số tiền (bắt buộc phải quy đổi ra số nguyên VNĐ, vd: 1 triệu = 1000000)")
                })
                put("type", JSONObject().apply {
                    put("type", "string")
                    put("enum", JSONArray().put("EXPENSE").put("INCOME"))
                    put("description", "Loại: EXPENSE (Chi tiêu) hoặc INCOME (Thu nhập)")
                })
                put("category_name", JSONObject().apply {
                    put("type", "string")
                    put("description", "Tên danh mục do 'query_categories' trả về")
                })
                put("note", JSONObject().apply {
                    put("type", "string")
                    put("description", "Ghi chú siêu ngắn (2-4 từ, vd: 'Ăn phở', 'Đổ xăng'). CẤM sao chép nguyên cả câu dài.")
                })
            },
            required = listOf("amount", "type", "category_name", "note")
        ))

        tools.put(createTool(
            name = "update_transaction",
            description = "Soạn thẻ Xem Trước để sửa chữa giao dịch đã tồn tại. Dùng khi người dùng yêu cầu sửa số tiền, đổi danh mục hoặc đổi ghi chú của 1 giao dịch vừa tạo hoặc trong quá khứ.",
            properties = JSONObject().apply {
                put("search_keyword", JSONObject().apply {
                    put("type", "string")
                    put("description", "Từ khóa tìm kiếm giao dịch cũ cần sửa (VD: 'ăn phở', 'xăng')")
                })
                put("old_amount", JSONObject().apply {
                    put("type", "integer")
                    put("description", "Số tiền cũ nếu có nhắc đến (giúp tìm kiếm chính xác hơn)")
                })
                put("new_amount", JSONObject().apply {
                    put("type", "integer")
                    put("description", "Số tiền mới cần cập nhật (nếu không đổi thì không truyền)")
                })
                put("new_category_name", JSONObject().apply {
                    put("type", "string")
                    put("description", "Tên danh mục mới cần cập nhật (nếu không đổi thì không truyền)")
                })
                put("new_note", JSONObject().apply {
                    put("type", "string")
                    put("description", "Ghi chú mới cần cập nhật (nếu không đổi thì không truyền)")
                })
            },
            required = listOf("search_keyword")
        ))

        tools.put(createTool(
            name = "delete_transaction",
            description = "Soạn thẻ Xem Trước để xóa bỏ một giao dịch đã tồn tại. Dùng khi người dùng bảo 'xóa khoản phở đi', 'hủy khoản xăng hôm qua'.",
            properties = JSONObject().apply {
                put("search_keyword", JSONObject().apply {
                    put("type", "string")
                    put("description", "Từ khóa để tìm kiếm giao dịch cần xóa (VD: 'ăn phở', 'xăng')")
                })
                put("amount", JSONObject().apply {
                    put("type", "integer")
                    put("description", "Số tiền của khoản cần xóa (nếu có)")
                })
            },
            required = listOf("search_keyword")
        ))

        // ==========================================
        // NHÓM 3: CÔNG CỤ QUẢN LÝ DANH MỤC (CATEGORY ACTION TOOLS)
        // ==========================================

        tools.put(createTool(
            name = "create_category",
            description = "Soạn thẻ Xem Trước để tạo một danh mục hoàn toàn mới. CHỈ SỬ DỤNG khi khoản thu/chi thực sự quá khác biệt và hệ thống chưa có danh mục nào phù hợp, HOẶC người dùng yêu cầu rõ ràng.",
            properties = JSONObject().apply {
                put("name", JSONObject().apply {
                    put("type", "string")
                    put("description", "Tên danh mục mới (VD: 'Nuôi mèo', 'Từ thiện', 'Tiền tiêu vặt')")
                })
                put("type", JSONObject().apply {
                    put("type", "string")
                    put("enum", JSONArray().put("EXPENSE").put("INCOME"))
                    put("description", "EXPENSE hoặc INCOME")
                })
                put("icon", JSONObject().apply {
                    put("type", "string")
                    put("description", "Emoji phù hợp đại diện cho danh mục (VD: '🐱', '🎗️', '💰')")
                })
                put("budget", JSONObject().apply {
                    put("type", "integer")
                    put("description", "Hạn mức ngân sách nếu có (mặc định 0)")
                })
            },
            required = listOf("name", "type", "icon")
        ))

        tools.put(createTool(
            name = "update_category",
            description = "Soạn thẻ Xem Trước để sửa tên hoặc biểu tượng (icon) của một danh mục ĐÃ CÓ. Dùng khi người dùng muốn 'đổi tên danh mục X thành Y' hoặc 'đổi icon danh mục X'.",
            properties = JSONObject().apply {
                put("search_keyword", JSONObject().apply {
                    put("type", "string")
                    put("description", "Tên danh mục hiện tại cần sửa (VD: 'Ăn uống')")
                })
                put("new_name", JSONObject().apply {
                    put("type", "string")
                    put("description", "Tên mới (nếu muốn đổi tên)")
                })
                put("new_icon", JSONObject().apply {
                    put("type", "string")
                    put("description", "Emoji biểu tượng mới (nếu muốn đổi icon)")
                })
            },
            required = listOf("search_keyword")
        ))

        tools.put(createTool(
            name = "delete_category",
            description = "Soạn thẻ Xem Trước để xóa một danh mục ĐÃ CÓ. Chỉ gọi khi người dùng yêu cầu rõ ràng (vd: 'xóa danh mục Nuôi mèo đi').",
            properties = JSONObject().apply {
                put("search_keyword", JSONObject().apply {
                    put("type", "string")
                    put("description", "Tên danh mục cần xóa (VD: 'Nuôi mèo', 'Ăn vặt')")
                })
            },
            required = listOf("search_keyword")
        ))

        // ==========================================
        // NHÓM 4: CÔNG CỤ QUẢN LÝ NGÂN SÁCH (BUDGET ACTION TOOLS)
        // ==========================================

        tools.put(createTool(
            name = "set_overall_budget",
            description = "Soạn thẻ Xem Trước để thiết lập ngân sách tổng thể của tháng. Dùng khi người dùng bảo 'tháng này giới hạn chi tiêu 10 triệu', 'đặt ngân sách tổng 20tr'.",
            properties = JSONObject().apply {
                put("amount", JSONObject().apply {
                    put("type", "integer")
                    put("description", "Hạn mức tổng mới bằng VNĐ (VD: 10 triệu -> 10000000)")
                })
            },
            required = listOf("amount")
        ))

        tools.put(createTool(
            name = "set_category_budget",
            description = "Soạn thẻ Xem Trước để thiết lập ngân sách cho một danh mục cụ thể. Dùng khi nói 'đặt hạn mức Ăn uống 3 triệu', 'giới hạn mua sắm 500k'.",
            properties = JSONObject().apply {
                put("category_name", JSONObject().apply {
                    put("type", "string")
                    put("description", "Tên danh mục cần áp dụng hạn mức (VD: 'Ăn uống', 'Mua sắm')")
                })
                put("amount", JSONObject().apply {
                    put("type", "integer")
                    put("description", "Hạn mức mới bằng VNĐ (VD: 3 triệu -> 3000000)")
                })
            },
            required = listOf("category_name", "amount")
        ))

        tools.put(createTool(
            name = "transfer_category",
            description = "Soạn thẻ Xem Trước để luân chuyển/chuyển đổi một giao dịch từ danh mục này sang danh mục khác. Dùng khi nói 'chuyển khoản phở vào danh mục giải trí'.",
            properties = JSONObject().apply {
                put("search_keyword", JSONObject().apply {
                    put("type", "string")
                    put("description", "Từ khóa tìm khoản giao dịch cần chuyển (VD: 'phở')")
                })
                put("target_category_name", JSONObject().apply {
                    put("type", "string")
                    put("description", "Tên danh mục ĐÍCH mà giao dịch sẽ được chuyển tới (VD: 'Giải trí')")
                })
                put("amount", JSONObject().apply {
                    put("type", "integer")
                    put("description", "Số tiền của giao dịch (nếu có)")
                })
            },
            required = listOf("search_keyword", "target_category_name")
        ))

        return tools
    }

    private fun createTool(
        name: String,
        description: String,
        properties: JSONObject,
        required: List<String>
    ): JSONObject {
        return JSONObject().apply {
            put("type", "function")
            put("function", JSONObject().apply {
                put("name", name)
                put("description", description)
                put("parameters", JSONObject().apply {
                    put("type", "object")
                    put("properties", properties)
                    if (required.isNotEmpty()) {
                        val reqArr = JSONArray()
                        required.forEach { reqArr.put(it) }
                        put("required", reqArr)
                    }
                })
            })
        }
    }
}
