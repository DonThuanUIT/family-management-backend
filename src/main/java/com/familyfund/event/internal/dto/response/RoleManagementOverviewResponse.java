package com.familyfund.event.internal.dto.response;

import java.util.List;

public record RoleManagementOverviewResponse(
        String clanId,
        List<BranchRoleOverviewDto> branches,
        List<MemberRoleOptionDto> members
) {
}
