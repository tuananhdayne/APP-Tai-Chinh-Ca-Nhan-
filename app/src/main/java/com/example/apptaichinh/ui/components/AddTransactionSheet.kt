package com.example.apptaichinh.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.apptaichinh.data.model.Category
import com.example.apptaichinh.data.model.Transaction
import com.example.apptaichinh.ui.viewmodel.FinanceViewModel
import com.example.apptaichinh.ui.viewmodel.ParsedTransaction

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AddTransactionSheet(
    viewModel: FinanceViewModel,
    categories: List<Category>,
    editingTransaction: Transaction? = null,
    onDismiss: () -> Unit,
    onSave: (Transaction) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    // 0 = Nhập thủ công, 1 = Nhập nhanh thông minh (Smart NLP)
    var selectedMode by remember { mutableStateOf(if (editingTransaction != null) 0 else 1) }

    // State thủ công
    var txType by remember { mutableStateOf(editingTransaction?.type ?: "EXPENSE") }
    var amountText by remember { mutableStateOf(editingTransaction?.let { it.amount.toString() } ?: "") }
    var note by remember { mutableStateOf(editingTransaction?.note ?: "") }

    val filteredCategories = categories.filter { it.type == txType }
    var selectedCategory by remember {
        mutableStateOf(
            categories.find { it.id == editingTransaction?.categoryId }
                ?: filteredCategories.firstOrNull()
        )
    }

    // State nhập nhanh
    var smartInputText by remember { mutableStateOf("") }
    var parsedTx by remember { mutableStateOf<ParsedTransaction?>(null) }
    var showAddCatDialog by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 36.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // Title
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (editingTransaction == null) "Thêm Giao Dịch Mới" else "Chỉnh Sửa Giao Dịch",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Mode Tabs (nếu thêm mới)
            if (editingTransaction == null) {
                TabRow(
                    selectedTabIndex = selectedMode,
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.clip(RoundedCornerShape(12.dp))
                ) {
                    Tab(
                        selected = selectedMode == 1,
                        onClick = { selectedMode = 1 },
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.AutoAwesome,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = if (selectedMode == 1) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Nhập Nhanh")
                            }
                        }
                    )
                    Tab(
                        selected = selectedMode == 0,
                        onClick = { selectedMode = 0 },
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = if (selectedMode == 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Nhập Chi Tiết")
                            }
                        }
                    )
                }

                Spacer(modifier = Modifier.height(18.dp))
            }

            if (selectedMode == 1 && editingTransaction == null) {
                // GIAO DIỆN NHẬP NHANH THÔNG MINH (SMART NLP)
                Text(
                    text = "Gõ câu chi tiêu bằng tiếng Việt tự nhiên:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.outline
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = smartInputText,
                    onValueChange = {
                        smartInputText = it
                        parsedTx = viewModel.parseQuickText(it)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Ví dụ: Ăn trưa 45k, Đổ xăng 80k, Tiền thưởng 2tr...") },
                    singleLine = false,
                    maxLines = 3,
                    shape = RoundedCornerShape(16.dp)
                )

                Spacer(modifier = Modifier.height(14.dp))

                // PREVIEW CARD ĐỀ XUẤT
                val parsed = parsedTx
                if (parsed != null && parsed.isValid) {
                    val isIncome = parsed.type == "INCOME"
                    val cat = categories.find { it.id == parsed.categoryId } ?: categories.firstOrNull()

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f))
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (isIncome) "THU NHẬP (+)" else "CHI TIÊU (-)",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isIncome) Color(0xFF10B981) else Color(0xFFEF4444)
                                )

                                Text(
                                    text = Formatters.formatVnd(parsed.amount),
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = if (isIncome) Color(0xFF10B981) else Color(0xFFEF4444)
                                )
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(text = "Danh mục: ", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                                Text(
                                    text = "${cat?.icon ?: "📦"} ${parsed.categoryName}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }

                            Spacer(modifier = Modifier.height(4.dp))

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(text = "Ghi chú: ", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                                Text(
                                    text = parsed.note,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            Button(
                                onClick = {
                                    focusManager.clearFocus()
                                    keyboardController?.hide()
                                    val newTx = Transaction(
                                        id = 0L,
                                        amount = parsed.amount,
                                        type = parsed.type,
                                        categoryId = cat?.id ?: parsed.categoryId,
                                        note = parsed.note,
                                        dateEpoch = System.currentTimeMillis()
                                    )
                                    onSave(newTx)
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(imageVector = Icons.Default.Check, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Xác Nhận & Lưu Vào Sổ")
                            }
                        }
                    }
                } else if (smartInputText.isNotBlank()) {
                    Text(
                        text = "💡 Hãy kèm số tiền (VD: 45k, 70 nghìn, 2tr, 1500000) để tự động trích xuất",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }

            } else {
                // GIAO DIỆN NHẬP THỦ CÔNG
                // Loại Chi tiêu / Thu nhập
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = {
                            txType = "EXPENSE"
                            selectedCategory = categories.filter { it.type == "EXPENSE" }.firstOrNull()
                            focusManager.clearFocus()
                            keyboardController?.hide()
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (txType == "EXPENSE") Color(0xFFEF4444) else MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = if (txType == "EXPENSE") Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Chi Tiêu (-)", fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = {
                            txType = "INCOME"
                            selectedCategory = categories.filter { it.type == "INCOME" }.firstOrNull()
                            focusManager.clearFocus()
                            keyboardController?.hide()
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (txType == "INCOME") Color(0xFF10B981) else MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = if (txType == "INCOME") Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Thu Nhập (+)", fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Ô nhập số tiền
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { input -> amountText = input.filter { it.isDigit() } },
                    label = { Text("Số tiền (VNĐ)") },
                    placeholder = { Text("Ví dụ: 50000") },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            focusManager.clearFocus()
                            keyboardController?.hide()
                        }
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    singleLine = true
                )

                val amtVal = amountText.toLongOrNull() ?: 0L
                if (amtVal > 0) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Số tiền: ${Formatters.formatVnd(amtVal)}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (txType == "EXPENSE") Color(0xFFEF4444) else Color(0xFF10B981)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Lưới chọn danh mục
                Text(
                    text = "Chọn danh mục:",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )

                Spacer(modifier = Modifier.height(8.dp))

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    filteredCategories.forEach { cat ->
                        val isSelected = selectedCategory?.id == cat.id
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                selectedCategory = cat
                                focusManager.clearFocus()
                                keyboardController?.hide()
                            },
                            label = { Text("${cat.icon} ${cat.name}") }
                        )
                    }

                    // Nút thêm nhanh danh mục mới
                    FilterChip(
                        selected = false,
                        onClick = {
                            focusManager.clearFocus()
                            keyboardController?.hide()
                            showAddCatDialog = true
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        label = { Text("Thêm mới", fontWeight = FontWeight.Medium) }
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Ghi chú
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Ghi chú (tùy chọn)") },
                    placeholder = { Text("Ví dụ: Ăn trưa cùng bạn, Đổ xăng...") },
                    keyboardOptions = KeyboardOptions(
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            focusManager.clearFocus()
                            keyboardController?.hide()
                        }
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Nút Lưu
                Button(
                    onClick = {
                        focusManager.clearFocus()
                        keyboardController?.hide()
                        val amount = amountText.toLongOrNull() ?: 0L
                        val catId = selectedCategory?.id ?: 0L
                        if (amount > 0 && catId > 0) {
                            val newTx = Transaction(
                                id = editingTransaction?.id ?: 0L,
                                amount = amount,
                                type = txType,
                                categoryId = catId,
                                note = note.trim(),
                                dateEpoch = editingTransaction?.dateEpoch ?: System.currentTimeMillis()
                            )
                            onSave(newTx)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    enabled = (amountText.toLongOrNull() ?: 0L) > 0 && selectedCategory != null
                ) {
                    Text(
                        text = if (editingTransaction == null) "Lưu Giao Dịch" else "Cập Nhật Giao Dịch",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }

    // Dialog thêm danh mục nhanh ngay trong sheet
    if (showAddCatDialog) {
        EditCategoryDialog(
            category = null,
            initialType = txType,
            onDismiss = { showAddCatDialog = false },
            onSave = { newCat ->
                viewModel.addCategory(newCat) { created ->
                    selectedCategory = created
                    showAddCatDialog = false
                }
            }
        )
    }
}
