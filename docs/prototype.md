# TÀI LIỆU ĐẶC TẢ PROTOTYPE & THIẾT KẾ GIAO DIỆN (UI/UX PROTOTYPE SPEC)

**Dự án:** Ứng dụng Quản Lý Thu Chi Cá Nhân & Chat Assistant  
**Nền tảng mục tiêu:** Android (Jetpack Compose / Material Design 3)  
**Phiên bản:** 1.0 (MVP Prototype)  
**Tài liệu tham chiếu:** MVP PRD - Simple Expense Tracker  

---

## 1. SƠ ĐỒ ĐIỀU HƯỚNG & KIẾN TRÚC THÔNG TIN (APP ARCHITECTURE & USER FLOW)

### 1.1 Sơ đồ chuyển trang (Navigation Graph)

```text
               ┌───────────────────────────────┐
               │    Màn hình 1: Dashboard      │
               │   (Xem số dư & danh sách)     │
               └──────┬─────────────────┬──────┘
                      │                 │
     [Bấm FAB (+)]    │                 │  [Bấm Chat Icon trên TopBar]
                      ▼                 ▼
        ┌───────────────────┐     ┌───────────────────────────────────┐
        │  Màn hình 2:      │     │  Màn hình 3:                      │
        │  Form nhập tay    │     │  Chat Assistant trích xuất        │
        │  (Manual Entry)   │     │  (AI Parsing + Preview Card)      │
        └─────────┬─────────┘     └─────────────────┬─────────────────┘
                  │                                 │
                  │ [Lưu]                           │ [Xác nhận trên Card]
                  ▼                                 ▼
        ┌─────────────────────────────────────────────────────────────┐
        │                 Lưu vào Room Database                       │
        │           (Cập nhật tức thì số dư Dashboard)                │
        └─────────────────────────────────────────────────────────────┘
```

### 1.2 Nguyên tắc thiết kế trải nghiệm (UX Principles)
1. **One-thumb Reachability:** Các nút tương tác chính (FAB thêm nhanh, gửi chat, xác nhận card) tập trung ở nửa dưới màn hình để dễ thao tác bằng một tay.
2. **Clear Feedback:** Mọi thao tác thêm/xóa/sửa đều có phản hồi tức thời (Snackbar thông báo, cập nhật số dư ngay mà không cần reload trang).
3. **Safety-first AI Interaction:** Bot tuyệt đối không ghi đè hay tự động lưu trực tiếp vào cơ sở dữ liệu; luôn bắt buộc người dùng duyệt qua **Preview Card**.

---

## 2. ĐẶC TẢ CHI TIẾT CÁC MÀN HÌNH (WIREFRAME & LAYOUT SPEC)

### 2.1 Màn hình 1: Trang chủ (Dashboard Screen)

Màn hình hiển thị tổng quan tài chính trong tháng và danh sách chi tiết các giao dịch.

```text
┌──────────────────────────────────────────────────┐
│  <  Tháng 09/2026  >                      [ 💬 ] │  <-- TopBar: Chọn tháng & Shortcut Chat
├──────────────────────────────────────────────────┤
│ ┌──────────────────────────────────────────────┐ │
│ │  SỐ DƯ THÁNG                                 │ │
│ │  10.750.000 đ                                │ │  <-- Card Tổng quan (Balance Card)
│ │ ──────────────────────────────────────────── │ │
│ │  Thu nhập: +15.000.000 đ   Chi tiêu: -4.250k │ │
│ └──────────────────────────────────────────────┘ │
│                                                  │
│  LỊCH SỬ GIAO DỊCH                               │
│                                                  │
│  Hôm nay, 19/09                      -125.000 đ  │  <-- Header nhóm ngày & Tổng chi ngày
│  ┌───┐                                          │
│  │🍜 │ Ăn trưa bún bò                -45.000 đ  │  <-- Item giao dịch
│  └───┘ Ăn uống                                   │
│  ┌───┐                                          │
│  │⛽ │ Đổ xăng xe máy                 -80.000 đ  │
│  └───┘ Di chuyển                                 │
│                                                  │
│  Hôm qua, 18/09                   +5.000.000 đ  │
│  ┌───┐                                          │
│  │💵 │ Lương tạm ứng              +5.000.000 đ  │
│  └───┘ Thu nhập                                  │
│                                                  │
│                                           ┌───┐  │
│                                           │ + │  │  <-- FAB (Thêm thủ công)
│                                           └───┘  │
└──────────────────────────────────────────────────┘
```

#### Chi tiết thành phần:
* **TopBar:**
  * Bộ chọn tháng: Nhấn mũi tên trái/phải (`<` hoặc `>`) để lùi/tiến từng tháng; nhấn vào giữa hiển thị modal chọn tháng/năm.
  * Nút `[💬]`: Mở nhanh màn hình Chat Assistant.
