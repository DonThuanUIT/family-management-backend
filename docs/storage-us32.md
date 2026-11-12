# Vận hành lưu trữ US-32

Backend chỉ dùng Supabase Storage cho ảnh và tài liệu. Adapter filesystem và cấu hình chọn local đã được loại bỏ sau khi kiểm chứng cloud thành công. Database, JWT và phân quyền vẫn do Spring Boot quản lý.

## 1. Cấu hình bắt buộc

```dotenv
SUPABASE_URL=https://YOUR_PROJECT.supabase.co
SUPABASE_SERVICE_ROLE_KEY=YOUR_SERVER_SECRET_KEY
APP_STORAGE_BUCKET=member-media
APP_STORAGE_QUOTA_BYTES=524288000
APP_STORAGE_MAX_ATTACHMENTS=20
APP_STORAGE_CLEANUP_ENABLED=true
```

Cấu hình này áp dụng cả khi chạy Spring Boot trên máy cá nhân. Không còn `APP_STORAGE_PROVIDER`, `APP_STORAGE_LOCAL_ROOT` hoặc chế độ fallback ghi file xuống ổ đĩa. Thiếu Project URL/key hoặc dùng publishable key sẽ làm cấu hình backend bị từ chối.

## 2. Demo online với Supabase

1. Tạo một Supabase project và bucket **member-media**, đặt **Private**. Không tạo policy anonymous/public cho bucket. Đặt giới hạn bucket 10 MiB và allowlist `image/jpeg`, `image/png`, `application/pdf`.
2. Lấy Project URL và **secret key `sb_secret_...`** dành cho server tại **Settings → API Keys → Secret keys**. Điền secret key vào biến `SUPABASE_SERVICE_ROLE_KEY` bên dưới (giữ tên biến để tương thích cấu hình hiện tại). Adapter gửi secret key qua `apikey`, không coi nó là JWT. Legacy `service_role` JWT vẫn được hỗ trợ qua cả `apikey` và `Authorization: Bearer`. Không dùng publishable/anon key.
3. Đặt biến môi trường hoặc điền `.env` local (file này không commit):

```dotenv
SUPABASE_URL=https://YOUR_PROJECT.supabase.co
SUPABASE_SERVICE_ROLE_KEY=YOUR_SERVER_SECRET_KEY
APP_STORAGE_BUCKET=member-media
APP_STORAGE_QUOTA_BYTES=524288000
APP_STORAGE_MAX_ATTACHMENTS=20
APP_STORAGE_CLEANUP_ENABLED=true
```

4. Chạy backend bằng `./gradlew bootRun` hoặc `java -jar` với cùng các biến môi trường. Flyway áp dụng V19 và V20; không sửa migration cũ. V20 chặn triển khai nếu vẫn có object LOCAL chưa xóa, tránh làm mất khả năng truy cập dữ liệu cũ. Gradle đã có logic nạp `.env` cho bootRun/test; `java -jar` cần truyền biến môi trường từ môi trường triển khai.
5. Đăng nhập tài khoản Active gắn thành viên và clan; gọi GET detail lấy `mediaVersion` rồi upload avatar/tư liệu qua API. Không gửi Supabase key cho client.
6. Kiểm tra bytes tồn tại trong bucket và download qua API được; thử public object URL không có quyền phải bị chặn. Đăng nhập người không được xem tư liệu RESTRICTED: metadata và content đều trả 404.
7. Xóa tư liệu; API trả 204, không đọc được ngay. Worker định kỳ xóa object, giữ metadata tombstone `DELETED` để bảo toàn tham chiếu/lịch sử retry. Chỉ sau xác nhận xóa mới giải phóng quota object.

Đã kiểm chứng cloud: tạo bucket private, upload/download PNG và PDF khớp checksum, chặn public URL, xóa và xác nhận object không còn. Adapter Java cũng đã upload/download/xóa JPEG thật thành công; kiểm tra database xác nhận không có file LOCAL đang hoạt động trước khi gỡ adapter.

Kiểm thử API qua Spring Boot với Supabase thật cũng đã đạt: upload/xem avatar, upload/tải PDF đúng bytes, xóa qua API và chặn đọc lại sau xóa. Hồ sơ, audit và object mẫu của lượt kiểm thử được dọn riêng theo clan UUID; không quét/xóa dữ liệu khác trong bucket.

## 3. API chính

- `GET /api/v1/members/{memberId}`: hồ sơ đã áp dụng US-25, avatar, tiểu sử theo quyền và capabilities.
- `PUT /api/v1/members/{memberId}/avatar`: multipart `file`, `expectedMediaVersion`.
- `DELETE /api/v1/members/{memberId}/avatar?expectedMediaVersion=...`.
- `GET /api/v1/members/{memberId}/avatar/content`: bytes có xác thực.
- `PUT /api/v1/members/{memberId}/biography`: JSON `expectedMediaVersion`, `biography` Quill Delta, `visibility`.
- `POST /api/v1/members/{memberId}/attachments`: multipart `file`, `title`, `description` tùy chọn, `visibility` mặc định RESTRICTED; header `Idempotency-Key` UUID bắt buộc.
- `GET /api/v1/members/{memberId}/attachments?page=0&size=10`.
- `GET/PATCH/DELETE /api/v1/members/{memberId}/attachments/{id}`; PATCH có expectedVersion, DELETE có query expectedVersion.
- `GET /api/v1/members/{memberId}/attachments/{id}/content`: mặc định tải xuống; `disposition=inline` chỉ có tác dụng với ảnh.

