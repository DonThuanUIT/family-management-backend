package com.familyfund.genealogy.internal.service;

import com.familyfund.genealogy.api.dto.ClanBankingInfoDto;
import com.familyfund.genealogy.internal.dto.request.UpdateClanBankingRequest;

public interface ClanService {

    /**
     * Retrieves the beneficiary banking configuration for the current tenant clan.
     */
    ClanBankingInfoDto getCurrentClanBankingInfo();

    /**
     * Updates the beneficiary banking configuration for the current tenant clan.
     * Restricted to CLAN_HEAD or ADMIN roles.
     */
    ClanBankingInfoDto updateCurrentClanBankingInfo(UpdateClanBankingRequest request);
}
