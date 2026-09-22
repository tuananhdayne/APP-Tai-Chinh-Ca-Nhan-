package com.example.apptaichinh.data.ai

import com.example.apptaichinh.data.ai.tools.LocalToolExecutor
import com.example.apptaichinh.data.db.FinanceDatabaseHelper
import com.example.apptaichinh.data.model.Category
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.json.JSONObject

/**
 * BỘ KIỂM THỬ ĐA TÁC TỬ - MULTI-TRANSACTION PREVIEW CARDS
 * ============================================================
 * Kịch bản: "Hôm nay đổ xăng hết 40k, ăn bánh mì 100k, trả tiền thuê trọ 2 triệu"
 *
 * Kiểm tra các luồng:
 * 1. LLM nhận dạng 3 giao dịch → tạo 3 ToolAction ở trạng thái PENDING
 * 2. Hiển thị 3 Preview Card riêng lẻ có thể sửa từng thẻ
 * 3. Xác nhận từng thẻ một (Confirm Individual)
 * 4. Hủy từng thẻ một (Cancel Individual)
 * 5. Chỉnh sửa một thẻ rồi xác nhận (Edit → Confirm)
 * 6. Chấp nhận hết (Confirm All)
 * 7. Hủy hết (Cancel All)
 * 8. Tình huống hỗn hợp: Xác nhận 2, hủy 1
 * 9. Auto-Recovery: LLM hallucinate text → hệ thống tự tạo 3 Preview Cards
 * 10. Thứ tự thẻ được giữ nguyên sau khi sửa 1 thẻ bất kỳ
 */
class MultiTransactionTest {

    private val categories = FinanceDatabaseHelper.getDefaultCategoriesList()

    // ========================================================
    // HELPER: Tạo 3 ToolAction cho kịch bản chuẩn
    // ========================================================

    /** Tạo ToolAction cho khoản "đổ xăng 40k" */
    private fun buildXangAction(): ToolAction {
        val (cat, _) = LocalToolExecutor.matchBestCategory("đổ xăng", "EXPENSE", categories)
        val xangCat = cat ?: categories.first { it.type == "EXPENSE" }
        return ToolAction(
            type = ToolActionType.CREATE,
            amount = 40_000L,
            transactionType = "EXPENSE",
            categoryId = xangCat.id,
            categoryName = xangCat.name,
            categoryIcon = xangCat.icon,
            categoryColorHex = xangCat.colorHex,
            note = "Đổ xăng",
            status = CardStatus.PENDING
        )
    }

    /** Tạo ToolAction cho khoản "ăn bánh mì 100k" */
    private fun buildBanhMiAction(): ToolAction {
        val (cat, _) = LocalToolExecutor.matchBestCategory("ăn bánh mì", "EXPENSE", categories)
        val banhMiCat = cat ?: categories.first { it.type == "EXPENSE" }
        return ToolAction(
            type = ToolActionType.CREATE,
            amount = 100_000L,
            transactionType = "EXPENSE",
            categoryId = banhMiCat.id,
            categoryName = banhMiCat.name,
            categoryIcon = banhMiCat.icon,
            categoryColorHex = banhMiCat.colorHex,
            note = "Ăn bánh mì",
            status = CardStatus.PENDING
        )
    }

    /** Tạo ToolAction cho khoản "trả tiền thuê trọ 2 triệu" */
    private fun buildThueNhaAction(): ToolAction {
        val (cat, _) = LocalToolExecutor.matchBestCategory("tiền thuê trọ", "EXPENSE", categories)
        val thoNhaCat = cat ?: categories.first { it.type == "EXPENSE" }
        return ToolAction(
            type = ToolActionType.CREATE,
            amount = 2_000_000L,
            transactionType = "EXPENSE",
            categoryId = thoNhaCat.id,
            categoryName = thoNhaCat.name,
            categoryIcon = thoNhaCat.icon,
            categoryColorHex = thoNhaCat.colorHex,
            note = "Tiền thuê trọ tháng này",
            status = CardStatus.PENDING
        )
    }

    /** Tạo ChatMessage chứa 3 action từ một tin nhắn */
    private fun buildThreeActionMessage(): ChatMessage {
        val actions = listOf(buildXangAction(), buildBanhMiAction(), buildThueNhaAction())
        return ChatMessage(
            sender = MessageSender.ASSISTANT,
            text = "Mình đã nhận diện 3 khoản và chuẩn bị sẵn các phiếu bên dưới:\n" +
                    "1. Đổ xăng: -40.000 đ\n2. Ăn bánh mì: -100.000 đ\n3. Tiền thuê trọ: -2.000.000 đ",
            toolActions = actions,
            cardStatus = CardStatus.PENDING
        )
    }

