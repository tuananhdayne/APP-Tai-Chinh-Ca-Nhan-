package com.example.apptaichinh.data.ai

import com.example.apptaichinh.data.model.Category
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenAiToolSchemasTest {

    @Test
    fun testToolDefinitionsCountAndNames() {
        val tools = OpenAiToolSchemas.getToolsJsonArray()
        assertEquals(3, tools.length())

        val toolNames = mutableListOf<String>()
        for (i in 0 until tools.length()) {
            val toolObj = tools.getJSONObject(i)
            assertEquals("function", toolObj.getString("type"))
            val functionObj = toolObj.getJSONObject("function")
            toolNames.add(functionObj.getString("name"))
        }

        assertTrue(toolNames.contains("create_transaction"))
        assertTrue(toolNames.contains("update_transaction"))
        assertTrue(toolNames.contains("delete_transaction"))
    }

    @Test
    fun testDeleteTransactionParameters() {
        val tools = OpenAiToolSchemas.getToolsJsonArray()
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
    fun testSystemPromptContainsCategories() {
        val sampleCategories = listOf(
            Category(1, "Ăn uống", "EXPENSE", "🍜", "#EF4444", 3500000L),
            Category(2, "Lương", "INCOME", "💵", "#10B981", 0L)
        )
        val prompt = OpenAiToolSchemas.buildSystemPrompt(sampleCategories)

        assertTrue(prompt.contains("Ăn uống"))
        assertTrue(prompt.contains("Lương"))
        assertTrue(prompt.contains("create_transaction"))
        assertTrue(prompt.contains("update_transaction"))
        assertTrue(prompt.contains("delete_transaction"))
    }
}
