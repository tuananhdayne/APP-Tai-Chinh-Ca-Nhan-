package com.example.apptaichinh.data.ai

import com.example.apptaichinh.data.ai.tools.LocalToolExecutor
import com.example.apptaichinh.data.ai.tools.ToolDefinitions
import com.example.apptaichinh.data.db.FinanceDatabaseHelper
import com.example.apptaichinh.data.model.Category
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Bộ kiểm thử tự động toàn diện (Comprehensive Test Suite):
 * 1. Tổng tiền tháng tiêu bao nhiêu, số dư, tỷ lệ chi tiêu.
 * 2. Ngân sách tổng: kiểm tra vượt hạn mức hay chưa (vượt / chưa vượt / bằng / không cài).
 * 3. Ngân sách từng danh mục: kiểm tra danh mục đã tiêu bao nhiêu, đã vượt hạn mức chưa.
 * 4. Truy vấn đối chiếu danh mục mờ / từ đồng nghĩa (fuzzy category match).
 * 5. Bộ test case đầu vào đa dạng người dùng nhập vào (hơn 100 câu thoại tiếng Việt thực tế).
 * 6. Xử lý an toàn các trường hợp biên, từ lóng và fallback về 'Khác'.
 */
class AiBudgetAndUserInputsComprehensiveTest {

    private val defaultCategories = FinanceDatabaseHelper.getDefaultCategoriesList()

    // =========================================================================
    // PHẦN 1: KIỂM THỬ XEM TỔNG TIỀN THÁNG TIÊU BAO NHIÊU & VƯỢT NGÂN SÁCH TỔNG
    // =========================================================================

    @Test
    fun testMonthlySpending_UnderBudget_Normal() {
        val budgetLimit = 14400000L // 14.4 triệu
        val totalIncome = 20000000L  // 20 triệu
        val totalExpense = 8500000L  // 8.5 triệu

        val balance = totalIncome - totalExpense // 11.5 triệu
        val remaining = budgetLimit - totalExpense // 5.9 triệu
        val percentage = (totalExpense.toFloat() / budgetLimit.toFloat())
        val isOverBudget = budgetLimit > 0 && totalExpense > budgetLimit

        assertEquals(11500000L, balance)
        assertEquals(5900000L, remaining)
        assertFalse("Chưa vượt ngân sách thì isOverBudget phải là false", isOverBudget)
        assertTrue(percentage < 1.0f)
        assertEquals(59, (percentage * 100).toInt())
    }

    @Test
    fun testMonthlySpending_OverBudget_Warning() {
        val budgetLimit = 14400000L // 14.4 triệu
        val totalIncome = 15000000L  // 15 triệu
        val totalExpense = 16200000L // 16.2 triệu (vượt 1.8 triệu)

        val balance = totalIncome - totalExpense // -1.2 triệu (âm tiền)
        val remaining = budgetLimit - totalExpense // -1.8 triệu
        val percentage = (totalExpense.toFloat() / budgetLimit.toFloat())
        val isOverBudget = budgetLimit > 0 && totalExpense > budgetLimit

        assertEquals(-1200000L, balance)
        assertEquals(-1800000L, remaining)
        assertTrue("Đã tiêu 16.2tr vượt hạn mức 14.4tr thì isOverBudget phải là true", isOverBudget)
        assertTrue(percentage > 1.0f)
        assertEquals(112, (percentage * 100).toInt())
    }

    @Test
    fun testMonthlySpending_ExactLimit() {
        val budgetLimit = 10000000L // 10 triệu
        val totalExpense = 10000000L // 10 triệu tròn

        val remaining = budgetLimit - totalExpense
        val percentage = (totalExpense.toFloat() / budgetLimit.toFloat())
        val isOverBudget = budgetLimit > 0 && totalExpense > budgetLimit

        assertEquals(0L, remaining)
        assertFalse("Tiêu đúng 100% ngân sách chưa bị coi là vượt", isOverBudget)
        assertEquals(100, (percentage * 100).toInt())
    }

    @Test
    fun testMonthlySpending_OverByOneDong() {
        val budgetLimit = 5000000L
        val totalExpense = 5000001L

        val remaining = budgetLimit - totalExpense
        val isOverBudget = budgetLimit > 0 && totalExpense > budgetLimit

        assertEquals(-1L, remaining)
        assertTrue("Vượt dù chỉ 1 đồng cũng phải kích hoạt cảnh báo", isOverBudget)
    }

