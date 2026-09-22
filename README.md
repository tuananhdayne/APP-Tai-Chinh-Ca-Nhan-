# Ứng Dụng Quản Lý Tài Chính Cá Nhân (Personal Finance App)

Ứng dụng Sổ Thu Chi & Quản Lý Tài Chính Cá Nhân hiện đại trên nền tảng **Android Native**, được xây dựng bằng **Kotlin**, **Jetpack Compose (Material 3)**, cơ sở dữ liệu cục bộ **SQLite**, và tích hợp **Trợ Lý AI Tài Chính Thông Minh (ReAct Multi-Agent)**.

---

## 🌟 Tính Năng Nổi Bật (Cấu Trúc 5 Tab)

Ứng dụng được thiết kế tối ưu cho trải nghiệm người dùng hằng ngày với 5 màn hình chính:

### 1. ✍️ Tab 1: Nhập Vào Thủ Công (Ghi Chép Nhanh)
- **Chuyển đổi Thu / Chi tiện lợi:** Nút chọn nổi bật ở trên cùng giúp chuyển đổi giữa **Chi Tiêu (-)** và **Thu Nhập (+)**, tự động lọc danh mục tương ứng bên dưới.
- **Chọn Ngày (Date Picker):** Hiển thị ngày đã chọn (mặc định hôm nay), cho phép chọn bất kỳ ngày nào trong quá khứ hoặc hiện tại.
- **Nhập Số Tiền Trực Quan:** Định dạng VNĐ tự động kèm phím tắt cộng nhanh số tiền: `+10k`, `+20k`, `+50k`, `+100k`, `+200k`, `+500k`, `+1tr`, `Xóa`.
- **Lựa Chọn Danh Mục:** Danh sách danh mục phong phú với Emoji sinh động, viền nổi bật khi chọn và nút `+ Thêm mới` danh mục ngay tại chỗ.
- **Ghi Chú:** Trường nhập ghi chú rõ ràng cho từng giao dịch.
- **Hỗ trợ Gõ Tắt Bằng Câu Nói (Smart NLP & Voice):** Tự động phân tích câu văn tiếng Việt tự nhiên (VD: *"Ăn trưa 45k"*, *"Lương 15tr"*, *"Đổ xăng 70k"*) để trích xuất số tiền và phân loại danh mục tức thì. Tích hợp nút Micro để nói trực tiếp qua hệ thống nhận diện giọng nói có sẵn của Android.
- **Lưu Tức Thì:** Thông báo Snackbar xác nhận thành công và tự động làm mới form để sẵn sàng cho lần nhập tiếp theo.

### 2. 📅 Tab 2: Lịch Thu Chi Trực Quan
- **Lưới Lịch Tháng Thông Minh:** Hiển thị 7 ngày trong tuần (T2 đến CN).
  - Dưới mỗi ngày trong ô lịch hiển thị **Tổng Thu (màu xanh lá)** và **Tổng Chi (màu đỏ)** rõ ràng.
  - Đánh dấu nổi bật ngày đang chọn và ngày hôm nay.
- **Hàng Tổng Hợp (Summary Row):** 3 cột chỉ số nằm ngay dưới lịch:
  - **Thu Nhập:** Tổng số tiền thu.
  - **Chi Tiêu:** Tổng số tiền chi.
  - **Tổng (Số Dư):** Chênh lệch Thu - Chi (xanh nếu thặng dư, đỏ nếu thâm hụt).
  - Cho phép chuyển đổi xem tổng hợp của **Ngày đang chọn** hoặc **Cả tháng**.
- **Danh Sách Lịch Sử Giao Dịch:** Hiển thị chi tiết các khoản thu chi của ngày được chọn, cho phép nhấn vào để xem, chỉnh sửa hoặc xóa giao dịch.

### 3. 📊 Tab 3: Thống Kê & Phân Tích (Analytics)
- **Biểu Đồ Donut (Donut Chart):** Phân tích trực quan tỷ trọng chi tiêu theo từng nhóm danh mục (Ăn uống, Nhà ở, Mua sắm, Đi lại...).
- **So Sánh Thu Nhập vs Chi Tiêu:** Thanh so sánh tỷ lệ giữa dòng tiền vào và dòng tiền ra trong tháng.
- **Top Chi Tiêu Lớn Nhất:** Liệt kê các khoản chi tiêu có giá trị cao nhất trong tháng để dễ dàng kiểm soát dòng tiền lớn.

