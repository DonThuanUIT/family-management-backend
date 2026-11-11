package com.familyfund.genealogy.internal.media;

import com.familyfund.core.security.AccountAccess;
import com.familyfund.core.security.SystemRoles;
import com.familyfund.core.storage.MediaException;
import com.familyfund.core.tenant.TenantContext;
import com.familyfund.genealogy.api.GenealogyQueryService;
import com.familyfund.genealogy.internal.entity.Member;
import com.familyfund.genealogy.internal.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.util.HashSet;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class MemberMediaPolicy {
    private final AccountAccess accounts;
    private final MemberRepository members;
    private final GenealogyQueryService genealogy;

    public Access access(String memberId) {
        var caller = accounts.current();
        if (!caller.clanId().equals(TenantContext.getClanId())) throw MediaException.missing();
        Member target = members.findByBranch_ClanIdAndMemberId(caller.clanId(), memberId).orElseThrow(MediaException::missing);
        boolean self = caller.memberId().equals(target.getMemberId());
        Set<String> managed = caller.hasRole(SystemRoles.BRANCH_HEAD)
                ? new HashSet<>(genealogy.getBranchIdsManagedByMember(caller.clanId(), caller.memberId())) : Set.of();
        boolean manager = caller.hasRole(SystemRoles.CLAN_HEAD) || caller.hasRole(SystemRoles.ADMIN)
                || managed.contains(target.getBranch().getBranchId());
        String callerBranch = members.findByBranch_ClanIdAndMemberId(caller.clanId(), caller.memberId())
                .orElseThrow(MediaException::missing).getBranch().getBranchId();
        // Traverse using the existing public tree rather than granting parent/sibling branch access.
        boolean branch = contains(genealogy.getOnboardingBranchTree(caller.clanId(), target.getBranch().getBranchId()), callerBranch);
        return new Access(caller, target, self, manager,
                manager || (self && Member.STATUS_LIVING.equals(target.getStatus()) && !Boolean.TRUE.equals(target.getIsPlaceholder())), branch);
    }
    private boolean contains(com.familyfund.genealogy.api.OnboardingBranchTreeNode node, String branchId) {
        return node != null && (branchId.equals(node.branchId()) || node.children().stream().anyMatch(n -> contains(n, branchId)));
    }
    public record Access(AccountAccess.Snapshot caller, Member member, boolean self, boolean manager, boolean canEdit, boolean sameBranchTree) {
        public boolean canView(String visibility) {
            return self || manager || "CLAN".equals(visibility) || ("BRANCH".equals(visibility) && sameBranchTree);
        }
        public void requireEdit() {
            if (!canEdit) throw new MediaException(403, "MEMBER_MEDIA_FORBIDDEN", "Bạn không có quyền chỉnh sửa tư liệu của hồ sơ này");
        }
    }
}