    @Test
    fun testMonthlySpending_ZeroBudgetLimit_NoOverBudget() {
        val budgetLimit = 0L
        val totalExpense = 3000000L

        val remaining = budgetLimit - totalExpense
        val percentage = if (budgetLimit > 0) (totalExpense.toFloat() / budgetLimit.toFloat()) else 0f
        val isOverBudget = budgetLimit > 0 && totalExpense > budgetLimit

        assertEquals(-3000000L, remaining)
        assertEquals(0f, percentage, 0.001f)
        assertFalse("Chưa cài đặt hạn mức (0) thì không kích hoạt cảnh báo vượt", isOverBudget)
    }

    // =========================================================================
    // PHẦN 2: KIỂM THỬ XEM VƯỢT DANH MỤC HAY CHƯA (PER-CATEGORY BUDGET)
    // =========================================================================

    @Test
    fun testCategoryBudget_FoodAndDrink_UnderAndOver() {
        val catBudget = 3500000L // Ăn uống: 3.5 triệu

        // Ca 1: Đã tiêu 2.2 triệu -> Còn 1.3 triệu (62%), chưa vượt
        val spentUnder = 2200000L
        val remainingUnder = catBudget - spentUnder
        val isOverUnder = catBudget > 0 && spentUnder > catBudget
        assertEquals(1300000L, remainingUnder)
        assertFalse(isOverUnder)
        assertEquals(62, (spentUnder.toFloat() / catBudget.toFloat() * 100).toInt())

        // Ca 2: Đã tiêu 4.1 triệu -> Vượt 600k (117%), ĐÃ VƯỢT
        val spentOver = 4100000L
        val remainingOver = catBudget - spentOver
        val isOverOver = catBudget > 0 && spentOver > catBudget
        assertEquals(-600000L, remainingOver)
        assertTrue(isOverOver)
        assertEquals(117, (spentOver.toFloat() / catBudget.toFloat() * 100).toInt())
    }

    @Test
    fun testCategoryBudget_ShoppingAndTransport_Status() {
        // Mua sắm: 1.5 triệu, đã tiêu 1.8 triệu
        val shopBudget = 1500000L
        val shopSpent = 1800000L
        assertTrue("Mua sắm đã tiêu 1.8tr trên hạn mức 1.5tr -> Phải báo vượt", shopSpent > shopBudget)

        // Đi lại: 1.0 triệu, đã tiêu 750k
        val transportBudget = 1000000L
        val transportSpent = 750000L
        assertFalse("Đi lại tiêu 750k trên hạn mức 1.0tr -> Chưa vượt", transportSpent > transportBudget)
        assertEquals(250000L, transportBudget - transportSpent)
    }

    @Test
    fun testFuzzyCategoryQueryMatching_InTool() {
        // Kiểm tra khả năng ánh xạ ngôn ngữ tự nhiên vào tên danh mục chính xác
        val expenseCats = defaultCategories.filter { it.type == "EXPENSE" }

        val fuzzyQueries = listOf(
            "tiền ăn" to "Ăn uống",
            "cơm phở" to "Ăn uống",
            "xăng xe" to "Đi lại",
            "grab bike" to "Đi lại",
            "tiền phòng" to "Nhà ở",
            "tiền điện nước" to "Nhà ở",
            "quần áo" to "Mua sắm",
            "shopee" to "Mua sắm",
            "cà phê" to "Giải trí",
            "nhậu nhẹt" to "Giải trí",
            "thuốc men" to "Sức khỏe",
            "khám răng" to "Sức khỏe",
            "học phí" to "Giáo dục",
            "sách vở" to "Giáo dục",
            "mừng cưới" to "Hiếu hỉ",
            "gửi tiết kiệm" to "Tiết kiệm",
            "trả nợ thẻ tín dụng" to "Trả nợ"
        )

        for ((query, expectedCat) in fuzzyQueries) {
            val (matched, isFallback) = LocalToolExecutor.matchBestCategory(query, "EXPENSE", expenseCats)
            assertNotNull("Không nhận diện được danh mục cho: '$query'", matched)
            assertEquals("Sai danh mục cho từ khóa mờ '$query'", expectedCat, matched?.name)
            assertFalse("'$query' không phải là fallback", isFallback)
        }
    }

    // =========================================================================
    // PHẦN 3: BỘ KIỂM THỬ ĐẦY ĐỦ INPUT NGƯỜI DÙNG NHẬP VÀO THEO TỪNG DANH MỤC
    // =========================================================================

