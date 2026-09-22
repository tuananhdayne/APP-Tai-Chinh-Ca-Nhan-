package com.example.apptaichinh.data.ai

import com.example.apptaichinh.data.ai.tools.LocalToolExecutor
import com.example.apptaichinh.data.ai.tools.ToolDefinitions
import com.example.apptaichinh.data.db.FinanceDatabaseHelper
import com.example.apptaichinh.data.model.Category
import com.example.apptaichinh.data.model.Transaction
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * BỘ KIỂM THỬ TOÀN DIỆN CÁC CHUỖI HÀNH ĐỘNG NỐI TIẾP NHAU (SEQUENTIAL ACTION CHAINS):
 * 1. Chuỗi ReAct 2 bước: Query Tool (query_categories) -> Action Tool (create_transaction) -> Preview Card.
 * 2. Chuỗi ghi chi tiêu liên tiếp -> Tác động ngân sách tổng -> Kích hoạt cảnh báo vượt hạn mức (query_balance_summary).
 * 3. Chuỗi ghi chi tiêu danh mục liên tiếp -> Cảnh báo vượt hạn mức danh mục (query_category_budget).
 * 4. Chuỗi Tạo giao dịch -> Tìm kiếm (find_transactions) -> Cập nhật (update_transaction) -> Số dư thay đổi.
 * 5. Chuỗi Tạo giao dịch -> Tìm kiếm (find_transactions) -> Xóa (delete_transaction) -> Hoàn trả ngân sách.
 * 6. Chuỗi Thu - Chi đan xen liên tục -> Tổng kết số dư ròng chuẩn xác.
 * 7. Chuỗi Phục hồi tự động (Auto-Recovery) khi LLM quên gọi tool -> Xác nhận lưu -> Tra cứu lịch sử.
 */
class AiSequentialActionChainTest {

    private val defaultCategories = FinanceDatabaseHelper.getDefaultCategoriesList()

    // =========================================================================
    // CHUỖI 1: REACT 2 BƯỚC (QUERY TOOL -> ACTION TOOL -> CONFIRMATION CARD)
    // =========================================================================

    @Test
    fun testChain1_ReActTwoStepWorkflow_QueryToCreate() {
        val userSentence = "Trưa nay ăn bát phở tái nạm gầu 55k"

        // BƯỚC 1 & 2: Gọi query_categories để tra cứu đối chiếu danh mục & trích xuất số tiền
        val queryCatArgs = JSONObject().apply {
            put("type", "EXPENSE")
            put("user_text", userSentence)
        }
        val targetCats = defaultCategories.filter { it.type == "EXPENSE" }
        val (matchedCat, isFallback) = LocalToolExecutor.matchBestCategory(userSentence, "EXPENSE", targetCats)
        val detectedAmount = LocalToolExecutor.extractAmountFromText(userSentence)

        // Kiểm tra kết quả bước 2
        assertNotNull("Phải khớp danh mục", matchedCat)
        assertEquals("Ăn uống", matchedCat?.name)
        assertFalse("Không được fallback", isFallback)
        assertEquals(55000L, detectedAmount)

        // BƯỚC 3: Nối tiếp kết quả bước 2 để gọi create_transaction
        val createTxArgs = JSONObject().apply {
            put("amount", detectedAmount)
            put("type", "EXPENSE")
            put("category_name", matchedCat!!.name)
            put("note", "Ăn bát phở tái nạm gầu")
        }

        val finalCat = matchedCat!!
        val toolAction = ToolAction(
            type = ToolActionType.CREATE,
            amount = createTxArgs.getLong("amount"),
            transactionType = createTxArgs.getString("type"),
            categoryId = finalCat.id,
            categoryName = finalCat.name,
            categoryIcon = finalCat.icon,
            categoryColorHex = finalCat.colorHex,
            note = createTxArgs.getString("note")
        )

        // BƯỚC 4: Kiểm tra trạng thái phiếu xem trước (Preview Card)
        assertEquals(ToolActionType.CREATE, toolAction.type)
        assertEquals(55000L, toolAction.amount)
        assertEquals("Ăn uống", toolAction.categoryName)
        assertEquals("🍜", toolAction.categoryIcon)

        // BƯỚC 5: Người dùng bấm "Xác Nhận Lưu"
        val chatMessage = ChatMessage(
            sender = MessageSender.ASSISTANT,
            text = "Đã nhận diện khoản Chi tiêu 55.000 đ",
            toolAction = toolAction,
            cardStatus = CardStatus.PENDING
        )
        assertEquals(CardStatus.PENDING, chatMessage.cardStatus)

        // Người dùng bấm duyệt -> chuyển CONFIRMED
        val confirmedMessage = chatMessage.copy(cardStatus = CardStatus.CONFIRMED)
        assertEquals(CardStatus.CONFIRMED, confirmedMessage.cardStatus)
    }

    // =========================================================================
    // CHUỖI 2: GHI NHIỀU CHI TIÊU LIÊN TIẾP -> CẢNH BÁO VƯỢT NGÂN SÁCH TỔNG
    // =========================================================================

