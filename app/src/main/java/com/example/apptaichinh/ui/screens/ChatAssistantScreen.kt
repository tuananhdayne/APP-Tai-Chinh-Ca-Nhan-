package com.example.apptaichinh.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.apptaichinh.data.ai.CardStatus
import com.example.apptaichinh.data.ai.ChatMessage
import com.example.apptaichinh.data.ai.MessageSender
import com.example.apptaichinh.data.ai.ToolAction
import com.example.apptaichinh.data.ai.ToolActionType
import com.example.apptaichinh.data.model.Category
import com.example.apptaichinh.ui.components.EditCategoryDialog
import com.example.apptaichinh.ui.components.Formatters
import com.example.apptaichinh.ui.viewmodel.FinanceViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatAssistantScreen(
    viewModel: FinanceViewModel,
    onClose: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val messages by viewModel.chatMessages.collectAsState()
    val isThinking by viewModel.isAiThinking.collectAsState()
    val serverUrl by viewModel.aiServerUrl.collectAsState()
    val modelName by viewModel.aiModelName.collectAsState()
    val categories by viewModel.categories.collectAsState()

    var inputText by remember { mutableStateOf("") }
    var showSettingsDialog by remember { mutableStateOf(false) }
    var showClearChatDialog by remember { mutableStateOf(false) }

    val listState = rememberLazyListState()
    val keyboardController = LocalSoftwareKeyboardController.current
    val context = androidx.compose.ui.platform.LocalContext.current

    // Speech-to-Text launcher sử dụng Android RecognizerIntent có sẵn
    val speechLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val spokenText = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if (!spokenText.isNullOrBlank()) {
                inputText = spokenText
            }
        }
    }

    // Xin quyền ghi âm nếu hệ thống yêu cầu
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
                    putExtra(RecognizerIntent.EXTRA_PROMPT, "Nói câu thu chi của bạn (VD: Ăn phở 45k)...")
                }
                speechLauncher.launch(intent)
            } catch (e: ActivityNotFoundException) {
                Toast.makeText(context, "Thiết bị chưa cài đặt nhận diện giọng nói Google", Toast.LENGTH_LONG).show()
            }
        } else {
            Toast.makeText(context, "Cần cấp quyền ghi âm để sử dụng chức năng giọng nói", Toast.LENGTH_SHORT).show()
        }
    }

    fun startVoiceInput() {
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
                    putExtra(RecognizerIntent.EXTRA_PROMPT, "Nói câu thu chi của bạn (VD: Ăn phở 45k)...")
                }
                speechLauncher.launch(intent)
            } catch (e: ActivityNotFoundException) {
                Toast.makeText(context, "Thiết bị chưa cài đặt nhận diện giọng nói Google", Toast.LENGTH_LONG).show()
            }
        } else {
            audioPermissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
        }
    }

    // Tự động cuộn xuống tin nhắn mới nhất
    LaunchedEffect(messages.size, isThinking) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    if (onClose != null) {
                        IconButton(onClick = onClose) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Đóng",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Trợ Lý AI",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (serverUrl.isBlank()) "Chưa cấu hình URL" else "Qwen2.5-3B • Tool Calling",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (serverUrl.isBlank()) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { showClearChatDialog = true }) {
                        Icon(
                            imageVector = Icons.Outlined.Delete,
                            contentDescription = "Xóa cuộc trò chuyện",
                            tint = MaterialTheme.colorScheme.outline
                        )
                    }
                    IconButton(onClick = { showSettingsDialog = true }) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Cấu hình Server",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        },
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Danh sách tin nhắn cuộc trò chuyện
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                items(messages, key = { it.id }) { message ->
                    ChatMessageItem(
                        message = message,
                        categories = categories,
                        onConfirmAll = { viewModel.confirmToolAction(message.id, null) },
                        onConfirmAction = { actionIndex -> viewModel.confirmToolAction(message.id, actionIndex) },
                        onCancelAction = { actionIndex -> viewModel.cancelToolAction(message.id, actionIndex) },
                        onEditAction = { actionIndex, updatedAction ->
                            viewModel.updateToolAction(message.id, updatedAction, actionIndex)
                        },
                        onAddNewCategory = { newCat, onCreated ->
                            viewModel.addCategory(newCat, onCreated)
                        }
                    )
                }

                if (isThinking) {
                    item {
                        AiThinkingBubble()
                    }
                }
            }

            // Thanh nhập câu nói ở dưới cùng
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    placeholder = {
                        Text(
                            text = "VD: Ăn phở 45k, Sửa tiền xăng, Xóa khoản phở...",
                            fontSize = 13.sp
                        )
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(24.dp),
                    maxLines = 3,
                    singleLine = false,
                    trailingIcon = {
                        if (inputText.isNotEmpty()) {
                            IconButton(
                                onClick = { inputText = "" },
                                enabled = !isThinking
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Xóa nội dung",
                                    tint = MaterialTheme.colorScheme.outline
                                )
                            }
                        }
                    },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = {
                        if (inputText.isNotBlank() && !isThinking) {
                            val textToSend = inputText
                            inputText = ""
                            keyboardController?.hide()
                            viewModel.sendAiChatMessage(textToSend)
                        }
                    })
                )

                Spacer(modifier = Modifier.width(8.dp))

                IconButton(
                    onClick = {
                        if (inputText.isNotBlank() && !isThinking) {
                            val textToSend = inputText
                            inputText = ""
                            keyboardController?.hide()
                            viewModel.sendAiChatMessage(textToSend)
                        } else if (inputText.isBlank() && !isThinking) {
                            startVoiceInput()
                        }
                    },
                    enabled = !isThinking,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(
                            if (inputText.isNotBlank() && !isThinking) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                        )
                ) {
                    Icon(
                        imageVector = if (inputText.isNotBlank()) Icons.AutoMirrored.Filled.Send else Icons.Default.Mic,
                        contentDescription = if (inputText.isNotBlank()) "Gửi" else "Nói",
                        tint = if (inputText.isNotBlank() && !isThinking) Color.White else MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }

    // Dialog xóa toàn bộ cuộc trò chuyện
    if (showClearChatDialog) {
        AlertDialog(
            onDismissRequest = { showClearChatDialog = false },
            title = { Text("Làm mới hội thoại?", fontWeight = FontWeight.Bold) },
            text = { Text("Lịch sử chat sẽ được làm sạch. Các giao dịch đã lưu trong sổ tay tài chính vẫn được giữ nguyên.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.clearChat()
                        showClearChatDialog = false
                    }
                ) {
                    Text("Làm mới")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showClearChatDialog = false }) {
                    Text("Hủy")
                }
            }
        )
    }

    // Dialog Cài đặt Server AI (Cloudflare Tunnel URL)
    if (showSettingsDialog) {
        AiServerConfigDialog(
            currentUrl = serverUrl,
            currentModel = modelName,
            onDismiss = { showSettingsDialog = false },
            onSave = { url, model ->
                viewModel.setAiServerUrl(url)
                viewModel.setAiModelName(model)
                showSettingsDialog = false
            },
            onTestPing = { url, onResult ->
                viewModel.pingAiServer(url, onResult)
            }
        )
    }
}

