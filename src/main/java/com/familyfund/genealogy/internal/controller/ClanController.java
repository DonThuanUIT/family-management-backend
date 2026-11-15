package com.familyfund.genealogy.internal.controller;

import com.familyfund.core.dto.ApiResponse;
import com.familyfund.genealogy.api.dto.ClanBankingInfoDto;
import com.familyfund.genealogy.internal.dto.request.UpdateClanBankingRequest;
import com.familyfund.genealogy.internal.service.ClanService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller for managing Clan profile and multi-tenant banking configuration.
 */
@RestController
@RequestMapping("/api/v1/clans")
@RequiredArgsConstructor
public class ClanController {

    private final ClanService clanService;

    /**
     * Retrieves the beneficiary banking configuration of the current clan.
     * All members in the clan can view the official clan bank account.
     */
    @GetMapping("/banking-info")
    @PreAuthorize("hasAnyRole('CLAN_HEAD', 'BRANCH_HEAD', 'MEMBER', 'ADMIN')")
    public ResponseEntity<ApiResponse<ClanBankingInfoDto>> getClanBankingInfo() {
        ClanBankingInfoDto response = clanService.getCurrentClanBankingInfo();
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * Updates the beneficiary banking configuration of the current clan.
     * Restricted to CLAN_HEAD or ADMIN roles.
     */
    @PutMapping("/banking-info")
    @PreAuthorize("hasAnyRole('CLAN_HEAD', 'ADMIN')")
    public ResponseEntity<ApiResponse<ClanBankingInfoDto>> updateClanBankingInfo(
            @Valid @RequestBody UpdateClanBankingRequest request) {
        ClanBankingInfoDto response = clanService.updateCurrentClanBankingInfo(request);
        return ResponseEntity.ok(ApiResponse.success(response, "Cập nhật thông tin tài khoản ngân hàng dòng họ thành công"));
    }
}
