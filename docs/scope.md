# TÀI LIỆU ĐẶC TẢ SẢN PHẨM MVP TỐI GIẢN (BARE-BONES MVP)

**Dự án:** Ứng dụng Quản Lý Thu Chi Cá Nhân Cơ Bản  
**Nền tảng:** Android (Kotlin, Jetpack Compose, Room Database)  
**Tiêu chí cốt lõi:** Nhẹ, chạy offline 100%, ghi chép trong 3 giây, không tính năng thừa.

---

## 1. MỤC TIÊU SẢN PHẨM (CORE OBJECTIVE)

* Cung cấp một cuốn sổ ghi chép dòng tiền cá nhân điện tử tối giản nhất.
* Giúp người dùng trả lời được 3 câu hỏi chính:
  1. Hôm nay/tháng này mình đã tiêu bao nhiêu tiền?
  2. Số tiền đó chi vào việc gì (danh mục nào)?
  3. Hiện tại mình còn lại bao nhiêu tiền (số dư)?

---

## 2. PHẠM VI TÍNH NĂNG (MINIMAL SCOPE)

### 2.1 Các tính năng CÓ trong bản MVP (In-Scope)

1. **Quản lý giao dịch (CRUD Thu/Chi):**
   * Thêm giao dịch: Chọn Loại (Thu / Chi) $\rightarrow$ Nhập số tiền $\rightarrow$ Chọn Danh mục $\rightarrow$ Chọn Ngày $\rightarrow$ Nhập Ghi chú (tùy chọn) $\rightarrow$ Lưu.
   * Xem danh sách giao dịch: Sắp xếp theo ngày mới nhất lên đầu, phân biệt màu sắc (Xanh: Thu nhập, Đỏ: Chi tiêu).
   * Sửa và Xóa giao dịch khi nhập nhầm.

2. **Quản lý danh mục cơ bản (Categories):**
   * Cung cấp sẵn 6-8 danh mục mặc định thiết yếu:
     * *Chi tiêu:* Ăn uống, Mua sắm, Di chuyển, Hóa đơn, Khác.
     * *Thu nhập:* Lương, Thưởng, Khác.
   * Cho phép thêm danh mục mới đơn giản (chỉ cần nhập Tên danh mục và chọn Loại Thu/Chi).

3. **Màn hình Tổng quan (Dashboard):**
   * Bộ lọc xem theo Tháng (Tháng trước / Tháng này / Tháng sau).
   * Thẻ hiển thị 3 chỉ số chính:
     * **Tổng Thu** (màu xanh lá)
     * **Tổng Chi** (màu đỏ)
     * **Số Dư** = $\text{Tổng Thu} - \text{Tổng Chi}$

4. **Lưu trữ Cục bộ (Offline-First):**
   * Toàn bộ dữ liệu lưu trong máy (SQLite / Room Database). Không cần tài khoản, không cần kết nối Internet.

---

### 2.2 Các tính năng CẮT BỎ hoàn toàn ở bản MVP (Out-of-Scope)

* ❌ Chatbot AI / Đề xuất giao dịch thông minh.
* ❌ Nhận diện giọng nói (Speech-to-Text).
* ❌ Cảnh báo hạn mức ngân sách (Budgeting nâng cao).
* ❌ App Widget ngoài màn hình chính.
* ❌ Đăng nhập tài khoản / Đồng bộ đám mây (Cloud sync).
* ❌ Đa ví / Tài khoản ngân hàng liên kết.
* ❌ Biểu đồ phân tích phức tạp.

---

## 3. THIẾT KẾ CƠ SỞ DỮ LIỆU TỐI GIẢN (ROOM DB)

Chỉ dùng đúng **2 bảng duy nhất**:

### 3.1 Bảng `categories` (Danh mục)
| Tên cột | Kiểu dữ liệu | Ràng buộc | Mục đích |
| :--- | :--- | :--- | :--- |
| `id` | INTEGER | PK, Auto Increment | Khóa chính |
| `name` | TEXT | NOT NULL | Tên danh mục (VD: Ăn uống, Tiền lương) |
| `type` | TEXT | NOT NULL | Phân loại: `EXPENSE` (Chi) hoặc `INCOME` (Thu) |

