package com.familyfund.genealogy.internal.mapper;

import com.familyfund.genealogy.internal.dto.response.MemberResponse;
import com.familyfund.genealogy.internal.entity.Member;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING, unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface MemberMapper {

    @Mapping(target = "memberId", source = "entity.memberId")
    @Mapping(target = "fullName", source = "entity.fullName")
    @Mapping(target = "dateOfBirth", source = "entity.dateOfBirth")
    @Mapping(target = "gender", source = "entity.gender")
    @Mapping(target = "generation", source = "entity.generation")
    @Mapping(target = "status", source = "entity.status")
    @Mapping(target = "deathDateLunar", source = "entity.deathDateLunar")
    @Mapping(target = "branchId", source = "entity.branch.branchId")
    @Mapping(target = "branchName", source = "entity.branch.branchName")
    @Mapping(target = "clanId", source = "entity.branch.clanId")
    @Mapping(target = "isPlaceholder", source = "entity.isPlaceholder")
    @Mapping(target = "createdAt", source = "entity.createdAt")
    MemberResponse toResponse(Member entity);
}