### 4. 💰 Tab 4: Quản Lý Ngân Sách (Budgeting)
- **Ngân Sách Tổng Thể Cả Tháng:** Thiết lập hạn mức chi tiêu chung, hiển thị số tiền đã tiêu, số dư còn lại, thanh tiến trình % và cảnh báo khi chi tiêu chạm ngưỡng hoặc vượt hạn mức.
- **Ngân Sách Từng Danh Mục:** Kiểm soát ngân sách chi tiết cho từng nhóm chi tiêu (Ăn uống, Giải trí, Mua sắm...), giúp chủ động hạn chế chi tiêu vượt kế hoạch.

### 5. ⚙️ Tab 5: Cài Đặt & Chức Năng Khác
- **Quản Lý Danh Mục:** Xem, thêm mới, sửa tên, đổi icon emoji, màu sắc và thiết lập hạn mức riêng cho cả danh mục Thu và Chi.
- **Trợ Lý AI Tài Chính (Hỗ Trợ Voice / Giọng Nói):** Cửa sổ trò chuyện trực tiếp với Trợ lý AI. Tích hợp nút Micro thu âm giọng nói với Speech-to-Text chuẩn của Android (`RecognizerIntent`), chuyển đổi trực tiếp giọng nói tiếng Việt thành văn bản để ra lệnh chi tiêu, hỏi đáp số dư và tra cứu lịch sử rảnh tay.
- **Cấu Hình Kết Nối Máy Chủ AI:** Hỗ trợ kết nối linh hoạt tới LM Studio, Ollama hoặc Cloudflare/Ngrok Tunnel với tính năng kiểm tra kết nối (Ping).
- **Thống Kê Sổ Dữ Liệu:** Xem tổng quan số lượng giao dịch đã ghi chép, số danh mục và hạn mức chi tiêu.

---

## 🤖 Kiến Trúc Trợ Lý AI (Safety-First Multi-Agent ReAct)

Ứng dụng tích hợp hệ thống Trợ lý AI hoạt động theo quy trình **ReAct (Reasoning + Acting Workflow)** kết hợp cơ chế an toàn dữ liệu:

```
[Câu nói người dùng] 
       │
       ▼
[1. Phân tích ý định & gọi Query Tool ngầm] ──► Truy vấn SQLite cục bộ
       │
       ▼
[2. LLM tạo đề xuất & gọi Action Tool]
       │
       ▼
[3. Hiển thị Thẻ Xem Trước (Preview Card)] ──► Người dùng duyệt / chỉnh sửa
       │
       ▼
[4. Người dùng bấm "Xác Nhận"] ─────────────► Lưu thay đổi vào SQLite
```

### Hệ Thống 16 Công Cụ (Function Calling Tools):
1. **Nhóm Query Tools (7 công cụ chạy ngầm cục bộ):**
   - `query_categories`: Đối chiếu câu nói với danh mục có sẵn và trích xuất số tiền.
   - `query_balance_summary`: Tra cứu tổng thu, tổng chi, số dư và trạng thái vượt ngân sách.
   - `query_category_budget`: Tra cứu hạn mức và tỷ lệ đã chi của một danh mục cụ thể.
   - `find_transactions`: Tìm kiếm lịch sử giao dịch theo từ khóa hoặc số tiền.
   - `query_daily_summary`: Thống kê tổng quan thu chi trong ngày.
   - `query_top_expenses`: Tra cứu danh sách các khoản chi tiêu tốn kém nhất.
   - `query_spending_trend`: Phân tích xu hướng tăng/giảm chi tiêu so với tháng trước.