@Composable
fun ChatMessageItem(
    message: ChatMessage,
    categories: List<Category>,
    onConfirmAll: () -> Unit,
    onConfirmAction: (Int) -> Unit,
    onCancelAction: (Int) -> Unit,
    onEditAction: (Int, ToolAction) -> Unit,
    onAddNewCategory: (Category, (Category) -> Unit) -> Unit
) {
    when (message.sender) {
        MessageSender.USER -> {
            // Tin nhắn Người dùng (Lề phải)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(topStart = 18.dp, topEnd = 4.dp, bottomStart = 18.dp, bottomEnd = 18.dp))
                        .background(MaterialTheme.colorScheme.primary)
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    Text(
                        text = message.text,
                        color = Color.White,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }

        MessageSender.SYSTEM -> {
            // Thông báo hệ thống (Ở giữa)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF10B981).copy(alpha = 0.15f))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = message.text,
                        color = Color(0xFF065F46),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        MessageSender.ASSISTANT -> {
            // Tin nhắn Bot AI (Lề trái)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Start
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(
                            if (message.isErrorMessage) MaterialTheme.colorScheme.errorContainer
                            else MaterialTheme.colorScheme.primaryContainer
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = if (message.isErrorMessage) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                Column(modifier = Modifier.weight(1f, fill = false)) {
                    // Bubble văn bản phản hồi
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 18.dp, bottomStart = 18.dp, bottomEnd = 18.dp))
                            .background(
                                if (message.isErrorMessage) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f)
                                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                            )
                            .padding(horizontal = 14.dp, vertical = 10.dp)
                    ) {
                        Text(
                            text = message.text.replace("**", "").replace("`", ""),
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (message.isErrorMessage) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                        )
                    }

                    // Render Preview Card nếu có Tool Call (Safety-First, hỗ trợ tối đa 6 action)
                    val actions = message.allToolActions
                    if (actions.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))

                        // Lọc các action đang chờ duyệt (PENDING)
                        val pendingActions = actions.filter { it.status == CardStatus.PENDING }

                        // Nếu có từ 2 action đang chờ duyệt trở lên, hiển thị thanh tổng hợp + nút "Xác Nhận Lưu Tất Cả"
                        if (pendingActions.size > 1) {
                            val totalAmount = pendingActions.filter { it.type == ToolActionType.CREATE }.sumOf { it.amount }
                            Card(
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 6.dp)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "ĐỀ XUẤT ${pendingActions.size} GIAO DỊCH",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        if (totalAmount > 0) {
                                            Text(
                                                text = Formatters.formatVnd(totalAmount),
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.ExtraBold,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Button(
                                        onClick = onConfirmAll,
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Icon(imageVector = Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Xác Nhận Lưu Tất Cả (${pendingActions.size} Khoản)", fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }

                        actions.forEachIndexed { index, action ->
                            if (index > 0) {
                                Spacer(modifier = Modifier.height(8.dp))
                            }
                            val currentCardStatus = action.status
                            when (action.type) {
                                ToolActionType.CREATE -> {
                                    CreateTransactionCard(
                                        action = action,
                                        cardStatus = currentCardStatus,
                                        categories = categories,
                                        onConfirm = { onConfirmAction(index) },
                                        onCancel = { onCancelAction(index) },
                                        onEditAction = { updated -> onEditAction(index, updated) },
                                        onAddNewCategory = onAddNewCategory
                                    )
                                }
                                ToolActionType.UPDATE -> {
                                    UpdateTransactionCard(
                                        action = action,
                                        cardStatus = currentCardStatus,
                                        onConfirm = { onConfirmAction(index) },
                                        onCancel = { onCancelAction(index) }
                                    )
                                }
                                ToolActionType.DELETE -> {
                                    DeleteTransactionCard(
                                        action = action,
                                        cardStatus = currentCardStatus,
                                        onConfirm = { onConfirmAction(index) },
                                        onCancel = { onCancelAction(index) }
                                    )
                                }
                                ToolActionType.CREATE_CATEGORY -> {
                                    CreateCategoryCard(
                                        action = action,
                                        cardStatus = currentCardStatus,
                                        onConfirm = { onConfirmAction(index) },
                                        onCancel = { onCancelAction(index) }
                                    )
                                }
                                ToolActionType.SET_OVERALL_BUDGET -> {
                                    SetOverallBudgetCard(
                                        action = action,
                                        cardStatus = currentCardStatus,
                                        onConfirm = { onConfirmAction(index) },
                                        onCancel = { onCancelAction(index) }
                                    )
                                }
                                ToolActionType.SET_CATEGORY_BUDGET -> {
                                    SetCategoryBudgetCard(
                                        action = action,
                                        cardStatus = currentCardStatus,
                                        onConfirm = { onConfirmAction(index) },
                                        onCancel = { onCancelAction(index) }
                                    )
                                }
                                ToolActionType.TRANSFER_CATEGORY -> {
                                    TransferCategoryCard(
                                        action = action,
                                        cardStatus = currentCardStatus,
                                        onConfirm = { onConfirmAction(index) },
                                        onCancel = { onCancelAction(index) }
                                    )
                                }
                            }

                        }
                    }
                }
            }
        }
    }
}

// 1. Thẻ Xem Trước Thêm Mới Giao Dịch (Hỗ trợ Sửa trực tiếp trên thẻ)
@Composable
fun CreateTransactionCard(
    action: ToolAction,
    cardStatus: CardStatus,
    categories: List<Category>,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    onEditAction: (ToolAction) -> Unit,
    onAddNewCategory: (Category, (Category) -> Unit) -> Unit
) {
    val isIncome = action.transactionType == "INCOME"
    val badgeColor = if (isIncome) Color(0xFF10B981) else Color(0xFFEF4444)

    var isEditing by remember { mutableStateOf(false) }
    var editAmountText by remember(action.amount) { mutableStateOf(action.amount.toString()) }
    var editNote by remember(action.note) { mutableStateOf(action.note) }
    var selectedCategory by remember(action.categoryId, action.categoryName, categories) {
        mutableStateOf(
            categories.find { it.id == action.categoryId }
                ?: categories.find { it.name.equals(action.categoryName, ignoreCase = true) }
                ?: Category(action.categoryId, action.categoryName, action.transactionType, action.categoryIcon, action.categoryColorHex, 0L)
        )
    }
    var showCategoryPicker by remember { mutableStateOf(false) }
    var showAddCategoryDialog by remember { mutableStateOf(false) }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, badgeColor.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            if (!isEditing) {
                // Chế độ xem trước thông thường
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isIncome) "THU NHẬP MỚI (+)" else "CHI TIÊU MỚI (-)",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = badgeColor
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = Formatters.formatVnd(action.amount),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.ExtraBold,
                            color = badgeColor
                        )
                        if (cardStatus == CardStatus.PENDING) {
                            Spacer(modifier = Modifier.width(4.dp))
                            IconButton(
                                onClick = { isEditing = true },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = "Sửa thông tin",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = "Danh mục: ", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                    Text(
                        text = "${action.categoryIcon} ${action.categoryName}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                if (action.note.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(text = "Ghi chú: ", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                        Text(text = action.note, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            } else {
                // Chế độ chỉnh sửa trực tiếp trên thẻ
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "CHỈNH SỬA THẺ XEM TRƯỚC",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = if (isIncome) "THU NHẬP" else "CHI TIÊU",
                        style = MaterialTheme.typography.labelSmall,
                        color = badgeColor
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // 1. Sửa số tiền
                OutlinedTextField(
                    value = editAmountText,
                    onValueChange = { editAmountText = it.filter { ch -> ch.isDigit() } },
                    label = { Text("Số tiền (VNĐ)") },
                    supportingText = {
                        val amt = editAmountText.toLongOrNull() ?: 0L
                        Text(Formatters.formatVnd(amt), color = badgeColor, fontWeight = FontWeight.Bold)
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(6.dp))

                // 2. Chọn danh mục
                OutlinedCard(
                    onClick = { showCategoryPicker = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(text = "Danh mục: ", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "${selectedCategory.icon} ${selectedCategory.name}",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text("Đổi ▾", color = MaterialTheme.colorScheme.primary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // 3. Sửa ghi chú
                OutlinedTextField(
                    value = editNote,
                    onValueChange = { editNote = it },
                    label = { Text("Ghi chú") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Nút lưu / hủy sửa
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            editAmountText = action.amount.toString()
                            editNote = action.note
                            isEditing = false
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Hủy sửa")
                    }
                    Button(
                        onClick = {
                            val newAmount = editAmountText.toLongOrNull() ?: action.amount
                            val updated = action.copy(
                                amount = newAmount,
                                categoryId = selectedCategory.id,
                                categoryName = selectedCategory.name,
                                categoryIcon = selectedCategory.icon,
                                categoryColorHex = selectedCategory.colorHex,
                                note = editNote
                            )
                            onEditAction(updated)
                            isEditing = false
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Lưu thay đổi")
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Action Buttons
            when (cardStatus) {
                CardStatus.PENDING -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = onCancel,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Hủy")
                        }

                        Button(
                            onClick = onConfirm,
                            modifier = Modifier.weight(1.5f),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Icon(imageVector = Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Xác Nhận Lưu")
                        }
                    }
                }

                CardStatus.CONFIRMED -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF10B981).copy(alpha = 0.12f))
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "✓ Đã lưu vào sổ tay",
                            color = Color(0xFF047857),
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                }

                CardStatus.CANCELLED -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Đã hủy bỏ đề xuất",
                            color = MaterialTheme.colorScheme.outline,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }
    }

    if (showCategoryPicker) {
        val filteredCats = categories.filter { it.type == action.transactionType }
        AlertDialog(
            onDismissRequest = { showCategoryPicker = false },
            title = { Text("Chọn danh mục") },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(260.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(filteredCats) { cat ->
                            Card(
                                onClick = {
                                    selectedCategory = cat
                                    showCategoryPicker = false
                                },
                                shape = RoundedCornerShape(10.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (selectedCategory.id == cat.id || selectedCategory.name.equals(cat.name, ignoreCase = true))
                                        MaterialTheme.colorScheme.primaryContainer
                                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                ),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(text = cat.icon, fontSize = 20.sp)
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = cat.name,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedButton(
                        onClick = { showAddCategoryDialog = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("+ Tạo danh mục mới")
                    }
                }
            },
            confirmButton = {
                Button(onClick = { showCategoryPicker = false }) {
                    Text("Đóng")
                }
            }
        )
    }

    if (showAddCategoryDialog) {
        EditCategoryDialog(
            category = null,
            initialType = action.transactionType,
            onDismiss = { showAddCategoryDialog = false },
            onSave = { newCat ->
                onAddNewCategory(newCat) { created ->
                    selectedCategory = created
                    showAddCategoryDialog = false
                    showCategoryPicker = false
                }
            }
        )
    }
}

// 2. Thẻ Xem Trước Cập Nhật / Sửa Giao Dịch
@Composable
fun UpdateTransactionCard(
    action: ToolAction,
    cardStatus: CardStatus,
    onConfirm: () -> Unit,
    onCancel: () -> Unit
) {
    val oldTx = action.targetTransaction

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Color(0xFFF59E0B).copy(alpha = 0.4f), RoundedCornerShape(16.dp))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "CHỈNH SỬA GIAO DỊCH",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFD97706)
                )
                Icon(imageVector = Icons.Default.Edit, contentDescription = null, tint = Color(0xFFD97706), modifier = Modifier.size(18.dp))
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (oldTx != null) {
                Text(
                    text = "Bản ghi gốc:",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
                Text(
                    text = "${oldTx.categoryIcon} ${oldTx.categoryName} (${oldTx.note}) • ${Formatters.formatVnd(oldTx.amount)}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )

                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Thay đổi thành:",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )

                val newAmt = action.newAmount ?: oldTx.amount
                val newCatName = action.newCategory?.name ?: oldTx.categoryName
                val newNote = action.newNote ?: oldTx.note

                Text(
                    text = "${action.newCategory?.icon ?: oldTx.categoryIcon} $newCatName ($newNote) ➔ ${Formatters.formatVnd(newAmt)}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            when (cardStatus) {
                CardStatus.PENDING -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = onCancel,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Hủy")
                        }

                        Button(
                            onClick = onConfirm,
                            modifier = Modifier.weight(1.5f),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD97706))
                        ) {
                            Text("Cập Nhật")
                        }
                    }
                }

                CardStatus.CONFIRMED -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF10B981).copy(alpha = 0.12f))
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "✓ Đã cập nhật thành công",
                            color = Color(0xFF047857),
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                }

                CardStatus.CANCELLED -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = "Đã hủy bỏ cập nhật", color = MaterialTheme.colorScheme.outline, fontSize = 13.sp)
                    }
                }
            }
        }
    }
}

