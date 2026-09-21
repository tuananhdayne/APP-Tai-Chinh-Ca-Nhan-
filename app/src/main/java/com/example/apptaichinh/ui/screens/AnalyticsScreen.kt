package com.example.apptaichinh.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.apptaichinh.ui.components.DonutChart
import com.example.apptaichinh.ui.components.Formatters
import com.example.apptaichinh.ui.components.IncomeVsExpenseComparison
import com.example.apptaichinh.ui.components.MonthSelector
import com.example.apptaichinh.ui.components.TransactionItemView
import com.example.apptaichinh.ui.viewmodel.FinanceViewModel

@Composable
fun AnalyticsScreen(
    viewModel: FinanceViewModel,
    modifier: Modifier = Modifier
) {
    val year by viewModel.selectedYear.collectAsState()
    val month by viewModel.selectedMonth.collectAsState()
    val summary by viewModel.monthSummary.collectAsState()
    val categoryStats by viewModel.categoryStats.collectAsState()
    val transactions by viewModel.transactions.collectAsState()

    // Lấy top 5 khoản chi lớn nhất
    val topExpenses = transactions
        .filter { it.type == "EXPENSE" }
        .sortedByDescending { it.amount }
        .take(5)

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 90.dp, top = 8.dp, start = 16.dp, end = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Month Selector
        item {
            MonthSelector(
                year = year,
                month = month,
                onPrev = { viewModel.prevMonth() },
                onNext = { viewModel.nextMonth() }
            )
        }

        // 2. Biểu đồ tròn Donut Chart phân tích danh mục chi tiêu
        item {
            DonutChart(
                stats = categoryStats,
                totalExpense = summary.totalExpense
            )
        }

        // 3. Biểu đồ so sánh Thu nhập vs Chi tiêu
        item {
            IncomeVsExpenseComparison(
                income = summary.totalIncome,
                expense = summary.totalExpense
            )
        }

        // 4. Top các khoản chi tiêu lớn nhất trong tháng
        if (topExpenses.isNotEmpty()) {
            item {
                Text(
                    text = "Top Khoản Chi Lớn Nhất Tháng",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            items(topExpenses, key = { "top_${it.id}" }) { tx ->
                TransactionItemView(
                    transaction = tx,
                    onClick = {},
                    onDelete = { viewModel.deleteTransaction(tx.id) }
                )
            }
        }
    }
}
