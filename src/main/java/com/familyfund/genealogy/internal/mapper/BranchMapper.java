package com.familyfund.genealogy.internal.mapper;

import com.familyfund.genealogy.internal.dto.response.BranchDetailResponse;
import com.familyfund.genealogy.internal.dto.response.BranchTreeNodeResponse;
import com.familyfund.genealogy.internal.entity.FamilyBranch;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.ReportingPolicy;

import java.util.List;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING, unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface BranchMapper {

    @Mapping(target = "clanId", source = "entity.clanId")
    @Mapping(target = "clanName", source = "clanName")
    @Mapping(target = "parentBranchId", source = "entity.parentBranch.branchId")
    @Mapping(target = "representativeId", source = "entity.representative.memberId")
    @Mapping(target = "representativeName", source = "entity.representative.fullName")
    BranchDetailResponse toDetailResponse(FamilyBranch entity, String clanName);

    @Mapping(target = "branchId", source = "branch.branchId")
    @Mapping(target = "clanId", source = "branch.clanId")
    @Mapping(target = "clanName", source = "clanName")
    @Mapping(target = "branchName", source = "branch.branchName")
    @Mapping(target = "generationLevel", source = "branch.generationLevel")
    @Mapping(target = "parentBranchId", source = "branch.parentBranch.branchId")
    @Mapping(target = "representativeId", source = "branch.representative.memberId")
    @Mapping(target = "representativeName", source = "branch.representative.fullName")
    @Mapping(target = "memberCount", source = "memberCount")
    @Mapping(target = "children", source = "children")
    BranchTreeNodeResponse toTreeNodeResponse(FamilyBranch branch, String clanName, int memberCount, List<BranchTreeNodeResponse> children);
}
