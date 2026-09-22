package com.example.apptaichinh.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.apptaichinh.data.model.Transaction
import com.example.apptaichinh.ui.components.Formatters
import com.example.apptaichinh.ui.components.TransactionItemView
import com.example.apptaichinh.ui.viewmodel.FinanceViewModel
import java.util.Calendar

@Composable
fun CalendarScreen(
    viewModel: FinanceViewModel,
    onEditTransaction: (Transaction) -> Unit,
    modifier: Modifier = Modifier
) {
    val year by viewModel.selectedYear.collectAsState()
    val month by viewModel.selectedMonth.collectAsState() // 0-indexed (0=Jan, 8=Sep)
    val transactions by viewModel.transactions.collectAsState()
    val monthSummary by viewModel.monthSummary.collectAsState()

    // Hôm nay theo thời gian thực
    val todayCal = remember { Calendar.getInstance() }
    val todayYear = todayCal.get(Calendar.YEAR)
    val todayMonth = todayCal.get(Calendar.MONTH)
    val todayDay = todayCal.get(Calendar.DAY_OF_MONTH)

    // Ngày đang được chọn trên lịch
    var selectedDay by remember(year, month) {
        val defaultDay = if (year == todayYear && month == todayMonth) todayDay else 1
        mutableStateOf(defaultDay)
    }

    // Tính toán số ngày trong tháng và thứ bắt đầu (Thứ 2 = 0, ..., CN = 6)
    val monthCal = remember(year, month) {
        Calendar.getInstance().apply {
            set(year, month, 1)
        }
    }
    val daysInMonth = monthCal.getActualMaximum(Calendar.DAY_OF_MONTH)
    val startDayOfWeek = (monthCal.get(Calendar.DAY_OF_WEEK) - Calendar.MONDAY + 7) % 7

    // Nhóm giao dịch theo ngày trong tháng
    val transactionsByDay = remember(transactions, year, month) {
        val map = mutableMapOf<Int, MutableList<Transaction>>()
        val txCal = Calendar.getInstance()
        for (tx in transactions) {
            txCal.timeInMillis = tx.dateEpoch
            if (txCal.get(Calendar.YEAR) == year && txCal.get(Calendar.MONTH) == month) {
                val day = txCal.get(Calendar.DAY_OF_MONTH)
                map.getOrPut(day) { mutableListOf() }.add(tx)
            }
        }
        map
    }

    // Các giao dịch của ngày đang chọn
    val selectedDayTransactions = transactionsByDay[selectedDay] ?: emptyList()
    val dayIncome = selectedDayTransactions.filter { it.type == "INCOME" }.sumOf { it.amount }
    val dayExpense = selectedDayTransactions.filter { it.type == "EXPENSE" }.sumOf { it.amount }
    val dayBalance = dayIncome - dayExpense

    // Chế độ hiển thị tổng hợp: Theo ngày đang chọn hay Cả tháng
    var viewMode by remember { mutableStateOf(0) } // 0: Ngày đang chọn, 1: Cả tháng

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 90.dp, top = 8.dp, start = 14.dp, end = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. Month Selector Header
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { viewModel.prevMonth() }) {
                        Icon(Icons.Default.ChevronLeft, contentDescription = "Tháng trước", tint = MaterialTheme.colorScheme.primary)
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "Tháng ${String.format("%02d", month + 1)} / $year",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (year == todayYear && month == todayMonth) {
                            Text(
                                text = "Tháng hiện tại",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (year != todayYear || month != todayMonth) {
                            IconButton(onClick = {
                                viewModel.setMonth(todayYear, todayMonth)
                                selectedDay = todayDay
                            }) {
                                Icon(Icons.Default.Today, contentDescription = "Về hôm nay", tint = MaterialTheme.colorScheme.secondary)
                            }
                        }
                        IconButton(onClick = { viewModel.nextMonth() }) {
                            Icon(Icons.Default.ChevronRight, contentDescription = "Tháng sau", tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }

        // 2. Lưới Lịch Tháng (Calendar Grid)
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    // Tiêu đề các thứ trong tuần (T2..CN)
                    val daysOfWeek = listOf("T2", "T3", "T4", "T5", "T6", "T7", "CN")
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceAround
                    ) {
                        daysOfWeek.forEachIndexed { idx, dayName ->
                            val isWeekend = idx >= 5
                            Text(
                                text = dayName,
                                modifier = Modifier.weight(1f),
                                textAlign = TextAlign.Center,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isWeekend) Color(0xFFEF4444).copy(alpha = 0.8f) else MaterialTheme.colorScheme.outline
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Lưới các ô ngày trong tháng (tối đa 6 tuần)
                    val totalCells = startDayOfWeek + daysInMonth
                    val totalRows = (totalCells + 6) / 7

                    for (rowIndex in 0 until totalRows) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp),
                            horizontalArrangement = Arrangement.SpaceAround
                        ) {
                            for (colIndex in 0..6) {
                                val cellIndex = rowIndex * 7 + colIndex
                                val dayNumber = cellIndex - startDayOfWeek + 1

                                if (dayNumber in 1..daysInMonth) {
                                    val isSelected = dayNumber == selectedDay
                                    val isToday = year == todayYear && month == todayMonth && dayNumber == todayDay

                                    val dayTx = transactionsByDay[dayNumber] ?: emptyList()
                                    val inc = dayTx.filter { it.type == "INCOME" }.sumOf { it.amount }
                                    val exp = dayTx.filter { it.type == "EXPENSE" }.sumOf { it.amount }

                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(65.dp)
                                            .padding(1.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(
                                                when {
                                                    isSelected -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                                                    isToday -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                                                    else -> Color.Transparent
                                                }
                                            )
                                            .border(
                                                width = when {
                                                    isSelected -> 2.dp
                                                    isToday -> 1.dp
                                                    else -> 0.dp
                                                },
                                                color = when {
                                                    isSelected -> MaterialTheme.colorScheme.primary
                                                    isToday -> MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                                                    else -> Color.Transparent
                                                },
                                                shape = RoundedCornerShape(10.dp)
                                            )
                                            .clickable { selectedDay = dayNumber }
                                            .padding(top = 3.dp, bottom = 2.dp, start = 1.dp, end = 1.dp),
                                        contentAlignment = Alignment.TopCenter
                                    ) {
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.SpaceBetween,
                                            modifier = Modifier.fillMaxSize()
                                        ) {
                                            // Số ngày
                                            Text(
                                                text = "$dayNumber",
                                                fontSize = 12.sp,
                                                fontWeight = if (isSelected || isToday) FontWeight.Bold else FontWeight.Normal,
                                                color = when {
                                                    isSelected -> MaterialTheme.colorScheme.primary
                                                    colIndex == 6 -> Color(0xFFEF4444)
                                                    else -> MaterialTheme.colorScheme.onSurface
                                                }
                                            )

                                            // Hiển thị thu và chi đầy đủ số tiền dưới ngày với 2 màu khác nhau
                                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                if (inc > 0) {
                                                    Text(
                                                        text = Formatters.formatVnd(inc, showSign = true, isIncome = true),
                                                        fontSize = 8.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = Color(0xFF10B981), // Xanh lá thu nhập
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                }
                                                if (exp > 0) {
                                                    Text(
                                                        text = Formatters.formatVnd(exp, showSign = true, isIncome = false),
                                                        fontSize = 8.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = Color(0xFFEF4444), // Đỏ chi tiêu
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                }
                                            }
                                        }
                                    }
                                } else {
                                    // Ô trống đầu hoặc cuối tháng
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }
            }
        }

        // 3. Hàng Tổng Hợp (Thu, Chi, Tổng)
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (viewMode == 0) "Tổng Hợp Ngày $selectedDay/${month + 1}" else "Tổng Hợp Cả Tháng ${month + 1}/$year",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            FilterChip(
                                selected = viewMode == 0,
                                onClick = { viewMode = 0 },
                                label = { Text("Ngày $selectedDay", fontSize = 11.sp) }
                            )
                            FilterChip(
                                selected = viewMode == 1,
                                onClick = { viewMode = 1 },
                                label = { Text("Cả tháng", fontSize = 11.sp) }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    val curIncome = if (viewMode == 0) dayIncome else monthSummary.totalIncome
                    val curExpense = if (viewMode == 0) dayExpense else monthSummary.totalExpense
                    val curBalance = if (viewMode == 0) dayBalance else monthSummary.balance

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // 1. Cột Thu
                        Card(
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF10B981).copy(alpha = 0.1f))
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "Thu nhập",
                                    fontSize = 11.sp,
                                    color = Color(0xFF10B981),
                                    fontWeight = FontWeight.SemiBold
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = Formatters.formatVnd(curIncome),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF10B981),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        // 2. Cột Chi
                        Card(
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFEF4444).copy(alpha = 0.1f))
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "Chi tiêu",
                                    fontSize = 11.sp,
                                    color = Color(0xFFEF4444),
                                    fontWeight = FontWeight.SemiBold
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = Formatters.formatVnd(curExpense),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFEF4444),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        // 3. Cột Tổng (Chênh lệch / Số dư)
                        Card(
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (curBalance >= 0) Color(0xFF3B82F6).copy(alpha = 0.1f) else Color(0xFFF97316).copy(alpha = 0.1f)
                            )
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "Tổng (Số dư)",
                                    fontSize = 11.sp,
                                    color = if (curBalance >= 0) Color(0xFF2563EB) else Color(0xFFEA580C),
                                    fontWeight = FontWeight.SemiBold
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = Formatters.formatVnd(curBalance),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (curBalance >= 0) Color(0xFF2563EB) else Color(0xFFEA580C),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }

        // 4. Lịch Sử Giao Dịch Dưới Lịch
        val displayTransactions = if (viewMode == 0) selectedDayTransactions else transactions

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (viewMode == 0) "Lịch Sử Ngày $selectedDay/${month + 1}" else "Tất Cả Giao Dịch Tháng ${month + 1}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Text(
                    text = "${displayTransactions.size} giao dịch",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        }

        if (displayTransactions.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(text = "📅", fontSize = 32.sp)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = if (viewMode == 0) "Không có giao dịch nào vào ngày $selectedDay/${month + 1}" else "Chưa có giao dịch nào trong tháng này",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.outline
                            )
                            Text(
                                text = "Chuyển sang tab Nhập Vào để ghi chép nhanh nhé!",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                }
            }
        } else {
            items(displayTransactions, key = { it.id }) { tx ->
                TransactionItemView(
                    transaction = tx,
                    onClick = { onEditTransaction(tx) },
                    onDelete = { viewModel.deleteTransaction(tx.id) }
                )
            }
        }
    }
}