    // ========================================================
    // KIỂM THỬ 1: Nhận dạng đúng 3 khoản chi từ 1 tin nhắn
    // ========================================================

    @Test
    fun test01_ThreeTransactionsDetected_FromSingleMessage() {
        val userMessage = "Hôm nay đổ xăng hết 40k, ăn bánh mì 100k, trả tiền thuê trọ 2 triệu"
        val clauses = LocalToolExecutor.splitMultiItemText(userMessage)

        // Phải tách được ít nhất 3 mệnh đề
        assertTrue("Phải tách được ít nhất 3 mệnh đề", clauses.size >= 3)

        val amounts = clauses.mapNotNull { LocalToolExecutor.extractAmountFromText(it) }
        assertTrue("Phải nhận dạng được ít nhất 3 số tiền", amounts.size >= 3)
        assertTrue("Phải có 40.000đ", amounts.any { it == 40_000L })
        assertTrue("Phải có 100.000đ", amounts.any { it == 100_000L })
        assertTrue("Phải có 2.000.000đ", amounts.any { it == 2_000_000L })
    }

    // ========================================================
    // KIỂM THỬ 2: 3 Preview Card đều ở trạng thái PENDING
    // ========================================================

    @Test
    fun test02_AllThreeCards_InitiallyPending() {
        val msg = buildThreeActionMessage()

        assertEquals("Phải có đúng 3 toolActions", 3, msg.allToolActions.size)
        msg.allToolActions.forEachIndexed { i, action ->
            assertEquals("Thẻ $i phải ở trạng thái PENDING", CardStatus.PENDING, action.status)
        }
        assertEquals("Trạng thái tổng phải là PENDING", CardStatus.PENDING, msg.effectiveStatus)
    }

    // ========================================================
    // KIỂM THỬ 3: Xác nhận từng thẻ một (Confirm Individual)
    // ========================================================

    @Test
    fun test03_ConfirmIndividualCard_FirstCard() {
        val msg = buildThreeActionMessage()
        val actions = msg.allToolActions.toMutableList()

        // Xác nhận thẻ đầu tiên (xăng - index 0)
        actions[0] = actions[0].copy(status = CardStatus.CONFIRMED)
        val updatedMsg = msg.copy(toolActions = actions)

        assertEquals("Thẻ 0 (xăng) phải CONFIRMED", CardStatus.CONFIRMED, updatedMsg.allToolActions[0].status)
        assertEquals("Thẻ 1 (bánh mì) vẫn PENDING", CardStatus.PENDING, updatedMsg.allToolActions[1].status)
        assertEquals("Thẻ 2 (thuê trọ) vẫn PENDING", CardStatus.PENDING, updatedMsg.allToolActions[2].status)

        // effectiveStatus phải vẫn PENDING vì còn thẻ chưa xử lý
        assertEquals("effectiveStatus phải PENDING", CardStatus.PENDING, updatedMsg.effectiveStatus)
    }

    @Test
    fun test03b_ConfirmIndividualCard_MiddleCard() {
        val msg = buildThreeActionMessage()
        val actions = msg.allToolActions.toMutableList()

        // Xác nhận thẻ thứ 2 (bánh mì - index 1) trước
        actions[1] = actions[1].copy(status = CardStatus.CONFIRMED)
        val updatedMsg = msg.copy(toolActions = actions)

        assertEquals("Thẻ 0 (xăng) vẫn PENDING", CardStatus.PENDING, updatedMsg.allToolActions[0].status)
        assertEquals("Thẻ 1 (bánh mì) phải CONFIRMED", CardStatus.CONFIRMED, updatedMsg.allToolActions[1].status)
        assertEquals("Thẻ 2 (thuê trọ) vẫn PENDING", CardStatus.PENDING, updatedMsg.allToolActions[2].status)
        assertEquals("effectiveStatus phải PENDING", CardStatus.PENDING, updatedMsg.effectiveStatus)
    }

    // ========================================================
    // KIỂM THỬ 4: Hủy từng thẻ một (Cancel Individual)
    // ========================================================

