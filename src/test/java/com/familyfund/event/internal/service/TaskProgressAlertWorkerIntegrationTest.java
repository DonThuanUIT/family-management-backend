package com.familyfund.event.internal.service;

import com.familyfund.core.tenant.TenantContext;
import com.familyfund.event.api.dto.EventStatus;
import com.familyfund.event.api.dto.TaskAlertLevel;
import com.familyfund.event.api.dto.TaskStatus;
import com.familyfund.event.internal.entity.Event;
import com.familyfund.event.internal.entity.EventTask;
import com.familyfund.event.internal.entity.TaskProgressAlert;
import com.familyfund.event.internal.repository.EventRepository;
import com.familyfund.event.internal.repository.EventTaskRepository;
import com.familyfund.event.internal.repository.TaskProgressAlertRepository;
import com.familyfund.genealogy.internal.entity.Clan;
import com.familyfund.genealogy.internal.entity.FamilyBranch;
import com.familyfund.genealogy.internal.entity.Member;
import com.familyfund.genealogy.internal.repository.ClanRepository;
import com.familyfund.genealogy.internal.repository.FamilyBranchRepository;
import com.familyfund.genealogy.internal.repository.MemberRepository;
import com.familyfund.identity.internal.entity.AccountStatus;
import com.familyfund.identity.internal.entity.UserAccount;
import com.familyfund.identity.internal.repository.UserAccountRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end verification of the US-08 background worker over the real stack:
 * Flyway schema (V1), JPQL scan query across clans, {@code IdentityRoleCommandService}
 * account resolution, `task_progress_alerts` persistence and the notification adapter.
 *
 * <p>Every test runs inside a transaction that is rolled back, so no rows are left behind on the
 * shared PostgreSQL database. The scanner is invoked directly instead of waiting for the
 * {@code @Scheduled} trigger, but no tenant context is set — exactly like the scheduler thread.</p>
 */
@SpringBootTest
@Transactional
@DisplayName("US-08 Task Progress Alert Worker Integration Test")
class TaskProgressAlertWorkerIntegrationTest {

    private static final String CLAN_A = "CLAN_IT08_A";
    private static final String CLAN_B = "CLAN_IT08_B";
    private static final String BRANCH_A = "BR_IT08_A";
    private static final String BRANCH_B = "BR_IT08_B";
    private static final String MEMBER_WITH_ACCOUNT = "MEM_IT08_LINKED";
    private static final String MEMBER_WITHOUT_ACCOUNT = "MEM_IT08_VIRTUAL";
    private static final String ACCOUNT_ID = "ACC_IT08_OWNER";
    private static final String EVENT_A = "EVT_IT08_A";
    private static final String EVENT_B = "EVT_IT08_B";
    private static final String EVENT_CLOSED = "EVT_IT08_CLOSED";
    private static final String TASK_DUE_SOON = "TSK_IT08_SOON";
    private static final String TASK_COMPLETED = "TSK_IT08_DONE";
    private static final String TASK_FAR_AWAY = "TSK_IT08_FAR";
    private static final String TASK_OTHER_CLAN = "TSK_IT08_OTHER";
    private static final String TASK_CLOSED_EVENT = "TSK_IT08_CLOSED";
    private static final String TASK_NO_ACCOUNT = "TSK_IT08_NOACC";

    @Autowired
    private ClanRepository clanRepository;
    @Autowired
    private FamilyBranchRepository familyBranchRepository;
    @Autowired
    private MemberRepository memberRepository;
    @Autowired
    private UserAccountRepository userAccountRepository;
    @Autowired
    private EventRepository eventRepository;
    @Autowired
    private EventTaskRepository eventTaskRepository;
    @Autowired
    private TaskProgressAlertRepository taskProgressAlertRepository;
    @Autowired
    private TaskProgressWarningIssuer issuer;
    @Autowired
    private TaskProgressAlertService service;

