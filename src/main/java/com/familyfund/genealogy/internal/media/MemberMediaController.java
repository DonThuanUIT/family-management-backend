package com.familyfund.genealogy.internal.media;

import com.familyfund.core.dto.ApiResponse;
import com.familyfund.core.storage.MediaException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.core.io.InputStreamResource;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import tools.jackson.databind.JsonNode;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/members/{memberId}")
@PreAuthorize("isAuthenticated()")
@RequiredArgsConstructor
public class MemberMediaController {
    private final MemberMediaService service;
    public record BiographyRequest(@Min(0) @NotNull Long expectedMediaVersion, @NotNull JsonNode biography, @NotBlank String visibility) {}
    @GetMapping
    public ApiResponse<Map<String,Object>> detail(@PathVariable String memberId) { return ApiResponse.success(service.detail(memberId)); }
    @PutMapping(value="/avatar",consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<Map<String,Object>> avatar(@PathVariable String memberId,@RequestPart MultipartFile file,@RequestParam long expectedMediaVersion) {
        return ApiResponse.success(service.avatar(memberId,expectedMediaVersion,file));
    }
    @DeleteMapping("/avatar")
    public ApiResponse<Map<String,Object>> deleteAvatar(@PathVariable String memberId,@RequestParam long expectedMediaVersion) {
        return ApiResponse.success(service.deleteAvatar(memberId,expectedMediaVersion));
    }
    @GetMapping("/avatar/content")
    public ResponseEntity<InputStreamResource> avatarContent(@PathVariable String memberId) { return content(service.avatarContent(memberId),true); }
    @PutMapping("/biography")
    public ApiResponse<Map<String,Object>> biography(@PathVariable String memberId,@Valid @RequestBody BiographyRequest body) {
        return ApiResponse.success(service.biography(memberId,body.expectedMediaVersion(),body.biography(),body.visibility()));
    }
    @PostMapping(value="/attachments",consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<Map<String,Object>>> upload(@PathVariable String memberId,@RequestPart MultipartFile file,
            @RequestParam String title,@RequestParam(required=false) String description,
            @RequestParam(defaultValue="RESTRICTED") String visibility,@RequestHeader("Idempotency-Key") UUID key) {
        var response = service.uploadAttachment(memberId,file,title,description,visibility,key);
        return ResponseEntity.created(URI.create("/api/v1/members/"+memberId+"/attachments/"+response.get("id"))).body(ApiResponse.success(response));
    }
    @GetMapping("/attachments")
    public ApiResponse<Map<String,Object>> list(@PathVariable String memberId,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="10") int size) {
        return ApiResponse.success(service.attachments(memberId,page,size));
    }
    @GetMapping("/attachments/{id}")
    public ApiResponse<Map<String,Object>> attachment(@PathVariable String memberId,@PathVariable UUID id) { return ApiResponse.success(service.attachment(memberId,id)); }
    @PatchMapping("/attachments/{id}")
    public ApiResponse<Map<String,Object>> patch(@PathVariable String memberId,@PathVariable UUID id,@RequestBody JsonNode body) { return ApiResponse.success(service.patchAttachment(memberId,id,body)); }
    @DeleteMapping("/attachments/{id}")
    public ResponseEntity<Void> delete(@PathVariable String memberId,@PathVariable UUID id,@RequestParam long expectedVersion) {
        service.deleteAttachment(memberId,id,expectedVersion);return ResponseEntity.noContent().build();
    }
    @GetMapping("/attachments/{id}/content")
    public ResponseEntity<InputStreamResource> attachmentContent(@PathVariable String memberId,@PathVariable UUID id,@RequestParam(defaultValue="attachment") String disposition) {
        if (!disposition.equals("inline") && !disposition.equals("attachment")) throw MediaException.invalid("Cách tải xuống không hợp lệ");
        var download = service.attachmentContent(memberId,id);
        return content(download,disposition.equals("inline") && download.mimeType().startsWith("image/"));
    }
    private ResponseEntity<InputStreamResource> content(MemberMediaService.Download file,boolean inline) {
        var disposition = (inline ? ContentDisposition.inline() : ContentDisposition.attachment()).filename(file.filename(),StandardCharsets.UTF_8).build();
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(file.mimeType())).contentLength(file.size())
                .cacheControl(CacheControl.noStore().cachePrivate()).header(HttpHeaders.CONTENT_DISPOSITION,disposition.toString())
                .header("X-Content-Type-Options","nosniff").body(new InputStreamResource(file.stream()));
    }
}
