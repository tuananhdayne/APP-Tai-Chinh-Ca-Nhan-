package com.example.apptaichinh.data.db

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.example.apptaichinh.data.model.Category
import com.example.apptaichinh.data.model.CategoryBudget
import com.example.apptaichinh.data.model.CategoryStat
import com.example.apptaichinh.data.model.MonthSummary
import com.example.apptaichinh.data.model.OverallBudget
import com.example.apptaichinh.data.model.Transaction
import java.util.Calendar

class FinanceDatabaseHelper(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        const val DATABASE_NAME = "finance_app.db"
        const val DATABASE_VERSION = 2

        // Tables
        const val TABLE_CATEGORIES = "categories"
        const val TABLE_TRANSACTIONS = "transactions"
        const val TABLE_SETTINGS = "settings"

        // Columns Category
        const val COL_CAT_ID = "id"
        const val COL_CAT_NAME = "name"
        const val COL_CAT_TYPE = "type"
        const val COL_CAT_ICON = "icon"
        const val COL_CAT_COLOR = "color"
        const val COL_CAT_BUDGET = "budget"

        // Columns Transaction
        const val COL_TX_ID = "id"
        const val COL_TX_AMOUNT = "amount"
        const val COL_TX_TYPE = "type"
        const val COL_TX_CAT_ID = "category_id"
        const val COL_TX_NOTE = "note"
        const val COL_TX_DATE = "date_epoch"

        // Columns Settings
        const val COL_SETTING_KEY = "key"
        const val COL_SETTING_VAL = "value"
        const val SETTING_TOTAL_BUDGET = "total_monthly_budget"

        @Volatile
        private var instance: FinanceDatabaseHelper? = null

        fun getInstance(context: Context): FinanceDatabaseHelper {
            return instance ?: synchronized(this) {
                instance ?: FinanceDatabaseHelper(context.applicationContext).also { instance = it }
            }
        }

        fun getDefaultCategoriesList(): List<Category> {
            return listOf(
                // --- THU NHẬP (INCOME) ---
                Category(0, "Lương", "INCOME", "💵", "#10B981", 0L),
                Category(0, "Thưởng", "INCOME", "🎁", "#F59E0B", 0L),
                Category(0, "Làm thêm", "INCOME", "💼", "#3B82F6", 0L),
                Category(0, "Đầu tư", "INCOME", "📈", "#8B5CF6", 0L),
                Category(0, "Khác", "INCOME", "💰", "#6B7280", 0L),

                // --- CHI TIÊU (EXPENSE) ---
                Category(0, "Ăn uống", "EXPENSE", "🍜", "#EF4444", 3500000L),
                Category(0, "Nhà ở", "EXPENSE", "🏠", "#F97316", 2500000L),
                Category(0, "Đi lại", "EXPENSE", "🛵", "#06B6D4", 1000000L),
                Category(0, "Mua sắm", "EXPENSE", "🛍️", "#EC4899", 1500000L),
                Category(0, "Giải trí", "EXPENSE", "🎮", "#8B5CF6", 800000L),
                Category(0, "Giáo dục", "EXPENSE", "📚", "#3B82F6", 1000000L),
                Category(0, "Sức khỏe", "EXPENSE", "💊", "#14B8A6", 600000L),
                Category(0, "Hiếu hỉ", "EXPENSE", "💌", "#F43F5E", 500000L),
                Category(0, "Tiết kiệm", "EXPENSE", "🏦", "#10B981", 2000000L),
                Category(0, "Trả nợ", "EXPENSE", "💳", "#64748B", 1000000L)
            )
        }
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE $TABLE_CATEGORIES (
                $COL_CAT_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_CAT_NAME TEXT NOT NULL,
                $COL_CAT_TYPE TEXT NOT NULL,
                $COL_CAT_ICON TEXT NOT NULL,
                $COL_CAT_COLOR TEXT NOT NULL,
                $COL_CAT_BUDGET INTEGER NOT NULL DEFAULT 0
            )
        """.trimIndent())

        db.execSQL("""
            CREATE TABLE $TABLE_TRANSACTIONS (
                $COL_TX_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_TX_AMOUNT INTEGER NOT NULL,
                $COL_TX_TYPE TEXT NOT NULL,
                $COL_TX_CAT_ID INTEGER NOT NULL,
                $COL_TX_NOTE TEXT,
                $COL_TX_DATE INTEGER NOT NULL,
                FOREIGN KEY($COL_TX_CAT_ID) REFERENCES $TABLE_CATEGORIES($COL_CAT_ID) ON DELETE CASCADE
            )
        """.trimIndent())

        db.execSQL("""
            CREATE TABLE $TABLE_SETTINGS (
                $COL_SETTING_KEY TEXT PRIMARY KEY,
                $COL_SETTING_VAL TEXT NOT NULL
            )
        """.trimIndent())

        // Cài đặt ngân sách tổng mặc định (14.400.000 đ)
        val cvSetting = ContentValues().apply {
            put(COL_SETTING_KEY, SETTING_TOTAL_BUDGET)
            put(COL_SETTING_VAL, "14400000")
        }
        db.insert(TABLE_SETTINGS, null, cvSetting)

        // Seed danh mục mẫu
        seedDefaultData(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            upgradeToVersion2(db)
        }
    }

    private fun upgradeToVersion2(db: SQLiteDatabase) {
        // 1. Đồng bộ đổi tên các danh mục cũ nếu có
        val renames = mapOf(
            "Tiền lương" to "Lương",
            "Tiền thưởng" to "Thưởng",
            "Nghề phụ / Freelance" to "Làm thêm",
            "Di chuyển" to "Đi lại",
            "Hóa đơn sinh hoạt" to "Nhà ở",
            "Cà phê & Bạn bè" to "Giải trí",
            "Sức khỏe & Thuốc" to "Sức khỏe",
            "Chi tiêu khác" to "Khác"
        )
        for ((oldName, newName) in renames) {
            val cv = ContentValues().apply {
                put(COL_CAT_NAME, newName)
            }
            db.update(TABLE_CATEGORIES, cv, "$COL_CAT_NAME = ?", arrayOf(oldName))
        }

        // 2. Chèn bổ sung các danh mục mặc định còn thiếu
        val defaultCategories = getDefaultCategoriesList()
        for (cat in defaultCategories) {
            val cursor = db.query(
                TABLE_CATEGORIES,
                arrayOf(COL_CAT_ID),
                "$COL_CAT_NAME = ? AND $COL_CAT_TYPE = ?",
                arrayOf(cat.name, cat.type),
                null, null, null
            )
            val exists = cursor.use { it.moveToFirst() }
            if (!exists) {
                val cv = ContentValues().apply {
                    put(COL_CAT_NAME, cat.name)
                    put(COL_CAT_TYPE, cat.type)
                    put(COL_CAT_ICON, cat.icon)
                    put(COL_CAT_COLOR, cat.colorHex)
                    put(COL_CAT_BUDGET, cat.budget)
                }
                db.insert(TABLE_CATEGORIES, null, cv)
            }
        }
    }

    private fun seedDefaultData(db: SQLiteDatabase) {
        val categories = getDefaultCategoriesList()

        val catIds = mutableMapOf<String, Long>()
        for (cat in categories) {
            val cv = ContentValues().apply {
                put(COL_CAT_NAME, cat.name)
                put(COL_CAT_TYPE, cat.type)
                put(COL_CAT_ICON, cat.icon)
                put(COL_CAT_COLOR, cat.colorHex)
                put(COL_CAT_BUDGET, cat.budget)
            }
            val id = db.insert(TABLE_CATEGORIES, null, cv)
            catIds[cat.name] = id
        }

        // Tạo một số giao dịch mẫu trong tháng hiện tại
        val calendar = Calendar.getInstance()
        val year = calendar.get(Calendar.YEAR)
        val month = calendar.get(Calendar.MONTH) // 0-indexed

        fun makeTime(day: Int, hour: Int, minute: Int): Long {
            val c = Calendar.getInstance()
            c.set(year, month, day, hour, minute, 0)
            return c.timeInMillis
        }

        val sampleTx = listOf(
            Triple("Lương", 15000000L to "Lương tháng chuyển khoản", makeTime(1, 9, 30)),
            Triple("Thưởng", 2000000L to "Thưởng KPI quý", makeTime(5, 14, 0)),
            Triple("Làm thêm", 1200000L to "Dự án ngoài giờ", makeTime(10, 18, 0)),
            Triple("Đầu tư", 800000L to "Cổ tức & lãi đầu tư", makeTime(15, 10, 30)),
            Triple("Ăn uống", 45000L to "Bún bò sáng", makeTime(2, 7, 30)),
            Triple("Giải trí", 65000L to "Cà phê cùng bạn", makeTime(2, 9, 15)),
            Triple("Đi lại", 70000L to "Đổ xăng xe máy", makeTime(3, 11, 0)),
            Triple("Nhà ở", 850000L to "Tiền điện & nước tháng", makeTime(5, 10, 0)),
            Triple("Mua sắm", 420000L to "Áo phông thể thao", makeTime(8, 16, 45)),
            Triple("Ăn uống", 230000L to "Lẩu liên hoan cùng đồng nghiệp", makeTime(10, 19, 30)),
            Triple("Giáo dục", 350000L to "Mua sách chuyên ngành", makeTime(11, 14, 20)),
            Triple("Sức khỏe", 120000L to "Vitamin C & khẩu trang", makeTime(12, 15, 20)),
            Triple("Ăn uống", 55000L to "Cơm tấm trưa", makeTime(14, 12, 0)),
            Triple("Đi lại", 120000L to "Grab car đi họp", makeTime(15, 8, 45)),
            Triple("Hiếu hỉ", 500000L to "Mừng đám cưới bạn", makeTime(16, 18, 0)),
            Triple("Tiết kiệm", 2000000L to "Gửi tích lũy ngân hàng", makeTime(17, 10, 0)),
            Triple("Trả nợ", 1000000L to "Thanh toán trả góp thẻ", makeTime(18, 9, 0))
        )

        for ((catName, info, time) in sampleTx) {
            val (amount, note) = info
            val catId = catIds[catName] ?: continue
            val isIncome = catName in listOf("Lương", "Thưởng", "Làm thêm", "Đầu tư", "Khác")
            val cv = ContentValues().apply {
                put(COL_TX_AMOUNT, amount)
                put(COL_TX_TYPE, if (isIncome) "INCOME" else "EXPENSE")
                put(COL_TX_CAT_ID, catId)
                put(COL_TX_NOTE, note)
                put(COL_TX_DATE, time)
            }
            db.insert(TABLE_TRANSACTIONS, null, cv)
        }
    }

    // --- Danh mục (Categories) CRUD ---

    fun getAllCategories(typeFilter: String? = null): List<Category> {
        val list = mutableListOf<Category>()
        val db = readableDatabase
        val selection = if (typeFilter != null) "$COL_CAT_TYPE = ?" else null
        val selectionArgs = if (typeFilter != null) arrayOf(typeFilter) else null
        val cursor = db.query(TABLE_CATEGORIES, null, selection, selectionArgs, null, null, "$COL_CAT_TYPE ASC, $COL_CAT_NAME ASC")
        cursor.use {
            while (it.moveToNext()) {
                list.add(cursorToCategory(it))
            }
        }
        return list
    }

    fun getCategoryById(id: Long): Category? {
        val db = readableDatabase
        val cursor = db.query(TABLE_CATEGORIES, null, "$COL_CAT_ID = ?", arrayOf(id.toString()), null, null, null)
        cursor.use {
            if (it.moveToFirst()) {
                return cursorToCategory(it)
            }
        }
        return null
    }

    fun insertCategory(cat: Category): Long {
        val db = writableDatabase
        val cv = ContentValues().apply {
            put(COL_CAT_NAME, cat.name)
            put(COL_CAT_TYPE, cat.type)
            put(COL_CAT_ICON, cat.icon)
            put(COL_CAT_COLOR, cat.colorHex)
            put(COL_CAT_BUDGET, cat.budget)
        }
        return db.insert(TABLE_CATEGORIES, null, cv)
    }

    fun updateCategory(cat: Category): Boolean {
        val db = writableDatabase
        val cv = ContentValues().apply {
            put(COL_CAT_NAME, cat.name)
            put(COL_CAT_TYPE, cat.type)
            put(COL_CAT_ICON, cat.icon)
            put(COL_CAT_COLOR, cat.colorHex)
            put(COL_CAT_BUDGET, cat.budget)
        }
        return db.update(TABLE_CATEGORIES, cv, "$COL_CAT_ID = ?", arrayOf(cat.id.toString())) > 0
    }

    fun updateCategoryBudget(categoryId: Long, budget: Long): Boolean {
        val db = writableDatabase
        val cv = ContentValues().apply {
            put(COL_CAT_BUDGET, budget)
        }
        return db.update(TABLE_CATEGORIES, cv, "$COL_CAT_ID = ?", arrayOf(categoryId.toString())) > 0
    }

    fun deleteCategory(id: Long): Boolean {
        val db = writableDatabase
        return db.delete(TABLE_CATEGORIES, "$COL_CAT_ID = ?", arrayOf(id.toString())) > 0
    }

    private fun cursorToCategory(c: Cursor): Category {
        return Category(
            id = c.getLong(c.getColumnIndexOrThrow(COL_CAT_ID)),
            name = c.getString(c.getColumnIndexOrThrow(COL_CAT_NAME)),
            type = c.getString(c.getColumnIndexOrThrow(COL_CAT_TYPE)),
            icon = c.getString(c.getColumnIndexOrThrow(COL_CAT_ICON)),
            colorHex = c.getString(c.getColumnIndexOrThrow(COL_CAT_COLOR)),
            budget = c.getLong(c.getColumnIndexOrThrow(COL_CAT_BUDGET))
        )
    }

    // --- Giao dịch (Transactions) CRUD ---

    fun insertTransaction(tx: Transaction): Long {
        val db = writableDatabase
        val cv = ContentValues().apply {
            put(COL_TX_AMOUNT, tx.amount)
            put(COL_TX_TYPE, tx.type)
            put(COL_TX_CAT_ID, tx.categoryId)
            put(COL_TX_NOTE, tx.note)
            put(COL_TX_DATE, tx.dateEpoch)
        }
        return db.insert(TABLE_TRANSACTIONS, null, cv)
    }

    fun updateTransaction(tx: Transaction): Boolean {
        val db = writableDatabase
        val cv = ContentValues().apply {
            put(COL_TX_AMOUNT, tx.amount)
            put(COL_TX_TYPE, tx.type)
            put(COL_TX_CAT_ID, tx.categoryId)
            put(COL_TX_NOTE, tx.note)
            put(COL_TX_DATE, tx.dateEpoch)
        }
        return db.update(TABLE_TRANSACTIONS, cv, "$COL_TX_ID = ?", arrayOf(tx.id.toString())) > 0
    }

    fun deleteTransaction(id: Long): Boolean {
        val db = writableDatabase
        return db.delete(TABLE_TRANSACTIONS, "$COL_TX_ID = ?", arrayOf(id.toString())) > 0
    }

    private fun getMonthTimestamps(year: Int, month: Int): Pair<Long, Long> {
        val startCal = Calendar.getInstance().apply {
            set(year, month, 1, 0, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val endCal = Calendar.getInstance().apply {
            set(year, month, getActualMaximum(Calendar.DAY_OF_MONTH), 23, 59, 59)
            set(Calendar.MILLISECOND, 999)
        }
        return Pair(startCal.timeInMillis, endCal.timeInMillis)
    }

    fun getTransactionsByMonth(
        year: Int,
        month: Int,
        typeFilter: String? = null,
        searchQuery: String? = null
    ): List<Transaction> {
        val (startTime, endTime) = getMonthTimestamps(year, month)
        val list = mutableListOf<Transaction>()
        val db = readableDatabase

        val queryBuilder = StringBuilder("""
            SELECT t.*, c.$COL_CAT_NAME, c.$COL_CAT_ICON, c.$COL_CAT_COLOR 
            FROM $TABLE_TRANSACTIONS t
            INNER JOIN $TABLE_CATEGORIES c ON t.$COL_TX_CAT_ID = c.$COL_CAT_ID
            WHERE t.$COL_TX_DATE >= ? AND t.$COL_TX_DATE <= ?
        """.trimIndent())

        val args = mutableListOf(startTime.toString(), endTime.toString())

        if (!typeFilter.isNullOrBlank() && typeFilter != "ALL") {
            queryBuilder.append(" AND t.$COL_TX_TYPE = ?")
            args.add(typeFilter)
        }

        if (!searchQuery.isNullOrBlank()) {
            queryBuilder.append(" AND (t.$COL_TX_NOTE LIKE ? OR c.$COL_CAT_NAME LIKE ?)")
            args.add("%$searchQuery%")
            args.add("%$searchQuery%")
        }

        queryBuilder.append(" ORDER BY t.$COL_TX_DATE DESC")

        val cursor = db.rawQuery(queryBuilder.toString(), args.toTypedArray())
        cursor.use {
            while (it.moveToNext()) {
                list.add(
                    Transaction(
                        id = it.getLong(it.getColumnIndexOrThrow(COL_TX_ID)),
                        amount = it.getLong(it.getColumnIndexOrThrow(COL_TX_AMOUNT)),
                        type = it.getString(it.getColumnIndexOrThrow(COL_TX_TYPE)),
                        categoryId = it.getLong(it.getColumnIndexOrThrow(COL_TX_CAT_ID)),
                        categoryName = it.getString(it.getColumnIndexOrThrow(COL_CAT_NAME)),
                        categoryIcon = it.getString(it.getColumnIndexOrThrow(COL_CAT_ICON)),
                        categoryColorHex = it.getString(it.getColumnIndexOrThrow(COL_CAT_COLOR)),
                        note = it.getString(it.getColumnIndexOrThrow(COL_TX_NOTE)) ?: "",
                        dateEpoch = it.getLong(it.getColumnIndexOrThrow(COL_TX_DATE))
                    )
                )
            }
        }
        return list
    }

    // --- Tổng kết & Ngân sách (Budget) ---

    fun getMonthSummary(year: Int, month: Int): MonthSummary {
        val (startTime, endTime) = getMonthTimestamps(year, month)
        val db = readableDatabase
        val sql = """
            SELECT $COL_TX_TYPE, SUM($COL_TX_AMOUNT) as total 
            FROM $TABLE_TRANSACTIONS 
            WHERE $COL_TX_DATE >= ? AND $COL_TX_DATE <= ? 
            GROUP BY $COL_TX_TYPE
        """.trimIndent()

        var income = 0L
        var expense = 0L
        val cursor = db.rawQuery(sql, arrayOf(startTime.toString(), endTime.toString()))
        cursor.use {
            while (it.moveToNext()) {
                val type = it.getString(it.getColumnIndexOrThrow(COL_TX_TYPE))
                val total = it.getLong(it.getColumnIndexOrThrow("total"))
                if (type == "INCOME") income = total else if (type == "EXPENSE") expense = total
            }
        }
        return MonthSummary(totalIncome = income, totalExpense = expense, balance = income - expense)
    }

    fun getOverallBudgetLimit(): Long {
        val db = readableDatabase
        val cursor = db.query(TABLE_SETTINGS, arrayOf(COL_SETTING_VAL), "$COL_SETTING_KEY = ?", arrayOf(SETTING_TOTAL_BUDGET), null, null, null)
        cursor.use {
            if (it.moveToFirst()) {
                return it.getString(0).toLongOrNull() ?: 0L
            }
        }
        return 0L
    }

    fun setOverallBudgetLimit(amount: Long) {
        val db = writableDatabase
        val cv = ContentValues().apply {
            put(COL_SETTING_KEY, SETTING_TOTAL_BUDGET)
            put(COL_SETTING_VAL, amount.toString())
        }
        db.insertWithOnConflict(TABLE_SETTINGS, null, cv, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun getOverallBudget(year: Int, month: Int): OverallBudget {
        val totalBudget = getOverallBudgetLimit()
        val summary = getMonthSummary(year, month)
        val spent = summary.totalExpense
        val remaining = totalBudget - spent
        val percentage = if (totalBudget > 0) (spent.toFloat() / totalBudget.toFloat()) else 0f
        return OverallBudget(
            totalBudget = totalBudget,
            totalExpense = spent,
            remaining = remaining,
            percentage = percentage,
            isOverBudget = totalBudget > 0 && spent > totalBudget
        )
    }

    fun getCategoryBudgets(year: Int, month: Int): List<CategoryBudget> {
        val (startTime, endTime) = getMonthTimestamps(year, month)
        val db = readableDatabase

        // Lấy chi tiêu từng danh mục chi phí
        val sql = """
            SELECT c.*, COALESCE(SUM(t.$COL_TX_AMOUNT), 0) as spent
            FROM $TABLE_CATEGORIES c
            LEFT JOIN $TABLE_TRANSACTIONS t 
                ON c.$COL_CAT_ID = t.$COL_TX_CAT_ID 
                AND t.$COL_TX_DATE >= ? AND t.$COL_TX_DATE <= ?
            WHERE c.$COL_CAT_TYPE = 'EXPENSE'
            GROUP BY c.$COL_CAT_ID
            ORDER BY c.$COL_CAT_BUDGET DESC, spent DESC
        """.trimIndent()

        val list = mutableListOf<CategoryBudget>()
        val cursor = db.rawQuery(sql, arrayOf(startTime.toString(), endTime.toString()))
        cursor.use {
            while (it.moveToNext()) {
                val cat = cursorToCategory(it)
                val spent = it.getLong(it.getColumnIndexOrThrow("spent"))
                val budget = cat.budget
                val percentage = if (budget > 0) (spent.toFloat() / budget.toFloat()) else 0f
                val remaining = budget - spent
                list.add(
                    CategoryBudget(
                        category = cat,
                        spentAmount = spent,
                        budgetAmount = budget,
                        percentage = percentage,
                        remaining = remaining,
                        isOverBudget = budget > 0 && spent > budget
                    )
                )
            }
        }
        return list
    }

    // --- Thống kê biểu đồ (Analytics) ---

    fun getCategoryExpenseStats(year: Int, month: Int): List<CategoryStat> {
        val (startTime, endTime) = getMonthTimestamps(year, month)
        val db = readableDatabase

        val sql = """
            SELECT c.$COL_CAT_NAME, c.$COL_CAT_ICON, c.$COL_CAT_COLOR, SUM(t.$COL_TX_AMOUNT) as total
            FROM $TABLE_TRANSACTIONS t
            INNER JOIN $TABLE_CATEGORIES c ON t.$COL_TX_CAT_ID = c.$COL_CAT_ID
            WHERE t.$COL_TX_TYPE = 'EXPENSE' AND t.$COL_TX_DATE >= ? AND t.$COL_TX_DATE <= ?
            GROUP BY c.$COL_CAT_ID
            ORDER BY total DESC
        """.trimIndent()

        val rawStats = mutableListOf<Triple<String, Pair<String, String>, Long>>()
        var overallExpense = 0L

        val cursor = db.rawQuery(sql, arrayOf(startTime.toString(), endTime.toString()))
        cursor.use {
            while (it.moveToNext()) {
                val name = it.getString(it.getColumnIndexOrThrow(COL_CAT_NAME))
                val icon = it.getString(it.getColumnIndexOrThrow(COL_CAT_ICON))
                val color = it.getString(it.getColumnIndexOrThrow(COL_CAT_COLOR))
                val total = it.getLong(it.getColumnIndexOrThrow("total"))
                overallExpense += total
                rawStats.add(Triple(name, Pair(icon, color), total))
            }
        }

        return rawStats.map { (name, iconColor, amount) ->
            val pct = if (overallExpense > 0) (amount.toFloat() / overallExpense.toFloat()) else 0f
            CategoryStat(
                categoryName = name,
                categoryIcon = iconColor.first,
                colorHex = iconColor.second,
                amount = amount,
                percentage = pct
            )
        }
    }
}