// 3. Thẻ Cảnh Báo Xóa Giao Dịch (Delete Tool)
@Composable
fun DeleteTransactionCard(
    action: ToolAction,
    cardStatus: CardStatus,
    onConfirm: () -> Unit,
    onCancel: () -> Unit
) {
    val oldTx = action.targetTransaction

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "XÁC NHẬN XÓA GIAO DỊCH",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.error
                )
                Icon(imageVector = Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (oldTx != null) {
                Text(
                    text = "Tìm thấy giao dịch trong sổ:",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
                Text(
                    text = "${oldTx.categoryIcon} ${oldTx.categoryName} (${oldTx.note})",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Số tiền: ${Formatters.formatVnd(oldTx.amount)}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.error
                )
            }

            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "⚠️ Khoản tiền này sẽ được hoàn tác khỏi số dư tháng.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error.copy(alpha = 0.8f)
            )

            Spacer(modifier = Modifier.height(12.dp))

            when (cardStatus) {
                CardStatus.PENDING -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = onCancel,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Bỏ qua")
                        }

                        Button(
                            onClick = onConfirm,
                            modifier = Modifier.weight(1.5f),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                        ) {
                            Icon(imageVector = Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Đồng Ý Xóa")
                        }
                    }
                }

                CardStatus.CONFIRMED -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFFEF4444).copy(alpha = 0.12f))
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "✓ Đã xóa giao dịch khỏi sổ",
                            color = Color(0xFFB91C1C),
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                }

                CardStatus.CANCELLED -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = "Đã bỏ qua (Không xóa)", color = MaterialTheme.colorScheme.outline, fontSize = 13.sp)
                    }
                }
            }
        }
    }
}

