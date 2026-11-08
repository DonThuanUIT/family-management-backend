package com.familyfund.identity.internal.service;

import com.familyfund.core.exception.BadRequestException;
import com.familyfund.core.exception.ResourceNotFoundException;
import com.familyfund.genealogy.api.GenealogyQueryService;
import com.familyfund.genealogy.api.dto.MemberSummaryDto;
import com.familyfund.identity.api.dto.AccountSummaryDto;
import com.familyfund.identity.internal.entity.AccountRole;
import com.familyfund.identity.internal.entity.AccountRoleId;
import com.familyfund.identity.internal.entity.AccountStatus;
import com.familyfund.identity.internal.entity.SystemRole;
import com.familyfund.identity.internal.entity.UserAccount;
import com.familyfund.identity.internal.repository.AccountRoleRepository;
import com.familyfund.identity.internal.repository.SystemRoleRepository;
import com.familyfund.identity.internal.repository.UserAccountRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class IdentityRoleCommandServiceImplTest {

    private static final String CLAN_ID = "CLAN_TEST";
    private static final String MEMBER_ID = "MEM_001";
    private static final String ACCOUNT_ID = "ACC_001";

    @Mock
    private UserAccountRepository userAccountRepository;

    @Mock
    private SystemRoleRepository systemRoleRepository;

    @Mock
    private AccountRoleRepository accountRoleRepository;

    @Mock
    private GenealogyQueryService genealogyQueryService;

    @InjectMocks
    private IdentityRoleCommandServiceImpl identityRoleCommandService;

    @Nested
    @DisplayName("requireActiveAccountByMemberId Tests")
    class RequireActiveAccountTests {

        @Test
        @DisplayName("Member with active account - Success")
        void activeAccount_Success() {
            when(genealogyQueryService.findMemberSummary(MEMBER_ID))
                    .thenReturn(Optional.of(new MemberSummaryDto(MEMBER_ID, "Nguyễn Văn A", CLAN_ID, "BR_1")));
            UserAccount account = UserAccount.builder()
                    .accountId(ACCOUNT_ID)
                    .memberId(MEMBER_ID)
                    .phoneNumber("0901234567")
                    .email("a@gtvn.vn")
                    .accountStatus(AccountStatus.Active)
                    .roles(new HashSet<>())
                    .build();
            when(userAccountRepository.findByMemberId(MEMBER_ID)).thenReturn(Optional.of(account));

            AccountSummaryDto dto = identityRoleCommandService.requireActiveAccountByMemberId(MEMBER_ID, CLAN_ID);

            assertNotNull(dto);
            assertEquals(ACCOUNT_ID, dto.accountId());
            assertEquals("Active", dto.accountStatus());
        }

        @Test
        @DisplayName("Member without linked account - Throws BadRequestException")
        void noLinkedAccount_ThrowsException() {
            when(genealogyQueryService.findMemberSummary(MEMBER_ID))
                    .thenReturn(Optional.of(new MemberSummaryDto(MEMBER_ID, "Nguyễn Văn A", CLAN_ID, "BR_1")));
            when(userAccountRepository.findByMemberId(MEMBER_ID)).thenReturn(Optional.empty());

            assertThrows(BadRequestException.class,
                    () -> identityRoleCommandService.requireActiveAccountByMemberId(MEMBER_ID, CLAN_ID));
        }

        @Test
        @DisplayName("Member with Suspended account - Throws BadRequestException")
        void suspendedAccount_ThrowsException() {
            when(genealogyQueryService.findMemberSummary(MEMBER_ID))
                    .thenReturn(Optional.of(new MemberSummaryDto(MEMBER_ID, "Nguyễn Văn A", CLAN_ID, "BR_1")));
            UserAccount account = UserAccount.builder()
                    .accountId(ACCOUNT_ID)
                    .memberId(MEMBER_ID)
                    .accountStatus(AccountStatus.Suspended)
                    .build();
            when(userAccountRepository.findByMemberId(MEMBER_ID)).thenReturn(Optional.of(account));

            assertThrows(BadRequestException.class,
                    () -> identityRoleCommandService.requireActiveAccountByMemberId(MEMBER_ID, CLAN_ID));
        }
    }

    @Nested
    @DisplayName("Role Mutation Tests")
    class RoleMutationTests {

        @Test
        @DisplayName("Assign role adds system role to account and persists account_roles")
        void assignRole_Success() {
            UserAccount account = UserAccount.builder()
                    .accountId(ACCOUNT_ID)
                    .roles(new HashSet<>())
                    .build();
            when(userAccountRepository.findById(ACCOUNT_ID)).thenReturn(Optional.of(account));
            when(systemRoleRepository.findById("ROLE_BRANCH_HEAD")).thenReturn(Optional.of(
                    SystemRole.builder().roleId("ROLE_BRANCH_HEAD").roleName("Trưởng chi").build()
            ));

            identityRoleCommandService.assignRoleToAccount(ACCOUNT_ID, "ROLE_BRANCH_HEAD");

            verify(userAccountRepository).save(account);
            verify(accountRoleRepository).save(any(AccountRole.class));
            assertTrue(account.getRoles().stream().anyMatch(r -> "ROLE_BRANCH_HEAD".equals(r.getRoleId())));
        }

        @Test
        @DisplayName("Revoke role removes from account and deletes account_roles entry")
        void revokeRole_Success() {
            SystemRole branchRole = SystemRole.builder().roleId("ROLE_BRANCH_HEAD").roleName("Trưởng chi").build();
            Set<SystemRole> roles = new HashSet<>();
            roles.add(branchRole);
            UserAccount account = UserAccount.builder()
                    .accountId(ACCOUNT_ID)
                    .roles(roles)
                    .build();

            when(userAccountRepository.findById(ACCOUNT_ID)).thenReturn(Optional.of(account));
            AccountRoleId expectedId = new AccountRoleId(ACCOUNT_ID, "ROLE_BRANCH_HEAD");
            when(accountRoleRepository.existsById(expectedId)).thenReturn(true);

            identityRoleCommandService.revokeRoleFromAccount(ACCOUNT_ID, "ROLE_BRANCH_HEAD");

            verify(userAccountRepository).save(account);
            verify(accountRoleRepository).deleteById(expectedId);
            assertTrue(account.getRoles().isEmpty());
        }
    }
}
