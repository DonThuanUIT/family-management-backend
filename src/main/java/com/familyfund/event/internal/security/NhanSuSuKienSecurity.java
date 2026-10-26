package com.familyfund.event.internal.security;

import com.familyfund.core.security.CurrentUserProvider;
import com.familyfund.core.security.SystemRoles;
import com.familyfund.core.tenant.TenantContext;
import com.familyfund.event.api.dto.EventRole;
import com.familyfund.event.api.dto.EventStatus;
import com.familyfund.event.internal.entity.Event;
import com.familyfund.event.internal.entity.EventPersonnel;
import com.familyfund.event.internal.repository.EventPersonnelRepository;
import com.familyfund.event.internal.repository.EventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Middleware Contextual RBAC for events.
 * Registered as Spring bean "nhanSuSuKien" so it can be evaluated directly in Spring Security
 * expressions, e.g. {@code @PreAuthorize("@nhanSuSuKien.xacThucQuyenTheoNguCanh(#eventId, 'HeadOfOrganizer')")}.
 */
@Slf4j
@Component("nhanSuSuKien")
@RequiredArgsConstructor
public class NhanSuSuKienSecurity {

    private final CurrentUserProvider currentUserProvider;
    private final EventRepository eventRepository;
    private final EventPersonnelRepository eventPersonnelRepository;

    /**
     * Contextual RBAC authorization check for an event.
     *
     * @param eventId the event ID
     * @param roles   the allowed contextual roles for this action (e.g. "HeadOfOrganizer", "EventTreasurer")
     * @return true if the caller is authorized, false otherwise
     */
    public boolean xacThucQuyenTheoNguCanh(String eventId, String... roles) {
        if (eventId == null || eventId.isBlank()) {
            return false;
        }

        String currentClanId = TenantContext.getClanId();

        // 1. System administrators have global access
        if (currentUserProvider.hasAuthority(SystemRoles.ADMIN)) {
            return true;
        }

        // 2. Clan head has full authority over all events in their clan
        if (currentUserProvider.hasAuthority(SystemRoles.CLAN_HEAD)) {
            return eventRepository.existsByEventIdAndClanId(eventId, currentClanId);
        }

        // 3. For other users, verify they are linked to a genealogy member profile
        Optional<String> currentMemberIdOpt = currentUserProvider.currentMemberId();
        if (currentMemberIdOpt.isEmpty()) {
            log.debug("Access denied: caller has no linked member_id");
            return false;
        }
        String currentMemberId = currentMemberIdOpt.get();

        // 4. Verify event exists within the current tenant clan
        Optional<Event> eventOpt = eventRepository.findByEventIdAndClanId(eventId, currentClanId);
        if (eventOpt.isEmpty()) {
            log.debug("Access denied: event {} not found in clan {}", eventId, currentClanId);
            return false;
        }
        Event event = eventOpt.get();

        // 5. If the event is closed, all modifications are blocked (read-only mode)
        if (event.getStatus() == EventStatus.Closed) {
            log.debug("Access denied: event {} is Closed, contextual write permissions are frozen", eventId);
            return false;
        }

        // 6. Check if caller has any of the required contextual roles assigned to this event
        List<EventPersonnel> personnelList = eventPersonnelRepository.findAllByEventIdAndMemberId(eventId, currentMemberId);
        if (personnelList.isEmpty()) {
            log.debug("Access denied: member {} has no assigned roles in event {}", currentMemberId, eventId);
            return false;
        }

        Set<String> requiredRoleSet = Arrays.stream(roles).collect(Collectors.toSet());
        boolean hasMatchingRole = personnelList.stream()
                .filter(p -> !Boolean.TRUE.equals(p.getIsReadOnly()))
                .anyMatch(p -> requiredRoleSet.contains(p.getEventRole().name()));

        if (!hasMatchingRole) {
            log.debug("Access denied: member {} does not possess any of required roles {} in event {}",
                    currentMemberId, requiredRoleSet, eventId);
        }

        return hasMatchingRole;
    }

    /**
     * Overload accepting typed EventRole enum values.
     */
    public boolean xacThucQuyenTheoNguCanh(String eventId, EventRole... roles) {
        if (roles == null || roles.length == 0) {
            return false;
        }
        String[] stringRoles = Arrays.stream(roles)
                .map(EventRole::name)
                .toArray(String[]::new);
        return xacThucQuyenTheoNguCanh(eventId, stringRoles);
    }
}
