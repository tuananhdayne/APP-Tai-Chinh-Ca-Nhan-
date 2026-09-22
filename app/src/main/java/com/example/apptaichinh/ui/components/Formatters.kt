package com.example.apptaichinh.ui.components

import androidx.compose.ui.graphics.Color
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object Formatters {

    private val vndSymbols = DecimalFormatSymbols(Locale("vi", "VN")).apply {
        groupingSeparator = '.'
        decimalSeparator = ','
    }

    private val formatter = DecimalFormat("#,###", vndSymbols)

    fun formatVnd(amount: Long, showSign: Boolean = false, isIncome: Boolean = false): String {
        val formatted = formatter.format(Math.abs(amount))
        return when {
            showSign && isIncome -> "+$formatted ₫"
            showSign && !isIncome -> "-$formatted ₫"
            else -> "$formatted ₫"
        }
    }

    fun formatCompactVnd(amount: Long): String {
        val abs = Math.abs(amount)
        return when {
            abs >= 1_000_000_000 -> String.format(Locale("vi", "VN"), "%.1fB", abs / 1_000_000_000.0).replace(",0", "").replace(".0", "")
            abs >= 1_000_000 -> String.format(Locale("vi", "VN"), "%.1ftr", abs / 1_000_000.0).replace(",0", "").replace(".0", "")
            abs >= 1_000 -> "${abs / 1_000}k"
            else -> "$abs"
        }
    }

    fun formatDate(dateEpoch: Long): String {
        val nowCal = Calendar.getInstance()
        val txCal = Calendar.getInstance().apply { timeInMillis = dateEpoch }

        val isToday = nowCal.get(Calendar.YEAR) == txCal.get(Calendar.YEAR) &&
                nowCal.get(Calendar.DAY_OF_YEAR) == txCal.get(Calendar.DAY_OF_YEAR)

        val isYesterday = nowCal.get(Calendar.YEAR) == txCal.get(Calendar.YEAR) &&
                nowCal.get(Calendar.DAY_OF_YEAR) - txCal.get(Calendar.DAY_OF_YEAR) == 1

        val timeFmt = SimpleDateFormat("HH:mm", Locale("vi", "VN"))
        val dateFmt = SimpleDateFormat("dd/MM/yyyy", Locale("vi", "VN"))

        return when {
            isToday -> "Hôm nay, ${timeFmt.format(Date(dateEpoch))}"
            isYesterday -> "Hôm qua, ${timeFmt.format(Date(dateEpoch))}"
            else -> "${dateFmt.format(Date(dateEpoch))} (${timeFmt.format(Date(dateEpoch))})"
        }
    }

    fun parseColor(hex: String, defaultColor: Color = Color(0xFF607D8B)): Color {
        return try {
            val clean = hex.removePrefix("#")
            val colorLong = clean.toLong(16)
            if (clean.length == 6) {
                Color(colorLong or 0x00000000FF000000)
            } else {
                Color(colorLong)
            }
        } catch (e: Exception) {
            defaultColor
        }
    }
}
