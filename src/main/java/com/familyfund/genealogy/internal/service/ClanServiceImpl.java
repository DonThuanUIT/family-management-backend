package com.familyfund.genealogy.internal.service;

import com.familyfund.core.exception.ResourceNotFoundException;
import com.familyfund.core.tenant.TenantContext;
import com.familyfund.core.utils.BankBinDirectory;
import com.familyfund.genealogy.api.dto.ClanBankingInfoDto;
import com.familyfund.genealogy.internal.dto.request.UpdateClanBankingRequest;
import com.familyfund.genealogy.internal.entity.Clan;
import com.familyfund.genealogy.internal.repository.ClanRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ClanServiceImpl implements ClanService {

    private final ClanRepository clanRepository;

    @Override
    @Transactional(readOnly = true)
    public ClanBankingInfoDto getCurrentClanBankingInfo() {
        String clanId = TenantContext.getClanId();
        Clan clan = clanRepository.findById(clanId)
                .orElseThrow(() -> new ResourceNotFoundException("Clan not found with ID: " + clanId));

        return toBankingInfoDto(clan);
    }

    @Override
    @Transactional
    public ClanBankingInfoDto updateCurrentClanBankingInfo(UpdateClanBankingRequest request) {
        String clanId = TenantContext.getClanId();
        Clan clan = clanRepository.findById(clanId)
                .orElseThrow(() -> new ResourceNotFoundException("Clan not found with ID: " + clanId));

        log.info("Updating clan beneficiary banking info for clanId: {}, bankBin: {}, accountNumber: {}",
                clanId, request.bankBin(), request.bankAccountNumber());

        clan.setBankBin(request.bankBin().trim());
        clan.setBankAccountNumber(request.bankAccountNumber().trim());
        clan.setBankAccountName(request.bankAccountName().trim().toUpperCase());
        if (request.qrTemplate() != null && !request.qrTemplate().isBlank()) {
            clan.setQrTemplate(request.qrTemplate().trim());
        }

        Clan updatedClan = clanRepository.save(clan);
        return toBankingInfoDto(updatedClan);
    }

    private ClanBankingInfoDto toBankingInfoDto(Clan clan) {
        return new ClanBankingInfoDto(
                clan.getClanId(),
                clan.getClanName(),
                clan.getBankBin(),
                BankBinDirectory.lookupBankName(clan.getBankBin()),
                clan.getBankAccountNumber(),
                clan.getBankAccountName(),
                clan.getQrTemplate() != null ? clan.getQrTemplate() : "compact2"
        );
    }
}
