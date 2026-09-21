package com.example.apptaichinh.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.apptaichinh.data.model.CategoryBudget
import com.example.apptaichinh.data.model.OverallBudget

@Composable
fun OverallBudgetCard(
    budget: OverallBudget,
    onEditBudget: () -> Unit,
    modifier: Modifier = Modifier
) {
    val progress = budget.percentage.coerceIn(0f, 1f)
    val animatedProgress by animateFloatAsState(targetValue = progress, animationSpec = tween(700))

    val statusColor by animateColorAsState(
        targetValue = when {
            budget.isOverBudget -> Color(0xFFEF4444) // Đỏ
            budget.percentage > 0.8f -> Color(0xFFF59E0B) // Vàng cam cảnh báo
            else -> Color(0xFF10B981) // Xanh an toàn
        },
        animationSpec = tween(500)
    )

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(statusColor.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = "🎯", fontSize = 18.sp)
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Ngân Sách Tổng Cả Tháng",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (budget.isOverBudget) "⚠️ Đã vượt hạn mức chi tiêu!" else "Kế hoạch tài chính hàng tháng",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (budget.isOverBudget) Color(0xFFEF4444) else MaterialTheme.colorScheme.outline
                        )
                    }
                }

                IconButton(onClick = onEditBudget) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Sửa ngân sách",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Hạn mức & Đã tiêu
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "Đã chi tiêu",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.outline
                    )
                    Text(
                        text = Formatters.formatVnd(budget.totalExpense),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = statusColor
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "Hạn mức tối đa",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.outline
                    )
                    Text(
                        text = Formatters.formatVnd(budget.totalBudget),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Thanh tiến độ Budget
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(animatedProgress)
                        .height(10.dp)
                        .clip(RoundedCornerShape(5.dp))
                        .background(statusColor)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Footer info: Số dư còn lại & %
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (budget.remaining >= 0) {
                        "Còn lại: ${Formatters.formatVnd(budget.remaining)}"
                    } else {
                        "Vượt mức: ${Formatters.formatVnd(Math.abs(budget.remaining))}"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (budget.remaining >= 0) MaterialTheme.colorScheme.onSurface else Color(0xFFEF4444)
                )

                val pctText = String.format("%.1f%%", budget.percentage * 100)
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(statusColor.copy(alpha = 0.12f))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = pctText,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = statusColor
                    )
                }
            }
        }
    }
}

@Composable
fun CategoryBudgetCard(
    categoryBudget: CategoryBudget,
    onEditCategoryBudget: () -> Unit,
    modifier: Modifier = Modifier
) {
    val progress = categoryBudget.percentage.coerceIn(0f, 1f)
    val animatedProgress by animateFloatAsState(targetValue = progress, animationSpec = tween(600))

    val statusColor by animateColorAsState(
        targetValue = when {
            categoryBudget.isOverBudget -> Color(0xFFEF4444)
            categoryBudget.percentage > 0.8f -> Color(0xFFF59E0B)
            else -> Color(0xFF10B981)
        }
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onEditCategoryBudget() },
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(Formatters.parseColor(categoryBudget.category.colorHex).copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = categoryBudget.category.icon, fontSize = 20.sp)
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = categoryBudget.category.name,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (categoryBudget.budgetAmount > 0) {
                                "Hạn mức: ${Formatters.formatVnd(categoryBudget.budgetAmount)}"
                            } else {
                                "Chưa đặt ngân sách"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = Formatters.formatVnd(categoryBudget.spentAmount),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        color = if (categoryBudget.isOverBudget) Color(0xFFEF4444) else MaterialTheme.colorScheme.onSurface
                    )
                    if (categoryBudget.budgetAmount > 0) {
                        val pctStr = String.format("%.0f%%", categoryBudget.percentage * 100)
                        Text(
                            text = if (categoryBudget.isOverBudget) "Vượt $pctStr" else "$pctStr",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = statusColor
                        )
                    }
                }
            }

            if (categoryBudget.budgetAmount > 0) {
                Spacer(modifier = Modifier.height(10.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(animatedProgress)
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(statusColor)
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = if (categoryBudget.remaining >= 0) {
                            "Còn lại: ${Formatters.formatVnd(categoryBudget.remaining)}"
                        } else {
                            "Vượt: ${Formatters.formatVnd(Math.abs(categoryBudget.remaining))}"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = if (categoryBudget.remaining >= 0) MaterialTheme.colorScheme.outline else Color(0xFFEF4444)
                    )

                    Text(
                        text = "Chạm để đổi",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            } else {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "+ Chạm để thiết lập ngân sách cho mục này",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}
