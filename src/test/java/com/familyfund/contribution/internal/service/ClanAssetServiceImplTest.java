package com.familyfund.contribution.internal.service;

import com.familyfund.contribution.internal.dto.response.ClanAssetResponse;
import com.familyfund.contribution.internal.dto.response.ClanAssetSummaryResponse;
import com.familyfund.contribution.internal.entity.ClanAsset;
import com.familyfund.contribution.internal.repository.ClanAssetRepository;
import com.familyfund.core.exception.ResourceNotFoundException;
import com.familyfund.core.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ClanAssetServiceImplTest {

    @Mock
    private ClanAssetRepository clanAssetRepository;

    @InjectMocks
    private ClanAssetServiceImpl clanAssetService;

    private static final String CLAN_ID = "CLAN_01";

    @BeforeEach
    void setUp() {
        TenantContext.setClanId(CLAN_ID);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("Should search clan assets with summary totals successfully")
    void searchAssets_success() {
        Pageable pageable = PageRequest.of(0, 10);
        ClanAsset asset = ClanAsset.builder()
                .assetId("AST_001")
                .assetName("Lư đồng")
                .quantity(1)
                .unit("cái")
                .assessedValue(new BigDecimal("10000000"))
                .storageLocation("Gian chính điện")
                .originContributionId("CTB_001")
                .clanId(CLAN_ID)
                .createdAt(Instant.now())
                .build();

        when(clanAssetRepository.searchAssets(CLAN_ID, "Lư", pageable))
                .thenReturn(new PageImpl<>(List.of(asset), pageable, 1));
        when(clanAssetRepository.sumTotalQuantityByClanId(CLAN_ID)).thenReturn(1L);
        when(clanAssetRepository.sumAssessedValueByClanId(CLAN_ID)).thenReturn(new BigDecimal("10000000"));

        ClanAssetSummaryResponse response = clanAssetService.searchAssets("Lư", pageable);

        assertThat(response).isNotNull();
        assertThat(response.totalElements()).isEqualTo(1);
        assertThat(response.totalItemCount()).isEqualTo(1L);
        assertThat(response.totalAssessedValue()).isEqualByComparingTo(new BigDecimal("10000000"));
        assertThat(response.content().get(0).assetName()).isEqualTo("Lư đồng");
    }

    @Test
    @DisplayName("Should retrieve asset by ID successfully")
    void getAssetById_success() {
        ClanAsset asset = ClanAsset.builder()
                .assetId("AST_001")
                .assetName("Lư đồng")
                .quantity(1)
                .unit("cái")
                .assessedValue(new BigDecimal("10000000"))
                .storageLocation("Gian chính điện")
                .originContributionId("CTB_001")
                .clanId(CLAN_ID)
                .createdAt(Instant.now())
                .build();

        when(clanAssetRepository.findByAssetIdAndClanId("AST_001", CLAN_ID)).thenReturn(Optional.of(asset));

        ClanAssetResponse response = clanAssetService.getAssetById("AST_001");

        assertThat(response).isNotNull();
        assertThat(response.assetId()).isEqualTo("AST_001");
        assertThat(response.assetName()).isEqualTo("Lư đồng");
    }

    @Test
    @DisplayName("Should throw ResourceNotFoundException when asset ID does not exist")
    void getAssetById_notFound_throwsException() {
        when(clanAssetRepository.findByAssetIdAndClanId("AST_999", CLAN_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> clanAssetService.getAssetById("AST_999"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Clan asset not found");
    }
}
