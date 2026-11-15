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
    private static final String MEMBER_REP = "MEM_IT08_REP";
    private static final String ACCOUNT_REP = "ACC_IT08_REP";
    private static final String TASK_OVERDUE_20H = "TSK_IT08_LATE20";
    private static final String TASK_OVERDUE_11H = "TSK_IT08_LATE11";
    private static final String TASK_OVERDUE_COMPLETED = "TSK_IT08_LATEDONE";
    private static final String TASK_OVERDUE_NO_REP = "TSK_IT08_LATENOREP";

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
    private TaskOverdueEscalationIssuer overdueIssuer;
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

        // US-47 escalation: the representative (Trưởng chi) of BRANCH_A is the recipient of 12h overdue alerts.
        Member representative = memberRepository.saveAndFlush(Member.builder()
                .memberId(MEMBER_REP).fullName("Nguyen Van Truong Chi").gender("Male")
                .generation(1).branch(branchA).isPlaceholder(false).build());
        branchA.setRepresentative(representative);
        familyBranchRepository.saveAndFlush(branchA);

        userAccountRepository.saveAndFlush(UserAccount.builder()
                .accountId(ACCOUNT_ID).memberId(linked.getMemberId()).phoneNumber("0898000801")
                .passwordHash("{noop}integration").accountStatus(AccountStatus.Active).build());
        userAccountRepository.saveAndFlush(UserAccount.builder()
                .accountId(ACCOUNT_REP).memberId(MEMBER_REP).phoneNumber("0898000802")
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

        // US-47 escalation candidates: deadlines in the past.
        eventTaskRepository.saveAll(List.of(
                task(TASK_OVERDUE_20H, EVENT_A, TaskStatus.InProgress, MEMBER_WITH_ACCOUNT, Duration.ofHours(-20)),
                task(TASK_OVERDUE_11H, EVENT_A, TaskStatus.InProgress, MEMBER_WITH_ACCOUNT, Duration.ofHours(-11)),
                task(TASK_OVERDUE_COMPLETED, EVENT_A, TaskStatus.Completed, MEMBER_WITH_ACCOUNT, Duration.ofHours(-20)),
                branchOnlyTask(TASK_OVERDUE_NO_REP, EVENT_A, BRANCH_B, Duration.ofHours(-20))
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

    private EventTask branchOnlyTask(String taskId, String eventId, String branchId, Duration untilDeadline) {
        return EventTask.builder()
                .taskId(taskId)
                .eventId(eventId)
                .taskName("Chuan bi le vat " + taskId)
                .advanceBudget(new BigDecimal("1500000.00"))
                .deadline(Instant.now().plus(untilDeadline))
                .status(TaskStatus.InProgress)
                .assignedBranchId(branchId)
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

    // ------------------------------------------------ US-47 escalation pass (12h overdue)

    @Test
    @DisplayName("Escalates a 20h-overdue task to the branch representative (Trưởng chi) with task, assignee and lateness")
    void writesOverdueEscalationForRepresentative() {
        assertTrue(overdueIssuer.issueOverdueEscalation(TASK_OVERDUE_20H, Instant.now()));

        List<TaskProgressAlert> alerts =
                taskProgressAlertRepository.findAllByTaskIdOrderBySentTimeDesc(TASK_OVERDUE_20H);
        assertEquals(1, alerts.size());

        TaskProgressAlert alert = alerts.getFirst();
        assertEquals(TaskAlertLevel.OverdueAlert12H, alert.getAlertLevel());
        // Recipient is the branch representative's account, not the assignee's.
        assertEquals(ACCOUNT_REP, alert.getRecipientAccountId());
        assertFalse(alert.getIsRead());
        assertNotNull(alert.getSentTime());
        assertTrue(alert.getAlertContent().contains("Gio To Integration A"), alert.getAlertContent());
        assertTrue(alert.getAlertContent().contains(MEMBER_WITH_ACCOUNT), alert.getAlertContent());
        assertTrue(alert.getAlertContent().contains("Nguyen Van Lien Ket"), alert.getAlertContent());
        assertTrue(alert.getAlertContent().contains("20 giờ"), alert.getAlertContent());
        assertTrue(alert.getAlertContent().contains(MEMBER_REP), alert.getAlertContent());
    }

    @Test
    @DisplayName("Escalation is idempotent: a task that stays overdue is escalated exactly once")
    void escalatesOnlyOncePerTask() {
        Instant now = Instant.now();

        assertTrue(overdueIssuer.issueOverdueEscalation(TASK_OVERDUE_20H, now));
        assertFalse(overdueIssuer.issueOverdueEscalation(TASK_OVERDUE_20H, now.plus(Duration.ofDays(2))));

        assertEquals(1, taskProgressAlertRepository.findAllByTaskIdOrderBySentTimeDesc(TASK_OVERDUE_20H).size());
    }

    @Test
    @DisplayName("Escalation scan skips tasks under 12h late, completed tasks and branches without a representative")
    void escalationScanSkipsIneligibleTasks() {
        int issued = service.scanAndEscalateOverdueTasks();

        assertTrue(issued >= 1, "The 20h overdue task must be escalated, issued=" + issued);
        assertTrue(taskProgressAlertRepository.existsByTaskIdAndAlertLevel(TASK_OVERDUE_20H, TaskAlertLevel.OverdueAlert12H));
        assertFalse(taskProgressAlertRepository.existsByTaskIdAndAlertLevel(TASK_OVERDUE_11H, TaskAlertLevel.OverdueAlert12H));
        assertFalse(taskProgressAlertRepository.existsByTaskIdAndAlertLevel(TASK_OVERDUE_COMPLETED, TaskAlertLevel.OverdueAlert12H));
        assertFalse(taskProgressAlertRepository.existsByTaskIdAndAlertLevel(TASK_OVERDUE_NO_REP, TaskAlertLevel.OverdueAlert12H));

        // A second pass finds nothing new for the escalated task.
        assertFalse(overdueIssuer.issueOverdueEscalation(TASK_OVERDUE_20H, Instant.now()));
    }
}