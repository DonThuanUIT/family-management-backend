package com.familyfund.genealogy.internal.mapper;

import com.familyfund.genealogy.internal.dto.response.MemberResponse;
import com.familyfund.genealogy.internal.entity.FamilyBranch;
import com.familyfund.genealogy.internal.entity.Member;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Verifies that Member -> MemberResponse resolves branchId/branchName/clanId
 * through the members.branch_id -> family_branches relation (US-03).
 */
class MemberMapperTest {

    private final MemberMapper mapper = Mappers.getMapper(MemberMapper.class);

    @Test
    @DisplayName("toResponse maps scalar columns and resolves branchName/clanId from the parent branch")
    void toResponse_ShouldMapAllFields() {
        FamilyBranch branch = FamilyBranch.builder()
                .branchId("BR_CHILD_1")
                .clanId("CLAN_DEFAULT")
                .branchName("Chi Hai")
                .generationLevel(2)
                .build();

        Member member = Member.builder()
                .memberId("MEMK4F8ZQ1M7XB3QP")
                .fullName("Nguyen Van An")
                .gender("Male")
                .generation(3)
                .status("Deceased")
                .deathDateLunar("15/07 AL")
                .branch(branch)
                .isPlaceholder(false)
                .createdAt(Instant.parse("2026-01-01T00:00:00Z"))
                .build();

        MemberResponse response = mapper.toResponse(member);

        assertNotNull(response);
        assertEquals("MEMK4F8ZQ1M7XB3QP", response.memberId());
        assertEquals("Nguyen Van An", response.fullName());
        assertEquals("Male", response.gender());
        assertEquals(3, response.generation());
        assertEquals("Deceased", response.status());
        assertEquals("15/07 AL", response.deathDateLunar());
        assertEquals("BR_CHILD_1", response.branchId());
        assertEquals("Chi Hai", response.branchName());
        assertEquals("CLAN_DEFAULT", response.clanId());
        assertEquals(false, response.isPlaceholder());
        assertEquals(Instant.parse("2026-01-01T00:00:00Z"), response.createdAt());
    }
}
