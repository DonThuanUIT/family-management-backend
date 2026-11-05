package com.familyfund.core.audit.aspect;

import com.familyfund.core.audit.annotation.Audited;
import com.familyfund.core.audit.model.AuditAction;
import com.familyfund.core.audit.model.AuditEntityType;
import com.familyfund.core.audit.snapshot.AuditSnapshotProvider;
import com.familyfund.core.audit.writer.AuditLogWriter;
import com.familyfund.core.security.CurrentUserProvider;
import com.familyfund.core.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuditAspectTest {

    @Mock
    private AuditSnapshotProvider snapshotProvider;

    @Mock
    private AuditLogWriter auditLogWriter;

    @Mock
    private CurrentUserProvider currentUserProvider;

    private TestAuditedService proxy;

    interface TestAuditedService {
        String updateBranch(String branchId, String newName);
        String createBranch(String branchName);
        void deleteBranch(String branchId);
    }

    static class TestAuditedServiceImpl implements TestAuditedService {
        @Override
        @Audited(action = AuditAction.UPDATE, entityType = AuditEntityType.BRANCH, entityId = "#branchId", description = "Update branch")
        public String updateBranch(String branchId, String newName) {
            return "updated:" + branchId;
        }

        @Override
        @Audited(action = AuditAction.CREATE, entityType = AuditEntityType.BRANCH, entityId = "#result", description = "Create branch")
        public String createBranch(String branchName) {
            return "BR_NEW_01";
        }

        @Override
        @Audited(action = AuditAction.DELETE, entityType = AuditEntityType.BRANCH, entityId = "#branchId", description = "Delete branch")
        public void deleteBranch(String branchId) {
            // delete
        }
    }

    @BeforeEach
    void setUp() {
        TenantContext.setClanId("CLAN_TEST");
        lenient().when(currentUserProvider.currentAccountId()).thenReturn(Optional.of("ACC_ACTOR_01"));
        when(snapshotProvider.supports()).thenReturn(AuditEntityType.BRANCH);

        AuditAspect aspect = new AuditAspect(List.of(snapshotProvider), auditLogWriter, currentUserProvider);

        AspectJProxyFactory factory = new AspectJProxyFactory(new TestAuditedServiceImpl());
        factory.addAspect(aspect);
        this.proxy = factory.getProxy();
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("Should capture pre- and post-snapshots and write audit log on update when state changed")
    void shouldCaptureSnapshotsOnUpdate() {
        Map<String, Object> preState = Map.of("branchId", "BR_01", "branchName", "Old Name");
        Map<String, Object> postState = Map.of("branchId", "BR_01", "branchName", "New Name");

        when(snapshotProvider.snapshot("BR_01", "CLAN_TEST"))
                .thenReturn(Optional.of(preState))
                .thenReturn(Optional.of(postState));

        String result = proxy.updateBranch("BR_01", "New Name");

        assertEquals("updated:BR_01", result);
        verify(auditLogWriter).writeLog(
                eq("CLAN_TEST"),
                eq("ACC_ACTOR_01"),
                eq(AuditAction.UPDATE),
                eq(AuditEntityType.BRANCH),
                eq("BR_01"),
                isNull(),
                eq(preState),
                eq(postState),
                any(),
                eq("Update branch")
        );
    }

    @Test
    @DisplayName("Should skip writing audit log when pre and post states are identical")
    void shouldSkipLogWhenStateIsIdentical() {
        Map<String, Object> sameState = Map.of("branchId", "BR_01", "branchName", "Same Name");

        when(snapshotProvider.snapshot("BR_01", "CLAN_TEST"))
                .thenReturn(Optional.of(sameState))
                .thenReturn(Optional.of(sameState));

        proxy.updateBranch("BR_01", "Same Name");

        verify(auditLogWriter, never()).writeLog(any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("Should write audit log on CREATE with post-snapshot only")
    void shouldWriteLogOnCreate() {
        Map<String, Object> createdState = Map.of("branchId", "BR_NEW_01", "branchName", "Chi nhánh 1");

        when(snapshotProvider.snapshot("BR_NEW_01", "CLAN_TEST"))
                .thenReturn(Optional.of(createdState));

        String result = proxy.createBranch("Chi nhánh 1");

        assertEquals("BR_NEW_01", result);
        verify(auditLogWriter).writeLog(
                eq("CLAN_TEST"),
                eq("ACC_ACTOR_01"),
                eq(AuditAction.CREATE),
                eq(AuditEntityType.BRANCH),
                eq("BR_NEW_01"),
                isNull(),
                isNull(),
                eq(createdState),
                any(),
                eq("Create branch")
        );
    }

    @Test
    @DisplayName("Should write audit log on DELETE with pre-snapshot only")
    void shouldWriteLogOnDelete() {
        Map<String, Object> existingState = Map.of("branchId", "BR_DEL_01", "branchName", "To be deleted");

        when(snapshotProvider.snapshot("BR_DEL_01", "CLAN_TEST"))
                .thenReturn(Optional.of(existingState));

        proxy.deleteBranch("BR_DEL_01");

        verify(auditLogWriter).writeLog(
                eq("CLAN_TEST"),
                eq("ACC_ACTOR_01"),
                eq(AuditAction.DELETE),
                eq(AuditEntityType.BRANCH),
                eq("BR_DEL_01"),
                isNull(),
                eq(existingState),
                isNull(),
                any(),
                eq("Delete branch")
        );
    }
}