    @Test
    fun test04_CancelIndividualCard_FirstCard() {
        val msg = buildThreeActionMessage()
        val actions = msg.allToolActions.toMutableList()

        // Hủy thẻ đầu tiên (xăng - index 0)
        actions[0] = actions[0].copy(status = CardStatus.CANCELLED)
        val updatedMsg = msg.copy(toolActions = actions)

        assertEquals("Thẻ 0 (xăng) phải CANCELLED", CardStatus.CANCELLED, updatedMsg.allToolActions[0].status)
        assertEquals("Thẻ 1 (bánh mì) vẫn PENDING", CardStatus.PENDING, updatedMsg.allToolActions[1].status)
        assertEquals("Thẻ 2 (thuê trọ) vẫn PENDING", CardStatus.PENDING, updatedMsg.allToolActions[2].status)

        // effectiveStatus vẫn PENDING vì còn thẻ chưa xử lý
        assertEquals("effectiveStatus phải PENDING (còn thẻ chưa xử lý)", CardStatus.PENDING, updatedMsg.effectiveStatus)
    }

    @Test
    fun test04b_CancelIndividualCard_LastCard() {
        val msg = buildThreeActionMessage()
        val actions = msg.allToolActions.toMutableList()

        // Hủy thẻ cuối (thuê trọ - index 2)
        actions[2] = actions[2].copy(status = CardStatus.CANCELLED)
        val updatedMsg = msg.copy(toolActions = actions)

        assertEquals("Thẻ 0 (xăng) vẫn PENDING", CardStatus.PENDING, updatedMsg.allToolActions[0].status)
        assertEquals("Thẻ 1 (bánh mì) vẫn PENDING", CardStatus.PENDING, updatedMsg.allToolActions[1].status)
        assertEquals("Thẻ 2 (thuê trọ) phải CANCELLED", CardStatus.CANCELLED, updatedMsg.allToolActions[2].status)
        assertEquals("effectiveStatus phải PENDING", CardStatus.PENDING, updatedMsg.effectiveStatus)
    }

    // ========================================================
    // KIỂM THỬ 5: Chỉnh sửa một thẻ → xác nhận thẻ đó
    // ========================================================

    @Test
    fun test05_EditAmountThenConfirm_SecondCard() {
        val msg = buildThreeActionMessage()
        val actions = msg.allToolActions.toMutableList()

        // Người dùng sửa số tiền bánh mì từ 100k thành 85k
        val editedAction = actions[1].copy(amount = 85_000L)
        actions[1] = editedAction

        // Kiểm tra số tiền đã được cập nhật
        assertEquals("Số tiền bánh mì phải là 85.000đ", 85_000L, actions[1].amount)
        assertEquals("Thẻ vẫn phải PENDING sau khi sửa", CardStatus.PENDING, actions[1].status)

        // Người dùng xác nhận sau khi sửa
        actions[1] = actions[1].copy(status = CardStatus.CONFIRMED)
        val updatedMsg = msg.copy(toolActions = actions)

        assertEquals("Thẻ 1 (bánh mì sửa) phải CONFIRMED", CardStatus.CONFIRMED, updatedMsg.allToolActions[1].status)
        assertEquals("Số tiền sửa phải được giữ nguyên", 85_000L, updatedMsg.allToolActions[1].amount)
        assertEquals("effectiveStatus phải PENDING (còn 2 thẻ chưa xử lý)", CardStatus.PENDING, updatedMsg.effectiveStatus)
    }

    @Test
    fun test05b_EditNote_ThirdCard() {
        val msg = buildThreeActionMessage()
        val actions = msg.allToolActions.toMutableList()

        // Người dùng sửa ghi chú thẻ thuê trọ
        val editedAction = actions[2].copy(note = "Thuê trọ tháng 9/2026")
        actions[2] = editedAction

        assertEquals("Ghi chú phải được cập nhật", "Thuê trọ tháng 9/2026", actions[2].note)
        assertEquals("Số tiền thuê trọ không đổi", 2_000_000L, actions[2].amount)
    }

    // ========================================================
    // KIỂM THỬ 6: Chấp nhận hết (Confirm All)
    // ========================================================

