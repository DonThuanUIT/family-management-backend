# Đặc tả US-32: Đính kèm ảnh chân dung, tiểu sử, tư liệu gia sử vào hồ sơ thành viên

Trạng thái: **Đã triển khai backend và kiểm chứng Supabase thật; đã bỏ lưu trữ local; frontend chưa triển khai**. Ngày cập nhật: **12/11/2026**.

## 1. User story và phạm vi

**Là** thành viên hoặc người quản lý gia phả trong phạm vi được phân công, **tôi muốn** bổ sung ảnh chân dung, viết tiểu sử và đính kèm tư liệu gia sử cho hồ sơ thành viên, **để** lưu giữ ký ức, tìm hiểu cuộc đời người thân và chia sẻ tư liệu đúng đối tượng.

Nguồn: yêu cầu US-32 do người dùng cung cấp trong phiên làm việc. Các giới hạn dung lượng, định dạng và mức chia sẻ dưới đây là **đề xuất cho đồ án**, không phải yêu cầu đã được xác nhận từ backlog gốc.

### 1.1. Hiện trạng trước triển khai đã đối chiếu mã nguồn

- Backend dùng Spring Boot, PostgreSQL, Flyway và Spring Modulith. `genealogy.internal.entity.Member` chưa có avatar hoặc biography; tenant của thành viên được suy ra qua `members.branch_id → family_branches.clan_id`.
- `MemberController` có API danh sách, tìm kiếm, tạo hồ sơ, chuyển nhánh và cập nhật tình trạng sống/mất; chưa có endpoint chi tiết `GET /api/v1/members/{memberId}` trong controller này.
- US-25 có `MemberPrivacyPolicy` và `PrivacyEvaluationContext` cho ngày sinh, điện thoại, email. Quyền đó không đồng nghĩa được xem mọi tài liệu đính kèm.
- Frontend là Flutter Web, Android, iOS. Feature gia phả đã có sidebar chi tiết nhánh, repository và datasource; trang chi tiết thành viên đầy đủ là phần cần bổ sung cho US này.
- Các API và cấu trúc dữ liệu ở tài liệu này là **hợp đồng đề xuất**. JSON theo `ApiResponse` thực tế (`success`, `message`, `data`, `timestamp`), tên trường API camelCase; cột SQL snake_case.

### 1.2. MVP

- Một ảnh đại diện hiện hành cho mỗi thành viên; chọn ảnh, cắt vuông, xem trước, thay và xóa.
- Một bài tiểu sử Rich Text; tạo, sửa, xóa nội dung và cấu hình quyền xem.
- Nhiều tư liệu PDF hoặc ảnh; tải lên, sửa mô tả/quyền xem, xem ảnh, tải tệp và xóa.
- Áp dụng cho thành viên còn sống, đã mất và hồ sơ ảo. Người quản lý đúng phạm vi có thể cập nhật hồ sơ không có tài khoản.
- Dùng Supabase Storage ở cả môi trường phát triển và demo; không còn adapter lưu trữ filesystem.

Ngoài MVP: OCR, video/audio, DOCX/XLSX/ZIP, nhúng ảnh vào bài tiểu sử, bình luận, đồng biên tập, lịch sử phục hồi phiên bản, quy trình duyệt tư liệu và chia sẻ công khai ngoài dòng họ. PDF có thể tải xuống để mở bằng trình xem của thiết bị; trình đọc PDF nhúng chưa bắt buộc.

## 2. Chọn dịch vụ lưu trữ cho đồ án

### 2.1. Đã chốt: Supabase Storage cho cả ảnh và tư liệu

Người dùng đã đồng ý chọn Supabase và yêu cầu triển khai backend theo đặc tả ngày **11/11/2026**. Bắt đầu với gói Free cho đồ án. Đã kiểm chứng bucket private và thao tác upload/download/delete trên cloud thật; theo yêu cầu tiếp theo của người dùng, adapter filesystem và cấu hình local đã được loại bỏ.

