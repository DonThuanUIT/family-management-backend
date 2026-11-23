package com.familyfund.core.exception;

import com.familyfund.core.dto.ApiResponse;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.sql.SQLException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Centralized exception handler providing consistent API error responses.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidationException(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new HashMap<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            errors.put(error.getField(), error.getDefaultMessage());
        }
        log.warn("Validation error on request: {}", errors);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error("Invalid request payload", errors));
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleResourceNotFoundException(ResourceNotFoundException ex) {
        log.warn("Resource not found: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error(ex.getMessage()));
    }

    @ExceptionHandler(BusinessRuleException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusinessRuleException(BusinessRuleException ex) {
        log.warn("Business rule violation: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(ex.getMessage()));
    }

    @ExceptionHandler(BranchCycleException.class)
    public ResponseEntity<ApiResponse<Void>> handleBranchCycleException(BranchCycleException ex) {
        log.warn("Branch cycle violation: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(ex.getMessage()));
    }

    @ExceptionHandler(InvalidGenerationException.class)
    public ResponseEntity<ApiResponse<Void>> handleInvalidGenerationException(InvalidGenerationException ex) {
        log.warn("Invalid generation level: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(ex.getMessage()));
    }

    /**
     * Invitation / position-link rejections: keeps the HTTP status of {@link BadRequestException} but also exposes
     * the machine-readable reason (revoked / expired / exhausted) so clients do not have to parse the message.
     */
    @ExceptionHandler(InviteCodeException.class)
    public ResponseEntity<ApiResponse<Void>> handleInviteCodeException(InviteCodeException ex) {
        log.warn("Invite code rejected [{}]: {}", ex.getCode(), ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.errorWithCode(ex.getCode(), ex.getMessage()));
    }

    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<ApiResponse<Void>> handleBadRequestException(BadRequestException ex) {
        log.warn("Bad request: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(ex.getMessage()));
    }

    @ExceptionHandler(DuplicateResourceException.class)
    public ResponseEntity<ApiResponse<Void>> handleDuplicateResourceException(DuplicateResourceException ex) {
        log.warn("Duplicate resource: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.error(ex.getMessage()));
    }

    @ExceptionHandler(LedgerImmutabilityViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleLedgerImmutabilityViolation(LedgerImmutabilityViolationException ex) {
        log.warn("SECURITY VIOLATION [NFR-01]: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.errorWithCode(LedgerImmutabilityViolationException.ERROR_CODE, ex.getMessage()));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleDataIntegrityViolationException(DataIntegrityViolationException ex) {
        if (isLedgerImmutabilityDbViolation(ex)) {
            log.warn("SECURITY VIOLATION [NFR-01] DB trigger/constraint blocked ledger mutation: {}",
                    ex.getMostSpecificCause().getMessage());
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.errorWithCode(
                            LedgerImmutabilityViolationException.ERROR_CODE,
                            "Vi phạm bảo mật sổ cái bất biến (NFR-01): Thao tác sửa/xóa hoặc ghi sai chuỗi băm trên sổ cái bị từ chối ở tầng cơ sở dữ liệu."));
        }
        // Last line of defence (PK/UNIQUE/FK/CHECK). Do not leak SQL details to clients.
        log.warn("Data integrity violation: {}", ex.getMostSpecificCause().getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.error("Request conflicts with existing data"));
    }

    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<ApiResponse<Void>> handleDataAccessException(DataAccessException ex) {
        if (isLedgerImmutabilityDbViolation(ex)) {
            log.warn("SECURITY VIOLATION [NFR-01] DB trigger blocked ledger mutation: {}",
                    ex.getMostSpecificCause().getMessage());
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.errorWithCode(
                            LedgerImmutabilityViolationException.ERROR_CODE,
                            "Vi phạm bảo mật sổ cái bất biến (NFR-01): Thao tác sửa/xóa hoặc ghi sai chuỗi băm trên sổ cái bị từ chối ở tầng cơ sở dữ liệu."));
        }
        log.error("Database access error: ", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("Internal server error: " + ex.getMessage()));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDeniedException(AccessDeniedException ex) {
        log.warn("Access denied: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.error("Access denied: You do not have permission to perform this action"));
    }

    @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
    public ResponseEntity<ApiResponse<Void>> handleIllegalExceptions(RuntimeException ex) {
        log.warn("Illegal argument/state error: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(ex.getMessage()));
    }

    /*
     * Spring MVC signals these as exceptions carrying their own 4xx status. Without explicit handlers the
     * catch-all below would turn a malformed body / unknown route / wrong verb into a misleading 500.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnreadableBody(HttpMessageNotReadableException ex) {
        log.warn("Malformed request body: {}", ex.getMostSpecificCause().getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error("Malformed JSON request body"));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        String paramName = ex.getName();
        Object rawValue = ex.getValue();
        Class<?> requiredType = ex.getRequiredType();

        String message;
        if (requiredType != null && requiredType.isEnum()) {
            String allowedValues = Arrays.stream(requiredType.getEnumConstants())
                    .map(Object::toString)
                    .collect(Collectors.joining(", "));
            message = "Tham số '" + paramName + "' có giá trị không hợp lệ ('" + rawValue
                    + "'). Giá trị hợp lệ: [" + allowedValues + "]";
        } else {
            message = "Tham số '" + paramName + "' có kiểu dữ liệu không hợp lệ ('" + rawValue + "')";
        }

        log.warn("Parameter type mismatch on '{}': value='{}'", paramName, rawValue);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(message));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiResponse<Void>> handleMissingParameter(MissingServletRequestParameterException ex) {
        log.warn("Missing required request parameter '{}'", ex.getParameterName());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error("Thiếu tham số bắt buộc '" + ex.getParameterName() + "'"));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleConstraintViolation(ConstraintViolationException ex) {
        Map<String, String> errors = new HashMap<>();
        for (ConstraintViolation<?> violation : ex.getConstraintViolations()) {
            String path = violation.getPropertyPath() != null ? violation.getPropertyPath().toString() : "param";
            errors.put(path, violation.getMessage());
        }
        log.warn("Constraint violation on request: {}", errors);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error("Invalid request parameters", errors));
    }

    // Multipart resolution can fail before Spring selects a controller, so this must be global.
    @ExceptionHandler(org.springframework.web.multipart.MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiResponse<Void>> handleUploadTooLarge(org.springframework.web.multipart.MaxUploadSizeExceededException ex) {
        return ResponseEntity.status(413)
                .body(ApiResponse.errorWithCode("FILE_TOO_LARGE", "Tệp vượt dung lượng cho phép"));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNoResourceFound(NoResourceFoundException ex) {
        log.warn("No handler for request: {}", ex.getResourcePath());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error("Resource not found: " + ex.getResourcePath()));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex) {
        log.warn("Method not supported: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
                .body(ApiResponse.error("HTTP method " + ex.getMethod() + " is not supported for this resource"));
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleMediaTypeNotSupported(HttpMediaTypeNotSupportedException ex) {
        log.warn("Media type not supported: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
                .body(ApiResponse.error("Content-Type " + ex.getContentType() + " is not supported; use application/json"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGenericException(Exception ex) {
        if (isLedgerImmutabilityDbViolation(ex)) {
            log.warn("SECURITY VIOLATION [NFR-01] blocked ledger mutation: {}", ex.getMessage());
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.errorWithCode(
                            LedgerImmutabilityViolationException.ERROR_CODE,
                            "Vi phạm bảo mật sổ cái bất biến (NFR-01): Thao tác sửa/xóa hoặc ghi sai chuỗi băm trên sổ cái bị từ chối ở tầng cơ sở dữ liệu."));
        }
        log.error("Unhandled server error: ", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("Internal server error: " + ex.getMessage()));
    }

    private static boolean isLedgerImmutabilityDbViolation(Throwable ex) {
        Throwable cursor = ex;
        while (cursor != null) {
            String message = cursor.getMessage();
            if (message != null && (message.contains("SECURITY VIOLATION [NFR-01]")
                    || message.contains("immutable_ledgers_event_id_fkey")
                    || message.contains("immutable_ledgers_adjustment_request_id_fkey")
                    || message.contains("immutable_ledgers_disbursement_request_id_fkey")
                    || message.contains("chk_immutable_ledgers_record_hash_sha256")
                    || message.contains("chk_immutable_ledgers_previous_hash_sha256"))) {
                return true;
            }
            if (cursor instanceof SQLException sqlEx
                    && "42501".equals(sqlEx.getSQLState())
                    && message != null
                    && message.contains("immutable_ledgers")) {
                return true;
            }
            cursor = cursor.getCause();
        }
        return false;
    }
}
