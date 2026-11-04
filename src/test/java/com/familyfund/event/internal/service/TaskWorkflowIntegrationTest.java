package com.familyfund.event.internal.service;

import com.familyfund.core.exception.BusinessRuleException;
import com.familyfund.core.security.CurrentUserProvider;
import com.familyfund.core.tenant.TenantContext;
import com.familyfund.event.api.dto.TaskStatus;
import com.familyfund.event.api.event.TaskAssignedDomainEvent;
import com.familyfund.event.internal.dto.request.AssignTaskRequest;
import com.familyfund.event.internal.dto.request.CreateTaskRequest;
import com.familyfund.event.internal.dto.response.TaskResponse;
import com.familyfund.event.internal.entity.Event;
import com.familyfund.event.internal.entity.EventNotification;
import com.familyfund.event.internal.entity.EventTask;
import com.familyfund.event.internal.mapper.TaskMapper;
import com.familyfund.event.internal.notification.NotificationSender;
import com.familyfund.event.internal.notification.TaskNotificationListener;
import com.familyfund.event.internal.repository.EventNotificationRepository;
import com.familyfund.event.internal.repository.EventRepository;
import com.familyfund.event.internal.repository.EventTaskRepository;
import com.familyfund.event.internal.util.NotificationIdGenerator;
import com.familyfund.event.internal.util.TaskIdGenerator;
import com.familyfund.genealogy.api.GenealogyQueryService;
import com.familyfund.genealogy.api.dto.MemberSummaryDto;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * End-to-End Workflow Integration Test for US-07: Phân hệ Công việc & tạm ứng.
 * Integrates TaskService, TaskMapper, TaskNotificationListener, and Domain Event publishing
 * to verify the complete lifecycle:
 * Task Creation (Unassigned) -> Business Validation -> Assignment (InProgress) ->
 * Domain Event Emission -> Async Notification Handling -> Living Branch Members Resolution ->
 * Notification DB Persistence -> Delivery Dispatch.
 */
@ExtendWith(MockitoExtension.class)
class TaskWorkflowIntegrationTest {

    private static final String CLAN_ID = "CLAN_NGUYEN_01";
    private static final String EVENT_ID = "EVT_GIO_TO_2027";
    private static final String BRANCH_ID = "BR_CHI_TRUONG";
    private static final String MEMBER_ID_1 = "MEM_TRUONG_AN";
    private static final String MEMBER_ID_2 = "MEM_PHU_THINH";
    private static final String OPERATOR_ID = "ACC_BTC_LEADER";

    private static final Instant EVENT_TIME = Instant.parse("2027-05-10T08:00:00Z");
    private static final Instant TASK_DEADLINE = Instant.parse("2027-05-01T10:00:00Z");

    @Mock
    private EventRepository eventRepository;

    @Mock
    private EventTaskRepository eventTaskRepository;

    @Mock
    private EventNotificationRepository eventNotificationRepository;

    @Mock
    private NotificationSender notificationSender;

    @Mock
    private GenealogyQueryService genealogyQueryService;

    @Mock
    private CurrentUserProvider currentUserProvider;

    private TaskMapper taskMapper;
    private TaskIdGenerator taskIdGenerator;
    private NotificationIdGenerator notificationIdGenerator;

    private TaskServiceImpl taskService;
    private TaskNotificationListener taskNotificationListener;

    // In-memory backing storage to simulate realistic persistence across operations
    private final Map<String, EventTask> taskStore = new HashMap<>();

