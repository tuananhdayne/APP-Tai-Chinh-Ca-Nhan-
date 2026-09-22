package com.example.apptaichinh.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.apptaichinh.data.model.Category
import com.example.apptaichinh.data.model.CategoryBudget
import com.example.apptaichinh.data.model.Transaction
import com.example.apptaichinh.ui.components.AddTransactionSheet
import com.example.apptaichinh.ui.components.AiChatBubble
import com.example.apptaichinh.ui.components.EditBudgetDialog
import com.example.apptaichinh.ui.components.EditCategoryDialog
import com.example.apptaichinh.ui.screens.AnalyticsScreen
import com.example.apptaichinh.ui.screens.BudgetScreen
import com.example.apptaichinh.ui.screens.CategoryScreen
import com.example.apptaichinh.ui.screens.ChatAssistantScreen
import com.example.apptaichinh.ui.screens.HomeScreen
import com.example.apptaichinh.ui.viewmodel.FinanceViewModel

import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Settings
import com.example.apptaichinh.ui.screens.CalendarScreen
import com.example.apptaichinh.ui.screens.ManualEntryScreen
import com.example.apptaichinh.ui.screens.MoreScreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainApp(
    viewModel: FinanceViewModel,
    modifier: Modifier = Modifier
) {
    val currentTab by viewModel.currentTab.collectAsState()
    val overallBudget by viewModel.overallBudget.collectAsState()
    val categories by viewModel.categories.collectAsState()
    val isAiThinking by viewModel.isAiThinking.collectAsState()

    // Dialog & Sheet States
    var showAddTxSheet by remember { mutableStateOf(false) }
    var editingTx by remember { mutableStateOf<Transaction?>(null) }

    var showOverallBudgetDialog by remember { mutableStateOf(false) }
    var editingCategoryBudget by remember { mutableStateOf<CategoryBudget?>(null) }

    var showAddCategoryDialog by remember { mutableStateOf(false) }
    var editingCategory by remember { mutableStateOf<Category?>(null) }
    var showCategorySheet by remember { mutableStateOf(false) }

    // Trạng thái mở cửa sổ chat từ bong bóng nổi
    var showAiChatWindow by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp
            ) {
                NavigationBarItem(
                    icon = { Icon(Icons.Default.EditNote, contentDescription = "Nhập vào") },
                    label = { Text("Nhập vào") },
                    selected = currentTab == 0,
                    onClick = { viewModel.setCurrentTab(0) }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.CalendarMonth, contentDescription = "Lịch") },
                    label = { Text("Lịch") },
                    selected = currentTab == 1,
                    onClick = { viewModel.setCurrentTab(1) }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.PieChart, contentDescription = "Thống Kê") },
                    label = { Text("Thống Kê") },
                    selected = currentTab == 2,
                    onClick = { viewModel.setCurrentTab(2) }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Savings, contentDescription = "Ngân Sách") },
                    label = { Text("Ngân Sách") },
                    selected = currentTab == 3,
                    onClick = { viewModel.setCurrentTab(3) }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Settings, contentDescription = "Cài Đặt") },
                    label = { Text("Cài Đặt") },
                    selected = currentTab == 4,
                    onClick = { viewModel.setCurrentTab(4) }
                )
            }
        },
        floatingActionButton = {
            if (currentTab != 0) {
                FloatingActionButton(
                    onClick = {
                        editingTx = null
                        showAddTxSheet = true
                    },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = Color.White,
                    shape = CircleShape
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Thêm Giao Dịch")
                }
            }
        }
    ) { innerPadding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (currentTab) {
                0 -> ManualEntryScreen(
                    viewModel = viewModel,
                    onNavigateToCalendar = { viewModel.setCurrentTab(1) }
                )

                1 -> CalendarScreen(
                    viewModel = viewModel,
                    onEditTransaction = { tx ->
                        editingTx = tx
                        showAddTxSheet = true
                    }
                )

                2 -> AnalyticsScreen(
                    viewModel = viewModel
                )

                3 -> BudgetScreen(
                    viewModel = viewModel,
                    onEditOverallBudget = { showOverallBudgetDialog = true },
                    onEditCategoryBudget = { catBudget ->
                        editingCategoryBudget = catBudget
                    }
                )

                4 -> MoreScreen(
                    viewModel = viewModel,
                    onOpenCategories = { showCategorySheet = true },
                    onOpenAiChat = { showAiChatWindow = true }
                )
            }

            // Bong bóng Trợ Lý AI nổi trên màn hình (Floating Chat Bubble - Tự do kéo di chuyển)
            if (!showAiChatWindow) {
                AiChatBubble(
                    containerWidth = maxWidth,
                    containerHeight = maxHeight,
                    onClick = { showAiChatWindow = true },
                    isThinking = isAiThinking
                )
            }
        }
    }

    // Cửa sổ Chat Assistant mở lên khi nhấn vào Bong bóng AI
    if (showAiChatWindow) {
        ModalBottomSheet(
            onDismissRequest = { showAiChatWindow = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            containerColor = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            dragHandle = null
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.92f)
            ) {
                ChatAssistantScreen(
                    viewModel = viewModel,
                    onClose = { showAiChatWindow = false }
                )
            }
        }
    }

    // Cửa sổ Quản Lý Danh Mục mở từ Tab Cài Đặt
    if (showCategorySheet) {
        ModalBottomSheet(
            onDismissRequest = { showCategorySheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            containerColor = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.92f)
            ) {
                CategoryScreen(
                    viewModel = viewModel,
                    onAddCategory = {
                        editingCategory = null
                        showAddCategoryDialog = true
                    },
                    onEditCategory = { cat ->
                        editingCategory = cat
                        showAddCategoryDialog = true
                    }
                )
            }
        }
    }

    // Modal Sheet Thêm / Sửa Giao Dịch
    if (showAddTxSheet) {
        AddTransactionSheet(
            viewModel = viewModel,
            categories = categories,
            editingTransaction = editingTx,
            onDismiss = { showAddTxSheet = false },
            onSave = { tx ->
                if (tx.id == 0L) {
                    viewModel.addTransaction(tx) {
                        showAddTxSheet = false
                    }
                } else {
                    viewModel.updateTransaction(tx) {
                        showAddTxSheet = false
                    }
                }
            }
        )
    }

    // Dialog Sửa Ngân Sách Tổng Thể
    if (showOverallBudgetDialog) {
        EditBudgetDialog(
            title = "Cài Đặt Ngân Sách Tổng Cả Tháng",
            currentAmount = overallBudget.totalBudget,
            onDismiss = { showOverallBudgetDialog = false },
            onSave = { newBudget ->
                viewModel.updateOverallBudget(newBudget)
                showOverallBudgetDialog = false
            }
        )
    }

    // Dialog Sửa Ngân Sách Cho Từng Danh Mục
    editingCategoryBudget?.let { catBudget ->
        EditBudgetDialog(
            title = "Ngân Sách Cho ${catBudget.category.icon} ${catBudget.category.name}",
            currentAmount = catBudget.budgetAmount,
            onDismiss = { editingCategoryBudget = null },
            onSave = { newBudget ->
                viewModel.updateCategoryBudget(catBudget.category.id, newBudget)
                editingCategoryBudget = null
            }
        )
    }

    // Dialog Thêm / Sửa Danh Mục
    if (showAddCategoryDialog) {
        EditCategoryDialog(
            category = editingCategory,
            onDismiss = { showAddCategoryDialog = false },
            onSave = { cat ->
                if (cat.id == 0L) {
                    viewModel.addCategory(cat) {
                        showAddCategoryDialog = false
                    }
                } else {
                    viewModel.updateCategory(cat) {
                        showAddCategoryDialog = false
                    }
                }
            }
        )
    }
}
