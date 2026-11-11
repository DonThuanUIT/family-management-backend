package com.familyfund.genealogy.internal.repository;

import com.familyfund.genealogy.internal.entity.MemberPrivacySetting;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface MemberPrivacySettingRepository extends JpaRepository<MemberPrivacySetting, String> {

    List<MemberPrivacySetting> findByMemberIdIn(Collection<String> memberIds);
}
