# Đặc Tả Kỹ Thuật & Hướng Dẫn Vận Hành US-11: Giải Thuật Đệ Quy `tinhTongDongGopDeQuy(maSuKien)` Trên Nhánh Gia Phả

Tài liệu này đặc tả toàn diện thiết kế kỹ thuật, mô hình dữ liệu, giải thuật đệ quy kết hợp tầng CSDL và Service, hợp đồng API và bộ kiểm thử cho tính năng **[US-11] Hàm đệ quy `TinhTongDongGop()` xem tổng mức đóng góp của một nhánh bất kỳ**:
- **Cài đặt giải thuật đệ quy:** `tinhTongDongGopDeQuy(maSuKien)` trên thực thể `FamilyBranch` (`NhanhGiaDinh`) kết hợp **PostgreSQL Recursive CTE (`WITH RECURSIVE`)** dưới tầng CSDL và tầng Service.
- **Cộng dồn toàn bộ đóng góp:** Tiền mặt (`Cash`) + Giá trị quy đổi Hiện vật (`InKind`) từ bảng `CONTRIBUTION_MEMBER_DETAIL` (`contribution_member_details` / `ChiTietDongGop`) của nhánh đang xét và tất cả nhánh con/thành viên trực thuộc.
- **Bảo toàn ranh giới kiến trúc:** Tuân thủ 100% quy tắc ranh giới của **Spring Modulith** (`ArchitectureTest`).

> **Điểm vào API chính:**
> - `GET /api/v1/contributions/branches/{branchId}/recursive-total`: Lấy tổng mức đóng góp đệ quy (dạng số tiền `BigDecimal`).
> - `GET /api/v1/contributions/branches/{branchId}/recursive-summary`: Lấy báo cáo phân rã chi tiết (đóng góp trực tiếp của nhánh vs. tổng cộng từ các nhánh con, tiền mặt vs. hiện vật, số lượng thành viên, và danh sách đệ quy `childBranches`).
> 
> *Hỗ trợ đồng thời các đường dẫn alias:*
> - `GET /api/v1/branches/{branchId}/contributions/recursive-total`
> - `GET /api/v1/branches/{branchId}/contributions/recursive-summary`

---

## 1. Tổng Quan Nghiệp Vụ (User Story & Acceptance Criteria)

### 1.1. Bối Cảnh Nghiệp Vụ
Trong hệ thống Quản lý Gia tộc (**Family Management**), cấu trúc dòng họ được tổ chức dưới dạng cây phân cấp đa thế hệ (`FAMILY_BRANCH`: Đại Tôn $\rightarrow$ Chi $\rightarrow$ Phái $\rightarrow$ Nhánh $\rightarrow$ Phân nhánh). Khi tổ chức các sự kiện dòng họ (Giỗ Tổ, Tu bổ Từ đường, Lễ hội Xuân...) hoặc khi tổng kết niên độ:
- Các thành viên đóng góp tiền mặt (`US-09`) hoặc hiện vật cúng tiến (`US-10`) có thể đóng góp độc lập hoặc nhiều con cháu trong gia đình/hộ cùng đứng tên trong một phiếu thu (đồng đóng góp / Joint Contribution).
- Toàn bộ số tiền công đức đều được phân bổ chính xác theo từng thành viên tại cột `recorded_amount` trong bảng `contribution_member_details`.
- Trưởng tộc, Trưởng nhánh và thành viên cần tra cứu **tổng mức đóng góp của một nhánh bất kỳ** kèm toàn bộ con cháu các nhánh con trực thuộc nhánh đó.

### 1.2. Công Thức Nghiệp Vụ Đệ Quy
$$\text{TongDongGopDeQuy}(B, \text{maSuKien}) = \text{DongGopTrucTiep}(B, \text{maSuKien}) + \sum_{C \in \text{childBranches}(B)} \text{TongDongGopDeQuy}(C, \text{maSuKien})$$

Trong đó:
1. $\text{DongGopTrucTiep}(B, \text{maSuKien})$: Tổng số tiền ghi nhận tại `contribution_member_details.recorded_amount` của các thành viên trực thuộc chính nhánh $B$ (không tính các nhánh con).
2. $\sum \text{TongDongGopDeQuy}(C)$: Tổng mức đóng góp cộng dồn đệ quy từ tất cả các nhánh con cháu bên dưới.
3. **Phân loại đóng góp:**
   - **Tiền mặt (`Cash`):** Tính 100% số tiền phân bổ.
   - **Hiện vật (`InKind`):** Tính giá trị quy đổi tương đương của các hiện vật tiêu dùng/thụ lộc lễ (`Consumed`) hoặc tích lũy vào kho bảo vật từ đường (`AddedToCommonAssets`). **Tự động loại trừ** các hiện vật cho mượn phục vụ sự kiện đã hoàn trả gia chủ (`processing_status = 'Returned'`).
