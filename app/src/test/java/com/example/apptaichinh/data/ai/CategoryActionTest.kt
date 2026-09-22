package com.example.apptaichinh.data.ai

import com.example.apptaichinh.data.model.Category
import org.junit.Assert.assertEquals
import org.junit.Test

class CategoryActionTest {

    @Test
    fun testUpdateCategoryToolAction() {
        // Giả lập LLM trả về JSON argument cho update_category
        val searchKeyword = "Ăn uống"
        val newName = "Ăn hàng"
        val newIcon = "🍔"

        val targetCat = Category(1, "Ăn uống", "EXPENSE", "🍜", "#EF4444", 3000000L)

        // Mô phỏng logic bên trong AiService.processToolAction
        val toolAction = ToolAction(
            type = ToolActionType.UPDATE_CATEGORY,
            newCategory = targetCat,
            categoryName = newName,
            categoryIcon = newIcon,
            searchKeyword = searchKeyword,
            note = "Sửa danh mục ${targetCat.name}"
        )

        assertEquals(ToolActionType.UPDATE_CATEGORY, toolAction.type)
        assertEquals("Ăn hàng", toolAction.categoryName)
        assertEquals("🍔", toolAction.categoryIcon)
        assertEquals("Ăn uống", toolAction.searchKeyword)
        assertEquals("Sửa danh mục Ăn uống", toolAction.note)
    }

    @Test
    fun testDeleteCategoryToolAction() {
        // Giả lập LLM trả về JSON argument cho delete_category
        val searchKeyword = "Thú cưng"

        val targetCat = Category(2, "Thú cưng", "EXPENSE", "🐱", "#FFC107", 500000L)

        // Mô phỏng logic bên trong AiService.processToolAction
        val toolAction = ToolAction(
            type = ToolActionType.DELETE_CATEGORY,
            newCategory = targetCat,
            searchKeyword = searchKeyword,
            note = "Xóa danh mục ${targetCat.name}"
        )

        assertEquals(ToolActionType.DELETE_CATEGORY, toolAction.type)
        assertEquals("Thú cưng", toolAction.searchKeyword)
        assertEquals("Xóa danh mục Thú cưng", toolAction.note)
    }
}