    @BeforeEach
    void setUp() {
        TenantContext.setClanId(CLAN_ID);

        taskMapper = Mappers.getMapper(TaskMapper.class);
        taskIdGenerator = new TaskIdGenerator();
        notificationIdGenerator = new NotificationIdGenerator();

        // Wire an event publisher that routes directly to TaskNotificationListener
        ApplicationEventPublisher eventPublisher = event -> {
            if (event instanceof TaskAssignedDomainEvent domainEvent) {
                taskNotificationListener.onTaskAssigned(domainEvent);
            }
        };

        taskService = new TaskServiceImpl(
                eventRepository,
                eventTaskRepository,
                taskIdGenerator,
                taskMapper,
                currentUserProvider,
                genealogyQueryService,
                eventPublisher
        );

        taskNotificationListener = new TaskNotificationListener(
                eventNotificationRepository,
                notificationSender,
                notificationIdGenerator,
                genealogyQueryService
        );

        when(currentUserProvider.currentAccountId()).thenReturn(Optional.of(OPERATOR_ID));

        // Setup event in DB
        Event event = Event.builder()
                .eventId(EVENT_ID)
                .clanId(CLAN_ID)
                .eventName("Đại lễ Giỗ Tổ Họ Nguyễn 2027")
                .eventDatetime(EVENT_TIME)
                .build();
        when(eventRepository.findByEventIdAndClanId(EVENT_ID, CLAN_ID)).thenReturn(Optional.of(event));

        // Mock repository save & saveAndFlush behavior using in-memory map
        when(eventTaskRepository.saveAndFlush(any(EventTask.class))).thenAnswer(invocation -> {
            EventTask t = invocation.getArgument(0);
            taskStore.put(t.getTaskId(), t);
            return t;
        });

        when(eventTaskRepository.findByTaskIdAndEvent_ClanId(any(), eq(CLAN_ID)))
                .thenAnswer(invocation -> {
                    String tId = invocation.getArgument(0);
                    return Optional.ofNullable(taskStore.get(tId));
                });
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        taskStore.clear();
    }

