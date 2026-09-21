package com.example.apptaichinh.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import com.example.apptaichinh.data.model.Transaction
import com.example.apptaichinh.ui.components.BalanceSummaryCard
import com.example.apptaichinh.ui.components.MonthSelector
import com.example.apptaichinh.ui.components.OverallBudgetCard
import com.example.apptaichinh.ui.components.TransactionItemView
import com.example.apptaichinh.ui.viewmodel.FinanceViewModel

@Composable
fun HomeScreen(
    viewModel: FinanceViewModel,
    onEditTransaction: (Transaction) -> Unit,
    onOpenBudgetEdit: () -> Unit,
    onOpenChat: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val year by viewModel.selectedYear.collectAsState()
    val month by viewModel.selectedMonth.collectAsState()
    val summary by viewModel.monthSummary.collectAsState()
    val transactions by viewModel.transactions.collectAsState()
    val overallBudget by viewModel.overallBudget.collectAsState()
    val currentFilter by viewModel.typeFilter.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()

    var showSearch by remember { mutableStateOf(false) }

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

        // Lối tắt vào Trợ Lý AI
        item {
            Card(
                onClick = onOpenChat,
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Trợ Lý AI (Qwen 2.5)",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Nhập 'Ăn phở 45k', 'Xóa tiền phở' để tự xử lý",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        // 2. Card Tổng Quan Số Dư
        item {
            BalanceSummaryCard(summary = summary)
        }

        // 3. Card Ngân Sách Tổng Tháng
        item {
            OverallBudgetCard(
                budget = overallBudget,
                onEditBudget = onOpenBudgetEdit
            )
        }

        // 4. Thanh lọc và tìm kiếm giao dịch
        item {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Lịch Sử Giao Dịch",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    IconButton(onClick = {
                        showSearch = !showSearch
                        if (!showSearch) viewModel.setSearchQuery("")
                    }) {
                        Icon(
                            imageVector = if (showSearch) Icons.Default.Clear else Icons.Default.Search,
                            contentDescription = "Tìm kiếm",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                if (showSearch) {
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { viewModel.setSearchQuery(it) },
                        placeholder = { Text("Tìm kiếm theo ghi chú, danh mục...") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        leadingIcon = {
                            Icon(imageVector = Icons.Default.Search, contentDescription = null)
                        }
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = currentFilter == "ALL",
                        onClick = { viewModel.setFilter("ALL") },
                        label = { Text("Tất cả (${transactions.size})") }
                    )
                    FilterChip(
                        selected = currentFilter == "EXPENSE",
                        onClick = { viewModel.setFilter("EXPENSE") },
                        label = { Text("Chi tiêu") }
                    )
                    FilterChip(
                        selected = currentFilter == "INCOME",
                        onClick = { viewModel.setFilter("INCOME") },
                        label = { Text("Thu nhập") }
                    )
                }
            }
        }

        // 5. Danh sách giao dịch
        if (transactions.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 40.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(text = "📝", style = MaterialTheme.typography.headlineLarge)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Chưa có giao dịch nào",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.outline
                        )
                        Text(
                            text = "Nhấn nút (+) bên dưới để thêm chi tiêu mới nhé!",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            }
        } else {
            items(transactions, key = { it.id }) { tx ->
                TransactionItemView(
                    transaction = tx,
                    onClick = { onEditTransaction(tx) },
                    onDelete = { viewModel.deleteTransaction(tx.id) }
                )
            }
        }
    }
}
