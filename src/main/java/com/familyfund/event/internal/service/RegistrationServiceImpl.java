package com.familyfund.event.internal.service;

import com.familyfund.core.exception.BadRequestException;
import com.familyfund.core.exception.BusinessRuleException;
import com.familyfund.core.exception.DuplicateResourceException;
import com.familyfund.core.exception.ResourceNotFoundException;
import com.familyfund.core.security.CurrentUserProvider;
import com.familyfund.core.security.SystemRoles;
import com.familyfund.core.tenant.TenantContext;
import com.familyfund.event.api.dto.EventRole;
import com.familyfund.event.api.dto.EventStatus;
import com.familyfund.event.api.dto.RegistrationStatus;
import com.familyfund.event.api.event.FamilyRegistrationSubmittedDomainEvent;
import com.familyfund.event.api.event.RegistrationModificationRequestedDomainEvent;
import com.familyfund.event.api.event.RegistrationReviewedDomainEvent;
import com.familyfund.event.internal.dto.request.RegisterAttendeesRequest;
import com.familyfund.event.internal.dto.request.RequestCancelRegistrationRequest;
import com.familyfund.event.internal.dto.request.RequestModifyRegistrationRequest;
import com.familyfund.event.internal.dto.request.ReviewRegistrationRequest;
import com.familyfund.event.internal.dto.response.RegistrationMemberDetailResponse;
import com.familyfund.event.internal.dto.response.RegistrationResponse;
import com.familyfund.event.internal.dto.response.RegistrationSummaryResponse;
import com.familyfund.event.internal.entity.Event;
import com.familyfund.event.internal.entity.FamilyRegistration;
import com.familyfund.event.internal.entity.RegistrationMemberDetail;
import com.familyfund.event.internal.mapper.RegistrationMapper;
import com.familyfund.event.internal.repository.EventPersonnelRepository;
import com.familyfund.event.internal.repository.EventRepository;
import com.familyfund.event.internal.repository.FamilyRegistrationRepository;
import com.familyfund.event.internal.repository.RegistrationAggregateProjection;
import com.familyfund.event.internal.repository.RegistrationMemberDetailRepository;
import com.familyfund.event.internal.util.RegistrationIdGenerator;
import com.familyfund.genealogy.api.GenealogyQueryService;
import com.familyfund.genealogy.api.dto.MemberSummaryDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service implementation for managing family event registrations,
 * headcount aggregation, and change requests (US-06).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RegistrationServiceImpl implements RegistrationService {

    private final EventRepository eventRepository;
    private final FamilyRegistrationRepository familyRegistrationRepository;
    private final RegistrationMemberDetailRepository registrationMemberDetailRepository;
    private final EventPersonnelRepository eventPersonnelRepository;
    private final GenealogyQueryService genealogyQueryService;
    private final CurrentUserProvider currentUserProvider;
    private final RegistrationIdGenerator registrationIdGenerator;
    private final RegistrationMapper registrationMapper;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    @Transactional
    public RegistrationResponse registerAttendees(String eventId, RegisterAttendeesRequest request) {
        String clanId = TenantContext.getClanId();

        // 1. Validate event existence and clan tenancy
        Event event = eventRepository.findByEventIdAndClanId(eventId, clanId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy sự kiện với mã: " + eventId));

        // 2. Precondition: Event must be in RegistrationOpen status
        if (event.getStatus() != EventStatus.RegistrationOpen) {
            throw new BusinessRuleException(String.format(
                    "Sự kiện '%s' hiện không ở trạng thái mở tiếp nhận đăng ký (trạng thái hiện tại: %s)",
                    event.getEventName(), event.getStatus()));
        }

        // 3. Resolve representative member ID based on caller roles
        String callerMemberId = currentUserProvider.currentMemberId().orElse(null);
        boolean isPrivileged = isPrivilegedOrganizer(eventId, callerMemberId);

        String representativeMemberId;
        if (isPrivileged && request.representativeMemberId() != null && !request.representativeMemberId().isBlank()) {
            representativeMemberId = request.representativeMemberId().trim();
        } else {
            if (callerMemberId == null || callerMemberId.isBlank()) {
                throw new BadRequestException("Tài khoản chưa được liên kết với hồ sơ thành viên dòng họ để đại diện đăng ký");
            }
            representativeMemberId = callerMemberId;
        }

        // 4. Validate representative member exists in genealogy and belongs to current clan
        MemberSummaryDto repMember = genealogyQueryService.findMemberSummary(representativeMemberId)
                .orElseThrow(() -> new ResourceNotFoundException("Thành viên đại diện không tồn tại trong gia phả: " + representativeMemberId));
        if (!clanId.equals(repMember.clanId())) {
            throw new BusinessRuleException(String.format(
                    "Thành viên đại diện %s (%s) không thuộc dòng họ hiện tại", representativeMemberId, repMember.fullName()));
        }

        // 5. Invariant: Total meals must not exceed total attendees
        int totalAttendees = request.totalAttendees();
        int vegMeals = request.vegetarianMeals() != null ? request.vegetarianMeals() : 0;
        int savMeals = request.savoryMeals() != null ? request.savoryMeals() : 0;
        if (vegMeals + savMeals > totalAttendees) {
            throw new BusinessRuleException(String.format(
                    "Tổng số suất ăn đăng ký (%d) không được vượt quá tổng số người tham dự (%d)",
                    vegMeals + savMeals, totalAttendees));
        }

        // 6. Prevent duplicate active registrations for the same representative in this event
        if (familyRegistrationRepository.existsByEventIdAndRepresentativeMemberIdAndStatusNot(
                eventId, representativeMemberId, RegistrationStatus.Cancelled)) {
            throw new DuplicateResourceException(String.format(
                    "Hộ gia đình của thành viên %s (%s) đã có lượt đăng ký tham gia sự kiện này. Vui lòng gửi yêu cầu thay đổi nếu muốn cập nhật thông tin.",
                    representativeMemberId, repMember.fullName()));
        }

        String registrationId = generateUniqueRegistrationId();

        // 7. Process attending family members details if supplied
        List<RegistrationMemberDetail> memberDetails = new ArrayList<>();
        List<RegistrationMemberDetailResponse> detailResponses = new ArrayList<>();

        if (request.attendingMemberIds() != null && !request.attendingMemberIds().isEmpty()) {
            Set<String> distinctMemberIds = request.attendingMemberIds().stream()
                    .filter(id -> id != null && !id.isBlank())
                    .map(String::trim)
                    .collect(Collectors.toCollection(LinkedHashSet::new));

            if (distinctMemberIds.size() > totalAttendees) {
                throw new BusinessRuleException(String.format(
                        "Danh sách thành viên gia đình đi kèm (%d người) vượt quá tổng số người tham dự đăng ký (%d người)",
                        distinctMemberIds.size(), totalAttendees));
            }

            for (String memberId : distinctMemberIds) {
                MemberSummaryDto attendee = genealogyQueryService.findMemberSummary(memberId)
                        .orElseThrow(() -> new ResourceNotFoundException("Thành viên đi kèm không tồn tại trong gia phả: " + memberId));
                if (!clanId.equals(attendee.clanId())) {
                    throw new BusinessRuleException(String.format(
                            "Thành viên đi kèm %s (%s) không thuộc dòng họ hiện tại", memberId, attendee.fullName()));
                }
                memberDetails.add(RegistrationMemberDetail.builder()
                        .registrationId(registrationId)
                        .memberId(memberId)
                        .isCheckedIn(false)
                        .build());
                detailResponses.add(new RegistrationMemberDetailResponse(memberId, attendee.fullName(), false));
            }
        }

        // 8. Build and persist FamilyRegistration entity
        FamilyRegistration registration = FamilyRegistration.builder()
                .registrationId(registrationId)
                .eventId(eventId)
                .representativeMemberId(representativeMemberId)
                .totalAttendees(totalAttendees)
                .vegetarianMeals(vegMeals)
                .savoryMeals(savMeals)
                .dietaryRequirements(request.dietaryRequirements() != null ? request.dietaryRequirements().trim() : null)
                .transportationNotes(request.transportationNotes() != null ? request.transportationNotes().trim() : null)
                .registrationDate(Instant.now())
                .status(RegistrationStatus.Confirmed)
                .build();

        FamilyRegistration saved = familyRegistrationRepository.save(registration);
        if (!memberDetails.isEmpty()) {
            registrationMemberDetailRepository.saveAll(memberDetails);
        }

        // 9. Publish Domain Event
        String accountId = currentUserProvider.currentAccountId().orElse("SYSTEM");
        eventPublisher.publishEvent(new FamilyRegistrationSubmittedDomainEvent(
                saved.getRegistrationId(),
                saved.getEventId(),
                clanId,
                saved.getRepresentativeMemberId(),
                saved.getTotalAttendees(),
                saved.getVegetarianMeals(),
                saved.getSavoryMeals(),
                saved.getRegistrationDate(),
                accountId
        ));

        log.info("Family registration {} created for event {} by representative {} (attendees: {}, meals: {}/{})",
                saved.getRegistrationId(), eventId, representativeMemberId, totalAttendees, vegMeals, savMeals);

        return registrationMapper.toResponse(saved, repMember.fullName(), detailResponses);
    }

    @Override
    @Transactional(readOnly = true)
    public RegistrationSummaryResponse getRegistrationSummary(String eventId) {
        String clanId = TenantContext.getClanId();
        if (!eventRepository.existsByEventIdAndClanId(eventId, clanId)) {
            throw new ResourceNotFoundException("Không tìm thấy sự kiện với mã: " + eventId);
        }

        RegistrationAggregateProjection projection = familyRegistrationRepository.getAggregateSummary(eventId, clanId);
        return registrationMapper.toSummaryResponse(eventId, projection);
    }

    @Override
    @Transactional(readOnly = true)
    public List<RegistrationResponse> listRegistrations(String eventId) {
        String clanId = TenantContext.getClanId();
        if (!eventRepository.existsByEventIdAndClanId(eventId, clanId)) {
            throw new ResourceNotFoundException("Không tìm thấy sự kiện với mã: " + eventId);
        }

        List<FamilyRegistration> registrations =
                familyRegistrationRepository.findAllByEvent_EventIdAndEvent_ClanIdOrderByRegistrationDateDesc(eventId, clanId);

        return registrations.stream()
                .map(this::enrichRegistration)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Page<RegistrationResponse> listRegistrations(String eventId, Pageable pageable) {
        String clanId = TenantContext.getClanId();
        if (!eventRepository.existsByEventIdAndClanId(eventId, clanId)) {
            throw new ResourceNotFoundException("Không tìm thấy sự kiện với mã: " + eventId);
        }

        Page<FamilyRegistration> page =
                familyRegistrationRepository.findAllByEvent_EventIdAndEvent_ClanIdOrderByRegistrationDateDesc(eventId, clanId, pageable);

        List<RegistrationResponse> enriched = page.getContent().stream()
                .map(this::enrichRegistration)
                .toList();

        return new PageImpl<>(enriched, pageable, page.getTotalElements());
    }

    @Override
    @Transactional(readOnly = true)
    public RegistrationResponse getRegistrationById(String eventId, String registrationId) {
        String clanId = TenantContext.getClanId();
        FamilyRegistration registration = familyRegistrationRepository.findByRegistrationIdAndEvent_ClanId(registrationId, clanId)
                .filter(r -> r.getEventId().equals(eventId))
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy thông tin đăng ký với mã: " + registrationId));

        return enrichRegistration(registration);
    }

    @Override
    @Transactional(readOnly = true)
    public RegistrationResponse getMyRegistration(String eventId) {
        String clanId = TenantContext.getClanId();
        String currentMemberId = currentUserProvider.currentMemberId()
                .orElseThrow(() -> new BadRequestException("Tài khoản chưa được liên kết với hồ sơ thành viên dòng họ"));

        FamilyRegistration registration = familyRegistrationRepository
                .findByEventIdAndRepresentativeMemberIdAndEvent_ClanId(eventId, currentMemberId, clanId)
                .filter(r -> r.getStatus() != RegistrationStatus.Cancelled)
                .orElseThrow(() -> new ResourceNotFoundException("Hộ gia đình của bạn chưa đăng ký tham gia sự kiện này"));

        return enrichRegistration(registration);
    }

    @Override
    @Transactional
    public RegistrationResponse requestModification(String eventId, String registrationId, RequestModifyRegistrationRequest request) {
        String clanId = TenantContext.getClanId();
        Event event = eventRepository.findByEventIdAndClanId(eventId, clanId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy sự kiện với mã: " + eventId));

        if (event.getStatus() != EventStatus.RegistrationOpen) {
            throw new BusinessRuleException(String.format(
                    "Sự kiện '%s' hiện không ở trạng thái mở tiếp nhận đăng ký, không thể yêu cầu thay đổi",
                    event.getEventName()));
        }

        FamilyRegistration registration = familyRegistrationRepository.findByRegistrationIdAndEvent_ClanId(registrationId, clanId)
                .filter(r -> r.getEventId().equals(eventId))
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy lượt đăng ký: " + registrationId));

        String callerMemberId = currentUserProvider.currentMemberId().orElse(null);
        if (!canModifyRegistration(registration, callerMemberId, eventId)) {
            throw new AccessDeniedException("Bạn không có quyền yêu cầu chỉnh sửa lượt đăng ký này");
        }

        if (registration.getStatus() == RegistrationStatus.Cancelled) {
            throw new BusinessRuleException("Lượt đăng ký đã bị hủy, không thể yêu cầu chỉnh sửa");
        }

        int proposedTotal = request.proposedTotalAttendees();
        int proposedVeg = request.proposedVegetarianMeals() != null ? request.proposedVegetarianMeals() : 0;
        int proposedSav = request.proposedSavoryMeals() != null ? request.proposedSavoryMeals() : 0;
        if (proposedVeg + proposedSav > proposedTotal) {
            throw new BusinessRuleException(String.format(
                    "Tổng số suất ăn đề xuất (%d) không được vượt quá tổng số người tham dự đề xuất (%d)",
                    proposedVeg + proposedSav, proposedTotal));
        }

        registration.setStatus(RegistrationStatus.PendingModification);
        registration.setProposedTotalAttendees(proposedTotal);
        registration.setProposedVegetarianMeals(proposedVeg);
        registration.setProposedSavoryMeals(proposedSav);
        registration.setProposedDietaryRequirements(request.proposedDietaryRequirements() != null ? request.proposedDietaryRequirements().trim() : null);
        registration.setProposedTransportationNotes(request.proposedTransportationNotes() != null ? request.proposedTransportationNotes().trim() : null);
        registration.setModificationReason(request.modificationReason().trim());
        registration.setReviewedByAccountId(null);
        registration.setReviewedAt(null);
        registration.setRejectionReason(null);
        registration.setUpdatedAt(Instant.now());

        FamilyRegistration updated = familyRegistrationRepository.save(registration);

        String accountId = currentUserProvider.currentAccountId().orElse("SYSTEM");
        eventPublisher.publishEvent(new RegistrationModificationRequestedDomainEvent(
                updated.getRegistrationId(),
                eventId,
                clanId,
                updated.getRepresentativeMemberId(),
                "MODIFICATION",
                request.modificationReason(),
                Instant.now(),
                accountId
        ));

        log.info("Registration modification requested for registration {} in event {} by user {}",
                registrationId, eventId, accountId);

        return enrichRegistration(updated);
    }

    @Override
    @Transactional
    public RegistrationResponse requestCancellation(String eventId, String registrationId, RequestCancelRegistrationRequest request) {
        String clanId = TenantContext.getClanId();
        Event event = eventRepository.findByEventIdAndClanId(eventId, clanId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy sự kiện với mã: " + eventId));

        if (event.getStatus() != EventStatus.RegistrationOpen) {
            throw new BusinessRuleException(String.format(
                    "Sự kiện '%s' hiện không ở trạng thái mở tiếp nhận đăng ký, không thể yêu cầu hủy",
                    event.getEventName()));
        }

        FamilyRegistration registration = familyRegistrationRepository.findByRegistrationIdAndEvent_ClanId(registrationId, clanId)
                .filter(r -> r.getEventId().equals(eventId))
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy lượt đăng ký: " + registrationId));

        String callerMemberId = currentUserProvider.currentMemberId().orElse(null);
        if (!canModifyRegistration(registration, callerMemberId, eventId)) {
            throw new AccessDeniedException("Bạn không có quyền yêu cầu hủy lượt đăng ký này");
        }

        if (registration.getStatus() == RegistrationStatus.Cancelled) {
            throw new BusinessRuleException("Lượt đăng ký này đã ở trạng thái hủy");
        }

        registration.setStatus(RegistrationStatus.PendingCancellation);
        registration.setModificationReason(request.cancellationReason().trim());
        registration.setReviewedByAccountId(null);
        registration.setReviewedAt(null);
        registration.setRejectionReason(null);
        registration.setUpdatedAt(Instant.now());

        FamilyRegistration updated = familyRegistrationRepository.save(registration);

        String accountId = currentUserProvider.currentAccountId().orElse("SYSTEM");
        eventPublisher.publishEvent(new RegistrationModificationRequestedDomainEvent(
                updated.getRegistrationId(),
                eventId,
                clanId,
                updated.getRepresentativeMemberId(),
                "CANCELLATION",
                request.cancellationReason(),
                Instant.now(),
                accountId
        ));

        log.info("Registration cancellation requested for registration {} in event {} by user {}",
                registrationId, eventId, accountId);

        return enrichRegistration(updated);
    }

    @Override
    @Transactional
    public RegistrationResponse reviewRegistrationRequest(String eventId, String registrationId, ReviewRegistrationRequest request) {
        String clanId = TenantContext.getClanId();
        Event event = eventRepository.findByEventIdAndClanId(eventId, clanId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy sự kiện với mã: " + eventId));

        if (event.getStatus() == EventStatus.Closed) {
            throw new BusinessRuleException("Sự kiện đã kết thúc và khóa sổ, không thể xét duyệt đăng ký");
        }

        String callerMemberId = currentUserProvider.currentMemberId().orElse(null);
        if (!isPrivilegedOrganizer(eventId, callerMemberId)) {
            throw new AccessDeniedException("Chỉ Trưởng tộc, Ban quản trị hoặc Trưởng ban tổ chức mới có quyền xét duyệt đăng ký");
        }

        FamilyRegistration registration = familyRegistrationRepository.findByRegistrationIdAndEvent_ClanId(registrationId, clanId)
                .filter(r -> r.getEventId().equals(eventId))
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy lượt đăng ký: " + registrationId));

        if (!registration.getStatus().isPendingReview()) {
            throw new BusinessRuleException("Lượt đăng ký này không có yêu cầu sửa đổi hoặc hủy nào đang chờ duyệt");
        }

        String reviewerAccountId = currentUserProvider.currentAccountId().orElse("ORGANIZER");
        Instant now = Instant.now();

        if (Boolean.TRUE.equals(request.approved())) {
            if (registration.getStatus() == RegistrationStatus.PendingModification) {
                registration.setTotalAttendees(registration.getProposedTotalAttendees());
                registration.setVegetarianMeals(registration.getProposedVegetarianMeals());
                registration.setSavoryMeals(registration.getProposedSavoryMeals());
                if (registration.getProposedDietaryRequirements() != null) {
                    registration.setDietaryRequirements(registration.getProposedDietaryRequirements());
                }
                if (registration.getProposedTransportationNotes() != null) {
                    registration.setTransportationNotes(registration.getProposedTransportationNotes());
                }
                registration.setStatus(RegistrationStatus.Confirmed);
            } else if (registration.getStatus() == RegistrationStatus.PendingCancellation) {
                registration.setStatus(RegistrationStatus.Cancelled);
            }
            registration.setRejectionReason(null);
        } else {
            if (request.rejectionReason() == null || request.rejectionReason().isBlank()) {
                throw new BadRequestException("Vui lòng cung cấp lý do từ chối yêu cầu");
            }
            registration.setStatus(RegistrationStatus.Confirmed);
            registration.setRejectionReason(request.rejectionReason().trim());
        }

        registration.setReviewedByAccountId(reviewerAccountId);
        registration.setReviewedAt(now);
        registration.setUpdatedAt(now);

        FamilyRegistration saved = familyRegistrationRepository.save(registration);

        eventPublisher.publishEvent(new RegistrationReviewedDomainEvent(
                saved.getRegistrationId(),
                eventId,
                clanId,
                saved.getRepresentativeMemberId(),
                Boolean.TRUE.equals(request.approved()),
                reviewerAccountId,
                saved.getRejectionReason(),
                now
        ));

        log.info("Registration {} reviewed (approved: {}) by account {}",
                registrationId, request.approved(), reviewerAccountId);

        return enrichRegistration(saved);
    }

    private boolean isPrivilegedOrganizer(String eventId, String callerMemberId) {
        if (currentUserProvider.hasAuthority(SystemRoles.ADMIN) || currentUserProvider.hasAuthority(SystemRoles.CLAN_HEAD)) {
            return true;
        }
        return callerMemberId != null && eventPersonnelRepository.existsByEventIdAndMemberIdAndEventRole(
                eventId, callerMemberId, EventRole.HeadOfOrganizer);
    }

    private boolean canModifyRegistration(FamilyRegistration registration, String callerMemberId, String eventId) {
        if (callerMemberId != null && callerMemberId.equals(registration.getRepresentativeMemberId())) {
            return true;
        }
        return isPrivilegedOrganizer(eventId, callerMemberId);
    }

    private RegistrationResponse enrichRegistration(FamilyRegistration registration) {
        String repName = genealogyQueryService.findMemberSummary(registration.getRepresentativeMemberId())
                .map(MemberSummaryDto::fullName)
                .orElse(registration.getRepresentativeMemberId());

        List<RegistrationMemberDetail> details =
                registrationMemberDetailRepository.findAllByRegistrationId(registration.getRegistrationId());

        List<RegistrationMemberDetailResponse> detailResponses = details.stream()
                .map(d -> {
                    String attendeeName = genealogyQueryService.findMemberSummary(d.getMemberId())
                            .map(MemberSummaryDto::fullName)
                            .orElse(d.getMemberId());
                    return registrationMapper.toMemberDetailResponse(d, attendeeName);
                })
                .toList();

        return registrationMapper.toResponse(registration, repName, detailResponses);
    }

    private String generateUniqueRegistrationId() {
        for (int i = 0; i < 5; i++) {
            String candidate = registrationIdGenerator.generate();
            if (!familyRegistrationRepository.existsById(candidate)) {
                return candidate;
            }
        }
        return RegistrationIdGenerator.PREFIX + UUID.randomUUID().toString().replace("-", "").substring(0, 14).toUpperCase();
    }
}

