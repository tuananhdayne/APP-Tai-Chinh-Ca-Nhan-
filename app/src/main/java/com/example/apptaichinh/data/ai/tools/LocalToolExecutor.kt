package com.example.apptaichinh.data.ai.tools

import com.example.apptaichinh.data.db.FinanceDatabaseHelper
import com.example.apptaichinh.data.model.Category
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar

/**
 * Thực thi các Query Tools cục bộ trên thiết bị thông qua SQLite Database
 * và chuyển đổi kết quả thành JSON string để nạp lại vào vòng lặp đa tác tử.
 */
class LocalToolExecutor(private val dbHelper: FinanceDatabaseHelper) {

    fun isQueryTool(toolName: String): Boolean {
        return toolName in setOf(
            "query_balance_summary",
            "query_category_budget",
            "find_transactions",
            "query_categories"
        )
    }

    fun executeQueryTool(toolName: String, args: JSONObject, categories: List<Category>): String {
        val calendar = Calendar.getInstance()
        val currentYear = calendar.get(Calendar.YEAR)
        val currentMonth = calendar.get(Calendar.MONTH) // 0-11

        return when (toolName) {
            "query_balance_summary" -> {
                val offset = args.optInt("month_offset", 0)
                val targetCal = Calendar.getInstance().apply { add(Calendar.MONTH, offset) }
                val y = targetCal.get(Calendar.YEAR)
                val m = targetCal.get(Calendar.MONTH)

                val summary = dbHelper.getMonthSummary(y, m)
                val overallBudget = dbHelper.getOverallBudget(y, m)
                val catBudgets = dbHelper.getCategoryBudgets(y, m)
                val overBudgetCategories = catBudgets.filter { it.isOverBudget }

                val isOver = overallBudget.isOverBudget
                val overAmount = if (isOver) (overallBudget.totalExpense - overallBudget.totalBudget) else 0L
                val netBalance = summary.balance
                val isDeficit = netBalance < 0
                val deficitAmount = if (isDeficit) Math.abs(netBalance) else 0L

                JSONObject().apply {
                    put("year", y)
                    put("month", m + 1)
                    put("total_income_vnd", summary.totalIncome)
                    put("total_income_formatted", formatVndAmount(summary.totalIncome))
                    put("total_expense_vnd", summary.totalExpense)
                    put("total_expense_formatted", formatVndAmount(summary.totalExpense))
                    put("net_balance_vnd", summary.balance)
                    if (isDeficit) {
                        put("net_balance_status", "THÂM HỤT (Chi nhiều hơn Thu)")
                        put("net_deficit_formatted", formatVndAmount(deficitAmount))
                    } else {
                        put("net_balance_status", "DƯ (Thu nhiều hơn Chi)")
                        put("net_surplus_formatted", formatVndAmount(netBalance))
                    }
                    put("total_budget_limit_vnd", overallBudget.totalBudget)
                    put("total_budget_limit_formatted", formatVndAmount(overallBudget.totalBudget))
                    put("remaining_budget_vnd", if (isOver) 0L else overallBudget.remaining)
                    put("remaining_budget_formatted", if (isOver) "0 đ" else formatVndAmount(overallBudget.remaining))
                    put("percentage_spent", "${(overallBudget.percentage * 100).toInt()}%")
                    put("is_over_budget", isOver)
                    put("budget_status", if (isOver) "ĐÃ VƯỢT NGÂN SÁCH" else "TRONG HẠN MỨC")
                    if (isOver) {
                        put("over_budget_amount_vnd", overAmount)
                        put("over_budget_amount_formatted", formatVndAmount(overAmount))
                        put("budget_verdict", "Đang VƯỢT NGÂN SÁCH ${formatVndAmount(overAmount)} (Hạn mức: ${formatVndAmount(overallBudget.totalBudget)}, Đã chi tiêu: ${formatVndAmount(overallBudget.totalExpense)}). TUYỆT ĐỐI KHÔNG dùng từ 'còn thiếu', phải nói rõ là 'ĐANG VƯỢT NGÂN SÁCH ${formatVndAmount(overAmount)}'.")
                    } else {
                        put("over_budget_amount_vnd", 0L)
                        put("over_budget_amount_formatted", "0 đ")
                        put("budget_verdict", "Còn lại ${formatVndAmount(overallBudget.remaining)} trong hạn mức ngân sách tháng.")
                    }
                    put("over_budget_category_count", overBudgetCategories.size)
                    if (overBudgetCategories.isNotEmpty()) {
                        val overList = JSONArray()
                        overBudgetCategories.forEach {
                            val catOver = it.spentAmount - it.budgetAmount
                            overList.put("${it.category.icon} ${it.category.name}: Hạn mức ${formatVndAmount(it.budgetAmount)}, đã tiêu ${formatVndAmount(it.spentAmount)} -> VƯỢT HẠN MỨC ${formatVndAmount(catOver)}")
                        }
                        put("over_budget_categories", overList)
                    }
                }.toString()
            }

            "query_category_budget" -> {
                val offset = args.optInt("month_offset", 0)
                val targetCal = Calendar.getInstance().apply { add(Calendar.MONTH, offset) }
                val y = targetCal.get(Calendar.YEAR)
                val m = targetCal.get(Calendar.MONTH)

                val catQuery = args.optString("category_name", "").trim()
                val catBudgets = dbHelper.getCategoryBudgets(y, m)

                var targetBudgets = if (catQuery.isBlank()) {
                    catBudgets
                } else {
                    catBudgets.filter {
                        it.category.name.contains(catQuery, ignoreCase = true) ||
                                catQuery.contains(it.category.name, ignoreCase = true)
                    }
                }

                // Nếu tìm chính xác tên không có, dùng matchBestCategory thông minh
                if (targetBudgets.isEmpty() && catQuery.isNotBlank()) {
                    val (matched, isFallback) = matchBestCategory(catQuery, "EXPENSE", catBudgets.map { it.category })
                    if (!isFallback && matched != null) {
                        targetBudgets = catBudgets.filter { it.category.id == matched.id }
                    }
                }

                val resultList = JSONArray()
                targetBudgets.forEach { cb ->
                    val isCatOver = cb.isOverBudget
                    val catOverAmount = if (isCatOver) (cb.spentAmount - cb.budgetAmount) else 0L
                    resultList.put(JSONObject().apply {
                        put("category", "${cb.category.icon} ${cb.category.name}")
                        put("budget_limit_vnd", cb.budgetAmount)
                        put("budget_limit_formatted", formatVndAmount(cb.budgetAmount))
                        put("spent_vnd", cb.spentAmount)
                        put("spent_formatted", formatVndAmount(cb.spentAmount))
                        put("remaining_vnd", if (isCatOver) 0L else cb.remaining)
                        put("remaining_formatted", if (isCatOver) "0 đ" else formatVndAmount(cb.remaining))
                        put("percentage_spent", "${(cb.percentage * 100).toInt()}%")
                        put("is_over_budget", isCatOver)
                        if (isCatOver) {
                            put("status", "ĐÃ VƯỢT HẠN MỨC")
                            put("over_budget_amount_vnd", catOverAmount)
                            put("over_budget_amount_formatted", formatVndAmount(catOverAmount))
                            put("category_verdict", "Đã vượt hạn mức ${formatVndAmount(catOverAmount)} (Hạn mức: ${formatVndAmount(cb.budgetAmount)}, Đã chi: ${formatVndAmount(cb.spentAmount)}). KHÔNG nói 'còn thiếu', hãy nói 'đã vượt hạn mức ${formatVndAmount(catOverAmount)}'.")
                        } else {
                            put("status", "TRONG HẠN MỨC")
                            put("over_budget_amount_vnd", 0L)
                            put("over_budget_amount_formatted", "0 đ")
                            put("category_verdict", "Còn lại ${formatVndAmount(cb.remaining)} trong hạn mức.")
                        }
                    })
                }

                JSONObject().apply {
                    put("year", y)
                    put("month", m + 1)
                    put("over_budget_count", targetBudgets.count { it.isOverBudget })
                    put("results", resultList)
                }.toString()
            }

            "find_transactions" -> {
                val keyword = args.optString("keyword", "").trim()
                val type = args.optString("type", "ALL").ifBlank { "ALL" }
                val limit = args.optInt("limit", 5).coerceIn(1, 10)

                val txList = dbHelper.getTransactionsByMonth(currentYear, currentMonth, type, keyword.ifBlank { null })
                    .take(limit)

                val jsonArray = JSONArray()
                txList.forEach { tx ->
                    jsonArray.put(JSONObject().apply {
                        put("id", tx.id)
                        put("amount_vnd", tx.amount)
                        put("type", tx.type)
                        put("category", "${tx.categoryIcon} ${tx.categoryName}")
                        put("note", tx.note)
                        put("timestamp", tx.dateEpoch)
                    })
                }

                JSONObject().apply {
                    put("found_count", txList.size)
                    put("transactions", jsonArray)
                }.toString()
            }

            "query_categories" -> {
                val requestedType = args.optString("type", "EXPENSE").uppercase()
                val normalizedType = if (requestedType == "INCOME") "INCOME" else "EXPENSE"
                val userText = (if (args.has("user_text")) args.optString("user_text") else args.optString("keyword", "")).trim()

                val targetCats = categories.filter { it.type.equals(normalizedType, ignoreCase = true) }
                val (matchedCat, isFallback) = matchBestCategory(userText, normalizedType, targetCats)
                val detectedAmount = extractAmountFromText(userText)

                val availableCatsArray = JSONArray()
                targetCats.forEach { cat ->
                    availableCatsArray.put(JSONObject().apply {
                        put("id", cat.id)
                        put("name", cat.name)
                        put("icon", cat.icon)
                        put("type", cat.type)
                    })
                }

                JSONObject().apply {
                    put("type", normalizedType)
                    put("type_name", if (normalizedType == "INCOME") "Thu nhập" else "Chi tiêu")
                    put("user_text", userText)
                    if (detectedAmount != null) {
                        put("detected_amount_vnd", detectedAmount)
                    }
                    if (matchedCat != null) {
                        put("selected_category", JSONObject().apply {
                            put("id", matchedCat.id)
                            put("name", matchedCat.name)
                            put("icon", matchedCat.icon)
                            put("is_fallback_default", isFallback)
                        })
                    }
                    put("available_categories", availableCatsArray)
                    val amountStr = if (detectedAmount != null) " Số tiền nhận diện được là $detectedAmount đ." else ""
                    val amountArg = if (detectedAmount != null) " và amount=$detectedAmount" else ""
                    put(
                        "instruction",
                        "Đã đối chiếu câu nói và chọn danh mục '${matchedCat?.name ?: "Khác"}'.$amountStr Bước tiếp theo: HÃY GỌI 'create_transaction' với category_name='${matchedCat?.name ?: "Khác"}'$amountArg. TUYỆT ĐỐI KHÔNG gọi lại 'query_categories'."
                    )
                }.toString()
            }

            else -> JSONObject().apply { put("error", "Unknown tool: $toolName") }.toString()
        }
    }