* **Balance Card:**
  * Nền: Bo góc 16dp, màu Surface Variant hoặc gradient nhẹ.
  * Số dư lớn (Typography: `HeadlineMedium`, Bold).
  * 2 nhãn phụ: Thu nhập (Màu xanh lá `#2E7D32`), Chi tiêu (Màu đỏ `#C62828`).
* **Danh sách giao dịch (`LazyColumn`):**
  * Sắp xếp: Thời gian mới nhất ở trên cùng.
  * Nhóm theo Ngày (`Sticky Header`): Hiển thị thứ, ngày/tháng kèm tổng số tiền phát sinh trong ngày đó.
  * Mỗi dòng giao dịch:
    * Bên trái: Icon tròn đại diện danh mục (40x40dp).
    * Ở giữa: Tiêu đề ghi chú (VD: *Ăn trưa bún bò*) và tên danh mục phụ (VD: *Ăn uống*).
    * Bên phải: Số tiền định dạng có dấu phẩy (`45.000 đ`), màu sắc theo loại (Chi: Đỏ `-`, Thu: Xanh `+`).
  * Trượt để xóa (Swipe-to-delete) hoặc bấm vào để xem/sửa chi tiết.
* **Nút Floating Action Button (FAB):**
  * Nút tròn lớn đặt ở góc phải dưới (`Alignment.BottomEnd`). Icon dấu `+`.

---

### 2.2 Màn hình 2: Chat Assistant Trích Xuất (AI Chat Screen)

Nơi người dùng trò chuyện bằng ngôn ngữ tự nhiên để ghi chép thu chi nhanh chóng.

```text
┌──────────────────────────────────────────────────┐
│ [ ← ]  Trợ lý Nhập liệu                   [ ℹ️ ] │  <-- TopBar: Quay lại & Hướng dẫn mẫu
├──────────────────────────────────────────────────┤
│                                                  │
│  [Bot]: Chào bạn! Hãy nhập khoản chi tiêu hoặc   │
│         thu nhập, mình sẽ soạn phiếu giúp bạn.   │
│                                                  │
│                   ┌────────────────────────────┐ │
│                   │ Vừa ăn trưa bún bò 45k     │ │  <-- Bubble người dùng (User Bubble)
│                   └────────────────────────────┘ │
│                                                  │
│  [Bot]: Mình đã phân loại giao dịch này:         │
│  ┌─────────────────────────────────────────────┐ │
│  │ 📝 PHIẾU GIAO DỊCH ĐỀ XUẤT                  │ │  <-- PREVIEW CONFIRMATION CARD
│  │ ─────────────────────────────────────────── │ │
│  │ Loại:      Chi tiêu (-)                     │ │
│  │ Số tiền:   45,000 đ                         │ │
│  │ Danh mục:  [ 🍜 Ăn uống        ▼ ]          │ │  <-- Dropdown cho phép sửa nhanh
│  │ Ghi chú:   Ăn trưa bún bò                   │ │
│  │ Ngày:      19/09/2026 (Hôm nay)             │ │
│  │ ─────────────────────────────────────────── │ │
│  │   [ ❌ Hủy bỏ ]      [ 💾 Xác nhận & Lưu ]   │ │  <-- Hai nút thao tác chính
│  └─────────────────────────────────────────────┘ │
│                                                  │
├──────────────────────────────────────────────────┤
│ ┌────────────────────────────────────────┐ ┌───┐ │
│ │ Nhập chi tiêu (VD: Đổ xăng 50k)...     │ │ > │ │  <-- Input Bar & Send Button
│ └────────────────────────────────────────┘ └───┘ │
└──────────────────────────────────────────────────┘
```

#### Chi tiết thành phần & Luồng Preview Card:
* **Bottom Input Field:**
  * `TextField` hỗ trợ gõ nhiều dòng ngắn, có placeholder gợi ý mẫu câu: *"VD: Trả tiền điện 350k, Nhận thưởng 2tr"*.
  * Nút `Send` (Icon mũi tên): Disable khi ô nhập rỗng; Enable khi có ký tự.
* **Trạng thái phân tích (Parsing State):**
  * Khi bấm gửi, tin nhắn người dùng hiển thị ngay lề phải.
  * Bên dưới xuất hiện bubble của Bot với hiệu ứng 3 chấm nhảy (`Typing indicator`).
