package com.familyfund.core.storage;

import lombok.Getter;

@Getter
public class MediaException extends RuntimeException {
    private final int status;
    private final String code;
    public MediaException(int status, String code, String message) {
        super(message); this.status = status; this.code = code;
    }
    public static MediaException invalid(String message) { return new MediaException(400, "INVALID_MEDIA_FILE", message); }
    public static MediaException unavailable() { return new MediaException(503, "STORAGE_UNAVAILABLE", "Dịch vụ lưu trữ tạm thời không khả dụng"); }
    public static MediaException missing() { return new MediaException(404, "MEDIA_NOT_FOUND", "Không tìm thấy nội dung"); }
}
