package com.familyfund.event.internal.service;

import com.familyfund.core.exception.BadRequestException;
import com.familyfund.core.exception.BusinessRuleException;
import com.familyfund.core.exception.ResourceNotFoundException;
import com.familyfund.core.security.CurrentUserProvider;
import com.familyfund.core.tenant.TenantContext;
import com.familyfund.event.api.dto.EventStatus;
import com.familyfund.event.api.dto.TaskStatus;
import com.familyfund.event.api.event.TaskAssignedDomainEvent;
import com.familyfund.event.internal.dto.request.AssignTaskRequest;
import com.familyfund.event.internal.dto.request.CreateTaskRequest;
import com.familyfund.event.internal.dto.response.TaskResponse;
import com.familyfund.event.internal.entity.Event;
import com.familyfund.event.internal.entity.EventTask;
import com.familyfund.event.internal.mapper.TaskMapper;
import com.familyfund.event.internal.repository.EventRepository;
import com.familyfund.event.internal.repository.EventTaskRepository;
import com.familyfund.event.internal.util.TaskIdGenerator;
import com.familyfund.genealogy.api.GenealogyQueryService;
import com.familyfund.genealogy.api.dto.MemberSummaryDto;
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
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("TaskServiceImpl Unit Tests")
class TaskServiceImplTest {

    private static final String CLAN_ID = "CLAN_DEFAULT";
    private static final String ACCOUNT_ID = "ACC_TEST_01";
    private static final String EVENT_ID = "EVT_2026_TEST";
    private static final String TASK_ID = "TSK_TEST_001";
    private static final String MEMBER_ID = "MEM_001";
    private static final String BRANCH_ID = "BR_CHI_2";

    @Mock
    private EventRepository eventRepository;

    @Mock
    private EventTaskRepository eventTaskRepository;

    @Mock
    private TaskIdGenerator taskIdGenerator;

    @Spy
    private TaskMapper taskMapper = Mappers.getMapper(TaskMapper.class);

    @Mock
    private CurrentUserProvider currentUserProvider;

    @Mock
    private GenealogyQueryService genealogyQueryService;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private TaskServiceImpl taskService;

    private Event activeEvent;