    @Test
    fun test06_ConfirmAll_AllCardsBecomeConfirmed() {
        val msg = buildThreeActionMessage()
        val actions = msg.allToolActions.toMutableList()

        // Simulate "Confirm All" - set tất cả PENDING thành CONFIRMED
        val confirmedActions = actions.map { action ->
            if (action.status == CardStatus.PENDING) action.copy(status = CardStatus.CONFIRMED)
            else action
        }
        val updatedMsg = msg.copy(
            toolActions = confirmedActions,
            cardStatus = CardStatus.CONFIRMED
        )

        // Kiểm tra tất cả thẻ đều CONFIRMED
        updatedMsg.allToolActions.forEachIndexed { i, action ->
            assertEquals("Thẻ $i phải CONFIRMED", CardStatus.CONFIRMED, action.status)
        }
        assertEquals("effectiveStatus phải CONFIRMED", CardStatus.CONFIRMED, updatedMsg.effectiveStatus)

        // Tổng số tiền phải đúng = 40k + 100k + 2tr = 2.140.000đ
        val totalAmount = updatedMsg.allToolActions.sumOf { it.amount }
        assertEquals("Tổng tiền phải là 2.140.000đ", 2_140_000L, totalAmount)
    }

    @Test
    fun test06b_ConfirmAll_OnlyPendingCardsAreConfirmed_WhenSomeCancelled() {
        val msg = buildThreeActionMessage()
        val actions = msg.allToolActions.toMutableList()

        // Hủy thẻ xăng trước
        actions[0] = actions[0].copy(status = CardStatus.CANCELLED)

        // Sau đó nhấn "Confirm All" → chỉ confirm các thẻ còn PENDING
        val afterConfirmAll = actions.map { action ->
            if (action.status == CardStatus.PENDING) action.copy(status = CardStatus.CONFIRMED)
            else action
        }
        val updatedMsg = msg.copy(toolActions = afterConfirmAll)

        assertEquals("Thẻ 0 (xăng) vẫn CANCELLED", CardStatus.CANCELLED, updatedMsg.allToolActions[0].status)
        assertEquals("Thẻ 1 (bánh mì) phải CONFIRMED", CardStatus.CONFIRMED, updatedMsg.allToolActions[1].status)
        assertEquals("Thẻ 2 (thuê trọ) phải CONFIRMED", CardStatus.CONFIRMED, updatedMsg.allToolActions[2].status)

        // effectiveStatus: không còn PENDING, có CONFIRMED nên = CONFIRMED
        assertEquals("effectiveStatus phải CONFIRMED", CardStatus.CONFIRMED, updatedMsg.effectiveStatus)
    }

    // ========================================================
    // KIỂM THỬ 7: Hủy hết (Cancel All)
    // ========================================================

    @Test
    fun test07_CancelAll_AllCardsBecomeCanCelled() {
        val msg = buildThreeActionMessage()
        val actions = msg.allToolActions.toMutableList()

        // Simulate "Cancel All"
        val cancelledActions = actions.map { it.copy(status = CardStatus.CANCELLED) }
        val updatedMsg = msg.copy(
            toolActions = cancelledActions,
            cardStatus = CardStatus.CANCELLED
        )

        updatedMsg.allToolActions.forEachIndexed { i, action ->
            assertEquals("Thẻ $i phải CANCELLED", CardStatus.CANCELLED, action.status)
        }
        assertEquals("effectiveStatus phải CANCELLED", CardStatus.CANCELLED, updatedMsg.effectiveStatus)
    }

    @Test
    fun test07b_CancelAll_WhenSomeAlreadyConfirmed() {
        val msg = buildThreeActionMessage()
        val actions = msg.allToolActions.toMutableList()

        // Xác nhận thẻ xăng trước
        actions[0] = actions[0].copy(status = CardStatus.CONFIRMED)

        // Nhấn Cancel All → chỉ cancel các thẻ PENDING (CONFIRMED không bị hủy)
        val afterCancelAll = actions.map { action ->
            if (action.status == CardStatus.PENDING) action.copy(status = CardStatus.CANCELLED)
            else action // CONFIRMED không bị hủy
        }
        val updatedMsg = msg.copy(toolActions = afterCancelAll)

        assertEquals("Thẻ 0 (xăng) vẫn CONFIRMED (đã lưu)", CardStatus.CONFIRMED, updatedMsg.allToolActions[0].status)
        assertEquals("Thẻ 1 (bánh mì) phải CANCELLED", CardStatus.CANCELLED, updatedMsg.allToolActions[1].status)
        assertEquals("Thẻ 2 (thuê trọ) phải CANCELLED", CardStatus.CANCELLED, updatedMsg.allToolActions[2].status)
    }

    // ========================================================
    // KIỂM THỬ 8: Tình huống hỗn hợp
    // ========================================================

