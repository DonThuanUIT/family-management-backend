package com.familyfund.core.storage;

import org.springframework.stereotype.Component;
import java.io.IOException;
import java.io.InputStream;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;

/** Private Storage REST API; credentials never leave this server. No automatic redirects. */
@Component
public class SupabaseStorageAdapter implements FileStorage {
    private final StorageProperties properties;
    private final HttpClient client;
    @org.springframework.beans.factory.annotation.Autowired
    public SupabaseStorageAdapter(StorageProperties properties) {
        this(properties, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NEVER).build());
    }
    SupabaseStorageAdapter(StorageProperties properties, HttpClient client) {
        this.properties = properties;
        this.client = client;
    }
    @Override public String provider() { return "SUPABASE"; }
    private HttpRequest.Builder request(String operation, String bucket, String key) {
        StorageKeys.validate(bucket, key);
        String base = properties.getSupabaseUrl().replaceAll("/+$", "");
        String serverKey = properties.getSupabaseServiceKey();
        if (!base.startsWith("https://") || serverKey.isBlank() || serverKey.startsWith("sb_publishable_")
                || serverKey.chars().anyMatch(c -> c <= 32 || c >= 127)) throw MediaException.unavailable();
        var builder = HttpRequest.newBuilder(URI.create(base + "/storage/v1/" + operation + "/" + bucket + "/" + key))
                .timeout(Duration.ofSeconds(properties.getTimeoutSeconds()))
                .header("apikey", serverKey);
        // New secret keys are API keys, not JWTs. Legacy service_role uses Bearer auth as well.
        if (!serverKey.startsWith("sb_secret_")) builder.header("Authorization", "Bearer " + serverKey);
        return builder;
    }
    private <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> handler) {
        try { return client.send(request, handler); }
        catch (InterruptedException ex) { Thread.currentThread().interrupt(); throw MediaException.unavailable(); }
        catch (IOException ex) { throw MediaException.unavailable(); }
    }
    @Override public void put(String bucket, String key, byte[] content, String mime) {
        var response = send(request("object", bucket, key).header("Content-Type", mime)
                .header("x-upsert", "false").POST(HttpRequest.BodyPublishers.ofByteArray(content)).build(),
                HttpResponse.BodyHandlers.discarding());
        if (response.statusCode() < 200 || response.statusCode() >= 300) throw MediaException.unavailable();
    }
    @Override public InputStream open(String bucket, String key) {
        // Buffer bounded application files so the request deadline includes downloading the body.
        var response = send(request("object/authenticated", bucket, key).GET().build(), HttpResponse.BodyHandlers.ofByteArray());
        if (response.statusCode() == 200 && response.body().length <= 10 * 1024 * 1024) return new ByteArrayInputStream(response.body());
        if (absent(response.statusCode(), response.body())) throw MediaException.missing();
        throw MediaException.unavailable();
    }
    @Override public void delete(String bucket, String key) {
        var response = send(request("object", bucket, key).DELETE().build(), HttpResponse.BodyHandlers.ofByteArray());
        if (!absent(response.statusCode(), response.body()) && (response.statusCode() < 200 || response.statusCode() >= 300)) throw MediaException.unavailable();
        // Do not release reservations unless absence can be confirmed independently.
        if (exists(bucket, key)) throw MediaException.unavailable();
    }
    @Override public boolean exists(String bucket, String key) {
        var response = send(request("object/info", bucket, key).GET().build(), HttpResponse.BodyHandlers.ofByteArray());
        if (absent(response.statusCode(), response.body())) return false;
        if (response.statusCode() == 200) return true;
        throw MediaException.unavailable();
    }
    private boolean absent(int status, byte[] body) {
        if (status == 404) return true;
        if (status != 400 || body.length > 4096) return false;
        try {
            var error = tools.jackson.databind.json.JsonMapper.builder().build().readTree(body);
            return "404".equals(error.path("statusCode").asString()) || "NoSuchKey".equals(error.path("code").asString());
        } catch (RuntimeException ex) { return false; }
    }
}