2. **Nhóm Action Tools (9 công cụ hiển thị thẻ Preview Card để người dùng duyệt):**
   - `create_transaction`: Soạn thẻ ghi chép khoản thu/chi mới.
   - `update_transaction`: Soạn thẻ cập nhật thông tin giao dịch cũ.
   - `delete_transaction`: Soạn thẻ xác nhận xóa giao dịch.
   - `create_category`: Soạn thẻ đề xuất tạo nhóm danh mục mới.
   - `update_category`: Soạn thẻ đề xuất sửa danh mục cũ.
   - `delete_category`: Soạn thẻ đề xuất xóa danh mục.
   - `set_overall_budget`: Thiết lập hạn mức ngân sách tổng thể.
   - `set_category_budget`: Thiết lập hạn mức ngân sách cho từng danh mục.
   - `transfer_category`: Luân chuyển giao dịch sang danh mục khác.

---

## 🛠️ Công Nghệ Sử Dụng (Tech Stack)

| Thành phần | Công nghệ |
| :--- | :--- |
| **Ngôn ngữ** | Kotlin 2.x |
| **Giao diện (UI)** | Jetpack Compose, Material 3, Edge-to-Edge |
| **Kiến trúc ứng dụng** | MVVM, Repository Pattern, StateFlow & Coroutines |
| **Cơ sở dữ liệu** | SQLite Cục bộ (Hoạt động Offline 100%, bảo mật riêng tư) |
| **Giao tiếp mạng / AI** | OkHttp3, JSON Parsing theo chuẩn OpenAI Function Calling |
| **Android SDK** | minSdk 24 (Android 7.0+), compileSdk 36, targetSdk 36 |

---

## 📁 Cấu Trúc Dự Án

```
app/src/main/java/com/example/apptaichinh/
├── MainActivity.kt               # Entry point ứng dụng
├── theme/                        # Màu sắc, Typography, Shape (Material 3)
├── data/
│   ├── ai/                       # Kiến trúc AI: Tools, Services, Schemas, ReAct Agent
│   ├── db/                       # FinanceDatabaseHelper (SQLite CRUD, Quản lý giao dịch)
│   ├── model/                    # Data models: Transaction, Category, Budget, Summary
│   └── repository/               # FinanceRepository (Quản lý luồng dữ liệu trung tâm)
└── ui/
    ├── MainApp.kt                # Điều hướng Scaffold & Bottom Navigation 5 Tabs
    ├── screens/
    │   ├── ManualEntryScreen.kt  # Tab 1: Trang nhập thủ công / Smart NLP
    │   ├── CalendarScreen.kt     # Tab 2: Lịch thu chi & lịch sử giao dịch
    │   ├── AnalyticsScreen.kt    # Tab 3: Thống kê biểu đồ Donut & so sánh
    │   ├── BudgetScreen.kt       # Tab 4: Quản lý ngân sách tổng & danh mục
    │   ├── MoreScreen.kt         # Tab 5: Cài đặt, danh mục & cấu hình AI
    │   ├── CategoryScreen.kt     # Quản lý chi tiết danh mục
    │   └── ChatAssistantScreen.kt# Giao diện trò chuyện cùng Trợ lý AI
    ├── components/               # Các UI components: Thẻ giao dịch, Biểu đồ, Formatter...
    └── viewmodel/                # FinanceViewModel (State management & xử lý nghiệp vụ)
```

---

## 🚀 Hướng Dẫn Cài Đặt & Khởi Chạy

### 1. Yêu cầu môi trường
- **JDK:** Java 17 trở lên.
- **Android Studio:** Bản mới nhất (Hedgehog, Iguana, Koala hoặc mới hơn).
- **Thiết bị:** Android 7.0 (API level 24) trở lên hoặc Android Emulator.

### 2. Biên dịch và kiểm thử
Mở terminal tại thư mục gốc của dự án:

```bash
# Biên dịch gói cài đặt Debug APK
./gradlew assembleDebug

# Chạy toàn bộ Unit Tests kiểm thử tự động
./gradlew testDebugUnitTest
```

### 3. Cài đặt APK lên thiết bị
Sau khi biên dịch thành công, file APK sẽ nằm tại:
`app/build/outputs/apk/debug/app-debug.apk`

Cài đặt nhanh qua ADB:
```bash
adb install app/build/outputs/apk/debug/app-debug.apk
```

---

## 📄 Bản Quyền & Giấy Phép
Dự án được phát triển phục vụ mục đích quản lý tài chính cá nhân an toàn, bảo mật và hiệu quả. Mọi dữ liệu tài chính được lưu trữ hoàn toàn cục bộ trên thiết bị của người dùng.
