package com.familyfund.genealogy.internal.media;

import com.familyfund.core.audit.model.AuditAction;
import com.familyfund.core.audit.model.AuditEntityType;
import com.familyfund.core.audit.writer.AuditLogWriter;
import com.familyfund.core.storage.*;
import com.familyfund.genealogy.internal.service.MemberService;
import com.familyfund.genealogy.internal.security.MemberPrivacyPolicy;
import com.familyfund.genealogy.internal.security.PrivacyEvaluationContext;
import com.familyfund.genealogy.internal.repository.MemberPrivacySettingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import java.io.InputStream;
import java.util.*;
import java.util.function.Supplier;
import static com.familyfund.genealogy.internal.media.MemberMediaRepository.*;

@Service
@RequiredArgsConstructor
public class MemberMediaService {
    private final MemberMediaRepository repository;
    private final MemberMediaPolicy policy;
    private final MemberService members;
    private final MediaFileValidator validator;
    private final BiographyValidator biographies;
    private final StorageProperties properties;
    private final StorageRegistry storage;
    private final PlatformTransactionManager transactions;
    private final ObjectMapper mapper;
    private final AuditLogWriter audit;
    private final MemberPrivacyPolicy privacyPolicy;
    private final MemberPrivacySettingRepository privacySettings;