    @Test
    @DisplayName("Complete E2E Lifecycle: Create unassigned -> Assign to branch & member -> Auto transition to InProgress -> Dispatch notifications")
    void completeTaskLifecycleWorkflow() {
        // =========================================================================
        // Step 1: BTC creates task without assignee
        // =========================================================================
        CreateTaskRequest createReq = new CreateTaskRequest(
                "Chuẩn bị mâm cỗ cúng tế",
                "Mua thực phẩm tươi sống, chế biến 10 mâm cỗ mặn truyền thống",
                new BigDecimal("5000000"),
                TASK_DEADLINE,
                null,
                null
        );

        TaskResponse createdResponse = taskService.createTask(EVENT_ID, createReq);

        assertNotNull(createdResponse);
        assertNotNull(createdResponse.taskId());
        assertTrue(createdResponse.taskId().startsWith("TSK"));
        assertEquals("Chuẩn bị mâm cỗ cúng tế", createdResponse.taskName());
        assertEquals(TaskStatus.Unassigned, createdResponse.status());
        assertEquals(new BigDecimal("5000000"), createdResponse.advanceBudget());
        assertEquals(TASK_DEADLINE, createdResponse.deadline());

        String generatedTaskId = createdResponse.taskId();
        assertThat(taskStore).containsKey(generatedTaskId);

        // =========================================================================
        // Step 2: Validate branch & member resolution mocks for assignment
        // =========================================================================
        when(genealogyQueryService.existsBranch(CLAN_ID, BRANCH_ID)).thenReturn(true);
        when(genealogyQueryService.findMemberSummary(MEMBER_ID_1)).thenReturn(Optional.of(
                new MemberSummaryDto(MEMBER_ID_1, "Nguyễn Trường An", CLAN_ID, BRANCH_ID)
        ));
        // Genealogy query resolves living members in branch (MEM_1 and MEM_2)
        when(genealogyQueryService.getMemberIdsByBranch(CLAN_ID, BRANCH_ID))
                .thenReturn(List.of(MEMBER_ID_1, MEMBER_ID_2));

        // =========================================================================
        // Step 3: BTC assigns task to branch BR_CHI_TRUONG and individual MEM_TRUONG_AN
        // =========================================================================
        AssignTaskRequest assignReq = new AssignTaskRequest(BRANCH_ID, MEMBER_ID_1);

        TaskResponse assignedResponse = taskService.assignTask(EVENT_ID, generatedTaskId, assignReq);

        assertNotNull(assignedResponse);
        assertEquals(generatedTaskId, assignedResponse.taskId());
        // Status successfully transitioned from Unassigned to InProgress
        assertEquals(TaskStatus.InProgress, assignedResponse.status());
        assertEquals(BRANCH_ID, assignedResponse.assignedBranchId());
        assertEquals(MEMBER_ID_1, assignedResponse.assignedMemberId());

        // =========================================================================
        // Step 4: Verify Async Event Listener intercepted event & persisted notification
        // =========================================================================
        ArgumentCaptor<EventNotification> notifCaptor = ArgumentCaptor.forClass(EventNotification.class);
        verify(eventNotificationRepository).save(notifCaptor.capture());
        EventNotification savedNotification = notifCaptor.getValue();

        assertNotNull(savedNotification);
        assertTrue(savedNotification.getNotificationId().startsWith("NTF"));
        assertEquals(EVENT_ID, savedNotification.getEventId());
        assertTrue(savedNotification.getTitle().contains("Chuẩn bị mâm cỗ cúng tế"));
        assertTrue(savedNotification.getContentChanges().contains("5000000 VNĐ"));
        assertTrue(savedNotification.getContentChanges().contains(BRANCH_ID));
        assertTrue(savedNotification.getContentChanges().contains(MEMBER_ID_1));
        assertEquals(OPERATOR_ID, savedNotification.getTriggeredBy());

        // =========================================================================
        // Step 5: Verify NotificationSender dispatched message with deduplicated recipients
        // =========================================================================
        verify(notificationSender).sendTaskAssignedNotification(
                eq(CLAN_ID),
                eq(EVENT_ID),
                eq(generatedTaskId),
                eq(savedNotification.getTitle()),
                eq(savedNotification.getContentChanges()),
                // Recipients: MEMBER_ID_1 + MEMBER_ID_2 (deduplicated)
                eq(List.of(MEMBER_ID_1, MEMBER_ID_2))
        );

        // =========================================================================
        // Step 6: Query task details with enriched names
        // =========================================================================
        when(eventRepository.existsByEventIdAndClanId(EVENT_ID, CLAN_ID)).thenReturn(true);
        when(genealogyQueryService.getBranchName(CLAN_ID, BRANCH_ID)).thenReturn(Optional.of("Chi Trưởng"));

        TaskResponse detailResponse = taskService.getTaskDetail(EVENT_ID, generatedTaskId);
        assertEquals("Nguyễn Trường An", detailResponse.assignedMemberName());
        assertEquals("Chi Trưởng", detailResponse.assignedBranchName());
        assertEquals(TaskStatus.InProgress, detailResponse.status());

        // =========================================================================
        // Step 7: Query tasks by event with filter
        // =========================================================================
        when(eventTaskRepository.findAllByEvent_EventIdAndEvent_ClanIdOrderByDeadlineAsc(EVENT_ID, CLAN_ID))
                .thenReturn(new ArrayList<>(taskStore.values()));

        List<TaskResponse> inProgressTasks = taskService.getTasksByEvent(
                EVENT_ID,
                TaskStatus.InProgress,
                MEMBER_ID_1,
                BRANCH_ID
        );

        assertThat(inProgressTasks).hasSize(1);
        assertEquals(generatedTaskId, inProgressTasks.get(0).taskId());
        assertEquals("Nguyễn Trường An", inProgressTasks.get(0).assignedMemberName());
    }

    @Test
    @DisplayName("Workflow Validation: Assigning member from foreign clan is rejected with BusinessRuleException")
    void foreignMemberAssignment_Rejected() {
        CreateTaskRequest createReq = new CreateTaskRequest(
                "Mua pháo hoa và hoa đăng",
                "Chuẩn bị lễ hội",
                BigDecimal.ZERO,
                TASK_DEADLINE,
                null,
                null
        );
        TaskResponse created = taskService.createTask(EVENT_ID, createReq);
        String taskId = created.taskId();

        // Member belongs to a different clan
        when(genealogyQueryService.findMemberSummary("MEM_FOREIGN")).thenReturn(Optional.of(
                new MemberSummaryDto("MEM_FOREIGN", "Người Ngoại Tộc", "CLAN_OTHER_99", "BR_OTHER")
        ));

        AssignTaskRequest assignReq = new AssignTaskRequest(null, "MEM_FOREIGN");

        BusinessRuleException ex = assertThrows(BusinessRuleException.class, () ->
                taskService.assignTask(EVENT_ID, taskId, assignReq));

        assertTrue(ex.getMessage().contains("không thuộc dòng họ hiện tại"));
        assertEquals(TaskStatus.Unassigned, taskStore.get(taskId).getStatus());
    }
}