    @Test
    fun testChain2_MultipleExpensesChain_TriggerOverBudget() {
        val totalBudgetLimit = 14400000L // Hạn mức tháng: 14.4 triệu
        var currentExpense = 11000000L   // Đã chi tiêu ban đầu: 11 triệu

        // Hành động 1: Người dùng ghi nhận tiền thuê nhà 2.5 triệu
        val action1Amount = 2500000L
        currentExpense += action1Amount
        // Sau hành động 1: Tiêu 13.5tr / 14.4tr -> Còn 900k (93%)
        val remaining1 = totalBudgetLimit - currentExpense
        val isOver1 = totalBudgetLimit > 0 && currentExpense > totalBudgetLimit
        assertEquals(900000L, remaining1)
        assertFalse("13.5tr chưa vượt 14.4tr", isOver1)

        // Hành động 2: Người dùng ghi nhận mua sắm quần áo 1.5 triệu
        val action2Amount = 1500000L
        currentExpense += action2Amount
        // Sau hành động 2: Tiêu 15.0tr / 14.4tr -> Vượt 600k (104%)!
        val remaining2 = totalBudgetLimit - currentExpense
        val isOver2 = totalBudgetLimit > 0 && currentExpense > totalBudgetLimit
        assertEquals(-600000L, remaining2)
        assertTrue("15.0tr ĐÃ VƯỢT hạn mức 14.4tr", isOver2)

        // Hành động 3: Người dùng hỏi "Tháng này tiêu hết bao nhiêu, đã vượt chưa?"
        // Giả lập kết quả query_balance_summary
        val summaryJson = JSONObject().apply {
            put("total_budget_limit_vnd", totalBudgetLimit)
            put("total_expense_vnd", currentExpense)
            put("remaining_budget_vnd", remaining2)
            put("percentage_spent", "${(currentExpense.toFloat() / totalBudgetLimit.toFloat() * 100).toInt()}%")
            put("is_over_budget", isOver2)
        }

        assertEquals(15000000L, summaryJson.getLong("total_expense_vnd"))
        assertEquals(-600000L, summaryJson.getLong("remaining_budget_vnd"))
        assertEquals("104%", summaryJson.getString("percentage_spent"))
        assertTrue("Phải kích hoạt cờ is_over_budget", summaryJson.getBoolean("is_over_budget"))
    }

    // =========================================================================
    // CHUỖI 3: CHI TIÊU DANH MỤC NỐI TIẾP -> VƯỢT HẠN MỨC DANH MỤC CỤ THỂ
    // =========================================================================

    @Test
    fun testChain3_CategorySpendingChain_TriggerCategoryOverBudget() {
        val foodBudget = 3500000L // Ăn uống: 3.5 triệu
        var spentAmount = 0L

        // Giao dịch 1: "Ăn trưa 50k"
        spentAmount += 50000L
        assertEquals(3450000L, foodBudget - spentAmount)
        assertFalse(spentAmount > foodBudget)

        // Giao dịch 2: "Tiệc liên hoan buffet công ty 3tr4"
        spentAmount += 3400000L
        // Tổng: 3.450.000 / 3.500.000 -> Còn 50k (98%), chưa vượt
        assertEquals(3450000L, spentAmount)
        assertEquals(50000L, foodBudget - spentAmount)
        assertFalse("3.45tr chưa vượt 3.5tr", spentAmount > foodBudget)

        // Giao dịch 3: "Uống cốc trà sữa 60k"
        spentAmount += 60000L
        // Tổng: 3.510.000 / 3.500.000 -> Vượt 10k!
        val remaining = foodBudget - spentAmount
        val isOver = spentAmount > foodBudget
        assertEquals(-10000L, remaining)
        assertTrue("3.51tr ĐÃ VƯỢT hạn mức 3.5tr", isOver)

        // Hành động 4: Gọi query_category_budget kiểm tra kết quả
        val catBudgetJson = JSONObject().apply {
            put("category", "🍜 Ăn uống")
            put("budget_limit_vnd", foodBudget)
            put("spent_vnd", spentAmount)
            put("remaining_vnd", remaining)
            put("percentage_spent", "100%")
            put("is_over_budget", isOver)
        }

        assertTrue(catBudgetJson.getBoolean("is_over_budget"))
        assertEquals(-10000L, catBudgetJson.getLong("remaining_vnd"))
    }

    // =========================================================================
    // CHUỖI 4: TẠO GIAO DỊCH -> TÌM KIẾM -> SỬA (UPDATE) -> SỐ TIỀN THAY ĐỔI
    // =========================================================================