    @Test
    fun test08_MixedActions_ConfirmTwoAndCancelOne() {
        val msg = buildThreeActionMessage()
        val actions = msg.allToolActions.toMutableList()

        // Xác nhận xăng + thuê trọ, hủy bánh mì
        actions[0] = actions[0].copy(status = CardStatus.CONFIRMED)
        actions[1] = actions[1].copy(status = CardStatus.CANCELLED)
        actions[2] = actions[2].copy(status = CardStatus.CONFIRMED)

        val updatedMsg = msg.copy(toolActions = actions)

        assertEquals("Thẻ 0 (xăng) CONFIRMED", CardStatus.CONFIRMED, updatedMsg.allToolActions[0].status)
        assertEquals("Thẻ 1 (bánh mì) CANCELLED", CardStatus.CANCELLED, updatedMsg.allToolActions[1].status)
        assertEquals("Thẻ 2 (thuê trọ) CONFIRMED", CardStatus.CONFIRMED, updatedMsg.allToolActions[2].status)

        // effectiveStatus: không còn PENDING, có CONFIRMED nên là CONFIRMED
        assertEquals("effectiveStatus phải CONFIRMED", CardStatus.CONFIRMED, updatedMsg.effectiveStatus)

        // Tổng tiền ghi sổ thực tế = chỉ CONFIRMED actions
        val savedAmount = updatedMsg.allToolActions
            .filter { it.status == CardStatus.CONFIRMED }
            .sumOf { it.amount }
        assertEquals("Chỉ ghi xăng + thuê trọ = 2.040.000đ", 2_040_000L, savedAmount)
    }

    @Test
    fun test08b_MixedActions_Sequential_OneByOne() {
        val msg = buildThreeActionMessage()
        var actions = msg.allToolActions.toMutableList()

        // Bước 1: Xác nhận thẻ xăng
        actions[0] = actions[0].copy(status = CardStatus.CONFIRMED)
        val afterStep1 = msg.copy(toolActions = actions)
        assertEquals("Sau bước 1: effectiveStatus PENDING", CardStatus.PENDING, afterStep1.effectiveStatus)

        // Bước 2: Hủy thẻ bánh mì
        actions = afterStep1.allToolActions.toMutableList()
        actions[1] = actions[1].copy(status = CardStatus.CANCELLED)
        val afterStep2 = afterStep1.copy(toolActions = actions)
        assertEquals("Sau bước 2: effectiveStatus PENDING", CardStatus.PENDING, afterStep2.effectiveStatus)

        // Bước 3: Xác nhận thẻ thuê trọ
        actions = afterStep2.allToolActions.toMutableList()
        actions[2] = actions[2].copy(status = CardStatus.CONFIRMED)
        val afterStep3 = afterStep2.copy(toolActions = actions)
        assertEquals("Sau bước 3: effectiveStatus CONFIRMED (không còn PENDING)", CardStatus.CONFIRMED, afterStep3.effectiveStatus)
    }

    // ========================================================
    // KIỂM THỬ 9: Auto-Recovery khi LLM hallucinate text
    // ========================================================

    @Test
    fun test09_AutoRecovery_WhenLlmForgetsToolCalls() {
        val userMessage = "Hôm nay đổ xăng hết 40k, ăn bánh mì 100k, trả tiền thuê trọ 2 triệu"

        // Giả sử LLM trả về text thuần thay vì gọi tool (hallucinate)
        val pendingActions = mutableListOf<ToolAction>()
        // pendingToolActions.isEmpty() → kích hoạt Auto-Recovery

        val clauses = LocalToolExecutor.splitMultiItemText(userMessage)
        val lowerMsg = userMessage.lowercase()
        val isQueryIntent = lowerMsg.contains("hết bao nhiêu") || lowerMsg.contains("bao nhiêu tiền") ||
                lowerMsg.contains("vượt chưa") || lowerMsg.contains("còn bao nhiêu") ||
                lowerMsg.contains("xem lại") || lowerMsg.contains("tìm") || lowerMsg.contains("kiểm tra")

        assertFalse("Không phải query intent", isQueryIntent)

        // Mô phỏng Auto-Recovery loop
        for (clause in clauses.take(6)) {
            val detectedAmount = LocalToolExecutor.extractAmountFromText(clause)
            if (detectedAmount != null && detectedAmount > 0) {
                val isIncome = LocalToolExecutor.isIncomeIntent(clause)
                val type = if (isIncome) "INCOME" else "EXPENSE"
                val (matchedCat, _) = LocalToolExecutor.matchBestCategory(clause, type, categories)
                val cat = matchedCat ?: categories.find { it.type == type }
                    ?: Category(0, "Khác", type, "📦", "#607D8B", 0L)

                pendingActions.add(
                    ToolAction(
                        type = ToolActionType.CREATE,
                        amount = detectedAmount,
                        transactionType = type,
                        categoryId = cat.id,
                        categoryName = cat.name,
                        categoryIcon = cat.icon,
                        categoryColorHex = cat.colorHex,
                        note = clause.trim()
                    )
                )
            }
        }

        // Phải tạo được 3 ToolAction từ Auto-Recovery
        assertTrue("Auto-Recovery phải tạo ít nhất 3 action", pendingActions.size >= 3)

        val amounts = pendingActions.map { it.amount }
        assertTrue("Auto-Recovery phải nhận 40.000đ", amounts.any { it == 40_000L })
        assertTrue("Auto-Recovery phải nhận 100.000đ", amounts.any { it == 100_000L })
        assertTrue("Auto-Recovery phải nhận 2.000.000đ", amounts.any { it == 2_000_000L })

        pendingActions.forEach { action ->
            assertFalse("Không có action nào là INCOME (đây là chi tiêu)", action.transactionType == "INCOME")
            assertEquals("Tất cả action phải ở trạng thái PENDING", CardStatus.PENDING, action.status)
        }
    }

