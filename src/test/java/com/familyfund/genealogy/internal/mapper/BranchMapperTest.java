package com.familyfund.genealogy.internal.mapper;

import com.familyfund.genealogy.internal.dto.response.BranchDetailResponse;
import com.familyfund.genealogy.internal.dto.response.BranchTreeNodeResponse;
import com.familyfund.genealogy.internal.entity.FamilyBranch;
import com.familyfund.genealogy.internal.entity.Member;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BranchMapperTest {

    private final BranchMapper branchMapper = Mappers.getMapper(BranchMapper.class);

    @Test
    @DisplayName("Maps FamilyBranch to BranchDetailResponse with null relations")
    void testToDetailResponse_WithNullRelations() {
        FamilyBranch branch = FamilyBranch.builder()
                .branchId("BR_ROOT")
                .clanId("CLAN_DEFAULT")
                .clanName("Nguyen Clan")
                .branchName("Dai Ton")
                .generationLevel(1)
                .parentBranch(null)
                .representative(null)
                .description("Root branch")
                .createdAt(Instant.now())
                .build();

        BranchDetailResponse response = branchMapper.toDetailResponse(branch);

        assertNotNull(response);
        assertEquals("BR_ROOT", response.branchId());
        assertEquals("CLAN_DEFAULT", response.clanId());
        assertEquals("Nguyen Clan", response.clanName());
        assertEquals("Dai Ton", response.branchName());
        assertEquals(1, response.generationLevel());
        assertNull(response.parentBranchId());
        assertNull(response.representativeId());
        assertNull(response.representativeName());
        assertEquals("Root branch", response.description());
    }

    @Test
    @DisplayName("Maps FamilyBranch to BranchDetailResponse with parent and representative")
    void testToDetailResponse_WithRelations() {
        FamilyBranch parent = FamilyBranch.builder()
                .branchId("BR_ROOT")
                .clanId("CLAN_DEFAULT")
                .build();

        Member representative = Member.builder()
                .memberId("MEM_001")
                .fullName("Nguyen Van A")
                .build();

        FamilyBranch branch = FamilyBranch.builder()
                .branchId("BR_CHILD")
                .clanId("CLAN_DEFAULT")
                .clanName("Nguyen Clan")
                .branchName("Chi Hai")
                .generationLevel(2)
                .parentBranch(parent)
                .representative(representative)
                .description("Second branch")
                .createdAt(Instant.now())
                .build();

        BranchDetailResponse response = branchMapper.toDetailResponse(branch);

        assertNotNull(response);
        assertEquals("BR_CHILD", response.branchId());
        assertEquals("CLAN_DEFAULT", response.clanId());
        assertEquals("BR_ROOT", response.parentBranchId());
        assertEquals("MEM_001", response.representativeId());
        assertEquals("Nguyen Van A", response.representativeName());
    }

    @Test
    @DisplayName("Maps FamilyBranch to BranchTreeNodeResponse")
    void testToTreeNodeResponse() {
        FamilyBranch branch = FamilyBranch.builder()
                .branchId("BR_ROOT")
                .clanId("CLAN_DEFAULT")
                .branchName("Dai Ton")
                .clanName("Nguyen Clan")
                .generationLevel(1)
                .build();

        BranchTreeNodeResponse childNode = BranchTreeNodeResponse.builder()
                .branchId("BR_CHILD")
                .clanId("CLAN_DEFAULT")
                .branchName("Chi Hai")
                .clanName("Nguyen Clan")
                .generationLevel(2)
                .parentBranchId("BR_ROOT")
                .memberCount(5)
                .build();

        BranchTreeNodeResponse treeNode = branchMapper.toTreeNodeResponse(branch, 10, List.of(childNode));

        assertNotNull(treeNode);
        assertEquals("BR_ROOT", treeNode.branchId());
        assertEquals("CLAN_DEFAULT", treeNode.clanId());
        assertEquals(10, treeNode.memberCount());
        assertEquals(1, treeNode.children().size());
        assertEquals("BR_CHILD", treeNode.children().get(0).branchId());
    }
}
