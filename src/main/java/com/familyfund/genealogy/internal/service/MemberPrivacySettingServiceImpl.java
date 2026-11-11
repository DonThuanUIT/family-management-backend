package com.familyfund.genealogy.internal.service;

import com.familyfund.core.exception.ResourceNotFoundException;
import com.familyfund.core.security.CurrentUserProvider;
import com.familyfund.genealogy.internal.dto.request.UpdateMemberPrivacySettingRequest;
import com.familyfund.genealogy.internal.dto.response.MemberPrivacySettingResponse;
import com.familyfund.genealogy.internal.entity.MemberPrivacySetting;
import com.familyfund.genealogy.internal.repository.MemberPrivacySettingRepository;
import com.familyfund.genealogy.internal.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class MemberPrivacySettingServiceImpl implements MemberPrivacySettingService {

    private final MemberPrivacySettingRepository privacySettingRepository;
    private final MemberRepository memberRepository;
    private final CurrentUserProvider currentUserProvider;

    @Override
    @Transactional(readOnly = true)
    public MemberPrivacySettingResponse getMyPrivacySettings() {
        String memberId = resolveCurrentMemberId();

        MemberPrivacySetting setting = privacySettingRepository.findById(memberId).orElse(null);
        if (setting == null) {
            return MemberPrivacySettingResponse.builder()
                    .memberId(memberId)
                    .shareDateOfBirth(false)
                    .sharePhoneNumber(false)
                    .shareEmail(false)
                    .updatedAt(null)
                    .build();
        }

        return toResponse(setting);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MemberPrivacySettingResponse updateMyPrivacySettings(UpdateMemberPrivacySettingRequest request) {
        String memberId = resolveCurrentMemberId();

        // Verify member exists
        if (!memberRepository.existsByMemberId(memberId)) {
            throw new ResourceNotFoundException("Member not found with ID: " + memberId);
        }

        MemberPrivacySetting setting = privacySettingRepository.findById(memberId)
                .orElseGet(() -> MemberPrivacySetting.builder()
                        .memberId(memberId)
                        .build());

        setting.setShareDateOfBirth(Boolean.TRUE.equals(request.shareDateOfBirth()));
        setting.setSharePhoneNumber(Boolean.TRUE.equals(request.sharePhoneNumber()));
        setting.setShareEmail(Boolean.TRUE.equals(request.shareEmail()));

        MemberPrivacySetting saved = privacySettingRepository.save(setting);
        log.info("Updated privacy settings for member: memberId={}, shareDob={}, sharePhone={}, shareEmail={}",
                memberId, saved.isShareDateOfBirth(), saved.isSharePhoneNumber(), saved.isShareEmail());

        return toResponse(saved);
    }

    private String resolveCurrentMemberId() {
        return currentUserProvider.currentMemberId()
                .filter(id -> !id.isBlank())
                .orElseThrow(() -> new AccessDeniedException("Authenticated user is not linked to a member profile"));
    }

    private MemberPrivacySettingResponse toResponse(MemberPrivacySetting setting) {
        return MemberPrivacySettingResponse.builder()
                .memberId(setting.getMemberId())
                .shareDateOfBirth(setting.isShareDateOfBirth())
                .sharePhoneNumber(setting.isSharePhoneNumber())
                .shareEmail(setting.isShareEmail())
                .updatedAt(setting.getUpdatedAt())
                .build();
    }
}