4. **Bộ lọc linh hoạt:** Hỗ trợ lọc theo `maSuKien` (`eventId`), lọc theo năm (`year`), hoặc bỏ trống cả hai để xem tổng mức đóng góp toàn thời gian.

---

## 2. Kiến Trúc & Ranh Giới Module (Spring Modulith)

### 2.1. Ranh Giới Phụ Thuộc (Zero Cyclic Dependency)
- Module `genealogy` có khai báo `allowedDependencies = {"core"}`.
- Module `contribution` có khai báo `allowedDependencies = {"core", "identity::api", "genealogy::api", "event::api"}`.
- Để thỏa mãn yêu cầu *"Cài đặt giải thuật đệ quy `tinhTongDongGopDeQuy(maSuKien)` trên lớp `NhanhGiaDinh` (`FamilyBranch`)"* mà **không** để `genealogy` phụ thuộc ngược vào `contribution`, hệ thống áp dụng kiến trúc:
  1. **Tầng Domain (`genealogy`):** Lớp thực thể `FamilyBranch` cung cấp phương thức nghiệp vụ đệ quy `tinhTongDongGopDeQuy(maSuKien, directBranchContributions)`.
  2. **Public API Contract (`genealogy::api`):** `GenealogyQueryService` cung cấp hàm `findBranchSubtree(...)` và `tinhTongDongGopDeQuy(...)` dựng cây `FamilyBranch` từ dữ liệu PostgreSQL Recursive CTE để kích hoạt logic đệ quy.
  3. **Tầng Service (`contribution`):** `BranchContributionServiceImpl` thực thi truy vấn PostgreSQL Recursive CTE tại `ContributionMemberDetailRepository` để tính `directTotalAmount` cho từng nhánh, sau đó chuyển giao cho `FamilyBranch.tinhTongDongGopDeQuy` tổng hợp đệ quy và bóc tách cấu trúc cây trả về Client.

```
┌────────────────────────────────────────────────────────────────────────┐
│ Client (Flutter Web / Mobile App)                                      │
└───────────────────────────────────┬────────────────────────────────────┘
                                    │ HTTP GET
                                    ▼
┌────────────────────────────────────────────────────────────────────────┐
│ [Module: contribution]                                                 │
│ • BranchContributionController                                         │
│ • BranchContributionServiceImpl                                        │
│ • ContributionMemberDetailRepository (PostgreSQL Recursive CTE)        │
└───────────────────────────────────┬────────────────────────────────────┘
                                    │ Gọi qua Public Interface
                                    ▼
┌────────────────────────────────────────────────────────────────────────┐
│ [Module: genealogy]                                                    │
│ • GenealogyQueryService / GenealogyQueryServiceImpl                    │
│ • FamilyBranchRepository (PostgreSQL Recursive CTE)                    │
│ • Domain Entity: FamilyBranch.tinhTongDongGopDeQuy(maSuKien)           │
└────────────────────────────────────────────────────────────────────────┘
```

---

## 3. Đặc Tả Chi Tiết Kỹ Thuật

### 3.1. Phương Thức Đệ Quy Trên Lớp `FamilyBranch` (`NhanhGiaDinh`)
File: `src/main/java/com/familyfund/genealogy/internal/entity/FamilyBranch.java`

```java
public BigDecimal tinhTongDongGopDeQuy(String maSuKien) {
    return tinhTongDongGopDeQuyInternal(maSuKien, null, new HashSet<>());
}

public BigDecimal tinhTongDongGopDeQuy(String maSuKien, Map<String, BigDecimal> directBranchContributions) {
    hydrateDirectContributions(maSuKien, directBranchContributions, new HashSet<>());
    return tinhTongDongGopDeQuyInternal(maSuKien, directBranchContributions, new HashSet<>());
}

private BigDecimal tinhTongDongGopDeQuyInternal(
        String maSuKien,
        Map<String, BigDecimal> directBranchContributions,
        Set<String> visitedBranchIds
) {
    if (this.branchId != null && !visitedBranchIds.add(this.branchId)) {
        return BigDecimal.ZERO; // Chống chu trình đệ quy vô hạn (Cycle Guard)
    }

    BigDecimal directAmount = (directBranchContributions != null && this.branchId != null)
            ? directBranchContributions.getOrDefault(this.branchId, BigDecimal.ZERO)
            : layDongGopTrucTiep(maSuKien);

    BigDecimal childrenTotal = BigDecimal.ZERO;
    if (this.childBranches != null && !this.childBranches.isEmpty()) {
        for (FamilyBranch child : this.childBranches) {
            if (child != null) {
                childrenTotal = childrenTotal.add(
                        child.tinhTongDongGopDeQuyInternal(maSuKien, directBranchContributions, visitedBranchIds)
                );
            }
        }
    }

    return directAmount.add(childrenTotal);
}
```

