package com.familyfund.contribution.internal.repository;

import com.familyfund.contribution.internal.entity.ClanAsset;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Repository
public interface ClanAssetRepository extends JpaRepository<ClanAsset, String> {

    Optional<ClanAsset> findByAssetIdAndClanId(String assetId, String clanId);

    Page<ClanAsset> findAllByClanId(String clanId, Pageable pageable);

    @Query("SELECT a FROM ClanAsset a WHERE a.clanId = :clanId " +
           "AND (LOWER(a.assetName) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
           "     OR LOWER(a.storageLocation) LIKE LOWER(CONCAT('%', :keyword, '%')))")
    Page<ClanAsset> searchAssets(@Param("clanId") String clanId,
                                 @Param("keyword") String keyword,
                                 Pageable pageable);

    @Query("SELECT COALESCE(SUM(a.assessedValue), 0) FROM ClanAsset a WHERE a.clanId = :clanId")
    BigDecimal sumAssessedValueByClanId(@Param("clanId") String clanId);

    @Query("SELECT COALESCE(SUM(a.quantity), 0) FROM ClanAsset a WHERE a.clanId = :clanId")
    Long sumTotalQuantityByClanId(@Param("clanId") String clanId);

    List<ClanAsset> findByOriginContributionId(String originContributionId);
}