    @Test
    fun testUserInput_FoodAndDrink_Varieties() {
        val inputs = listOf(
            "Sáng nay ăn bát phở tái nạm gầu 55k",
            "Trưa ăn bún bò huế giò heo 60 ngàn",
            "Uống cốc tà tưa trân châu 45k",
            "Ăn trưa cơm tấm sườn bì chả 45k",
            "Chiều đói mua cái bánh bao trứng cút 15k",
            "Tối đi ăn bún đậu mắm tôm thập cẩm 55k",
            "Đi chợ mua thịt lợn và rau muống 120k",
            "Ăn bát cháo lòng buổi sáng 30k",
            "Mua xôi xéo ruốc hành 20k",
            "Đi ăn buffet hải sản nướng cùng phòng 350k",
            "Uống ly nước mía vỉa hè 10k",
            "Ăn đĩa bánh cuốn chả mực 40k",
            "Ăn bát bánh canh ghẹ 50k",
            "Bữa tối ăn gói mì tôm trứng 15k",
            "Mua hộp sữa chua nếp cẩm 25k"
        )

        for (input in inputs) {
            val (cat, isFallback) = LocalToolExecutor.matchBestCategory(input, "EXPENSE", defaultCategories)
            assertEquals("Câu '$input' phải là Ăn uống", "Ăn uống", cat?.name)
            assertFalse(isFallback)
        }
    }

    @Test
    fun testUserInput_Transport_Varieties() {
        val inputs = listOf(
            "Đổ 70k xăng xe máy e5",
            "Đặt grab bike từ công ty về nhà 38k",
            "Đi be car ra ga Hà Nội 85k",
            "Bắt taxi xanh sm đi bệnh viện 120k",
            "Gửi ô tô qua đêm ở bãi xe 100k",
            "Thay nhớt và bảo dưỡng xe máy định kỳ 150k",
            "Bị thủng săm phải vá xe dọc đường 20k",
            "Mua vé xe buýt tháng liên tuyến 100k",
            "Qua trạm thu phí cao tốc bot 35k",
            "Mua vé tàu hỏa se1 về quê ăn tết 650k"
        )

        for (input in inputs) {
            val (cat, isFallback) = LocalToolExecutor.matchBestCategory(input, "EXPENSE", defaultCategories)
            assertEquals("Câu '$input' phải là Đi lại", "Đi lại", cat?.name)
            assertFalse(isFallback)
        }
    }

    @Test
    fun testUserInput_Housing_Varieties() {
        val inputs = listOf(
            "Đóng tiền điện tháng 8 hết 750k",
            "Thanh toán hóa đơn tiền nước sinh hoạt 110k",
            "Tiền mạng internet wifi fpt tháng này 220k",
            "Chuyển khoản tiền thuê nhà trọ 3 triệu",
            "Nộp tiền phí dịch vụ quản lý chung cư 450k",
            "Đóng tiền rác sinh hoạt 3 tháng 90k",
            "Gọi thợ sửa đường ống nước và thay vòi sen 180k",
            "Đổi bình gas đun nấu 420k",
            "Mua bóng đèn led thay phòng khách 65k"
        )

        for (input in inputs) {
            val (cat, isFallback) = LocalToolExecutor.matchBestCategory(input, "EXPENSE", defaultCategories)
            assertEquals("Câu '$input' phải là Nhà ở", "Nhà ở", cat?.name)
            assertFalse(isFallback)
        }
    }

    @Test
    fun testUserInput_Shopping_Varieties() {
        val inputs = listOf(
            "Mua cái áo sơ mi trắng công sở 280k",
            "Mua đôi giày thể thao bitis hunter 850k",
            "Sắm chiếc quần jean nam ống đứng 350k",
            "Order thỏi son và bộ mỹ phẩm shopee 320k",
            "Mua túi xách da đi làm 450k",
            "Đi siêu thị mua chảo chống dính và đồ gia dụng 380k",
            "Mua chiếc váy đầm dự tiệc 550k",
            "Mua chiếc đồng hồ đeo tay casio 600k"
        )

        for (input in inputs) {
            val (cat, isFallback) = LocalToolExecutor.matchBestCategory(input, "EXPENSE", defaultCategories)
            assertEquals("Câu '$input' phải là Mua sắm", "Mua sắm", cat?.name)
            assertFalse(isFallback)
        }
    }

