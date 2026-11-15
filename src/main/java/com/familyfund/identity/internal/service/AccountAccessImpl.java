package com.familyfund.identity.internal.service;

import com.familyfund.core.security.AccountAccess;
import com.familyfund.core.security.CurrentUserProvider;
import com.familyfund.genealogy.api.GenealogyQueryService;
import com.familyfund.identity.internal.entity.AccountStatus;
import com.familyfund.identity.internal.entity.SystemRole;
import com.familyfund.identity.internal.repository.UserAccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AccountAccessImpl implements AccountAccess {
    private final CurrentUserProvider currentUser;
    private final UserAccountRepository accounts;
    private final GenealogyQueryService genealogy;

    @Override
    @Transactional(readOnly = true)
    public Snapshot current() {
        String id = currentUser.currentAccountId().orElseThrow(AccountAccessImpl::denied);
        var account = accounts.findById(id).orElseThrow(AccountAccessImpl::denied);
        if (account.getAccountStatus() != AccountStatus.Active || account.getMemberId() == null) throw denied();
        var member = genealogy.findMemberSummary(account.getMemberId()).orElseThrow(AccountAccessImpl::denied);
        return new Snapshot(id, member.memberId(), member.clanId(), account.getRoles().stream()
                .map(SystemRole::getRoleId).collect(Collectors.toSet()));
    }

    private static AccessDeniedException denied() {
        return new AccessDeniedException("Tài khoản không hoạt động hoặc chưa liên kết gia phả");
    }
}
