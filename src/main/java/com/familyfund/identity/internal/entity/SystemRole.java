package com.familyfund.identity.internal.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "system_roles")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SystemRole {

    @Id
    @Column(name = "role_id", length = 20, nullable = false)
    private String roleId;

    @Column(name = "role_name", length = 50, nullable = false)
    private String roleName;

    @Column(name = "description", length = 255)
    private String description;
}
