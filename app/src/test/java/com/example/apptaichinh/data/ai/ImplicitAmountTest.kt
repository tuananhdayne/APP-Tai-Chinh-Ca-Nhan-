package com.example.apptaichinh.data.ai

import com.example.apptaichinh.data.ai.tools.LocalToolExecutor
import org.junit.Assert.assertEquals
import org.junit.Test

class ImplicitAmountTest {
    @Test
    fun testImplicitAmountExtraction() {
        val amount1 = LocalToolExecutor.extractAmountFromText("trả tiền thuê trọ hết 100")
        assertEquals(100000L, amount1)
        
        val amount2 = LocalToolExecutor.extractAmountFromText("đổ xăng 50")
        assertEquals(50000L, amount2)
        
        val amount3 = LocalToolExecutor.extractAmountFromText("nhận lương 2 triệu")
        assertEquals(2000000L, amount3)
        
        val clauses = LocalToolExecutor.splitMultiItemText("trả tiền thuê trọ hết 100 và nhận lương 2 triệu")
        assertEquals(2, clauses.size)
        assertEquals("trả tiền thuê trọ hết 100", clauses[0])
        assertEquals("nhận lương 2 triệu", clauses[1])
    }
}
