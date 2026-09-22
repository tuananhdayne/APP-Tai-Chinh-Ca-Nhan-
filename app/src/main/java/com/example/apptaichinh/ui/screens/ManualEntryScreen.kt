package com.example.apptaichinh.ui.screens

import android.app.DatePickerDialog
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
import androidx.compose.foundation.layout.fillMaxSize
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
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.apptaichinh.data.model.Category
import com.example.apptaichinh.data.model.Transaction
import com.example.apptaichinh.ui.components.EditCategoryDialog
import com.example.apptaichinh.ui.components.Formatters
import com.example.apptaichinh.ui.viewmodel.FinanceViewModel
import com.example.apptaichinh.ui.viewmodel.ParsedTransaction
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ManualEntryScreen(
    viewModel: FinanceViewModel,
    onNavigateToCalendar: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val categories by viewModel.categories.collectAsState()

    // 0: Nhập thủ công, 1: Nhập nhanh văn bản tự nhiên (Smart NLP)
    var inputMode by remember { mutableStateOf(0) }

    // State loại giao dịch: EXPENSE hoặc INCOME
    var txType by remember { mutableStateOf("EXPENSE") }

    // State ngày chọn (mặc định hôm nay)
    val selectedCalendar = remember { mutableStateOf(Calendar.getInstance()) }
    var selectedDateEpoch by remember { mutableStateOf(System.currentTimeMillis()) }

    // State số tiền & ghi chú
    var amountText by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }

    // Danh mục theo loại
    val filteredCategories = categories.filter { it.type == txType }
    var selectedCategory by remember(txType, categories) {
        mutableStateOf(filteredCategories.firstOrNull())
    }

    // Dialog tạo danh mục mới
    var showAddCatDialog by remember { mutableStateOf(false) }

    // State Smart NLP
    var smartInputText by remember { mutableStateOf("") }
    var parsedTx by remember { mutableStateOf<ParsedTransaction?>(null) }

    // Speech-to-Text launcher
    val speechLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val spokenText = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if (!spokenText.isNullOrBlank()) {
                smartInputText = spokenText
                parsedTx = viewModel.parseQuickText(spokenText)
            }
        }
    }

    val audioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            try {
                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, "vi-VN")
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "vi-VN")
                    putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, "vi-VN")
                    putExtra(RecognizerIntent.EXTRA_PROMPT, "Nói câu chi tiêu (VD: Ăn phở 45k, Đổ xăng 80k)...")
                }
                speechLauncher.launch(intent)
            } catch (e: ActivityNotFoundException) {
                Toast.makeText(context, "Thiết bị chưa cài đặt nhận diện giọng nói Google", Toast.LENGTH_LONG).show()
            }
        } else {
            Toast.makeText(context, "Cần cấp quyền ghi âm để sử dụng chức năng giọng nói", Toast.LENGTH_SHORT).show()
        }
    }

    fun startVoiceSmartInput() {
        val permissionCheck = androidx.core.content.ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.RECORD_AUDIO
        )
        if (permissionCheck == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            try {
                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, "vi-VN")
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "vi-VN")
                    putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, "vi-VN")
                    putExtra(RecognizerIntent.EXTRA_PROMPT, "Nói câu chi tiêu (VD: Ăn phở 45k, Đổ xăng 80k)...")
                }
                speechLauncher.launch(intent)
            } catch (e: ActivityNotFoundException) {
                Toast.makeText(context, "Thiết bị chưa cài đặt nhận diện giọng nói Google", Toast.LENGTH_LONG).show()
            }
        } else {
            audioPermissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
        }
    }

    // Helper chọn ngày bằng DatePickerDialog
    fun openDatePicker() {
        val cal = selectedCalendar.value
        val y = cal.get(Calendar.YEAR)
        val m = cal.get(Calendar.MONTH)
        val d = cal.get(Calendar.DAY_OF_MONTH)

        DatePickerDialog(context, { _, year, month, dayOfMonth ->
            val newCal = Calendar.getInstance().apply {
                set(year, month, dayOfMonth, 12, 0, 0)
            }
            selectedCalendar.value = newCal
            selectedDateEpoch = newCal.timeInMillis
        }, y, m, d).show()
    }

    val dateFormat = remember { SimpleDateFormat("EEEE, dd/MM/yyyy", Locale("vi", "VN")) }
    val formattedSelectedDate = dateFormat.format(Date(selectedDateEpoch)).replaceFirstChar { it.uppercase() }

    val amountLong = amountText.toLongOrNull() ?: 0L

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
                .padding(bottom = 90.dp, top = 8.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // 1. Tiêu đề
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Ghi Chép Giao Dịch",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Nhập nhanh thu chi hằng ngày vào sổ",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }

                // Tab đổi chế độ Nhập Thủ Công vs Nhập Nhanh
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                        .padding(2.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (inputMode == 0) MaterialTheme.colorScheme.primary else Color.Transparent)
                            .clickable { inputMode = 0 }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "Chi tiết",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (inputMode == 0) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (inputMode == 1) MaterialTheme.colorScheme.primary else Color.Transparent)
                            .clickable { inputMode = 1 }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = null,
                                modifier = Modifier.size(12.dp),
                                tint = if (inputMode == 1) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Gõ tắt",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (inputMode == 1) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            if (inputMode == 0) {
                // --- CHẾ ĐỘ NHẬP THỦ CÔNG CHI TIẾT ---

                // 2. Bộ chọn Thu hay Chi ở trên cùng
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = {
                            txType = "EXPENSE"
                            focusManager.clearFocus()
                            keyboardController?.hide()
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (txType == "EXPENSE") Color(0xFFEF4444) else MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = if (txType == "EXPENSE") Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(14.dp),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = if (txType == "EXPENSE") 3.dp else 0.dp)
                    ) {
                        Text(
                            text = "Chi Tiêu (-)",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                    }

                    Button(
                        onClick = {
                            txType = "INCOME"
                            focusManager.clearFocus()
                            keyboardController?.hide()
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (txType == "INCOME") Color(0xFF10B981) else MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = if (txType == "INCOME") Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(14.dp),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = if (txType == "INCOME") 3.dp else 0.dp)
                    ) {
                        Text(
                            text = "Thu Nhập (+)",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // 3. Chọn Ngày giao dịch
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { openDatePicker() },
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.CalendarMonth,
                                contentDescription = "Chọn ngày",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "Ngày giao dịch",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.outline
                                )
                                Text(
                                    text = formattedSelectedDate,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }

                        Text(
                            text = "Đổi ngày",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // 4. Ô nhập Số Tiền
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Số tiền (VNĐ):",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.outline
                            )
                            if (amountLong > 0) {
                                Text(
                                    text = Formatters.formatVnd(amountLong, showSign = true, isIncome = txType == "INCOME"),
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = if (txType == "EXPENSE") Color(0xFFEF4444) else Color(0xFF10B981)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedTextField(
                            value = amountText,
                            onValueChange = { input -> amountText = input.filter { it.isDigit() } },
                            placeholder = { Text("Nhập số tiền (VD: 50000)") },
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
                            shape = RoundedCornerShape(14.dp),
                            singleLine = true,
                            trailingIcon = {
                                if (amountText.isNotEmpty()) {
                                    IconButton(onClick = { amountText = "" }) {
                                        Icon(imageVector = Icons.Default.Clear, contentDescription = "Xóa số tiền")
                                    }
                                }
                            }
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        // Các phím gợi ý cộng nhanh số tiền
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf(10_000L, 20_000L, 50_000L, 100_000L, 200_000L, 500_000L, 1_000_000L).forEach { addVal ->
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                        .clickable {
                                            val cur = amountText.toLongOrNull() ?: 0L
                                            amountText = (cur + addVal).toString()
                                        }
                                        .padding(horizontal = 10.dp, vertical = 5.dp)
                                ) {
                                    Text(
                                        text = "+${Formatters.formatCompactVnd(addVal)}",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            if (amountLong > 0) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f))
                                        .clickable { amountText = "" }
                                        .padding(horizontal = 10.dp, vertical = 5.dp)
                                ) {
                                    Text(
                                        text = "Xóa",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // 5. Chọn Danh Mục (tự động theo bên Thu hoặc Chi)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Danh mục ${if (txType == "EXPENSE") "chi tiêu" else "thu nhập"}:",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Text(
                        text = "${filteredCategories.size} danh mục",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }

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
                            label = {
                                Text(
                                    text = "${cat.icon} ${cat.name}",
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = if (txType == "EXPENSE") Color(0xFFEF4444).copy(alpha = 0.15f) else Color(0xFF10B981).copy(alpha = 0.15f),
                                selectedLabelColor = if (txType == "EXPENSE") Color(0xFFEF4444) else Color(0xFF10B981)
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = isSelected,
                                selectedBorderColor = if (txType == "EXPENSE") Color(0xFFEF4444) else Color(0xFF10B981),
                                selectedBorderWidth = 1.5.dp
                            )
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
                        label = { Text("+ Thêm mới", fontWeight = FontWeight.Medium) }
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // 6. Ô nhập Ghi Chú
                Text(
                    text = "Ghi chú (tùy chọn):",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.outline
                )

                Spacer(modifier = Modifier.height(6.dp))

                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    placeholder = { Text("Ví dụ: Ăn trưa bún bò, Đổ xăng xe máy, Tiền thưởng...") },
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
                    shape = RoundedCornerShape(14.dp),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(22.dp))

                // 7. Nút Lưu Giao Dịch
                val canSave = amountLong > 0 && selectedCategory != null

                Button(
                    onClick = {
                        focusManager.clearFocus()
                        keyboardController?.hide()
                        val cat = selectedCategory ?: return@Button
                        val newTx = Transaction(
                            id = 0L,
                            amount = amountLong,
                            type = txType,
                            categoryId = cat.id,
                            categoryName = cat.name,
                            categoryIcon = cat.icon,
                            categoryColorHex = cat.colorHex,
                            note = note.trim(),
                            dateEpoch = selectedDateEpoch
                        )

                        viewModel.addTransaction(newTx) {
                            scope.launch {
                                snackbarHostState.showSnackbar(
                                    message = "Đã lưu vào sổ: ${cat.icon} ${cat.name} (${Formatters.formatVnd(amountLong)})",
                                    duration = SnackbarDuration.Short
                                )
                            }
                            // Reset form để sẵn sàng ghi khoản tiếp theo
                            amountText = ""
                            note = ""
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp),
                    shape = RoundedCornerShape(16.dp),
                    enabled = canSave,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (txType == "EXPENSE") Color(0xFFEF4444) else Color(0xFF10B981)
                    )
                ) {
                    Icon(imageVector = Icons.Default.CheckCircle, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Lưu Giao Dịch Vào Sổ",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }

            } else {
                // --- CHẾ ĐỘ NHẬP NHANH THÔNG MINH (SMART NLP) ---
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Gõ câu tiếng Việt tự nhiên:",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        OutlinedTextField(
                            value = smartInputText,
                            onValueChange = {
                                smartInputText = it
                                parsedTx = viewModel.parseQuickText(it)
                            },
                            placeholder = { Text("Ví dụ: Ăn trưa 45k, Đổ xăng 80k, Tiền thưởng 2tr...") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            maxLines = 3,
                            trailingIcon = {
                                IconButton(onClick = { startVoiceSmartInput() }) {
                                    Icon(
                                        imageVector = Icons.Default.Mic,
                                        contentDescription = "Nói bằng giọng nói",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        val parsed = parsedTx
                        if (parsed != null && parsed.isValid) {
                            val isIncome = parsed.type == "INCOME"
                            val cat = categories.find { it.id == parsed.categoryId } ?: categories.firstOrNull()

                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
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
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = if (isIncome) Color(0xFF10B981) else Color(0xFFEF4444)
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(8.dp))

                                    Text(
                                        text = "Danh mục: ${cat?.icon ?: "📦"} ${parsed.categoryName}",
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    Text(
                                        text = "Ghi chú: ${parsed.note}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.outline
                                    )

                                    Spacer(modifier = Modifier.height(12.dp))

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
                                            viewModel.addTransaction(newTx) {
                                                scope.launch {
                                                    snackbarHostState.showSnackbar(
                                                        message = "Đã lưu vào sổ: ${parsed.categoryName} (${Formatters.formatVnd(parsed.amount)})"
                                                    )
                                                }
                                                smartInputText = ""
                                                parsedTx = null
                                            }
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Icon(imageVector = Icons.Default.Check, contentDescription = null)
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Xác Nhận & Lưu Vào Sổ")
                                    }
                                }
                            }
                        } else if (smartInputText.isNotBlank()) {
                            Text(
                                text = "💡 Hãy kèm số tiền (VD: 45k, 70 nghìn, 2tr) để tự động trích xuất",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Phím tắt nhanh xem Lịch
            OutlinedButton(
                onClick = onNavigateToCalendar,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.CalendarMonth,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Xem Sổ Thu Chi Dạng Lịch")
            }
        }

        // Snackbar thông báo lưu thành công
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 96.dp)
        )
    }

    // Dialog thêm nhanh danh mục
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
