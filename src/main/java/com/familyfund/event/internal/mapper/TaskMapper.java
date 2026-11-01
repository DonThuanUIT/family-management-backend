package com.familyfund.event.internal.mapper;

import com.familyfund.event.internal.dto.response.TaskResponse;
import com.familyfund.event.internal.entity.EventTask;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.ReportingPolicy;

import java.util.List;

/**
 * MapStruct mapper for converting between `EventTask` and `TaskResponse`.
 */
@Mapper(componentModel = MappingConstants.ComponentModel.SPRING, unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface TaskMapper {

    @Mapping(target = "assignedBranchName", ignore = true)
    @Mapping(target = "assignedMemberName", ignore = true)
    TaskResponse toResponse(EventTask task);

    List<TaskResponse> toResponseList(List<EventTask> tasks);

    @Mapping(target = "assignedBranchName", source = "branchName")
    @Mapping(target = "assignedMemberName", source = "memberName")
    TaskResponse toResponse(EventTask task, String branchName, String memberName);
}

