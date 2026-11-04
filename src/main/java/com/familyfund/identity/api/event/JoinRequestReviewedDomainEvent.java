package com.familyfund.identity.api.event;

import java.time.Instant;

/**
 * Published after a branch join request has been reviewed (approved or rejected) so the applicant can be notified
 * out of band (US-17 follow-up).
 *
 * <p>Carries everything a delivery adapter needs (recipient phone number, outcome, reason) without exposing the
 * user-account entity or any identity-internal type, mirroring the domain-event style used by the {@code event}
 * module (see {@code TaskAssignedDomainEvent}).</p>
 *
 * @param requestId         the reviewed join request
 * @param accountId         the applicant's temporary account
 * @param phoneNumber       the applicant's phone number (delivery address)
 * @param fullName          the applicant's full name
 * @param clanId            tenant scope
 * @param targetBranchId    the branch the applicant was approved into (or proposed)
 * @param targetBranchName  display name of that branch, may be null
 * @param approved          true when approved, false when rejected
 * @param rejectionReason   the reason recorded on rejection, null when approved
 * @param memberId          the created member ID when approved, null when rejected
 * @param reviewedByAccountId the reviewer's account
 * @param reviewedAt        review timestamp
 */
public record JoinRequestReviewedDomainEvent(
        String requestId,
        String accountId,
        String phoneNumber,
        String fullName,
        String clanId,
        String targetBranchId,
        String targetBranchName,
        boolean approved,
        String rejectionReason,
        String memberId,
        String reviewedByAccountId,
        Instant reviewedAt
) {
}