    @Test
    fun testChain4_CreateThenSearchThenUpdateChain() {
        // Bước 1: Tạo giao dịch ban đầu (Ăn bún bò 50k)
        val initialTx = Transaction(
            id = 101L,
            amount = 50000L,
            type = "EXPENSE",
            categoryId = 1L,
            categoryName = "Ăn uống",
            categoryIcon = "🍜",
            categoryColorHex = "#EF4444",
            note = "Ăn bún bò",
            dateEpoch = System.currentTimeMillis()
        )

        // Bước 2: Người dùng yêu cầu "Sửa tiền bún bò thành 65k"
        // AI gọi find_transactions(keyword="bún bò") -> Tìm ra initialTx
        val searchKeyword = "bún bò"
        assertTrue(initialTx.note.contains(searchKeyword))

        // Bước 3: AI gọi update_transaction với new_amount = 65000
        val updateAction = ToolAction(
            type = ToolActionType.UPDATE,
            targetTransaction = initialTx,
            newAmount = 65000L,
            newNote = "Ăn bún bò đặc biệt",
            searchKeyword = searchKeyword
        )

        // Kiểm tra phiếu xem trước Sửa
        assertEquals(ToolActionType.UPDATE, updateAction.type)
        assertEquals(101L, updateAction.targetTransaction?.id)
        assertEquals(65000L, updateAction.newAmount)

        // Bước 4: Người dùng duyệt -> Giao dịch được cập nhật
        val updatedTx = initialTx.copy(
            amount = updateAction.newAmount ?: initialTx.amount,
            note = updateAction.newNote ?: initialTx.note
        )

        assertEquals(65000L, updatedTx.amount)
        assertEquals("Ăn bún bò đặc biệt", updatedTx.note)
        // Số tiền chi tiêu tăng thêm 15.000 đ
        val difference = updatedTx.amount - initialTx.amount
        assertEquals(15000L, difference)
    }

    // =========================================================================
    // CHUỖI 5: TẠO GIAO DỊCH -> TÌM KIẾM -> XÓA (DELETE) -> HOÀN TIỀN NGÂN SÁCH
    // =========================================================================

    @Test
    fun testChain5_CreateThenSearchThenDeleteChain() {
        var totalExpense = 5000000L

        // Bước 1: Thêm giao dịch nhầm (Mua áo 300k)
        val txToDelete = Transaction(
            id = 202L,
            amount = 300000L,
            type = "EXPENSE",
            categoryId = 4L,
            categoryName = "Mua sắm",
            categoryIcon = "🛍️",
            categoryColorHex = "#EC4899",
            note = "Mua cái áo thun",
            dateEpoch = System.currentTimeMillis()
        )
        totalExpense += txToDelete.amount
        assertEquals(5300000L, totalExpense)

        // Bước 2: Người dùng yêu cầu "Xóa khoản mua áo thun 300k"
        // AI gọi delete_transaction(search_keyword="áo thun", amount=300000)
        val deleteAction = ToolAction(
            type = ToolActionType.DELETE,
            targetTransaction = txToDelete,
            amount = 300000L,
            searchKeyword = "áo thun"
        )

        // Kiểm tra phiếu xem trước Xóa
        assertEquals(ToolActionType.DELETE, deleteAction.type)
        assertEquals(202L, deleteAction.targetTransaction?.id)
        assertEquals(300000L, deleteAction.amount)

        // Bước 3: Người dùng bấm "Xác Nhận Xóa" -> Hoàn lại ngân sách
        totalExpense -= deleteAction.amount
        assertEquals(5000000L, totalExpense)
    }

    // =========================================================================
    // CHUỖI 6: THU - CHI ĐAN XEN LIÊN TỤC -> SỐ DƯ RÒNG CHUẨN XÁC
    // =========================================================================

    @Test
    fun testChain6_InterleavedIncomeAndExpenseChain() {
        var totalIncome = 0L
        var totalExpense = 0L

        // Lượt 1: Nhận lương đầu tháng (+20 triệu)
        val income1 = 20000000L
        totalIncome += income1

        // Lượt 2: Đóng tiền thuê phòng trọ (-3 triệu)
        val expense1 = 3000000L
        totalExpense += expense1

        // Lượt 3: Tiền thưởng dự án (+5 triệu)
        val income2 = 5000000L
        totalIncome += income2

        // Lượt 4: Mua sắm đồ gia dụng (-1.5 triệu)
        val expense2 = 1500000L
        totalExpense += expense2

        // Lượt 5: Đổ xăng đi lại (-100k)
        val expense3 = 100000L
        totalExpense += expense3

        // Tổng kết kiểm tra số dư ròng
        val expectedNetBalance = (20000000L + 5000000L) - (3000000L + 1500000L + 100000L)
        val actualBalance = totalIncome - totalExpense

        assertEquals(25000000L, totalIncome)
        assertEquals(4600000L, totalExpense)
        assertEquals(20400000L, actualBalance)
        assertEquals(expectedNetBalance, actualBalance)
    }

    // =========================================================================
    // CHUỖI 7: AUTO-RECOVERY NỐI TIẾP XÁC NHẬN & TRA CỨU LỊCH SỬ
    // =========================================================================

