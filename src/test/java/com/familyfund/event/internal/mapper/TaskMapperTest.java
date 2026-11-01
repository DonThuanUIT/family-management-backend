package com.familyfund.event.internal.mapper;

import com.familyfund.event.api.dto.TaskStatus;
import com.familyfund.event.internal.dto.response.TaskResponse;
import com.familyfund.event.internal.entity.EventTask;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("TaskMapper MapStruct Test")
class TaskMapperTest {

    private TaskMapper taskMapper;

    @BeforeEach
    void setUp() {
        taskMapper = Mappers.getMapper(TaskMapper.class);
    }

    @Test
    @DisplayName("Maps EventTask entity to TaskResponse DTO")
    void testToResponse() {
        Instant now = Instant.now();
        EventTask task = EventTask.builder()
                .taskId("TSK_001")
                .eventId("EVT_001")
                .taskName("Chuẩn bị mâm ngũ quả")
                .details("5 loại trái cây tươi")
                .advanceBudget(BigDecimal.valueOf(1500000))
                .deadline(now)
                .status(TaskStatus.InProgress)
                .assignedBranchId("BR_CHI_2")
                .assignedMemberId("MEM_002")
                .createdBy("ACC_001")
                .build();

        TaskResponse response = taskMapper.toResponse(task);

        assertNotNull(response);
        assertEquals("TSK_001", response.taskId());
        assertEquals("EVT_001", response.eventId());
        assertEquals("Chuẩn bị mâm ngũ quả", response.taskName());
        assertEquals("5 loại trái cây tươi", response.details());
        assertEquals(BigDecimal.valueOf(1500000), response.advanceBudget());
        assertEquals(now, response.deadline());
        assertEquals(TaskStatus.InProgress, response.status());
        assertEquals("BR_CHI_2", response.assignedBranchId());
        assertEquals("MEM_002", response.assignedMemberId());
        assertEquals("ACC_001", response.createdBy());
        assertNull(response.assignedBranchName());
        assertNull(response.assignedMemberName());
    }

    @Test
    @DisplayName("Maps EventTask entity to TaskResponse DTO with enriched names")
    void testToResponseWithEnrichedNames() {
        Instant now = Instant.now();
        EventTask task = EventTask.builder()
                .taskId("TSK_002")
                .eventId("EVT_001")
                .taskName("Thuê âm thanh ánh sáng")
                .details("Loa kéo, micro không dây")
                .advanceBudget(BigDecimal.valueOf(3000000))
                .deadline(now)
                .status(TaskStatus.InProgress)
                .assignedBranchId("BR_CHI_2")
                .assignedMemberId("MEM_MAI_ANH")
                .createdBy("ACC_001")
                .build();

        TaskResponse response = taskMapper.toResponse(task, "Chi 2 - Nhánh Giáp", "Nguyễn Thị Mai Anh");

        assertNotNull(response);
        assertEquals("TSK_002", response.taskId());
        assertEquals("Chi 2 - Nhánh Giáp", response.assignedBranchName());
        assertEquals("Nguyễn Thị Mai Anh", response.assignedMemberName());
    }

    @Test
    @DisplayName("Maps list of EventTask entities to list of TaskResponse DTOs")
    void testToResponseList() {
        EventTask task1 = EventTask.builder().taskId("TSK_01").taskName("Việc 1").build();
        EventTask task2 = EventTask.builder().taskId("TSK_02").taskName("Việc 2").build();

        List<TaskResponse> list = taskMapper.toResponseList(List.of(task1, task2));

        assertNotNull(list);
        assertEquals(2, list.size());
        assertEquals("TSK_01", list.get(0).taskId());
        assertEquals("TSK_02", list.get(1).taskId());
    }
}