* **Thành phần Preview Card (Trái tim của tính năng Chat):**
  * **Header Card:** Huy hiệu loại giao dịch (Chi tiêu màu đỏ / Thu nhập màu xanh).
  * **Số tiền:** In đậm, cỡ chữ lớn, tự động thêm đơn vị tiền tệ VNĐ.
  * **Dropdown Danh mục:** Hiển thị danh mục AI tự chọn kèm biểu tượng; khi bấm vào sẽ mở BottomSheet danh sách các danh mục khác để người dùng chọn lại nếu AI nhận diện sai.
  * **Ghi chú & Thời gian:** Trích xuất từ câu nói của người dùng và thời gian thực tế.
  * **Nút bấm hành động:**
    * `[ ❌ Hủy bỏ ]`: Nút Outline mờ; bấm vào sẽ hủy thẻ và hiện text *"Đã hủy đề xuất"*.
    * `[ 💾 Xác nhận & Lưu ]`: Nút Filled Button màu Primary; bấm vào sẽ:
      1. Lưu bản ghi vào bảng `transactions` trong Room DB.
      2. Chuyển trạng thái Card sang: `[ ✓ Đã lưu vào sổ lúc 12:30 ]` (vô hiệu hóa 2 nút bấm để tránh lưu trùng).
      3. Phát âm thanh nhẹ/rung haptic xác nhận.

---

### 2.3 Màn hình 3: Form Thêm / Chỉnh Sửa Thủ Công (Manual Entry Screen)

Dành cho người dùng muốn nhập chi tiết hoặc không dùng chat.

```text
┌──────────────────────────────────────────────────┐
│ [ ✕ ]             Thêm Giao Dịch                 │
├──────────────────────────────────────────────────┤
│        ┌───────────────┬───────────────┐         │
│        │  [ CHI TIÊU ] │   THU NHẬP    │         │  <-- Tab Selector
│        └───────────────┴───────────────┘         │
│                                                  │
│                     SỐ TIỀN                      │
│                  50,000 đ                        │  <-- Numeric Display lớn
│                                                  │
│  DANH MỤC                                        │
│  ┌──────┐    ┌──────┐    ┌──────┐    ┌──────┐    │
│  │  🍜  │    │  ⛽  │    │  🛒  │    │  🧾  │    │
│  │Ăn uốn│    │Di chu│    │Mua sắ│    │Hóa đơ│    │  <-- Category Grid (Lưới icon)
│  └──────┘    └──────┘    └──────┘    └──────┘    │
│  ┌──────┐    ┌──────┐    ┌──────┐    ┌──────┐    │
│  │  💊  │    │  ☕  │    │  ➕  │    │      │    │
│  │Y tế  │    │Cà phê│    │Thêm  │    │      │    │  <-- Nút thêm danh mục mới
│  └──────┘    └──────┘    └──────┘    └──────┘    │
│                                                  │
│  THÔNG TIN BỔ SUNG                               │
│  📅 Ngày:     19/09/2026 (Hôm nay)          [>]  │  <-- Date Picker
│  📝 Ghi chú:  Mua bánh mì sáng                   │  <-- Note Input
│                                                  │
├──────────────────────────────────────────────────┤
│ ┌──────────────────────────────────────────────┐ │
│ │               LƯU GIAO DỊCH                  │ │  <-- Nút Submit toàn màn hình
│ └──────────────────────────────────────────────┘ │
└──────────────────────────────────────────────────┘
```

#### Chi tiết thành phần:
* **Tab Switcher:** Chuyển đổi giữa 2 chế độ `Chi tiêu` (mặc định) và `Thu nhập`. Màu sắc chủ đạo đổi tương ứng (Đỏ / Xanh).
* **Ô nhập số tiền:**
  * Font to ở trung tâm (`DisplaySmall`).
  * Chỉ mở bàn phím số (Numeric Keyboard).
  * Tự động format hàng nghìn theo thời gian thực (User gõ `50000` -> hiển thị `50,000 đ`).
* **Lưới danh mục (Category Grid):**
  * Hiển thị danh mục theo dạng lưới 4 cột.
  * Ô cuối cùng luôn là nút `[➕ Thêm danh mục]` (mở popup thêm danh mục tùy chỉnh gồm tên và chọn icon).
  * Danh mục được chọn sẽ có viền bo màu Primary nổi bật.
* **Chọn Ngày:** Bấm vào mở `DatePickerDialog` chuẩn của Android Material 3.
* **Nút [Lưu giao dịch]:** Nằm cố định sát đáy màn hình, luôn nổi trên bàn phím.

---

## 3. ĐẶC TẢ TRẠNG THÁI GIAO DIỆN & XỬ LÝ NGOẠI LỆ (UI STATES & EDGE CASES)

### 3.1 Bảng phân tích trạng thái các màn hình

