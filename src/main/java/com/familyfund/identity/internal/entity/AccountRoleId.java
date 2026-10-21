package com.familyfund.identity.internal.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * Composite Primary Key for `account_roles` table.
 */
@Embeddable
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AccountRoleId implements Serializable {

    @Column(name = "account_id", length = 20, nullable = false)
    private String accountId;

    @Column(name = "role_id", length = 20, nullable = false)
    private String roleId;
}