    // ========================================================
    // KIỂM THỬ 10: Thứ tự thẻ giữ nguyên sau khi sửa
    // ========================================================

    @Test
    fun test10_CardOrder_PreservedAfterEdit() {
        val msg = buildThreeActionMessage()
        val actions = msg.allToolActions

        // Xác nhận thứ tự ban đầu
        assertEquals("Thẻ 0 là xăng", 40_000L, actions[0].amount)
        assertEquals("Thẻ 1 là bánh mì", 100_000L, actions[1].amount)
        assertEquals("Thẻ 2 là thuê trọ", 2_000_000L, actions[2].amount)

        // Sửa thẻ giữa (bánh mì)
        val updatedActions = actions.toMutableList()
        updatedActions[1] = updatedActions[1].copy(amount = 75_000L, note = "Bánh mì + nước")
        val updatedMsg = msg.copy(toolActions = updatedActions)

        // Kiểm tra thứ tự vẫn giữ nguyên
        assertEquals("Sau sửa: Thẻ 0 vẫn là xăng (40k)", 40_000L, updatedMsg.allToolActions[0].amount)
        assertEquals("Sau sửa: Thẻ 1 bánh mì đã sửa (75k)", 75_000L, updatedMsg.allToolActions[1].amount)
        assertEquals("Sau sửa: Thẻ 2 vẫn là thuê trọ (2tr)", 2_000_000L, updatedMsg.allToolActions[2].amount)
        assertEquals("Tổng cộng 3 thẻ", 3, updatedMsg.allToolActions.size)
    }

    // ========================================================
    // KIỂM THỬ 11: effectiveStatus logic toàn diện
    // ========================================================

    @Test
    fun test11_EffectiveStatus_AllPending() {
        val msg = buildThreeActionMessage()
        assertEquals("Tất cả PENDING → effectiveStatus PENDING", CardStatus.PENDING, msg.effectiveStatus)
    }

    @Test
    fun test11b_EffectiveStatus_AllCancelled() {
        val actions = buildThreeActionMessage().allToolActions.map { it.copy(status = CardStatus.CANCELLED) }
        val msg = buildThreeActionMessage().copy(toolActions = actions)
        assertEquals("Tất cả CANCELLED → effectiveStatus CANCELLED", CardStatus.CANCELLED, msg.effectiveStatus)
    }

    @Test
    fun test11c_EffectiveStatus_AllConfirmed() {
        val actions = buildThreeActionMessage().allToolActions.map { it.copy(status = CardStatus.CONFIRMED) }
        val msg = buildThreeActionMessage().copy(toolActions = actions)
        assertEquals("Tất cả CONFIRMED → effectiveStatus CONFIRMED", CardStatus.CONFIRMED, msg.effectiveStatus)
    }

    @Test
    fun test11d_EffectiveStatus_OnePendingRemaining() {
        val actions = buildThreeActionMessage().allToolActions.toMutableList()
        actions[0] = actions[0].copy(status = CardStatus.CONFIRMED)
        actions[1] = actions[1].copy(status = CardStatus.CANCELLED)
        // actions[2] vẫn PENDING
        val msg = buildThreeActionMessage().copy(toolActions = actions)
        assertEquals("Còn 1 PENDING → effectiveStatus PENDING", CardStatus.PENDING, msg.effectiveStatus)
    }

