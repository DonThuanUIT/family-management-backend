package com.familyfund.contribution.internal.repository;

import com.familyfund.contribution.internal.entity.ClanAssetAudit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ClanAssetAuditRepository extends JpaRepository<ClanAssetAudit, String> {

    List<ClanAssetAudit> findByAssetIdOrderByAuditDateDesc(String assetId);
}
