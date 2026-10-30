package com.familyfund.identity.internal.repository;

import com.familyfund.identity.internal.entity.SystemRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SystemRoleRepository extends JpaRepository<SystemRole, String> {
}