### 3.2. Truy Vấn PostgreSQL Recursive CTE Dưới CSDL
File: `src/main/java/com/familyfund/contribution/internal/repository/ContributionMemberDetailRepository.java`

```sql
WITH RECURSIVE branch_subtree AS (
    -- Điểm neo: Nhánh gốc cần xét
    SELECT 
        anchor.branch_id,
        anchor.parent_branch_id,
        anchor.clan_id,
        0 AS relative_depth,
        ARRAY[CAST(anchor.branch_id AS text)] AS visited_path
    FROM family_branches anchor
    WHERE anchor.clan_id = :clanId
      AND anchor.branch_id = :rootBranchId

    UNION ALL

    -- Đệ quy: Mở rộng tất cả các nhánh con trực tiếp và gián tiếp
    SELECT 
        child.branch_id,
        child.parent_branch_id,
        child.clan_id,
        parent_tree.relative_depth + 1 AS relative_depth,
        parent_tree.visited_path || CAST(child.branch_id AS text)
    FROM family_branches child
    INNER JOIN branch_subtree parent_tree
        ON child.parent_branch_id = parent_tree.branch_id
       AND child.clan_id = parent_tree.clan_id
    WHERE NOT (CAST(child.branch_id AS text) = ANY(parent_tree.visited_path))
),
eligible_contributions AS (
    SELECT 
        cmd.member_id,
        cmd.contribution_id,
        cmd.recorded_amount,
        c.contribution_type
    FROM contribution_member_details cmd
    INNER JOIN contributions c 
        ON c.contribution_id = cmd.contribution_id
    INNER JOIN events e 
        ON e.event_id = c.event_id
    LEFT JOIN in_kind_contributions ikc 
        ON ikc.contribution_id = c.contribution_id
    WHERE e.clan_id = :clanId
      AND (CAST(:eventId AS text) IS NULL OR c.event_id = CAST(:eventId AS text))
      AND (CAST(:year AS integer) IS NULL OR EXTRACT(YEAR FROM c.contribution_date AT TIME ZONE 'UTC') = CAST(:year AS integer))
      AND (c.contribution_type <> 'InKind' OR ikc.processing_status IS NULL OR ikc.processing_status <> 'Returned')
)
SELECT 
    bt.branch_id AS branchId,
    COALESCE(SUM(ec.recorded_amount), 0) AS directTotalAmount,
    COALESCE(SUM(CASE WHEN ec.contribution_type = 'Cash' THEN ec.recorded_amount ELSE 0 END), 0) AS directCashAmount,
    COALESCE(SUM(CASE WHEN ec.contribution_type = 'InKind' THEN ec.recorded_amount ELSE 0 END), 0) AS directInKindAmount,
    CAST(COUNT(DISTINCT ec.member_id) AS integer) AS contributingMemberCount,
    CAST(COUNT(DISTINCT ec.contribution_id) AS integer) AS contributionCount
FROM branch_subtree bt
LEFT JOIN members m 
    ON m.branch_id = bt.branch_id
LEFT JOIN eligible_contributions ec 
    ON ec.member_id = m.member_id
GROUP BY bt.branch_id, bt.relative_depth
ORDER BY bt.relative_depth ASC, bt.branch_id ASC;
```

---

## 4. Hợp Đồng API RESTful (Endpoints Contract)

### 4.1. Lấy tổng số tiền đóng góp đệ quy
* **Method:** `GET`
* **URL:** `/api/v1/contributions/branches/{branchId}/recursive-total` (hoặc alias `/api/v1/branches/{branchId}/contributions/recursive-total`)
* **Headers:**
  - `Authorization: Bearer <token>`
  - `X-Clan-Id: <clanId>` (hoặc query param `?clanId=...`)
