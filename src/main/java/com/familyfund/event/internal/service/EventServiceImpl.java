package com.familyfund.event.internal.service;

import com.familyfund.core.audit.annotation.Audited;
import com.familyfund.core.audit.model.AuditAction;
import com.familyfund.core.audit.model.AuditEntityType;
import com.familyfund.core.exception.BadRequestException;
import com.familyfund.core.exception.BusinessRuleException;
import com.familyfund.core.exception.ResourceNotFoundException;
import com.familyfund.core.security.CurrentUserProvider;
import com.familyfund.core.tenant.TenantContext;
import com.familyfund.event.api.dto.EventRole;
import com.familyfund.event.api.dto.EventStatus;
import com.familyfund.event.api.event.EventOpenedDomainEvent;
import com.familyfund.event.api.event.EventScheduleChangedDomainEvent;
import com.familyfund.event.internal.dto.request.AssignPersonnelRequest;
import com.familyfund.event.internal.dto.request.CreateEventRequest;
import com.familyfund.event.internal.dto.request.UpdateEventRequest;
import com.familyfund.event.internal.dto.request.UpdateEventStatusRequest;
import com.familyfund.event.internal.dto.response.EventBudgetSummaryResponse;
import com.familyfund.event.internal.dto.response.EventDetailResponse;
import com.familyfund.event.internal.dto.response.EventNotificationResponse;
import com.familyfund.event.internal.dto.response.EventPersonnelResponse;
import com.familyfund.event.internal.dto.response.EventResponse;
import com.familyfund.event.internal.dto.response.TaskBudgetBreakdownResponse;
import com.familyfund.event.internal.entity.Event;
import com.familyfund.event.internal.entity.EventNotification;
import com.familyfund.event.internal.entity.EventPersonnel;
import com.familyfund.event.internal.mapper.EventMapper;
import com.familyfund.event.internal.repository.EventNotificationRepository;
import com.familyfund.event.internal.repository.EventPersonnelRepository;
import com.familyfund.event.internal.repository.EventRepository;
import com.familyfund.event.internal.repository.EventTaskRepository;
import com.familyfund.event.internal.repository.EventTaskRepository.TaskBudgetAggregateProjection;
import com.familyfund.event.internal.util.EventIdGenerator;
import com.familyfund.genealogy.api.GenealogyQueryService;
import com.familyfund.genealogy.api.dto.MemberSummaryDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.*;

@Slf4j
@Service
public class EventServiceImpl implements EventService {

    private final EventRepository eventRepository;
    private final EventPersonnelRepository eventPersonnelRepository;
    private final EventNotificationRepository eventNotificationRepository;
    private final EventMapper eventMapper;
    private final EventIdGenerator eventIdGenerator;
    private final CurrentUserProvider currentUserProvider;
    private final GenealogyQueryService genealogyQueryService;
    private final ApplicationEventPublisher eventPublisher;
    private final EventTaskRepository eventTaskRepository;

    @Autowired
    public EventServiceImpl(
            EventRepository eventRepository,
            EventPersonnelRepository eventPersonnelRepository,
            EventNotificationRepository eventNotificationRepository,
            EventMapper eventMapper,
            EventIdGenerator eventIdGenerator,
            CurrentUserProvider currentUserProvider,
            GenealogyQueryService genealogyQueryService,
            ApplicationEventPublisher eventPublisher,
            EventTaskRepository eventTaskRepository
    ) {
        this.eventRepository = eventRepository;
        this.eventPersonnelRepository = eventPersonnelRepository;
        this.eventNotificationRepository = eventNotificationRepository;
        this.eventMapper = eventMapper;
        this.eventIdGenerator = eventIdGenerator;
        this.currentUserProvider = currentUserProvider;
        this.genealogyQueryService = genealogyQueryService;
        this.eventPublisher = eventPublisher;
        this.eventTaskRepository = eventTaskRepository;
    }

    public EventServiceImpl(
            EventRepository eventRepository,
            EventPersonnelRepository eventPersonnelRepository,
            EventNotificationRepository eventNotificationRepository,
            EventMapper eventMapper,
            EventIdGenerator eventIdGenerator,
            CurrentUserProvider currentUserProvider,
            GenealogyQueryService genealogyQueryService,
            ApplicationEventPublisher eventPublisher
    ) {
        this(
                eventRepository,
                eventPersonnelRepository,
                eventNotificationRepository,
                eventMapper,
                eventIdGenerator,
                currentUserProvider,
                genealogyQueryService,
                eventPublisher,
                null
        );
    }