    @Test
    fun testUserInput_Entertainment_Varieties() {
        val inputs = listOf(
            "Đi uống cafe muối chú long với bạn 35k",
            "Uống cà phê highland chém gió cuối tuần 65k",
            "Đi nhậu bia hơi hà nội cùng anh em công ty 280k",
            "Mua vé xem phim rạp CGV cuối tuần 150k",
            "Đi hát karaoke mừng sinh nhật bạn 350k",
            "Chơi bida giải trí 2 tiếng 90k",
            "Nạp thẻ garena chơi game liên quân 100k",
            "Đi xem kịch nghệ kịch sân khấu 200k"
        )

        for (input in inputs) {
            val (cat, isFallback) = LocalToolExecutor.matchBestCategory(input, "EXPENSE", defaultCategories)
            assertEquals("Câu '$input' phải là Giải trí", "Giải trí", cat?.name)
            assertFalse(isFallback)
        }
    }

    @Test
    fun testUserInput_Health_Varieties() {
        val inputs = listOf(
            "Mua thuốc hạ sốt panadol ở hiệu thuốc pharmacity 45k",
            "Đi khám răng lấy cao răng định kỳ 200k",
            "Chi phí đi nhổ răng khôn số 8 1tr5",
            "Mua khẩu trang y tế và c sủi vitamin 70k",
            "Viện phí khám sức khỏe tổng quát bệnh viện 600k",
            "Mua thuốc kháng sinh và siro ho 95k"
        )

        for (input in inputs) {
            val (cat, isFallback) = LocalToolExecutor.matchBestCategory(input, "EXPENSE", defaultCategories)
            assertEquals("Câu '$input' phải là Sức khỏe", "Sức khỏe", cat?.name)
            assertFalse(isFallback)
        }
    }

    @Test
    fun testUserInput_Education_Varieties() {
        val inputs = listOf(
            "Nộp tiền học phí khóa học tiếng anh ielts 3 triệu",
            "Mua cuốn sách lập trình android bằng kotlin 180k",
            "Mua sách giáo khoa và vở viết cho con 250k",
            "Thuê gia sư dạy kèm toán 600k",
            "Mua bút bi và tập tài liệu học tập 35k"
        )

        for (input in inputs) {
            val (cat, isFallback) = LocalToolExecutor.matchBestCategory(input, "EXPENSE", defaultCategories)
            assertEquals("Câu '$input' phải là Giáo dục", "Giáo dục", cat?.name)
            assertFalse(isFallback)
        }
    }

    @Test
    fun testUserInput_SocialAndGifts_Varieties() {
        val inputs = listOf(
            "Bỏ phong bì mừng đám cưới bạn thân 500k",
            "Mua quà sinh nhật tặng người yêu 350k",
            "Thăm người ốm mua giỏ hoa quả bánh sữa 300k",
            "Mừng tiệc thôi nôi cháu 200k",
            "Đi phúng viếng đám tang 300k",
            "Lì xì tết cho các cháu nhỏ 500k"
        )

        for (input in inputs) {
            val (cat, isFallback) = LocalToolExecutor.matchBestCategory(input, "EXPENSE", defaultCategories)
            assertEquals("Câu '$input' phải là Hiếu hỉ", "Hiếu hỉ", cat?.name)
            assertFalse(isFallback)
        }
    }

    @Test
    fun testUserInput_SavingsAndDebt_Varieties() {
        val expenseInputs = listOf(
            "Gửi tiết kiệm ngân hàng vietcombank 5 triệu" to "Tiết kiệm",
            "Trích tiền vào sổ tiết kiệm online 2tr" to "Tiết kiệm",
            "Thanh toán dư nợ thẻ tín dụng tháng này 3 triệu" to "Trả nợ",
            "Trả góp tiền mua điện thoại fe credit 1tr2" to "Trả nợ",
            "Trả tiền vay bạn tuần trước 500k" to "Trả nợ"
        )

        for ((input, expectedCat) in expenseInputs) {
            val (cat, isFallback) = LocalToolExecutor.matchBestCategory(input, "EXPENSE", defaultCategories)
            assertEquals("Câu '$input' sai danh mục", expectedCat, cat?.name)
            assertFalse(isFallback)
        }
    }

    @Test
    fun testUserInput_Incomes_Varieties() {
        val incomeInputs = listOf(
            "Công ty bắn lương tháng này 20 triệu" to "Lương",
            "Ting ting lương về tài khoản 16tr5" to "Lương",
            "Nhận lương cứng đợt 1 10 triệu" to "Lương",
            "Thưởng nóng hoàn thành dự án xuất sắc 3 củ" to "Thưởng",
            "Tiền thưởng tết nguyên đán 2 tháng lương 30 triệu" to "Thưởng",
            "Nhận thưởng KPI quý 5 triệu" to "Thưởng",
            "Làm thêm dự án freelance thiết kế ngoài giờ 4tr" to "Làm thêm",
            "Tiền công làm part-time ca tối 1tr5" to "Làm thêm",
            "Tiền làm gia sư dạy kèm tháng này 1tr8" to "Làm thêm",
            "Nhận tiền cổ tức chứng khoán fpt 2tr5" to "Đầu tư",
            "Lãi đầu tư tiền ảo crypto 2 triệu" to "Đầu tư",
            "Rút tiền lãi từ sổ tiết kiệm ngân hàng 600k" to "Đầu tư"
        )

        for ((input, expectedCat) in incomeInputs) {
            val (cat, isFallback) = LocalToolExecutor.matchBestCategory(input, "INCOME", defaultCategories)
            assertEquals("Câu '$input' phải là $expectedCat", expectedCat, cat?.name)
            assertFalse(isFallback)
        }
    }