// 4. Thẻ Xem Trước Đề Xuất Tạo Danh Mục Mới
@Composable
fun CreateCategoryCard(
    action: ToolAction,
    cardStatus: CardStatus,
    onConfirm: () -> Unit,
    onCancel: () -> Unit
) {
    val isIncome = action.transactionType == "INCOME"
    val badgeColor = if (isIncome) Color(0xFF10B981) else Color(0xFF8B5CF6)

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, badgeColor.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "ĐỀ XUẤT TẠO DANH MỤC MỚI",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = badgeColor
                )
                Text(
                    text = if (isIncome) "THU NHẬP (+)" else "CHI TIÊU (-)",
                    style = MaterialTheme.typography.labelSmall,
                    color = badgeColor
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(badgeColor.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = action.categoryIcon, fontSize = 24.sp)
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = action.categoryName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    if (action.categoryBudget > 0) {
                        Text(
                            text = "Hạn mức: ${Formatters.formatVnd(action.categoryBudget)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            when (cardStatus) {
                CardStatus.PENDING -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = onCancel,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Hủy")
                        }

                        Button(
                            onClick = onConfirm,
                            modifier = Modifier.weight(1.5f),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = badgeColor)
                        ) {
                            Icon(imageVector = Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Xác Nhận Tạo")
                        }
                    }
                }

                CardStatus.CONFIRMED -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF10B981).copy(alpha = 0.12f))
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "✓ Đã tạo danh mục thành công",
                            color = Color(0xFF047857),
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                }

                CardStatus.CANCELLED -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Đã bỏ qua đề xuất",
                            color = MaterialTheme.colorScheme.outline,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }
    }
}