    @Test
    fun testChain7_AutoRecoveryThenConfirmThenQueryHistory() {
        // Mô phỏng tình huống người dùng nhập: "nay đóng tiền thuê nhà hết 1000k"
        val userPrompt = "nay đóng tiền thuê nhà hết 1000k"

        // Giả lập LLM "chém gió" trả về text mà quên gọi create_transaction (pendingToolAction == null)
        var pendingAction: ToolAction? = null

        // Chốt chặn Auto-Recovery được kích hoạt
        val detectedAmount = LocalToolExecutor.extractAmountFromText(userPrompt)
        val isIncome = LocalToolExecutor.isIncomeIntent(userPrompt)
        val type = if (isIncome) "INCOME" else "EXPENSE"
        val (matchedCat, _) = LocalToolExecutor.matchBestCategory(userPrompt, type, defaultCategories)

        if (pendingAction == null && detectedAmount != null && detectedAmount > 0) {
            pendingAction = ToolAction(
                type = ToolActionType.CREATE,
                amount = detectedAmount,
                transactionType = type,
                categoryId = matchedCat!!.id,
                categoryName = matchedCat.name,
                categoryIcon = matchedCat.icon,
                categoryColorHex = matchedCat.colorHex,
                note = userPrompt.take(50)
            )
        }

        assertNotNull("Auto-Recovery phải tạo được ToolAction", pendingAction)
        assertEquals(1000000L, pendingAction?.amount)
        assertEquals("Nhà ở", pendingAction?.categoryName)

        // Người dùng bấm Xác Nhận Lưu -> Tạo Transaction lưu vào DB
        val savedTx = Transaction(
            id = 303L,
            amount = pendingAction!!.amount,
            type = pendingAction.transactionType,
            categoryId = pendingAction.categoryId,
            categoryName = pendingAction.categoryName,
            categoryIcon = pendingAction.categoryIcon,
            categoryColorHex = pendingAction.categoryColorHex,
            note = pendingAction.note,
            dateEpoch = System.currentTimeMillis()
        )

        assertEquals(1000000L, savedTx.amount)
        assertEquals("Nhà ở", savedTx.categoryName)

        // Lượt tiếp theo: Người dùng nói "Xem lại khoản tiền thuê nhà vừa đóng"
        val queryHistoryPrompt = "thuê nhà"
        val isMatchedInHistory = savedTx.note.contains(queryHistoryPrompt) || savedTx.categoryName.contains(queryHistoryPrompt)
        assertTrue("Phải tìm thấy giao dịch vừa tạo trong lịch sử", isMatchedInHistory)
    }

    // =========================================================================
    // CHUỖI 8: "KHÁM BỆNH 1000K" BẮT BUỘC KHỚP SỨC KHỎE, CẤM TẠO DANH MỤC MỚI
    // =========================================================================

    @Test
    fun testChain8_KhamBenh_MustMatchHealthCategory_NotProposeNewCategory() {
        val userSentences = listOf(
            "Khám bệnh hết 1000k",
            "Hôm nay đi khám bệnh mất 1000k",
            "Tiền đi khám bệnh hết 1.000.000 đ",
            "khám bệnh định kỳ 1 triệu"
        )

        for (sentence in userSentences) {
            val amount = LocalToolExecutor.extractAmountFromText(sentence)
            val isIncome = LocalToolExecutor.isIncomeIntent(sentence)
            val type = if (isIncome) "INCOME" else "EXPENSE"
            val (matchedCat, isFallback) = LocalToolExecutor.matchBestCategory(sentence, type, defaultCategories)

            assertEquals("Phải nhận diện là Chi tiêu", "EXPENSE", type)
            assertNotNull("Phải khớp danh mục", matchedCat)
            assertEquals("BẮT BUỘC phải khớp vào danh mục Sức khỏe", "Sức khỏe", matchedCat?.name)
            assertFalse("Không được fallback về Khác", isFallback)
            assertEquals("Số tiền phải là 1.000.000 đ", 1000000L, amount)

            // Kiểm tra ToolAction tạo ra phải là CREATE với danh mục Sức khỏe, KHÔNG PHẢI CREATE_CATEGORY
            val action = ToolAction(
                type = ToolActionType.CREATE,
                amount = amount ?: 0L,
                transactionType = type,
                categoryId = matchedCat!!.id,
                categoryName = matchedCat.name,
                categoryIcon = matchedCat.icon,
                categoryColorHex = matchedCat.colorHex,
                note = "Khám bệnh"
            )

            assertEquals(ToolActionType.CREATE, action.type)
            assertEquals("Sức khỏe", action.categoryName)
            assertEquals(1000000L, action.amount)
        }
    }

    // =========================================================================
    // CHUỖI 9: CHỈNH SỬA THẺ PREVIEW CARD TRƯỚC KHI BẤM XÁC NHẬN LƯU
    // =========================================================================