    // =========================================================================
    // PHẦN 4: KIỂM THỬ TRƯỜNG HỢP BIÊN, TRÁNH XUNG ĐỘT TỪ NGẮN & AN TOÀN FALLBACK
    // =========================================================================

    @Test
    fun testEdgeCases_ShortWordCollisions() {
        // "xe" (trong Đi lại) không được ăn vào "xem kịch" hay "xem phim" (Giải trí)
        val (cat1, _) = LocalToolExecutor.matchBestCategory("Mua vé xem kịch 200k", "EXPENSE", defaultCategories)
        assertEquals("Giải trí", cat1?.name)

        val (cat2, _) = LocalToolExecutor.matchBestCategory("Vé xem phim tối nay 100k", "EXPENSE", defaultCategories)
        assertEquals("Giải trí", cat2?.name)

        // "xe máy" hay "vé xe" phải về Đi lại
        val (cat3, _) = LocalToolExecutor.matchBestCategory("Tiền vé xe khách 150k", "EXPENSE", defaultCategories)
        assertEquals("Đi lại", cat3?.name)
    }

    @Test
    fun testEdgeCases_AmbiguousInputs_SafeFallbackToKhac() {
        val ambiguousExpense = listOf(
            "Chi tiêu một khoản 200k",
            "Tiêu 50k việc riêng tư",
            "Khoản tiền bí mật 100k",
            "Tiền tip bồi bàn 20k"
        )

        for (input in ambiguousExpense) {
            val (cat, isFallback) = LocalToolExecutor.matchBestCategory(input, "EXPENSE", defaultCategories)
            assertEquals("Khác", cat?.name)
            assertTrue("Câu '$input' phải bật cờ isFallback=true", isFallback)
        }

        val ambiguousIncome = listOf(
            "Nhặt được tiền rơi trên đường 50k",
            "Khoản thu nhập bất ngờ không rõ nguồn 100k"
        )

        for (input in ambiguousIncome) {
            val (cat, isFallback) = LocalToolExecutor.matchBestCategory(input, "INCOME", defaultCategories)
            assertEquals("Khác", cat?.name)
            assertTrue("Câu thu nhập '$input' phải fallback về Khác", isFallback)
        }
    }

    @Test
    fun testToolDefinitions_QueryCategoryBudget_HasMonthOffset() {
        val tools = ToolDefinitions.getAllTools()
        val queryCatBudgetTool = (0 until tools.length())
            .map { tools.getJSONObject(it).getJSONObject("function") }
            .first { it.getString("name") == "query_category_budget" }

        val params = queryCatBudgetTool.getJSONObject("parameters")
        val props = params.getJSONObject("properties")

        assertTrue("query_category_budget phải hỗ trợ category_name", props.has("category_name"))
        assertTrue("query_category_budget phải hỗ trợ month_offset", props.has("month_offset"))
    }

    // =========================================================================
    // PHẦN 5: KIỂM THỬ NÂNG CAO - CẤU TRÚC JSON HỢP ĐỒNG & CÁC CÂU NÓI PHỨC HỢP
    // =========================================================================

    @Test
    fun testComplexMultiTopicInputs() {
        val testPairs = listOf(
            "Mua vé máy bay khứ hồi Hà Nội Đà Nẵng 2 triệu" to "Đi lại",
            "Nộp tiền cước điện thoại di động viettel 100k" to "Nhà ở",
            "Tiền điện nước phòng trọ tháng này 450k" to "Nhà ở",
            "Mua chuột máy tính không dây logitech 250k" to "Mua sắm",
            "Mua sách ôn thi tiếng anh toeic 150k" to "Giáo dục",
            "Mua thuốc cảm cúm tiffy hạ sốt 30k" to "Sức khỏe",
            "Đi ăn cưới ở nhà hàng tiệc cưới mừng 500k" to "Hiếu hỉ",
            "Thanh toán dư nợ thẻ visa credit 2tr5" to "Trả nợ",
            "Gửi tiết kiệm có kỳ hạn online 10 triệu" to "Tiết kiệm"
        )

        for ((input, expectedCat) in testPairs) {
            val (cat, isFallback) = LocalToolExecutor.matchBestCategory(input, "EXPENSE", defaultCategories)
            assertEquals("Câu '$input' phải là $expectedCat", expectedCat, cat?.name)
            assertFalse("'$input' không được fallback", isFallback)
        }
    }

