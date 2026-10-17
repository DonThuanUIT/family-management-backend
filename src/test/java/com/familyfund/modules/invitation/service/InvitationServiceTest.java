package com.familyfund.modules.invitation.service;

import com.familyfund.core.exception.BadRequestException;
import com.familyfund.core.exception.ResourceNotFoundException;
import com.familyfund.modules.genealogy.repository.FamilyBranchRepository;
import com.familyfund.modules.invitation.dto.CreateGeneralInviteRequest;
import com.familyfund.modules.invitation.dto.InviteCodeResponse;
import com.familyfund.modules.invitation.entity.InvitationCode;
import com.familyfund.modules.invitation.entity.InviteType;
import com.familyfund.modules.invitation.repository.InvitationCodeRepository;
import com.familyfund.modules.invitation.service.impl.InvitationServiceImpl;
import com.familyfund.modules.invitation.util.InviteCodeGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InvitationServiceTest {

    @Mock
    private InvitationCodeRepository invitationCodeRepository;

    @Mock
    private FamilyBranchRepository familyBranchRepository;

    @Mock
    private InviteCodeGenerator inviteCodeGenerator;

    @InjectMocks
    private InvitationServiceImpl invitationService;

    private String testAccountId;

    @BeforeEach
    void setUp() {
        testAccountId = "ACC_ADMIN_01";
    }

    @Test
    @DisplayName("Create general invite with valid data and no target branch should succeed")
    void createGeneralClanInvite_WithoutBranch_ShouldSucceed() {
        CreateGeneralInviteRequest request = CreateGeneralInviteRequest.builder()
                .maxUses(50)
                .validityDays(7)
                .build();

        String generatedCode = "8K9P2X7N";
        String generatedUrl = "http://localhost:3000/join?code=8K9P2X7N";

        when(inviteCodeGenerator.generateCode()).thenReturn(generatedCode);
        when(invitationCodeRepository.existsByInviteCode(generatedCode)).thenReturn(false);
        when(inviteCodeGenerator.buildShareableUrl(generatedCode)).thenReturn(generatedUrl);
        when(invitationCodeRepository.save(any(InvitationCode.class))).thenAnswer(invocation -> invocation.getArgument(0));

        InviteCodeResponse response = invitationService.createGeneralClanInvite(request, testAccountId);

        assertNotNull(response);
        assertEquals(generatedCode, response.getInviteCode());
        assertEquals(InviteType.GeneralClanInvite, response.getInviteType());
        assertEquals(generatedUrl, response.getShareableUrl());
        assertEquals(50, response.getMaxUses());
        assertEquals(0, response.getUsedCount());
        assertTrue(response.getIsActive());
        assertNull(response.getTargetBranchId());
        assertNull(response.getTargetMemberId());
        assertEquals(testAccountId, response.getCreatedByAccountId());

        ArgumentCaptor<InvitationCode> captor = ArgumentCaptor.forClass(InvitationCode.class);
        verify(invitationCodeRepository).save(captor.capture());
        InvitationCode savedEntity = captor.getValue();
        assertEquals(generatedCode, savedEntity.getInviteCode());
        assertEquals(InviteType.GeneralClanInvite, savedEntity.getInviteType());
    }

    @Test
    @DisplayName("Create general invite with valid target branch should verify branch existence and succeed")
    void createGeneralClanInvite_WithValidBranch_ShouldSucceed() {
        String branchId = "BRANCH_ROOT";
        CreateGeneralInviteRequest request = CreateGeneralInviteRequest.builder()
                .targetBranchId(branchId)
                .maxUses(100)
                .build();

        when(familyBranchRepository.existsByBranchId(branchId)).thenReturn(true);
        when(inviteCodeGenerator.generateCode()).thenReturn("GEN_CODE_1");
        when(invitationCodeRepository.existsByInviteCode("GEN_CODE_1")).thenReturn(false);
        when(inviteCodeGenerator.buildShareableUrl("GEN_CODE_1")).thenReturn("http://localhost:3000/join?code=GEN_CODE_1");
        when(invitationCodeRepository.save(any(InvitationCode.class))).thenAnswer(invocation -> invocation.getArgument(0));

        InviteCodeResponse response = invitationService.createGeneralClanInvite(request, testAccountId);

        assertNotNull(response);
        assertEquals(branchId, response.getTargetBranchId());
        verify(familyBranchRepository).existsByBranchId(branchId);
    }

    @Test
    @DisplayName("Create general invite with non-existent target branch should throw ResourceNotFoundException")
    void createGeneralClanInvite_NonExistentBranch_ShouldThrowException() {
        String invalidBranchId = "INVALID_BRANCH";
        CreateGeneralInviteRequest request = CreateGeneralInviteRequest.builder()
                .targetBranchId(invalidBranchId)
                .build();

        when(familyBranchRepository.existsByBranchId(invalidBranchId)).thenReturn(false);

        assertThrows(ResourceNotFoundException.class,
                () -> invitationService.createGeneralClanInvite(request, testAccountId));

        verify(invitationCodeRepository, never()).save(any());
    }

    @Test
    @DisplayName("Create general invite with blank account ID should throw BadRequestException")
    void createGeneralClanInvite_BlankAccount_ShouldThrowBadRequest() {
        CreateGeneralInviteRequest request = CreateGeneralInviteRequest.builder().build();

        assertThrows(BadRequestException.class,
                () -> invitationService.createGeneralClanInvite(request, "   "));

        assertThrows(BadRequestException.class,
                () -> invitationService.createGeneralClanInvite(request, null));
    }

    @Test
    @DisplayName("Deactivate invite code should set isActive to false and save")
    void deactivateInviteCode_ExistingCode_ShouldSetIsActiveFalse() {
        String code = "INVITE_123";
        InvitationCode existingInvitation = InvitationCode.builder()
                .inviteCode(code)
                .isActive(true)
                .build();

        when(invitationCodeRepository.findById(code)).thenReturn(Optional.of(existingInvitation));
        when(invitationCodeRepository.save(any(InvitationCode.class))).thenAnswer(invocation -> invocation.getArgument(0));

        invitationService.deactivateInviteCode(code, testAccountId);

        assertFalse(existingInvitation.getIsActive());
        verify(invitationCodeRepository).save(existingInvitation);
    }

    @Test
    @DisplayName("Deactivate non-existent invite code should throw ResourceNotFoundException")
    void deactivateInviteCode_NonExistentCode_ShouldThrowNotFound() {
        String code = "NON_EXISTENT";
        when(invitationCodeRepository.findById(code)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> invitationService.deactivateInviteCode(code, testAccountId));
    }

    @Test
    @DisplayName("Get active general invites should query repository with GeneralClanInvite and active=true")
    void getActiveGeneralInvites_ShouldReturnList() {
        InvitationCode code1 = InvitationCode.builder().inviteCode("C1").inviteType(InviteType.GeneralClanInvite).isActive(true).build();
        InvitationCode code2 = InvitationCode.builder().inviteCode("C2").inviteType(InviteType.GeneralClanInvite).isActive(true).build();

        when(invitationCodeRepository.findAllByInviteTypeAndIsActiveTrueOrderByCreatedAtDesc(InviteType.GeneralClanInvite))
                .thenReturn(List.of(code1, code2));

        List<InviteCodeResponse> results = invitationService.getActiveGeneralInvites();

        assertEquals(2, results.size());
        assertEquals("C1", results.get(0).getInviteCode());
        assertEquals("C2", results.get(1).getInviteCode());
    }

    @Test
    @DisplayName("Get invite by code should return details if valid, throw BadRequest if expired")
    void getInviteByCode_Validation() {
        String activeCode = "ACTIVE_CODE";
        InvitationCode activeEntity = InvitationCode.builder()
                .inviteCode(activeCode)
                .isActive(true)
                .expiresAt(Instant.now().plus(5, ChronoUnit.DAYS))
                .build();

        when(invitationCodeRepository.findByInviteCodeAndIsActiveTrue(activeCode)).thenReturn(Optional.of(activeEntity));
        InviteCodeResponse response = invitationService.getInviteByCode(activeCode);
        assertNotNull(response);
        assertEquals(activeCode, response.getInviteCode());

        // Test expired code
        String expiredCode = "EXPIRED_CODE";
        InvitationCode expiredEntity = InvitationCode.builder()
                .inviteCode(expiredCode)
                .isActive(true)
                .expiresAt(Instant.now().minus(1, ChronoUnit.DAYS))
                .build();

        when(invitationCodeRepository.findByInviteCodeAndIsActiveTrue(expiredCode)).thenReturn(Optional.of(expiredEntity));
        assertThrows(BadRequestException.class, () -> invitationService.getInviteByCode(expiredCode));
    }
}