    @BeforeEach
    void seed() {
        TenantContext.clear();

        clanRepository.saveAndFlush(Clan.builder().clanId(CLAN_A).clanName("Clan Integration 08 A").build());
        clanRepository.saveAndFlush(Clan.builder().clanId(CLAN_B).clanName("Clan Integration 08 B").build());

        FamilyBranch branchA = familyBranchRepository.saveAndFlush(FamilyBranch.builder()
                .branchId(BRANCH_A).clanId(CLAN_A).branchName("Chi A").generationLevel(1).build());
        familyBranchRepository.saveAndFlush(FamilyBranch.builder()
                .branchId(BRANCH_B).clanId(CLAN_B).branchName("Chi B").generationLevel(1).build());

        Member linked = memberRepository.saveAndFlush(Member.builder()
                .memberId(MEMBER_WITH_ACCOUNT).fullName("Nguyen Van Lien Ket").gender("Male")
                .generation(2).branch(branchA).isPlaceholder(false).build());
        memberRepository.saveAndFlush(Member.builder()
                .memberId(MEMBER_WITHOUT_ACCOUNT).fullName("Nguyen Van Ho So Ao").gender("Male")
                .generation(3).branch(branchA).isPlaceholder(true).build());

        userAccountRepository.saveAndFlush(UserAccount.builder()
                .accountId(ACCOUNT_ID).memberId(linked.getMemberId()).phoneNumber("0898000801")
                .passwordHash("{noop}integration").accountStatus(AccountStatus.Active).build());

        Instant eventTime = Instant.now().plus(Duration.ofDays(10));
        eventRepository.saveAll(List.of(
                Event.builder()
                        .eventId(EVENT_A).clanId(CLAN_A).eventName("Gio To Integration A").eventDatetime(eventTime)
                        .location("Tu duong A").status(EventStatus.Ongoing).createdBy(ACCOUNT_ID).build(),
                Event.builder()
                        .eventId(EVENT_B).clanId(CLAN_B).eventName("Gio To Integration B").eventDatetime(eventTime)
                        .location("Tu duong B").status(EventStatus.Ongoing).createdBy(ACCOUNT_ID).build(),
                Event.builder()
                        .eventId(EVENT_CLOSED).clanId(CLAN_A).eventName("Gio To Da Khoa So").eventDatetime(eventTime)
                        .location("Tu duong A").status(EventStatus.Closed).createdBy(ACCOUNT_ID).build()
        ));
        eventRepository.flush();

        eventTaskRepository.saveAll(List.of(
                task(TASK_DUE_SOON, EVENT_A, TaskStatus.InProgress, MEMBER_WITH_ACCOUNT, Duration.ofHours(5)),
                task(TASK_COMPLETED, EVENT_A, TaskStatus.Completed, MEMBER_WITH_ACCOUNT, Duration.ofHours(5)),
                task(TASK_FAR_AWAY, EVENT_A, TaskStatus.InProgress, MEMBER_WITH_ACCOUNT, Duration.ofHours(30)),
                task(TASK_OTHER_CLAN, EVENT_B, TaskStatus.InProgress, MEMBER_WITH_ACCOUNT, Duration.ofHours(6)),
                task(TASK_CLOSED_EVENT, EVENT_CLOSED, TaskStatus.InProgress, MEMBER_WITH_ACCOUNT, Duration.ofHours(6)),
                task(TASK_NO_ACCOUNT, EVENT_A, TaskStatus.InProgress, MEMBER_WITHOUT_ACCOUNT, Duration.ofHours(7))
        ));
        eventTaskRepository.flush();
        TenantContext.clear();
    }

    @AfterEach
    void cleanup() {
        TenantContext.clear();
    }

    private EventTask task(String taskId, String eventId, TaskStatus status, String memberId, Duration untilDeadline) {
        return EventTask.builder()
                .taskId(taskId)
                .eventId(eventId)
                .taskName("Chuan bi le vat " + taskId)
                .advanceBudget(new BigDecimal("1500000.00"))
                .deadline(Instant.now().plus(untilDeadline))
                .status(status)
                .assignedBranchId(BRANCH_A)
                .assignedMemberId(memberId)
                .createdBy(ACCOUNT_ID)
                .build();
    }