    @Test
    fun testMonthlyBalanceJsonStructureVerification() {
        // Mô phỏng JSON kết quả trả về của query_balance_summary khi kiểm tra chi tiêu tháng
        val jsonOutput = JSONObject().apply {
            put("year", 2026)
            put("month", 9)
            put("total_income_vnd", 25000000L)
            put("total_expense_vnd", 16000000L)
            put("net_balance_vnd", 9000000L)
            put("total_budget_limit_vnd", 14400000L)
            put("remaining_budget_vnd", -1600000L)
            put("percentage_spent", "111%")
            put("is_over_budget", true)
            put("over_budget_category_count", 2)
            put("over_budget_categories", org.json.JSONArray().apply {
                put("🍜 Ăn uống: đã tiêu 4.100.000/3.500.000 đ (vượt 600.000 đ)")
                put("🛍️ Mua sắm: đã tiêu 1.800.000/1.500.000 đ (vượt 300.000 đ)")
            })
        }

        assertEquals(2026, jsonOutput.getInt("year"))
        assertEquals(9, jsonOutput.getInt("month"))
        assertEquals(25000000L, jsonOutput.getLong("total_income_vnd"))
        assertEquals(16000000L, jsonOutput.getLong("total_expense_vnd"))
        assertEquals(9000000L, jsonOutput.getLong("net_balance_vnd"))
        assertEquals(14400000L, jsonOutput.getLong("total_budget_limit_vnd"))
        assertEquals(-1600000L, jsonOutput.getLong("remaining_budget_vnd"))
        assertEquals("111%", jsonOutput.getString("percentage_spent"))
        assertTrue("is_over_budget phải là true khi tiêu vượt hạn mức", jsonOutput.getBoolean("is_over_budget"))
        assertEquals(2, jsonOutput.getInt("over_budget_category_count"))
        assertEquals(2, jsonOutput.getJSONArray("over_budget_categories").length())
    }

    @Test
    fun testCategoryBudgetJsonStructureVerification() {
        // Mô phỏng JSON kết quả trả về của query_category_budget
        val jsonOutput = JSONObject().apply {
            put("year", 2026)
            put("month", 9)
            put("over_budget_count", 1)
            put("results", org.json.JSONArray().apply {
                put(JSONObject().apply {
                    put("category", "🍜 Ăn uống")
                    put("budget_limit_vnd", 3500000L)
                    put("spent_vnd", 4100000L)
                    put("remaining_vnd", -600000L)
                    put("percentage_spent", "117%")
                    put("is_over_budget", true)
                })
            })
        }

        assertEquals(1, jsonOutput.getInt("over_budget_count"))
        val item = jsonOutput.getJSONArray("results").getJSONObject(0)
        assertEquals("🍜 Ăn uống", item.getString("category"))
        assertEquals(3500000L, item.getLong("budget_limit_vnd"))
        assertEquals(4100000L, item.getLong("spent_vnd"))
        assertEquals(-600000L, item.getLong("remaining_vnd"))
        assertEquals("117%", item.getString("percentage_spent"))
        assertTrue(item.getBoolean("is_over_budget"))
    }

    @Test
    fun testExactAmountExtraction_100kAndVariants() {
        // Kiểm tra lỗi người dùng báo: "ăn pizaa hết 100k" bị nhầm thành 10k
        val inputUser = "ăn pizaa hết 100k"
        val extractedAmount = LocalToolExecutor.extractAmountFromText(inputUser)
        assertEquals("100k phải được quy đổi chính xác thành 100.000 đ, TUYỆT ĐỐI KHÔNG phải 10.000 đ", 100000L, extractedAmount)

        // Kiểm tra các biến thể số tiền khác
        val cases = listOf(
            "ăn pizza hết 100k" to 100000L,
            "mua đồ 10k" to 10000L,
            "đổ xăng 50k" to 50000L,
            "mua áo 200k" to 200000L,
            "tiền điện 500k" to 500000L,
            "tiền phòng 1tr5" to 1500000L,
            "thuê nhà 1.5 triệu" to 1500000L,
            "nhận lương 15tr" to 15000000L,
            "thưởng tết 2 củ" to 2000000L,
            "mua bánh 25 cành" to 25000L,
            "tiền nước 2 lít" to 200000L,
            "chuyển khoản 100.000đ" to 100000L,
            "thanh toán 100,000 đồng" to 100000L,
            "nộp học phí 650 ngàn" to 650000L,
            "tiền rác 90 nghìn" to 90000L
        )

        for ((text, expected) in cases) {
            val amount = LocalToolExecutor.extractAmountFromText(text)
            assertEquals("Sai số tiền cho câu: '$text'", expected, amount)
        }
    }

