package com.familyfund.genealogy.internal.media;

import com.familyfund.core.storage.MediaException;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import static org.junit.jupiter.api.Assertions.*;

class BiographyValidatorTest {
    private final JsonMapper mapper = JsonMapper.builder().build();
    private final BiographyValidator validator = new BiographyValidator(mapper);
    @Test void normalizesFinalNewlineAndPreservesFormatting() {
        var input = mapper.readTree("{\"ops\":[{\"insert\":\"Gia đình\",\"attributes\":{\"bold\":true}}]}");
        var output = validator.validate(input);
        assertEquals(2,output.get("ops").size());
        assertEquals(1,input.get("ops").size());
        assertTrue(output.get("ops").get(0).get("attributes").get("bold").asBoolean());
    }
    @Test void rejectsEmbedsAndUpdateOperations() {
        for (String op : new String[]{"{\"insert\":{\"image\":\"data:abc\"}}","{\"retain\":2}","{\"insert\":\"abc\",\"delete\":1}"})
            assertThrows(MediaException.class,()->validator.validate(mapper.readTree("{\"ops\":["+op+"]}")));
    }
    @Test void rejectsUnsafeLinksAndUnknownAttributes() {
        for (String attrs : new String[]{"{\"link\":\"javascript:alert(1)\"}","{\"link\":\"data:text/html,test\"}","{\"color\":\"red\"}","{\"header\":1}"})
            assertThrows(MediaException.class,()->validator.validate(mapper.readTree("{\"ops\":[{\"insert\":\"abc\",\"attributes\":"+attrs+"}]}")));
    }
    @Test void acceptsHeadingOnNewlineAndHttpsLink() {
        assertDoesNotThrow(()->validator.validate(mapper.readTree("{\"ops\":[{\"insert\":\"Nguồn\",\"attributes\":{\"link\":\"https://example.com\"}},{\"insert\":\"\\n\",\"attributes\":{\"header\":2}}]}")));
    }
    @Test void enforcesCharacterLimit() {
        var root = mapper.createObjectNode(); root.putArray("ops").addObject().put("insert","a".repeat(20_001));
        assertEquals("INVALID_BIOGRAPHY",assertThrows(MediaException.class,()->validator.validate(root)).getCode());
    }
}
