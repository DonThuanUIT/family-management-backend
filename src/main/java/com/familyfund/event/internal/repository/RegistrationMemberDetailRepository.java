package com.familyfund.event.internal.repository;

import com.familyfund.event.internal.entity.RegistrationMemberDetail;
import com.familyfund.event.internal.entity.RegistrationMemberDetailId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Spring Data JPA Repository for `RegistrationMemberDetail` (table `registration_member_details`).
 */
@Repository
public interface RegistrationMemberDetailRepository extends JpaRepository<RegistrationMemberDetail, RegistrationMemberDetailId> {

    List<RegistrationMemberDetail> findAllByRegistrationId(String registrationId);

    List<RegistrationMemberDetail> findAllByMemberId(String memberId);

    void deleteAllByRegistrationId(String registrationId);
}