    @Test
    fun testChain9_EditablePreviewCard_ModifyBeforeConfirm() {
        // Bước 1: Ban đầu AI nhận diện từ câu "Khám bệnh 1000k"
        val initialAction = ToolAction(
            type = ToolActionType.CREATE,
            amount = 1000000L,
            transactionType = "EXPENSE",
            categoryId = 7L,
            categoryName = "Sức khỏe",
            categoryIcon = "💊",
            categoryColorHex = "#14B8A6",
            note = "Khám bệnh"
        )

        // Bước 2: Người dùng bấm Sửa (Edit) trên thẻ Preview Card
        // Sửa số tiền từ 1.000.000 đ -> 1.250.000 đ (do có thêm tiền mua thuốc)
        // Sửa ghi chú -> "Khám bệnh + mua thuốc theo đơn"
        val editedAction = initialAction.copy(
            amount = 1250000L,
            note = "Khám bệnh + mua thuốc theo đơn"
        )

        // Kiểm tra dữ liệu đã cập nhật trên thẻ xem trước
        assertEquals(1250000L, editedAction.amount)
        assertEquals("Khám bệnh + mua thuốc theo đơn", editedAction.note)
        assertEquals("Sức khỏe", editedAction.categoryName)

        // Bước 3: Người dùng bấm "Xác Nhận Lưu"
        val savedTx = Transaction(
            id = 404L,
            amount = editedAction.amount,
            type = editedAction.transactionType,
            categoryId = editedAction.categoryId,
            categoryName = editedAction.categoryName,
            categoryIcon = editedAction.categoryIcon,
            categoryColorHex = editedAction.categoryColorHex,
            note = editedAction.note,
            dateEpoch = System.currentTimeMillis()
        )

        // Xác nhận dữ liệu lưu vào DB chính là dữ liệu đã được người dùng chỉnh sửa
        assertEquals(1250000L, savedTx.amount)
        assertEquals("Khám bệnh + mua thuốc theo đơn", savedTx.note)
        assertEquals("Sức khỏe", savedTx.categoryName)
    }

    // =========================================================================
    // CHUỖI 10: CHỈ ĐỀ XUẤT TẠO DANH MỤC MỚI KHI KHOẢN CHI QUÁ KHÁC BIỆT
    // =========================================================================

    @Test
    fun testChain10_CreateCategoryWhenTrulyNovel() {
        // Tình huống: Người dùng yêu cầu rõ hoặc có khoản chi quá khác biệt ("Nuôi thú cưng")
        // chưa từng tồn tại trong danh mục mặc định
        val novelCatName = "Nuôi thú cưng"
        val novelIcon = "🐱"
        val novelType = "EXPENSE"
        val novelBudget = 500000L

        val isAlreadyExist = defaultCategories.any { it.name.equals(novelCatName, ignoreCase = true) }
        assertFalse("Danh mục Nuôi thú cưng chưa có sẵn", isAlreadyExist)

        // AI gọi tool create_category
        val createCatAction = ToolAction(
            type = ToolActionType.CREATE_CATEGORY,
            transactionType = novelType,
            categoryName = novelCatName,
            categoryIcon = novelIcon,
            categoryColorHex = "#8B5CF6",
            categoryBudget = novelBudget,
            note = "Tạo danh mục $novelCatName"
        )

        assertEquals(ToolActionType.CREATE_CATEGORY, createCatAction.type)
        assertEquals("Nuôi thú cưng", createCatAction.categoryName)
        assertEquals("🐱", createCatAction.categoryIcon)
        assertEquals(500000L, createCatAction.categoryBudget)

        // Người dùng bấm "Xác Nhận Tạo" -> Thêm danh mục mới vào danh sách
        val newCategory = Category(
            id = 15L,
            name = createCatAction.categoryName,
            type = createCatAction.transactionType,
            icon = createCatAction.categoryIcon,
            colorHex = createCatAction.categoryColorHex,
            budget = createCatAction.categoryBudget
        )

        val updatedCategoryList = defaultCategories + newCategory
        val foundNew = updatedCategoryList.find { it.name == "Nuôi thú cưng" }
        assertNotNull("Danh mục mới phải xuất hiện trong hệ thống", foundNew)
        assertEquals("🐱", foundNew?.icon)
    }

    // =========================================================================
    // CHUỖI 11: 1 PROMPT CHỨA 2 HÀNH ĐỘNG LIÊN TIẾP (VÍ DỤ THỰC TẾ CỦA USER)
    // =========================================================================

