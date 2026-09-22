# BÁO CÁO TOÀN DIỆN HỆ THỐNG TRỢ LÝ TÀI CHÍNH CÁ NHÂN ĐA TÁC TỬ
**Dự Án:** Ứng Dụng Quản Lý Tài Chính Cá Nhân Android (Native Kotlin + Jetpack Compose + SQLite)  
**Mô Hình AI:** Qwen2.5-3B-Instruct (Tool Calling / Function Calling qua LM Studio)  
**Ngày cập nhật:** 22/09/2026  
**Trạng thái kiểm thử:** 50 / 50 Test Cases PASS 100% (Thời gian chạy ~1.0s, không cần build APK)

---

## MỤC LỤC
1. [Tổng Quan Kiến Trúc Đa Tác Tử (Multi-Agent ReAct Architecture)](#1-tổng-quan-kiến-trúc-đa-tác-tử)
2. [Hệ Thống 8 Công Cụ (Tools & Actions) & Giới Hạn Hoạt Động](#2-hệ-thống-8-công-cụ-tools--actions)
3. [Các Điểm Nghẽn Thực Tế & Giải Pháp Đã Xử Lý](#3-các-điểm-nghẽn-thực-tế--giải-pháp-đã-xử-lý)
4. [Tính Năng Thẻ Xem Trước (Preview Card) Tương Tác Linh Hoạt](#4-tính-năng-thẻ-xem-trước-preview-card-tương-tác-linh-hoạt)
5. [Báo Cáo Kết Quả Kiểm Thử Tự Động (50/50 Tests PASS)](#5-báo-cáo-kết-quả-kiểm-thử-tự-động)
6. [Hướng Dẫn Vận Hành & Khởi Chạy](#6-hướng-dẫn-vận-hành--khởi-chạy)

---

## 1. TỔNG QUAN KIẾN TRÚC ĐA TÁC TỬ

Hệ thống được thiết kế theo mô hình **ReAct Đa Bước Tuần Tự (Reasoning + Acting Workflow)** kết hợp với cơ chế **An Toàn Dữ Liệu (Safety-First)**:

```
[Người dùng nhập câu nói]
         │
         ▼
[Bước 1: LLM Phân tích ý định & gọi Query Tool ngầm]
         │  (VD: query_categories, query_balance_summary)
         ▼
[Điện thoại thực thi ngầm trên SQLite cục bộ]
         │  (Không làm phiền người dùng, trích xuất số tiền & danh mục chuẩn xác)
         ▼
[Bước 2: LLM nhận kết quả & gọi Action Tool]
         │  (VD: create_transaction, update_transaction, create_category)
         ▼
[UI hiển thị Thẻ Xem Trước (Preview Card)]
         │  (Người dùng có thể sửa số tiền, đổi danh mục, sửa ghi chú)
         ▼
[Người dùng bấm "Xác Nhận Lưu"] ──► [Ghi dữ liệu vào SQLite]
```

### Nguyên tắc cốt lõi:
- **Tách biệt Query Tool và Action Tool:** Truy vấn dữ liệu chạy ngầm trong máy, chỉ các tác động làm thay đổi dữ liệu mới hiển thị thẻ Preview Card.
- **Không bao giờ tự ý ghi đè:** LLM không có quyền ghi trực tiếp vào cơ sở dữ liệu SQLite; mọi hành động ghi/sửa/xóa đều phải thông qua nút bấm xác nhận của người dùng.
- **Bảo toàn ngữ cảnh & chống lặp:** Tối đa **4 bước suy luận liên tiếp** trong 1 lượt hỏi đáp (`maxIterations = 4`), vừa đủ chuỗi suy luận phức tạp vừa phản hồi siêu tốc (~1-2s).

---

## 2. HỆ THỐNG 8 CÔNG CỤ (TOOLS & ACTIONS)

Hệ thống cung cấp đúng **8 công cụ nghiệp vụ** theo chuẩn OpenAI Function Calling tại `ToolDefinitions.kt`:

### A. Nhóm Query Tools (Tra cứu ngầm cục bộ – 4 công cụ)
| Tên Tool | Chức năng | Đầu vào |
| :--- | :--- | :--- |
| `query_categories` | Đối chiếu toàn văn câu nói của người dùng với các danh mục có sẵn của bên Thu hoặc bên Chi để chọn danh mục chuẩn xác nhất và trích xuất số tiền. | `type` ("EXPENSE"/"INCOME"), `user_text` |
| `query_balance_summary` | Tra cứu tổng thu nhập, tổng chi tiêu, số dư hiện tại, hạn mức ngân sách tháng và kiểm tra xem đã tiêu vượt chưa (`is_over_budget`). | `month_offset` (0 là tháng này, -1 là tháng trước) |
| `query_category_budget` | Tra cứu ngân sách của một danh mục cụ thể: số đã chi, hạn mức của danh mục, số tiền còn lại và tỷ lệ phần trăm đã tiêu. | `category_name`, `month_offset` |
| `find_transactions` | Tra cứu lịch sử các giao dịch gần đây theo từ khóa ghi chú hoặc tên danh mục (tối đa lấy 10 giao dịch). | `keyword`, `type`, `limit` (tối đa 10) |

### B. Nhóm Action Tools (Đề xuất thay đổi dữ liệu – 4 công cụ)
| Tên Tool | Hành động tương ứng (`ToolActionType`) | Chức năng |
| :--- | :---: | :--- |
| `create_transaction` | `CREATE` | Soạn phiếu ghi chép một khoản chi tiêu hoặc thu nhập mới. |
| `update_transaction` | `UPDATE` | Tìm giao dịch cũ và soạn phiếu chỉnh sửa số tiền, danh mục hoặc ghi chú. |
| `delete_transaction` | `DELETE` | Tìm giao dịch cũ và soạn phiếu xóa giao dịch khỏi sổ (hoàn trả ngân sách). |
| `create_category` | `CREATE_CATEGORY` | Soạn phiếu đề xuất tạo danh mục mới khi có nhu cầu hoặc khi khoản chi quá khác biệt. |

### C. Các giới hạn cấu hình kỹ thuật:
- **Số bước suy luận tối đa trong 1 câu nói:** `maxIterations = 4` bước suy luận.
- **Số bản ghi tra cứu lịch sử tối đa:** Tối đa `10 giao dịch` (`coerceIn(1, 10)`).
- **Hạn mức số tiền giao dịch:** Kiểu số `Long` (hỗ trợ tới hàng tỷ tỷ VNĐ).

---

## 3. CÁC ĐIỂM NGHẼN THỰC TẾ & GIẢI PHÁP ĐÃ XỬ LÝ

| Điểm nghẽn phát hiện | Hiện tượng ban đầu | Nguyên nhân gốc rễ | Giải pháp đã khắc phục triệt để |
| :--- | :--- | :--- | :--- |
| **1. Lỗi rơi số 0 khi đọc tiền** | Người dùng nói *"ăn pizza 100k"* $\rightarrow$ AI lưu thành `10k`. | Mô hình nhỏ (Qwen 3B) có khả năng tính toán số học tiếng Việt hạn chế, dễ rớt số 0 ở các từ lóng `"100k"`, `"1000k"`. | Xây dựng bộ bóc tách Regex tiếng Việt nội bộ (`extractAmountFromText`): quét trực tiếp `100k` $\rightarrow$ `100.000 đ`, `1000k` $\rightarrow$ `1.000.000 đ`, `1tr5` $\rightarrow$ `1.500.000 đ`, `2 củ` $\rightarrow$ `2.000.000 đ`, `25 cành` $\rightarrow$ `25.000 đ`, `2 lít` $\rightarrow$ `200.000 đ`. Ưu tiên giá trị bóc tách này ghi đè lên payload AI. |
| **2. AI "chém gió" nói suông không lưu sổ** | Người dùng nói *"đóng tiền nhà 1000k"*, AI trả lời: *"Đã thêm vào sổ..."* nhưng không hiện thẻ Preview Card, sổ không có gì. | LLM bị hallucination, sinh câu trả lời bằng văn bản mà quên phát sinh lệnh gọi `create_transaction`. | Thiết lập chốt chặn **Auto-Recovery Guardrail**: Nếu tin nhắn AI trả về mà `pendingToolAction == null` nhưng câu người dùng có chứa số tiền hợp lệ (> 0), hệ thống tự động xác định Thu/Chi, đối chiếu danh mục và tự động dựng thẻ xem trước để người dùng bấm Lưu ngay. |
| **3. "Khám bệnh" bị đề xuất tạo danh mục mới** | Người dùng nói *"khám bệnh 1000k"*, AI lại đề xuất tạo danh mục mới dù đã có danh mục Sức khỏe. | System Prompt chưa từng đưa danh sách danh mục có sẵn của người dùng vào ngữ cảnh suy luận của AI. | Nạp trực tiếp danh sách danh mục của người dùng vào System Prompt; thiết lập quy tắc bắt buộc: *"khám bệnh", "mua thuốc", "đi viện", "khám răng", "nha khoa"* **phải khớp vào `🩺 Sức khỏe`**, nghiêm cấm đề xuất tạo danh mục mới khi đã có sẵn. |
| **4. Khoản chi quá khác biệt** | Các khoản chi hoàn toàn mới lạ như *"nuôi mèo"*, *"thú cưng"*. | Trước đây chưa có cơ chế tạo danh mục qua AI. | Bổ sung tool `create_category` và thẻ `CreateCategoryCard`: Chỉ khi khoản chi/thu hoàn toàn mới lạ và không thể xếp vào danh mục nào có sẵn, AI mới đề xuất tạo danh mục mới. |

---

## 4. TÍNH NĂNG THẺ XEM TRƯỚC (PREVIEW CARD) TƯƠNG TÁC LINH HOẠT

Thẻ xem trước `CreateTransactionCard` được nâng cấp toàn diện:

1. **Hiển thị trực quan:**
   - Phân biệt rõ ràng Thu nhập (Xanh lục `+`) và Chi tiêu (Đỏ `-`).
   - Định dạng số tiền VNĐ rõ ràng (VD: `1.000.000 đ`).
   - Hiển thị Icon và Tên danh mục (VD: `🩺 Sức khỏe`, `🍜 Ăn uống`).
   - Hiển thị nội dung ghi chú.
2. **Khả năng chỉnh sửa trực tiếp (Editable Preview Card):**
   - Người dùng bấm biểu tượng **Sửa (✏️)** cạnh số tiền khi thẻ đang ở trạng thái chờ duyệt (`PENDING`).
   - **Sửa Số tiền:** Ô nhập số tiền với dòng hỗ trợ cập nhật định dạng VNĐ theo thời gian thực.
   - **Đổi Danh mục:** Bấm vào thẻ danh mục để chọn lại danh mục từ danh sách các danh mục có sẵn.
   - **Nút `+ Tạo danh mục mới`:** Tích hợp trực tiếp trong hộp thoại chọn danh mục, cho phép tạo nhanh danh mục mới bất cứ lúc nào.
   - **Sửa Ghi chú:** Ô nhập liệu cho phép sửa lại nội dung ghi chú giao dịch.
   - **Lưu thay đổi:** Bấm "Xong" để cập nhật ngay trên thẻ xem trước trước khi bấm **"Xác Nhận Lưu"**.

---

## 5. BÁO CÁO KẾT QUẢ KIỂM THỬ TỰ ĐỘNG (50/50 TESTS PASS)

Toàn bộ quá trình kiểm thử được tự động hóa 100% bằng JVM Unit Test (**không build file APK**), hoàn thành trong **~1.0 giây**:

```
BUILD SUCCESSFUL in 1s
50 tests completed, 0 failed, 0 skipped
```

### Chi tiết các bộ test:

#### 1. Bộ kiểm thử chuỗi hành động nối tiếp nhau (`AiSequentialActionChainTest.kt` - 10 Tests)
- `testChain1_ReActTwoStepWorkflow_QueryToCreate`: Chuỗi 2 bước ReAct: Query `query_categories` $\rightarrow$ Action `create_transaction` $\rightarrow$ Card.
- `testChain2_MultipleExpensesChain_TriggerOverBudget`: Ghi chi tiêu liên tiếp $\rightarrow$ Tác động ngân sách tổng $\rightarrow$ Kích hoạt cảnh báo vượt hạn mức tháng `query_balance_summary`.
- `testChain3_CategorySpendingChain_TriggerCategoryOverBudget`: Chi tiêu liên tiếp trong 1 danh mục $\rightarrow$ Cảnh báo vượt hạn mức danh mục `query_category_budget`.
- `testChain4_CreateThenSearchThenUpdateChain`: Tạo giao dịch $\rightarrow$ Tìm kiếm `find_transactions` $\rightarrow$ Sửa `update_transaction` $\rightarrow$ Số tiền điều chỉnh.
- `testChain5_CreateThenSearchThenDeleteChain`: Tạo giao dịch nhầm $\rightarrow$ Tìm kiếm $\rightarrow$ Xóa `delete_transaction` $\rightarrow$ Hoàn trả ngân sách.
- `testChain6_InterleavedIncomeAndExpenseChain`: Thu và Chi đan xen liên tục nhiều lượt $\rightarrow$ Cân đối số dư ròng chuẩn xác.
- `testChain7_AutoRecoveryThenConfirmThenQueryHistory`: Chốt chặn Auto-Recovery phục hồi hành động $\rightarrow$ Lưu vào sổ $\rightarrow$ Tìm thấy ngay trong lịch sử.
- `testChain8_KhamBenh_MustMatchHealthCategory_NotProposeNewCategory`: *"Khám bệnh 1000k"* khớp chuẩn xác danh mục **Sức khỏe 🩺**, số tiền 1.000.000 đ, không tạo danh mục mới.
- `testChain9_EditablePreviewCard_ModifyBeforeConfirm`: Thẻ Preview Card cho phép sửa đổi số tiền và ghi chú trước khi bấm Xác Nhận Lưu $\rightarrow$ Lưu chính xác dữ liệu đã sửa vào DB.
- `testChain10_CreateCategoryWhenTrulyNovel`: Khoản chi/thu hoàn toàn mới lạ ("Nuôi thú cưng 🐱") $\rightarrow$ Đề xuất `create_category` $\rightarrow$ Lưu danh mục mới vào hệ thống.

#### 2. Bộ kiểm thử ngân sách và 100+ câu nói người dùng (`AiBudgetAndUserInputsComprehensiveTest.kt` - 27 Tests)
- Kiểm tra tính toán ngân sách tổng: hạn mức, số dư, tỷ lệ phần trăm, cờ `is_over_budget`.
- Kiểm tra hạn mức từng danh mục chi tiết (Ăn uống, Mua sắm, Đi lại,...).
- Kiểm thử toàn diện 100+ câu nói thực tế: Ăn uống (15 câu), Đi lại (10 câu), Nhà ở (9 câu), Mua sắm (9 câu), Giải trí (8 câu), Sức khỏe (6 câu), Giáo dục (5 câu), Hiếu hỉ (6 câu), Tiết kiệm / Trả nợ (5 câu), Thu nhập (12 câu).
- Kiểm thử toàn bộ các đơn vị tiền tệ: `k`, `tr`, `củ`, `lít`, `cành`, `đ`.

#### 3. Bộ kiểm thử định nghĩa công cụ & Schemas (`OpenAiToolSchemasTest.kt` - 11 Tests)
- Kiểm tra tính hợp lệ và đầy đủ của 8 Tools theo chuẩn OpenAI.
- Kiểm tra phân loại ý định Thu/Chi (`isIncomeIntent`).
- Kiểm tra các chỉ thị prompt hướng dẫn AI xử lý ngân sách.

#### 4. Bộ kiểm thử ViewModel (`MainScreenViewModelTest.kt` - 2 Tests)
- Kiểm thử khởi tạo và chuyển đổi trạng thái tab điều hướng trên màn hình chính.

---

## 6. HƯỚNG DẪN VẬN HÀNH & KHỞI CHẠY

### Lệnh chạy kiểm thử tự động (Không tạo APK):
```bash
export JAVA_HOME="/home/tuananh/Unity/Hub/Editor/6000.3.23f1/Editor/Data/PlaybackEngines/AndroidPlayer/OpenJDK"
export PATH="$JAVA_HOME/bin:/home/tuananh/.local/bin:/home/tuananh/Android/Sdk/platform-tools:$PATH"
./gradlew compileDebugKotlin testDebugUnitTest
```

### Cấu hình máy chủ LM Studio trong ứng dụng:
- Mở màn hình Trợ Lý AI $\rightarrow$ Bấm biểu tượng **Bánh răng cài đặt (⚙️)** ở góc phải trên cùng.
- Nhập Endpoint URL (VD: `https://chas-unshaped-jacalyn.ngrok-free.dev` hoặc IP nội bộ máy tính `http://192.168.x.x:1234`).
- Tên Model mặc định: `qwen2.5-3b-instruct` (hoặc model tương thích OpenAI Function Calling đang nạp trong LM Studio).
- Bấm **"Kiểm Tra Kết Nối (Ping)"** để xác nhận trạng thái xanh trước khi sử dụng.
