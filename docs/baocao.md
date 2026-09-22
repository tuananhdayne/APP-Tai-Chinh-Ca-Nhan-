# BÁO CÁO TOÀN DIỆN HỆ THỐNG TRỢ LÝ TÀI CHÍNH CÁ NHÂN ĐA TÁC TỬ
**Dự Án:** Ứng Dụng Quản Lý Tài Chính Cá Nhân Android (Native Kotlin + Jetpack Compose + SQLite)  
**Mô Hình AI:** Qwen2.5-3B-Instruct (Tool Calling / Function Calling qua LM Studio / Ngrok Tunnel)  
**Ngày cập nhật:** 22/09/2026  
**Trạng thái kiểm thử:** 65 / 65 Test Cases PASS 100% (Thời gian chạy ~1-2s)  
**Trạng thái biên dịch:** `BUILD SUCCESSFUL` (APK Debug sẵn sàng cài đặt)

---

## MỤC LỤC
1. [Tổng Quan Kiến Trúc Đa Tác Tử (Multi-Agent ReAct Architecture)](#1-tổng-quan-kiến-trúc-đa-tác-tử)
2. [Hệ Thống 8 Công Cụ (Tools & Actions) & Giới Hạn Hoạt Động](#2-hệ-thống-8-công-cụ-tools--actions)
3. [Các Điểm Nghẽn Thực Tế & Giải Pháp Đã Xử Lý](#3-các-điểm-nghẽn-thực-tế--giải-pháp-đã-xử-lý)
4. [Xử Lý Chuỗi Đa Giao Dịch & Quản Lý Thẻ Linh Hoạt (Multi-Actions)](#4-xử-lý-chuỗi-đa-giao-dịch--quản-lý-thẻ-linh-hoạt-multi-actions)
5. [Tính Năng Thẻ Xem Trước (Preview Card) Tương Tác Độc Lập](#5-tính-năng-thẻ-xem-trước-preview-card-tương-tác-độc-lập)
6. [Báo Cáo Kết Quả Kiểm Thử Tự Động (65/65 Tests PASS)](#6-báo-cáo-kết-quả-kiểm-thử-tự-động)
7. [Hướng Dẫn Vận Hành & Khởi Chạy](#7-hướng-dẫn-vận-hành--khởi-chạy)

---

## 1. TỔNG QUAN KIẾN TRÚC ĐA TÁC TỬ

Hệ thống được thiết kế theo mô hình **ReAct Đa Bước Tuần Tự (Reasoning + Acting Workflow)** kết hợp với cơ chế **An Toàn Dữ Liệu (Safety-First)**:

```
[Người dùng nhập câu nói (1 hoặc nhiều khoản)]
                  │
                  ▼
[Bước 1: LLM Phân tích ý định & gọi Query Tool ngầm]
                  │  (VD: query_categories, query_balance_summary)
                  ▼
[Điện thoại thực thi ngầm trên SQLite cục bộ]
                  │  (Không làm phiền người dùng, trích xuất số tiền & danh mục chuẩn xác)
                  ▼
[Bước 2: LLM nhận kết quả & gọi Action Tools tuần tự]
                  │  (Hỗ trợ tối đa 6 action: create_transaction, update_transaction...)
                  ▼
[UI hiển thị Master Banner + Các Thẻ Xem Trước (Preview Cards)]
                  │  (Người dùng có thể sửa từng thẻ, hủy từng thẻ, hoặc lưu tất cả)
                  ▼
[Người dùng bấm "Xác Nhận Lưu"] ──► [Ghi dữ liệu an toàn vào SQLite]
```

### Nguyên tắc cốt lõi:
- **Tách biệt Query Tool và Action Tool:** Truy vấn dữ liệu chạy ngầm trong máy, chỉ các tác động làm thay đổi dữ liệu mới hiển thị thẻ Preview Card.
- **Không bao giờ tự ý ghi đè:** LLM không có quyền ghi trực tiếp vào cơ sở dữ liệu SQLite; mọi hành động ghi/sửa/xóa đều phải thông qua nút bấm xác nhận của người dùng.
- **Bảo toàn ngữ cảnh & chuỗi hành động đa tác vụ:** Hỗ trợ tối đa **6 bước suy luận liên tiếp** trong 1 lượt hỏi đáp (`maxIterations = 6`), cho phép bóc tách chuỗi tối đa 6 giao dịch cùng lúc mà vẫn đảm bảo thời gian phản hồi nhanh (~1-2s).

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
- **Số bước suy luận tối đa trong 1 câu nói:** `maxIterations = 6` bước suy luận.
- **Số lượng hành động tối đa trong 1 prompt:** Tối đa `6 giao dịch` (`actions.take(6)`).
- **Số bản ghi tra cứu lịch sử tối đa:** Tối đa `10 giao dịch` (`coerceIn(1, 10)`).
- **Hạn mức số tiền giao dịch:** Kiểu số `Long` (hỗ trợ tới hàng tỷ tỷ VNĐ).

---

## 3. CÁC ĐIỂM NGHẼN THỰC TẾ & GIẢI PHÁP ĐÃ XỬ LÝ

| Điểm nghẽn phát hiện | Hiện tượng thực tế | Nguyên nhân gốc rễ | Giải pháp đã khắc phục triệt để |
| :--- | :--- | :--- | :--- |
| **1. Bỏ sót giao dịch khi người dùng không gõ dấu phẩy** | Nhập: *"nay ăn sáng 200k đổ xăng 100k , đóng tiền điện 1 triệu"* $\rightarrow$ bị mất khoản *"đổ xăng 100k"*, gộp thành *"Nay ăn sáng đổ xăng: -200.000 đ"*. | Giữa `200k` và `đổ xăng` chỉ có dấu cách, regex trước đây chỉ tách theo `[,;\n]` hoặc `và, rồi`, khiến 2 khoản đầu bị dính liền thành 1 mệnh đề. | Nâng cấp thuật toán `splitMultiItemText`: Tự động nhận diện ranh giới số tiền trong câu kể cả khi **không có bất kỳ dấu phẩy hay liên từ nào**, hỗ trợ cả 2 dạng cú pháp `[Tên khoản] [Số tiền]` và `[Số tiền] [Tên khoản]`. |
| **2. Ghi chú bị nuốt chữ cụt đuôi ("Đóng tiền điện iệu")** | Nhập: *"đóng tiền điện 1 triệu"* $\rightarrow$ ghi chú sinh ra là `"Đóng tiền điện iệu"`. | Trong regex xóa số tiền, `tr` đặt trước `triệu` trong phép tuyển lựa `|`. Khi quét `"1 triệu"`, regex khớp `"1 tr"` và xóa đi, chừa lại chữ `"iệu"`. | Đảo thứ tự ưu tiên: từ dài đứng trước (`triệu`, `trieu` trước `tr`), kết hợp lookahead phủ định `(?![a-zA-ZÀ-ỹ0-9])` để không ăn lẹm vào chữ cái tiếp theo. Thêm từ đệm `"nay"` vào danh sách lọc bỏ. |
| **3. Hủy 1 thẻ làm hủy toàn bộ danh sách đề xuất** | Khi AI đề xuất 3 thẻ, bấm nút "Hủy" trên 1 thẻ bất kỳ thì cả 3 thẻ đều biến mất. | Trạng thái `cardStatus` trước đây gắn ở cấp tin nhắn (`ChatMessage`), callback hủy gọi chung toàn bộ tin nhắn. | Bổ sung trường `status: CardStatus` riêng cho từng `ToolAction`. Khi hủy thẻ nào, chỉ thẻ đó chuyển sang trạng thái "Đã hủy bỏ", các thẻ còn lại vẫn `PENDING` và Banner Master tự động trừ tiền. |
| **4. Lỗi rơi số 0 khi đọc tiền** | Người dùng nói *"ăn pizza 100k"* $\rightarrow$ AI lưu thành `10k`. | Mô hình nhỏ (Qwen 3B) có khả năng tính toán số học tiếng Việt hạn chế, dễ rớt số 0 ở các từ lóng `"100k"`, `"1000k"`. | Xây dựng bộ bóc tách Regex tiếng Việt nội bộ (`extractAmountFromText`): quét trực tiếp `100k` $\rightarrow$ `100.000 đ`, `1000k` $\rightarrow$ `1.000.000 đ`, `1tr5` $\rightarrow$ `1.500.000 đ`, `2 củ` $\rightarrow$ `2.000.000 đ`, `25 cành` $\rightarrow$ `25.000 đ`, `2 lít` $\rightarrow$ `200.000 đ`. Ưu tiên giá trị bóc tách này ghi đè lên payload AI. |
| **5. AI "chém gió" nói suông không lưu sổ** | Người dùng nói *"đóng tiền nhà 1000k"*, AI trả lời: *"Đã thêm vào sổ..."* nhưng không hiện thẻ Preview Card, sổ không có gì. | LLM bị hallucination, sinh câu trả lời bằng văn bản mà quên phát sinh lệnh gọi `create_transaction`. | Thiết lập chốt chặn **Auto-Recovery Guardrail**: Nếu tin nhắn AI trả về mà `pendingToolActions.isEmpty()` nhưng câu người dùng có chứa số tiền hợp lệ (> 0), hệ thống tự động bóc tách đa mệnh đề và tự động dựng thẻ xem trước để người dùng bấm Lưu ngay. |
| **6. "Khám bệnh" bị đề xuất tạo danh mục mới** | Người dùng nói *"khám bệnh 1000k"*, AI lại đề xuất tạo danh mục mới dù đã có danh mục Sức khỏe. | System Prompt chưa từng đưa danh sách danh mục có sẵn của người dùng vào ngữ cảnh suy luận của AI. | Nạp trực tiếp danh sách danh mục của người dùng vào System Prompt; thiết lập quy tắc bắt buộc: *"khám bệnh", "mua thuốc", "đi viện", "khám răng", "nha khoa"* **phải khớp vào `🩺 Sức khỏe`**, nghiêm cấm đề xuất tạo danh mục mới khi đã có sẵn. |
| **7. Báo cáo nhầm "còn thiếu" thay vì "đang vượt ngân sách"** | Tiêu hết 20.310.000 đ (vượt ngân sách 18.310.000 đ) nhưng AI lại nói *"và còn thiếu 1.831.000đ"*. | JSON công cụ trả về số âm `remaining_budget_vnd: -18310000`, khiến mô hình dịch chữ "remaining" thành "còn thiếu". | 1) Chuẩn hóa JSON: Khi vượt ngân sách thì `remaining_budget_vnd = 0`, cấp sẵn trường `over_budget_amount_formatted: "18.310.000 đ"`. 2) Prompt nghiêm cấm dùng từ "còn thiếu". 3) Bổ sung Guardrail khử ảo giác tự động làm sạch câu văn. |
| **8. Bàn phím che khuất màn hình & nút Mic trùng lặp** | Nhập tiền xong chọn danh mục thì bàn phím vẫn mở che mất giao diện; đáy màn hình xuất hiện 2 nút Mic. | 1) Thiếu sự kiện `keyboardController?.hide()` khi chọn danh mục. 2) Ô nhập liệu đặt icon Mic ở cả trailingIcon bên trong lẫn nút tròn bên ngoài. | 1) Tự động ẩn bàn phím ngay khi người dùng chọn một danh mục, chỉ mở lại khi chạm vào ô nhập tiền. 2) Dọn sạch icon trong ô nhập: khi trống không có icon, khi có chữ hiện nút X (Close), bên ngoài là nút Mic/Send chuyển đổi linh hoạt. |

