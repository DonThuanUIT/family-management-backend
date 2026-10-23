package com.familyfund.genealogy.internal.service;

import com.familyfund.core.exception.BadRequestException;
import com.familyfund.core.exception.DuplicateResourceException;
import com.familyfund.core.exception.InvalidGenerationException;
import com.familyfund.core.exception.ResourceNotFoundException;
import com.familyfund.core.security.CurrentUserProvider;
import com.familyfund.core.security.SystemRoles;
import com.familyfund.core.tenant.TenantContext;
import com.familyfund.genealogy.internal.dto.request.CreateMemberRequest;
import com.familyfund.genealogy.internal.dto.response.MemberResponse;
import com.familyfund.genealogy.internal.entity.Clan;
import com.familyfund.genealogy.internal.entity.FamilyBranch;
import com.familyfund.genealogy.internal.entity.Member;
import com.familyfund.genealogy.internal.mapper.MemberMapper;
import com.familyfund.genealogy.internal.repository.ClanRepository;
import com.familyfund.genealogy.internal.repository.FamilyBranchRepository;
import com.familyfund.genealogy.internal.repository.MemberRepository;
import com.familyfund.genealogy.internal.util.MemberIdGenerator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.access.AccessDeniedException;

import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link MemberServiceImpl#createMember(String, CreateMemberRequest)} (US-03).
 * The branch is passed separately because it comes from the request path
 * (POST /api/v1/branches/{branchId}/members).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MemberServiceImplTest {

    private static final String CLAN = "CLAN_DEFAULT";
    private static final String MY_BRANCH = "BR_MINE";
    private static final String OTHER_BRANCH = "BR_OTHER";
    private static final String HEAD_MEMBER_ID = "MEMHEAD0000000001";

    @Mock
    private MemberRepository memberRepository;
    @Mock
    private FamilyBranchRepository branchRepository;
    @Mock
    private ClanRepository clanRepository;
    @Mock
    private CurrentUserProvider currentUserProvider;
    @Spy
    private MemberMapper memberMapper = Mappers.getMapper(MemberMapper.class);
    @Spy
    private MemberIdGenerator memberIdGenerator = new MemberIdGenerator();

    @InjectMocks
    private MemberServiceImpl memberService;

    @BeforeEach
    void setUp() {
        TenantContext.setClanId(CLAN);
        when(clanRepository.findByIdForUpdate(CLAN)).thenReturn(Optional.of(Clan.builder().clanId(CLAN).build()));
        when(branchRepository.findByClanIdAndBranchId(CLAN, MY_BRANCH)).thenReturn(Optional.of(branch(MY_BRANCH)));
        when(branchRepository.findByClanIdAndBranchId(CLAN, OTHER_BRANCH)).thenReturn(Optional.of(branch(OTHER_BRANCH)));
        when(branchRepository.existsByClanIdAndBranchIdAndRepresentative_MemberId(CLAN, MY_BRANCH, HEAD_MEMBER_ID)).thenReturn(true);
        when(memberRepository.existsByMemberId(anyString())).thenReturn(false);
        when(memberRepository.saveAndFlush(any(Member.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private static FamilyBranch branch(String id) {
        return FamilyBranch.builder().branchId(id).clanId(CLAN).branchName("Chi " + id).generationLevel(2).build();
    }

    /** Full, valid profile payload. The branch is intentionally absent: it is a path variable. */
    private static CreateMemberRequest request() {
        return CreateMemberRequest.builder()
                .fullName("  Nguyen Van An  ")
                .dateOfBirth(LocalDate.of(1990, 5, 20))
                .gender("Male")
                .generation(3)
                .build();
    }

    private void actAs(String... roles) {
        for (String role : roles) {
            when(currentUserProvider.hasAuthority(role)).thenReturn(true);
        }
    }

    @Nested
    @DisplayName("Role-based branch scope")
    class Authorization {

        @Test
        @DisplayName("CLAN_HEAD can add to any branch")
        void clanHead_AnyBranch() {
            actAs(SystemRoles.CLAN_HEAD);
            MemberResponse response = memberService.createMember(OTHER_BRANCH, request());
            assertEquals(OTHER_BRANCH, response.branchId());
        }

        @Test
        @DisplayName("ADMIN can add to any branch")
        void admin_AnyBranch() {
            actAs(SystemRoles.ADMIN);
            MemberResponse response = memberService.createMember(OTHER_BRANCH, request());
            assertEquals(OTHER_BRANCH, response.branchId());
        }

        @Test
        @DisplayName("BRANCH_HEAD can add to the branch they represent")
        void branchHead_OwnBranch() {
            actAs(SystemRoles.BRANCH_HEAD);
            when(currentUserProvider.currentMemberId()).thenReturn(Optional.of(HEAD_MEMBER_ID));
            MemberResponse response = memberService.createMember(MY_BRANCH, request());
            assertEquals(MY_BRANCH, response.branchId());
        }

        @Test
        @DisplayName("BRANCH_HEAD adding to another branch is denied (403) and nothing is saved")
        void branchHead_OtherBranch_Denied() {
            actAs(SystemRoles.BRANCH_HEAD);
            when(currentUserProvider.currentMemberId()).thenReturn(Optional.of(HEAD_MEMBER_ID));
            assertThrows(AccessDeniedException.class, () -> memberService.createMember(OTHER_BRANCH, request()));
            verify(memberRepository, never()).saveAndFlush(any());
        }

        @Test
        @DisplayName("BRANCH_HEAD without a linked member profile is denied (403)")
        void branchHead_Unlinked_Denied() {
            actAs(SystemRoles.BRANCH_HEAD);
            when(currentUserProvider.currentMemberId()).thenReturn(Optional.empty());
            assertThrows(AccessDeniedException.class, () -> memberService.createMember(MY_BRANCH, request()));
            verify(memberRepository, never()).saveAndFlush(any());
        }

        @Test
        @DisplayName("Caller with no manager role (e.g. MEMBER) is denied (403)")
        void member_Denied() {
            actAs(SystemRoles.MEMBER);
            assertThrows(AccessDeniedException.class, () -> memberService.createMember(MY_BRANCH, request()));
            verify(memberRepository, never()).saveAndFlush(any());
        }
    }

    @Nested
    @DisplayName("Business rules")
    class BusinessRules {

        @BeforeEach
        void asClanHead() {
            actAs(SystemRoles.CLAN_HEAD);
        }

        @Test
        @DisplayName("Saved member is a placeholder with trimmed name and default status, under a clan lock")
        void createMember_SetsPlaceholderAndDefaults() {
            MemberResponse response = memberService.createMember(MY_BRANCH, request());

            ArgumentCaptor<Member> captor = ArgumentCaptor.forClass(Member.class);
            verify(memberRepository).saveAndFlush(captor.capture());
            Member saved = captor.getValue();

            assertTrue(saved.getIsPlaceholder());
            assertEquals("Nguyen Van An", saved.getFullName());
            assertEquals(Member.STATUS_LIVING, saved.getStatus());
            assertTrue(saved.getMemberId().startsWith("MEM") && saved.getMemberId().length() <= 20);
            assertEquals(MY_BRANCH, response.branchId());
            assertTrue(response.isPlaceholder());
            verify(clanRepository).findByIdForUpdate(CLAN);
        }

        @Test
        @DisplayName("Branch id from the path is trimmed before it is looked up and persisted")
        void createMember_TrimsBranchId() {
            when(branchRepository.findByClanIdAndBranchId(CLAN, MY_BRANCH)).thenReturn(Optional.of(branch(MY_BRANCH)));

            MemberResponse response = memberService.createMember("  " + MY_BRANCH + "  ", request());

            assertEquals(MY_BRANCH, response.branchId());
            verify(branchRepository).findByClanIdAndBranchId(CLAN, MY_BRANCH);
        }

        @Test
        @DisplayName("Blank branch id in the path is rejected with 400 before anything is loaded")
        void blankBranchId_BadRequest() {
            assertThrows(BadRequestException.class, () -> memberService.createMember("   ", request()));
            assertThrows(BadRequestException.class, () -> memberService.createMember(null, request()));
            verify(branchRepository, never()).findByClanIdAndBranchId(anyString(), anyString());
            verify(memberRepository, never()).saveAndFlush(any());
        }

        @Test
        @DisplayName("Branch id longer than VARCHAR(20) is rejected with 400")
        void tooLongBranchId_BadRequest() {
            assertThrows(BadRequestException.class, () -> memberService.createMember("B".repeat(21), request()));
            verify(memberRepository, never()).saveAndFlush(any());
        }

        @Test
        @DisplayName("Missing branch returns 404 and nothing is saved")
        void missingBranch_NotFound() {
            when(branchRepository.findByClanIdAndBranchId(CLAN, "BR_NOPE")).thenReturn(Optional.empty());
            assertThrows(ResourceNotFoundException.class, () -> memberService.createMember("BR_NOPE", request()));
            verify(memberRepository, never()).saveAndFlush(any());
        }

        @Test
        @DisplayName("Generation below branch level is rejected")
        void generationTooLow_Rejected() {
            CreateMemberRequest req = CreateMemberRequest.builder()
                    .fullName("Ancestor").gender("Male").generation(1).build();
            assertThrows(InvalidGenerationException.class, () -> memberService.createMember(MY_BRANCH, req));
            verify(memberRepository, never()).saveAndFlush(any());
        }

        @Test
        @DisplayName("Duplicate profile (same name/gender/generation/DOB in clan) is rejected with 409")
        void duplicateProfile_Rejected() {
            when(memberRepository.existsByBranch_ClanIdAndFullNameIgnoreCaseAndGenderAndGenerationAndDateOfBirth(
                    CLAN, "Nguyen Van An", "Male", 3, LocalDate.of(1990, 5, 20))).thenReturn(true);
            assertThrows(DuplicateResourceException.class, () -> memberService.createMember(MY_BRANCH, request()));
            verify(memberRepository, never()).saveAndFlush(any());
        }

        @Test
        @DisplayName("Duplicate check is clan-wide, not branch-wide: a second branch is rejected too")
        void duplicateProfile_AcrossBranches_Rejected() {
            when(memberRepository.existsByBranch_ClanIdAndFullNameIgnoreCaseAndGenderAndGenerationAndDateOfBirth(
                    CLAN, "Nguyen Van An", "Male", 3, LocalDate.of(1990, 5, 20))).thenReturn(true);
            assertThrows(DuplicateResourceException.class, () -> memberService.createMember(OTHER_BRANCH, request()));
            verify(memberRepository, never()).saveAndFlush(any());
        }

        @Test
        @DisplayName("Duplicate check uses the IS NULL variant when date of birth is absent")
        void duplicateProfile_NullDob_Rejected() {
            CreateMemberRequest req = CreateMemberRequest.builder()
                    .fullName("Nguyen Van An").gender("Male").generation(3).build();
            when(memberRepository.existsByBranch_ClanIdAndFullNameIgnoreCaseAndGenderAndGenerationAndDateOfBirthIsNull(
                    CLAN, "Nguyen Van An", "Male", 3)).thenReturn(true);
            assertThrows(DuplicateResourceException.class, () -> memberService.createMember(MY_BRANCH, req));
            verify(memberRepository, never()).saveAndFlush(any());
        }

        @Test
        @DisplayName("ID generation gives up after bounded collisions and nothing is saved")
        void idCollisions_GiveUp() {
            when(memberRepository.existsByMemberId(anyString())).thenReturn(true);
            assertThrows(IllegalStateException.class, () -> memberService.createMember(MY_BRANCH, request()));
            verify(memberRepository, never()).saveAndFlush(any());
        }

        @Test
        @DisplayName("Branch from another clan is invisible (404) - tenant isolation")
        void otherClanBranch_NotFound() {
            TenantContext.setClanId("CLAN_OTHER");
            when(clanRepository.findByIdForUpdate("CLAN_OTHER")).thenReturn(Optional.of(Clan.builder().clanId("CLAN_OTHER").build()));
            when(branchRepository.findByClanIdAndBranchId(eq("CLAN_OTHER"), anyString())).thenReturn(Optional.empty());
            assertThrows(ResourceNotFoundException.class, () -> memberService.createMember(MY_BRANCH, request()));
        }
    }
}