    @BeforeEach
    void setUp() {
        TenantContext.setClanId(CLAN_ID);
        when(currentUserProvider.currentAccountId()).thenReturn(Optional.of(ACCOUNT_ID));
        when(taskIdGenerator.generate()).thenReturn(TASK_ID);
        when(eventTaskRepository.saveAndFlush(any(EventTask.class))).thenAnswer(invocation -> invocation.getArgument(0));

        activeEvent = Event.builder()
                .eventId(EVENT_ID)
                .clanId(CLAN_ID)
                .eventName("Lễ Giỗ Tổ Thường Niên")
                .eventDatetime(Instant.now().plus(30, ChronoUnit.DAYS))
                .status(EventStatus.RegistrationOpen)
                .build();
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Nested
    @DisplayName("createTask() Tests")
    class CreateTaskTests {

        @Test
        @DisplayName("Successfully creates task with assignee -> status InProgress and publishes TaskAssignedDomainEvent")
        void testCreateTask_WithAssignee_Success() {
            Instant deadline = Instant.now().plus(10, ChronoUnit.DAYS);
            CreateTaskRequest request = new CreateTaskRequest(
                    "Mua sắm lễ vật cúng tổ",
                    "5 mâm ngũ quả, hoa tươi",
                    BigDecimal.valueOf(2500000),
                    deadline,
                    BRANCH_ID,
                    MEMBER_ID
            );

            when(eventRepository.findByEventIdAndClanId(EVENT_ID, CLAN_ID)).thenReturn(Optional.of(activeEvent));
            when(genealogyQueryService.findMemberSummary(MEMBER_ID)).thenReturn(
                    Optional.of(new MemberSummaryDto(MEMBER_ID, "Nguyễn Văn An", CLAN_ID, BRANCH_ID))
            );
            when(genealogyQueryService.existsBranch(CLAN_ID, BRANCH_ID)).thenReturn(true);
            when(genealogyQueryService.getBranchName(CLAN_ID, BRANCH_ID)).thenReturn(Optional.of("Chi 2 - Nhánh Giáp"));

            TaskResponse response = taskService.createTask(EVENT_ID, request);

            assertNotNull(response);
            assertEquals(TASK_ID, response.taskId());
            assertEquals(EVENT_ID, response.eventId());
            assertEquals("Mua sắm lễ vật cúng tổ", response.taskName());
            assertEquals(TaskStatus.InProgress, response.status());
            assertEquals(MEMBER_ID, response.assignedMemberId());
            assertEquals("Nguyễn Văn An", response.assignedMemberName());
            assertEquals(BRANCH_ID, response.assignedBranchId());
            assertEquals("Chi 2 - Nhánh Giáp", response.assignedBranchName());

            ArgumentCaptor<TaskAssignedDomainEvent> eventCaptor = ArgumentCaptor.forClass(TaskAssignedDomainEvent.class);
            verify(eventPublisher, times(1)).publishEvent(eventCaptor.capture());
            TaskAssignedDomainEvent publishedEvent = eventCaptor.getValue();
            assertEquals(EVENT_ID, publishedEvent.eventId());
            assertEquals(TASK_ID, publishedEvent.taskId());
            assertEquals("Nguyễn Văn An", response.assignedMemberName());
        }

        @Test
        @DisplayName("Successfully creates task without assignee -> status Unassigned and does NOT publish event")
        void testCreateTask_WithoutAssignee_Success() {
            Instant deadline = Instant.now().plus(5, ChronoUnit.DAYS);
            CreateTaskRequest request = new CreateTaskRequest(
                    "Chuẩn bị rạp và bàn ghế",
                    "Thuê 10 bộ bàn ghế",
                    BigDecimal.ZERO,
                    deadline,
                    null,
                    null
            );

            when(eventRepository.findByEventIdAndClanId(EVENT_ID, CLAN_ID)).thenReturn(Optional.of(activeEvent));

            TaskResponse response = taskService.createTask(EVENT_ID, request);

            assertNotNull(response);
            assertEquals(TaskStatus.Unassigned, response.status());
            assertNull(response.assignedMemberId());
            assertNull(response.assignedBranchId());
            verify(eventPublisher, never()).publishEvent(any());
        }

        @Test
        @DisplayName("Throws ResourceNotFoundException when event does not exist in clan")
        void testCreateTask_EventNotFound_ThrowsException() {
            CreateTaskRequest request = new CreateTaskRequest(
                    "Khánh tiết", null, BigDecimal.ZERO, Instant.now().plus(2, ChronoUnit.DAYS), null, null
            );
            when(eventRepository.findByEventIdAndClanId(EVENT_ID, CLAN_ID)).thenReturn(Optional.empty());

            assertThrows(ResourceNotFoundException.class, () -> taskService.createTask(EVENT_ID, request));
        }

        @Test
        @DisplayName("Throws BusinessRuleException when event is Closed")
        void testCreateTask_EventClosed_ThrowsException() {
            activeEvent.setStatus(EventStatus.Closed);
            CreateTaskRequest request = new CreateTaskRequest(
                    "Khánh tiết", null, BigDecimal.ZERO, Instant.now().plus(2, ChronoUnit.DAYS), null, null
            );
            when(eventRepository.findByEventIdAndClanId(EVENT_ID, CLAN_ID)).thenReturn(Optional.of(activeEvent));

            assertThrows(BusinessRuleException.class, () -> taskService.createTask(EVENT_ID, request));
        }

        @Test
        @DisplayName("Throws BadRequestException when deadline is in the past")
        void testCreateTask_PastDeadline_ThrowsException() {
            CreateTaskRequest request = new CreateTaskRequest(
                    "Khánh tiết", null, BigDecimal.ZERO, Instant.now().minus(1, ChronoUnit.DAYS), null, null
            );
            when(eventRepository.findByEventIdAndClanId(EVENT_ID, CLAN_ID)).thenReturn(Optional.of(activeEvent));

            assertThrows(BadRequestException.class, () -> taskService.createTask(EVENT_ID, request));
        }

        @Test
        @DisplayName("Throws BusinessRuleException when deadline is after or equal to event datetime")
        void testCreateTask_DeadlineAfterEvent_ThrowsException() {
            Instant deadlineAfterEvent = activeEvent.getEventDatetime().plus(1, ChronoUnit.HOURS);
            CreateTaskRequest request = new CreateTaskRequest(
                    "Dọn dẹp", null, BigDecimal.ZERO, deadlineAfterEvent, null, null
            );
            when(eventRepository.findByEventIdAndClanId(EVENT_ID, CLAN_ID)).thenReturn(Optional.of(activeEvent));

            assertThrows(BusinessRuleException.class, () -> taskService.createTask(EVENT_ID, request));
        }

        @Test
        @DisplayName("Throws ResourceNotFoundException when assigned member does not exist")
        void testCreateTask_MemberNotFound_ThrowsException() {
            CreateTaskRequest request = new CreateTaskRequest(
                    "Nấu cỗ", null, BigDecimal.ZERO, Instant.now().plus(2, ChronoUnit.DAYS), null, "MEM_UNKNOWN"
            );
            when(eventRepository.findByEventIdAndClanId(EVENT_ID, CLAN_ID)).thenReturn(Optional.of(activeEvent));
            when(genealogyQueryService.findMemberSummary("MEM_UNKNOWN")).thenReturn(Optional.empty());

            assertThrows(ResourceNotFoundException.class, () -> taskService.createTask(EVENT_ID, request));
        }

        @Test
        @DisplayName("Throws BusinessRuleException when assigned member belongs to another clan")
        void testCreateTask_MemberOtherClan_ThrowsException() {
            CreateTaskRequest request = new CreateTaskRequest(
                    "Nấu cỗ", null, BigDecimal.ZERO, Instant.now().plus(2, ChronoUnit.DAYS), null, "MEM_OTHER"
            );
            when(eventRepository.findByEventIdAndClanId(EVENT_ID, CLAN_ID)).thenReturn(Optional.of(activeEvent));
            when(genealogyQueryService.findMemberSummary("MEM_OTHER")).thenReturn(
                    Optional.of(new MemberSummaryDto("MEM_OTHER", "Lê Văn C", "CLAN_OTHER", "BR_01"))
            );

            assertThrows(BusinessRuleException.class, () -> taskService.createTask(EVENT_ID, request));
        }

        @Test
        @DisplayName("Throws ResourceNotFoundException when assigned branch does not exist in clan")
        void testCreateTask_BranchNotFound_ThrowsException() {
            CreateTaskRequest request = new CreateTaskRequest(
                    "Nấu cỗ", null, BigDecimal.ZERO, Instant.now().plus(2, ChronoUnit.DAYS), "BR_UNKNOWN", null
            );
            when(eventRepository.findByEventIdAndClanId(EVENT_ID, CLAN_ID)).thenReturn(Optional.of(activeEvent));
            when(genealogyQueryService.existsBranch(CLAN_ID, "BR_UNKNOWN")).thenReturn(false);

            assertThrows(ResourceNotFoundException.class, () -> taskService.createTask(EVENT_ID, request));
        }
    }

    @Nested
    @DisplayName("assignTask() Tests")
    class AssignTaskTests {

        private EventTask unassignedTask;

        @BeforeEach
        void initTask() {
            unassignedTask = EventTask.builder()
                    .taskId(TASK_ID)
                    .eventId(EVENT_ID)
                    .taskName("Thuê âm thanh")
                    .advanceBudget(BigDecimal.valueOf(1000000))
                    .deadline(Instant.now().plus(5, ChronoUnit.DAYS))
                    .status(TaskStatus.Unassigned)
                    .createdBy(ACCOUNT_ID)
                    .build();
        }

        @Test
        @DisplayName("Successfully assigns unassigned task -> transitions to InProgress and publishes domain event")
        void testAssignTask_Success() {
            AssignTaskRequest request = new AssignTaskRequest(BRANCH_ID, MEMBER_ID);

            when(eventRepository.findByEventIdAndClanId(EVENT_ID, CLAN_ID)).thenReturn(Optional.of(activeEvent));
            when(eventTaskRepository.findByTaskIdAndEvent_ClanId(TASK_ID, CLAN_ID)).thenReturn(Optional.of(unassignedTask));
            when(genealogyQueryService.findMemberSummary(MEMBER_ID)).thenReturn(
                    Optional.of(new MemberSummaryDto(MEMBER_ID, "Nguyễn Văn An", CLAN_ID, BRANCH_ID))
            );
            when(genealogyQueryService.existsBranch(CLAN_ID, BRANCH_ID)).thenReturn(true);
            when(genealogyQueryService.getBranchName(CLAN_ID, BRANCH_ID)).thenReturn(Optional.of("Chi 2 - Nhánh Giáp"));

            TaskResponse response = taskService.assignTask(EVENT_ID, TASK_ID, request);

            assertNotNull(response);
            assertEquals(TaskStatus.InProgress, response.status());
            assertEquals(MEMBER_ID, response.assignedMemberId());
            assertEquals("Nguyễn Văn An", response.assignedMemberName());
            assertEquals(BRANCH_ID, response.assignedBranchId());
            assertEquals("Chi 2 - Nhánh Giáp", response.assignedBranchName());

            verify(eventPublisher, times(1)).publishEvent(any(TaskAssignedDomainEvent.class));
        }

        @Test
        @DisplayName("Throws BusinessRuleException when event is Closed")
        void testAssignTask_EventClosed_ThrowsException() {
            activeEvent.setStatus(EventStatus.Closed);
            AssignTaskRequest request = new AssignTaskRequest(BRANCH_ID, MEMBER_ID);
            when(eventRepository.findByEventIdAndClanId(EVENT_ID, CLAN_ID)).thenReturn(Optional.of(activeEvent));

            assertThrows(BusinessRuleException.class, () -> taskService.assignTask(EVENT_ID, TASK_ID, request));
        }

        @Test
        @DisplayName("Throws BadRequestException when task belongs to a different event")
        void testAssignTask_TaskWrongEvent_ThrowsException() {
            unassignedTask.setEventId("EVT_OTHER");
            AssignTaskRequest request = new AssignTaskRequest(BRANCH_ID, MEMBER_ID);
            when(eventRepository.findByEventIdAndClanId(EVENT_ID, CLAN_ID)).thenReturn(Optional.of(activeEvent));
            when(eventTaskRepository.findByTaskIdAndEvent_ClanId(TASK_ID, CLAN_ID)).thenReturn(Optional.of(unassignedTask));

            assertThrows(BadRequestException.class, () -> taskService.assignTask(EVENT_ID, TASK_ID, request));
        }

        @Test
        @DisplayName("Throws BusinessRuleException when task is already Completed")
        void testAssignTask_TaskCompleted_ThrowsException() {
            unassignedTask.setStatus(TaskStatus.Completed);
            AssignTaskRequest request = new AssignTaskRequest(BRANCH_ID, MEMBER_ID);
            when(eventRepository.findByEventIdAndClanId(EVENT_ID, CLAN_ID)).thenReturn(Optional.of(activeEvent));
            when(eventTaskRepository.findByTaskIdAndEvent_ClanId(TASK_ID, CLAN_ID)).thenReturn(Optional.of(unassignedTask));

            assertThrows(BusinessRuleException.class, () -> taskService.assignTask(EVENT_ID, TASK_ID, request));
        }

        @Test
        @DisplayName("Throws BadRequestException when neither member nor branch is provided")
        void testAssignTask_NoAssignee_ThrowsException() {
            AssignTaskRequest request = new AssignTaskRequest(null, null);
            when(eventRepository.findByEventIdAndClanId(EVENT_ID, CLAN_ID)).thenReturn(Optional.of(activeEvent));
            when(eventTaskRepository.findByTaskIdAndEvent_ClanId(TASK_ID, CLAN_ID)).thenReturn(Optional.of(unassignedTask));

            assertThrows(BadRequestException.class, () -> taskService.assignTask(EVENT_ID, TASK_ID, request));
        }
    }

    @Nested
    @DisplayName("getTasksByEvent() & getTaskDetail() Tests")
    class QueryTasksTests {

        @Test
        @DisplayName("Successfully retrieves and enriches tasks list for event")
        void testGetTasksByEvent_Success() {
            EventTask task1 = EventTask.builder()
                    .taskId("TSK_01")
                    .eventId(EVENT_ID)
                    .taskName("Việc 1")
                    .status(TaskStatus.InProgress)
                    .assignedMemberId(MEMBER_ID)
                    .assignedBranchId(BRANCH_ID)
                    .build();
            EventTask task2 = EventTask.builder()
                    .taskId("TSK_02")
                    .eventId(EVENT_ID)
                    .taskName("Việc 2")
                    .status(TaskStatus.Unassigned)
                    .build();

            when(eventRepository.existsByEventIdAndClanId(EVENT_ID, CLAN_ID)).thenReturn(true);
            when(eventTaskRepository.findAllByEvent_EventIdAndEvent_ClanIdOrderByDeadlineAsc(EVENT_ID, CLAN_ID))
                    .thenReturn(List.of(task1, task2));
            when(genealogyQueryService.findMemberSummary(MEMBER_ID))
                    .thenReturn(Optional.of(new MemberSummaryDto(MEMBER_ID, "Nguyễn Văn An", CLAN_ID, BRANCH_ID)));
            when(genealogyQueryService.getBranchName(CLAN_ID, BRANCH_ID))
                    .thenReturn(Optional.of("Chi 2"));

            List<TaskResponse> responses = taskService.getTasksByEvent(EVENT_ID, null, null, null);

            assertNotNull(responses);
            assertEquals(2, responses.size());
            assertEquals("Nguyễn Văn An", responses.get(0).assignedMemberName());
            assertEquals("Chi 2", responses.get(0).assignedBranchName());
            assertNull(responses.get(1).assignedMemberName());
        }

        @Test
        @DisplayName("Successfully filters tasks by status")
        void testGetTasksByEvent_FilterByStatus() {
            EventTask task1 = EventTask.builder().taskId("TSK_01").status(TaskStatus.InProgress).build();
            EventTask task2 = EventTask.builder().taskId("TSK_02").status(TaskStatus.Unassigned).build();

            when(eventRepository.existsByEventIdAndClanId(EVENT_ID, CLAN_ID)).thenReturn(true);
            when(eventTaskRepository.findAllByEvent_EventIdAndEvent_ClanIdOrderByDeadlineAsc(EVENT_ID, CLAN_ID))
                    .thenReturn(List.of(task1, task2));

            List<TaskResponse> responses = taskService.getTasksByEvent(EVENT_ID, TaskStatus.InProgress, null, null);

            assertEquals(1, responses.size());
            assertEquals("TSK_01", responses.get(0).taskId());
        }

        @Test
        @DisplayName("Successfully retrieves task detail with enriched assignee names")
        void testGetTaskDetail_Success() {
            EventTask task = EventTask.builder()
                    .taskId(TASK_ID)
                    .eventId(EVENT_ID)
                    .taskName("Chuẩn bị mâm quả")
                    .status(TaskStatus.InProgress)
                    .assignedMemberId(MEMBER_ID)
                    .assignedBranchId(BRANCH_ID)
                    .build();

            when(eventRepository.existsByEventIdAndClanId(EVENT_ID, CLAN_ID)).thenReturn(true);
            when(eventTaskRepository.findByTaskIdAndEvent_ClanId(TASK_ID, CLAN_ID)).thenReturn(Optional.of(task));
            when(genealogyQueryService.findMemberSummary(MEMBER_ID))
                    .thenReturn(Optional.of(new MemberSummaryDto(MEMBER_ID, "Nguyễn Văn An", CLAN_ID, BRANCH_ID)));
            when(genealogyQueryService.getBranchName(CLAN_ID, BRANCH_ID))
                    .thenReturn(Optional.of("Chi 2"));

            TaskResponse response = taskService.getTaskDetail(EVENT_ID, TASK_ID);

            assertNotNull(response);
            assertEquals(TASK_ID, response.taskId());
            assertEquals("Nguyễn Văn An", response.assignedMemberName());
            assertEquals("Chi 2", response.assignedBranchName());
        }

        @Test
        @DisplayName("Throws ResourceNotFoundException when task not found for detail query")
        void testGetTaskDetail_NotFound_ThrowsException() {
            when(eventRepository.existsByEventIdAndClanId(EVENT_ID, CLAN_ID)).thenReturn(true);
            when(eventTaskRepository.findByTaskIdAndEvent_ClanId("TSK_UNKNOWN", CLAN_ID)).thenReturn(Optional.empty());

            assertThrows(ResourceNotFoundException.class, () -> taskService.getTaskDetail(EVENT_ID, "TSK_UNKNOWN"));
        }
    }
}

