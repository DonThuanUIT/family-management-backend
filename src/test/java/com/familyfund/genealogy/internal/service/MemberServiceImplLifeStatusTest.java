package com.familyfund.genealogy.internal.service;

import com.familyfund.core.exception.BadRequestException;
import com.familyfund.core.exception.ResourceNotFoundException;
import com.familyfund.core.security.CurrentUserProvider;
import com.familyfund.core.security.SystemRoles;
import com.familyfund.core.tenant.TenantContext;
import com.familyfund.genealogy.api.GenealogyQueryService;
import com.familyfund.genealogy.api.event.MemberLifeStatusChangedEvent;
import com.familyfund.genealogy.internal.dto.request.UpdateLifeStatusRequest;
import com.familyfund.genealogy.internal.dto.response.MemberResponse;
import com.familyfund.genealogy.internal.entity.FamilyBranch;
import com.familyfund.genealogy.internal.entity.Member;
import com.familyfund.genealogy.internal.mapper.MemberMapper;
import com.familyfund.genealogy.internal.repository.ClanRepository;
import com.familyfund.genealogy.internal.repository.FamilyBranchRepository;
import com.familyfund.genealogy.internal.repository.MemberPrivacySettingRepository;
import com.familyfund.genealogy.internal.repository.MemberRepository;
import com.familyfund.genealogy.internal.security.MemberPrivacyPolicy;
import com.familyfund.genealogy.internal.util.MemberIdGenerator;
import com.familyfund.genealogy.internal.util.VietnameseLunarCalendarConverter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.AccessDeniedException;

import java.time.LocalDate;
import java.util.Collections;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("US-27: MemberServiceImpl Life Status Tests")
class MemberServiceImplLifeStatusTest {

    private static final String CLAN_ID = "CLAN_01";
    private static final String BRANCH_ID = "BR_01";
    private static final String MEMBER_ID = "MEM_001";

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private FamilyBranchRepository branchRepository;

    @Mock
    private ClanRepository clanRepository;

    @Spy
    private MemberMapper memberMapper = Mappers.getMapper(MemberMapper.class);

    @Mock
    private MemberIdGenerator memberIdGenerator;

    @Mock
    private CurrentUserProvider currentUserProvider;

    @Mock
    private MemberPrivacySettingRepository privacySettingRepository;

    @Mock
    private MemberPrivacyPolicy memberPrivacyPolicy;

    @Mock
    private GenealogyQueryService genealogyQueryService;

    @Spy
    private VietnameseLunarCalendarConverter lunarCalendarConverter = new VietnameseLunarCalendarConverter();

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private MemberServiceImpl memberService;

    private FamilyBranch testBranch;
    private Member testMember;

