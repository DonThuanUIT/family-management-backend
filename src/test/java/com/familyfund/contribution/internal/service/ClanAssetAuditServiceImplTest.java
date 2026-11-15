package com.familyfund.contribution.internal.service;

import com.familyfund.contribution.internal.dto.request.AuditClanAssetRequest;
import com.familyfund.contribution.internal.dto.response.AuditClanAssetResultResponse;
import com.familyfund.contribution.internal.dto.response.ClanAssetAuditResponse;
import com.familyfund.contribution.internal.entity.ClanAsset;
import com.familyfund.contribution.internal.entity.ClanAssetAudit;
import com.familyfund.contribution.internal.repository.ClanAssetAuditRepository;
import com.familyfund.contribution.internal.repository.ClanAssetRepository;
import com.familyfund.contribution.internal.util.AssetAuditIdGenerator;
import com.familyfund.core.exception.ResourceNotFoundException;
import com.familyfund.core.security.CurrentUserProvider;
import com.familyfund.core.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ClanAssetAuditServiceImplTest {

    @Mock
    private ClanAssetRepository clanAssetRepository;

    @Mock
    private ClanAssetAuditRepository clanAssetAuditRepository;

    @Mock
    private AssetAuditIdGenerator assetAuditIdGenerator;

    @Mock
    private CurrentUserProvider currentUserProvider;

    @InjectMocks
    private ClanAssetServiceImpl clanAssetService;

    private final String clanId = "CLAN_01";
    private final String assetId = "AST_01";

    @BeforeEach
    void setUp() {
        TenantContext.setClanId(clanId);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("auditAsset - successfully updates condition, records audit log and returns result")
    void auditAsset_success() {
        ClanAsset asset = ClanAsset.builder()
                .assetId(assetId)
                .assetName("Lư hương đồng cổ")
                .quantity(1)
                .unit("bộ")
                .assessedValue(new BigDecimal("15000000.00"))
                .storageLocation("Bàn thờ chính gian giữa")
                .clanId(clanId)
                .currentCondition("Good")
                .createdAt(Instant.now())
                .build();

        when(clanAssetRepository.findByAssetIdAndClanId(assetId, clanId)).thenReturn(Optional.of(asset));
        when(assetAuditIdGenerator.generate()).thenReturn("AUD_20261109001");
        when(currentUserProvider.currentAccountId()).thenReturn(Optional.of("ACC_HEAD"));

        AuditClanAssetRequest request = new AuditClanAssetRequest(
                1, "Good", "Bàn thờ chính gian giữa (đã lau bóng)", "Kiểm kê định kỳ tháng 11/2026"
        );

        AuditClanAssetResultResponse response = clanAssetService.auditAsset(assetId, request);

        assertThat(response).isNotNull();
        assertThat(response.asset().assetId()).isEqualTo(assetId);
        assertThat(response.asset().currentCondition()).isEqualTo("Good");
        assertThat(response.auditLog().auditId()).isEqualTo("AUD_20261109001");
        assertThat(response.auditLog().notes()).isEqualTo("Kiểm kê định kỳ tháng 11/2026");

        verify(clanAssetAuditRepository).save(any(ClanAssetAudit.class));
        verify(clanAssetRepository).save(asset);
    }

    @Test
    @DisplayName("getAssetAuditHistory - returns history list")
    void getAssetAuditHistory_success() {
        ClanAssetAudit audit = ClanAssetAudit.builder()
                .auditId("AUD_01")
                .assetId(assetId)
                .auditDate(Instant.now())
                .previousQuantity(1)
                .actualQuantity(1)
                .previousCondition("Good")
                .conditionStatus("Good")
                .storageLocation("Gian giữa")
                .auditorAccountId("ACC_01")
                .notes("Kỳ 1")
                .createdAt(Instant.now())
                .build();

        when(clanAssetRepository.existsById(assetId)).thenReturn(true);
        when(clanAssetAuditRepository.findByAssetIdOrderByAuditDateDesc(assetId)).thenReturn(List.of(audit));

        List<ClanAssetAuditResponse> result = clanAssetService.getAssetAuditHistory(assetId);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).auditId()).isEqualTo("AUD_01");
    }

    @Test
    @DisplayName("getAssetAuditHistory - throws 404 when asset does not exist")
    void getAssetAuditHistory_notFound_throwsException() {
        when(clanAssetRepository.existsById("AST_NOT_FOUND")).thenReturn(false);

        assertThatThrownBy(() -> clanAssetService.getAssetAuditHistory("AST_NOT_FOUND"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("not found");
    }
}