    @Test
    fun testChain11_SinglePrompt_TwoActions_HouseRentAndBreakfast() {
        val prompt = "nay trả tiền thuê nhà hết 1000k , ăn sáng hết 20k"

        // BƯỚC 1: Phân tách đa mệnh đề từ 1 prompt duy nhất
        val clauses = LocalToolExecutor.splitMultiItemText(prompt)
        assertEquals(2, clauses.size)
        assertEquals("nay trả tiền thuê nhà hết 1000k", clauses[0])
        assertEquals("ăn sáng hết 20k", clauses[1])

        // BƯỚC 2: Trích xuất số tiền và danh mục cho từng mệnh đề
        val amount1 = LocalToolExecutor.extractAmountFromText(clauses[0])
        val amount2 = LocalToolExecutor.extractAmountFromText(clauses[1])
        assertEquals(1000000L, amount1)
        assertEquals(20000L, amount2)

        val cat1 = LocalToolExecutor.matchBestCategory(clauses[0], "EXPENSE", defaultCategories).first
        val cat2 = LocalToolExecutor.matchBestCategory(clauses[1], "EXPENSE", defaultCategories).first
        assertEquals("Nhà ở", cat1?.name)
        assertEquals("Ăn uống", cat2?.name)

        // BƯỚC 3: Sinh chuỗi 2 Action Tools độc lập
        val action1 = ToolAction(
            type = ToolActionType.CREATE,
            amount = amount1!!,
            transactionType = "EXPENSE",
            categoryId = cat1!!.id,
            categoryName = cat1.name,
            categoryIcon = cat1.icon,
            note = "Tiền thuê nhà"
        )
        val action2 = ToolAction(
            type = ToolActionType.CREATE,
            amount = amount2!!,
            transactionType = "EXPENSE",
            categoryId = cat2!!.id,
            categoryName = cat2.name,
            categoryIcon = cat2.icon,
            note = "Ăn sáng"
        )

        val message = ChatMessage(
            sender = MessageSender.ASSISTANT,
            text = "Mình đã nhận diện 2 khoản chi tiêu của bạn",
            toolAction = action1,
            toolActions = listOf(action1, action2),
            cardStatus = CardStatus.PENDING
        )

        assertEquals(2, message.allToolActions.size)
        assertEquals(1020000L, message.allToolActions.sumOf { it.amount })

        // BƯỚC 4: Người dùng bấm "Xác Nhận Lưu Tất Cả (2 Khoản)"
        val savedTransactions = message.allToolActions.map { act ->
            Transaction(
                id = 0L,
                amount = act.amount,
                type = act.transactionType,
                categoryId = act.categoryId,
                categoryName = act.categoryName,
                categoryIcon = act.categoryIcon,
                note = act.note,
                dateEpoch = System.currentTimeMillis()
            )
        }

        assertEquals(2, savedTransactions.size)
        assertEquals(1000000L, savedTransactions[0].amount)
        assertEquals("Nhà ở", savedTransactions[0].categoryName)
        assertEquals(20000L, savedTransactions[1].amount)
        assertEquals("Ăn uống", savedTransactions[1].categoryName)

        val confirmedMessage = message.copy(cardStatus = CardStatus.CONFIRMED)
        assertEquals(CardStatus.CONFIRMED, confirmedMessage.cardStatus)
    }

    // =========================================================================
    // CHUỖI 12: 1 PROMPT ĐAN XEN THU NHẬP VÀ NHIỀU KHOẢN CHI TIÊU
    // =========================================================================

    @Test
    fun testChain12_SinglePrompt_MixedIncomeAndMultipleExpenses() {
        val prompt = "hôm nay nhận tiền thưởng 2tr , ăn trưa 40k , đổ xăng 60k và mua áo 150k"

        val clauses = LocalToolExecutor.splitMultiItemText(prompt)
        assertEquals(4, clauses.size)

        val actions = clauses.map { clause ->
            val amount = LocalToolExecutor.extractAmountFromText(clause) ?: 0L
            val isIncome = LocalToolExecutor.isIncomeIntent(clause)
            val type = if (isIncome) "INCOME" else "EXPENSE"
            val cat = LocalToolExecutor.matchBestCategory(clause, type, defaultCategories).first
                ?: defaultCategories.first { it.type == type }
            val note = AiService.cleanNote("", clause, cat.name)

            ToolAction(
                type = ToolActionType.CREATE,
                amount = amount,
                transactionType = type,
                categoryId = cat.id,
                categoryName = cat.name,
                categoryIcon = cat.icon,
                note = note
            )
        }

        assertEquals(4, actions.size)

        // Khoản 1: Thu nhập 2tr
        assertEquals("INCOME", actions[0].transactionType)
        assertEquals(2000000L, actions[0].amount)

        // Khoản 2: Ăn trưa 40k
        assertEquals("EXPENSE", actions[1].transactionType)
        assertEquals(40000L, actions[1].amount)
        assertEquals("Ăn uống", actions[1].categoryName)

        // Khoản 3: Đổ xăng 60k
        assertEquals("EXPENSE", actions[2].transactionType)
        assertEquals(60000L, actions[2].amount)
        assertEquals("Đi lại", actions[2].categoryName)

        // Khoản 4: Mua áo 150k
        assertEquals("EXPENSE", actions[3].transactionType)
        assertEquals(150000L, actions[3].amount)
        assertEquals("Mua sắm", actions[3].categoryName)

        // Tính biến động số dư ròng từ 1 prompt: +2.000.000 - 40.000 - 60.000 - 150.000 = +1.750.000 đ
        val netChange = actions.sumOf { if (it.transactionType == "INCOME") it.amount else -it.amount }
        assertEquals(1750000L, netChange)
    }

    // =========================================================================
    // CHUỖI 13: 1 PROMPT CHỨA TỐI ĐA 10 HÀNH ĐỘNG (MAX 10 ACTIONS CAP)
    // =========================================================================

