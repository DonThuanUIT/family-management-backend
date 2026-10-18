package com.familyfund.genealogy.internal.service;

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
        return familyBranchRepository.existsByBranchId(branchId.trim());
    }
}

