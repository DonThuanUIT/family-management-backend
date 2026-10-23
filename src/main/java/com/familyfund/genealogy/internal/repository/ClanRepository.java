package com.familyfund.genealogy.internal.repository;

import com.familyfund.genealogy.internal.entity.Clan;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ClanRepository extends JpaRepository<Clan, String> {

    Optional<Clan> findByClanName(String clanName);

    /**
     * SELECT ... FOR UPDATE on the clan row. Serializes write operations that must run a
     * check-then-insert inside one clan (e.g. duplicate member profile detection), because
     * `members` has no clan-wide UNIQUE constraint the database could enforce on its own.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM Clan c WHERE c.clanId = :clanId")
    Optional<Clan> findByIdForUpdate(@Param("clanId") String clanId);
}
