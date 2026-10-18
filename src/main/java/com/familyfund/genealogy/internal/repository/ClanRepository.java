package com.familyfund.genealogy.internal.repository;

import com.familyfund.genealogy.internal.entity.Clan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ClanRepository extends JpaRepository<Clan, String> {

    Optional<Clan> findByClanName(String clanName);
}

