package com.example.apptaichinh.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.apptaichinh.data.model.Category
import com.example.apptaichinh.data.model.CategoryBudget
import com.example.apptaichinh.data.model.Transaction
import com.example.apptaichinh.ui.components.AddTransactionSheet
import com.example.apptaichinh.ui.components.EditBudgetDialog
import com.example.apptaichinh.ui.components.EditCategoryDialog
import com.example.apptaichinh.ui.screens.AnalyticsScreen
import com.example.apptaichinh.ui.screens.BudgetScreen
import com.example.apptaichinh.ui.screens.CategoryScreen
import com.example.apptaichinh.ui.screens.ChatAssistantScreen
import com.example.apptaichinh.ui.screens.HomeScreen
import com.example.apptaichinh.ui.viewmodel.FinanceViewModel

@Composable
fun MainApp(
    viewModel: FinanceViewModel,
    modifier: Modifier = Modifier
) {
    val currentTab by viewModel.currentTab.collectAsState()
    val categories by viewModel.categories.collectAsState()
    val overallBudget by viewModel.overallBudget.collectAsState()

    // Dialog & Sheet States
    var showAddTxSheet by remember { mutableStateOf(false) }
    var editingTx by remember { mutableStateOf<Transaction?>(null) }

    var showOverallBudgetDialog by remember { mutableStateOf(false) }
    var editingCategoryBudget by remember { mutableStateOf<CategoryBudget?>(null) }

    var showAddCategoryDialog by remember { mutableStateOf(false) }
    var editingCategory by remember { mutableStateOf<Category?>(null) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp
            ) {
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Home, contentDescription = "Trang Chủ") },
                    label = { Text("Sổ Thu Chi") },
                    selected = currentTab == 0,
                    onClick = { viewModel.setCurrentTab(0) }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Savings, contentDescription = "Ngân Sách") },
                    label = { Text("Ngân Sách") },
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
                    icon = { Icon(Icons.Default.Category, contentDescription = "Danh Mục") },
                    label = { Text("Danh Mục") },
                    selected = currentTab == 3,
                    onClick = { viewModel.setCurrentTab(3) }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.AutoAwesome, contentDescription = "Trợ Lý AI") },
                    label = { Text("Trợ Lý AI") },
                    selected = currentTab == 4,
                    onClick = { viewModel.setCurrentTab(4) }
                )
            }
        },
        floatingActionButton = {
            if (currentTab != 4) {
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
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (currentTab) {
                0 -> HomeScreen(
                    viewModel = viewModel,
                    onEditTransaction = { tx ->
                        editingTx = tx
                        showAddTxSheet = true
                    },
                    onOpenBudgetEdit = { showOverallBudgetDialog = true },
                    onOpenChat = { viewModel.setCurrentTab(4) }
                )

                1 -> BudgetScreen(
                    viewModel = viewModel,
                    onEditOverallBudget = { showOverallBudgetDialog = true },
                    onEditCategoryBudget = { catBudget ->
                        editingCategoryBudget = catBudget
                    }
                )

                2 -> AnalyticsScreen(
                    viewModel = viewModel
                )

                3 -> CategoryScreen(
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

                4 -> ChatAssistantScreen(
                    viewModel = viewModel
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
