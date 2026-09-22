package com.example.apptaichinh.data.ai.tools

import org.json.JSONArray
import org.json.JSONObject

/**
 * Quản lý định nghĩa JSON Schemas cho toàn bộ các Tools (Function Calling) theo chuẩn OpenAI.
 * Tách biệt hoàn toàn thành một file chuyên biệt dễ bảo trì và mở rộng.
 */
object ToolDefinitions {

    fun getAllTools(): JSONArray {
        val tools = JSONArray()

        // ==========================================
        // NHÓM 1: CÔNG CỤ TRUY VẤN DỮ LIỆU NỘI BỘ (QUERY TOOLS)
        // ==========================================

        // 1. query_balance_summary: Tra cứu tổng thu, tổng chi, số dư & ngân sách tổng
        tools.put(createTool(
            name = "query_balance_summary",
            description = "Tra cứu số dư hiện tại, tổng thu nhập, tổng chi tiêu và ngân sách tổng thể của tháng",
            properties = JSONObject().apply {
                put("month_offset", JSONObject().apply {
                    put("type", "integer")
                    put("description", "0 là tháng hiện tại, -1 là tháng trước, 1 là tháng sau (mặc định 0)")
                })
            },
            required = emptyList()
        ))

        // 2. query_category_budget: Tra cứu tình hình ngân sách của danh mục (đã chi, hạn mức, còn lại)
        tools.put(createTool(
            name = "query_category_budget",
            description = "Tra cứu hạn mức ngân sách, số tiền đã chi tiêu và số tiền còn lại của một danh mục cụ thể (hoặc toàn bộ danh mục nếu để trống)",
            properties = JSONObject().apply {
                put("category_name", JSONObject().apply {
                    put("type", "string")
                    put("description", "Tên danh mục cần tra cứu (VD: 'Ăn uống', 'Đi lại', 'Mua sắm', hoặc để trống để xem tất cả)")
                })
                put("month_offset", JSONObject().apply {
                    put("type", "integer")
                    put("description", "0 là tháng hiện tại, -1 là tháng trước, 1 là tháng sau (mặc định 0)")
                })
            },
            required = emptyList()
        ))

        // 3. find_transactions: Tìm kiếm các giao dịch gần đây trong sổ
        tools.put(createTool(
            name = "find_transactions",
            description = "Tìm kiếm các khoản giao dịch gần nhất theo từ khóa ghi chú, tên danh mục hoặc phân loại Thu/Chi",
            properties = JSONObject().apply {
                put("keyword", JSONObject().apply {
                    put("type", "string")
                    put("description", "Từ khóa tìm kiếm (VD: 'phở', 'xăng', 'tiền điện', 'lương')")
                })
                put("type", JSONObject().apply {
                    put("type", "string")
                    put("enum", JSONArray().put("ALL").put("EXPENSE").put("INCOME"))
                    put("description", "Phân loại: ALL (tất cả), EXPENSE (chi tiêu), INCOME (thu nhập)")
                })
                put("limit", JSONObject().apply {
                    put("type", "integer")
                    put("description", "Số lượng giao dịch tối đa cần lấy (mặc định 5)")
                })
            },
            required = emptyList()
        ))

        // 4. query_categories: Tra cứu và đối chiếu toàn văn câu nói của người dùng với các danh mục có sẵn
        tools.put(createTool(
            name = "query_categories",
            description = "Tra cứu và đối chiếu toàn văn câu nói của người dùng với các danh mục có sẵn của bên Thu (INCOME) hoặc bên Chi (EXPENSE) để chọn danh mục phù hợp nhất.",
            properties = JSONObject().apply {
                put("type", JSONObject().apply {
                    put("type", "string")
                    put("enum", JSONArray().put("EXPENSE").put("INCOME"))
                    put("description", "Bắt buộc: 'EXPENSE' cho chi tiêu hoặc 'INCOME' cho thu nhập")
                })
                put("user_text", JSONObject().apply {
                    put("type", "string")
                    put("description", "Toàn bộ câu nói hoặc mô tả của người dùng (VD: 'Ăn bát phở bò 45k', 'Vừa nhận lương công ty 15 triệu') để đối chiếu với các danh mục")
                })
            },
            required = listOf("type", "user_text")
        ))

        // ==========================================
        // NHÓM 2: CÔNG CỤ ĐỀ XUẤT HÀNH ĐỘNG (ACTION TOOLS - CẦN PREVIEW CARD)
        // ==========================================

        // 4. create_transaction: Soạn phiếu thêm mới thu/chi
        tools.put(createTool(
            name = "create_transaction",
            description = "Soạn phiếu ghi chép một khoản chi tiêu hoặc thu nhập mới vào sổ tài chính",
            properties = JSONObject().apply {
                put("amount", JSONObject().apply {
                    put("type", "integer")
                    put("description", "Số tiền bằng số nguyên VNĐ (VD: 45000, 2000000)")
                })
                put("type", JSONObject().apply {
                    put("type", "string")
                    put("enum", JSONArray().put("EXPENSE").put("INCOME"))
                    put("description", "Loại giao dịch: EXPENSE (Chi tiêu) hoặc INCOME (Thu nhập)")
                })
                put("category_name", JSONObject().apply {
                    put("type", "string")
                    put("description", "Tên danh mục phù hợp nhất trong danh sách danh mục có sẵn của app")
                })
                put("note", JSONObject().apply {
                    put("type", "string")
                    put("description", "Ghi chú tóm tắt nội dung ngắn gọn 2-4 từ (VD: Ăn phở bò, Đổ xăng xe). CẤM sao chép nguyên cả câu nói dài của người dùng.")
                })
            },
            required = listOf("amount", "type", "category_name", "note")
        ))

        // 5. update_transaction: Soạn phiếu sửa đổi giao dịch
        tools.put(createTool(
            name = "update_transaction",
            description = "Soạn phiếu chỉnh sửa số tiền, danh mục hoặc ghi chú của một giao dịch đã ghi chép",
            properties = JSONObject().apply {
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
            },
            required = listOf("search_keyword")
        ))

        // 6. delete_transaction: Soạn phiếu xóa giao dịch
        tools.put(createTool(
            name = "delete_transaction",
            description = "Soạn phiếu xóa bỏ một giao dịch đã ghi chép trong sổ",
            properties = JSONObject().apply {
                put("search_keyword", JSONObject().apply {
                    put("type", "string")
                    put("description", "Từ khóa để tìm kiếm giao dịch cần xóa (VD: 'ăn phở', 'xăng', 'tiền điện')")
                })
                put("amount", JSONObject().apply {
                    put("type", "integer")
                    put("description", "Số tiền của giao dịch cần xóa nếu có nhắc đến (VD: 45000)")
                })
            },
            required = listOf("search_keyword")
        ))

        // 7. create_category: Soạn phiếu tạo danh mục mới khi quá khác biệt hoặc người dùng yêu cầu
        tools.put(createTool(
            name = "create_category",
            description = "Soạn phiếu tạo một danh mục Thu hoặc Chi mới khi người dùng yêu cầu hoặc khi khoản chi tiêu/thu nhập hoàn toàn mới lạ, quá khác biệt với mọi danh mục hiện có",
            properties = JSONObject().apply {
                put("name", JSONObject().apply {
                    put("type", "string")
                    put("description", "Tên danh mục mới (VD: 'Nuôi thú cưng', 'Làm đẹp', 'Từ thiện', 'Tiền tiêu vặt')")
                })
                put("type", JSONObject().apply {
                    put("type", "string")
                    put("enum", JSONArray().put("EXPENSE").put("INCOME"))
                    put("description", "Loại danh mục: EXPENSE (Chi tiêu) hoặc INCOME (Thu nhập)")
                })
                put("icon", JSONObject().apply {
                    put("type", "string")
                    put("description", "Emoji biểu tượng đại diện phù hợp (VD: '🐱', '💅', '🎗️', '👶', '🏋️')")
                })
                put("budget", JSONObject().apply {
                    put("type", "integer")
                    put("description", "Hạn mức ngân sách dự kiến hàng tháng bằng VNĐ nếu có (mặc định 0)")
                })
            },
            required = listOf("name", "type", "icon")
        ))

        // ==========================================
        // NHÓM 3: QUERY TOOLS MỞ RỘNG (ANALYTICS & TRENDS)
        // ==========================================

        // 8. query_daily_summary: Tổng kết thu chi trong ngày hôm nay
        tools.put(createTool(
            name = "query_daily_summary",
            description = "Tra cứu tổng thu nhập, tổng chi tiêu và số dư chỉ tính trong ngày hôm nay. Dùng khi người dùng hỏi 'hôm nay tôi tiêu bao nhiêu', 'hôm nay thu chi thế nào'.",
            properties = JSONObject().apply {
                put("date_offset", JSONObject().apply {
                    put("type", "integer")
                    put("description", "0 = hôm nay, -1 = hôm qua, -2 = 2 ngày trước (mặc định 0)")
                })
            },
            required = emptyList()
        ))

        // 9. query_top_expenses: Top N khoản chi lớn nhất
        tools.put(createTool(
            name = "query_top_expenses",
            description = "Lấy danh sách N khoản chi tiêu lớn nhất trong tháng hoặc tuần, sắp xếp từ cao đến thấp. Dùng khi người dùng hỏi 'tôi tiêu nhiều nhất vào đâu', 'khoản nào lớn nhất tháng này'.",
            properties = JSONObject().apply {
                put("limit", JSONObject().apply {
                    put("type", "integer")
                    put("description", "Số khoản chi muốn xem (mặc định 5, tối đa 10)")
                })
                put("month_offset", JSONObject().apply {
                    put("type", "integer")
                    put("description", "0 = tháng này, -1 = tháng trước (mặc định 0)")
                })
            },
            required = emptyList()
        ))

        // 10. query_spending_trend: So sánh xu hướng chi tiêu tháng này vs tháng trước
        tools.put(createTool(
            name = "query_spending_trend",
            description = "Phân tích xu hướng chi tiêu: so sánh tổng chi tháng hiện tại với tháng trước, tháng nào chi nhiều hơn, tăng/giảm bao nhiêu phần trăm và danh mục nào thay đổi nhiều nhất.",
            properties = JSONObject().apply {
                put("compare_months", JSONObject().apply {
                    put("type", "integer")
                    put("description", "Số tháng cần so sánh lùi về (mặc định 1 = so với tháng trước)")
                })
            },
            required = emptyList()
        ))

        // ==========================================
        // NHÓM 4: ACTION TOOLS MỞ RỘNG (BUDGET MANAGEMENT)
        // ==========================================

        // 11. set_overall_budget: Đặt hạn mức ngân sách tổng tháng
        tools.put(createTool(
            name = "set_overall_budget",
            description = "Soạn phiếu đặt hoặc thay đổi hạn mức ngân sách chi tiêu tổng thể cho tháng. Dùng khi người dùng nói 'đặt ngân sách tháng này là X triệu', 'sửa hạn mức thành X'.",
            properties = JSONObject().apply {
                put("amount", JSONObject().apply {
                    put("type", "integer")
                    put("description", "Hạn mức ngân sách tổng mới bằng VNĐ (VD: 10000000 cho 10 triệu)")
                })
            },
            required = listOf("amount")
        ))

        // 12. set_category_budget: Đặt hạn mức ngân sách danh mục
        tools.put(createTool(
            name = "set_category_budget",
            description = "Soạn phiếu đặt hoặc thay đổi hạn mức ngân sách cho một danh mục chi tiêu cụ thể. Dùng khi người dùng nói 'đặt hạn mức Ăn uống thành 3 triệu', 'giới hạn Mua sắm X đồng'.",
            properties = JSONObject().apply {
                put("category_name", JSONObject().apply {
                    put("type", "string")
                    put("description", "Tên danh mục cần đặt hạn mức (VD: 'Ăn uống', 'Đi lại', 'Mua sắm')")
                })
                put("amount", JSONObject().apply {
                    put("type", "integer")
                    put("description", "Hạn mức ngân sách mới bằng VNĐ (VD: 3000000 cho 3 triệu)")
                })
            },
            required = listOf("category_name", "amount")
        ))

        // 13. transfer_category: Chuyển giao dịch sang danh mục khác
        tools.put(createTool(
            name = "transfer_category",
            description = "Soạn phiếu chuyển một giao dịch đã ghi chép từ danh mục hiện tại sang một danh mục khác. Dùng khi người dùng nói 'chuyển khoản X sang danh mục Y', 'khoản phở hôm qua bỏ vào Ăn uống đi'.",
            properties = JSONObject().apply {
                put("search_keyword", JSONObject().apply {
                    put("type", "string")
                    put("description", "Từ khóa để tìm giao dịch cần chuyển (VD: 'phở hôm qua', 'xăng tuần trước')")
                })
                put("target_category_name", JSONObject().apply {
                    put("type", "string")
                    put("description", "Tên danh mục đích muốn chuyển vào (VD: 'Ăn uống', 'Giải trí')")
                })
                put("amount", JSONObject().apply {
                    put("type", "integer")
                    put("description", "Số tiền của giao dịch cần chuyển nếu có nhắc đến (để thu hẹp tìm kiếm)")
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
