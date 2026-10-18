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

    @Mapping(target = "clanId", source = "clanId")
    @Mapping(target = "parentBranchId", source = "parentBranch.branchId")
    @Mapping(target = "representativeId", source = "representative.memberId")
    @Mapping(target = "representativeName", source = "representative.fullName")
    BranchDetailResponse toDetailResponse(FamilyBranch entity);

    @Mapping(target = "branchId", source = "branch.branchId")
    @Mapping(target = "clanId", source = "branch.clanId")
    @Mapping(target = "branchName", source = "branch.branchName")
    @Mapping(target = "clanName", source = "branch.clanName")
    @Mapping(target = "generationLevel", source = "branch.generationLevel")
    @Mapping(target = "parentBranchId", source = "branch.parentBranch.branchId")
    @Mapping(target = "representativeId", source = "branch.representative.memberId")
    @Mapping(target = "representativeName", source = "branch.representative.fullName")
    @Mapping(target = "memberCount", source = "memberCount")
    @Mapping(target = "children", source = "children")
    BranchTreeNodeResponse toTreeNodeResponse(FamilyBranch branch, int memberCount, List<BranchTreeNodeResponse> children);
}
