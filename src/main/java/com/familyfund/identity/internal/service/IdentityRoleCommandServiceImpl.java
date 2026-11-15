package com.familyfund.identity.internal.service;

import com.familyfund.core.exception.BadRequestException;
import com.familyfund.core.exception.ResourceNotFoundException;
import com.familyfund.genealogy.api.GenealogyQueryService;
import com.familyfund.genealogy.api.dto.MemberSummaryDto;
import com.familyfund.identity.api.IdentityRoleCommandService;
import com.familyfund.identity.api.dto.AccountSummaryDto;
import com.familyfund.identity.internal.entity.AccountRole;
import com.familyfund.identity.internal.entity.AccountRoleId;
import com.familyfund.identity.internal.entity.AccountStatus;
import com.familyfund.identity.internal.entity.SystemRole;
import com.familyfund.identity.internal.entity.UserAccount;
import com.familyfund.identity.internal.repository.AccountRoleRepository;
import com.familyfund.identity.internal.repository.SystemRoleRepository;
import com.familyfund.identity.internal.repository.UserAccountRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Implementation of {@link IdentityRoleCommandService} for managing user account roles.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IdentityRoleCommandServiceImpl implements IdentityRoleCommandService {

    private final UserAccountRepository userAccountRepository;
    private final SystemRoleRepository systemRoleRepository;
    private final AccountRoleRepository accountRoleRepository;
    private final GenealogyQueryService genealogyQueryService;

    @Override
    @Transactional(readOnly = true)
    public Optional<AccountSummaryDto> findAccountByMemberId(String memberId) {
        if (memberId == null || memberId.isBlank()) {
            return Optional.empty();
        }
        return userAccountRepository.findByMemberId(memberId.trim())
                .map(this::toAccountSummaryDto);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, AccountSummaryDto> findAccountsByMemberIds(Collection<String> memberIds) {
        if (memberIds == null || memberIds.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, AccountSummaryDto> map = new HashMap<>();
        for (String memberId : memberIds) {
            if (memberId != null && !memberId.isBlank()) {
                userAccountRepository.findByMemberId(memberId.trim())
                        .ifPresent(acc -> map.put(memberId.trim(), toAccountSummaryDto(acc)));
            }
        }
        return map;
    }

    @Override
    @Transactional(readOnly = true)
    public List<AccountSummaryDto> findActiveAccountsByClan(String clanId) {
        if (clanId == null || clanId.isBlank()) {
            return List.of();
        }
        List<String> memberIds = genealogyQueryService.findAllAliveMembersByClanId(clanId.trim()).stream()
                .map(MemberSummaryDto::memberId)
                .filter(memberId -> memberId != null && !memberId.isBlank())
                .toList();
        if (memberIds.isEmpty()) {
            return List.of();
        }
        return userAccountRepository.findByMemberIdIn(memberIds).stream()
                .filter(account -> account.getAccountStatus() == AccountStatus.Active)
                .map(this::toAccountSummaryDto)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public AccountSummaryDto requireActiveAccountByMemberId(String memberId, String clanId) {
        if (memberId == null || memberId.isBlank()) {
            throw new BadRequestException("Mã thành viên không được để trống");
        }

        // Verify member exists in genealogy and belongs to clan
        MemberSummaryDto member = genealogyQueryService.findMemberSummary(memberId.trim())
                .orElseThrow(() -> new ResourceNotFoundException("Thành viên", "memberId", memberId));

        if (clanId != null && !clanId.isBlank() && member.clanId() != null && !clanId.equals(member.clanId())) {
            throw new BadRequestException("Thành viên '" + memberId + "' không thuộc dòng họ '" + clanId + "'");
        }

        UserAccount account = userAccountRepository.findByMemberId(memberId.trim())
                .orElseThrow(() -> new BadRequestException(
                        "Thành viên '" + member.fullName() + "' (ID: " + memberId + ") chưa có tài khoản người dùng liên kết. " +
                        "Vui lòng tạo tài khoản hoặc mời thành viên kích hoạt trước khi chỉ định vai trò Trưởng nhánh."
                ));

        if (account.getAccountStatus() != AccountStatus.Active) {
            throw new BadRequestException(
                    "Tài khoản của thành viên '" + member.fullName() + "' đang ở trạng thái '" +
                    account.getAccountStatus() + "' (không hoạt động). Không thể chỉ định vai trò."
            );
        }

        return toAccountSummaryDto(account);
    }

    @Override
    @Transactional
    public void assignRoleToAccount(String accountId, String roleId) {
        if (accountId == null || accountId.isBlank() || roleId == null || roleId.isBlank()) {
            throw new BadRequestException("accountId và roleId không được để trống");
        }

        String cleanAccountId = accountId.trim();
        String cleanRoleId = roleId.trim();

        UserAccount account = userAccountRepository.findById(cleanAccountId)
                .orElseThrow(() -> new ResourceNotFoundException("Tài khoản", "accountId", cleanAccountId));

        SystemRole systemRole = systemRoleRepository.findById(cleanRoleId)
                .orElseGet(() -> systemRoleRepository.save(SystemRole.builder()
                        .roleId(cleanRoleId)
                        .roleName(cleanRoleId)
                        .description("Tự động khởi tạo vai trò hệ thống")
                        .build()));

        // Add to user account entity Set and save
        account.getRoles().add(systemRole);
        userAccountRepository.save(account);

        // Also ensure account_roles row exists
        AccountRoleId compositeId = new AccountRoleId(cleanAccountId, cleanRoleId);
        if (!accountRoleRepository.existsById(compositeId)) {
            accountRoleRepository.save(AccountRole.builder().id(compositeId).build());
        }

        log.info("Assigned role [{}] to account [{}]", cleanRoleId, cleanAccountId);
    }

    @Override
    @Transactional
    public void revokeRoleFromAccount(String accountId, String roleId) {
        if (accountId == null || accountId.isBlank() || roleId == null || roleId.isBlank()) {
            throw new BadRequestException("accountId và roleId không được để trống");
        }

        String cleanAccountId = accountId.trim();
        String cleanRoleId = roleId.trim();

        userAccountRepository.findById(cleanAccountId).ifPresent(account -> {
            account.getRoles().removeIf(r -> cleanRoleId.equalsIgnoreCase(r.getRoleId()));
            userAccountRepository.save(account);
        });

        AccountRoleId compositeId = new AccountRoleId(cleanAccountId, cleanRoleId);
        if (accountRoleRepository.existsById(compositeId)) {
            accountRoleRepository.deleteById(compositeId);
        }

        log.info("Revoked role [{}] from account [{}]", cleanRoleId, cleanAccountId);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean hasRole(String accountId, String roleId) {
        if (accountId == null || accountId.isBlank() || roleId == null || roleId.isBlank()) {
            return false;
        }
        return accountRoleRepository.existsByIdAccountIdAndIdRoleId(accountId.trim(), roleId.trim());
    }

    private AccountSummaryDto toAccountSummaryDto(UserAccount account) {
        Set<String> roleCodes = account.getRoles() != null
                ? account.getRoles().stream().map(SystemRole::getRoleId).collect(Collectors.toSet())
                : Collections.emptySet();

        return new AccountSummaryDto(
                account.getAccountId(),
                account.getMemberId(),
                account.getPhoneNumber(),
                account.getEmail(),
                account.getAccountStatus() != null ? account.getAccountStatus().name() : null,
                roleCodes
        );
    }
}
