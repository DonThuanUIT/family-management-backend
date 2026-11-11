package com.familyfund.genealogy.internal.service;

import com.familyfund.core.exception.ResourceNotFoundException;
import com.familyfund.core.security.CurrentUserProvider;
import com.familyfund.genealogy.internal.dto.request.UpdateMemberPrivacySettingRequest;
import com.familyfund.genealogy.internal.dto.response.MemberPrivacySettingResponse;
import com.familyfund.genealogy.internal.entity.MemberPrivacySetting;
import com.familyfund.genealogy.internal.repository.MemberPrivacySettingRepository;
import com.familyfund.genealogy.internal.repository.MemberRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("US-25: MemberPrivacySettingServiceImpl Unit Tests")
class MemberPrivacySettingServiceImplTest {

    private static final String MEMBER_ID = "MEM123456";

    @Mock
    private MemberPrivacySettingRepository privacySettingRepository;

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private CurrentUserProvider currentUserProvider;

    @InjectMocks
    private MemberPrivacySettingServiceImpl privacySettingService;

    @Test
    @DisplayName("getMyPrivacySettings: Throws AccessDeniedException when caller has no linked member profile")
    void shouldThrowWhenCallerNotLinkedToMember() {
        when(currentUserProvider.currentMemberId()).thenReturn(Optional.empty());

        assertThrows(AccessDeniedException.class, () -> privacySettingService.getMyPrivacySettings());
    }

    @Test
    @DisplayName("getMyPrivacySettings: Returns defaults (all false) when no setting record exists")
    void shouldReturnDefaultSettingsWhenNotFound() {
        when(currentUserProvider.currentMemberId()).thenReturn(Optional.of(MEMBER_ID));
        when(privacySettingRepository.findById(MEMBER_ID)).thenReturn(Optional.empty());

        MemberPrivacySettingResponse response = privacySettingService.getMyPrivacySettings();

        assertNotNull(response);
        assertEquals(MEMBER_ID, response.memberId());
        assertFalse(response.shareDateOfBirth());
        assertFalse(response.sharePhoneNumber());
        assertFalse(response.shareEmail());
    }

    @Test
    @DisplayName("getMyPrivacySettings: Returns persisted settings when present")
    void shouldReturnExistingSettings() {
        when(currentUserProvider.currentMemberId()).thenReturn(Optional.of(MEMBER_ID));
        MemberPrivacySetting existing = MemberPrivacySetting.builder()
                .memberId(MEMBER_ID)
                .shareDateOfBirth(true)
                .sharePhoneNumber(false)
                .shareEmail(true)
                .build();
        when(privacySettingRepository.findById(MEMBER_ID)).thenReturn(Optional.of(existing));

        MemberPrivacySettingResponse response = privacySettingService.getMyPrivacySettings();

        assertTrue(response.shareDateOfBirth());
        assertFalse(response.sharePhoneNumber());
        assertTrue(response.shareEmail());
    }

    @Test
    @DisplayName("updateMyPrivacySettings: Throws ResourceNotFoundException if member profile no longer exists in members table")
    void shouldThrowWhenMemberProfileNotFound() {
        when(currentUserProvider.currentMemberId()).thenReturn(Optional.of(MEMBER_ID));
        when(memberRepository.existsByMemberId(MEMBER_ID)).thenReturn(false);

        UpdateMemberPrivacySettingRequest req = UpdateMemberPrivacySettingRequest.builder()
                .shareDateOfBirth(true)
                .sharePhoneNumber(true)
                .shareEmail(true)
                .build();

        assertThrows(ResourceNotFoundException.class, () -> privacySettingService.updateMyPrivacySettings(req));
        verify(privacySettingRepository, never()).save(any());
    }

    @Test
    @DisplayName("updateMyPrivacySettings: Creates or updates privacy setting successfully")
    void shouldUpdatePrivacySettingsSuccessfully() {
        when(currentUserProvider.currentMemberId()).thenReturn(Optional.of(MEMBER_ID));
        when(memberRepository.existsByMemberId(MEMBER_ID)).thenReturn(true);
        when(privacySettingRepository.findById(MEMBER_ID)).thenReturn(Optional.empty());
        when(privacySettingRepository.save(any(MemberPrivacySetting.class))).thenAnswer(inv -> inv.getArgument(0));

        UpdateMemberPrivacySettingRequest req = UpdateMemberPrivacySettingRequest.builder()
                .shareDateOfBirth(true)
                .sharePhoneNumber(false)
                .shareEmail(true)
                .build();

        MemberPrivacySettingResponse response = privacySettingService.updateMyPrivacySettings(req);

        ArgumentCaptor<MemberPrivacySetting> captor = ArgumentCaptor.forClass(MemberPrivacySetting.class);
        verify(privacySettingRepository).save(captor.capture());
        MemberPrivacySetting saved = captor.getValue();

        assertEquals(MEMBER_ID, saved.getMemberId());
        assertTrue(saved.isShareDateOfBirth());
        assertFalse(saved.isSharePhoneNumber());
        assertTrue(saved.isShareEmail());

        assertTrue(response.shareDateOfBirth());
        assertFalse(response.sharePhoneNumber());
        assertTrue(response.shareEmail());
    }
}