    private <T> T tx(Supplier<T> work) { return new TransactionTemplate(transactions).execute(status -> work.get()); }
    private MemberMediaPolicy.Access edit(String memberId) {
        var access = policy.access(memberId); access.requireEdit(); return access;
    }
    private static void version(Map<String, Object> row, String field, long expected) {
        if (expected < 0 || number(row, field) != expected) throw new MediaException(409, "MEDIA_VERSION_CONFLICT", "Hồ sơ đã thay đổi, vui lòng tải lại trước khi lưu");
    }
    private void audit(MemberMediaPolicy.Access access, AuditAction action, String operation, String visibility) {
        audit.writeLog(access.caller().clanId(), access.caller().accountId(), action, AuditEntityType.MEMBER,
                access.member().getMemberId(), access.member().getMemberId(), null,
                Map.of("operation", operation, "visibility", visibility), null, "Cập nhật tư liệu hồ sơ thành viên");
    }
    public Map<String, Object> detail(String memberId) {
        return tx(() -> {
            var access = policy.access(memberId);
            // Existing member service applies US-25 privacy before serialization.
            var profile = privacyPolicy.applyPrivacy(members.getMember(memberId), privacySettings.findById(memberId).orElse(null),
                    new PrivacyEvaluationContext(access.caller().memberId(), access.manager(), Set.of(), false));
            Map<String, Object> result = mapper.convertValue(profile, new tools.jackson.core.type.TypeReference<LinkedHashMap<String,Object>>() {});
            var media = repository.member(memberId, false);
            boolean visible = access.canView(string(media, "biography_visibility"));
            result.put("biography", visible && media.get("biography") != null ? mapper.readTree(string(media, "biography")) : null);
            result.put("biographyVisibility", visible ? media.get("biography_visibility") : "RESTRICTED");
            result.put("biographyAccess", visible ? "VISIBLE" : "RESTRICTED");
            result.put("mediaVersion", media.get("media_version"));
            result.put("avatarUrl", media.get("avatar_url"));
            result.put("capabilities", Map.of("canEditMedia", access.canEdit(), "canUploadAttachment", access.canEdit()));
            return result;
        });
    }
    public Map<String,Object> biography(String memberId, long expected, JsonNode biography, String visibility) {
        return tx(() -> {
            repository.lockMember(memberId);
            var access = edit(memberId);
            var current = repository.member(memberId, false);
            version(current, "media_version", expected);
            JsonNode document = biographies.validate(biography);
            var scope = MediaVisibility.parse(visibility);
            repository.update("UPDATE members SET biography=?::jsonb,biography_visibility=?,media_version=media_version+1,media_updated_at=now(),media_updated_by=? WHERE member_id=?",
                    document.toString(), scope.name(), access.caller().accountId(), memberId);
            audit(access, AuditAction.UPDATE, "BIOGRAPHY_UPDATED", scope.name());
            return Map.of("biography", document, "biographyVisibility", scope.name(), "mediaVersion", expected + 1);
        });
    }
    public Map<String,Object> avatar(String memberId, long expected, MultipartFile file) {
        tx(() -> { edit(memberId); return null; });
        var validated = validator.validate(file, true);
        Reservation reserved = reserve(memberId, validated, true, expected, UUID.randomUUID(), "", "", "CLAN");
        upload(reserved, validated);
        return finish(reserved, () -> {
            repository.lockMember(memberId);
            var access = edit(memberId);
            var current = repository.member(memberId, false);
            version(current, "media_version", expected);
            ready(reserved.fileId());
            String url = "/api/v1/members/" + memberId + "/avatar/content";
            repository.update("UPDATE members SET avatar_file_id=?,avatar_url=?,media_version=media_version+1,media_updated_at=now(),media_updated_by=? WHERE member_id=?",
                    reserved.fileId(), url, access.caller().accountId(), memberId);
            markDelete(uuid(current, "avatar_file_id"));
            complete(reserved, null);
            audit(access, AuditAction.UPDATE, "AVATAR_UPDATED", "CLAN");
            return Map.<String,Object>of("avatarUrl", url, "mediaVersion", expected + 1);
        });
    }
    public Map<String,Object> deleteAvatar(String memberId, long expected) {
        return tx(() -> {
            repository.lockMember(memberId);
            var access = edit(memberId);
            var current = repository.member(memberId, false);
            version(current, "media_version", expected);
            UUID old = uuid(current, "avatar_file_id");
            if (old != null) {
                repository.update("UPDATE members SET avatar_file_id=NULL,avatar_url=NULL,media_version=media_version+1,media_updated_at=now(),media_updated_by=? WHERE member_id=?", access.caller().accountId(), memberId);
                markDelete(old);
                audit(access, AuditAction.DELETE, "AVATAR_DELETED", "CLAN");
            }
            Map<String,Object> response = new LinkedHashMap<>();
            response.put("avatarUrl", null); response.put("mediaVersion", old == null ? expected : expected + 1);
            return response;
        });
    }
    public record Download(InputStream stream, String filename, String mimeType, long size) {}
    public Download avatarContent(String memberId) {
        Map<String,Object> file = tx(() -> {
            policy.access(memberId);
            var row = repository.one("SELECT f.* FROM stored_files f JOIN members m ON m.avatar_file_id=f.id WHERE m.member_id=? AND f.state='READY' AND f.member_id=m.member_id", memberId);
            if (row == null) throw MediaException.missing();
            return row;
        });
        return download(file, "portrait.jpg");
    }
    public Map<String,Object> uploadAttachment(String memberId, MultipartFile file, String title, String description, String visibility, UUID key) {
        tx(() -> { edit(memberId); return null; });
        title = text(title, 150, true); description = text(description, 1000, false);
        visibility = MediaVisibility.parse(visibility).name();
        var validated = validator.validate(file, false);
        Reservation reserved = reserve(memberId, validated, false, 0, key, title, description, visibility);
        if (reserved.replayId() != null) return attachment(memberId, reserved.replayId());
        upload(reserved, validated);
        String finalTitle = title, finalDescription = description, finalVisibility = visibility;
        UUID attachmentId = UUID.randomUUID();
        finish(reserved, () -> {
            repository.lockMember(memberId);
            var access = edit(memberId);
            ready(reserved.fileId());
            repository.update("INSERT INTO member_attachments (id,member_id,file_id,title,description,visibility,created_by,updated_by) VALUES (?,?,?,?,?,?,?,?)",
                    attachmentId, memberId, reserved.fileId(), finalTitle, finalDescription, finalVisibility, access.caller().accountId(), access.caller().accountId());
            complete(reserved, attachmentId);
            audit(access, AuditAction.CREATE, "ATTACHMENT_CREATED", finalVisibility);
            return null;
        });
        return attachment(memberId, attachmentId);
    }
    private record Reservation(UUID requestId, UUID fileId, String provider, String bucket, String key, UUID replayId) {}
    private Reservation reserve(String memberId, MediaFileValidator.Validated file, boolean avatar, long expected,
                                UUID key, String title, String description, String visibility) {
        return tx(() -> {
            // All upload reservations acquire locks in this order; no network I/O in this transaction.
            repository.lockQuota(); repository.lockMember(memberId);
            var access = edit(memberId);
            String caller = access.caller().accountId();
            String hash = MediaFileValidator.sha256(mapper.writeValueAsBytes(List.of(file.checksum(), file.originalFilename(), title,
                    description == null ? "" : description, visibility)));
            var previous = repository.one("SELECT * FROM media_upload_requests WHERE caller_account_id=? AND member_id=? AND idempotency_key=?", caller, memberId, key);
            if (previous != null) {
                if (!hash.equals(string(previous, "request_hash"))) throw new MediaException(409, "IDEMPOTENCY_CONFLICT", "Mã tải lên đã được sử dụng cho nội dung khác");
                if ("COMPLETE".equals(previous.get("state")) && instant(previous, "expires_at").isAfter(java.time.Instant.now()))
                    return new Reservation(null,null,null,null,null,uuid(previous,"attachment_id"));
                if ("PENDING".equals(previous.get("state"))) throw new MediaException(409, "UPLOAD_IN_PROGRESS", "Tệp đang được xử lý");
                throw new MediaException(409, "IDEMPOTENCY_CONFLICT", "Lần tải trước đã kết thúc hoặc hết hạn; vui lòng dùng mã tải mới");
            }
            if (avatar) version(repository.member(memberId, false), "media_version", expected);
            if (repository.count("SELECT count(*) FROM media_upload_requests WHERE caller_account_id=? AND created_at>now()-interval '1 minute'", caller) >= 10
                    || repository.count("SELECT count(*) FROM stored_files WHERE created_by=? AND state='PENDING'", caller) >= 2)
                throw new MediaException(429, "UPLOAD_RATE_LIMITED", "Đã vượt số lượt tải lên, vui lòng thử lại sau");
            if (!avatar && repository.count("SELECT (SELECT count(*) FROM member_attachments WHERE member_id=? AND deleted_at IS NULL) + (SELECT count(*) FROM stored_files WHERE member_id=? AND purpose='ATTACHMENT' AND state='PENDING')", memberId, memberId) >= properties.getMaxAttachments())
                throw new MediaException(409, "ATTACHMENT_LIMIT_REACHED", "Hồ sơ đã đạt giới hạn tư liệu");
            if (repository.count("SELECT coalesce(sum(size_bytes),0) FROM stored_files WHERE state<>'DELETED'") + file.bytes().length > properties.getQuotaBytes())
                throw new MediaException(503, "STORAGE_QUOTA_EXCEEDED", "Kho lưu trữ đã đạt hạn mức");
            UUID id = UUID.randomUUID(), request = UUID.randomUUID();
            String provider = storage.current().provider(), bucket = properties.getBucket();
            String objectKey = access.caller().clanId() + "/" + memberId + "/" + id + "." + file.extension();
            repository.update("INSERT INTO stored_files (id,clan_id,member_id,purpose,provider,bucket,object_key,original_filename,mime_type,size_bytes,checksum_sha256,created_by,state) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,'PENDING')",
                    id, access.caller().clanId(), memberId, avatar ? "AVATAR" : "ATTACHMENT", provider, bucket, objectKey,
                    file.originalFilename(), file.mimeType(), file.bytes().length, file.checksum(), caller);
            repository.update("INSERT INTO media_upload_requests (id,caller_account_id,member_id,idempotency_key,request_hash,file_id,state) VALUES (?,?,?,?,?,?,'PENDING')", request, caller, memberId, key, hash, id);
            return new Reservation(request,id,provider,bucket,objectKey,null);
        });
    }
    private void upload(Reservation reserved, MediaFileValidator.Validated file) {
        try { storage.get(reserved.provider()).put(reserved.bucket(), reserved.key(), file.bytes(), file.mimeType()); }
        catch (RuntimeException ex) {
            tx(() -> { markDelete(reserved.fileId()); repository.update("UPDATE media_upload_requests SET state='FAILED' WHERE id=?", reserved.requestId()); return null; });
            throw ex;
        }
    }
    private <T> T finish(Reservation reserved, Supplier<T> commit) {
        try { return tx(commit); }
        catch (RuntimeException failure) {
            try {
                tx(() -> {
                    // Conditional state prevents deleting an object if a commit outcome is uncertain.
                    repository.update("UPDATE stored_files SET state='DELETE_PENDING',cleanup_after=now(),updated_at=now() WHERE id=? AND state='PENDING'", reserved.fileId());
                    repository.update("UPDATE media_upload_requests SET state='FAILED' WHERE id=? AND state='PENDING'", reserved.requestId());
                    return null;
                });
            } catch (RuntimeException cleanupFailure) { failure.addSuppressed(cleanupFailure); }
            throw failure;
        }
    }
    private void ready(UUID file) {
        if (repository.update("UPDATE stored_files SET state='READY',updated_at=now() WHERE id=? AND state='PENDING'", file) != 1)
            throw new MediaException(409, "MEDIA_VERSION_CONFLICT", "Lần tải lên không còn hiệu lực");
    }
    private void complete(Reservation reserved, UUID attachment) {
        repository.update("UPDATE media_upload_requests SET state='COMPLETE',attachment_id=? WHERE id=?", attachment, reserved.requestId());
    }
    private void markDelete(UUID file) {
        if (file != null) repository.update("UPDATE stored_files SET state='DELETE_PENDING',updated_at=now(),cleanup_after=now() WHERE id=?", file);
    }
    public Map<String,Object> attachments(String memberId, int page, int size) {
        if (page < 0 || page > 1_000_000 || size < 1 || size > 20) throw MediaException.invalid("Phân trang không hợp lệ");
        return tx(() -> {
            var access = policy.access(memberId);
            String where = " FROM member_attachments a JOIN stored_files f ON f.id=a.file_id WHERE a.member_id=? AND a.deleted_at IS NULL AND f.state='READY' AND (? OR a.visibility='CLAN' OR (a.visibility='BRANCH' AND ?))";
            boolean privileged = access.self() || access.manager();
            long count = repository.count("SELECT count(*)" + where, memberId, privileged, access.sameBranchTree());
            var rows = repository.rows("SELECT a.*,f.original_filename,f.mime_type,f.size_bytes" + where + " ORDER BY a.created_at DESC,a.id DESC LIMIT ? OFFSET ?", memberId, privileged, access.sameBranchTree(), size, (long)page * size);
            return Map.of("items", rows.stream().map(r -> metadata(r,access.canEdit())).toList(), "page",page,"size",size,"totalElements",count,"totalPages",(count+size-1)/size);
        });
    }
    public Map<String,Object> attachment(String memberId, UUID id) {
        return tx(() -> {
            var access = policy.access(memberId);
            var row = repository.attachment(memberId,id);
            if (row == null || !access.canView(string(row,"visibility"))) throw MediaException.missing();
            return metadata(row, access.canEdit());
        });
    }
    public Download attachmentContent(String memberId, UUID id) {
        var row = tx(() -> {
            var access = policy.access(memberId); var file = repository.attachment(memberId,id);
            if (file == null || !access.canView(string(file,"visibility"))) throw MediaException.missing();
            return file;
        });
        String filename = string(row, "original_filename");
        // Normalized images may have changed format; download names follow actual stored bytes.
        if ("image/jpeg".equals(row.get("mime_type"))) filename = filename.replaceAll("(?i)\\.[^.]+$", "") + ".jpg";
        return download(row,filename);
    }
    private Download download(Map<String,Object> row, String filename) {
        return new Download(storage.get(string(row,"provider")).open(string(row,"bucket"),string(row,"object_key")), filename,
                string(row,"mime_type"),number(row,"size_bytes"));
    }
    public Map<String,Object> patchAttachment(String memberId, UUID id, JsonNode patch) {
        return tx(() -> {
            repository.lockMember(memberId); var access = edit(memberId);
            var row = repository.attachment(memberId,id); if (row == null) throw MediaException.missing();
            if (patch == null || !patch.isObject() || !patch.has("expectedVersion") || !patch.get("expectedVersion").isIntegralNumber()
                    || !patch.get("expectedVersion").canConvertToLong()) throw MediaException.invalid("Thiếu hoặc sai phiên bản tư liệu");
            version(row,"version",patch.get("expectedVersion").asLong());
            for (var field : patch.properties()) if (!Set.of("expectedVersion","title","description","visibility").contains(field.getKey())) throw MediaException.invalid("Trường cập nhật không hợp lệ");
            if (patch.size() < 2) throw MediaException.invalid("Cần ít nhất một trường để cập nhật");
            String title = patch.has("title") ? textValue(patch.get("title"),150,true) : string(row,"title");
            String description = patch.has("description") ? textValue(patch.get("description"),1000,false) : string(row,"description");
            String visibility = patch.has("visibility") ? MediaVisibility.parse(textValue(patch.get("visibility"),20,true)).name() : string(row,"visibility");
            repository.update("UPDATE member_attachments SET title=?,description=?,visibility=?,version=version+1,updated_at=now(),updated_by=? WHERE id=?", title,description,visibility,access.caller().accountId(),id);
            audit(access,AuditAction.UPDATE,"ATTACHMENT_UPDATED",visibility);
            return metadata(repository.attachment(memberId,id),true);
        });
    }
    public void deleteAttachment(String memberId, UUID id, long expected) {
        tx(() -> {
            repository.lockMember(memberId); var access = edit(memberId);
            var row = repository.attachment(memberId,id); if (row == null) throw MediaException.missing();
            version(row,"version",expected);
            repository.update("UPDATE member_attachments SET deleted_at=now(),updated_at=now(),updated_by=?,version=version+1 WHERE id=?",access.caller().accountId(),id);
            markDelete(uuid(row,"file_id")); audit(access,AuditAction.DELETE,"ATTACHMENT_DELETED",string(row,"visibility")); return null;
        });
    }
    private Map<String,Object> metadata(Map<String,Object> row, boolean editable) {
        Map<String,Object> result = new LinkedHashMap<>();
        for (String field : List.of("id","member_id","title","description","original_filename","mime_type","size_bytes","visibility","version","created_at","updated_at")) {
            String[] pieces = field.split("_"); StringBuilder camel = new StringBuilder(pieces[0]);
            for (int i=1;i<pieces.length;i++) camel.append(Character.toUpperCase(pieces[i].charAt(0))).append(pieces[i].substring(1));
            result.put(camel.toString(), field.endsWith("_at") ? instant(row,field) : row.get(field));
        }
        result.put("contentUrl","/api/v1/members/"+row.get("member_id")+"/attachments/"+row.get("id")+"/content");
        result.put("canEdit",editable);result.put("canDelete",editable);return result;
    }
    private String textValue(JsonNode value,int max,boolean required) {
        if (value == null || value.isNull()) return text(null,max,required);
        if (!value.isString()) throw MediaException.invalid("Nội dung phải là chuỗi");
        return text(value.asString(),max,required);
    }
    private String text(String value,int max,boolean required) {
        String clean = value == null ? null : value.trim();
        if ((required && (clean == null || clean.isBlank())) || (clean != null && (clean.length()>max || clean.indexOf('\0')>=0)))
            throw MediaException.invalid("Tiêu đề hoặc mô tả không hợp lệ");
        return clean;
    }
}
