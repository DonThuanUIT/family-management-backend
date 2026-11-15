package com.familyfund.core.storage;

final class StorageKeys {
    private StorageKeys() {}
    static void validate(String bucket, String key) {
        if (bucket == null || !bucket.matches("[a-zA-Z0-9_-]{1,100}") || key == null
                || !key.matches("[a-zA-Z0-9_-]{1,20}/[a-zA-Z0-9_-]{1,20}/[a-fA-F0-9-]{36}\\.(jpg|png|pdf)")) {
            throw MediaException.invalid("Đường dẫn lưu trữ không hợp lệ");
        }
    }
}
