package com.familyfund.event.internal.service;

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
import com.familyfund.event.internal.dto.response.EventDetailResponse;
import com.familyfund.event.internal.dto.response.EventNotificationResponse;
import com.familyfund.event.internal.dto.response.EventPersonnelResponse;
import com.familyfund.event.internal.dto.response.EventResponse;
import com.familyfund.event.internal.entity.Event;
import com.familyfund.event.internal.entity.EventNotification;
import com.familyfund.event.internal.entity.EventPersonnel;
import com.familyfund.event.internal.mapper.EventMapper;
import com.familyfund.event.internal.repository.EventNotificationRepository;
import com.familyfund.event.internal.repository.EventPersonnelRepository;
import com.familyfund.event.internal.repository.EventRepository;
import com.familyfund.event.internal.util.EventIdGenerator;
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

@Slf4j
@Service
@RequiredArgsConstructor
public class EventServiceImpl implements EventService {

    private final EventRepository eventRepository;
    private final EventPersonnelRepository eventPersonnelRepository;
    private final EventNotificationRepository eventNotificationRepository;
    private final EventMapper eventMapper;
    private final EventIdGenerator eventIdGenerator;
    private final CurrentUserProvider currentUserProvider;
    private final GenealogyQueryService genealogyQueryService;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    @Transactional
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
                .status(EventStatus.Draft)
                .createdBy(createdByAccountId)
                .build();

        Event saved = eventRepository.saveAndFlush(event);
        log.info("Created event {} ({}) for clan {} by user {}", eventId, saved.getEventName(), clanId, createdByAccountId);
        return eventMapper.toResponse(saved);
    }

    @Override
    @Transactional
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

        Event updated = eventRepository.saveAndFlush(event);
        log.info("Event {} updated by clan {}", eventId, clanId);
        return eventMapper.toResponse(updated);
    }

    @Override
    @Transactional
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
        return eventMapper.toResponse(updated);
    }

    @Override
    @Transactional
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

        return eventMapper.toDetailResponse(event, personnelResponses);
    }

    @Override
    @Transactional(readOnly = true)
    public List<EventResponse> listEvents() {
        String clanId = TenantContext.getClanId();
        List<Event> events = eventRepository.findAllByClanIdOrderByEventDatetimeDesc(clanId);
        return eventMapper.toResponseList(events);
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
