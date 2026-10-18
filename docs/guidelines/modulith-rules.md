# Quy Tắc Kiến Trúc Modular Monolith & Spring Modulith (Coding Conventions)

Dự án **Family Management Backend** (`familyfund-api`) áp dụng mô hình **Modular Monolith** kết hợp **Domain-Driven Design (DDD)** và sử dụng **Spring Modulith 2.1.1** để kiểm soát tính đóng gói mã nguồn.

Mọi thành viên khi viết mã nguồn mới bắt buộc phải tuân thủ nghiêm ngặt các quy tắc kiến trúc dưới đây.

---

## 1. Nguyên Tắc Cốt Lõi (Core Principles)

1. **Một Bounded Context = Một Package cấp 1**:
   Tất cả các module nghiệp vụ đều nằm trực tiếp dưới package gốc `com.familyfund`:
   * `com.familyfund.identity` (Định danh, Tài khoản, Lời mời, Tiếp nhận thành viên)
   * `com.familyfund.genealogy` (Quản lý Dòng họ, Chi nhánh, Cây phả hệ)
   * `com.familyfund.event` (Quản lý Sự kiện & Contextual RBAC)
   * `com.familyfund.finance` (Tài chính, Tạm ứng, Quyết toán, Sổ cái bất biến)
   * `com.familyfund.contribution` (Đóng góp Quỹ & Quản lý Tài sản dòng họ)
   * `com.familyfund.ai` (Trợ lý AI & Vector Store)
   * `com.familyfund.core` (Shared Kernel: Cấu hình chung, Security, Exception, DTO toàn cục)

2. **Quy Tắc Phân Chia Package: `api` vs `internal`**:
   * **`com.familyfund.<module>.api` (Public Contract)**:
     - Chứa các interface dịch vụ dùng để gọi giữa các module (ví dụ: `GenealogyQueryService`).
     - Chứa DTO công khai, Enum dùng chung hoặc Domain Events.
     - Phải có file `package-info.java` khai báo `@org.springframework.modulith.NamedInterface("api")`.
     - **Chỉ có các class trong package này mới được phép cho module khác import.**
   * **`com.familyfund.<module>.internal` (Private Implementation)**:
     - Chứa REST Controllers (`internal.controller`), Service Implementations (`internal.service`), JPA Entities (`internal.entity`), Repositories (`internal.repository`), và Utilities (`internal.util`).
     - **TUYỆT ĐỐI KHÔNG ĐƯỢC PHÉP** import bất kỳ class nào trong `internal` từ bên ngoài module.

---

## 2. Quy Tắc Giao Tiếp Giữa Các Module (Cross-Module Communication)

### ❌ Những điều NGHIÊM CẤM (Forbidden):
* **Cấm rò rỉ Repository**: Tuyệt đối không inject `Repository` của module khác.
  *(Ví dụ sai: `InvitationServiceImpl` trong `identity` inject trực tiếp `FamilyBranchRepository` của `genealogy`).*
* **Cấm chia sẻ Entity JPA trực tiếp**: Không trả về Entity JPA của module này làm tham số hoặc kết quả cho module khác. Hãy sử dụng DTO hoặc kiểu dữ liệu nguyên thủy (Primitive/ID).
* **Cấm tạo phụ thuộc vòng (Circular Dependencies)**: Nếu Module A phụ thuộc Module B, thì Module B không được phép phụ thuộc ngược lại Module A.

### ✅ Cách làm ĐÚNG (Allowed):
* Trích xuất interface nghiệp vụ công khai trong `<module>.api` (ví dụ: `GenealogyQueryService`).
* Triển khai interface đó trong `<module>.internal.service` (ví dụ: `GenealogyQueryServiceImpl`).
* Module gọi chỉ cần inject interface từ package `api` thông qua Constructor Injection.

---

## 3. Khai Báo Ranh Giới Module (`package-info.java`)

Mỗi module phải có file `package-info.java` tại thư mục gốc của module để khai báo các module được phép phụ thuộc:

```java
// Ví dụ trong src/main/java/com/familyfund/identity/package-info.java
@org.springframework.modulith.ApplicationModule(
        allowedDependencies = {"core", "genealogy::api"}
)
package com.familyfund.identity;
```

---

## 4. Tự Động Kiểm Tra Kiến Trúc (Architecture Verification Test)

Dự án tích hợp sẵn class kiểm thử [`ArchitectureTest.java`](file:///d:/WorkSpace/Family-Management/family-management-backend/src/test/java/com/familyfund/ArchitectureTest.java):

```java
@Test
void verifyModularity() {
    ApplicationModules.of(FamilyfundApiApplication.class).verify();
}
```

### Trước khi tạo Pull Request (PR):
Mọi developer bắt buộc phải chạy:
```powershell
.\gradlew.bat test
```
Nếu bạn vô tình import một class nội bộ từ module khác hoặc vi phạm ranh giới dependency, bài test `verifyModularity()` sẽ fail ngay lập tức kèm thông báo lỗi chi tiết chỉ rõ vị trí vi phạm.