    @Test
    fun testChain13_SinglePrompt_MaxTenActionsCap() {
        val prompt = "tiền nhà 1000k, ăn sáng 20k, đổ xăng 50k, cafe 35k, mua áo 150k, xem phim 90k, mua trà sữa 45k, cắt tóc 60k, rửa xe 30k, nạp điện thoại 100k, gửi xe 5k"
        // Tổng cộng có 11 khoản

        // Tối đa 10 khoản được bóc tách
        val clauses = LocalToolExecutor.splitMultiItemText(prompt)
        assertEquals(10, clauses.size)

        val amounts = clauses.mapNotNull { LocalToolExecutor.extractAmountFromText(it) }
        assertEquals(listOf(1000000L, 20000L, 50000L, 35000L, 150000L, 90000L, 45000L, 60000L, 30000L, 100000L), amounts)
        assertEquals(1580000L, amounts.sum())
    }

    // =========================================================================
    // CHUỖI 14: 1 PROMPT ĐA HÀNH ĐỘNG CÓ CHỈNH SỬA THẺ TRƯỚC KHI LƯU TẤT CẢ
    // =========================================================================

    @Test
    fun testChain14_SinglePrompt_EditIndividualCardBeforeSaveAll() {
        val prompt = "ăn sáng 20k , đổ xăng 50k"
        val clauses = LocalToolExecutor.splitMultiItemText(prompt)

        val action1 = ToolAction(type = ToolActionType.CREATE, amount = 20000L, categoryName = "Ăn uống", note = "Ăn sáng")
        val action2 = ToolAction(type = ToolActionType.CREATE, amount = 50000L, categoryName = "Đi lại", note = "Đổ xăng")

        var message = ChatMessage(
            sender = MessageSender.ASSISTANT,
            text = "Đã nhận diện 2 khoản",
            toolAction = action1,
            toolActions = listOf(action1, action2)
        )

        // Người dùng phát hiện ăn sáng gọi thêm trứng nên sửa Thẻ 1 từ 20k thành 25k
        val editedAction1 = action1.copy(amount = 25000L, note = "Ăn sáng có thêm trứng")
        val updatedList = message.allToolActions.toMutableList().apply { this[0] = editedAction1 }
        message = message.copy(toolActions = updatedList)

        assertEquals(25000L, message.allToolActions[0].amount)
        assertEquals("Ăn sáng có thêm trứng", message.allToolActions[0].note)
        assertEquals(50000L, message.allToolActions[1].amount)

        // Sau đó người dùng bấm "Xác Nhận Lưu Tất Cả"
        val totalToSave = message.allToolActions.sumOf { it.amount }
        assertEquals(75000L, totalToSave)
    }

    // =========================================================================
    // CHUỖI 15: AI NHẬN BIẾT RÕ RÀNG TRẠNG THÁI NGƯỜI DÙNG ĐÃ XÁC NHẬN HAY CHƯA
    // =========================================================================

    @Test
    fun testChain15_ConversationHistoryIncludesConfirmationStatus() {
        val action1 = ToolAction(type = ToolActionType.CREATE, amount = 1000000L, categoryName = "Nhà ở", note = "Tiền thuê nhà")
        val action2 = ToolAction(type = ToolActionType.CREATE, amount = 20000L, categoryName = "Ăn uống", note = "Ăn sáng")

        // 1. Tin nhắn User ban đầu
        val msgUser1 = ChatMessage(sender = MessageSender.USER, text = "nay trả tiền thuê nhà hết 1000k , ăn sáng hết 20k")

        // 2. Bot đề xuất và người dùng bấm "Xác Nhận Lưu" -> Trạng thái chuyển sang CONFIRMED
        val msgBot = ChatMessage(
            sender = MessageSender.ASSISTANT,
            text = "Mình đã nhận diện 2 khoản chi tiêu của bạn",
            toolActions = listOf(action1, action2),
            cardStatus = CardStatus.CONFIRMED
        )

        // 3. Hệ thống sinh thông báo đã lưu vào DB
        val msgSystem = ChatMessage(
            sender = MessageSender.SYSTEM,
            text = "✓ Đã ghi vào sổ 2 khoản thành công: Nhà ở 1.000.000 đ, Ăn uống 20.000 đ"
        )

        // 4. Lượt chat tiếp theo của người dùng: "vừa nãy mình lưu những khoản nào?"
        val history = listOf(msgUser1, msgBot, msgSystem)

        // Mô phỏng hàm nạp context của AiService:
        val parsedContext = history.map { msg ->
            when (msg.sender) {
                MessageSender.USER -> "USER: ${msg.text}"
                MessageSender.ASSISTANT -> {
                    val statusStr = when (msg.cardStatus) {
                        CardStatus.CONFIRMED -> "[HỆ THỐNG: Người dùng ĐÃ BẤM XÁC NHẬN LƯU thành công vào cơ sở dữ liệu]"
                        CardStatus.CANCELLED -> "[HỆ THỐNG: Người dùng ĐÃ BẤM HỦY BỎ đề xuất này]"
                        CardStatus.PENDING -> "[HỆ THỐNG: Phiếu đề xuất đang ở trạng thái CHỜ]"
                    }
                    "ASSISTANT: ${msg.text}\n$statusStr"
                }
                MessageSender.SYSTEM -> "SYSTEM: ${msg.text}"
            }
        }

        // Kiểm tra xem AI có đọc được trạng thái CONFIRMED và thông báo SYSTEM không
        assertTrue("AI phải đọc được trạng thái đã bấm xác nhận", parsedContext[1].contains("ĐÃ BẤM XÁC NHẬN LƯU"))
        assertTrue("AI phải đọc được tin nhắn hệ thống ghi nhận thành công", parsedContext[2].contains("✓ Đã ghi vào sổ 2 khoản"))
    }