// 5. Thẻ Xem Trước Đặt Ngân Sách Tổng Tháng
@Composable
fun SetOverallBudgetCard(
    action: ToolAction,
    cardStatus: CardStatus,
    onConfirm: () -> Unit,
    onCancel: () -> Unit
) {
    val accentColor = Color(0xFF0EA5E9) // Sky blue

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, accentColor.copy(alpha = 0.35f), RoundedCornerShape(16.dp))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "ĐẶT NGÂN SÁCH TỔNG THÁNG",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = accentColor
                )
                Text(text = "🎯", fontSize = 20.sp)
            }

            Spacer(modifier = Modifier.height(10.dp))

            if (action.oldBudget > 0L) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Hạn mức cũ: ", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                    Text(
                        text = Formatters.formatVnd(action.oldBudget),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.outline,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Hạn mức mới: ", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                Text(
                    text = Formatters.formatVnd(action.newBudget),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold,
                    color = accentColor
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            when (cardStatus) {
                CardStatus.PENDING -> {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f), shape = RoundedCornerShape(10.dp)) {
                            Text("Hủy")
                        }
                        Button(
                            onClick = onConfirm, modifier = Modifier.weight(1.5f),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = accentColor)
                        ) {
                            Icon(imageVector = Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Xác Nhận Đặt", fontWeight = FontWeight.Bold)
                        }
                    }
                }
                CardStatus.CONFIRMED -> {
                    Box(
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                            .background(accentColor.copy(alpha = 0.12f)).padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("✓ Đã cập nhật ngân sách tổng tháng", color = accentColor, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
                CardStatus.CANCELLED -> {
                    Box(
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant).padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Đã bỏ qua", color = MaterialTheme.colorScheme.outline, fontSize = 13.sp)
                    }
                }
            }
        }
    }
}