    @Test
    fun test11e_EffectiveStatus_MixedConfirmedAndCancelled_NoPending() {
        val actions = buildThreeActionMessage().allToolActions.toMutableList()
        actions[0] = actions[0].copy(status = CardStatus.CONFIRMED)
        actions[1] = actions[1].copy(status = CardStatus.CANCELLED)
        actions[2] = actions[2].copy(status = CardStatus.CONFIRMED)
        val msg = buildThreeActionMessage().copy(toolActions = actions)
        // Không còn PENDING, có ít nhất 1 CONFIRMED → CONFIRMED
        assertEquals("Confirmed + Cancelled (không PENDING) → effectiveStatus CONFIRMED", CardStatus.CONFIRMED, msg.effectiveStatus)
    }

    // ========================================================
    // KIỂM THỬ 12: Danh mục khớp chuẩn xác với từng loại chi tiêu
    // ========================================================

    @Test
    fun test12_CategoryMatching_Xang() {
        val (cat, isFallback) = LocalToolExecutor.matchBestCategory("đổ xăng", "EXPENSE", categories)
        assertNotNull("Phải khớp danh mục xăng", cat)
        assertFalse("Không được là fallback", isFallback)
        // Phải là danh mục liên quan đến đi lại/xăng xe
        val name = cat!!.name.lowercase()
        assertTrue("Phải là danh mục đi lại/xăng",
            name.contains("đi lại") || name.contains("xăng") || name.contains("xe")
        )
    }

    @Test
    fun test12b_CategoryMatching_BanhMi() {
        val (cat, isFallback) = LocalToolExecutor.matchBestCategory("ăn bánh mì", "EXPENSE", categories)
        assertNotNull("Phải khớp danh mục ăn uống", cat)
        val name = cat!!.name.lowercase()
        assertTrue("Phải là danh mục ăn uống",
            name.contains("ăn uống") || name.contains("thực phẩm") || name.contains("ăn")
        )
    }

    @Test
    fun test12c_CategoryMatching_ThueNha() {
        val (cat, isFallback) = LocalToolExecutor.matchBestCategory("tiền thuê trọ", "EXPENSE", categories)
        assertNotNull("Phải khớp danh mục nhà ở/thuê nhà", cat)
        val name = cat!!.name.lowercase()
        assertTrue("Phải là danh mục nhà ở/thuê nhà",
            name.contains("nhà") || name.contains("thuê") || name.contains("tiện ích")
        )
    }

    // ========================================================
    // KIỂM THỬ 13: ToolAction có đầy đủ thông tin danh mục
    // ========================================================

    @Test
    fun test13_ToolAction_HasCompleteInfo() {
        val xangAction = buildXangAction()

        assertEquals("Loại phải là CREATE", ToolActionType.CREATE, xangAction.type)
        assertEquals("Số tiền phải là 40.000đ", 40_000L, xangAction.amount)
        assertEquals("Loại giao dịch phải là EXPENSE", "EXPENSE", xangAction.transactionType)
        assertTrue("categoryName không được rỗng", xangAction.categoryName.isNotBlank())
        assertTrue("categoryIcon không được rỗng", xangAction.categoryIcon.isNotBlank())
        assertTrue("categoryColorHex phải hợp lệ", xangAction.categoryColorHex.startsWith("#"))
        assertEquals("Trạng thái ban đầu phải PENDING", CardStatus.PENDING, xangAction.status)
    }

    @Test
    fun test13b_ToolAction_BanhMi_HasCompleteInfo() {
        val banhMiAction = buildBanhMiAction()

        assertEquals("Số tiền phải là 100.000đ", 100_000L, banhMiAction.amount)
        assertTrue("Ghi chú không được rỗng", banhMiAction.note.isNotBlank())
        assertEquals("Trạng thái ban đầu phải PENDING", CardStatus.PENDING, banhMiAction.status)
    }

    @Test
    fun test13c_ToolAction_ThueNha_HasCompleteInfo() {
        val thueNhaAction = buildThueNhaAction()

        assertEquals("Số tiền phải là 2.000.000đ", 2_000_000L, thueNhaAction.amount)
        assertEquals("Trạng thái ban đầu phải PENDING", CardStatus.PENDING, thueNhaAction.status)
    }

    // ========================================================
    // KIỂM THỬ 14: Tổng số tiền sau Confirm All
    // ========================================================