---

## 4. XỬ LÝ CHUỖI ĐA GIAO DỊCH & QUẢN LÝ THẺ LINH HOẠT (MULTI-ACTIONS)

Hệ thống cho phép người dùng nhập các câu tự nhiên phức tạp chứa chuỗi nhiều giao dịch:
> Ví dụ: *"nay ăn sáng 200k đổ xăng 100k , đóng tiền điện 1 triệu"*

1. **Bóc tách độc lập:** Nhận diện đủ 3 giao dịch riêng biệt:
   - Khoản 1: `Ăn uống` 🍜 - Ghi chú: `Ăn sáng` - Số tiền: `200.000 ₫`
   - Khoản 2: `Đi lại` 🛵 - Ghi chú: `Đổ xăng` - Số tiền: `100.000 ₫`
   - Khoản 3: `Nhà ở` 🏠 - Ghi chú: `Đóng tiền điện` - Số tiền: `1.000.000 ₫`
2. **Thanh Tổng Hợp Thông Minh (Master Banner):**
   - Tiêu đề: **ĐỀ XUẤT 3 GIAO DỊCH (Tổng: 1.300.000 ₫)**
   - Nút hành động: **[✓ Xác Nhận Lưu Tất Cả (3 Khoản)]** cho phép lưu toàn bộ chỉ với 1 chạm.
