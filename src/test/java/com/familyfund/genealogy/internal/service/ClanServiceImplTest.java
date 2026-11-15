package com.familyfund.genealogy.internal.service;

import com.familyfund.core.exception.ResourceNotFoundException;
import com.familyfund.core.tenant.TenantContext;
import com.familyfund.genealogy.api.dto.ClanBankingInfoDto;
import com.familyfund.genealogy.internal.dto.request.UpdateClanBankingRequest;
import com.familyfund.genealogy.internal.entity.Clan;
import com.familyfund.genealogy.internal.repository.ClanRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ClanServiceImplTest {

    private static final String CLAN_ID = "CLAN_01";

    @Mock
    private ClanRepository clanRepository;

    @InjectMocks
    private ClanServiceImpl clanService;

    @BeforeEach
    void setUp() {
        TenantContext.setClanId(CLAN_ID);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("getCurrentClanBankingInfo returns clan banking details")
    void getCurrentClanBankingInfo_success() {
        Clan clan = Clan.builder()
                .clanId(CLAN_ID)
                .clanName("Họ Nguyễn")
                .bankBin("970422")
                .bankAccountNumber("0987654321")
                .bankAccountName("NGUYEN VAN THU QUY")
                .qrTemplate("compact2")
                .build();
        when(clanRepository.findById(CLAN_ID)).thenReturn(Optional.of(clan));

        ClanBankingInfoDto result = clanService.getCurrentClanBankingInfo();

        assertThat(result).isNotNull();
        assertThat(result.clanId()).isEqualTo(CLAN_ID);
        assertThat(result.bankBin()).isEqualTo("970422");
        assertThat(result.bankName()).isEqualTo("MBBank");
        assertThat(result.bankAccountNumber()).isEqualTo("0987654321");
        assertThat(result.bankAccountName()).isEqualTo("NGUYEN VAN THU QUY");
    }

    @Test
    @DisplayName("getCurrentClanBankingInfo throws ResourceNotFoundException when clan does not exist")
    void getCurrentClanBankingInfo_notFound() {
        when(clanRepository.findById(CLAN_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> clanService.getCurrentClanBankingInfo())
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Clan not found");
    }

    @Test
    @DisplayName("updateCurrentClanBankingInfo updates and returns new banking details")
    void updateCurrentClanBankingInfo_success() {
        Clan clan = Clan.builder()
                .clanId(CLAN_ID)
                .clanName("Họ Nguyễn")
                .build();
        when(clanRepository.findById(CLAN_ID)).thenReturn(Optional.of(clan));
        when(clanRepository.save(any(Clan.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UpdateClanBankingRequest request = new UpdateClanBankingRequest(
                "970436",
                "1023456789",
                "nguyen van thu quy",
                "compact"
        );

        ClanBankingInfoDto result = clanService.updateCurrentClanBankingInfo(request);

        assertThat(result).isNotNull();
        assertThat(result.bankBin()).isEqualTo("970436");
        assertThat(result.bankName()).isEqualTo("Vietcombank");
        assertThat(result.bankAccountNumber()).isEqualTo("1023456789");
        assertThat(result.bankAccountName()).isEqualTo("NGUYEN VAN THU QUY");
        assertThat(result.qrTemplate()).isEqualTo("compact");

        verify(clanRepository).save(clan);
    }
}
