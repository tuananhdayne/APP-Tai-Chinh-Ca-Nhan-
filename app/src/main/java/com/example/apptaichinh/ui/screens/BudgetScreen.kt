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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.apptaichinh.data.model.CategoryBudget
import com.example.apptaichinh.ui.components.CategoryBudgetCard
import com.example.apptaichinh.ui.components.MonthSelector
import com.example.apptaichinh.ui.components.OverallBudgetCard
import com.example.apptaichinh.ui.viewmodel.FinanceViewModel

@Composable
fun BudgetScreen(
    viewModel: FinanceViewModel,
    onEditOverallBudget: () -> Unit,
    onEditCategoryBudget: (CategoryBudget) -> Unit,
    modifier: Modifier = Modifier
) {
    val year by viewModel.selectedYear.collectAsState()
    val month by viewModel.selectedMonth.collectAsState()
    val overallBudget by viewModel.overallBudget.collectAsState()
    val categoryBudgets by viewModel.categoryBudgets.collectAsState()

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 90.dp, top = 8.dp, start = 16.dp, end = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
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

        // 2. Thẻ Ngân Sách Tổng Thể Cả Tháng
        item {
            OverallBudgetCard(
                budget = overallBudget,
                onEditBudget = onEditOverallBudget
            )
        }

        // 3. Section Title cho Ngân Sách Từng Danh Mục
        item {
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Ngân Sách Từng Danh Mục",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Kiểm soát chi tiết hạn mức cho mỗi nhóm chi tiêu",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
        }

        // 4. Danh sách ngân sách từng danh mục
        items(categoryBudgets, key = { it.category.id }) { catBudget ->
            CategoryBudgetCard(
                categoryBudget = catBudget,
                onEditCategoryBudget = { onEditCategoryBudget(catBudget) }
            )
        }
    }
}