3. **Thao tác độc lập từng thẻ (Granular Action):**
   - Người dùng có thể nhấn **[Hủy]** ở thẻ Đổ xăng: Thẻ này chuyển sang hộp xám *"Đã hủy bỏ đề xuất này"*.
   - Hai thẻ còn lại vẫn giữ nguyên nút lưu.
   - Master Banner tự động cập nhật: **ĐỀ XUẤT 2 GIAO DỊCH (Tổng: 1.200.000 ₫)** và nút **[Xác Nhận Lưu Tất Cả (2 Khoản)]**.
4. **Đồng bộ ngữ cảnh hội thoại cho AI:**
   - Hệ thống nạp thông tin chi tiết từng khoản (khoản nào ĐÃ LƯU, khoản nào ĐÃ HỦY, khoản nào ĐANG CHỜ DUYỆT) vào ngữ cảnh hội thoại.
   - Khi người dùng hỏi: *"Vừa rồi mình lưu những gì?"*, AI trả lời chính xác và minh bạch theo đúng trạng thái thực tế.

---

## 5. TÍNH NĂNG THẺ XEM TRƯỚC (PREVIEW CARD) TƯƠNG TÁC ĐỘC LẬP

Thẻ xem trước `CreateTransactionCard` được nâng cấp toàn diện:

1. **Hiển thị trực quan:**
   - Phân biệt rõ ràng Thu nhập (Xanh lục `+`) và Chi tiêu (Đỏ `-`).
   - Định dạng số tiền VNĐ rõ ràng (VD: `1.000.000 đ`).
   - Hiển thị Icon và Tên danh mục (VD: `🩺 Sức khỏe`, `🍜 Ăn uống`, `🛵 Đi lại`).
   - Ghi chú ngắn gọn, tinh tế (2-4 từ, không rác).
2. **Khả năng chỉnh sửa trực tiếp (Editable Preview Card):**
   - Người dùng bấm biểu tượng **Sửa (✏️)** cạnh số tiền khi thẻ đang ở trạng thái chờ duyệt (`PENDING`).
   - **Sửa Số tiền:** Ô nhập số tiền với dòng hỗ trợ cập nhật định dạng VNĐ theo thời gian thực.
   - **Đổi Danh mục:** Bấm vào thẻ danh mục để chọn lại danh mục từ danh sách các danh mục có sẵn.
   - **Nút `+ Tạo danh mục mới`:** Tích hợp trực tiếp trong hộp thoại chọn danh mục, cho phép tạo nhanh danh mục mới bất cứ lúc nào.
   - **Sửa Ghi chú:** Ô nhập liệu cho phép sửa lại nội dung ghi chú giao dịch.
   - **Lưu thay đổi:** Bấm "Xong" để cập nhật ngay trên thẻ xem trước trước khi bấm **"Xác Nhận Lưu"**.