    // =========================================================================
    // CHUỖI 16: HỦY RIÊNG LẺ 1 THẺ TRONG DANH SÁCH KHÔNG LÀM HỦY TOÀN BỘ CÁC THẺ KHÁC
    // =========================================================================

    @Test
    fun testChain16_CancelSingleCardInList_OtherCardsRemainPending() {
        val action1 = ToolAction(type = ToolActionType.CREATE, amount = 200000L, categoryName = "Ăn uống", note = "Ăn sáng")
        val action2 = ToolAction(type = ToolActionType.CREATE, amount = 100000L, categoryName = "Đi lại", note = "Đổ xăng")
        val action3 = ToolAction(type = ToolActionType.CREATE, amount = 1000000L, categoryName = "Nhà ở", note = "Đóng tiền điện")

        val initialMessage = ChatMessage(
            id = "msg-123",
            sender = MessageSender.ASSISTANT,
            text = "Mình đã chuẩn bị 3 phiếu bên dưới",
            toolActions = listOf(action1, action2, action3),
            cardStatus = CardStatus.PENDING
        )

        // Mô phỏng logic cancelToolAction với actionIndex = 1 (hủy riêng khoản Đổ xăng)
        val currentList = initialMessage.allToolActions.toMutableList()
        val actionIndexToCancel = 1
        currentList[actionIndexToCancel] = currentList[actionIndexToCancel].copy(status = CardStatus.CANCELLED)
        val allCancelled = currentList.all { it.status == CardStatus.CANCELLED }
        val hasPending = currentList.any { it.status == CardStatus.PENDING }
        val newCardStatus = if (allCancelled) CardStatus.CANCELLED else if (hasPending) CardStatus.PENDING else CardStatus.CONFIRMED

        val updatedMessage = initialMessage.copy(
            toolAction = currentList.firstOrNull(),
            toolActions = currentList,
            cardStatus = newCardStatus
        )

        // 1. Thẻ 2 (Đổ xăng) phải ở trạng thái CANCELLED
        assertEquals(CardStatus.CANCELLED, updatedMessage.allToolActions[1].status)

        // 2. Thẻ 1 (Ăn sáng) và Thẻ 3 (Đóng tiền điện) PHẢI VẪN Ở TRẠNG THÁI PENDING!
        assertEquals(CardStatus.PENDING, updatedMessage.allToolActions[0].status)
        assertEquals(CardStatus.PENDING, updatedMessage.allToolActions[2].status)

        // 3. Toàn bộ tin nhắn vẫn còn action PENDING -> cardStatus vẫn là PENDING
        assertEquals(CardStatus.PENDING, updatedMessage.cardStatus)

        // 4. Số thẻ pending còn lại là 2 thẻ
        val pendingCount = updatedMessage.allToolActions.count { it.status == CardStatus.PENDING }
        assertEquals(2, pendingCount)

        // 5. Tổng số tiền các thẻ pending còn lại được cập nhật chính xác (200k + 1tr = 1.200k, không còn 100k đổ xăng)
        val pendingTotal = updatedMessage.allToolActions
            .filter { it.status == CardStatus.PENDING && it.type == ToolActionType.CREATE }
            .sumOf { it.amount }
        assertEquals(1200000L, pendingTotal)
    }

    @Test
    fun testChain17_ConfirmAll_OnlyExecutesPendingActions() {
        val action1 = ToolAction(type = ToolActionType.CREATE, amount = 200000L, categoryName = "Ăn uống", note = "Ăn sáng", status = CardStatus.PENDING)
        val action2 = ToolAction(type = ToolActionType.CREATE, amount = 100000L, categoryName = "Đi lại", note = "Đổ xăng", status = CardStatus.CANCELLED) // Đã hủy trước đó
        val action3 = ToolAction(type = ToolActionType.CREATE, amount = 1000000L, categoryName = "Nhà ở", note = "Đóng tiền điện", status = CardStatus.PENDING)

        val message = ChatMessage(
            id = "msg-124",
            sender = MessageSender.ASSISTANT,
            text = "Test confirm all",
            toolActions = listOf(action1, action2, action3),
            cardStatus = CardStatus.PENDING
        )

        // Khi bấm "Xác Nhận Lưu Tất Cả", chỉ thực thi những thẻ có status == PENDING
        val actionsToExecute = message.allToolActions.filter { it.status == CardStatus.PENDING }

        assertEquals(2, actionsToExecute.size)
        assertEquals("Ăn sáng", actionsToExecute[0].note)
        assertEquals("Đóng tiền điện", actionsToExecute[1].note)
        // Khoản Đổ xăng đã bị hủy tuyệt đối không được thực thi
        assertFalse(actionsToExecute.any { it.note == "Đổ xăng" })
    }
}
