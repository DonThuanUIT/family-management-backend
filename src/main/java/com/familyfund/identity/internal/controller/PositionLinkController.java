package com.familyfund.identity.internal.controller;

import com.familyfund.core.dto.ApiResponse;
import com.familyfund.identity.api.PositionLinkService;
import com.familyfund.identity.api.dto.ClaimPositionRequest;
import com.familyfund.identity.api.dto.ClaimPositionResponse;
import com.familyfund.identity.api.dto.CreateVirtualMemberRequest;
import com.familyfund.identity.api.dto.CreateVirtualMemberResponse;
import com.familyfund.identity.api.dto.PositionLinkPreviewResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

/**
 * Position link endpoints.
 *
 * <p><b>US-18 (privileged):</b> a clan head / branch head / admin creates a virtual (placeholder) position in the
 * family tree and receives a one-time position link code to hand out:
 * {@code POST /api/v1/branches/{branchId}/virtual-members}. The path mirrors the resource hierarchy used by US-03
 * ({@code POST /api/v1/branches/{branchId}/members}) because the profile is created inside a branch, while the
 * {@code Location} header points at the position link code, which is an {@code /api/v1/invitations} resource.</p>
 *
 * <p><b>US-19 (public):</b> the recipient of that code previews the position and then claims it:
 * {@code GET /api/v1/position-links/{inviteCode}} and
 * {@code POST /api/v1/position-links/{inviteCode}/claim}. The code is the only credential needed for the preview;
 * the claim additionally asks for the phone number and password of the account that will own the position. A
 * correct code binds the account to the branch through the pre-created profile, activates the account and needs no
 * approval (see {@code SecurityConfig} for the {@code permitAll} rules).</p>
 *
 * <p>The contract is documented in {@code docker/swagger/openapi.yaml} (served by the Swagger UI container on
 * port 8082).</p>
 */
@RestController
@RequiredArgsConstructor
public class PositionLinkController {

    private final PositionLinkService positionLinkService;

    /**
     * Creates a placeholder member profile inside the given branch plus its one-time {@code PositionLinkCode}.
     *
     * @param branchId branch that receives the virtual profile, taken from the path
     * @return {@code 201 Created} with the profile and its position link code
     */
    @PostMapping("/api/v1/branches/{branchId}/virtual-members")
    @PreAuthorize("hasAnyRole('CLAN_HEAD', 'BRANCH_HEAD', 'ADMIN')")
    public ResponseEntity<ApiResponse<CreateVirtualMemberResponse>> createVirtualMemberWithPositionLink(
            @PathVariable String branchId,
            @Valid @RequestBody CreateVirtualMemberRequest request) {
        CreateVirtualMemberResponse response = positionLinkService
                .createVirtualMemberWithPositionLink(branchId, request);

        URI location = URI.create("/api/v1/invitations/" + response.positionLinkCode().getInviteCode());
        return ResponseEntity.status(HttpStatus.CREATED)
                .location(location)
                .body(ApiResponse.success(response, "Tạo hồ sơ ảo và mã liên kết vị trí thành công"));
    }

    /**
     * Previews the position a position link code points at (public, for the redemption landing page).
     *
     * @param inviteCode the code from the shared link
     * @return {@code 200 OK} with the clan, branch and profile the code belongs to
     */
    @GetMapping("/api/v1/position-links/{inviteCode}")
    public ResponseEntity<ApiResponse<PositionLinkPreviewResponse>> getPositionLinkPreview(
            @PathVariable String inviteCode) {
        PositionLinkPreviewResponse preview = positionLinkService.getPositionLinkPreview(inviteCode);
        return ResponseEntity.ok(ApiResponse.success(preview, "Lấy thông tin vị trí liên kết thành công"));
    }

    /**
     * Redeems the code: links the caller's account to the position and activates it, with no approval step.
     *
     * @param inviteCode the one-time code handed to the recipient
     * @param request    phone number + password of the account that will own the position
     * @return {@code 200 OK} with the linked account, the claimed position and the consumed code
     */
    @PostMapping("/api/v1/position-links/{inviteCode}/claim")
    public ResponseEntity<ApiResponse<ClaimPositionResponse>> claimPosition(
            @PathVariable String inviteCode,
            @Valid @RequestBody ClaimPositionRequest request) {
        ClaimPositionResponse response = positionLinkService.claimPosition(inviteCode, request);
        return ResponseEntity.ok(ApiResponse.success(response, "Liên kết tài khoản với vị trí phả hệ thành công"));
    }
}