---

## 6. BÁO CÁO KẾT QUẢ KIỂM THỬ TỰ ĐỘNG (65/65 TESTS PASS)

Toàn bộ quá trình kiểm thử được tự động hóa 100% bằng JVM Unit Test (**không cần thiết bị thật, không build file APK**), hoàn thành trong **~1-2 giây**:

```
BUILD SUCCESSFUL in 1s
65 actionable tasks: 65 tests completed, 0 failed, 0 skipped
```

### Chi tiết các bộ test:

#### 1. Bộ kiểm thử chuỗi hành động nối tiếp nhau (`AiSequentialActionChainTest.kt` - 17 Tests)
- `testChain1_ReActTwoStepWorkflow_QueryToCreate`: Chuỗi 2 bước ReAct: Query `query_categories` $\rightarrow$ Action `create_transaction` $\rightarrow$ Card.
- `testChain2_MultipleExpensesChain_TriggerOverBudget`: Ghi chi tiêu liên tiếp $\rightarrow$ Tác động ngân sách tổng $\rightarrow$ Cảnh báo vượt hạn mức tháng.
- `testChain3_CategorySpendingChain_TriggerCategoryOverBudget`: Chi tiêu liên tiếp trong 1 danh mục $\rightarrow$ Cảnh báo vượt hạn mức danh mục.
- `testChain4_CreateThenSearchThenUpdateChain`: Tạo giao dịch $\rightarrow$ Tìm kiếm $\rightarrow$ Sửa $\rightarrow$ Số tiền điều chỉnh.
- `testChain5_CreateThenSearchThenDeleteChain`: Tạo giao dịch nhầm $\rightarrow$ Tìm kiếm $\rightarrow$ Xóa $\rightarrow$ Hoàn trả ngân sách.
- `testChain6_InterleavedIncomeAndExpenseChain`: Thu và Chi đan xen liên tục nhiều lượt $\rightarrow$ Cân đối số dư ròng chuẩn xác.
- `testChain7_AutoRecoveryThenConfirmThenQueryHistory`: Chốt chặn Auto-Recovery phục hồi hành động $\rightarrow$ Lưu vào sổ $\rightarrow$ Tìm thấy ngay trong lịch sử.
- `testChain8_KhamBenh_MustMatchHealthCategory_NotProposeNewCategory`: *"Khám bệnh 1000k"* khớp chuẩn danh mục **Sức khỏe 🩺**, không tạo danh mục mới.
- `testChain9_EditablePreviewCard_ModifyBeforeConfirm`: Sửa đổi số tiền và ghi chú trực tiếp trên thẻ xem trước $\rightarrow$ Lưu chính xác dữ liệu đã sửa.
- `testChain10_CreateCategoryWhenTrulyNovel`: Khoản chi hoàn toàn mới lạ ("Nuôi thú cưng 🐱") $\rightarrow$ Đề xuất `create_category`.
- `testChain11_MultiActionsPrompt_ExtractUpToSixTransactions`: Bóc tách chuỗi tối đa 6 giao dịch từ 1 prompt duy nhất.
- `testChain12_MultiActionsPrompt_MasterCardCalculation`: Tính toán chính xác tổng tiền và số lượng cho Master Banner.
- `testChain13_MultiActionsPrompt_NoCommasWithConjunctions`: Tách mệnh đề mượt mà qua liên từ `và`, `rồi`.
- `testChain14_MultiActionsPrompt_MixedQueryAndAction`: Xử lý mượt mà prompt hỗn hợp: Tra cứu ngân sách + Đề xuất chi tiêu mới.
- `testChain15_ConversationHistoryIncludesConfirmationStatus`: Lịch sử hội thoại phản ánh chính xác trạng thái duyệt thực tế.
- `testChain16_CancelSingleCardInList_OtherCardsRemainPending`: Hủy 1 thẻ trong danh sách đề xuất, các thẻ còn lại vẫn pending và tổng tiền được cập nhật chuẩn xác.
- `testChain17_ConfirmAll_OnlyExecutesPendingActions`: Khi bấm "Xác Nhận Lưu Tất Cả", chỉ lưu những thẻ chưa bị hủy.