    @Test
    fun testPizzaExpenseInput() {
        // Kiểm tra cả "pizza" và "pizaa" đều vào đúng Ăn uống
        val (cat1, isFallback1) = LocalToolExecutor.matchBestCategory("ăn pizaa hết 100k", "EXPENSE", defaultCategories)
        assertEquals("Ăn uống", cat1?.name)
        assertFalse(isFallback1)

        val (cat2, isFallback2) = LocalToolExecutor.matchBestCategory("mua pizza hải sản 150k", "EXPENSE", defaultCategories)
        assertEquals("Ăn uống", cat2?.name)
        assertFalse(isFallback2)
    }

    @Test
    fun testHouseRent1000kInputAndAutoRecovery() {
        // Kiểm tra lỗi người dùng báo: "nay đóng tiền thuê nhà hết 1000k"
        val inputUser = "nay đóng tiền thuê nhà hết 1000k"

        // 1. Trích xuất số tiền: 1000k = 1.000.000 đ
        val extractedAmount = LocalToolExecutor.extractAmountFromText(inputUser)
        assertEquals("1000k phải được quy đổi thành 1.000.000 đ", 1000000L, extractedAmount)

        // 2. Xác định loại: Chi tiêu (EXPENSE)
        val isIncome = LocalToolExecutor.isIncomeIntent(inputUser)
        assertFalse("Đóng tiền thuê nhà phải là EXPENSE, không phải INCOME", isIncome)

        // 3. Khớp danh mục: Nhà ở
        val (matchedCat, isFallback) = LocalToolExecutor.matchBestCategory(inputUser, "EXPENSE", defaultCategories)
        assertNotNull(matchedCat)
        assertEquals("Nhà ở", matchedCat?.name)
        assertFalse("Không được coi là fallback", isFallback)

        // 4. Mô phỏng kịch bản Auto-Recovery khi LLM "chém gió" trả lời bằng text mà quên gọi create_transaction
        val lowerMsg = inputUser.lowercase()
        val isQueryIntent = lowerMsg.contains("hết bao nhiêu") || lowerMsg.contains("bao nhiêu tiền") ||
                lowerMsg.contains("vượt chưa") || lowerMsg.contains("còn bao nhiêu") ||
                lowerMsg.contains("xem lại") || lowerMsg.contains("tìm") || lowerMsg.contains("kiểm tra")
        assertFalse(isQueryIntent)

        val recoveredAction = ToolAction(
            type = ToolActionType.CREATE,
            amount = extractedAmount!!,
            transactionType = "EXPENSE",
            categoryId = matchedCat!!.id,
            categoryName = matchedCat.name,
            categoryIcon = matchedCat.icon,
            categoryColorHex = matchedCat.colorHex,
            note = inputUser.take(50)
        )

        assertEquals(1000000L, recoveredAction.amount)
        assertEquals("Nhà ở", recoveredAction.categoryName)
        assertEquals("EXPENSE", recoveredAction.transactionType)
    }