* **Query Parameters:**
  - `eventId` (String, optional): Mã sự kiện (vd: `EVT-GIO-TO-2026`).
  - `maSuKien` (String, optional): Alias tiếng Việt của `eventId`.
  - `year` (Integer, optional): Năm phát sinh đóng góp (vd: `2026`).
* **Responses:**
  - `200 OK`:
    ```json
    {
      "success": true,
      "message": "Branch recursive contribution total calculated successfully",
      "data": 35000000.00,
      "timestamp": "2026-11-11T14:30:00Z"
    }
    ```

---

### 4.2. Lấy báo cáo phân rã chi tiết đóng góp đệ quy
* **Method:** `GET`
* **URL:** `/api/v1/contributions/branches/{branchId}/recursive-summary` (hoặc alias `/api/v1/branches/{branchId}/contributions/recursive-summary`)
* **Headers:**
  - `Authorization: Bearer <token>`
  - `X-Clan-Id: <clanId>`
* **Query Parameters:** Tương tự như endpoint 4.1.
* **Responses:**
  - `200 OK`:
    ```json
    {
      "success": true,
      "message": "Branch recursive contribution summary retrieved successfully",
      "data": {
        "branchId": "BR_ROOT",
        "branchName": "Đại Tôn Nguyễn Tộc",
        "generationLevel": 1,
        "parentBranchId": null,
        "representativeId": "MEM_TRUONG_TOC",
        "representativeName": "Nguyễn Trọng Hoàng",
        "eventId": "EVT-GIO-TO-2026",
        "eventName": "Đại Lễ Giỗ Tổ Thủy Tộc Xuân Bính Ngọ 2026",
        "year": 2026,
        "directMemberCount": 4,
        "totalRecursiveMemberCount": 18,
        "directContributingMemberCount": 1,
        "totalRecursiveContributingMemberCount": 5,
        "directContributionCount": 1,
        "directCashAmount": 5000000.00,
        "directInKindAmount": 0.00,
        "directTotalAmount": 5000000.00,
        "descendantsCashAmount": 9000000.00,
        "descendantsInKindAmount": 21000000.00,
        "descendantsTotalAmount": 30000000.00,
        "totalRecursiveCashAmount": 14000000.00,
        "totalRecursiveInKindAmount": 21000000.00,
        "totalRecursiveAmount": 35000000.00,
        "totalRecursiveAmountInWords": "Ba mươi lăm triệu đồng chẵn",
        "childBranches": [
          {
            "branchId": "BR_CHI1",
            "branchName": "Chi 1 Nguyễn Tộc",
            "generationLevel": 2,
            "parentBranchId": "BR_ROOT",
            "directTotalAmount": 4000000.00,
            "descendantsTotalAmount": 11000000.00,
            "totalRecursiveAmount": 15000000.00,
            "childBranches": []
          }
        ]
      },
      "timestamp": "2026-11-11T14:30:00Z"
    }
    ```

---

## 5. Chiến Lược Kiểm Thử (Automated Test Suites)

Hệ thống được bảo vệ bởi 4 bộ kiểm thử tự động toàn diện:
1. **`ArchitectureTest` (`Spring Modulith`)**:
   - Kiểm tra ranh giới package, bảo đảm phụ thuộc 1 chiều `contribution -> genealogy::api`, không phát sinh chu trình.
2. **`FamilyBranchRecursiveContributionTest` (`Unit Test`)**:
   - Kiểm thử thuật toán `FamilyBranch.tinhTongDongGopDeQuy(maSuKien)` trên cây 4 thế hệ, kiểm tra chống vòng lặp vô hạn và tính cách ly theo sự kiện.
3. **`BranchContributionServiceTest` (`Mockito Unit Test`)**:
   - Kiểm thử tầng Service: cô lập dòng họ (Multi-tenancy isolation), xử lý lỗi 404 khi nhánh không tồn tại, và tổng hợp báo cáo cây phân cấp.
4. **`BranchContributionRecursiveIntegrationTest` (`Spring Boot Integration Test`)**:
   - Chạy trên cơ sở dữ liệu thật:
     - Phiếu đồng đóng góp (Joint Contribution) nhiều thành viên phân bổ về đúng nhánh.
     - Loại trừ hiện vật đã hoàn trả (`Returned`).
     - Lọc theo năm, lọc theo sự kiện, tính toàn thời gian.
     - Gọi thực tế qua MockMvc REST API.
