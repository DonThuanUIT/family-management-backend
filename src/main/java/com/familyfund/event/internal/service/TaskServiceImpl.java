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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service implementation for managing event tasks and assignments (US-07).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TaskServiceImpl implements TaskService {

    private final EventRepository eventRepository;
    private final EventTaskRepository eventTaskRepository;
    private final TaskIdGenerator taskIdGenerator;
    private final TaskMapper taskMapper;
    private final CurrentUserProvider currentUserProvider;
    private final GenealogyQueryService genealogyQueryService;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    @Transactional
    public TaskResponse createTask(String eventId, CreateTaskRequest request) {
        String clanId = TenantContext.getClanId();
        String createdByAccountId = currentUserProvider.currentAccountId()
                .orElseThrow(() -> new BadRequestException("Không tìm thấy thông tin tài khoản người tạo"));

        Event event = eventRepository.findByEventIdAndClanId(eventId, clanId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy sự kiện với mã: " + eventId));

        if (event.getStatus() == EventStatus.Closed) {
            throw new BusinessRuleException("Sự kiện đã kết thúc và khóa sổ, không thể tạo hoặc phân công công việc");
        }

        // Deadline validation: must be in the future AND before event time
        if (request.deadline() == null || !request.deadline().isAfter(Instant.now())) {
            throw new BadRequestException("Hạn chót công việc phải là thời điểm trong tương lai");
        }
        if (event.getEventDatetime() != null && !request.deadline().isBefore(event.getEventDatetime())) {
            throw new BusinessRuleException("Hạn chót công việc phải trước thời gian diễn ra sự kiện");
        }

        BigDecimal advanceBudget = request.advanceBudget() != null ? request.advanceBudget() : BigDecimal.ZERO;
        if (advanceBudget.compareTo(BigDecimal.ZERO) < 0) {
            throw new BadRequestException("Ngân sách tạm ứng dự kiến phải lớn hơn hoặc bằng 0");
        }

        String assignedMemberId = normalizeIdentifier(request.assignedMemberId());
        String assignedBranchId = normalizeIdentifier(request.assignedBranchId());

        String memberName = null;
        if (assignedMemberId != null) {
            MemberSummaryDto member = genealogyQueryService.findMemberSummary(assignedMemberId)
                    .orElseThrow(() -> new ResourceNotFoundException("Thành viên không tồn tại trong gia phả: " + assignedMemberId));
            if (!clanId.equals(member.clanId())) {
                throw new BusinessRuleException(String.format("Thành viên %s không thuộc dòng họ hiện tại", assignedMemberId));
            }
            memberName = member.fullName();
        }

        String branchName = null;
        if (assignedBranchId != null) {
            if (!genealogyQueryService.existsBranch(clanId, assignedBranchId)) {
                throw new ResourceNotFoundException("Chi/nhánh không tồn tại trong dòng họ: " + assignedBranchId);
            }
            branchName = genealogyQueryService.getBranchName(clanId, assignedBranchId).orElse(null);
        }

        boolean isAssigned = (assignedMemberId != null || assignedBranchId != null);
        TaskStatus status = isAssigned ? TaskStatus.InProgress : TaskStatus.Unassigned;

        String taskId = generateUniqueTaskId();

        EventTask task = EventTask.builder()
                .taskId(taskId)
                .eventId(eventId)
                .taskName(request.taskName().trim())
                .details(request.details() != null ? request.details().trim() : null)
                .advanceBudget(advanceBudget)
                .deadline(request.deadline())
                .status(status)
                .assignedBranchId(assignedBranchId)
                .assignedMemberId(assignedMemberId)
                .createdBy(createdByAccountId)
                .build();

        EventTask saved = eventTaskRepository.saveAndFlush(task);
        log.info("Task {} created for event {} with status {}", taskId, eventId, status);

        if (isAssigned) {
            eventPublisher.publishEvent(new TaskAssignedDomainEvent(
                    eventId,
                    clanId,
                    event.getEventName(),
                    saved.getTaskId(),
                    saved.getTaskName(),
                    saved.getAssignedMemberId(),
                    saved.getAssignedBranchId(),
                    saved.getAdvanceBudget(),
                    saved.getDeadline(),
                    createdByAccountId
            ));
            log.info("Published TaskAssignedDomainEvent for task {}", taskId);
        }

        return taskMapper.toResponse(saved, branchName, memberName);
    }

    @Override
    @Transactional
    public TaskResponse assignTask(String eventId, String taskId, AssignTaskRequest request) {
        String clanId = TenantContext.getClanId();
        String currentAccountId = currentUserProvider.currentAccountId()
                .orElseThrow(() -> new BadRequestException("Không tìm thấy thông tin tài khoản người thực hiện"));

        Event event = eventRepository.findByEventIdAndClanId(eventId, clanId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy sự kiện với mã: " + eventId));

        if (event.getStatus() == EventStatus.Closed) {
            throw new BusinessRuleException("Sự kiện đã kết thúc và khóa sổ, không thể phân công công việc");
        }

        EventTask task = eventTaskRepository.findByTaskIdAndEvent_ClanId(taskId, clanId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy công việc với mã: " + taskId));

        if (!task.getEventId().equals(eventId)) {
            throw new BadRequestException("Công việc không thuộc sự kiện: " + eventId);
        }

        if (task.getStatus() == TaskStatus.Completed) {
            throw new BusinessRuleException("Công việc đã hoàn thành, không thể phân công lại");
        }

        String assignedMemberId = normalizeIdentifier(request.assignedMemberId());
        String assignedBranchId = normalizeIdentifier(request.assignedBranchId());

        if (assignedMemberId == null && assignedBranchId == null) {
            throw new BadRequestException("Phải chỉ định ít nhất cá nhân hoặc nhánh gia đình phụ trách");
        }

        String memberName = null;
        if (assignedMemberId != null) {
            MemberSummaryDto member = genealogyQueryService.findMemberSummary(assignedMemberId)
                    .orElseThrow(() -> new ResourceNotFoundException("Thành viên không tồn tại trong gia phả: " + assignedMemberId));
            if (!clanId.equals(member.clanId())) {
                throw new BusinessRuleException(String.format("Thành viên %s không thuộc dòng họ hiện tại", assignedMemberId));
            }
            memberName = member.fullName();
        }

        String branchName = null;
        if (assignedBranchId != null) {
            if (!genealogyQueryService.existsBranch(clanId, assignedBranchId)) {
                throw new ResourceNotFoundException("Chi/nhánh không tồn tại trong dòng họ: " + assignedBranchId);
            }
            branchName = genealogyQueryService.getBranchName(clanId, assignedBranchId).orElse(null);
        }

        task.setAssignedMemberId(assignedMemberId);
        task.setAssignedBranchId(assignedBranchId);
        task.setStatus(TaskStatus.InProgress);

        EventTask updated = eventTaskRepository.saveAndFlush(task);
        log.info("Task {} assigned to member {} / branch {} with status {}",
                taskId, assignedMemberId, assignedBranchId, TaskStatus.InProgress);

        eventPublisher.publishEvent(new TaskAssignedDomainEvent(
                eventId,
                clanId,
                event.getEventName(),
                updated.getTaskId(),
                updated.getTaskName(),
                updated.getAssignedMemberId(),
                updated.getAssignedBranchId(),
                updated.getAdvanceBudget(),
                updated.getDeadline(),
                currentAccountId
        ));
        log.info("Published TaskAssignedDomainEvent for task {}", taskId);

        return taskMapper.toResponse(updated, branchName, memberName);
    }

    @Override
    @Transactional(readOnly = true)
    public List<TaskResponse> getTasksByEvent(String eventId, TaskStatus status, String assignedMemberId, String assignedBranchId) {
        String clanId = TenantContext.getClanId();

        if (!eventRepository.existsByEventIdAndClanId(eventId, clanId)) {
            throw new ResourceNotFoundException("Không tìm thấy sự kiện với mã: " + eventId);
        }

        List<EventTask> tasks = eventTaskRepository.findAllByEvent_EventIdAndEvent_ClanIdOrderByDeadlineAsc(eventId, clanId);

        String normalizedMemberId = normalizeIdentifier(assignedMemberId);
        String normalizedBranchId = normalizeIdentifier(assignedBranchId);

        List<EventTask> filteredTasks = tasks.stream()
                .filter(t -> status == null || t.getStatus() == status)
                .filter(t -> normalizedMemberId == null || normalizedMemberId.equals(t.getAssignedMemberId()))
                .filter(t -> normalizedBranchId == null || normalizedBranchId.equals(t.getAssignedBranchId()))
                .toList();

        if (filteredTasks.isEmpty()) {
            return Collections.emptyList();
        }

        Set<String> memberIds = filteredTasks.stream()
                .map(EventTask::getAssignedMemberId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Set<String> branchIds = filteredTasks.stream()
                .map(EventTask::getAssignedBranchId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Map<String, String> memberNameMap = new HashMap<>();
        for (String mid : memberIds) {
            genealogyQueryService.findMemberSummary(mid)
                    .ifPresent(m -> memberNameMap.put(mid, m.fullName()));
        }

        Map<String, String> branchNameMap = new HashMap<>();
        for (String bid : branchIds) {
            genealogyQueryService.getBranchName(clanId, bid)
                    .ifPresent(b -> branchNameMap.put(bid, b));
        }

        return filteredTasks.stream()
                .map(t -> taskMapper.toResponse(
                        t,
                        branchNameMap.get(t.getAssignedBranchId()),
                        memberNameMap.get(t.getAssignedMemberId())
                ))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public TaskResponse getTaskDetail(String eventId, String taskId) {
        String clanId = TenantContext.getClanId();

        if (!eventRepository.existsByEventIdAndClanId(eventId, clanId)) {
            throw new ResourceNotFoundException("Không tìm thấy sự kiện với mã: " + eventId);
        }

        EventTask task = eventTaskRepository.findByTaskIdAndEvent_ClanId(taskId, clanId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy công việc với mã: " + taskId));

        if (!task.getEventId().equals(eventId)) {
            throw new BadRequestException("Công việc không thuộc sự kiện: " + eventId);
        }

        String memberName = resolveMemberName(task.getAssignedMemberId());
        String branchName = resolveBranchName(clanId, task.getAssignedBranchId());

        return taskMapper.toResponse(task, branchName, memberName);
    }

    private String normalizeIdentifier(String id) {
        return (id != null && !id.isBlank()) ? id.trim() : null;
    }

    private String resolveMemberName(String memberId) {
        if (memberId == null) {
            return null;
        }
        return genealogyQueryService.findMemberSummary(memberId)
                .map(MemberSummaryDto::fullName)
                .orElse(null);
    }

    private String resolveBranchName(String clanId, String branchId) {
        if (branchId == null) {
            return null;
        }
        return genealogyQueryService.getBranchName(clanId, branchId).orElse(null);
    }

    private String generateUniqueTaskId() {
        String taskId;
        int attempts = 0;
        do {
            taskId = taskIdGenerator.generate();
            attempts++;
            if (attempts > 10) {
                throw new IllegalStateException("Không thể tạo mã định danh duy nhất cho công việc");
            }
        } while (eventTaskRepository.existsByTaskId(taskId));
        return taskId;
    }
}