Theo [bảng giá Supabase](https://supabase.com/pricing), gói Free hiện có **1 GB file storage**, **5 GB egress** và **5 GB cached egress**, tối đa **50 MB/tệp**. Project Free có thể bị tạm dừng sau **một tuần không hoạt động**; tối đa hai project hoạt động. Đây là hạn mức nhà cung cấp, cần kiểm tra lại trước khi triển khai/bảo vệ đồ án.

Lý do lựa chọn: US-32 cần cả PDF và ảnh có quyền xem; một kho object riêng tư giúp thống nhất quản lý. Chỉ dùng dịch vụ Storage, giữ nguyên database PostgreSQL và JWT hiện có. Không cần chuyển toàn bộ backend sang Supabase.

[Supabase hỗ trợ bucket private và URL có thời hạn](https://supabase.com/docs/guides/storage/buckets/fundamentals). MVP dùng bucket private và backend truyền nội dung tệp sau khi kiểm tra quyền mỗi request. Không dùng public URL cho tư liệu giới hạn quyền. Image Transformations không nằm trong gói Free theo bảng giá; việc crop/nén sẽ thực hiện ở client và chuẩn hóa ở backend.

Ước lượng minh họa: 300 avatar × 200 KB + 150 tài liệu × 2 MB ≈ **360 MB**; phù hợp một bộ dữ liệu demo nhỏ. Đây là giả định dung lượng, không phải cam kết đáp ứng mọi quy mô; lượt tải lại còn tiêu thụ egress.

### 2.2. Các lựa chọn khác

- **Cloudinary Free:** phù hợp khi trọng tâm là xử lý ảnh. Có **25 credits/tháng** dùng chung cho lưu trữ, biến đổi và băng thông; không cần thẻ để đăng ký. Không hiểu thành 25 GB lưu trữ cộng thêm 25 GB băng thông độc lập. Với US này, đề xuất Supabase để thống nhất kho ảnh và tài liệu riêng tư. Nguồn: [Cloudinary Billing and Plans](https://cloudinary.com/documentation/billing_and_plans).
- **Firebase Storage:** không phải lựa chọn ưu tiên nếu muốn tránh tài khoản thanh toán; tài liệu hiện hành yêu cầu Blaze và liên kết Cloud Billing để dùng Cloud Storage. Có hạn mức miễn phí tùy loại bucket, nhưng vượt hạn mức có thể tính phí. Nguồn: [Firebase Storage billing requirements](https://firebase.google.com/docs/storage/faqs-storage-changes-announced-sept-2024).
- **Local filesystem (phương án đã loại bỏ):** không còn được hỗ trợ trong backend sau khi xác nhận Supabase thật hoạt động.

### 2.3. Định hướng mở rộng: album ảnh và khoảnh khắc gia đình

**Supabase Storage đáp ứng việc lưu ảnh kỷ niệm và album gia đình.** Album, chú thích, thời điểm chụp, thành viên xuất hiện trong ảnh và quyền chia sẻ là nghiệp vụ do ứng dụng quản lý trong PostgreSQL; Storage lưu bytes ảnh. Không cần đổi nhà cung cấp chỉ vì mở thêm tính năng album.

Gói Free phù hợp thử nghiệm/demo; không coi là kho ảnh miễn phí không giới hạn. Minh họa dùng đơn vị thập phân: 1.000 ảnh × 1 MB ≈ 1 GB, đã chiếm toàn bộ dung lượng file của gói Free trước khi tính avatar, PDF, thumbnail và ảnh gốc. 10.000 ảnh cùng kích thước ≈ 10 GB. Lưu lượng tải ảnh phải tính riêng: 1.000 lượt xem ảnh × 1 MB ≈ 1 GB dữ liệu truyền; chỉ áp dụng khi mỗi lượt thực sự tải đủ ảnh đó.

Theo [bảng giá Supabase](https://supabase.com/pricing), Pro hiện bắt đầu từ **25 USD/tháng**, gồm **100 GB file storage**, **250 GB egress** và **250 GB cached egress**; tài nguyên vượt quota và các thành phần khác có thể phát sinh phí. Gói trả phí là phương án khi kho ảnh phát triển, không phải chi phí bắt buộc ngay cho đồ án.

Định hướng thiết kế cho user story album sau này:

- Tạo nghiệp vụ `family_albums` và `album_photos` riêng; không ép ảnh album vào `member_attachments` hoặc giới hạn 20 tư liệu/hồ sơ. Một ảnh có thể gắn nhiều thành viên qua bảng liên kết.
- Tiếp tục dùng `FileStorage` để tái sử dụng adapter Supabase. Khi xây album, tổng quát hóa metadata file khỏi yêu cầu mọi object phải thuộc một member: file gắn clan, bảng nghiệp vụ quản lý liên kết đến hồ sơ hoặc album; không sửa schema/code chỉ để dự phòng trong US-32.
- Bucket album private với policy nghiệp vụ riêng; quyền xem album không tự kế thừa quyền xem liên hệ hoặc chỉnh sửa hồ sơ. Mặc định chưa mở public URL.
- Tạo thumbnail và ảnh để xem trên màn hình; danh sách album phân trang/lazy-load, không tải ảnh gốc cho mọi ô ảnh. Cho phép giữ ảnh gốc nếu người dùng cần và tính dung lượng cho toàn bộ các phiên bản. Gói Free tự xử lý resize/nén; Image Transformations của Supabase không nằm trong Free theo bảng giá.
- Backend proxy hiện tại phù hợp MVP. Khi album nhiều ảnh/lượt xem, đánh giá signed URL thời hạn ngắn hoặc cơ chế phân phối có xác thực để giảm tải backend; phải ghi rõ cửa sổ thu hồi quyền và kiểm tra cơ chế cache. [Supabase có resumable uploads](https://supabase.com/docs/guides/storage/uploads/resumable-uploads) cho luồng upload cần tiếp tục sau gián đoạn; tích hợp sẽ cần thiết kế xác thực riêng phù hợp JWT hiện có.
- Tính lại quota tổng khi thêm album; giới hạn 500 MiB ở US-32 là cấu hình demo của ứng dụng, không phải giới hạn kỹ thuật Supabase. Theo dõi storage/egress, có chính sách xóa/khôi phục và backup object bên cạnh metadata.

Album là hướng mở rộng, **không bổ sung vào phạm vi triển khai MVP US-32** trong lần cập nhật này.

## 3. Quyền truy cập và quy tắc nghiệp vụ

### 3.1. Quyền chỉnh sửa

Đề xuất chính sách thống nhất `canEditMemberMedia`:

- Chính chủ có tài khoản Active liên kết đúng `memberId`, hồ sơ còn sống: chỉnh sửa hồ sơ của mình.
- `ROLE_CLAN_HEAD`: chỉnh sửa hồ sơ trong dòng họ hiện hành.
- `ROLE_BRANCH_HEAD`: chỉnh sửa hồ sơ thuộc nhánh đang quản lý và các nhánh con, xác định từ cây nhánh hiện tại.
- `ROLE_ADMIN`: chỉ khi đã được hệ thống xác nhận quyền trong tenant hiện hành; không bỏ qua cách ly tenant vì có role admin.
- BTC sự kiện, thủ quỹ hoặc người đã tải tệp lên không mặc nhiên có quyền chỉnh sửa hồ sơ đó.
- Hồ sơ ảo/đã mất: người quản lý đúng phạm vi cập nhật; chưa mở quyền cho mọi hậu duệ trong MVP.

Backend kiểm tra tài khoản, tenant, hồ sơ và phạm vi thực tế. Không tin `memberId`, `clanId`, vai trò hay `uploadedBy` do client tự gửi. Khi chuyển nhánh/thu hồi vai trò phải tính lại quyền, không dựa trên nhánh của lần upload cũ.

### 3.2. Quyền xem

Avatar hiển thị cho người đăng nhập được phép xem hồ sơ trong cùng dòng họ. Không mở công khai Internet.

Tiểu sử và **từng tư liệu** có `visibility` độc lập:

- `CLAN`: thành viên hợp lệ trong cùng dòng họ được xem.
- `BRANCH`: thành viên cùng nhánh với hồ sơ hoặc thuộc các nhánh con của nhánh đó được xem; không tự mở cho nhánh cha/nhánh anh em.
- `RESTRICTED`: chính chủ và người quản lý có quyền chỉnh sửa hồ sơ được xem.

Với cả ba mức, chính chủ và người quản lý đúng phạm vi vẫn được xem. Mặc định tiểu sử mới và tài liệu mới là **RESTRICTED**; người có quyền sửa chủ động chọn phạm vi rộng hơn. Không có mức PUBLIC trong MVP. Avatar là thông tin nhận diện cùng dòng họ; UI phải giải thích phạm vi trước khi lưu.

Mọi quyền xem đều bắt buộc thỏa điều kiện cùng tenant và có quyền truy cập hồ sơ trước. API lọc tài liệu trước khi phân trang/đếm; không trả tên, số lượng ẩn, object key hoặc URL của tư liệu không được phép xem. Tiểu sử bị hạn chế trả `biography: null`, `biographyVisibility: "RESTRICTED"` và `biographyAccess: "RESTRICTED"`; tiểu sử được xem trả `biographyAccess: "VISIBLE"`. Không suy ra trạng thái có/không có nội dung từ dữ liệu bị ẩn.

Không tái sử dụng quyền BTC xem liên hệ của US-25 cho tư liệu gia sử. Nội dung được tác giả chọn chia sẻ có thể chứa thông tin nhạy cảm; frontend hiển thị nhắc kiểm tra nội dung trước khi chọn CLAN/BRANCH. Backend không có khả năng tự tách và che ngày sinh/SĐT bên trong bài viết hoặc PDF.

### 3.3. Giới hạn đề xuất

- Avatar: JPEG/PNG, tối đa 5 MiB đầu vào, cạnh tối thiểu 128 px, giới hạn giải mã 20 megapixel. Client crop 1:1; backend xác nhận ảnh vuông, sửa orientation, xóa EXIF và encode JPEG 512 × 512 px. Không giữ ảnh gốc. Client hướng tới khoảng 200 KB; đây không phải điều kiện bắt buộc cho mọi ảnh.
- Tư liệu: PDF/JPEG/PNG, tối đa 10 MiB/tệp, 20 tư liệu đang hoạt động/hồ sơ. JPEG/PNG giới hạn 20 megapixel và được chuẩn hóa/xóa EXIF; không resize làm mất khả năng đọc bản scan. `sizeBytes` phản ánh tệp thực tế lưu sau xử lý.
- Tiêu đề tư liệu: 1–150 ký tự sau trim; mô tả tùy chọn, tối đa 1.000 ký tự. Tên tệp gốc tối đa 255 ký tự sau chuẩn hóa, không dùng làm đường dẫn lưu trữ.
- Tiểu sử: tối đa 20.000 ký tự nội dung thuần và 100 KiB JSON UTF-8; định dạng đậm/nghiêng/gạch chân, heading 1–3, danh sách, trích dẫn và liên kết HTTPS. Không cho embed, ảnh base64, HTML thô, script hoặc liên kết javascript/data/file.
- Hạn mức ứng dụng đề xuất: 500 MiB tổng object của đồ án, gồm file đang dùng, upload chờ và file chờ xóa. Cần bộ đếm/reservation dùng chung cho mọi tenant, kiểm tra atomically để tránh upload đồng thời vượt hạn mức. Theo dõi dashboard nhà cung cấp cho egress; hạn mức storage nội bộ không giới hạn được lượt tải.

## 4. Tiêu chí chấp nhận

1. **AC-01 — Avatar:** người được phép chọn JPEG/PNG, crop vuông, xem trước rồi lưu; reload hiển thị ảnh mới trên chi tiết, danh sách và cây gia phả. Hủy crop không gửi upload; xóa ảnh trả về chữ viết tắt mặc định.
2. **AC-02 — An toàn khi thay:** upload/chuẩn hóa/lưu metadata lỗi không làm mất avatar hiện hành. Tệp mới không được tham chiếu được đưa vào hàng đợi dọn dẹp.
3. **AC-03 — Tiểu sử:** tạo/sửa Rich Text, lưu, tải lại giữ đúng định dạng được hỗ trợ. Xóa về nội dung rỗng hợp lệ; backend từ chối nội dung/định dạng vượt giới hạn.
4. **AC-04 — Tư liệu:** upload thành công trả metadata; người có quyền xem thấy danh sách đúng phạm vi, preview ảnh và tải file gốc đã được hệ thống chấp nhận. Người có quyền sửa có thể sửa tiêu đề/mô tả/quyền xem và xóa.
5. **AC-05 — Tenant/RBAC:** đổi `memberId`, `attachmentId`, `X-Clan-Id` hay đoán đường dẫn không đọc/sửa được tài nguyên dòng họ khác hoặc ngoài phạm vi quản lý.
6. **AC-06 — Quyền xem:** CLAN/BRANCH/RESTRICTED được thực thi trên cả metadata và nội dung file, trên Supabase Storage. Tên và số lượng tư liệu ẩn không rò rỉ qua phân trang.
7. **AC-07 — Validation:** tệp rỗng, giả MIME, ảnh hỏng, quá dung lượng/kích thước, PDF không hợp lệ hoặc vượt số lượng đều bị từ chối với thông báo tiếng Việt; kiểm tra lại ở backend.
8. **AC-08 — Lỗi mạng:** có tiến trình upload, thông báo lỗi và nút thử lại; không báo thành công trước khi backend commit. Mỗi file có trạng thái riêng khi chọn nhiều tệp.
9. **AC-09 — Đồng thời:** phiên bản cũ trả 409; không ghi đè thay đổi của người khác hoặc vượt giới hạn 20 tài liệu do hai request đồng thời.
10. **AC-10 — Thu hồi:** sau đổi quyền/xóa, request nội dung tiếp theo phải kiểm tra lại và từ chối nếu mất quyền. Không cam kết thu hồi bản mà người dùng đã tải xuống.
11. **AC-11 — Đa nền tảng:** luồng chọn file/crop/biên tập hoạt động trên Flutter Web, Android, iOS; có loading, rỗng, lỗi, chỉ đọc và không đủ quyền.
12. **AC-12 — Tương thích:** không thay đổi cờ chia sẻ ngày sinh/điện thoại/email của US-25; dữ liệu hồ sơ cũ vẫn đọc được sau migration, avatar mặc định rỗng.

## 5. Kiến trúc đề xuất

Nghiệp vụ thuộc `genealogy`; adapter lưu trữ dùng chung ở `core.storage`.

```text
Flutter Screen/Widget → Notifier → Repository → RemoteDataSource → Dio
  → MemberMediaController / MemberDetailController
  → MemberMediaService + MemberMediaPolicy (tenant, edit, view)
  → genealogy repositories (PostgreSQL)
  → core.storage.FileStorage (SupabaseStorageAdapter)
```

Các thành phần thực tế: `MemberMediaController`, `MemberMediaService`, `MemberMediaRepository` (JDBC cho bảng metadata/file và tư liệu), `MemberMediaPolicy`, `BiographyValidator`, `MediaFileValidator`, `MediaCleanupWorker`. `FileStorage` cung cấp `put`, `open`, `delete`, `exists`; không chứa logic phân quyền theo dòng họ. Module khác chỉ gọi public API của genealogy nếu cần, không import entity/repository `internal`. Liên kết tài khoản và roles hiện hành đọc qua port core `AccountAccess`, adapter thuộc identity, tránh phụ thuộc vòng.

Supabase chỉ nhận yêu cầu từ backend bằng credential server. Không giả định JWT tự phát hành của dự án được Supabase hiểu như token Supabase Auth. [Service key có thể bỏ qua RLS](https://supabase.com/docs/guides/storage/security/access-control), nên backend phải kiểm tra quyền trước mọi thao tác; không đưa key vào Flutter, log hoặc repository. Bucket `member-media` private, không cấp quyền đọc/ghi anonymous và không tạo policy cho client truy cập trực tiếp.

MVP truyền file qua backend, không upload thẳng từ Flutter lên cloud. Endpoint, bucket và credential Supabase lấy từ môi trường. Không còn cấu hình chọn provider hoặc local root. Cloud lỗi trả lỗi rõ ràng, không tạo file fallback trên ổ đĩa.

## 6. Mô hình dữ liệu và migration

Đây là thiết kế logic; đặt số Flyway mới sau migration mới nhất tại thời điểm triển khai, không sửa migration đã chạy.

### 6.1. Mở rộng `members`

- `avatar_file_id UUID NULL`: tham chiếu `stored_files`.
- `avatar_url TEXT NULL`: đường dẫn API tương đối ổn định, ví dụ `/api/v1/members/MEM001/avatar/content`; do server quản lý. Không lưu signed URL hết hạn hay URL bên ngoài do client gửi. Khi xóa ảnh, đặt null; mapping response sang `avatarUrl`.
- `biography JSONB NULL`: Quill Delta theo hợp đồng dưới đây, không lưu blob ảnh hoặc HTML tùy ý.
- `biography_visibility VARCHAR(20) NOT NULL DEFAULT 'RESTRICTED'`, CHECK CLAN/BRANCH/RESTRICTED.
- `media_version BIGINT NOT NULL DEFAULT 0`: optimistic concurrency cho avatar/tiểu sử, trả `mediaVersion` cho client.
- `media_updated_at TIMESTAMPTZ NULL`, `media_updated_by`: định danh account theo schema identity hiện hành; chỉ lưu server-side.

`avatar_url` là đường dẫn API, không dùng để tìm object; `avatar_file_id` mới là nguồn tham chiếu tệp. Hai trường phải thay đổi trong cùng transaction. Chưa có dữ liệu US-32 thì null; không backfill ảnh giả.

### 6.2. `stored_files` — metadata kỹ thuật, không trả trực tiếp ra client

- `id UUID PRIMARY KEY`, `clan_id` đúng kiểu/khóa của bảng clans, `member_id VARCHAR(20)` FK members.
- `purpose`: AVATAR hoặc ATTACHMENT; `provider`: SUPABASE cho mọi file hoạt động (chỉ giữ LOCAL/DELETED nếu có tombstone lịch sử); `bucket`, `object_key` do server tạo; UNIQUE(provider, bucket, object_key).
- `original_filename`, `mime_type`, `size_bytes BIGINT`, `checksum_sha256`, `created_by`, `created_at`, `updated_at`.
- `state`: PENDING, READY, DELETE_PENDING, DELETED; `cleanup_after`, `delete_attempts`, `last_delete_error` đã loại bỏ dữ liệu nhạy cảm. DELETED là tombstone sau khi xác nhận object đã xóa, giữ metadata phục vụ tham chiếu/lịch sử nhưng không tính quota object.
- CHECK size_bytes > 0 khi READY; index `(state, cleanup_after)` phục vụ cleanup; index `(clan_id, member_id)`.

Tách metadata file để avatar cũ và upload dở đều có thể retry cleanup. Không cascade xóa metadata này khi xóa thành viên trước khi ghi nhận tác vụ xóa object. Nếu triển khai xóa clan/member, phải đưa file vào DELETE_PENDING trong transaction đó.

### 6.3. `member_attachments` — nghiệp vụ tư liệu

- `id UUID PRIMARY KEY`, `member_id VARCHAR(20) NOT NULL` FK members, `file_id UUID NOT NULL UNIQUE` FK stored_files.
- `title VARCHAR(150) NOT NULL`, `description VARCHAR(1000) NULL`, `visibility VARCHAR(20) NOT NULL DEFAULT 'RESTRICTED'` có CHECK.
- `version BIGINT NOT NULL DEFAULT 0`, `created_by`, `updated_by`, `created_at`, `updated_at`, `deleted_at NULL`.
- Index `(member_id, deleted_at, created_at, id)`; kiểm tra file thuộc đúng member/clan, purpose ATTACHMENT và READY trước khi công bố.

Avatar/attachment file không được chia sẻ chéo hồ sơ trong MVP. Tenant được xác nhận qua nhánh hiện tại của member và so khớp clan metadata; object path không thay cho kiểm tra quyền. Khi xóa mềm attachment, loại ngay khỏi danh sách/download và đánh dấu file DELETE_PENDING cùng transaction.

## 7. Hợp đồng API đề xuất

Mọi endpoint yêu cầu JWT và tenant hợp lệ. Role annotation chỉ là lớp kiểm tra đầu; service kiểm tra chính chủ, nhánh, visibility. Các request mutation dùng version bên dưới; thao tác ảnh không cập nhật tiểu sử ngoài ý muốn.

### 7.1. Đọc hồ sơ chi tiết

`GET /api/v1/members/{memberId}` → 200, kế thừa trường hồ sơ đã lọc quyền US-25 và bổ sung:

```json
{
  "success": true,
  "message": "Thao tác thành công",
  "data": {
    "memberId": "MEM001",
    "avatarUrl": "/api/v1/members/MEM001/avatar/content",
    "biography": {"ops": [{"insert": "Cuộc đời và sự nghiệp\n"}]},
    "biographyVisibility": "CLAN",
    "biographyAccess": "VISIBLE",
    "mediaVersion": 3,
    "capabilities": {"canEditMedia": true, "canUploadAttachment": true}
  },
  "timestamp": "2026-11-11T08:00:00Z"
}
```

Ví dụ trên chỉ trích phần mở rộng, không phải thay thế toàn bộ `MemberResponse`. Không nhúng toàn văn tiểu sử hoặc danh sách tư liệu vào API danh sách/cây/tìm kiếm; chỉ bổ sung avatarUrl khi cần. `capabilities` hỗ trợ UI, không phải chứng cứ cấp quyền cho request tiếp theo.

### 7.2. Avatar

- `PUT /api/v1/members/{memberId}/avatar`: multipart `file` và `expectedMediaVersion`; 200 trả `avatarUrl`, `mediaVersion` mới.
- `DELETE /api/v1/members/{memberId}/avatar?expectedMediaVersion=3`: 200 trả `avatarUrl: null`, version mới. Nếu đã không có ảnh và version khớp thì thành công, không tăng version.
- `GET /api/v1/members/{memberId}/avatar/content`: stream JPEG khi có quyền; không có ảnh trả 404. Không bọc bytes trong ApiResponse.

Client không gửi `avatarUrl` để backend tải ảnh từ URL ngoài. Flutter dùng Dio lấy bytes có Authorization rồi hiển thị ảnh; không gắn token vào query URL. Sau mutation, invalidate cache ảnh theo member và version để URL ổn định vẫn hiển thị ảnh mới.

### 7.3. Tiểu sử

`PUT /api/v1/members/{memberId}/biography` → 200 trả dữ liệu đã lưu và mediaVersion mới.

```json
{
  "expectedMediaVersion": 3,
  "biography": {"ops": [
    {"insert": "Nguyễn Văn A", "attributes": {"bold": true}},
    {"insert": "\n"},
    {"insert": "Tư liệu do gia đình cung cấp.\n"}
  ]},
  "visibility": "CLAN"
}
```

Delta là document hoàn chỉnh chỉ gồm `insert` string và attributes thuộc allowlist; không nhận retain/delete/embed hoặc attributes lạ. Heading/list/blockquote chỉ gắn trên newline, heading có giá trị 1–3; danh sách ordered/bullet, bold/italic/underline là boolean, link chỉ HTTPS hợp lệ. Backend chuẩn hóa document kết thúc bằng newline, kiểm tra giới hạn, từ chối cấu trúc lạ với 400. Xóa tiểu sử gửi `{"ops":[{"insert":"\n"}]}`; giữ visibility do client chọn. Không phát sinh HTML để render; nếu sau này xuất HTML phải escape và sanitize.

### 7.4. Tư liệu

- `POST /api/v1/members/{memberId}/attachments`: multipart `file`, `title`, `description` tùy chọn, `visibility` mặc định RESTRICTED. Có `Idempotency-Key` UUID để retry upload không nhân đôi; 201 kèm Location trỏ endpoint metadata.
- `GET /api/v1/members/{memberId}/attachments?page=0&size=10`: trả `data.items`, `page`, `size`, `totalElements`, `totalPages`; size 1–20, chỉ đếm tài liệu được xem. Sắp xếp `createdAt DESC, id DESC`.
- `GET /api/v1/members/{memberId}/attachments/{attachmentId}`: metadata của một tư liệu được phép xem.
- `PATCH /api/v1/members/{memberId}/attachments/{attachmentId}`: JSON chứa `expectedVersion` và ít nhất một trong `title`, `description`, `visibility`; trường vắng mặt giữ nguyên, description null là xóa mô tả. 200 trả metadata/version mới; title/visibility không được null.
- `GET /api/v1/members/{memberId}/attachments/{attachmentId}/content?disposition=attachment`: truyền bytes sau kiểm tra quyền. Mặc định attachment; chỉ cho inline với ảnh đã chuẩn hóa, PDF tải xuống trong MVP.
- `DELETE /api/v1/members/{memberId}/attachments/{attachmentId}?expectedVersion=0`: 204 sau khi metadata bị ẩn và tác vụ xóa file được ghi bền vững; không hứa object đã bị xóa vật lý ngay.

Metadata trả `id`, `memberId`, `title`, `description`, `originalFilename`, `mimeType`, `sizeBytes`, `visibility`, `version`, `createdAt`, `updatedAt`, `contentUrl`, `canEdit`, `canDelete`. Không trả bucket/key, credential, signed URL hoặc định danh account người tải lên trong danh sách thông thường.

Idempotency key được lưu bền vững theo `(callerAccountId, memberId, key)` với hash request và trạng thái; retry cùng payload trong 24h trả lại kết quả đã lưu, khác payload trả 409, request cùng key đang xử lý trả 409 `UPLOAD_IN_PROGRESS`. Phải kiểm tra lại quyền hiện tại trước replay. File giới hạn 20 tính cả upload đang giữ chỗ.

### 7.5. Lỗi và headers

- 400: `INVALID_MEDIA_FILE`, `INVALID_BIOGRAPHY`, `INVALID_VISIBILITY` hoặc input không hợp lệ.
- 401: chưa đăng nhập/token hết hạn. 403 `MEMBER_MEDIA_FORBIDDEN`: đã có quyền thấy hồ sơ nhưng không được sửa.
- 404: hồ sơ ngoài tenant, attachment không thuộc member, file không tồn tại hoặc tư liệu người gọi không được biết đến.
- 409: `MEDIA_VERSION_CONFLICT`, `ATTACHMENT_LIMIT_REACHED`, `IDEMPOTENCY_CONFLICT`, `UPLOAD_IN_PROGRESS`.
- 413 `FILE_TOO_LARGE`: vượt giới hạn file/request. 415 `UNSUPPORTED_MEDIA_TYPE`: loại file không hỗ trợ.
- 429 `UPLOAD_RATE_LIMITED`: quá giới hạn request; 503 `STORAGE_UNAVAILABLE` hoặc `STORAGE_QUOTA_EXCEEDED`: provider lỗi/hết quota. Không trả thông tin tài khoản cloud.

JSON lỗi theo `ApiResponse` có `success: false`, `code`, `message` tiếng Việt và timestamp. Proxy limit/multipart limit cần thống nhất để không trả trang HTML khó xử lý. Giới hạn multipart request đề xuất 12 MiB, file tối đa 10 MiB; kiểm tra avatar riêng 5 MiB.

Nội dung file dùng MIME xác thực, `X-Content-Type-Options: nosniff`, `Cache-Control: private, no-store` và Content-Disposition với filename đã làm sạch. Endpoint có xác thực không redirect sang public object URL. URL có chữ ký là phương án mở rộng, phải chấp nhận người giữ link truy cập tới khi hết hạn; chưa dùng trong MVP để giữ kiểm tra quyền theo request.

## 8. Luồng upload, đồng thời và dọn file

```mermaid
sequenceDiagram
    actor User as Người dùng
    participant FE as Flutter
    participant BE as MemberMediaService
    participant DB as PostgreSQL
    participant FS as FileStorage
    User->>FE: Chọn/crop ảnh hoặc chọn tư liệu
    FE->>BE: Multipart + JWT + version/idempotency key
    BE->>BE: Xác thực tenant, quyền, loại và giới hạn file
    BE->>DB: Transaction ngắn: giữ quota/slot, ghi file PENDING
    BE->>FS: Upload bằng object key ngẫu nhiên
    alt Upload thành công
        BE->>DB: Khóa hồ sơ, kiểm tra lại quyền/version, READY + gắn tham chiếu
        DB-->>BE: Commit; đánh dấu file cũ DELETE_PENDING nếu có
        BE-->>FE: 200/201 + metadata mới
    else Upload thất bại
        BE->>DB: Đánh dấu chờ cleanup, giữ quota tới khi xác nhận không còn object
        BE-->>FE: Lỗi rõ ràng, dữ liệu hiện hành giữ nguyên
    end
```

- Không giữ transaction DB trong thời gian chờ network. PENDING được ghi trước upload; worker nhận diện upload bỏ dở kể cả process bị tắt. Dùng lease/timeout upload; chỉ dọn PENDING quá 24h và không còn lease hoạt động.
- Object key theo `{clanId}/{memberId}/{uuid}.{validatedExtension}`, tên server sinh. Không overwrite avatar cũ; mỗi lần thay sinh key mới.
- Transaction cuối khóa member để kiểm tra số lượng, version, scope hiện tại và cập nhật liên kết. Version không khớp hoặc mất quyền sau upload thì file mới chuyển DELETE_PENDING; ảnh/tiểu sử đang dùng không thay đổi.
- Worker chạy định kỳ, xóa file DELETE_PENDING, retry có backoff. Chỉ giải phóng quota object sau khi đã xác nhận object xóa/không tồn tại. Không xóa file READY đang được hồ sơ tham chiếu.
- Khi provider timeout không biết upload đã thành công hay chưa, vẫn giữ bản ghi PENDING và cleanup theo key; không giải phóng quota sớm. Thành công nhưng response bị mất: attachment retry qua idempotency; avatar/biography tải lại version trước khi lưu tiếp.
- Audit chỉ lưu loại thao tác, actor, member/attachment, visibility, thời điểm và kết quả. Không log nội dung tiểu sử, bytes, credential, URL bí mật hay thông tin liên hệ.

## 9. Yêu cầu frontend

### 9.1. Trang chi tiết thành viên

Header có avatar, tên và thông tin hồ sơ hiện có; bên dưới có phần **Tiểu sử** và **Tư liệu gia sử**. Web màn hình rộng có thể đặt avatar/tóm tắt cạnh nội dung; mobile bố cục dọc. Kế thừa theme và phân quyền từ hệ thống, không build ứng dụng desktop Windows/macOS/Linux.

Trang chỉ đọc vẫn hiển thị nội dung được phép xem; nút Thay ảnh/Viết tiểu sử/Thêm tư liệu chỉ hiện theo capabilities. Không có quyền xem tiểu sử thì hiển thị “Nội dung này bị giới hạn quyền xem”; danh sách không có tài liệu được xem hiển thị “Chưa có tư liệu để hiển thị”, không khẳng định không tồn tại tư liệu ẩn.

### 9.2. Chân dung

Chọn từ thiết bị → kiểm tra sơ bộ → crop vuông có zoom/di chuyển → xem trước → Lưu. Crop tròn chỉ là khung xem trước; file lưu là hình vuông. Có Hủy, Thay ảnh và Xóa ảnh (xác nhận trước khi xóa). Không tự lưu khi vừa chọn file; chỉ cập nhật UI thành công khi API trả kết quả. Chuẩn hóa orientation trước crop để ảnh iPhone không xoay sai.

### 9.3. Rich Text

Toolbar tối giản theo allowlist, bộ đếm ký tự, bộ chọn quyền xem, Lưu/Hủy và cảnh báo rời trang khi có thay đổi. Paste nội dung chỉ giữ định dạng được hỗ trợ; không có nút thêm ảnh/video. Có chế độ xem trước. Xóa trắng nội dung phải xác nhận nếu tiểu sử hiện tại không rỗng. Không autosave trong MVP.

### 9.4. Tư liệu

Danh sách hiển thị tên, loại, dung lượng, ngày thêm, nhãn quyền xem. Form upload có tiêu đề, mô tả, phạm vi chia sẻ, tên file, tiến trình và Hủy trước khi gửi. Chọn nhiều tệp thì từng tệp có form/metadata riêng và upload tuần tự, thông báo kết quả riêng; không coi cả batch là một transaction. Retry một upload đang xử lý dùng lại idempotency key.

Ảnh có preview bằng bytes qua Dio; PDF có nút tải xuống/mở bằng ứng dụng thiết bị. Có sửa metadata và xóa với xác nhận. Sau 403/404, bỏ nội dung đang giữ trong state và tải lại capabilities; sau 409 thông báo “Hồ sơ đã thay đổi, vui lòng tải lại trước khi lưu” và giữ bản nháp để người dùng sao chép.

### 9.5. Tích hợp Flutter

Tuân thủ `Screen → Notifier → Repository interface → RepositoryImpl → RemoteDataSource → Dio`; không gọi Dio trực tiếp từ widget. Dùng enum cho visibility/capability, theme token, thông báo tiếng Việt và quản lý lỗi hiện có. Dữ liệu private chỉ giữ trong memory, xóa khi logout/đổi tenant; không cache ra shared_preferences hoặc service worker.

Cần đánh giá package chọn file/crop/Quill tương thích Flutter Web/Android/iOS tại thời điểm triển khai và schema Delta đã chốt; tài liệu này không thêm dependency hay chốt phiên bản. Theo AGENTS frontend, thêm dependency cần hỏi và sửa `lib/` cho UI cần qua `/story-ui` và duyệt thiết kế. US-32 backend đã thêm PDFBox để kiểm tra PDF; chưa sửa mã ứng dụng hoặc dependency frontend.

## 10. Kiểm tra file và vận hành

- Đối chiếu extension, MIME khai báo, magic bytes và khả năng decode/parse; không tin Content-Type do client gửi. Reject SVG, HTML, executable, file đa định dạng đáng ngờ và PDF mã hóa không kiểm tra được.
- Với PDF, dùng parser có giới hạn tài nguyên để kiểm tra cấu trúc và từ chối JavaScript, embedded files, launch actions. Đây không thay thế antivirus; nếu mở upload từ Internet thực tế cần quét mã độc/quarantine trước READY. Demo dùng dữ liệu tự chuẩn bị, không quảng cáo đã quét antivirus nếu chưa tích hợp.
- Giới hạn upload mặc định đề xuất 10 lần bắt đầu/phút/account, tối đa 2 upload đồng thời/account; xử lý ảnh tối đa 2 job đồng thời/instance, có queue hữu hạn để tránh cạn RAM. Có thể cấu hình lại khi đo tải thực tế.
- Local root nằm ngoài thư mục static/webroot, normalize đường dẫn và chặn traversal/symlink thoát root. Chỉ nhận ID tài nguyên qua API; không nhận đường dẫn filesystem từ người dùng.
- Mọi môi trường chạy cần bucket Supabase private và cấu hình môi trường riêng. Có timeout hữu hạn cho kết nối/upload/download; hủy stream khi client ngắt.
- Trước demo kiểm tra project còn hoạt động, quota, credential và truy cập một ảnh/PDF mẫu. Backup cả metadata PostgreSQL lẫn object storage; backup database không chứa bytes của tư liệu.

## 11. Kế hoạch triển khai và kiểm thử

### 11.1. Thứ tự triển khai

1. Chốt các đề xuất sản phẩm: chính chủ/người quản lý được sửa, 3 mức chia sẻ, Delta, giới hạn và Supabase; bổ sung thiết kế UI theo workflow frontend.
2. Thêm migration/entity/policy, reservation quota và optimistic concurrency; bảo toàn dữ liệu hồ sơ cũ.
3. Xây FileStorage với adapter Supabase; chuẩn hóa ảnh, validator Delta/PDF, cleanup worker và audit.
4. Thêm endpoint chi tiết và media APIs, cập nhật OpenAPI; kiểm thử tenant và quyền trước tích hợp UI.
5. Tích hợp UI avatar, tiểu sử, tư liệu; demo xuyên suốt trên Web và thiết bị mobile.

### 11.2. Phạm vi kiểm thử

- **Policy unit:** chính chủ/người lạ, trưởng nhánh đúng/sai/nhánh con, trưởng tộc, admin tenant, BTC; mỗi mức visibility; hồ sơ ảo/đã mất, tài khoản không Active, đổi nhánh/thu hồi role.
- **Validation unit:** ảnh sai MIME, hỏng, quá kích thước giải mã, EXIF orientation/GPS; PDF có embedded action; Delta chứa script/link nguy hiểm/ops không hợp lệ; biên ký tự và byte.
- **Controller/MockMvc:** multipart đúng/sai, JSON envelope thật, version conflict, headers download, 401/403/404/413/415, chống lộ attachment ngoài tenant và tài liệu ẩn trong count/page.
- **Integration PostgreSQL + storage adapter:** thay avatar lỗi giữ ảnh cũ, DB commit lỗi sau upload, timeout không rõ kết quả, retry cleanup, hai upload đồng thời ở slot cuối, quota reservation, idempotency replay, delete ẩn ngay dù cloud tạm lỗi.
- **Architecture:** chạy `ArchitectureTest`, không rò rỉ module internal hoặc tạo vòng dependency.
- **Frontend:** crop hủy/lưu, định dạng round-trip Delta, đổi tenant/logout xóa state, lỗi upload giữ draft, 409 tải lại, danh sách chỉ đọc; Web ở ~390/~840/~1280 px, Android và iOS. Kiểm tra API không bị cache ảnh cũ sau thay.

Các lệnh kiểm thử, chạy từ repo tương ứng (frontend chỉ chạy sau khi có triển khai UI):

```powershell
# Backend: tên package/lớp cụ thể bổ sung khi có code
.\gradlew.bat test --tests "com.familyfund.genealogy.*" --tests "com.familyfund.core.storage.*" --tests "com.familyfund.ArchitectureTest"
# Frontend
flutter analyze
flutter test
```

### 11.3. Smoke test API (hợp đồng đề xuất)

Biến TOKEN và MEMBER_ID do người kiểm thử cung cấp, không commit token. Chạy từ PowerShell, thay đường dẫn file mẫu phù hợp; version phải lấy từ GET detail mới nhất.

```powershell
curl.exe "http://localhost:8080/api/v1/members/$env:MEMBER_ID" -H "Authorization: Bearer $env:TOKEN"
curl.exe -X PUT "http://localhost:8080/api/v1/members/$env:MEMBER_ID/avatar" -H "Authorization: Bearer $env:TOKEN" -F "file=@portrait-cropped.jpg" -F "expectedMediaVersion=0"
curl.exe -X POST "http://localhost:8080/api/v1/members/$env:MEMBER_ID/attachments" -H "Authorization: Bearer $env:TOKEN" -H "Idempotency-Key: 6e59160d-bd9f-47a0-aa4d-9c77e62cfaba" -F "file=@family-history.pdf" -F "title=Gia su" -F "visibility=RESTRICTED"
curl.exe "http://localhost:8080/api/v1/members/$env:MEMBER_ID/attachments?page=0&size=10" -H "Authorization: Bearer $env:TOKEN"
```

Sau đó đăng nhập người cùng clan không có quyền quản lý: RESTRICTED không xuất hiện; đoán content URL trả 404. Đổi tư liệu sang CLAN bằng tài khoản có quyền: người đó thấy và tải được. Dùng tài khoản clan khác kiểm tra cả metadata và bytes đều bị chặn. Dừng storage rồi thử thay avatar: trả lỗi và avatar cũ vẫn được tham chiếu.

### 11.4. Definition of Done

- Đạt AC-01 đến AC-12, OpenAPI và implementation khớp hợp đồng đã chốt.
- Migration chạy trên dữ liệu cũ; Supabase dùng policy thống nhất cho metadata và bytes, không có public object truy cập bỏ qua backend.
- Kiểm thử quyền/đồng thời/lỗi lưu trữ đạt; frontend analyze/test đạt; xác minh thực tế Web, Android, iOS.
- Có hướng dẫn cấu hình môi trường, hạn mức, backup và cleanup; không commit secret hoặc tư liệu cá nhân thật.
- Backend chỉ được xem là đã kiểm chứng cloud sau smoke test trên Supabase thật. Toàn bộ US-32 chỉ hoàn tất khi frontend và kiểm thử đa nền tảng cũng đạt.

## 12. Kết quả triển khai backend

- Migration V19 tạo mô hình media; [`V20__require_supabase_for_active_member_media.sql`](../../src/main/resources/db/migration/V20__require_supabase_for_active_member_media.sql) loại bỏ LOCAL cho file hoạt động, giữ nguyên lịch sử migration đã áp dụng.
- API, policy, repository JDBC và worker: [`genealogy/internal/media/`](../../src/main/java/com/familyfund/genealogy/internal/media/). Các ghi media dùng transaction Spring và khóa PostgreSQL; entity Member chỉ đọc cột avatar_url để các cập nhật hồ sơ cũ không ghi đè giá trị media.
- Lưu trữ: [`core/storage/`](../../src/main/java/com/familyfund/core/storage/), chỉ còn adapter Supabase; đã gỡ LocalStorageAdapter và các biến cấu hình local. V20 chặn LOCAL cho mọi file hoạt động và từ chối migration nếu có dữ liệu local chưa được di chuyển.
- Kiểm tra tài khoản/role hiện hành qua port [`AccountAccess`](../../src/main/java/com/familyfund/core/security/AccountAccess.java), adapter trong identity; không tạo phụ thuộc vòng genealogy → identity. Tenant được đối chiếu thêm với clan thực tế của account, không chỉ tin header/JWT.
- JSON lỗi và multipart, optimistic concurrency, quota/slot reservation, idempotency key, cleanup lease/retry, audit metadata đã được triển khai. Lần upload lỗi hoặc conflict sau khi lưu object sẽ đưa file mới vào DELETE_PENDING, không thay ảnh hiện hành.
- Hợp đồng: [`OpenAPI`](../../docker/swagger/openapi.yaml). Hướng dẫn cấu hình và smoke test: [`storage-us32.md`](../storage-us32.md).
- Kiểm thử thông thường dùng PostgreSQL với storage giả lập trong bộ nhớ; contract test adapter có 7 kịch bản, gồm secret key mới và từ chối publishable key. Hai kiểm thử cloud opt-in `SupabaseStorageLiveTest` và `SupabaseMemberMediaLiveTest` đã chạy riêng và đều đạt; không chạy ngầm trong suite mặc định.
- `./gradlew build` bản Supabase-only thành công: **808 kiểm thử đạt, 0 lỗi, 2 cloud tests được bỏ qua có chủ đích** trong tổng 810 test. Hai cloud tests đã đạt ở lượt bật `SUPABASE_LIVE_TEST=true` riêng. JAR mới được kiểm tra có SupabaseStorageAdapter và không còn LocalStorageAdapter.
- Đã cấu hình Supabase thật: bucket private, upload/download PNG/PDF đúng checksum, public URL bị chặn, file mẫu được xóa và xác nhận không còn. Adapter Java cũng đạt round-trip JPEG thật; preflight database không có LOCAL đang hoạt động. Chưa triển khai frontend hoặc antivirus; album ảnh vẫn ngoài MVP.

Lưu ý vận hành: key upload đã hoàn thành chỉ replay trong 24h; key FAILED/hết hạn không tái sử dụng. Cloud download MVP buffer file tối đa 10 MiB để timeout bao phủ toàn bộ tải nội dung; mở rộng album/file lớn cần thiết kế lại phân phối ảnh, không chỉ tăng giới hạn file.
