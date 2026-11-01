package com.familyfund.event.internal.controller;

import com.familyfund.core.exception.BusinessRuleException;
import com.familyfund.core.exception.GlobalExceptionHandler;
import com.familyfund.core.exception.ResourceNotFoundException;
import com.familyfund.event.api.dto.TaskStatus;
import com.familyfund.event.internal.dto.request.AssignTaskRequest;
import com.familyfund.event.internal.dto.request.CreateTaskRequest;
import com.familyfund.event.internal.dto.response.TaskResponse;
import com.familyfund.event.internal.service.TaskService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class TaskControllerTest {

    private static final String EVENT_ID = "EVT_TEST_123";
    private static final String TASK_ID = "TSK_TEST_456";
    private static final String FUTURE_DEADLINE_STR = "2027-04-20T10:00:00Z";
    private static final Instant FUTURE_DEADLINE = Instant.parse(FUTURE_DEADLINE_STR);

    private MockMvc mockMvc;

    @Mock
    private TaskService taskService;

    @InjectMocks
    private TaskController taskController;

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();

        mockMvc = MockMvcBuilders
                .standaloneSetup(taskController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .build();
    }

    @Test
    @DisplayName("POST /api/v1/events/{eventId}/tasks - 201 Created on valid payload")
    void createTask_Success() throws Exception {
        TaskResponse response = new TaskResponse(
                TASK_ID,
                EVENT_ID,
                "Chuẩn bị mâm cỗ",
                "Mua sắm thực phẩm và chế biến",
                new BigDecimal("5000000.00"),
                FUTURE_DEADLINE,
                TaskStatus.Unassigned,
                null,
                null,
                null,
                null,
                "ACC_ADMIN"
        );

        when(taskService.createTask(eq(EVENT_ID), any(CreateTaskRequest.class))).thenReturn(response);

        String payload = """
                {
                    "taskName": "Chuẩn bị mâm cỗ",
                    "details": "Mua sắm thực phẩm và chế biến",
                    "advanceBudget": 5000000.00,
                    "deadline": "2027-04-20T10:00:00Z"
                }
                """;

        mockMvc.perform(post("/api/v1/events/{eventId}/tasks", EVENT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Khởi tạo công việc thành công"))
                .andExpect(jsonPath("$.data.taskId").value(TASK_ID))
                .andExpect(jsonPath("$.data.taskName").value("Chuẩn bị mâm cỗ"))
                .andExpect(jsonPath("$.data.status").value("Unassigned"));

        verify(taskService).createTask(eq(EVENT_ID), any(CreateTaskRequest.class));
    }

    @Test
    @DisplayName("POST /api/v1/events/{eventId}/tasks - 400 Bad Request on blank taskName")
    void createTask_BlankName_BadRequest() throws Exception {
        String payload = """
                {
                    "taskName": "   ",
                    "deadline": "2027-04-20T10:00:00Z"
                }
                """;

        mockMvc.perform(post("/api/v1/events/{eventId}/tasks", EVENT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("POST /api/v1/events/{eventId}/tasks - 400 Bad Request on null deadline")
    void createTask_NullDeadline_BadRequest() throws Exception {
        String payload = """
                {
                    "taskName": "Chuẩn bị mâm cỗ"
                }
                """;

        mockMvc.perform(post("/api/v1/events/{eventId}/tasks", EVENT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("POST /api/v1/events/{eventId}/tasks - 400 Bad Request on negative advanceBudget")
    void createTask_NegativeBudget_BadRequest() throws Exception {
        String payload = """
                {
                    "taskName": "Chuẩn bị mâm cỗ",
                    "advanceBudget": -500.00,
                    "deadline": "2027-04-20T10:00:00Z"
                }
                """;

        mockMvc.perform(post("/api/v1/events/{eventId}/tasks", EVENT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("POST /api/v1/events/{eventId}/tasks - 400 Bad Request when service throws BusinessRuleException")
    void createTask_BusinessRuleViolation() throws Exception {
        when(taskService.createTask(eq(EVENT_ID), any(CreateTaskRequest.class)))
                .thenThrow(new BusinessRuleException("Hạn chót công việc phải trước thời điểm diễn ra sự kiện"));

        String payload = """
                {
                    "taskName": "Chuẩn bị mâm cỗ",
                    "deadline": "2027-04-20T10:00:00Z"
                }
                """;

        mockMvc.perform(post("/api/v1/events/{eventId}/tasks", EVENT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Hạn chót công việc phải trước thời điểm diễn ra sự kiện"));
    }

    @Test
    @DisplayName("POST /api/v1/events/{eventId}/tasks/{taskId}/assign - 200 OK on valid assignee")
    void assignTask_Success() throws Exception {
        TaskResponse response = new TaskResponse(
                TASK_ID,
                EVENT_ID,
                "Chuẩn bị mâm cỗ",
                "Mua sắm thực phẩm",
                new BigDecimal("5000000.00"),
                FUTURE_DEADLINE,
                TaskStatus.InProgress,
                "BR_01",
                "Chi Trưởng",
                "MEM_01",
                "Nguyễn Văn An",
                "ACC_ADMIN"
        );

        when(taskService.assignTask(eq(EVENT_ID), eq(TASK_ID), any(AssignTaskRequest.class))).thenReturn(response);

        String payload = """
                {
                    "assignedBranchId": "BR_01",
                    "assignedMemberId": "MEM_01"
                }
                """;

        mockMvc.perform(post("/api/v1/events/{eventId}/tasks/{taskId}/assign", EVENT_ID, TASK_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Phân công công việc thành công"))
                .andExpect(jsonPath("$.data.taskId").value(TASK_ID))
                .andExpect(jsonPath("$.data.status").value("InProgress"))
                .andExpect(jsonPath("$.data.assignedBranchId").value("BR_01"))
                .andExpect(jsonPath("$.data.assignedBranchName").value("Chi Trưởng"))
                .andExpect(jsonPath("$.data.assignedMemberId").value("MEM_01"))
                .andExpect(jsonPath("$.data.assignedMemberName").value("Nguyễn Văn An"));

        verify(taskService).assignTask(eq(EVENT_ID), eq(TASK_ID), any(AssignTaskRequest.class));
    }

    @Test
    @DisplayName("POST /api/v1/events/{eventId}/tasks/{taskId}/assign - 400 Bad Request when no assignee provided")
    void assignTask_NoAssignee_BadRequest() throws Exception {
        String payload = """
                {
                    "assignedBranchId": null,
                    "assignedMemberId": "   "
                }
                """;

        mockMvc.perform(post("/api/v1/events/{eventId}/tasks/{taskId}/assign", EVENT_ID, TASK_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("GET /api/v1/events/{eventId}/tasks - 200 OK without filters")
    void getTasks_WithoutFilters() throws Exception {
        TaskResponse t1 = new TaskResponse(
                TASK_ID, EVENT_ID, "Task 1", null, BigDecimal.ZERO,
                FUTURE_DEADLINE, TaskStatus.Unassigned,
                null, null, null, null, "ACC_ADMIN"
        );

        when(taskService.getTasksByEvent(EVENT_ID, null, null, null)).thenReturn(List.of(t1));

        mockMvc.perform(get("/api/v1/events/{eventId}/tasks", EVENT_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].taskId").value(TASK_ID))
                .andExpect(jsonPath("$.data[0].taskName").value("Task 1"));

        verify(taskService).getTasksByEvent(EVENT_ID, null, null, null);
    }

    @Test
    @DisplayName("GET /api/v1/events/{eventId}/tasks - 200 OK with filters")
    void getTasks_WithFilters() throws Exception {
        TaskResponse t1 = new TaskResponse(
                TASK_ID, EVENT_ID, "Task 1", null, BigDecimal.ZERO,
                FUTURE_DEADLINE, TaskStatus.InProgress,
                "BR_01", "Chi Trưởng", "MEM_01", "Nguyễn Văn An", "ACC_ADMIN"
        );

        when(taskService.getTasksByEvent(EVENT_ID, TaskStatus.InProgress, "MEM_01", "BR_01"))
                .thenReturn(List.of(t1));

        mockMvc.perform(get("/api/v1/events/{eventId}/tasks", EVENT_ID)
                        .param("status", "InProgress")
                        .param("assignedMemberId", "MEM_01")
                        .param("assignedBranchId", "BR_01"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].taskId").value(TASK_ID))
                .andExpect(jsonPath("$.data[0].status").value("InProgress"));

        verify(taskService).getTasksByEvent(EVENT_ID, TaskStatus.InProgress, "MEM_01", "BR_01");
    }

    @Test
    @DisplayName("GET /api/v1/events/{eventId}/tasks/{taskId} - 200 OK on existing task")
    void getTaskDetail_Success() throws Exception {
        TaskResponse response = new TaskResponse(
                TASK_ID, EVENT_ID, "Task Detail", "Details", BigDecimal.valueOf(1000000),
                FUTURE_DEADLINE, TaskStatus.InProgress,
                "BR_01", "Chi Trưởng", "MEM_01", "Nguyễn Văn An", "ACC_ADMIN"
        );

        when(taskService.getTaskDetail(EVENT_ID, TASK_ID)).thenReturn(response);

        mockMvc.perform(get("/api/v1/events/{eventId}/tasks/{taskId}", EVENT_ID, TASK_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.taskId").value(TASK_ID))
                .andExpect(jsonPath("$.data.taskName").value("Task Detail"));

        verify(taskService).getTaskDetail(EVENT_ID, TASK_ID);
    }

    @Test
    @DisplayName("GET /api/v1/events/{eventId}/tasks/{taskId} - 404 Not Found on non-existent task")
    void getTaskDetail_NotFound() throws Exception {
        when(taskService.getTaskDetail(EVENT_ID, "TSK_UNKNOWN"))
                .thenThrow(new ResourceNotFoundException("EventTask", "taskId", "TSK_UNKNOWN"));

        mockMvc.perform(get("/api/v1/events/{eventId}/tasks/{taskId}", EVENT_ID, "TSK_UNKNOWN"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("EventTask không tồn tại với taskId: 'TSK_UNKNOWN'"));
    }
}