    @BeforeEach
    void setUp() {
        TenantContext.setClanId(CLAN_ID);

        testBranch = FamilyBranch.builder()
                .branchId(BRANCH_ID)
                .clanId(CLAN_ID)
                .branchName("Chi 1 (Đông Kết)")
                .generationLevel(1)
                .build();

        testMember = Member.builder()
                .memberId(MEMBER_ID)
                .fullName("Nguyễn Văn An")
                .dateOfBirth(LocalDate.of(1950, 5, 20))
                .gender("Male")
                .generation(2)
                .status(Member.STATUS_LIVING)
                .branch(testBranch)
                .build();

        memberService = new MemberServiceImpl(
                memberRepository,
                branchRepository,
                clanRepository,
                memberMapper,
                memberIdGenerator,
                currentUserProvider,
                privacySettingRepository,
                memberPrivacyPolicy,
                genealogyQueryService,
                lunarCalendarConverter,
                eventPublisher
        );
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("Successfully updates member to Deceased, computes lunar anniversary, and publishes event")
    void updateLifeStatus_toDeceased_success() {
        when(currentUserProvider.hasAuthority(SystemRoles.CLAN_HEAD)).thenReturn(true);
        when(memberRepository.findById(MEMBER_ID)).thenReturn(Optional.of(testMember));
        when(memberRepository.save(any(Member.class))).thenAnswer(inv -> inv.getArgument(0));
        when(privacySettingRepository.findByMemberIdIn(any())).thenReturn(Collections.emptyList());
        when(memberPrivacyPolicy.applyPrivacy(any(), any(), any())).thenAnswer(inv -> inv.getArgument(0));

        LocalDate deathDate = LocalDate.of(2024, 3, 24);
        UpdateLifeStatusRequest request = new UpdateLifeStatusRequest(
                "Deceased",
                deathDate,
                "Khu lăng mộ tổ họ Nguyễn"
        );

        MemberResponse response = memberService.updateLifeStatus(MEMBER_ID, request);

        assertNotNull(response);
        assertEquals("Deceased", testMember.getStatus());
        assertEquals(deathDate, testMember.getDeathDate());
        assertEquals("15/02 Giáp Thìn", testMember.getDeathDateLunar());
        assertEquals("Khu lăng mộ tổ họ Nguyễn", testMember.getBurialPlace());

        ArgumentCaptor<MemberLifeStatusChangedEvent> eventCaptor = ArgumentCaptor.forClass(MemberLifeStatusChangedEvent.class);
        verify(eventPublisher, times(1)).publishEvent(eventCaptor.capture());

        MemberLifeStatusChangedEvent publishedEvent = eventCaptor.getValue();
        assertEquals(MEMBER_ID, publishedEvent.memberId());
        assertEquals(CLAN_ID, publishedEvent.clanId());
        assertEquals("Deceased", publishedEvent.status());
        assertEquals(deathDate, publishedEvent.deathDate());
        assertEquals("15/02 Giáp Thìn", publishedEvent.deathDateLunar());
        assertTrue(publishedEvent.isDeceased());
    }

    @Test
    @DisplayName("Successfully reverts Deceased member to Living and clears death dates")
    void updateLifeStatus_toLiving_success() {
        testMember.setStatus(Member.STATUS_DECEASED);
        testMember.setDeathDate(LocalDate.of(2024, 3, 24));
        testMember.setDeathDateLunar("15/02 Giáp Thìn");
        testMember.setBurialPlace("Khu lăng mộ");

        when(currentUserProvider.hasAuthority(SystemRoles.CLAN_HEAD)).thenReturn(true);
        when(memberRepository.findById(MEMBER_ID)).thenReturn(Optional.of(testMember));
        when(memberRepository.save(any(Member.class))).thenAnswer(inv -> inv.getArgument(0));
        when(privacySettingRepository.findByMemberIdIn(any())).thenReturn(Collections.emptyList());
        when(memberPrivacyPolicy.applyPrivacy(any(), any(), any())).thenAnswer(inv -> inv.getArgument(0));

        UpdateLifeStatusRequest request = new UpdateLifeStatusRequest("Living", null, null);

        MemberResponse response = memberService.updateLifeStatus(MEMBER_ID, request);

        assertNotNull(response);
        assertEquals("Living", testMember.getStatus());
        assertNull(testMember.getDeathDate());
        assertNull(testMember.getDeathDateLunar());
        assertNull(testMember.getBurialPlace());

        verify(eventPublisher, times(1)).publishEvent(any(MemberLifeStatusChangedEvent.class));
    }

    @Test
    @DisplayName("Throws BadRequestException when death date is in future")
    void updateLifeStatus_futureDeathDate_throwsBadRequest() {
        when(currentUserProvider.hasAuthority(SystemRoles.CLAN_HEAD)).thenReturn(true);
        when(memberRepository.findById(MEMBER_ID)).thenReturn(Optional.of(testMember));

        LocalDate futureDeathDate = LocalDate.now().plusDays(1);
        UpdateLifeStatusRequest request = new UpdateLifeStatusRequest("Deceased", futureDeathDate, null);

        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> memberService.updateLifeStatus(MEMBER_ID, request));
        assertTrue(ex.getMessage().contains("tương lai"));
    }

    @Test
    @DisplayName("Throws BadRequestException when death date is before date of birth")
    void updateLifeStatus_deathBeforeBirth_throwsBadRequest() {
        when(currentUserProvider.hasAuthority(SystemRoles.CLAN_HEAD)).thenReturn(true);
        when(memberRepository.findById(MEMBER_ID)).thenReturn(Optional.of(testMember));

        // Sinh 1950-05-20, mất 1949-01-01
        LocalDate deathDateBeforeBirth = LocalDate.of(1949, 1, 1);
        UpdateLifeStatusRequest request = new UpdateLifeStatusRequest("Deceased", deathDateBeforeBirth, null);

        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> memberService.updateLifeStatus(MEMBER_ID, request));
        assertTrue(ex.getMessage().contains("trước ngày sinh"));
    }

    @Test
    @DisplayName("Throws ResourceNotFoundException when member not found")
    void updateLifeStatus_memberNotFound_throwsNotFound() {
        when(memberRepository.findById("UNKNOWN")).thenReturn(Optional.empty());

        UpdateLifeStatusRequest request = new UpdateLifeStatusRequest("Living", null, null);

        assertThrows(ResourceNotFoundException.class,
                () -> memberService.updateLifeStatus("UNKNOWN", request));
    }

    @Test
    @DisplayName("Throws AccessDeniedException when branch head manages different branch")
    void updateLifeStatus_branchHeadUnauthorized_throwsAccessDenied() {
        when(currentUserProvider.hasAuthority(SystemRoles.CLAN_HEAD)).thenReturn(false);
        when(currentUserProvider.hasAuthority(SystemRoles.ADMIN)).thenReturn(false);
        when(currentUserProvider.hasAuthority(SystemRoles.BRANCH_HEAD)).thenReturn(true);
        when(currentUserProvider.currentMemberId()).thenReturn(Optional.of("MEM_OTHER_LEADER"));
        when(branchRepository.existsByClanIdAndBranchIdAndRepresentative_MemberId(CLAN_ID, BRANCH_ID, "MEM_OTHER_LEADER"))
                .thenReturn(false);

        when(memberRepository.findById(MEMBER_ID)).thenReturn(Optional.of(testMember));

        UpdateLifeStatusRequest request = new UpdateLifeStatusRequest("Living", null, null);

        assertThrows(AccessDeniedException.class,
                () -> memberService.updateLifeStatus(MEMBER_ID, request));
    }
}
