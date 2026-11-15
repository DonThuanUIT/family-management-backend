package com.familyfund.genealogy.internal.media;

import com.familyfund.core.dto.ApiResponse;
import com.familyfund.core.storage.MediaException;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice(assignableTypes=MemberMediaController.class)
@Order(-1)
public class MemberMediaExceptionHandler {
    @ExceptionHandler(MediaException.class)
    public ResponseEntity<ApiResponse<Void>> media(MediaException ex) {
        return ResponseEntity.status(ex.getStatus()).body(ApiResponse.errorWithCode(ex.getCode(),ex.getMessage()));
    }
    @ExceptionHandler({MissingServletRequestParameterException.class,MissingRequestHeaderException.class,
            MissingServletRequestPartException.class,MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ApiResponse<Void>> badRequest(Exception ex) {
        return ResponseEntity.badRequest().body(ApiResponse.errorWithCode("INVALID_MEDIA_REQUEST","Thiếu hoặc sai thông tin yêu cầu"));
    }
}