### 3.2 Bảng `transactions` (Giao dịch)
| Tên cột | Kiểu dữ liệu | Ràng buộc | Mục đích |
| :--- | :--- | :--- | :--- |
| `id` | INTEGER | PK, Auto Increment | Khóa chính |
| `amount` | INTEGER (Long) | NOT NULL | Số tiền (lưu dạng số nguyên VNĐ) |
| `type` | TEXT | NOT NULL | `EXPENSE` hoặc `INCOME` |
| `category_id` | INTEGER | FK -> `categories(id)` | Liên kết danh mục |
| `note` | TEXT | NULLABLE | Ghi chú (VD: Mua trà sữa) |
| `date_epoch` | INTEGER (Long) | NOT NULL | Thời gian tạo giao dịch (milliseconds) |

---

## 4. LUỒNG NGƯỜI DÙNG CỐT LÕI (USER FLOWS)

```
[Màn hình chính (Dashboard)]
         │
         ├──> [Xem Tổng Thu, Tổng Chi, Số Dư tháng hiện tại]
         │
         ├──> [Cuộn xem danh sách các khoản đã chi theo ngày]
         │
         ├──> [Bấm nút (+)] ───> [Màn hình Nhập Giao Dịch]
         │                             │
         │                             ├── Nhập số tiền (VD: 40000)
         │                             ├── Chọn Danh mục (VD: Ăn uống)
         │                             ├── Chọn ngày (Mặc định hôm nay)
         │                             └── Bấm [Lưu] ──> Quay về Màn hình chính
         │
         └──> [Bấm vào 1 giao dịch cũ] ───> [Xem chi tiết / Sửa / Xóa]
```

---

## 5. BỐ CỤC 2 MÀN HÌNH CHÍNH (UI SPEC)

### Màn hình 1: Trang chủ (Dashboard & History)
* **Khu vực trên cùng:**
  * Thanh chuyển tháng: `< Tháng 09/2026 >`
  * Khung tóm tắt 3 cột:
    * **Thu:** $+15.000.000$ đ
    * **Chi:** $-4.250.000$ đ
    * **Số dư:** $+10.750.000$ đ
* **Khu vực danh sách:**
  * Danh sách cuộn nhóm theo từng ngày:
    * *Hôm nay - 19/09:*
      * [Ăn uống] Ăn trưa bún bò: $-45.000$ đ
      * [Di chuyển] Đổ xăng xe: $-80.000$ đ
    * *Hôm qua - 18/09:*
      * [Lương] Tạm ứng lương: $+5.000.000$ đ
* **Nút bấm hành động (FAB):** Nút tròn dấu `(+)` ở góc dưới bên phải màn hình để mở form nhập nhanh.

### Màn hình 2: Thêm / Chỉnh sửa Giao dịch (Add/Edit Form)
* Hai nút Tab trên cùng: **[ Chi tiêu ]** | **[ Thu nhập ]**
* Ô nhập tiền lớn, tự định dạng dấu phẩy khi gõ (VD: `50000` $\rightarrow$ `50,000 đ`).
* Danh sách Icon/Nút danh mục (Ăn uống, Di chuyển, Mua sắm, v.v.) dạng lưới để bấm chọn ngay.
* Ô chọn Ngày (mặc định là ngày giờ hiện tại).
* Ô Ghi chú ngắn (tùy chọn).
* Nút bấm lớn ở đáy màn hình: **[ Lưu giao dịch ]**.

---

## 6. TIÊU CHÍ HOÀN THÀNH MVP (DEFINITION OF DONE)

1. **Ghi chép nhanh:** Mở app, bấm `(+)`, nhập số tiền, chọn mục và lưu trong vòng không quá 5 giây.
2. **Không lỗi tính toán:** Phép tính Số dư luôn chính xác 100% theo các bản ghi trong Room DB.
3. **Hoàn toàn Offline:** Ứng dụng hoạt động bình thường kể cả khi bật Chế độ máy bay (Airplane Mode).
4. **Không mất dữ liệu:** Xoay ngang màn hình, thoát ứng dụng hoặc tắt nguồn máy mở lại dữ liệu vẫn nguyên vẹn.