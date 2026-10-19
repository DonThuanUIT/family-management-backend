package com.familyfund.genealogy.internal.service;

import com.familyfund.core.tenant.TenantContext;
import com.familyfund.genealogy.api.GenealogyQueryService;
import com.familyfund.genealogy.internal.repository.FamilyBranchRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Internal service implementation of the GenealogyQueryService public contract.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GenealogyQueryServiceImpl implements GenealogyQueryService {

    private final FamilyBranchRepository familyBranchRepository;

    @Override
    public boolean existsBranch(String branchId) {
        if (branchId == null || branchId.isBlank()) {
            return false;
        }
        String clanId = TenantContext.getClanId();
        return existsBranch(clanId, branchId.trim());
    }

    @Override
    public boolean existsBranch(String clanId, String branchId) {
        if (clanId == null || clanId.isBlank() || branchId == null || branchId.isBlank()) {
            return false;
        }
        return familyBranchRepository.existsByClanIdAndBranchId(clanId.trim(), branchId.trim());
    }
}

