package com.familyfund.core.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.sql.DriverManager;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

/** Opt-in only. Creates and removes disposable objects; never prints environment secrets. */
@EnabledIfEnvironmentVariable(named="SUPABASE_LIVE_TEST",matches="true")
class SupabaseStorageLiveTest {
    @Test void realAdapterRoundTripAndNoActiveLocalFiles() throws Exception {
        var properties=new StorageProperties();
        properties.setSupabaseUrl(System.getenv("SUPABASE_URL"));
        properties.setSupabaseServiceKey(System.getenv("SUPABASE_SERVICE_ROLE_KEY"));
        properties.setBucket(System.getenv().getOrDefault("APP_STORAGE_BUCKET","member-media"));
        var storage=new SupabaseStorageAdapter(properties);
        var bytes=new ByteArrayOutputStream();ImageIO.write(new BufferedImage(128,128,BufferedImage.TYPE_INT_RGB),"jpg",bytes);
        String key="US32SMOKE/US32SMOKE/"+UUID.randomUUID()+".jpg";
        try {
            storage.put(properties.getBucket(),key,bytes.toByteArray(),"image/jpeg");
            assertTrue(storage.exists(properties.getBucket(),key));
            try(var content=storage.open(properties.getBucket(),key)) { assertArrayEquals(bytes.toByteArray(),content.readAllBytes()); }
        } finally { storage.delete(properties.getBucket(),key); }
        assertFalse(storage.exists(properties.getBucket(),key));
        try(var connection=DriverManager.getConnection(System.getenv("SPRING_DATASOURCE_URL"),System.getenv("DB_USER"),System.getenv("DB_PASSWORD"));
            var statement=connection.prepareStatement("SELECT count(*) FROM stored_files WHERE provider='LOCAL' AND state<>'DELETED'");
            var rows=statement.executeQuery()) {
            assertTrue(rows.next());assertEquals(0,rows.getLong(1),"Migrate active local files before removing local storage");
        }
    }
}
