package com.familyfund.event.internal.repository;

import com.familyfund.event.internal.entity.DeathAnniversaryReminder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Spring Data JPA repository for `death_anniversary_reminders` (US-40).
 */
@Repository
public interface DeathAnniversaryReminderRepository extends JpaRepository<DeathAnniversaryReminder, String> {

    /** Idempotency check: has this milestone already been sent for this member in this lunar year? */
    boolean existsByMemberIdAndLunarYearAndMilestoneDays(String memberId, Integer lunarYear, Integer milestoneDays);

    /** Every reminder recorded for one member, across years (US-40 read-side). */
    List<DeathAnniversaryReminder> findAllByMemberId(String memberId);
}
