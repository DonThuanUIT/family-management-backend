package com.familyfund.contribution.internal.entity;

import com.familyfund.contribution.api.dto.ContributionType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Entity mapping table `contributions`.
 * Base parent record tracking all financial and in-kind contributions for events.
 */
@Entity
@Table(name = "contributions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Contribution {

    @Id
    @Column(name = "contribution_id", length = 20, nullable = false, updatable = false)
    private String contributionId;

    @Column(name = "event_id", length = 20, nullable = false)
    private String eventId;

    @Column(name = "payer_member_id", length = 20, nullable = false)
    private String payerMemberId;

    @Column(name = "receiver_account_id", length = 20, nullable = false)
    private String receiverAccountId;

    @CreationTimestamp
    @Column(name = "contribution_date", updatable = false)
    private Instant contributionDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "contribution_type", length = 20, nullable = false)
    private ContributionType contributionType;

    @Column(name = "total_monetary_value", precision = 18, scale = 2, nullable = false)
    private BigDecimal totalMonetaryValue;

    @PrePersist
    public void prePersist() {
        if (contributionDate == null) {
            contributionDate = Instant.now();
        }
    }
}