| Màn hình | Thành phần | Trạng thái (UI State) | Biểu hiện giao diện & Hành vi |
| :--- | :--- | :--- | :--- |
| **Dashboard** | Danh sách | **Empty State** | Hiển thị vector rỗng + text: *"Bạn chưa có giao dịch nào trong tháng này. Hãy bấm (+) hoặc Chat để thêm nhé!"* |
| **Dashboard** | Số dư | **Dương / Âm** | Số dư > 0 hiển thị màu đen/xanh; Số dư < 0 (chi vượt thu) hiển thị màu đỏ cảnh báo. |
| **Chat** | Kết nối mạng | **Mất mạng (Offline)** | Hiện banner cảnh báo: *"Tính năng Chat cần kết nối mạng để phân tích câu nói. Bạn vui lòng dùng form nhập tay (+)"*. |
| **Chat** | Bộ phân tích AI | **Không rõ thông tin** | Bot phản hồi: *"Mình chưa nhận diện được số tiền hoặc nội dung. Bạn hãy thử lại theo cú pháp: 'Ăn phở 45k' hoặc 'Đổ xăng 70k' nhé!"* |
| **Chat** | Preview Card | **Sau khi Lưu thành công** | Hai nút bấm biến mất; thay bằng badge: `✓ Đã lưu vào danh mục [Ăn uống]` màu xám nhạt, ngăn chặn bấm đúp. |
| **Form nhập tay** | Nút Lưu | **Validation lỗi** | Nếu số tiền bằng 0 hoặc chưa chọn danh mục: Nút Lưu bị Disable (xám mờ). |

---

## 4. DỮ LIỆU MẪU KIỂM THỬ GIAO DIỆN (MOCK DATA FOR TESTING)

### 4.1 Danh mục mặc định hệ thống (Initial Categories)

```kotlin
val defaultCategories = listOf(
    // Danh mục Chi tiêu (EXPENSE)
    Category(id = 1, name = "Ăn uống", type = "EXPENSE", iconName = "ic_food", color = "#FF5722"),
    Category(id = 2, name = "Di chuyển", type = "EXPENSE", iconName = "ic_car", color = "#2196F3"),
    Category(id = 3, name = "Mua sắm", type = "EXPENSE", iconName = "ic_shopping", color = "#E91E63"),
    Category(id = 4, name = "Hóa đơn", type = "EXPENSE", iconName = "ic_bill", color = "#9C27B0"),
    Category(id = 5, name = "Khác", type = "EXPENSE", iconName = "ic_more", color = "#607D8B"),

    // Danh mục Thu nhập (INCOME)
    Category(id = 6, name = "Tiền lương", type = "INCOME", iconName = "ic_salary", color = "#4CAF50"),
    Category(id = 7, name = "Thưởng / Phụ cấp", type = "INCOME", iconName = "ic_gift", color = "#8BC34A"),
    Category(id = 8, name = "Khác", type = "INCOME", iconName = "ic_income_more", color = "#009688")
)
```

### 4.2 Các kịch bản test Chatbot trích xuất dữ liệu

| Câu nhập vào (Input Text) | Kỳ vọng phân tích (Expected Extraction) | Hành vi Preview Card |
| :--- | :--- | :--- |
| *"Sáng nay ăn bát phở bò 50k"* | Amount: `50000`, Type: `EXPENSE`, Category: `Ăn uống`, Note: `Ăn bát phở bò` | Hiện Card Chi tiêu, chọn sẵn mục Ăn uống. |
| *"Được công ty thưởng dự án 2 triệu"* | Amount: `2000000`, Type: `INCOME`, Category: `Thưởng / Phụ cấp`, Note: `Thưởng dự án` | Hiện Card Thu nhập màu xanh, số tiền 2.000.000 đ. |
| *"Mua đôi giày 450 nghìn"* | Amount: `450000`, Type: `EXPENSE`, Category: `Mua sắm`, Note: `Mua đôi giày` | Hiện Card Chi tiêu 450.000 đ. |
| *"Hôm nay trời mưa to quá"* | Không có số tiền / giao dịch | Bot đáp lịch sự: *"Câu này có vẻ không phải chi tiêu. Bạn có muốn ghi nhận khoản tiền nào không?"* |

---

## 5. TIÊU CHUẨN THIẾT KẾ (DESIGN TOKENS)

* **Hệ màu chính (Color Palette):**
  * `Primary`: `#1E88E5` (Xanh dương hiện đại - Tin cậy, công nghệ).
  * `Expense (Chi tiêu)`: `#E53935` (Đỏ tươi - Nhận diện chi phí).
  * `Income (Thu nhập)`: `#43A047` (Xanh lá lục - Nhận diện dòng tiền vào).
  * `Background`: `#F8F9FA` (Sáng sủa, sạch sẽ, tối giản).
  * `Surface`: `#FFFFFF` (Nền thẻ card nổi bật).
* **Typography:** Chuẩn Roboto / Google Sans trên Android (Hỗ trợ tiếng Việt đầy đủ dấu mà không lỗi font).