    @Override
    @Transactional
    @Audited(action = AuditAction.CREATE, entityType = AuditEntityType.EVENT, entityId = "#result.eventId", description = "Tạo mới sự kiện dòng họ")
    public EventResponse createEvent(CreateEventRequest request) {
        String clanId = TenantContext.getClanId();
        String createdByAccountId = currentUserProvider.currentAccountId()
                .orElseThrow(() -> new BadRequestException("Không tìm thấy thông tin tài khoản người tạo"));

        BigDecimal estimatedBudget = request.estimatedBudget() != null ? request.estimatedBudget() : BigDecimal.ZERO;
        if (estimatedBudget.compareTo(BigDecimal.ZERO) < 0) {
            throw new BadRequestException("Dự toán ngân sách phải lớn hơn hoặc bằng 0");
        }

        String eventId = generateUniqueEventId();

        Event event = Event.builder()
                .eventId(eventId)
                .clanId(clanId)
                .eventName(request.eventName().trim())
                .eventDatetime(request.eventDatetime())
                .eventDateLunar(request.eventDateLunar() != null ? request.eventDateLunar().trim() : null)
                .location(request.location().trim())
                .estimatedBudget(estimatedBudget)
                .description(request.description() != null ? request.description().trim() : null)
                .registrationDeadline(request.registrationDeadline())
                .maxCapacity(request.maxCapacity())
                .status(EventStatus.Draft)
                .createdBy(createdByAccountId)
                .build();

        Event saved = eventRepository.saveAndFlush(event);
        log.info("Created event {} ({}) for clan {} by user {}", eventId, saved.getEventName(), clanId, createdByAccountId);
        return toEnrichedEventResponse(saved);
    }