Hợp đồng máy đọc: [`docker/swagger/openapi.yaml`](../docker/swagger/openapi.yaml). Quy tắc nghiệp vụ: [đặc tả US-32](./user-stories/US-32-member-portrait-biography-and-family-documents.md).

Mọi endpoint cần Bearer JWT của ứng dụng. Tài khoản và roles được đọc lại từ database; tenant yêu cầu phải khớp clan thực tế của tài khoản. AvatarUrl là đường dẫn API, không chứa token. Flutter phải tải bytes bằng Authorization header.

Idempotency replay thành công trong 24h chỉ hợp lệ khi payload khớp và người gọi còn quyền; không tái sử dụng key cho lần upload mới. Khi một lần tải đã FAILED hoặc hết hạn, dùng key mới. Khi đang xử lý, giữ key và retry sau khi nhận `UPLOAD_IN_PROGRESS`. Sau 409 phiên bản, đọc lại hồ sơ/metadata trước khi ghi tiếp.

## 4. Quota và dọn file

- 500 MiB là quota **toàn ứng dụng** mặc định, không phải giới hạn Supabase. Tính cả READY, PENDING, DELETE_PENDING; không tính DELETED.
- Tối đa 20 tư liệu/hồ sơ và 2 upload PENDING/account; 10 lần bắt đầu/phút/account. Kiểm tra reservation dùng khóa PostgreSQL chung nên có hiệu lực giữa nhiều backend instance.
- Worker mặc định bắt đầu sau 60 giây, chạy mỗi 60 giây, tối đa 20 object/lượt. PENDING bỏ dở chỉ dọn sau 24h; DELETE_PENDING dọn khi đến hạn. Claim có lease 5 phút và retry backoff tối đa 1 giờ.
- Theo dõi lỗi qua `stored_files.last_delete_error`, `delete_attempts`, `cleanup_after`; không xóa thủ công metadata để “giải phóng quota” khi object vẫn còn trên cloud.
- Không có fallback local khi Supabase lỗi. Mọi file hoạt động phải có provider SUPABASE; chỉ cho phép giữ tombstone LOCAL/DELETED để bảo toàn lịch sử. Đổi bucket vẫn cần migration object riêng, không chỉ đổi biến môi trường.
- Backup cả PostgreSQL và object. Snapshot database không chứa bytes ảnh/PDF. Không mở public bucket để xử lý lỗi download.

## 5. Kiểm chứng và giới hạn

Kiểm thử thông thường dùng fixture PostgreSQL cô lập và storage giả lập bằng bộ nhớ, không ghi filesystem hoặc gọi cloud. Cấu hình test dùng URL/key giả. Adapter Supabase có contract test HTTP giả lập; kiểm thử cloud opt-in đọc cấu hình thật từ môi trường Gradle và chỉ tạo/xóa object mẫu của lượt chạy đó.

Kết quả ngày 12/11/2026: build thành công với **808 test đạt, không lỗi**; 2 bài cloud được bỏ qua trong suite mặc định và đã chạy riêng thành công. JAR đã được kiểm tra không chứa adapter local.

```powershell
.\gradlew.bat test --tests "*MemberMediaIntegrationTest" --tests "*MemberMediaOpenApiTest" --tests "*MemberMediaErrorTest" --tests "*BiographyValidatorTest" --tests "*MediaFileValidatorTest" --tests "*SupabaseStorageAdapterTest" --tests "com.familyfund.ArchitectureTest.verifyModularity"

# Chỉ bật khi chủ động kiểm chứng Supabase thật bằng .env đã cấu hình:
$env:SUPABASE_LIVE_TEST='true'
.\gradlew.bat test --tests "*SupabaseStorageLiveTest" --tests "*SupabaseMemberMediaLiveTest"
Remove-Item Env:SUPABASE_LIVE_TEST
```

PDF được parse và từ chối nội dung chủ động/nhúng, nhưng chưa tích hợp antivirus. Ảnh được decode/re-encode để loại metadata; avatar JPEG 512 px, ảnh tư liệu giữ độ phân giải. Crop trực quan thuộc frontend, API chỉ chấp nhận avatar vuông.

MVP giới hạn file 10 MiB; adapter cloud buffer nội dung để áp dụng timeout cho toàn bộ download trước khi trả response. Khi làm album/ảnh lớn cần thiết kế phân phối ảnh và streaming/resumable riêng, không tăng giới hạn vô điều kiện.
