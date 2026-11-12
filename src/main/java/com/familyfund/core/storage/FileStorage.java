package com.familyfund.core.storage;

import java.io.InputStream;

public interface FileStorage {
    String provider();
    void put(String bucket, String key, byte[] content, String mimeType);
    InputStream open(String bucket, String key);
    /** Idempotent; success also means the object was already absent. */
    void delete(String bucket, String key);
    boolean exists(String bucket, String key);
}
