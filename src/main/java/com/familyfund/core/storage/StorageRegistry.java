package com.familyfund.core.storage;

import org.springframework.stereotype.Component;

@Component
public class StorageRegistry {
    private final SupabaseStorageAdapter storage;
    public StorageRegistry(SupabaseStorageAdapter storage, StorageProperties properties) {
        this.storage = storage;
        if (!properties.getSupabaseUrl().startsWith("https://") || properties.getSupabaseServiceKey().isBlank()
                || properties.getSupabaseServiceKey().startsWith("sb_publishable_"))
            throw new IllegalStateException("Supabase storage requires SUPABASE_URL (HTTPS) and SUPABASE_SERVICE_ROLE_KEY");
    }
    public FileStorage current() { return storage; }
    public FileStorage get(String provider) {
        if (!"SUPABASE".equalsIgnoreCase(provider)) throw MediaException.unavailable();
        return storage;
    }
}
