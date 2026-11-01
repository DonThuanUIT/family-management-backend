package com.familyfund.event.internal.mapper;

import com.familyfund.event.api.dto.RegistrationSummaryDto;
import com.familyfund.event.internal.dto.response.RegistrationMemberDetailResponse;
import com.familyfund.event.internal.dto.response.RegistrationResponse;
import com.familyfund.event.internal.dto.response.RegistrationSummaryResponse;
import com.familyfund.event.internal.entity.FamilyRegistration;
import com.familyfund.event.internal.entity.RegistrationMemberDetail;
import com.familyfund.event.internal.repository.RegistrationAggregateProjection;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.ReportingPolicy;

import java.util.List;

/**
 * MapStruct mapper for converting between registration entities, projections, and DTOs (US-06).
 */
@Mapper(componentModel = MappingConstants.ComponentModel.SPRING, unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface RegistrationMapper {

    @Mapping(target = "representativeMemberName", ignore = true)
    @Mapping(target = "attendingMembers", ignore = true)
    RegistrationResponse toResponse(FamilyRegistration registration);

    @Mapping(target = "representativeMemberName", source = "representativeName")
    @Mapping(target = "attendingMembers", source = "memberDetailResponses")
    RegistrationResponse toResponse(
            FamilyRegistration registration,
            String representativeName,
            List<RegistrationMemberDetailResponse> memberDetailResponses);

    List<RegistrationResponse> toResponseList(List<FamilyRegistration> registrations);

    @Mapping(target = "fullName", ignore = true)
    RegistrationMemberDetailResponse toMemberDetailResponse(RegistrationMemberDetail detail);

    @Mapping(target = "fullName", source = "fullName")
    RegistrationMemberDetailResponse toMemberDetailResponse(RegistrationMemberDetail detail, String fullName);

    List<RegistrationMemberDetailResponse> toMemberDetailResponseList(List<RegistrationMemberDetail> details);

    default RegistrationSummaryResponse toSummaryResponse(String eventId, RegistrationAggregateProjection projection) {
        if (projection == null) {
            return new RegistrationSummaryResponse(eventId, 0, 0, 0, 0);
        }
        return new RegistrationSummaryResponse(
                eventId,
                projection.getTotalRegistrations(),
                projection.getTotalAttendees(),
                projection.getTotalVegetarianMeals(),
                projection.getTotalSavoryMeals()
        );
    }

    default RegistrationSummaryDto toSummaryDto(String eventId, RegistrationAggregateProjection projection) {
        if (projection == null) {
            return new RegistrationSummaryDto(eventId, 0, 0, 0, 0);
        }
        return new RegistrationSummaryDto(
                eventId,
                projection.getTotalRegistrations(),
                projection.getTotalAttendees(),
                projection.getTotalVegetarianMeals(),
                projection.getTotalSavoryMeals()
        );
    }
}