#### 2. Bộ kiểm thử ngân sách và 100+ câu nói người dùng (`AiBudgetAndUserInputsComprehensiveTest.kt` - 35 Tests)
- Kiểm tra tính toán ngân sách tổng: hạn mức, số dư, tỷ lệ phần trăm, cờ `is_over_budget`.
- Kiểm tra hạn mức từng danh mục chi tiết (Ăn uống, Mua sắm, Đi lại,...).
- Kiểm thử toàn diện 100+ câu nói thực tế: Ăn uống, Đi lại, Nhà ở, Mua sắm, Giải trí, Sức khỏe, Giáo dục, Hiếu hỉ, Thu nhập.
- `testSplitMultiItemText_UserExample_BreakfastGasElectricity_NoCommaBetweenFirstTwo`: Kiểm thử câu thực tế không có dấu phẩy giữa các khoản.
- `testSplitMultiItemText_ZeroCommas_AllSpaces`: Kiểm thử câu hoàn toàn dùng khoảng trắng.
- `testCleanNote_ElectricityBillNoIeu`: Kiểm tra loại bỏ triệt để lỗi đuôi `"iệu"` trong `"đóng tiền điện 1 triệu"`.
- `testCleanNote_ConciseAndRemovesFillers`: Rút gọn ghi chú tinh tế, xóa bỏ từ đệm `"nay"`, `"hôm nay"`.

#### 3. Bộ kiểm thử định nghĩa công cụ & Schemas (`OpenAiToolSchemasTest.kt` - 11 Tests)
- Kiểm tra tính hợp lệ và đầy đủ của 8 Tools theo chuẩn OpenAI.
- Kiểm tra phân loại ý định Thu/Chi (`isIncomeIntent`).
- Kiểm tra các chỉ thị prompt hướng dẫn AI xử lý ngân sách.

#### 4. Bộ kiểm thử ViewModel (`MainScreenViewModelTest.kt` - 2 Tests)
- Kiểm thử khởi tạo và chuyển đổi trạng thái tab điều hướng trên màn hình chính.

---

## 7. HƯỚNG DẪN VẬN HÀNH & KHỞI CHẠY

### 1. Lệnh chạy kiểm thử tự động (Không tạo APK):
```bash
export JAVA_HOME="/snap/android-studio/current/jbr"
./gradlew testDebugUnitTest
```

### 2. Lệnh biên dịch file cài đặt APK:
```bash
export JAVA_HOME="/snap/android-studio/current/jbr"
./gradlew assembleDebug
```
File APK cài đặt được tạo tại:
`app/build/outputs/apk/debug/app-debug.apk`

### 3. Cấu hình máy chủ LM Studio trong ứng dụng:
- Mở màn hình Trợ Lý AI $\rightarrow$ Bấm biểu tượng **Bánh răng cài đặt (⚙️)** ở góc phải trên cùng.
- Nhập Endpoint URL (VD: `https://chas-unshaped-jacalyn.ngrok-free.dev` hoặc IP nội bộ máy tính `http://192.168.x.x:1234`).
- Tên Model mặc định: `qwen2.5-3b-instruct` (hoặc model tương thích OpenAI Function Calling đang nạp trong LM Studio).
- Bấm **"Kiểm Tra Kết Nối (Ping)"** để xác nhận trạng thái xanh trước khi sử dụng.
