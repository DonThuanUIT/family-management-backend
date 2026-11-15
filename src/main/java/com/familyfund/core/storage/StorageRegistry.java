package com.familyfund.core.storage;

import org.springframework.stereotype.Component;

@Component
public class StorageRegistry {
    private final SupabaseStorageAdapter storage;
    private final StorageProperties properties;

    public StorageRegistry(SupabaseStorageAdapter storage, StorageProperties properties) {
        this.storage = storage;
        this.properties = properties;
    }

    public boolean isConfigured() {
        return properties.getSupabaseUrl() != null
                && properties.getSupabaseUrl().startsWith("https://")
                && properties.getSupabaseServiceKey() != null
                && !properties.getSupabaseServiceKey().isBlank()
                && !properties.getSupabaseServiceKey().startsWith("sb_publishable_");
    }

    public FileStorage current() {
        if (!isConfigured()) {
            throw new IllegalStateException("Supabase storage requires SUPABASE_URL (HTTPS) and SUPABASE_SERVICE_ROLE_KEY");
        }
        return storage;
    }

    public FileStorage get(String provider) {
        if (!isConfigured()) {
            throw new IllegalStateException("Supabase storage requires SUPABASE_URL (HTTPS) and SUPABASE_SERVICE_ROLE_KEY");
        }
        if (!"SUPABASE".equalsIgnoreCase(provider)) throw MediaException.unavailable();
        return storage;
    }
}