// 6. Thẻ Xem Trước Đặt Hạn Mức Danh Mục
@Composable
fun SetCategoryBudgetCard(
    action: ToolAction,
    cardStatus: CardStatus,
    onConfirm: () -> Unit,
    onCancel: () -> Unit
) {
    val accentColor = Color(0xFF8B5CF6) // Purple

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, accentColor.copy(alpha = 0.35f), RoundedCornerShape(16.dp))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "ĐẶT HẠN MỨC DANH MỤC",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = accentColor
                )
                Text(text = action.categoryIcon, fontSize = 20.sp)
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Danh mục: ", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                Text(
                    text = "${action.categoryIcon} ${action.categoryName}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            if (action.oldBudget > 0L) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Hạn mức cũ: ", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                    Text(
                        text = Formatters.formatVnd(action.oldBudget),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.outline,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Hạn mức mới: ", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                Text(
                    text = Formatters.formatVnd(action.newBudget),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold,
                    color = accentColor
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            when (cardStatus) {
                CardStatus.PENDING -> {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f), shape = RoundedCornerShape(10.dp)) {
                            Text("Hủy")
                        }
                        Button(
                            onClick = onConfirm, modifier = Modifier.weight(1.5f),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = accentColor)
                        ) {
                            Icon(imageVector = Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Xác Nhận Đặt", fontWeight = FontWeight.Bold)
                        }
                    }
                }
                CardStatus.CONFIRMED -> {
                    Box(
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                            .background(accentColor.copy(alpha = 0.12f)).padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("✓ Đã cập nhật hạn mức ${action.categoryName}", color = accentColor, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
                CardStatus.CANCELLED -> {
                    Box(
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant).padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Đã bỏ qua", color = MaterialTheme.colorScheme.outline, fontSize = 13.sp)
                    }
                }
            }
        }
    }
}