    @Test
    fun test14_TotalAmount_AfterConfirmAll() {
        val msg = buildThreeActionMessage()

        val totalPending = msg.allToolActions.sumOf { it.amount }
        assertEquals("Tổng 3 khoản = 2.140.000đ", 2_140_000L, totalPending)

        // Sau Confirm All
        val confirmed = msg.allToolActions.map { it.copy(status = CardStatus.CONFIRMED) }
        val totalConfirmed = confirmed.sumOf { it.amount }
        assertEquals("Tổng sau Confirm All vẫn = 2.140.000đ", 2_140_000L, totalConfirmed)
    }

    // ========================================================
    // KIỂM THỬ 15: Pending count hiển thị đúng trên banner
    // ========================================================

    @Test
    fun test15_PendingCount_ForBatchBanner() {
        val msg = buildThreeActionMessage()

        // Ban đầu: 3 thẻ PENDING → hiện banner "Xác Nhận Lưu Tất Cả (3 Khoản)"
        val pendingCount0 = msg.allToolActions.count { it.status == CardStatus.PENDING }
        assertEquals("Ban đầu: 3 thẻ PENDING", 3, pendingCount0)
        assertTrue("Khi >= 2 PENDING phải hiện banner batch", pendingCount0 >= 2)

        // Xác nhận thẻ thứ 1 → còn 2 PENDING → vẫn hiện banner
        val actions1 = msg.allToolActions.toMutableList()
        actions1[0] = actions1[0].copy(status = CardStatus.CONFIRMED)
        val msg1 = msg.copy(toolActions = actions1)
        val pendingCount1 = msg1.allToolActions.count { it.status == CardStatus.PENDING }
        assertEquals("Sau confirm thẻ 1: còn 2 thẻ PENDING", 2, pendingCount1)
        assertTrue("Còn >= 2 PENDING phải hiện banner batch", pendingCount1 >= 2)

        // Xác nhận thẻ thứ 2 → còn 1 PENDING → ẩn banner batch
        val actions2 = msg1.allToolActions.toMutableList()
        actions2[1] = actions2[1].copy(status = CardStatus.CONFIRMED)
        val msg2 = msg1.copy(toolActions = actions2)
        val pendingCount2 = msg2.allToolActions.count { it.status == CardStatus.PENDING }
        assertEquals("Sau confirm thẻ 2: còn 1 thẻ PENDING", 1, pendingCount2)
        assertFalse("Chỉ còn 1 PENDING → ẩn banner batch", pendingCount2 >= 2)
    }

    // ========================================================
    // KIỂM THỬ 16: Hỗ trợ tối đa 10 action (Test 8 action)
    // ========================================================

    @Test
    fun test16_MaxActionsSupport_8Actions() {
        // Mô phỏng người dùng nhập 8 giao dịch
        val inputText = "nay đổ xăng 50k, ăn sáng 30k, uống cafe 40k, mua trà đá 10k, ăn trưa 45k, mua bút 15k, gửi xe 5k, nạp điện thoại 100k"
        
        // Giả lập Auto-Recovery (splitMultiItemText)
        val clauses = LocalToolExecutor.splitMultiItemText(inputText)
        assertEquals("Phải tách được 8 mệnh đề", 8, clauses.size)

        // LLM tạo ra 8 action tương ứng
        val actions = mutableListOf<ToolAction>()
        for (clause in clauses) {
            val amount = LocalToolExecutor.extractAmountFromText(clause) ?: continue
            val (cat, _) = LocalToolExecutor.matchBestCategory(clause, "EXPENSE", categories)
            val matchedCat = cat ?: categories.first { it.type == "EXPENSE" }
            
            actions.add(
                ToolAction(
                    type = ToolActionType.CREATE,
                    amount = amount,
                    transactionType = "EXPENSE",
                    categoryId = matchedCat.id,
                    categoryName = matchedCat.name,
                    categoryIcon = matchedCat.icon,
                    categoryColorHex = matchedCat.colorHex,
                    note = clause
                )
            )
        }

        assertEquals("Phải tạo được 8 action", 8, actions.size)

        val msg = ChatMessage(
            sender = MessageSender.ASSISTANT,
            text = "Mình đã chuẩn bị 8 phiếu ghi chép:",
            toolAction = actions.first(),
            toolActions = actions,
            cardStatus = CardStatus.PENDING
        )

        assertEquals("Message phải chứa danh sách 8 thẻ", 8, msg.allToolActions.size)
        assertTrue("Không bị giới hạn ở 6 thẻ", msg.allToolActions.size > 6)
    }
}
