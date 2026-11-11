package com.familyfund.identity.internal.service;

import com.familyfund.genealogy.api.event.MemberLifeStatusChangedEvent;
import com.familyfund.identity.internal.entity.AccountStatus;
import com.familyfund.identity.internal.entity.UserAccount;
import com.familyfund.identity.internal.repository.UserAccountRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Lắng nghe sự kiện thay đổi tình trạng sống/đã mất của thành viên (US-27).
 * Khi thành viên qua đời (Deceased), tự động khóa tài khoản đăng nhập liên kết để bảo mật.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MemberLifeStatusEventListener {

    private final UserAccountRepository userAccountRepository;

    @EventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onMemberLifeStatusChanged(MemberLifeStatusChangedEvent event) {
        if (event == null || event.memberId() == null || event.memberId().isBlank()) {
            return;
        }

        String memberId = event.memberId().trim();
        Optional<UserAccount> accountOpt = userAccountRepository.findByMemberId(memberId);
        if (accountOpt.isEmpty()) {
            log.debug("Member [{}] has no associated user account, skipping account lockout check", memberId);
            return;
        }

        UserAccount account = accountOpt.get();

        if (event.isDeceased()) {
            if (account.getAccountStatus() != AccountStatus.Deactivated) {
                account.setAccountStatus(AccountStatus.Deactivated);
                userAccountRepository.save(account);
                log.info("Locked user account [{}] (status set to Deactivated) for deceased member [{}]",
                        account.getAccountId(), memberId);
            }
        } else {
            // Khi hiệu đính từ Deceased về Living: khôi phục trạng thái Active nếu trước đó bị Deactivated
            if (account.getAccountStatus() == AccountStatus.Deactivated) {
                account.setAccountStatus(AccountStatus.Active);
                userAccountRepository.save(account);
                log.info("Restored user account [{}] (status set to Active) for member [{}]",
                        account.getAccountId(), memberId);
            }
        }
    }
}