// 7. Thẻ Xem Trước Chuyển Giao Dịch Sang Danh Mục Khác
@Composable
fun TransferCategoryCard(
    action: ToolAction,
    cardStatus: CardStatus,
    onConfirm: () -> Unit,
    onCancel: () -> Unit
) {
    val accentColor = Color(0xFFF59E0B) // Amber

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, accentColor.copy(alpha = 0.35f), RoundedCornerShape(16.dp))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "CHUYỂN DANH MỤC GIAO DỊCH",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = accentColor
                )
                Text(text = "🔄", fontSize = 18.sp)
            }

            Spacer(modifier = Modifier.height(10.dp))

            if (action.targetTransaction != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Giao dịch: ", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                    Text(
                        text = "${action.categoryIcon} ${action.note} (${Formatters.formatVnd(action.amount)})",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Từ: ", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                    Text(
                        text = "${action.categoryIcon} ${action.categoryName}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Sang: ", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                    Text(
                        text = "${action.targetCategoryIcon} ${action.targetCategoryName}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = accentColor
                    )
                }
            } else {
                Text(
                    text = "⚠️ Không tìm thấy giao dịch \"${action.searchKeyword}\" để chuyển.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            when (cardStatus) {
                CardStatus.PENDING -> {
                    if (action.targetTransaction != null) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f), shape = RoundedCornerShape(10.dp)) {
                                Text("Hủy")
                            }
                            Button(
                                onClick = onConfirm, modifier = Modifier.weight(1.5f),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = accentColor)
                            ) {
                                Icon(imageVector = Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Xác Nhận Chuyển", fontWeight = FontWeight.Bold)
                            }
                        }
                    } else {
                        OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp)) {
                            Text("Đóng")
                        }
                    }
                }
                CardStatus.CONFIRMED -> {
                    Box(
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                            .background(accentColor.copy(alpha = 0.12f)).padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("✓ Đã chuyển sang ${action.targetCategoryIcon} ${action.targetCategoryName}", color = accentColor, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
                CardStatus.CANCELLED -> {
                    Box(
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant).padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Đã bỏ qua", color = MaterialTheme.colorScheme.outline, fontSize = 13.sp)
                    }
                }
            }
        }
    }
}

