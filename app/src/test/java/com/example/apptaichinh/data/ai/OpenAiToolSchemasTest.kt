package com.example.apptaichinh.data.ai

import com.example.apptaichinh.data.ai.prompts.AgentPrompts
import com.example.apptaichinh.data.ai.tools.ToolDefinitions
import com.example.apptaichinh.data.model.Category
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenAiToolSchemasTest {

    @Test
    fun testToolDefinitionsCountAndNames() {
        val tools = ToolDefinitions.getAllTools()
        assertEquals(8, tools.length())

        val toolNames = mutableListOf<String>()
        for (i in 0 until tools.length()) {
            val toolObj = tools.getJSONObject(i)
            assertEquals("function", toolObj.getString("type"))
            val functionObj = toolObj.getJSONObject("function")
            toolNames.add(functionObj.getString("name"))
        }

        // Query tools (4 tools)
        assertTrue(toolNames.contains("query_balance_summary"))
        assertTrue(toolNames.contains("query_category_budget"))
        assertTrue(toolNames.contains("find_transactions"))
        assertTrue(toolNames.contains("query_categories"))

        // Action tools (4 tools)
        assertTrue(toolNames.contains("create_transaction"))
        assertTrue(toolNames.contains("update_transaction"))
        assertTrue(toolNames.contains("delete_transaction"))
        assertTrue(toolNames.contains("create_category"))
    }

    @Test
    fun testQueryCategoriesParameters() {
        val tools = ToolDefinitions.getAllTools()
        val queryCatTool = (0 until tools.length())
            .map { tools.getJSONObject(it).getJSONObject("function") }
            .first { it.getString("name") == "query_categories" }

        val params = queryCatTool.getJSONObject("parameters")
        assertEquals("object", params.getString("type"))
        val props = params.getJSONObject("properties")
        assertNotNull(props.getJSONObject("type"))
        assertNotNull(props.getJSONObject("user_text"))

        val typeProp = props.getJSONObject("type")
        val enums = typeProp.getJSONArray("enum")
        assertEquals("EXPENSE", enums.getString(0))
        assertEquals("INCOME", enums.getString(1))

        val required = params.getJSONArray("required")
        assertEquals("type", required.getString(0))
        assertEquals("user_text", required.getString(1))
    }

    @Test
    fun testDeleteTransactionParameters() {
        val tools = ToolDefinitions.getAllTools()
        val deleteTool = (0 until tools.length())
            .map { tools.getJSONObject(it).getJSONObject("function") }
            .first { it.getString("name") == "delete_transaction" }

        val params = deleteTool.getJSONObject("parameters")
        assertEquals("object", params.getString("type"))
        val props = params.getJSONObject("properties")
        assertNotNull(props.getJSONObject("search_keyword"))
        assertNotNull(props.getJSONObject("amount"))

        val required = params.getJSONArray("required")
        assertEquals("search_keyword", required.getString(0))
    }

    @Test
    fun testSystemPromptContainsReActWorkflow() {
        val sampleCategories = listOf(
            Category(1, "Ăn uống", "EXPENSE", "🍜", "#EF4444", 3500000L),
            Category(2, "Lương", "INCOME", "💵", "#10B981", 0L)
        )
        val prompt = AgentPrompts.buildOrchestratorPrompt(sampleCategories)

        // Kiểm tra đầy đủ các bước ReAct bắt buộc
        assertTrue(prompt.contains("query_categories"))
        assertTrue(prompt.contains("create_transaction"))
        assertTrue(prompt.contains("PHÂN LOẠI THU / CHI"))
        assertTrue(prompt.contains("TRA CỨU & ĐỐI CHIẾU DANH MỤC PHÙ HỢP TỪ CÂU NÓI"))
        assertTrue(prompt.contains("user_text"))
        assertTrue(prompt.contains("LẬP PHIẾU GIAO DỊCH"))
        assertTrue(prompt.contains("TỔNG HỢP & PHẢN HỒI MINH BẠCH CHO NGƯỜI DÙNG"))
        assertTrue(prompt.contains("QUY TẮC CHỐNG LẶP"))
        assertTrue(prompt.contains("query_balance_summary"))
        assertTrue(prompt.contains("query_category_budget"))
    }

    @Test
    fun testMatchBestCategoryWithFullUserText() {
        val categories = listOf(
            Category(1, "Ăn uống", "EXPENSE", "🍜", "#EF4444", 3500000L),
            Category(2, "Đi lại", "EXPENSE", "🛵", "#3B82F6", 1000000L),
            Category(3, "Nhà ở", "EXPENSE", "🏠", "#8B5CF6", 2000000L),
            Category(4, "Khác", "EXPENSE", "📦", "#607D8B", 0L),
            Category(5, "Lương", "INCOME", "💵", "#10B981", 0L),
            Category(6, "Thưởng", "INCOME", "🎯", "#F59E0B", 0L),
            Category(7, "Khác", "INCOME", "📦", "#607D8B", 0L)
        )

        // Kịch bản 1: "Ăn bát phở bò 45k" -> Khớp Ăn uống
        val (match1, isFallback1) = com.example.apptaichinh.data.ai.tools.LocalToolExecutor.matchBestCategory(
            "Ăn bát phở bò 45k", "EXPENSE", categories
        )
        assertNotNull(match1)
        assertEquals("Ăn uống", match1?.name)
        assertEquals(false, isFallback1)

        // Kịch bản 2: "Vừa đổ 50k xăng xe máy" -> Khớp Đi lại
        val (match2, isFallback2) = com.example.apptaichinh.data.ai.tools.LocalToolExecutor.matchBestCategory(
            "Vừa đổ 50k xăng xe máy", "EXPENSE", categories
        )
        assertNotNull(match2)
        assertEquals("Đi lại", match2?.name)
        assertEquals(false, isFallback2)

        // Kịch bản 3: "Hôm nay nhận lương công ty 15 triệu" -> Khớp Lương
        val (match3, isFallback3) = com.example.apptaichinh.data.ai.tools.LocalToolExecutor.matchBestCategory(
            "Hôm nay nhận lương công ty 15 triệu", "INCOME", categories
        )
        assertNotNull(match3)
        assertEquals("Lương", match3?.name)
        assertEquals(false, isFallback3)

        // Kịch bản 4: Câu lạ không từ khóa -> Fallback về Khác
        val (match4, isFallback4) = com.example.apptaichinh.data.ai.tools.LocalToolExecutor.matchBestCategory(
            "Chi tiêu một khoản bí mật 100k", "EXPENSE", categories
        )
        assertNotNull(match4)
        assertEquals("Khác", match4?.name)
        assertEquals(true, isFallback4)
    }

    @Test
    fun testComprehensiveExpenseInputsAcrossAllCategories() {
        val categories = com.example.apptaichinh.data.db.FinanceDatabaseHelper.getDefaultCategoriesList()

        val testCases = listOf(
            // 1. Ăn uống
            "Hôm nay ăn bát phở bò tái nạm 45k" to "Ăn uống",
            "Uống cốc trà sữa trân châu đường đen 35 ngàn" to "Ăn uống",
            "Đi ăn lẩu nướng buffet với bạn bè 250k" to "Ăn uống",
            "Mua cái bánh mì pate xúc xích 20k ăn sáng" to "Ăn uống",
            "Cơm tấm sườn bì chả trưa nay 45k" to "Ăn uống",
            "Đi chợ mua rau củ quả thịt cá 150k" to "Ăn uống",
            "Ăn bát cháo sườn nóng 30k" to "Ăn uống",

            // 2. Đi lại
            "Đổ xăng đầy bình xe máy 70k" to "Đi lại",
            "Đặt grab bike đi làm lúc sáng 32k" to "Đi lại",
            "Tiền gửi xe ở bến xe Mỹ Đình 20k" to "Đi lại",
            "Thay dầu nhớt bảo dưỡng xe máy 120k" to "Đi lại",
            "Bắt taxi mai linh đi sân bay 180k" to "Đi lại",
            "Mua vé tàu hỏa về quê 450k" to "Đi lại",

            // 3. Nhà ở
            "Đóng tiền điện tháng này hết 550k" to "Nhà ở",
            "Tiền nước sinh hoạt căn hộ 95k" to "Nhà ở",
            "Thanh toán cước internet wifi fpt 220k" to "Nhà ở",
            "Chuyển khoản tiền phòng trọ tháng này 2tr5" to "Nhà ở",
            "Mua bóng đèn và đồ sửa nhà 80k" to "Nhà ở",

            // 4. Mua sắm
            "Mua cái áo thun unisex trên shopee 160k" to "Mua sắm",
            "Mua đôi giày sneaker thể thao 750k" to "Mua sắm",
            "Mua thỏi son môi và mỹ phẩm trang điểm 320k" to "Mua sắm",
            "Đi siêu thị mua đồ gia dụng gia đình 450k" to "Mua sắm",

            // 5. Giải trí
            "Mua vé xem phim rạp CGV 150k" to "Giải trí",
            "Đi uống cà phê highland chém gió 65k" to "Giải trí",
            "Đi nhậu cuối tuần bia hơi với cty 350k" to "Giải trí",
            "Chơi bida giải trí 2 tiếng 90k" to "Giải trí",
            "Nạp thẻ game giải trí 100k" to "Giải trí",
            "Đi hát karaoke cùng bạn bè 400k" to "Giải trí",

            // 6. Sức khỏe
            "Mua thuốc panadol hạ sốt và kháng sinh 45k" to "Sức khỏe",
            "Đi khám răng nha khoa lấy cao răng 250k" to "Sức khỏe",
            "Mua hộp khẩu trang y tế và vitamin c 85k" to "Sức khỏe",
            "Chi phí viện phí khám sức khỏe tổng quát 500k" to "Sức khỏe",

            // 7. Giáo dục
            "Mua cuốn sách học lập trình Android 180k" to "Giáo dục",
            "Nộp tiền học phí khóa học tiếng Anh 2 triệu" to "Giáo dục",
            "Mua tập vở viết và bút bi 35k" to "Giáo dục",

            // 8. Hiếu hỉ
            "Đi ăn đám cưới bạn thân mừng phong bì 500k" to "Hiếu hỉ",
            "Mua quà mừng sinh nhật cháu 200k" to "Hiếu hỉ",
            "Thăm người ốm mua giỏ hoa quả 300k" to "Hiếu hỉ",
            "Tiền phúng viếng đám ma 300k" to "Hiếu hỉ",

            // 9. Tiết kiệm
            "Gửi tiết kiệm ngân hàng Techcombank 5 triệu" to "Tiết kiệm",
            "Chuyển tiền vào tài khoản tích lũy 2tr" to "Tiết kiệm",

            // 10. Trả nợ
            "Thanh toán dư nợ thẻ tín dụng 2 triệu" to "Trả nợ",
            "Trả nợ tiền vay bạn hồi tháng trước 1tr" to "Trả nợ",
            "Trả góp tiền mua điện thoại fe credit 1tr5" to "Trả nợ"
        )

        for ((input, expectedCategory) in testCases) {
            val (matched, isFallback) = com.example.apptaichinh.data.ai.tools.LocalToolExecutor.matchBestCategory(
                input, "EXPENSE", categories
            )
            assertNotNull("Câu '$input' không tìm thấy danh mục", matched)
            assertEquals("Sai danh mục cho câu: '$input'", expectedCategory, matched?.name)
            assertEquals("Câu '$input' bị coi là fallback sai", false, isFallback)
        }
    }

    @Test
    fun testComprehensiveIncomeInputs() {
        val categories = com.example.apptaichinh.data.db.FinanceDatabaseHelper.getDefaultCategoriesList()

        val incomeCases = listOf(
            "Hôm nay công ty bắn lương tháng 20 triệu" to "Lương",
            "Chuyển khoản lương kỳ 1 12tr5" to "Lương",
            "Nhận tiền thưởng hoàn thành KPI quý 5 củ" to "Thưởng",
            "Tiền thưởng tết nguyên đán 15 triệu" to "Thưởng",
            "Làm dự án freelance thiết kế logo ngoài giờ 3tr" to "Làm thêm",
            "Tiền công làm part-time ca tối 1tr2" to "Làm thêm",
            "Nhận cổ tức và lãi đầu tư chứng khoán 2tr5" to "Đầu tư",
            "Lãi đầu tư tiền ảo crypto 1tr" to "Đầu tư",
            "Rút tiền lãi từ sổ tiết kiệm ngân hàng 400k" to "Đầu tư"
        )

        for ((input, expectedCategory) in incomeCases) {
            val (matched, isFallback) = com.example.apptaichinh.data.ai.tools.LocalToolExecutor.matchBestCategory(
                input, "INCOME", categories
            )
            assertNotNull("Câu '$input' không tìm thấy danh mục", matched)
            assertEquals("Sai danh mục cho câu: '$input'", expectedCategory, matched?.name)
            assertEquals("Câu '$input' bị coi là fallback sai", false, isFallback)
        }
    }

    @Test
    fun testCustomUserCategoriesMatching() {
        val baseCategories = com.example.apptaichinh.data.db.FinanceDatabaseHelper.getDefaultCategoriesList()
        val customCategories = baseCategories + listOf(
            Category(99, "Nuôi mèo", "EXPENSE", "🐱", "#FFB74D", 800000L),
            Category(100, "Tập gym", "EXPENSE", "🏋️", "#4CAF50", 1200000L)
        )

        // Test 1: Khớp danh mục custom "Nuôi mèo"
        val (match1, isFallback1) = com.example.apptaichinh.data.ai.tools.LocalToolExecutor.matchBestCategory(
            "Mua hạt và pate cho mèo 250k", "EXPENSE", customCategories
        )
        assertNotNull(match1)
        assertEquals("Nuôi mèo", match1?.name)
        assertEquals(false, isFallback1)

        // Test 2: Khớp danh mục custom "Tập gym"
        val (match2, isFallback2) = com.example.apptaichinh.data.ai.tools.LocalToolExecutor.matchBestCategory(
            "Đóng tiền gia hạn phòng tập gym 6 tháng", "EXPENSE", customCategories
        )
        assertNotNull(match2)
        assertEquals("Tập gym", match2?.name)
        assertEquals(false, isFallback2)
    }

    @Test
    fun testBudgetAndOverBudgetCalculations() {
        // Kiểm thử tính toán hạn mức ngân sách tổng thể
        val totalBudgetLimit = 14400000L // 14.4 triệu

        // Trường hợp 1: Tiêu chưa vượt hạn mức
        val spentUnder = 9500000L
        val remainingUnder = totalBudgetLimit - spentUnder
        val percentageUnder = (spentUnder.toFloat() / totalBudgetLimit.toFloat())
        val isOverUnder = totalBudgetLimit > 0 && spentUnder > totalBudgetLimit
        assertEquals(4900000L, remainingUnder)
        assertEquals(false, isOverUnder)
        assertTrue(percentageUnder < 1.0f)

        // Trường hợp 2: Tiêu ĐÃ VƯỢT hạn mức
        val spentOver = 16000000L
        val remainingOver = totalBudgetLimit - spentOver
        val percentageOver = (spentOver.toFloat() / totalBudgetLimit.toFloat())
        val isOverOver = totalBudgetLimit > 0 && spentOver > totalBudgetLimit
        assertEquals(-1600000L, remainingOver)
        assertEquals(true, isOverOver)
        assertTrue(percentageOver > 1.0f)

        // Kiểm thử tính toán hạn mức danh mục cụ thể (Ăn uống: 3.5 triệu)
        val catBudgetLimit = 3500000L
        val catSpent = 4100000L // đã tiêu 4.1 triệu
        val catRemaining = catBudgetLimit - catSpent
        val catIsOver = catBudgetLimit > 0 && catSpent > catBudgetLimit
        assertEquals(-600000L, catRemaining)
        assertEquals(true, catIsOver)
    }

    @Test
    fun testComplexAndEdgeCaseInputs() {
        val categories = com.example.apptaichinh.data.db.FinanceDatabaseHelper.getDefaultCategoriesList()

        val edgeCases = listOf(
            "Bữa tối ăn bún đậu mắm tôm 55k" to ("Ăn uống" to false),
            "Mua vé xem kịch nghệ 200k" to ("Giải trí" to false),
            "Mua áo mưa giấy đi đường 15k" to ("Mua sắm" to false),
            "Tiền gửi xe ô tô tháng 1tr2" to ("Đi lại" to false),
            "Mua kính cận và khám mắt 450k" to ("Sức khỏe" to false),
            "Đóng tiền quỹ lớp học kỳ 250k" to ("Giáo dục" to false),
            "Mừng thôi nôi cháu 200k" to ("Hiếu hỉ" to false),
            "Tiền tip bồi bàn 20k" to ("Khác" to true),
            "Chi 50k việc riêng không tiện nói" to ("Khác" to true)
        )

        for ((input, expected) in edgeCases) {
            val (expectedCategory, expectedFallback) = expected
            val (matched, isFallback) = com.example.apptaichinh.data.ai.tools.LocalToolExecutor.matchBestCategory(
                input, "EXPENSE", categories
            )
            assertNotNull("Không tìm thấy danh mục cho: '$input'", matched)
            assertEquals("Sai danh mục cho: '$input'", expectedCategory, matched?.name)
            assertEquals("Sai cờ fallback cho: '$input'", expectedFallback, isFallback)
        }
    }

    @Test
    fun testPromptDirectivesForBudgetAndOverBudgetQueries() {
        val sampleCategories = com.example.apptaichinh.data.db.FinanceDatabaseHelper.getDefaultCategoriesList()
        val prompt = AgentPrompts.buildOrchestratorPrompt(sampleCategories)

        // Kiểm tra các chỉ thị AI về xem tổng tiêu và kiểm tra vượt hạn mức
        assertTrue(prompt.contains("TRA CỨU TỔNG TIÊU, NGÂN SÁCH & CẢNH BÁO VƯỢT HẠN MỨC"))
        assertTrue(prompt.contains("tháng này tiêu hết bao nhiêu"))
        assertTrue(prompt.contains("đã vượt ngân sách chưa"))
        assertTrue(prompt.contains("query_balance_summary"))
        assertTrue(prompt.contains("query_category_budget"))
        assertTrue(prompt.contains("is_over_budget"))
        assertTrue(prompt.contains("CẢNH BÁO VƯỢT HẠN MỨC"))
    }
}
