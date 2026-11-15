package com.familyfund.contribution.internal.security;

import com.familyfund.contribution.internal.entity.Contribution;
import com.familyfund.contribution.internal.repository.ContributionRepository;
import com.familyfund.core.security.CurrentUserProvider;
import com.familyfund.core.security.SystemRoles;
import com.familyfund.event.api.EventQueryService;
import com.familyfund.event.api.dto.EventRole;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Security evaluation component for contribution actions requiring contextual event roles (US-50, US-51).
 */
@Slf4j
@Component("contributionSecurity")
@RequiredArgsConstructor
public class ContributionSecurity {

    private final ContributionRepository contributionRepository;
    private final EventQueryService eventQueryService;
    private final CurrentUserProvider currentUserProvider;

    public boolean hasContributionRole(String contributionId, String... roles) {
        if (currentUserProvider.hasAuthority(SystemRoles.ADMIN)
                || currentUserProvider.hasAuthority(SystemRoles.CLAN_HEAD)) {
            return true;
        }

        String currentMemberId = currentUserProvider.currentMemberId().orElse(null);
        if (currentMemberId == null || contributionId == null || contributionId.isBlank()) {
            return false;
        }

        Contribution contribution = contributionRepository.findById(contributionId).orElse(null);
        if (contribution == null || contribution.getEventId() == null) {
            return false;
        }

        Set<String> requiredRoleNames = Arrays.stream(roles).collect(Collectors.toSet());
        Set<EventRole> memberRoles = eventQueryService.getMemberRolesInEvent(contribution.getEventId(), currentMemberId);

        return memberRoles.stream().anyMatch(r -> requiredRoleNames.contains(r.name()));
    }
}