// Bong bóng "Đang suy nghĩ..."
@Composable
fun AiThinkingBubble() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.AutoAwesome,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 18.dp, bottomStart = 18.dp, bottomEnd = 18.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "Qwen2.5 đang phân tích lệnh...",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        }
    }
}

// Dialog cấu hình Server URL & Model
@Composable
fun AiServerConfigDialog(
    currentUrl: String,
    currentModel: String,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit,
    onTestPing: (String, (Boolean, String) -> Unit) -> Unit
) {
    var urlText by remember { mutableStateOf(currentUrl) }
    var modelText by remember { mutableStateOf(currentModel) }
    var isTesting by remember { mutableStateOf(false) }
    var pingResult by remember { mutableStateOf<Pair<Boolean, String>?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(20.dp),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(imageVector = Icons.Default.Settings, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Cài Đặt Máy Chủ AI", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Nhập URL ngrok (HTTPS) hoặc IP máy chủ LM Studio (port 1234):",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = urlText,
                    onValueChange = {
                        urlText = it
                        pingResult = null
                    },
                    label = { Text("Server URL") },
                    placeholder = { Text("https://chas-unshaped-jacalyn.ngrok-free.dev") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = modelText,
                    onValueChange = { modelText = it },
                    label = { Text("Model Identifier") },
                    placeholder = { Text("qwen2.5-3b-instruct") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Nút test kết nối
                OutlinedButton(
                    onClick = {
                        isTesting = true
                        pingResult = null
                        onTestPing(urlText) { success, msg ->
                            isTesting = false
                            pingResult = Pair(success, msg)
                        }
                    },
                    enabled = urlText.isNotBlank() && !isTesting,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    if (isTesting) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Đang kiểm tra...")
                    } else {
                        Icon(imageVector = Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Kiểm tra kết nối (Ping)")
                    }
                }

                // Kết quả test ping
                pingResult?.let { (success, msg) ->
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = (if (success) "✓ " else "✕ ") + msg,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (success) Color(0xFF10B981) else MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(urlText, modelText) },
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("Lưu Cài Đặt")
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("Đóng")
            }
        }
    )
}
