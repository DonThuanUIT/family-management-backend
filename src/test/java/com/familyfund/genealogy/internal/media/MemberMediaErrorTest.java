package com.familyfund.genealogy.internal.media;

import com.familyfund.core.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import static org.junit.jupiter.api.Assertions.*;

class MemberMediaErrorTest {
    @Test void multipartLimitBeforeControllerSelectionStillReturns413JsonEnvelope() {
        var response=new GlobalExceptionHandler().handleUploadTooLarge(new MaxUploadSizeExceededException(10*1024*1024));
        assertEquals(413,response.getStatusCode().value());
        assertNotNull(response.getBody());assertFalse(response.getBody().isSuccess());
        assertEquals("FILE_TOO_LARGE",response.getBody().getCode());
    }
}
