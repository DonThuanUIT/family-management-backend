# Tài Liệu Kỹ Thuật Hệ Thống Family Management Backend (`familyfund-api`)

Chào mừng bạn đến với kho tài liệu kỹ thuật chính thức của dự án **Family Management Backend** (`familyfund-api`). Tài liệu này cung cấp cái nhìn toàn diện từ bức tranh kiến trúc tổng thể, chi tiết mô hình cơ sở dữ liệu PostgreSQL, các luồng nghiệp vụ cốt lõi, đến hướng dẫn vận hành môi trường Docker và trạng thái triển khai dự án.

---

## 📚 Mục Lục & Thứ Tự Đọc Khuyến Nghị

Để hiểu rõ hệ thống từ ngoài vào trong theo đúng chuẩn kỹ thuật, vui lòng theo dõi tài liệu theo thứ tự sau:

1. **[01-architecture.md](./01-architecture.md) - Tổng quan Kiến trúc & Hạ tầng**:
   - Mục tiêu nghiệp vụ & bài toán giải quyết.
   - Tech stack chi tiết (Java 21, Spring Boot 4.1.1, Spring AI, PostgreSQL 16 pgvector, Gradle Kotlin DSL).
   - Kiến trúc phân lớp & các phân hệ cốt lõi.
   - Môi trường Docker đa dịch vụ (PostgreSQL, Adminer, Swagger UI).
   - Giải pháp kiến trúc cho các ràng buộc phụ thuộc vòng (Circular Dependencies).

2. **[02-database-schema.md](./02-database-schema.md) - Đặc tả Cơ sở dữ liệu (PostgreSQL Schema)**:
   - Sơ đồ thực thể liên kết (ERD) hoàn chỉnh 21 bảng.
   - Chi tiết 4 phân hệ: Cây gia phả & Định danh, Quản lý sự kiện & Contextual RBAC, Tài chính tạm ứng/quyết toán & Sổ cái bất biến, Đóng góp quỹ đa hình & Tài sản dòng họ.
   - Thiết kế kiểu dữ liệu, ràng buộc khóa ngoại, CHECK constraints và chiến lược Indexes tối ưu hóa truy vấn.
   - Kịch bản DDL (`docker/postgres/init.sql`) & Seed Data khởi tạo.

3. **[03-business-flows.md](./03-business-flows.md) - Các Luồng Nghiệp Vụ Cốt Lõi (Business Flows)**:
   - Luồng 1: Định danh thành viên, cây gia phả & Phân quyền toàn cục.
   - Luồng 2: Vòng đời sự kiện & Phân quyền động (Contextual RBAC).
   - Luồng 3: Quy trình Tạm ứng $\rightarrow$ Quyết toán $\rightarrow$ Đối soát và cơ chế chặn khóa sổ khi có giải trình.
   - Luồng 4: Đóng góp quỹ họ (Tiền mặt/Hiện vật) & Thuật toán phân bổ công đức đệ quy theo nhánh phả hệ.
   - Luồng 5: Sổ cái Quỹ chung bất biến (Audit Trail - NFR-01) & Giao dịch bù trừ điều chỉnh sai sót.

4. **[project-status.md](./project-status.md) - Trạng Thái Dự Án & Kế Hoạch Triển Khai**:
   - Hiện trạng triển khai theo từng task (BE-Task 1, US-16: Hoàn thành).
   - Kết quả kiểm chứng thực tế (Compile, Test, Docker Compose verification).
   - Kế hoạch triển khai các task tiếp theo.

5. **[04-us16-general-clan-invite.md](./04-us16-general-clan-invite.md) - Đặc Tả Kỹ Thuật US-16: Tạo Mã Mời Chung Gia Phả**:
   - Phân tích User Story & Tiêu chí nghiệm thu (AC-01 đến AC-06).
   - Kiến trúc phân tầng Spring Boot, bảo mật Method Security & Exception Handling.
   - Thiết kế lược đồ bảng `invitation_codes` & các giải pháp kỹ thuật (SecureRandom, Crockford Base32, Collision Retry).
   - Sơ đồ luồng tuần tự tạo mã mời và kiểm tra mã công khai không cần đăng nhập.
   - Hướng dẫn kiểm thử tự động (Unit, MockMvc) & kiểm thử End-to-End thực tế với cURL/Swagger UI.

