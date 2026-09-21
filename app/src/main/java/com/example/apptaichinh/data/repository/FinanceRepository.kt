package com.example.apptaichinh.data.repository

import android.content.Context
import com.example.apptaichinh.data.db.FinanceDatabaseHelper
import com.example.apptaichinh.data.model.Category
import com.example.apptaichinh.data.model.CategoryBudget
import com.example.apptaichinh.data.model.CategoryStat
import com.example.apptaichinh.data.model.MonthSummary
import com.example.apptaichinh.data.model.OverallBudget
import com.example.apptaichinh.data.model.Transaction
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar

class FinanceRepository private constructor(context: Context) {

    private val dbHelper = FinanceDatabaseHelper.getInstance(context)
    private val scope = CoroutineScope(Dispatchers.IO)

    private val calendar = Calendar.getInstance()
    private val _selectedYear = MutableStateFlow(calendar.get(Calendar.YEAR))
    val selectedYear: StateFlow<Int> = _selectedYear.asStateFlow()

    private val _selectedMonth = MutableStateFlow(calendar.get(Calendar.MONTH)) // 0-11
    val selectedMonth: StateFlow<Int> = _selectedMonth.asStateFlow()

    private val _typeFilter = MutableStateFlow("ALL") // ALL, EXPENSE, INCOME
    val typeFilter: StateFlow<String> = _typeFilter.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _transactions = MutableStateFlow<List<Transaction>>(emptyList())
    val transactions: StateFlow<List<Transaction>> = _transactions.asStateFlow()

    private val _monthSummary = MutableStateFlow(MonthSummary())
    val monthSummary: StateFlow<MonthSummary> = _monthSummary.asStateFlow()

    private val _overallBudget = MutableStateFlow(OverallBudget())
    val overallBudget: StateFlow<OverallBudget> = _overallBudget.asStateFlow()

    private val _categoryBudgets = MutableStateFlow<List<CategoryBudget>>(emptyList())
    val categoryBudgets: StateFlow<List<CategoryBudget>> = _categoryBudgets.asStateFlow()

    private val _categoryStats = MutableStateFlow<List<CategoryStat>>(emptyList())
    val categoryStats: StateFlow<List<CategoryStat>> = _categoryStats.asStateFlow()

    private val _categories = MutableStateFlow<List<Category>>(emptyList())
    val categories: StateFlow<List<Category>> = _categories.asStateFlow()

    val aiService = com.example.apptaichinh.data.ai.AiService(dbHelper)

    private val _aiServerUrl = MutableStateFlow(dbHelper.getAiServerUrl())
    val aiServerUrl: StateFlow<String> = _aiServerUrl.asStateFlow()

    private val _aiModelName = MutableStateFlow(dbHelper.getAiModelName())
    val aiModelName: StateFlow<String> = _aiModelName.asStateFlow()

    fun setAiServerUrl(url: String) {
        dbHelper.setAiServerUrl(url)
        _aiServerUrl.value = url.trim()
    }

    fun setAiModelName(model: String) {
        dbHelper.setAiModelName(model)
        _aiModelName.value = model.trim()
    }

    fun searchTransactions(keyword: String, amount: Long? = null): List<Transaction> {
        return dbHelper.searchTransactions(keyword, amount)
    }

    init {
        refreshAll()
    }

    companion object {
        @Volatile
        private var instance: FinanceRepository? = null

        fun getInstance(context: Context): FinanceRepository {
            return instance ?: synchronized(this) {
                instance ?: FinanceRepository(context.applicationContext).also { instance = it }
            }
        }
    }

    fun setMonth(year: Int, month: Int) {
        _selectedYear.value = year
        _selectedMonth.value = month
        refreshAll()
    }

    fun prevMonth() {
        val y = _selectedYear.value
        val m = _selectedMonth.value
        if (m == 0) {
            setMonth(y - 1, 11)
        } else {
            setMonth(y, m - 1)
        }
    }

    fun nextMonth() {
        val y = _selectedYear.value
        val m = _selectedMonth.value
        if (m == 11) {
            setMonth(y + 1, 0)
        } else {
            setMonth(y, m + 1)
        }
    }

    fun setFilter(type: String) {
        _typeFilter.value = type
        refreshTransactions()
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
        refreshTransactions()
    }

    fun refreshAll() {
        scope.launch {
            loadData()
        }
    }

    private suspend fun loadData() = withContext(Dispatchers.IO) {
        val y = _selectedYear.value
        val m = _selectedMonth.value
        val tf = _typeFilter.value
        val q = _searchQuery.value

        val txList = dbHelper.getTransactionsByMonth(y, m, tf, q)
        val summary = dbHelper.getMonthSummary(y, m)
        val overall = dbHelper.getOverallBudget(y, m)
        val catBudgets = dbHelper.getCategoryBudgets(y, m)
        val stats = dbHelper.getCategoryExpenseStats(y, m)
        val allCats = dbHelper.getAllCategories()

        _transactions.value = txList
        _monthSummary.value = summary
        _overallBudget.value = overall
        _categoryBudgets.value = catBudgets
        _categoryStats.value = stats
        _categories.value = allCats
    }

    private fun refreshTransactions() {
        scope.launch {
            val y = _selectedYear.value
            val m = _selectedMonth.value
            val tf = _typeFilter.value
            val q = _searchQuery.value
            val txList = dbHelper.getTransactionsByMonth(y, m, tf, q)
            _transactions.value = txList
        }
    }

    suspend fun addTransaction(tx: Transaction): Long = withContext(Dispatchers.IO) {
        val id = dbHelper.insertTransaction(tx)
        loadData()
        id
    }

    suspend fun updateTransaction(tx: Transaction): Boolean = withContext(Dispatchers.IO) {
        val success = dbHelper.updateTransaction(tx)
        loadData()
        success
    }

    suspend fun deleteTransaction(id: Long): Boolean = withContext(Dispatchers.IO) {
        val success = dbHelper.deleteTransaction(id)
        loadData()
        success
    }

    suspend fun addCategory(cat: Category): Long = withContext(Dispatchers.IO) {
        val id = dbHelper.insertCategory(cat)
        loadData()
        id
    }

    suspend fun updateCategory(cat: Category): Boolean = withContext(Dispatchers.IO) {
        val success = dbHelper.updateCategory(cat)
        loadData()
        success
    }

    suspend fun deleteCategory(id: Long): Boolean = withContext(Dispatchers.IO) {
        val success = dbHelper.deleteCategory(id)
        loadData()
        success
    }

    suspend fun updateCategoryBudget(categoryId: Long, budget: Long): Boolean = withContext(Dispatchers.IO) {
        val success = dbHelper.updateCategoryBudget(categoryId, budget)
        loadData()
        success
    }

    suspend fun updateOverallBudget(amount: Long) = withContext(Dispatchers.IO) {
        dbHelper.setOverallBudgetLimit(amount)
        loadData()
    }
}