    @Override
    @Transactional
    @Audited(action = AuditAction.UPDATE, entityType = AuditEntityType.EVENT, entityId = "#eventId", description = "Cập nhật thông tin sự kiện")
    public EventResponse updateEvent(String eventId, UpdateEventRequest request) {
        String clanId = TenantContext.getClanId();
        Event event = eventRepository.findByEventIdAndClanId(eventId, clanId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy sự kiện với mã: " + eventId));

        if (event.getStatus() == EventStatus.Closed) {
            throw new BusinessRuleException("Sự kiện đã kết thúc và khóa sổ, không thể chỉnh sửa thông tin");
        }

        BigDecimal estimatedBudget = request.estimatedBudget() != null ? request.estimatedBudget() : BigDecimal.ZERO;
        if (estimatedBudget.compareTo(BigDecimal.ZERO) < 0) {
            throw new BadRequestException("Dự toán ngân sách phải lớn hơn hoặc bằng 0");
        }

        // Compare schedule & location changes
        boolean datetimeChanged = !event.getEventDatetime().equals(request.eventDatetime());
        String reqLunar = request.eventDateLunar() != null ? request.eventDateLunar().trim() : null;
        boolean lunarChanged = !Objects.equals(event.getEventDateLunar(), reqLunar);
        boolean locationChanged = !event.getLocation().equalsIgnoreCase(request.location().trim());

        if (datetimeChanged || lunarChanged || locationChanged) {
            List<String> changeDescriptions = new ArrayList<>();
            if (datetimeChanged) {
                changeDescriptions.add(String.format("Thời gian dương lịch: từ '%s' sang '%s'",
                        event.getEventDatetime(), request.eventDatetime()));
            }
            if (lunarChanged) {
                changeDescriptions.add(String.format("Ngày âm lịch: từ '%s' sang '%s'",
                        event.getEventDateLunar() != null ? event.getEventDateLunar() : "chưa có",
                        reqLunar != null ? reqLunar : "chưa có"));
            }
            if (locationChanged) {
                changeDescriptions.add(String.format("Địa điểm: từ '%s' sang '%s'",
                        event.getLocation(), request.location().trim()));
            }

            String contentChanges = String.join("; ", changeDescriptions);
            String title = "Thông báo thay đổi lịch trình sự kiện: " + request.eventName().trim();
            String accountId = currentUserProvider.currentAccountId().orElse("SYSTEM");

            eventPublisher.publishEvent(new EventScheduleChangedDomainEvent(
                    eventId,
                    clanId,
                    request.eventName().trim(),
                    title,
                    contentChanges,
                    accountId
            ));
            log.info("Published EventScheduleChangedDomainEvent for event {}: {}", eventId, contentChanges);
        }

        event.setEventName(request.eventName().trim());
        event.setEventDatetime(request.eventDatetime());
        event.setEventDateLunar(reqLunar);
        event.setLocation(request.location().trim());
        event.setEstimatedBudget(estimatedBudget);
        event.setDescription(request.description() != null ? request.description().trim() : null);
        event.setRegistrationDeadline(request.registrationDeadline());
        event.setMaxCapacity(request.maxCapacity());

        Event updated = eventRepository.saveAndFlush(event);
        log.info("Event {} updated by clan {}", eventId, clanId);
        return toEnrichedEventResponse(updated);
    }

    @Override
    @Transactional
    @Audited(action = AuditAction.STATUS_CHANGE, entityType = AuditEntityType.EVENT, entityId = "#eventId", description = "Thay đổi trạng thái vòng đời sự kiện")
    public EventResponse updateEventStatus(String eventId, UpdateEventStatusRequest request) {
        String clanId = TenantContext.getClanId();
        Event event = eventRepository.findByEventIdAndClanId(eventId, clanId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy sự kiện với mã: " + eventId));

        EventStatus currentStatus = event.getStatus();
        EventStatus targetStatus = request.status();

        if (!currentStatus.canTransitionTo(targetStatus)) {
            throw new BusinessRuleException(String.format(
                    "Không thể chuyển đổi trạng thái sự kiện từ '%s' sang '%s'", currentStatus, targetStatus));
        }

        if (targetStatus == EventStatus.RegistrationOpen && currentStatus != EventStatus.RegistrationOpen) {
            String accountId = currentUserProvider.currentAccountId().orElse("SYSTEM");
            eventPublisher.publishEvent(new EventOpenedDomainEvent(
                    event.getEventId(),
                    event.getClanId(),
                    event.getEventName(),
                    event.getEventDatetime(),
                    event.getEventDateLunar(),
                    event.getLocation(),
                    accountId
            ));
            log.info("Published EventOpenedDomainEvent for event {}", eventId);
        }

        if (targetStatus == EventStatus.Closed) {
            event.setClosedAt(Instant.now());
            // Freeze all personnel permissions to read-only
            List<EventPersonnel> personnelList = eventPersonnelRepository.findAllByEventId(eventId);
            for (EventPersonnel p : personnelList) {
                p.setIsReadOnly(true);
            }
            eventPersonnelRepository.saveAll(personnelList);
            log.info("Event {} marked as Closed, {} personnel permissions set to read-only", eventId, personnelList.size());
        }

        event.setStatus(targetStatus);
        Event updated = eventRepository.saveAndFlush(event);
        log.info("Event {} status updated: {} -> {}", eventId, currentStatus, targetStatus);
        return toEnrichedEventResponse(updated);
    }

    @Override
    @Transactional
    @Audited(action = AuditAction.ASSIGN_PERSONNEL, entityType = AuditEntityType.EVENT_PERSONNEL, entityId = "#eventId", description = "Phân bổ ban nhân sự phụ trách sự kiện")
    public List<EventPersonnelResponse> assignPersonnel(String eventId, AssignPersonnelRequest request) {
        String clanId = TenantContext.getClanId();
        Event event = eventRepository.findByEventIdAndClanId(eventId, clanId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy sự kiện với mã: " + eventId));

        if (event.getStatus() == EventStatus.Closed) {
            throw new BusinessRuleException("Sự kiện đã kết thúc và khóa sổ, không thể thay đổi nhân sự ban tổ chức");
        }

        List<AssignPersonnelRequest.PersonnelItem> items = request.personnel();

        // Validate max 1 HeadOfOrganizer
        long headCount = items.stream().filter(i -> i.role() == EventRole.HeadOfOrganizer).count();
        if (headCount > 1) {
            throw new BusinessRuleException("Mỗi sự kiện chỉ được có tối đa 1 Trưởng ban tổ chức (HeadOfOrganizer)");
        }

        // Validate max 1 EventTreasurer
        long treasurerCount = items.stream().filter(i -> i.role() == EventRole.EventTreasurer).count();
        if (treasurerCount > 1) {
            throw new BusinessRuleException("Mỗi sự kiện chỉ được có tối đa 1 Thủ quỹ (EventTreasurer)");
        }

        // Validate each member exists in genealogy and belongs to current clan
        Map<String, String> memberNameMap = new HashMap<>();
        Set<String> processedKeys = new HashSet<>();

        List<EventPersonnel> newPersonnelList = new ArrayList<>();
        for (AssignPersonnelRequest.PersonnelItem item : items) {
            String memberId = item.memberId().trim();
            String compositeKey = memberId + ":" + item.role().name();
            if (processedKeys.contains(compositeKey)) {
                continue; // Skip duplicate entries in request
            }
            processedKeys.add(compositeKey);

            MemberSummaryDto member = genealogyQueryService.findMemberSummary(memberId)
                    .orElseThrow(() -> new ResourceNotFoundException("Thành viên không tồn tại trong gia phả: " + memberId));

            if (!clanId.equals(member.clanId())) {
                throw new BusinessRuleException(String.format(
                        "Thành viên %s (%s) không thuộc dòng họ hiện tại", memberId, member.fullName()));
            }

            memberNameMap.put(memberId, member.fullName());

            newPersonnelList.add(EventPersonnel.builder()
                    .eventId(eventId)
                    .memberId(memberId)
                    .eventRole(item.role())
                    .isReadOnly(false)
                    .assignedDate(Instant.now())
                    .build());
        }

        // Replace existing personnel
        eventPersonnelRepository.deleteAllByEventId(eventId);
        eventPersonnelRepository.flush();

        List<EventPersonnel> saved = eventPersonnelRepository.saveAllAndFlush(newPersonnelList);
        log.info("Assigned {} personnel members to event {}", saved.size(), eventId);

        return saved.stream()
                .map(p -> new EventPersonnelResponse(
                        p.getEventId(),
                        p.getMemberId(),
                        memberNameMap.get(p.getMemberId()),
                        p.getEventRole(),
                        p.getIsReadOnly(),
                        p.getAssignedDate()))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public EventDetailResponse getEventDetail(String eventId) {
        String clanId = TenantContext.getClanId();
        Event event = eventRepository.findByEventIdAndClanId(eventId, clanId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy sự kiện với mã: " + eventId));

        List<EventPersonnel> personnel = eventPersonnelRepository.findAllByEventId(eventId);
        List<EventPersonnelResponse> personnelResponses = personnel.stream()
                .map(p -> {
                    String memberName = genealogyQueryService.findMemberSummary(p.getMemberId())
                            .map(MemberSummaryDto::fullName)
                            .orElse(null);
                    return new EventPersonnelResponse(
                            p.getEventId(),
                            p.getMemberId(),
                            memberName,
                            p.getEventRole(),
                            p.getIsReadOnly(),
                            p.getAssignedDate());
                })
                .toList();

        int currentAttendees = (int) eventRepository.sumActiveAttendeesByEventIdAndClanId(eventId, clanId);
        return eventMapper.toDetailResponse(event, personnelResponses, currentAttendees);
    }

    @Override
    @Transactional(readOnly = true)
    public List<EventResponse> listEvents() {
        String clanId = TenantContext.getClanId();
        List<Event> events = eventRepository.findAllByClanIdOrderByEventDatetimeDesc(clanId);
        return events.stream()
                .map(this::toEnrichedEventResponse)
                .toList();
    }

    private EventResponse toEnrichedEventResponse(Event event) {
        int currentAttendees = (int) eventRepository.sumActiveAttendeesByEventIdAndClanId(
                event.getEventId(), event.getClanId());
        return eventMapper.toResponse(event, currentAttendees);
    }

    @Override
    @Transactional(readOnly = true)
    public List<EventNotificationResponse> getEventNotifications(String eventId) {
        String clanId = TenantContext.getClanId();
        if (!eventRepository.existsByEventIdAndClanId(eventId, clanId)) {
            throw new ResourceNotFoundException("Không tìm thấy sự kiện với mã: " + eventId);
        }

        List<EventNotification> notifications = eventNotificationRepository.findAllByEventIdOrderBySentTimeDesc(eventId);
        return eventMapper.toNotificationResponseList(notifications);
    }

    @Override
    @Transactional(readOnly = true)
    public EventBudgetSummaryResponse getEventBudgetSummary(String eventId) {
        String clanId = TenantContext.getClanId();
        if (eventId == null || eventId.isBlank()) {
            throw new BadRequestException("Mã sự kiện không được để trống");
        }
        String normalizedEventId = eventId.trim();

        Event event = eventRepository.findByEventIdAndClanId(normalizedEventId, clanId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy sự kiện với mã: " + normalizedEventId));

        BigDecimal estimatedBudget = event.getEstimatedBudget() != null
                ? event.getEstimatedBudget()
                : BigDecimal.ZERO;

        List<TaskBudgetAggregateProjection> projections = (eventTaskRepository != null)
                ? eventTaskRepository.aggregateTaskBudgetsByEventId(normalizedEventId)
                : Collections.emptyList();

        BigDecimal totalAdvanceBudget = BigDecimal.ZERO;
        BigDecimal totalAdvanceAmount = BigDecimal.ZERO;
        BigDecimal totalActualExpenseAmount = BigDecimal.ZERO;
        BigDecimal verifiedActualExpenseAmount = BigDecimal.ZERO;

        int totalAdvanceVoucherCount = 0;
        int totalSettlementVoucherCount = 0;
        int needsExplanationVoucherCount = 0;
        boolean hasTaskOverBudget = false;

        List<TaskBudgetBreakdownResponse> taskBreakdowns = new ArrayList<>();
        for (TaskBudgetAggregateProjection p : projections) {
            BigDecimal taskBudget = p.getAdvanceBudget() != null ? p.getAdvanceBudget() : BigDecimal.ZERO;
            BigDecimal taskAdvance = p.getTotalAdvanceAmount() != null ? p.getTotalAdvanceAmount() : BigDecimal.ZERO;
            BigDecimal taskActual = p.getTotalActualExpenseAmount() != null ? p.getTotalActualExpenseAmount() : BigDecimal.ZERO;
            BigDecimal taskVerifiedActual = p.getVerifiedActualExpenseAmount() != null ? p.getVerifiedActualExpenseAmount() : BigDecimal.ZERO;

            int advCount = p.getAdvanceVoucherCount() != null ? p.getAdvanceVoucherCount() : 0;
            int setCount = p.getSettlementVoucherCount() != null ? p.getSettlementVoucherCount() : 0;
            int expCount = p.getNeedsExplanationCount() != null ? p.getNeedsExplanationCount() : 0;

            BigDecimal advanceVariance = taskBudget.subtract(taskAdvance);
            BigDecimal actualVariance = taskBudget.subtract(taskActual);

            boolean isAdvOverTask = taskAdvance.compareTo(taskBudget) > 0;
            boolean isActOverTask = taskActual.compareTo(taskBudget) > 0;
            boolean isTaskOver = isAdvOverTask || isActOverTask;
            if (isTaskOver) {
                hasTaskOverBudget = true;
            }

            taskBreakdowns.add(new TaskBudgetBreakdownResponse(
                    p.getTaskId(),
                    p.getTaskName(),
                    p.getTaskStatus(),
                    p.getAssignedMemberId(),
                    p.getAssignedBranchId(),
                    taskBudget,
                    taskAdvance,
                    taskActual,
                    taskVerifiedActual,
                    advCount,
                    setCount,
                    expCount,
                    advanceVariance,
                    actualVariance,
                    isAdvOverTask,
                    isActOverTask,
                    isTaskOver
            ));

            totalAdvanceBudget = totalAdvanceBudget.add(taskBudget);
            totalAdvanceAmount = totalAdvanceAmount.add(taskAdvance);
            totalActualExpenseAmount = totalActualExpenseAmount.add(taskActual);
            verifiedActualExpenseAmount = verifiedActualExpenseAmount.add(taskVerifiedActual);
            totalAdvanceVoucherCount += advCount;
            totalSettlementVoucherCount += setCount;
            needsExplanationVoucherCount += expCount;
        }

        BigDecimal unallocatedBudget = estimatedBudget.subtract(totalAdvanceBudget);
        BigDecimal remainingBudgetByAdvance = estimatedBudget.subtract(totalAdvanceAmount);
        BigDecimal remainingBudgetByActualExpense = estimatedBudget.subtract(totalActualExpenseAmount);

        boolean isAdvanceOverEstimated = totalAdvanceAmount.compareTo(estimatedBudget) > 0;
        boolean isActualExpenseOverEstimated = totalActualExpenseAmount.compareTo(estimatedBudget) > 0;
        boolean isTaskBudgetOverEstimated = totalAdvanceBudget.compareTo(estimatedBudget) > 0;
        boolean isOverBudget = isAdvanceOverEstimated || isActualExpenseOverEstimated;

        BigDecimal maxSpentOrAdvanced = totalAdvanceAmount.max(totalActualExpenseAmount);
        BigDecimal overrunAmount = maxSpentOrAdvanced.compareTo(estimatedBudget) > 0
                ? maxSpentOrAdvanced.subtract(estimatedBudget)
                : BigDecimal.ZERO;

        BigDecimal taskBudgetUtilizationPercent = calculateUtilizationPercent(totalAdvanceBudget, estimatedBudget);
        BigDecimal advanceUtilizationPercent = calculateUtilizationPercent(totalAdvanceAmount, estimatedBudget);
        BigDecimal actualExpenseUtilizationPercent = calculateUtilizationPercent(totalActualExpenseAmount, estimatedBudget);

        List<String> warningMessages = new ArrayList<>();
        if (isAdvanceOverEstimated) {
            warningMessages.add(String.format(
                    "Cảnh báo: Tổng tiền tạm ứng (%s VNĐ) đã vượt ngân sách dự kiến của sự kiện (%s VNĐ) một khoản là %s VNĐ.",
                    totalAdvanceAmount.toPlainString(),
                    estimatedBudget.toPlainString(),
                    totalAdvanceAmount.subtract(estimatedBudget).toPlainString()
            ));
        }
        if (isActualExpenseOverEstimated) {
            warningMessages.add(String.format(
                    "Cảnh báo: Tổng tiền thực chi quyết toán (%s VNĐ) đã vượt ngân sách dự kiến của sự kiện (%s VNĐ) một khoản là %s VNĐ.",
                    totalActualExpenseAmount.toPlainString(),
                    estimatedBudget.toPlainString(),
                    totalActualExpenseAmount.subtract(estimatedBudget).toPlainString()
            ));
        }
        if (isTaskBudgetOverEstimated) {
            warningMessages.add(String.format(
                    "Lưu ý: Tổng dự toán phân bổ cho các hạng mục công việc (%s VNĐ) đang vượt ngân sách dự kiến của sự kiện (%s VNĐ).",
                    totalAdvanceBudget.toPlainString(),
                    estimatedBudget.toPlainString()
            ));
        }
        if (hasTaskOverBudget) {
            warningMessages.add("Lưu ý: Có hạng mục công việc phát sinh tạm ứng hoặc thực chi vượt mức ngân sách dự kiến của hạng mục đó.");
        }

        String warningLevel;
        if (isOverBudget) {
            warningLevel = "OVERRUN_WARNING";
        } else if (isTaskBudgetOverEstimated || hasTaskOverBudget) {
            warningLevel = "ALLOCATION_EXCEEDED";
        } else {
            warningLevel = "NORMAL";
        }

        return new EventBudgetSummaryResponse(
                event.getEventId(),
                event.getEventName(),
                event.getStatus(),
                estimatedBudget,
                totalAdvanceBudget,
                totalAdvanceAmount,
                totalActualExpenseAmount,
                verifiedActualExpenseAmount,
                unallocatedBudget,
                remainingBudgetByAdvance,
                remainingBudgetByActualExpense,
                overrunAmount,
                taskBudgetUtilizationPercent,
                advanceUtilizationPercent,
                actualExpenseUtilizationPercent,
                isOverBudget,
                isAdvanceOverEstimated,
                isActualExpenseOverEstimated,
                isTaskBudgetOverEstimated,
                hasTaskOverBudget,
                warningLevel,
                warningMessages,
                taskBreakdowns.size(),
                totalAdvanceVoucherCount,
                totalSettlementVoucherCount,
                needsExplanationVoucherCount,
                taskBreakdowns
        );
    }

    private BigDecimal calculateUtilizationPercent(BigDecimal amount, BigDecimal estimatedBudget) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            return new BigDecimal("0.00");
        }
        if (estimatedBudget == null || estimatedBudget.compareTo(BigDecimal.ZERO) <= 0) {
            return new BigDecimal("100.00");
        }
        return amount.multiply(BigDecimal.valueOf(100))
                .divide(estimatedBudget, 2, RoundingMode.HALF_UP);
    }

    private String generateUniqueEventId() {
        for (int i = 0; i < 5; i++) {
            String candidate = eventIdGenerator.generate();
            if (!eventRepository.existsById(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("Không thể sinh mã sự kiện duy nhất sau 5 lần thử");
    }
}