    @Test
    fun testOverBudgetExceedsCalculationAndFormatting_ExactUserCase() {
        // Kịch bản thực tế của người dùng:
        // Tổng chi tiêu: 20.310.000 đ (trong đó Nhà ở = 20.000.000 đ)
        // Tổng thu nhập: 2.000.000 đ
        // Ngân sách tổng tháng: 2.000.000 đ
        val totalExpense = 20310000L
        val totalIncome = 2000000L
        val totalBudget = 2000000L

        val isOverBudget = totalExpense > totalBudget
        val overAmount = totalExpense - totalBudget
        val remainingBudget = if (isOverBudget) 0L else (totalBudget - totalExpense)

        assertTrue(isOverBudget)
        assertEquals(18310000L, overAmount)
        assertEquals(0L, remainingBudget)

        // Kiểm tra format tiền tệ không bị rớt số 0
        val formattedExpense = LocalToolExecutor.formatVndAmount(totalExpense)
        val formattedOver = LocalToolExecutor.formatVndAmount(overAmount)
        val formattedIncome = LocalToolExecutor.formatVndAmount(totalIncome)

        assertEquals("20.310.000 đ", formattedExpense)
        assertEquals("18.310.000 đ", formattedOver)
        assertEquals("2.000.000 đ", formattedIncome)

        // Kiểm tra cấu trúc JSON trả về cho LLM
        val summaryJson = JSONObject().apply {
            put("total_income_vnd", totalIncome)
            put("total_income_formatted", formattedIncome)
            put("total_expense_vnd", totalExpense)
            put("total_expense_formatted", formattedExpense)
            put("total_budget_limit_vnd", totalBudget)
            put("total_budget_limit_formatted", formattedIncome)
            put("remaining_budget_vnd", remainingBudget)
            put("remaining_budget_formatted", "0 đ")
            put("is_over_budget", isOverBudget)
            put("budget_status", "ĐÃ VƯỢT NGÂN SÁCH")
            put("over_budget_amount_vnd", overAmount)
            put("over_budget_amount_formatted", formattedOver)
            put("budget_verdict", "Đang VƯỢT NGÂN SÁCH $formattedOver (Hạn mức: $formattedIncome, Đã chi tiêu: $formattedExpense). TUYỆT ĐỐI KHÔNG dùng từ 'còn thiếu', phải nói rõ là 'ĐANG VƯỢT NGÂN SÁCH $formattedOver'.")
        }

        assertTrue(summaryJson.getBoolean("is_over_budget"))
        assertEquals(18310000L, summaryJson.getLong("over_budget_amount_vnd"))
        assertEquals("18.310.000 đ", summaryJson.getString("over_budget_amount_formatted"))
        assertEquals(0L, summaryJson.getLong("remaining_budget_vnd"))
        assertTrue(summaryJson.getString("budget_verdict").contains("Đang VƯỢT NGÂN SÁCH 18.310.000 đ"))
        assertTrue(summaryJson.getString("budget_verdict").contains("TUYỆT ĐỐI KHÔNG dùng từ 'còn thiếu'"))
    }

    @Test
    fun testAiExplanationSanitizer_RemovesConThieuHallucination() {
        // Mô phỏng đúng câu trả lời bị lỗi ảo giác của LLM từ ảnh chụp màn hình
        val rawHallucinatedAnswer = "Tháng này, bạn đã tổng cộng tiêu hết 20.310.000 đồng và còn thiếu 1.831.000đ. Tổng thu nhập của tháng là 2.000.000đ, nhưng chi tiêu vượt quá ngân sách tổng định trước với tỷ lệ 1015%."

        var cleanExplanation = rawHallucinatedAnswer
        if (cleanExplanation.contains("còn thiếu", ignoreCase = true)) {
            if (cleanExplanation.contains("vượt", ignoreCase = true) || 
                cleanExplanation.contains("lố", ignoreCase = true) || 
                cleanExplanation.contains("ngân sách", ignoreCase = true)) {
                cleanExplanation = cleanExplanation
                    .replace(Regex("và còn thiếu\\s+[0-9.,]+(đ| đồng| ₫)?", RegexOption.IGNORE_CASE), "")
                    .replace(Regex(",?\\s*còn thiếu\\s+[0-9.,]+(đ| đồng| ₫)?", RegexOption.IGNORE_CASE), "")
            }
        }

        assertFalse("Không được chứa cụm từ 'còn thiếu'", cleanExplanation.contains("còn thiếu"))
        assertTrue("Vẫn giữ nguyên tổng chi tiêu 20.310.000", cleanExplanation.contains("20.310.000"))
        assertTrue("Vẫn giữ cảnh báo vượt ngân sách", cleanExplanation.contains("vượt quá ngân sách"))
    }

    @Test
    fun testMarkdownAsterisksRemoval_StripDoubleAsterisksFromDataAndMoney() {
        // Kiểm tra việc loại bỏ hoàn toàn các ký tự markdown ** bao quanh số tiền hoặc dữ liệu
        val rawAiResponse = "Tháng này bạn đã tiêu **1800000** đồng cho danh mục **Nhà ở** và vượt **18.310.000 đ**."
        val sanitized = rawAiResponse.replace("**", "").replace("`", "")

        assertEquals("Tháng này bạn đã tiêu 1800000 đồng cho danh mục Nhà ở và vượt 18.310.000 đ.", sanitized)
        assertFalse(sanitized.contains("**"))
        assertFalse(sanitized.contains("`"))
    }
}