    @Test
    @DisplayName("Persists a Reminder24H progress alert and resolves the assignee account (assigned_member_id)")
    void writesProgressWarningForDueTask() {
        assertTrue(issuer.issueProgressWarning(TASK_DUE_SOON, Instant.now()));

        List<TaskProgressAlert> alerts = taskProgressAlertRepository.findAllByTaskIdOrderBySentTimeDesc(TASK_DUE_SOON);
        assertEquals(1, alerts.size());

        TaskProgressAlert alert = alerts.getFirst();
        assertEquals(TaskAlertLevel.Reminder24H, alert.getAlertLevel());
        assertEquals(ACCOUNT_ID, alert.getRecipientAccountId());
        assertFalse(alert.getIsRead());
        assertNotNull(alert.getSentTime());
        assertTrue(alert.getAlertContent().contains("Gio To Integration A"));
        assertTrue(alert.getAlertContent().contains(MEMBER_WITH_ACCOUNT));
    }

    @Test
    @DisplayName("Is idempotent: the same task is warned only once")
    void doesNotWarnTwice() {
        Instant now = Instant.now();

        assertTrue(issuer.issueProgressWarning(TASK_DUE_SOON, now));
        assertFalse(issuer.issueProgressWarning(TASK_DUE_SOON, now));

        assertEquals(1, taskProgressAlertRepository.findAllByTaskIdOrderBySentTimeDesc(TASK_DUE_SOON).size());
    }

    @Test
    @DisplayName("Skips completed tasks, distant deadlines, closed events and members without an account")
    void skipsIneligibleTasks() {
        Instant now = Instant.now();

        assertFalse(issuer.issueProgressWarning(TASK_COMPLETED, now));
        assertFalse(issuer.issueProgressWarning(TASK_FAR_AWAY, now));
        assertFalse(issuer.issueProgressWarning(TASK_CLOSED_EVENT, now));
        assertFalse(issuer.issueProgressWarning(TASK_NO_ACCOUNT, now));

        assertTrue(taskProgressAlertRepository.findAllByTaskIdOrderBySentTimeDesc(TASK_COMPLETED).isEmpty());
        assertTrue(taskProgressAlertRepository.findAllByTaskIdOrderBySentTimeDesc(TASK_FAR_AWAY).isEmpty());
        assertTrue(taskProgressAlertRepository.findAllByTaskIdOrderBySentTimeDesc(TASK_CLOSED_EVENT).isEmpty());
        assertTrue(taskProgressAlertRepository.findAllByTaskIdOrderBySentTimeDesc(TASK_NO_ACCOUNT).isEmpty());
    }

    @Test
    @DisplayName("Scan pass warns tasks of every clan without a tenant context (scheduler thread behaviour)")
    void scanCoversAllClans() {
        // No TenantContext: the scan must still see tasks of CLAN_A and CLAN_B.
        int issued = service.scanAndWarnUpcomingDeadlines();

        assertTrue(issued >= 2, "Both clans' due tasks must be warned, issued=" + issued);
        assertTrue(taskProgressAlertRepository.existsByTaskIdAndAlertLevel(TASK_DUE_SOON, TaskAlertLevel.Reminder24H));
        assertTrue(taskProgressAlertRepository.existsByTaskIdAndAlertLevel(TASK_OTHER_CLAN, TaskAlertLevel.Reminder24H));
        assertFalse(taskProgressAlertRepository.existsByTaskIdAndAlertLevel(TASK_COMPLETED, TaskAlertLevel.Reminder24H));

        // A second pass issues nothing new for those tasks.
        int secondPass = service.scanAndWarnUpcomingDeadlines();
        assertTrue(taskProgressAlertRepository.findAllByTaskIdOrderBySentTimeDesc(TASK_DUE_SOON).size() == 1);
        assertTrue(secondPass >= 0);
    }
}