package com.familyfund.genealogy.internal.media;

import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class MemberMediaOpenApiTest {
    @Test void openApiParsesAndUs32SchemaReferencesResolve() throws Exception {
        try(var input=Files.newInputStream(Path.of("docker/swagger/openapi.yaml"))) {
            Map<String,Object> document=new Yaml().load(input);
            Map<?,?> paths=(Map<?,?>)document.get("paths");
            assertTrue(paths.containsKey("/members/{memberId}/avatar"));
            assertTrue(paths.containsKey("/members/{memberId}/attachments/{id}/content"));
            for (var entry : paths.entrySet()) {
                String path=entry.getKey().toString();
                if (path.equals("/members/{memberId}") || path.contains("/avatar") || path.contains("/biography") || path.contains("/attachments")) verify(entry.getValue(),document);
            }
            Map<?,?> schemas=(Map<?,?>)((Map<?,?>)document.get("components")).get("schemas");
            for (var entry:schemas.entrySet()) if(entry.getKey().toString().matches("Member(Media|Biography|Attachment).*")) verify(entry.getValue(),document);
        }
    }
    private void verify(Object item,Map<String,Object> document) {
        if(item instanceof Map<?,?> map) {
            if(map.get("$ref") instanceof String ref && ref.startsWith("#/")) {
                Object target=document;
                for(String segment:ref.substring(2).split("/")) {
                    assertInstanceOf(Map.class,target,ref);target=((Map<?,?>)target).get(segment.replace("~1","/").replace("~0","~"));
                    assertNotNull(target,ref);
                }
            }
            map.values().forEach(value->verify(value,document));
        } else if(item instanceof List<?> list) list.forEach(value->verify(value,document));
    }
}
