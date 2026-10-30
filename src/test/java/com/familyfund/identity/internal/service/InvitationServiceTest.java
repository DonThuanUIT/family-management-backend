package com.familyfund.identity.internal.service;

import com.familyfund.core.exception.BadRequestException;
import com.familyfund.core.exception.ResourceNotFoundException;
import com.familyfund.core.tenant.TenantContext;
import com.familyfund.genealogy.api.GenealogyQueryService;
import com.familyfund.genealogy.api.OnboardingBranchTreeNode;
import com.familyfund.identity.api.dto.CreateGeneralInviteRequest;
import com.familyfund.identity.api.dto.InviteCodeResponse;
import com.familyfund.identity.internal.entity.InvitationCode;
import com.familyfund.identity.internal.entity.InviteType;
import com.familyfund.identity.internal.repository.InvitationCodeRepository;
import com.familyfund.identity.internal.util.InviteCodeGenerator;
import org.junit.jupiter.api.AfterEach;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InvitationServiceTest {

    private static final String TEST_CLAN_ID = "CLAN_01";
    private static final String OTHER_CLAN_ID = "CLAN_02";

    @Mock
    private InvitationCodeRepository invitationCodeRepository;

    @Mock
    private GenealogyQueryService genealogyQueryService;

    @Mock
    private InviteCodeGenerator inviteCodeGenerator;

    @InjectMocks
    private InvitationServiceImpl invitationService;

    private String testAccountId;

    @BeforeEach
    void setUp() {
        testAccountId = "ACC_ADMIN_01";
        TenantContext.setClanId(TEST_CLAN_ID);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("Create general invite with valid data and no target branch should assign clanId and succeed")
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
        assertEquals(TEST_CLAN_ID, response.getClanId());
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
        assertEquals(TEST_CLAN_ID, savedEntity.getClanId());
        assertEquals(InviteType.GeneralClanInvite, savedEntity.getInviteType());
    }

    @Test
    @DisplayName("Create general invite with valid target branch should verify branch within current clanId and succeed")
    void createGeneralClanInvite_WithValidBranch_ShouldSucceed() {
        String branchId = "BRANCH_ROOT";
        CreateGeneralInviteRequest request = CreateGeneralInviteRequest.builder()
                .targetBranchId(branchId)
                .maxUses(100)
                .build();

        when(genealogyQueryService.existsBranch(TEST_CLAN_ID, branchId)).thenReturn(true);
        when(inviteCodeGenerator.generateCode()).thenReturn("GEN_CODE_1");
        when(invitationCodeRepository.existsByInviteCode("GEN_CODE_1")).thenReturn(false);
        when(inviteCodeGenerator.buildShareableUrl("GEN_CODE_1")).thenReturn("http://localhost:3000/join?code=GEN_CODE_1");
        when(invitationCodeRepository.save(any(InvitationCode.class))).thenAnswer(invocation -> invocation.getArgument(0));

        InviteCodeResponse response = invitationService.createGeneralClanInvite(request, testAccountId);

        assertNotNull(response);
        assertEquals(TEST_CLAN_ID, response.getClanId());
        assertEquals(branchId, response.getTargetBranchId());
        verify(genealogyQueryService).existsBranch(TEST_CLAN_ID, branchId);
    }

    @Test
    @DisplayName("Create general invite with branch belonging to another clan should fail tenant verification")
    void createGeneralClanInvite_BranchFromAnotherClan_ShouldThrowException() {
        String otherClanBranchId = "BRANCH_OTHER_CLAN";
        CreateGeneralInviteRequest request = CreateGeneralInviteRequest.builder()
                .targetBranchId(otherClanBranchId)
                .build();

        when(genealogyQueryService.existsBranch(TEST_CLAN_ID, otherClanBranchId)).thenReturn(false);

        assertThrows(ResourceNotFoundException.class,
                () -> invitationService.createGeneralClanInvite(request, testAccountId));

        verify(genealogyQueryService).existsBranch(TEST_CLAN_ID, otherClanBranchId);
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
    @DisplayName("Deactivate invite code scoped to current clan should succeed")
    void deactivateInviteCode_ExistingCodeInCurrentClan_ShouldSetIsActiveFalse() {
        String code = "INVITE_123";
        InvitationCode existingInvitation = InvitationCode.builder()
                .inviteCode(code)
                .clanId(TEST_CLAN_ID)
                .isActive(true)
                .build();

        when(invitationCodeRepository.findByClanIdAndInviteCode(TEST_CLAN_ID, code)).thenReturn(Optional.of(existingInvitation));
        when(invitationCodeRepository.save(any(InvitationCode.class))).thenAnswer(invocation -> invocation.getArgument(0));

        invitationService.deactivateInviteCode(code, testAccountId);

        assertFalse(existingInvitation.getIsActive());
        verify(invitationCodeRepository).save(existingInvitation);
    }

    @Test
    @DisplayName("Deactivate invite code belonging to another clan should throw ResourceNotFoundException (prevent IDOR)")
    void deactivateInviteCode_CodeFromAnotherClan_ShouldThrowNotFound() {
        String code = "INVITE_OTHER_CLAN";
        when(invitationCodeRepository.findByClanIdAndInviteCode(TEST_CLAN_ID, code)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> invitationService.deactivateInviteCode(code, testAccountId));

        verify(invitationCodeRepository, never()).save(any());
    }

    @Test
    @DisplayName("Get active general invites should query repository strictly for current clan")
    void getActiveGeneralInvites_ShouldReturnListScopedToClan() {
        InvitationCode code1 = InvitationCode.builder()
                .inviteCode("C1")
                .clanId(TEST_CLAN_ID)
                .inviteType(InviteType.GeneralClanInvite)
                .isActive(true)
                .build();

        InvitationCode code2 = InvitationCode.builder()
                .inviteCode("C2")
                .clanId(TEST_CLAN_ID)
                .inviteType(InviteType.GeneralClanInvite)
                .isActive(true)
                .build();

        when(invitationCodeRepository.findAllByClanIdAndInviteTypeAndIsActiveTrueOrderByCreatedAtDesc(TEST_CLAN_ID, InviteType.GeneralClanInvite))
                .thenReturn(List.of(code1, code2));

        List<InviteCodeResponse> results = invitationService.getActiveGeneralInvites();

        assertEquals(2, results.size());
        assertEquals("C1", results.get(0).getInviteCode());
        assertEquals(TEST_CLAN_ID, results.get(0).getClanId());
        assertEquals("C2", results.get(1).getInviteCode());
        assertEquals(TEST_CLAN_ID, results.get(1).getClanId());
    }

    @Test
    @DisplayName("Get invite by code should return details with clanId if valid, throw BadRequest if expired")
    void getInviteByCode_Validation() {
        String activeCode = "ACTIVE_CODE";
        InvitationCode activeEntity = InvitationCode.builder()
                .inviteCode(activeCode)
                .clanId(TEST_CLAN_ID)
                .isActive(true)
                .expiresAt(Instant.now().plus(5, ChronoUnit.DAYS))
                .build();

        when(invitationCodeRepository.findByInviteCodeAndIsActiveTrue(activeCode)).thenReturn(Optional.of(activeEntity));
        InviteCodeResponse response = invitationService.getInviteByCode(activeCode);
        assertNotNull(response);
        assertEquals(activeCode, response.getInviteCode());
        assertEquals(TEST_CLAN_ID, response.getClanId());

        // Test expired code
        String expiredCode = "EXPIRED_CODE";
        InvitationCode expiredEntity = InvitationCode.builder()
                .inviteCode(expiredCode)
                .clanId(TEST_CLAN_ID)
                .isActive(true)
                .expiresAt(Instant.now().minus(1, ChronoUnit.DAYS))
                .build();

        when(invitationCodeRepository.findByInviteCodeAndIsActiveTrue(expiredCode)).thenReturn(Optional.of(expiredEntity));
        assertThrows(BadRequestException.class, () -> invitationService.getInviteByCode(expiredCode));
    }

    @Test
    @DisplayName("Get invite by code when max uses exceeded should throw BadRequestException")
    void getInviteByCode_MaxUsesExceeded_ThrowsBadRequestException() {
        String code = "EXCEEDED_CODE";
        InvitationCode entity = InvitationCode.builder()
                .inviteCode(code)
                .clanId(TEST_CLAN_ID)
                .isActive(true)
                .maxUses(5)
                .usedCount(5)
                .expiresAt(Instant.now().plus(5, ChronoUnit.DAYS))
                .build();

        when(invitationCodeRepository.findByInviteCodeAndIsActiveTrue(code)).thenReturn(Optional.of(entity));

        BadRequestException ex = assertThrows(BadRequestException.class, () -> invitationService.getInviteByCode(code));
        assertTrue(ex.getMessage().contains("hết lượt sử dụng"));
    }

    @Test
    @DisplayName("Get invite by code should enrich clanName and targetBranchName from GenealogyQueryService")
    void getInviteByCode_EnrichWithClanAndBranchName() {
        String code = "ENRICHED_CODE";
        InvitationCode entity = InvitationCode.builder()
                .inviteCode(code)
                .clanId(TEST_CLAN_ID)
                .targetBranchId("BR_01")
                .isActive(true)
                .maxUses(10)
                .usedCount(2)
                .expiresAt(Instant.now().plus(5, ChronoUnit.DAYS))
                .build();

        when(invitationCodeRepository.findByInviteCodeAndIsActiveTrue(code)).thenReturn(Optional.of(entity));
        when(genealogyQueryService.getClanName(TEST_CLAN_ID)).thenReturn(Optional.of("Dòng họ Nguyễn"));
        when(genealogyQueryService.getBranchName(TEST_CLAN_ID, "BR_01")).thenReturn(Optional.of("Chi Trưởng"));

        InviteCodeResponse response = invitationService.getInviteByCode(code);
        assertNotNull(response);
        assertEquals("Dòng họ Nguyễn", response.getClanName());
        assertEquals("Chi Trưởng", response.getTargetBranchName());
    }

    @Test
    @DisplayName("Get onboarding branch tree should validate invite and delegate to GenealogyQueryService")
    void getOnboardingBranchTree_Success() {
        String code = "TREE_CODE";
        InvitationCode entity = InvitationCode.builder()
                .inviteCode(code)
                .clanId(TEST_CLAN_ID)
                .targetBranchId("BR_01")
                .isActive(true)
                .maxUses(10)
                .usedCount(1)
                .expiresAt(Instant.now().plus(5, ChronoUnit.DAYS))
                .build();

        when(invitationCodeRepository.findByInviteCodeAndIsActiveTrue(code)).thenReturn(Optional.of(entity));

        OnboardingBranchTreeNode mockTree = OnboardingBranchTreeNode.builder()
                .branchId("BR_01")
                .clanId(TEST_CLAN_ID)
                .branchName("Chi Trưởng")
                .build();
        when(genealogyQueryService.getOnboardingBranchTree(TEST_CLAN_ID, "BR_01")).thenReturn(mockTree);

        OnboardingBranchTreeNode result = invitationService.getOnboardingBranchTree(code);
        assertNotNull(result);
        assertEquals("BR_01", result.branchId());
        assertEquals("Chi Trưởng", result.branchName());
    }
}
