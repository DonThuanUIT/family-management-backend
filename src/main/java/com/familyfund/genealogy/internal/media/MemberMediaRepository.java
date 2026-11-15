package com.familyfund.genealogy.internal.media;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.util.*;
import java.time.Instant;

/** JDBC writes use the same Spring transaction manager as the existing JPA repositories. */
@Repository
@RequiredArgsConstructor
public class MemberMediaRepository {
    private final JdbcTemplate jdbc;

    public int update(String sql, Object... args) { return jdbc.update(sql, args); }
    public long count(String sql, Object... args) { return Objects.requireNonNull(jdbc.queryForObject(sql, Long.class, args)); }
    public List<Map<String, Object>> rows(String sql, Object... args) { return jdbc.queryForList(sql, args); }
    public Map<String, Object> one(String sql, Object... args) {
        var rows = rows(sql, args);
        return rows.isEmpty() ? null : rows.getFirst();
    }
    public Map<String, Object> member(String memberId, boolean lock) {
        return one("SELECT avatar_file_id,avatar_url,biography::text,biography_visibility,media_version FROM members WHERE member_id=?" + (lock ? " FOR UPDATE" : ""), memberId);
    }
    public void lockQuota() { jdbc.queryForObject("SELECT id FROM media_quota_lock WHERE id=1 FOR UPDATE", Integer.class); }
    public void lockMember(String memberId) {
        if (one("SELECT member_id FROM members WHERE member_id=? FOR UPDATE", memberId) == null)
            throw com.familyfund.core.storage.MediaException.missing();
    }
    public Map<String, Object> attachment(String memberId, UUID id) {
        return one("SELECT a.*,f.original_filename,f.mime_type,f.size_bytes,f.provider,f.bucket,f.object_key FROM member_attachments a JOIN stored_files f ON f.id=a.file_id WHERE a.member_id=? AND a.id=? AND a.deleted_at IS NULL AND f.state='READY'", memberId, id);
    }
    public static String string(Map<String, Object> row, String key) { Object value = row.get(key); return value == null ? null : value.toString(); }
    public static long number(Map<String, Object> row, String key) { return ((Number) row.get(key)).longValue(); }
    public static UUID uuid(Map<String, Object> row, String key) { String value = string(row, key); return value == null ? null : UUID.fromString(value); }
    public static Instant instant(Map<String, Object> row, String key) { return ((java.sql.Timestamp)row.get(key)).toInstant(); }
}