    companion object {
        /**
         * Trích xuất số tiền chuẩn xác từ câu nói tiếng Việt của người dùng.
         * Khắc phục triệt để lỗi LLM tính nhẩm sai số 0 (như 100k thành 10k).
         */
        fun extractAmountFromText(text: String): Long? {
            val cleanText = text.lowercase().trim()

            // 1. Dạng triệu + nghìn: 1tr5, 2tr8, 1 triệu 5, 1 củ 2
            val trKRegex = Regex("""(\d+)\s*(?:tr|triệu|củ)\s*(\d+)\b""")
            val trKMatch = trKRegex.find(cleanText)
            if (trKMatch != null) {
                val millions = trKMatch.groupValues[1].toLongOrNull() ?: 0L
                val subStr = trKMatch.groupValues[2]
                val sub = subStr.toLongOrNull() ?: 0L
                val subAmount = when (subStr.length) {
                    1 -> sub * 100_000L
                    2 -> sub * 10_000L
                    else -> sub * 1_000L
                }
                return millions * 1_000_000L + subAmount
            }

            // 2. Dạng thập phân: 1.5tr, 1,5 triệu, 2.5 củ
            val decTrRegex = Regex("""(\d+)[,\.](\d+)\s*(?:tr|triệu|củ|m)\b""")
            val decTrMatch = decTrRegex.find(cleanText)
            if (decTrMatch != null) {
                val whole = decTrMatch.groupValues[1].toLongOrNull() ?: 0L
                val fracStr = decTrMatch.groupValues[2]
                val frac = fracStr.toLongOrNull() ?: 0L
                val fracAmount = when (fracStr.length) {
                    1 -> frac * 100_000L
                    2 -> frac * 10_000L
                    else -> frac * 1_000L
                }
                return whole * 1_000_000L + fracAmount
            }

            // 3. Dạng triệu chẵn: 15tr, 20 triệu, 5 củ
            val trRegex = Regex("""(\d+)\s*(?:tr|triệu|củ|m)\b""")
            val trMatch = trRegex.find(cleanText)
            if (trMatch != null) {
                val amount = trMatch.groupValues[1].toLongOrNull() ?: 0L
                return amount * 1_000_000L
            }

            // 4. Dạng nghìn: 100k, 50k, 10k, 500k, 50 ngàn, 60 nghìn, 25 cành
            val kRegex = Regex("""(\d+)\s*(?:k|nghìn|ngàn|cành)\b""")
            val kMatch = kRegex.find(cleanText)
            if (kMatch != null) {
                val amount = kMatch.groupValues[1].toLongOrNull() ?: 0L
                return amount * 1_000L
            }

            // 5. Dạng lít (tiếng lóng: 2 lít = 200k, 5 lít = 500k)
            val litRegex = Regex("""(\d+)\s*(?:lít|lit)\b""")
            val litMatch = litRegex.find(cleanText)
            if (litMatch != null) {
                val amount = litMatch.groupValues[1].toLongOrNull() ?: 0L
                return amount * 100_000L
            }

            // 6. Dạng số có dấu chấm/phẩy: 100.000, 100,000, 50.000đ, 1.500.000
            val formattedRegex = Regex("""(\d{1,3}(?:[.,]\d{3})+)\s*(?:đ|vnd|đồng)?""")
            val formattedMatch = formattedRegex.find(cleanText)
            if (formattedMatch != null) {
                val numStr = formattedMatch.groupValues[1].replace(".", "").replace(",", "")
                return numStr.toLongOrNull()
            }

            // 7. Dạng số nguyên có chữ đ/vnd/đồng: 100000đ, 50000 đồng
            val rawVndRegex = Regex("""(\d+)\s*(?:đ|vnd|đồng)\b""")
            val rawVndMatch = rawVndRegex.find(cleanText)
            if (rawVndMatch != null) {
                return rawVndMatch.groupValues[1].toLongOrNull()
            }

            // 8. Dạng số nguyên >= 1000 đứng độc lập
            val standaloneRegex = Regex("""\b(\d{4,})\b""")
            val standaloneMatch = standaloneRegex.find(cleanText)
            if (standaloneMatch != null) {
                return standaloneMatch.groupValues[1].toLongOrNull()
            }

            return null
        }

        /**
         * Tách một câu chứa nhiều khoản chi tiêu/thu nhập thành tối đa 6 mệnh đề độc lập.
         * Hỗ trợ đầy đủ:
         * 1. Phân tách bằng dấu câu: ",", ";", xuống dòng
         * 2. Phân tách bằng từ nối: "và", "với", "rồi", "kèm theo"
         * 3. Phân tách khi người dùng viết liền không có dấu phẩy (VD: "nay ăn sáng 200k đổ xăng 100k , đóng tiền điện 1 triệu")
         */
        fun splitMultiItemText(text: String): List<String> {
            val trimmed = text.trim()
            if (trimmed.isBlank()) return listOf(text)

            // Tách sơ bộ theo dấu phẩy, chấm phẩy, xuống dòng và các liên từ phổ biến
            val rawParts = trimmed.split(Regex("[,;\n]+|\\s+(?:và|với|rồi|kèm theo)\\s+"))
                .map { it.trim() }
                .filter { it.isNotBlank() }

            // Regex nhận diện các cụm số tiền tài chính tiếng Việt
            val amountRegex = Regex(
                """(?i)(\d+\s*tr\s*\d+|\d+[.,]\d+\s*(?:tr|triệu|trieu|củ|m)(?![a-zA-ZÀ-ỹ0-9])|\d+\s*(?:triệu|trieu|nghìn|nghin|ngàn|ngan|cành|đồng|vnd|củ|lít|lit|tr|k|m|đ)(?![a-zA-ZÀ-ỹ0-9])|\d{1,3}(?:[.,]\d{3})+(?:\s*(?:đ|vnd|đồng))?|\b\d{4,}\b)"""
            )

            val finalClauses = mutableListOf<String>()

            for (part in rawParts) {
                val matches = amountRegex.findAll(part).toList()
                if (matches.size <= 1) {
                    finalClauses.add(cleanClauseLeadingNoise(part))
                } else {
                    // Trong part có nhiều số tiền (VD: "nay ăn sáng 200k đổ xăng 100k")
                    // Kiểm tra cấu trúc: [Tên khoản] [Số tiền] hay [Số tiền] [Tên khoản]
                    val isAmountFirst = part.substring(0, matches[0].range.first).trim().isBlank()

                    if (isAmountFirst) {
                        // Dạng: 200k ăn sáng 100k đổ xăng
                        for (i in 0 until matches.size - 1) {
                            val sub = part.substring(matches[i].range.first, matches[i + 1].range.first).trim()
                            if (sub.isNotBlank()) finalClauses.add(cleanClauseLeadingNoise(sub))
                        }
                        val lastSub = part.substring(matches.last().range.first).trim()
                        if (lastSub.isNotBlank()) finalClauses.add(cleanClauseLeadingNoise(lastSub))
                    } else {
                        // Dạng phổ biến: "nay ăn sáng 200k đổ xăng 100k"
                        var start = 0
                        for (i in 0 until matches.size - 1) {
                            val end = matches[i].range.last + 1
                            val sub = part.substring(start, end).trim()
                            if (sub.isNotBlank()) finalClauses.add(cleanClauseLeadingNoise(sub))
                            start = end
                        }
                        val lastSub = part.substring(start).trim()
                        if (lastSub.isNotBlank()) finalClauses.add(cleanClauseLeadingNoise(lastSub))
                    }
                }
            }

            val partsWithAmount = finalClauses.filter { extractAmountFromText(it) != null }
            return if (partsWithAmount.size > 1) partsWithAmount.take(6) else listOf(text)
        }

        private fun cleanClauseLeadingNoise(clause: String): String {
            return clause.replace(Regex("^(?:[,;\\-\\s]|và|với|rồi|kèm theo|sau đó|tiếp|tiếp theo)+\\s*", RegexOption.IGNORE_CASE), "").trim()
        }

        /**
         * Phân loại nhanh ý định người dùng là Thu nhập (INCOME) hay Chi tiêu (EXPENSE).
         */
        fun isIncomeIntent(text: String): Boolean {
            val lower = text.lowercase().trim()
            val incomeKeywords = listOf(
                "nhận lương", "bắn lương", "lương", "thưởng", "thu nhập", "cổ tức", "tiền lãi",
                "lãi suất", "ting ting", "được cho", "được tặng", "kiếm được", "nhận tiền", "bán"
            )
            val expenseKeywords = listOf(
                "chi", "tiêu", "hết", "mua", "ăn", "uống", "đóng", "trả", "nộp", "thanh toán",
                "tốn", "mất", "đổ xăng", "gửi xe", "vé", "thuê", "phí", "sắm"
            )
            val incomeScore = incomeKeywords.count { lower.contains(it) }
            val expenseScore = expenseKeywords.count { lower.contains(it) }
            return incomeScore > expenseScore
        }

        /**
         * Đối chiếu toàn văn câu nói của người dùng với danh sách danh mục có sẵn trong DB
         * để tìm ra danh mục tương thích nhất.
         */
        fun matchBestCategory(
            userText: String,
            type: String,
            availableCategories: List<Category>
        ): Pair<Category?, Boolean> {
            val targetCats = availableCategories.filter { it.type.equals(type, ignoreCase = true) }
                .ifEmpty { availableCategories }
            if (targetCats.isEmpty()) return Pair(null, false)
            val text = userText.lowercase().trim()

            if (text.isNotBlank()) {
                val semanticMap: Map<String, List<String>> = if (type == "EXPENSE") {
                    mapOf(
                        "Ăn uống" to listOf(
                            "phở", "bún", "cơm", "bánh mì", "ăn trưa", "ăn sáng", "ăn tối", "bữa trưa", "bữa sáng", "bữa tối",
                            "cơm trưa", "cơm tối", "ăn", "uống", "tiền ăn", "ăn vặt", "ăn đêm",
                            "lẩu", "đồ ăn", "trà sữa", "thịt", "rau", "gà", "cá", "hủ tiếu", "chè", "đói", "bánh cuốn",
                            "bánh", "buffet", "kem", "cháo", "xôi", "trái cây", "hoa quả", "trà đá", "nước mía", "bánh bao",
                            "chợ", "đi chợ", "thực phẩm", "rau củ", "thịt cá", "tà tưa", "cành", "mì tôm", "mì gói",
                            "bún bò", "bún đậu", "cơm tấm", "bánh xèo", "sữa chua", "pizza", "pizaa", "kfc", "lotteria", "gà rán", "burger"
                        ),
                        "Đi lại" to listOf(
                            "xăng", "xe", "grab", "grab bike", "bike", "đi làm", "taxi", "bắt taxi", "đi taxi", "gửi xe", "vé xe", "bến xe", "sửa xe", "bảo dưỡng",
                            "vé tàu", "vé máy bay", "be", "gojek", "xanh sm", "cầu đường", "rửa xe", "nhớt", "xăng xe", "săm lốp", "vá xe",
                            "dầu nhớt", "máy bay", "tàu hỏa", "xe bus", "xe buýt", "phí cầu đường", "grab car", "be car", "be bike",
                            "trạm thu phí", "thu phí", "cao tốc", "bot", "vé cầu đường", "phí đường bộ"
                        ),
                        "Nhà ở" to listOf(
                            "tiền nhà", "tiền điện", "tiền nước", "mạng", "wifi", "tiền phòng", "phòng trọ", "tiền trọ",
                            "chung cư", "rác", "sửa nhà", "nội thất", "gas", "nước sinh hoạt", "tiền internet", "phí dịch vụ",
                            "thuê nhà", "thuê phòng", "tiền rác", "phí chung cư", "điện nước", "vnpt", "fpt", "viettel",
                            "thợ sửa", "thợ", "sửa chữa", "đường ống", "ống nước", "vòi sen", "bình gas", "bóng đèn", "đèn led",
                            "cước", "tiền cước", "cước điện thoại", "nạp điện thoại", "tiền điện thoại", "nạp thẻ điện thoại",
                            "thuê trọ", "tiền thuê trọ", "phí thuê nhà", "trả tiền thuê", "tiền phòng trọ", "nhà trọ", "phòng trọ sinh viên"
                        ),
                        "Mua sắm" to listOf(
                            "mua sắm", "shopping", "quần áo", "áo", "quần", "giày", "dép", "shopee", "tiki", "lazada",
                            "túi xách", "túi", "mỹ phẩm", "son", "đồng hồ", "đồ gia dụng", "bách hóa", "váy", "đầm", "phụ kiện",
                            "mua đồ", "sắm đồ", "quần jean", "áo sơ mi", "giày dép", "nước hoa", "bitis", "sneaker", "áo thun",
                            "chuột", "chuột máy tính", "bàn phím", "tai nghe", "sạc", "cáp sạc", "ốp lưng", "điện thoại", "laptop", "máy tính"
                        ),
                        "Giải trí" to listOf(
                            "cà phê", "cafe", "uống cà phê", "đi cà phê", "highland", "starbucks", "chém gió", "tụ tập",
                            "phim", "cinema", "nhậu", "bida", "karaoke", "du lịch", "chơi", "kịch", "xem kịch", "kịch nghệ",
                            "game", "nạp game", "bar", "pub", "hát", "nghỉ dưỡng", "vé xem phim", "vé ca nhạc", "bơi",
                            "bia hơi", "cafe muối", "uống cafe", "rạp chiếu phim", "nạp thẻ", "garena", "bowling", "hát hò",
                            "hát karaoke", "đi hát karaoke", "đi hát", "phòng hát"
                        ),
                        "Sức khỏe" to listOf(
                            "thuốc", "khám", "bệnh", "vitamin", "bác sĩ", "y tế", "nha khoa", "khẩu trang",
                            "bệnh viện", "xét nghiệm", "viện phí", "tiêm", "panadol", "khám răng", "nha sĩ",
                            "mua thuốc", "nhà thuốc", "pharmacity", "long châu", "hạ sốt", "hapacol", "kháng sinh",
                            "khám bệnh", "nhổ răng", "nhổ răng khôn", "lấy cao răng", "đi khám", "tiền khám",
                            "khám tổng quát", "phòng khám", "chữa bệnh", "đi viện"
                        ),
                        "Giáo dục" to listOf(
                            "sách", "học", "khóa học", "học phí", "bút", "vở", "giáo trình", "tiếng anh",
                            "trường", "lớp", "đào tạo", "gia sư", "dụng cụ học tập", "tiền học", "sách giáo khoa",
                            "sách vở", "dạy kèm", "ielts", "toeic"
                        ),
                        "Hiếu hỉ" to listOf(
                            "cưới", "hỏi", "sinh nhật", "mừng", "thăm", "thăm người ốm", "thăm bệnh", "thăm ốm",
                            "giỏ hoa quả", "tiệc", "phúng", "ma", "đám ma", "đám cưới", "tiệc cưới", "mừng cưới",
                            "phong bì cưới", "đầy tháng", "quà biếu", "lì xì", "phong bì", "chúc mừng", "thôi nôi", "viếng"
                        ),
                        "Tiết kiệm" to listOf(
                            "tiết kiệm", "gửi bank", "sổ tiết kiệm", "tích lũy", "gửi tiết kiệm", "gửi tích lũy",
                            "tiết kiệm online", "mở sổ tiết kiệm", "heo đất"
                        ),
                        "Trả nợ" to listOf(
                            "nợ", "vay", "trả góp", "thẻ tín dụng", "lãi", "vay mượn", "thanh toán thẻ",
                            "mượn tiền", "trả thẻ", "fe credit", "trả nợ", "trả tiền vay", "dư nợ thẻ", "home credit"
                        )
                    )
                } else {
                    mapOf(
                        "Lương" to listOf("lương", "salary", "chuyển khoản lương", "nhận lương", "ting ting", "bắn lương", "lương tháng", "lương cứng", "payroll"),
                        "Thưởng" to listOf("thưởng", "bonus", "kpi", "hoa hồng", "tiền thưởng", "thưởng tết", "thưởng kpi", "thưởng nóng", "thưởng quý", "thưởng dự án"),
                        "Làm thêm" to listOf("dự án", "freelance", "làm thêm", "part-time", "ot", "ngoài giờ", "thù lao", "tiền công", "tiền làm thêm", "dạy kèm", "shipper"),
                        "Đầu tư" to listOf("cổ phiếu", "lãi", "đầu tư", "crypto", "chứng khoán", "bất động sản", "coin", "vàng", "tiền lãi", "lãi tiết kiệm", "sổ tiết kiệm", "lãi ngân hàng", "cổ tức", "lãi suất")
                    )
                }

                var bestCategory: Category? = null
                var maxScore = 0

                val textTokens = text.split(" ", ",", ".", "!", "?", "-", ":", ";").filter { it.isNotBlank() }

                for (cat in targetCats) {
                    var score = 0
                    val nameLower = cat.name.lowercase()

                    // Điểm trực tiếp từ tên danh mục (ưu tiên nếu xuất hiện nguyên văn)
                    if (nameLower != "khác" && text.contains(nameLower)) {
                        score += nameLower.length * 5
                    }

                    val keywords = semanticMap[cat.name] ?: emptyList()
                    for (kw in keywords) {
                        val isMatched = if (kw.length <= 2) {
                            textTokens.contains(kw)
                        } else {
                            text.contains(kw)
                        }
                        if (isMatched) {
                            score += kw.length
                        }
                    }

                    // Hỗ trợ thêm cho các danh mục tùy chỉnh do người dùng tự đặt tên (loại trừ từ quá chung chung)
                    val catWords = cat.name.lowercase().split(" ", "_", "-")
                    for (w in catWords) {
                        if (w.length >= 3 && w !in setOf("khác", "mua", "sắm", "tiền", "khoản", "làm", "uống", "sinh", "hoạt") && text.contains(w)) {
                            score += w.length * 3
                        }
                    }

                    if (score > maxScore) {
                        maxScore = score
                        bestCategory = cat
                    }
                }

                if (bestCategory != null) {
                    return Pair(bestCategory, false)
                }
            }

            // 3. Fallback an toàn: Về danh mục "Khác" nếu không khớp
            val fallback = targetCats.find { it.name.equals("Khác", ignoreCase = true) }
                ?: targetCats.firstOrNull()
            return Pair(fallback, true)
        }

        /**
         * Định dạng số tiền VNĐ chuẩn mực kèm phân tách hàng nghìn bằng dấu chấm (VD: 18.310.000 đ)
         */
        fun formatVndAmount(amount: Long): String {
            val symbols = java.text.DecimalFormatSymbols(java.util.Locale.forLanguageTag("vi-VN")).apply {
                groupingSeparator = '.'
                decimalSeparator = ','
            }
            val formatter = java.text.DecimalFormat("#,###", symbols)
            return "${formatter.format(amount)} đ"
        }
    }
}
