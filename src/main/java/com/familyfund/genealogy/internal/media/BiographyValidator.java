package com.familyfund.genealogy.internal.media;

import com.familyfund.core.storage.MediaException;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Set;

@Component
public class BiographyValidator {
    private final ObjectMapper mapper;
    public BiographyValidator(ObjectMapper mapper) { this.mapper = mapper; }
    public JsonNode validate(JsonNode document) {
        if (document == null || !document.isObject() || document.size() != 1 || !document.has("ops")
                || !document.get("ops").isArray() || document.get("ops").isEmpty()) throw invalid();
        if (document.toString().getBytes(StandardCharsets.UTF_8).length > 100 * 1024) throw invalid();
        int chars = 0;
        String last = "";
        for (JsonNode op : document.get("ops")) {
            if (!op.isObject() || !op.has("insert") || !op.get("insert").isString()
                    || op.size() > (op.has("attributes") ? 2 : 1)) throw invalid();
            last = op.get("insert").asString();
            chars += last.codePointCount(0, last.length());
            if (chars > 20_000 || last.indexOf('\0') >= 0) throw invalid();
            JsonNode attrs = op.get("attributes");
            if (attrs == null) continue;
            if (!attrs.isObject()) throw invalid();
            int blockAttrs = 0;
            for (var entry : attrs.properties()) {
                String name = entry.getKey(); JsonNode value = entry.getValue();
                switch (name) {
                    case "bold", "italic", "underline" -> { if (!value.isBoolean()) throw invalid(); }
                    case "header" -> { if (!value.isIntegralNumber() || value.asInt() < 1 || value.asInt() > 3) throw invalid(); blockAttrs++; }
                    case "list" -> { if (!value.isString() || !Set.of("ordered", "bullet").contains(value.asString())) throw invalid(); blockAttrs++; }
                    case "blockquote" -> { if (!value.isBoolean()) throw invalid(); blockAttrs++; }
                    case "link" -> { if (!value.isString() || !safeLink(value.asString())) throw invalid(); }
                    default -> throw invalid();
                }
            }
            if (blockAttrs > 1 || (blockAttrs > 0 && !last.matches("\\n+"))) throw invalid();
        }
        if (!last.endsWith("\n")) {
            if (chars == 20_000) throw invalid();
            var copy = document.deepCopy();
            ((tools.jackson.databind.node.ArrayNode) copy.get("ops")).add(mapper.createObjectNode().put("insert", "\n"));
            if (copy.toString().getBytes(StandardCharsets.UTF_8).length > 100 * 1024) throw invalid();
            return copy;
        }
        return document.deepCopy();
    }
    private boolean safeLink(String value) {
        try {
            URI uri = URI.create(value);
            return "https".equalsIgnoreCase(uri.getScheme()) && uri.getHost() != null && uri.getUserInfo() == null;
        } catch (IllegalArgumentException ex) { return false; }
    }
    private MediaException invalid() { return new MediaException(400, "INVALID_BIOGRAPHY", "Tiểu sử không đúng định dạng hoặc vượt giới hạn cho phép"); }
}
