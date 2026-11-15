package com.familyfund.event.internal.anniversary;

import com.familyfund.core.exception.ResourceNotFoundException;
import com.familyfund.core.tenant.TenantContext;
import com.familyfund.event.api.DeathAnniversaryService;
import com.familyfund.event.api.dto.DeathAnniversaryDto;
import com.familyfund.event.api.dto.DeathAnniversaryReminderDto;
import com.familyfund.event.internal.entity.DeathAnniversaryReminder;
import com.familyfund.event.internal.repository.DeathAnniversaryReminderRepository;
import com.familyfund.genealogy.api.GenealogyQueryService;
import com.familyfund.genealogy.api.LunarCalendarService;
import com.familyfund.genealogy.api.dto.DeceasedMemberDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Implementation of {@link DeathAnniversaryService} (US-40): the daily reminder pass and the read-side API.
 *
 * <p>Scheduling concerns live in {@code DeathAnniversaryScheduler}; this class only knows "given a reference day, do
 * the work", which is what makes the whole feature testable without waiting for a cron trigger.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeathAnniversaryServiceImpl implements DeathAnniversaryService {

    private final GenealogyQueryService genealogyQueryService;
    private final LunarCalendarService lunarCalendarService;
    private final DeathAnniversaryReminderRepository reminderRepository;
    private final DeathAnniversaryIssuer issuer;
    private final DeathAnniversaryCalculator calculator;
    private final AnniversaryEventMatcher eventMatcher;
    private final DeathAnniversaryProperties properties;

    @Override
    public int scanAndSendReminders(LocalDate today) {
        LocalDate referenceDay = today != null ? today : LocalDate.now(zone());
        // A cron pass runs outside any request: make sure no leftover tenant of another thread leaks in.
        TenantContext.clear();

        List<DeceasedMemberDto> candidates = genealogyQueryService.findAllDeceasedMembersWithAnniversary();
        if (candidates.isEmpty()) {
            log.debug("US-40 scan {}: no deceased member with a lunar anniversary", referenceDay);
            return 0;
        }

        int sent = 0;
        for (DeceasedMemberDto member : candidates) {
            try {
                if (issuer.issueReminder(member, referenceDay)) {
                    sent++;
                }
            } catch (RuntimeException ex) {
                // One broken member must not abort the pass (its transaction rolled back on its own).
                log.error("US-40: cannot issue the anniversary reminder of member [{}]: {}",
                        member.memberId(), ex.getMessage());
            }
        }
        log.info("US-40 scan {}: {} candidate(s), {} reminder(s) issued", referenceDay, candidates.size(), sent);
        return sent;
    }

    @Override
    @Transactional(readOnly = true)
    public List<DeathAnniversaryDto> upcomingAnniversaries(LocalDate from, int windowDays) {
        String clanId = TenantContext.getClanId();
        LocalDate start = from != null ? from : LocalDate.now(zone());
        int days = windowDays > 0 ? windowDays : properties.windowDays();

        List<DeathAnniversaryDto> result = new ArrayList<>();
        for (DeceasedMemberDto member : genealogyQueryService.findDeceasedMembersWithAnniversary(clanId)) {
            Optional<DeathAnniversaryDto> computed = calculator.compute(
                    member, start, zone(), lunarCalendarService::convertSolarToLunar, false);
            if (computed.isEmpty() || computed.get().daysRemaining() > days) {
                continue;
            }
            DeathAnniversaryDto anniversary = computed.get();

            // A "Đám giỗ" event that already exists (same day, or same lunar label this year) suppresses the
            // suggestion: nothing left to create.
            boolean covered = eventMatcher.isCoveredByExistingEvent(
                    clanId, anniversary.anniversaryDate(), member.deathDateLunar(), zone());
            boolean alreadyReminded = anniversary.milestoneDays() != null
                    && reminderRepository.existsByMemberIdAndLunarYearAndMilestoneDays(
                            member.memberId(), anniversary.anniversaryYear(), anniversary.milestoneDays());
            result.add(withReminderState(covered ? withoutSuggestion(anniversary) : anniversary, alreadyReminded));
        }
        result.sort(Comparator.comparing(DeathAnniversaryDto::anniversaryDate)
                .thenComparing(DeathAnniversaryDto::memberFullName,
                        Comparator.nullsLast(String::compareToIgnoreCase)));
        return result;
    }

    @Override
    @Transactional(readOnly = true)
    public List<DeathAnniversaryReminderDto> remindersOfMember(String memberId) {
        String clanId = TenantContext.getClanId();
        // Tenant guard: a member of another clan answers 404, exactly like a missing one.
        genealogyQueryService.findMemberSummary(memberId)
                .filter(member -> clanId.equals(member.clanId()))
                .orElseThrow(() -> new ResourceNotFoundException("Thành viên", "memberId", memberId));

        return reminderRepository.findAllByMemberId(memberId).stream()
                .sorted(Comparator.comparing(DeathAnniversaryReminder::getAnniversaryYear).reversed()
                        .thenComparing(DeathAnniversaryReminder::getMilestoneDays))
                .map(this::toDto)
                .toList();
    }

    private DeathAnniversaryDto withReminderState(DeathAnniversaryDto anniversary, boolean alreadyReminded) {
        return DeathAnniversaryDto.builder()
                .memberId(anniversary.memberId())
                .memberFullName(anniversary.memberFullName())
                .generation(anniversary.generation())
                .branchId(anniversary.branchId())
                .branchName(anniversary.branchName())
                .deathDate(anniversary.deathDate())
                .deathDateLunar(anniversary.deathDateLunar())
                .lunarDay(anniversary.lunarDay())
                .lunarMonth(anniversary.lunarMonth())
                .leapMonth(anniversary.leapMonth())
                .anniversaryYear(anniversary.anniversaryYear())
                .anniversaryDate(anniversary.anniversaryDate())
                .daysRemaining(anniversary.daysRemaining())
                .milestoneDays(anniversary.milestoneDays())
                .reminderTriggered(alreadyReminded)
                .alreadyReminded(alreadyReminded)
                .suggestion(anniversary.suggestion())
                .computedAt(anniversary.computedAt())
                .build();
    }

    private DeathAnniversaryDto withoutSuggestion(DeathAnniversaryDto anniversary) {
        return DeathAnniversaryDto.builder()
                .memberId(anniversary.memberId())
                .memberFullName(anniversary.memberFullName())
                .generation(anniversary.generation())
                .branchId(anniversary.branchId())
                .branchName(anniversary.branchName())
                .deathDate(anniversary.deathDate())
                .deathDateLunar(anniversary.deathDateLunar())
                .lunarDay(anniversary.lunarDay())
                .lunarMonth(anniversary.lunarMonth())
                .leapMonth(anniversary.leapMonth())
                .anniversaryYear(anniversary.anniversaryYear())
                .anniversaryDate(anniversary.anniversaryDate())
                .daysRemaining(anniversary.daysRemaining())
                .milestoneDays(anniversary.milestoneDays())
                .reminderTriggered(anniversary.reminderTriggered())
                .alreadyReminded(anniversary.alreadyReminded())
                .suggestion(null)
                .computedAt(anniversary.computedAt())
                .build();
    }

    private DeathAnniversaryReminderDto toDto(DeathAnniversaryReminder reminder) {
        return new DeathAnniversaryReminderDto(
                reminder.getReminderId(),
                reminder.getMemberId(),
                reminder.getAnniversaryYear(),
                reminder.getLunarYear(),
                reminder.getMilestoneDays(),
                reminder.getAnniversaryDate(),
                reminder.getTriggerDate(),
                reminder.getRecipientCount());
    }

    private ZoneId zone() {
        return ZoneId.of(properties.zone() == null ? DeathAnniversaryProperties.DEFAULT_ZONE : properties.zone());
    }
}
