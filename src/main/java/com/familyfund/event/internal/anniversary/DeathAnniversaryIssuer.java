package com.familyfund.event.internal.anniversary;

import com.familyfund.event.api.dto.DeathAnniversaryDto;
import com.familyfund.event.api.event.DeathAnniversaryReminderIssuedDomainEvent;
import com.familyfund.event.internal.entity.DeathAnniversaryReminder;
import com.familyfund.event.internal.repository.DeathAnniversaryReminderRepository;
import com.familyfund.event.internal.util.AnniversaryKeyGenerator;
import com.familyfund.genealogy.api.GenealogyQueryService;
import com.familyfund.genealogy.api.LunarCalendarService;
import com.familyfund.genealogy.api.dto.DeceasedMemberDto;
import com.familyfund.identity.api.IdentityRoleCommandService;
import com.familyfund.identity.api.dto.AccountSummaryDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Issues ONE reminder for ONE member at ONE milestone (US-40).
 *
 * <p>Own bean on purpose: {@code @Transactional} only takes effect through the proxy, so the per-member unit of work
 * has to live outside the scanning service. That way one broken member (unparsable lunar string, missing account,
 * a failing publish) rolls back alone and the pass continues with the next one.</p>
 *
 * <p>Order inside the transaction: compute → resolve recipients → save the ledger row (idempotency key
 * {@code (member_id, lunar_year, milestone_days)}) → publish the event the notification module consumes. The ledger
 * row is written first so a duplicate pass can never double-send, and the whole unit rolls back if publishing fails,
 * leaving the reminder to be retried on the next pass.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DeathAnniversaryIssuer {

    private final DeathAnniversaryCalculator calculator;
    private final DeathAnniversaryReminderRepository reminderRepository;
    private final AnniversaryKeyGenerator keyGenerator;
    private final GenealogyQueryService genealogyQueryService;
    private final LunarCalendarService lunarCalendarService;
    private final IdentityRoleCommandService identityRoleCommandService;
    private final ApplicationEventPublisher eventPublisher;
    private final DeathAnniversaryProperties properties;

    /**
     * Evaluates one member and sends the reminder when the configured milestone is hit.
     *
     * @param member deceased member with a lunar anniversary
     * @param today  reference day of the pass
     * @return {@code true} when a reminder was issued, {@code false} when skipped
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean issueReminder(DeceasedMemberDto member, LocalDate today) {
        if (member == null || member.memberId() == null || member.clanId() == null) {
            return false;
        }
        ZoneId zone = zone();

        boolean covered = false;
        Optional<DeathAnniversaryDto> computed = calculator.compute(member, today, zone, lunarPort(), covered);
        if (computed.isEmpty()) {
            return false;
        }
        DeathAnniversaryDto anniversary = computed.get();
        Integer milestone = anniversary.milestoneDays();
        if (milestone == null) {
            log.debug("US-40: member [{}] anniversary in {} day(s) does not hit a milestone; skipped",
                    member.memberId(), anniversary.daysRemaining());
            return false;
        }
        if (reminderRepository.existsByMemberIdAndLunarYearAndMilestoneDays(
                member.memberId(), anniversary.anniversaryYear(), milestone)) {
            log.debug("US-40: reminder {}d for member [{}] in {} already sent; skipped",
                    milestone, member.memberId(), anniversary.anniversaryYear());
            return false;
        }

        List<String> recipientAccountIds = resolveRecipientAccounts(member);
        String lunarLabel = anniversary.deathDateLunar();
        String title = buildTitle(anniversary, milestone);
        String content = buildContent(anniversary, milestone, recipientAccountIds.isEmpty());

        DeathAnniversaryReminder ledger = DeathAnniversaryReminder.builder()
                .reminderId(keyGenerator.generateReminderId())
                .clanId(member.clanId())
                .memberId(member.memberId())
                .anniversaryYear(anniversary.anniversaryYear())
                .lunarYear(anniversary.anniversaryYear())
                .milestoneDays(milestone)
                .anniversaryDate(anniversary.anniversaryDate())
                .triggerDate(today)
                .recipientCount(recipientAccountIds.size())
                .build();
        reminderRepository.save(ledger);

        eventPublisher.publishEvent(new DeathAnniversaryReminderIssuedDomainEvent(
                member.clanId(),
                member.memberId(),
                member.fullName(),
                member.branchId(),
                member.branchName(),
                anniversary.anniversaryDate(),
                anniversary.anniversaryYear(),
                lunarLabel,
                milestone,
                anniversary.daysRemaining(),
                title,
                content,
                recipientAccountIds
        ));

        log.info("US-40: reminded {}d before the anniversary of member [{}] on {} ({} recipients)",
                milestone, member.memberId(), anniversary.anniversaryDate(), recipientAccountIds.size());
        return true;
    }

    /**
     * Recipients of a death-anniversary reminder: the branch representative plus every account of the branch.
     *
     * <p>The anniversary concerns the whole branch (the offering meal is prepared by its members), and the
     * representative is the person who creates the "Đám giỗ" event, so they are included even when their member
     * record lives in another branch.</p>
     */
    private List<String> resolveRecipientAccounts(DeceasedMemberDto member) {
        Set<String> accountIds = new LinkedHashSet<>();

        List<String> branchMemberIds = member.branchId() == null
                ? List.of()
                : genealogyQueryService.getMemberIdsByBranch(member.clanId(), member.branchId());
        if (branchMemberIds != null && !branchMemberIds.isEmpty()) {
            Map<String, AccountSummaryDto> accounts = identityRoleCommandService.findAccountsByMemberIds(branchMemberIds);
            accounts.values().stream()
                    .map(AccountSummaryDto::accountId)
                    .filter(id -> id != null && !id.isBlank())
                    .forEach(accountIds::add);
        }

        if (member.branchId() != null) {
            genealogyQueryService.findBranchByIdAndClanId(member.branchId(), member.clanId())
                    .map(branch -> branch.representativeMemberId())
                    .filter(representativeId -> representativeId != null && !representativeId.isBlank())
                    .flatMap(identityRoleCommandService::findAccountByMemberId)
                    .map(AccountSummaryDto::accountId)
                    .ifPresent(accountIds::add);
        }

        return List.copyOf(accountIds);
    }

    private String buildTitle(DeathAnniversaryDto anniversary, int milestone) {
        String name = anniversary.memberFullName() == null ? anniversary.memberId() : anniversary.memberFullName();
        return "Nhắc giỗ " + name + " — còn " + milestone + " ngày";
    }

    private String buildContent(DeathAnniversaryDto anniversary, int milestone, boolean noRecipient) {
        String name = anniversary.memberFullName() == null ? anniversary.memberId() : anniversary.memberFullName();
        StringBuilder content = new StringBuilder();
        content.append("Ngày giỗ ").append(name);
        if (anniversary.generation() != null) {
            content.append(" (đời thứ ").append(anniversary.generation()).append(')');
        }
        content.append(": ").append(anniversary.deathDateLunar())
                .append(", rơi vào ").append(anniversary.anniversaryDate())
                .append(" Dương lịch — còn ").append(milestone).append(" ngày.");
        if (anniversary.branchName() != null) {
            content.append(" Chi/nhánh: ").append(anniversary.branchName()).append('.');
        }
        content.append(" Hệ thống đã chuẩn bị gợi ý sự kiện 'Đám giỗ' để trưởng chi xem lại và tạo sự kiện.");
        if (noRecipient) {
            content.append(" (Chưa có tài khoản nào trong chi nhận được thông báo này.)");
        }
        return content.toString();
    }

    private LunarCalendarPort lunarPort() {
        return lunarCalendarService::convertSolarToLunar;
    }

    private ZoneId zone() {
        return ZoneId.of(properties.zone() == null ? DeathAnniversaryProperties.DEFAULT_ZONE : properties.zone());
    }
}
