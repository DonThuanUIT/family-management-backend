package com.familyfund.identity.internal.repository;

import com.familyfund.identity.internal.entity.AccountRole;
import com.familyfund.identity.internal.entity.AccountRoleId;
import com.familyfund.identity.internal.entity.AccountStatus;
import com.familyfund.identity.internal.entity.BranchJoinRequest;
import com.familyfund.identity.internal.entity.InvitationCode;
import com.familyfund.identity.internal.entity.InviteType;
import com.familyfund.identity.internal.entity.JoinRequestApprovalStatus;
import com.familyfund.identity.internal.entity.ProposalType;
import com.familyfund.identity.internal.entity.UserAccount;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@Transactional
class IdentityRepositoryTest {

    @Autowired
    private UserAccountRepository userAccountRepository;

    @Autowired
    private AccountRoleRepository accountRoleRepository;

    @Autowired
    private BranchJoinRequestRepository branchJoinRequestRepository;

    @Autowired
    private InvitationCodeRepository invitationCodeRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private EntityManager entityManager;

    @BeforeEach
    void setUpMasterData() {
        // Ensure family_branches record exists for foreign key constraint
        entityManager.createNativeQuery(
                "INSERT INTO family_branches (branch_id, clan_id, branch_name, generation_level) " +
                "VALUES ('BR_TEST_001', 'CLAN_DEFAULT', 'Nhánh Kiểm Thử', 1) " +
                "ON CONFLICT (branch_id) DO NOTHING"
        ).executeUpdate();
    }

    @Test
    @DisplayName("Verify UserAccount CRUD, uniqueness checks, and PasswordEncoder")
    void testUserAccountRepository() {
        String rawPassword = "SecurePassword123!";
        String encoded = passwordEncoder.encode(rawPassword);
        assertTrue(passwordEncoder.matches(rawPassword, encoded));

        UserAccount account = UserAccount.builder()
                .accountId("ACC_TEST_001")
                .phoneNumber("0912345678")
                .email("test.user@familyfund.vn")
                .passwordHash(encoded)
                .accountStatus(AccountStatus.PendingApproval)
                .build();

        userAccountRepository.save(account);

        Optional<UserAccount> found = userAccountRepository.findByPhoneNumber("0912345678");
        assertTrue(found.isPresent());
        assertEquals("ACC_TEST_001", found.get().getAccountId());
        assertEquals(AccountStatus.PendingApproval, found.get().getAccountStatus());
        assertTrue(passwordEncoder.matches(rawPassword, found.get().getPasswordHash()));

        assertTrue(userAccountRepository.existsByPhoneNumber("0912345678"));
        assertFalse(userAccountRepository.existsByPhoneNumber("0999999999"));
        assertTrue(userAccountRepository.existsByEmail("test.user@familyfund.vn"));
        assertFalse(userAccountRepository.existsByEmail("unknown@familyfund.vn"));
    }

    @Test
    @DisplayName("Verify AccountRole composite key mapping and queries")
    void testAccountRoleRepository() {
        UserAccount account = UserAccount.builder()
                .accountId("ACC_TEST_ROLE")
                .phoneNumber("0912345679")
                .passwordHash("hashed")
                .accountStatus(AccountStatus.Active)
                .build();
        userAccountRepository.save(account);

        AccountRole role = AccountRole.builder()
                .id(new AccountRoleId("ACC_TEST_ROLE", "ROLE_MEMBER"))
                .build();
        accountRoleRepository.save(role);

        List<AccountRole> roles = accountRoleRepository.findByIdAccountId("ACC_TEST_ROLE");
        assertEquals(1, roles.size());
        assertEquals("ROLE_MEMBER", roles.get(0).getId().getRoleId());
        assertTrue(accountRoleRepository.existsByIdAccountIdAndIdRoleId("ACC_TEST_ROLE", "ROLE_MEMBER"));
        assertFalse(accountRoleRepository.existsByIdAccountIdAndIdRoleId("ACC_TEST_ROLE", "ROLE_ADMIN"));
    }

    @Test
    @DisplayName("Verify BranchJoinRequest persistence and custom queries with JOIN FETCH")
    void testBranchJoinRequestRepository() {
        // 1. Create creator account and invitation code to satisfy FK constraints
        UserAccount creator = UserAccount.builder()
                .accountId("ACC_CREATOR")
                .phoneNumber("0911111111")
                .passwordHash("hash")
                .accountStatus(AccountStatus.Active)
                .build();
        userAccountRepository.save(creator);

        InvitationCode invite = InvitationCode.builder()
                .inviteCode("TESTCODE1")
                .clanId("CLAN_DEFAULT")
                .inviteType(InviteType.GeneralClanInvite)
                .createdByAccountId("ACC_CREATOR")
                .build();
        invitationCodeRepository.save(invite);

        // 2. Create applicant account
        UserAccount applicant = UserAccount.builder()
                .accountId("ACC_APPLICANT")
                .phoneNumber("0922222222")
                .passwordHash("hash")
                .accountStatus(AccountStatus.PendingApproval)
                .build();
        userAccountRepository.save(applicant);

        // 3. Create BranchJoinRequest
        BranchJoinRequest request = BranchJoinRequest.builder()
                .requestId("REQ_TEST_001")
                .clanId("CLAN_DEFAULT")
                .userAccount(applicant)
                .inviteCode("TESTCODE1")
                .fullName("Nguyen Van Test")
                .dateOfBirth(LocalDate.of(1995, 8, 20))
                .gender("Male")
                .proposalType(ProposalType.ExistingBranchLeaf)
                .targetBranchId("BR_TEST_001")
                .proposedGeneration(3)
                .relationshipNote("Con trai truong")
                .approvalStatus(JoinRequestApprovalStatus.Pending)
                .build();

        branchJoinRequestRepository.save(request);

        // 4. Test fetch join query
        Optional<BranchJoinRequest> found = branchJoinRequestRepository
                .findWithAccountByRequestIdAndClanId("REQ_TEST_001", "CLAN_DEFAULT");
        assertTrue(found.isPresent());
        assertEquals("Nguyen Van Test", found.get().getFullName());
        assertNotNull(found.get().getUserAccount());
        assertEquals("0922222222", found.get().getUserAccount().getPhoneNumber());
        assertEquals(ProposalType.ExistingBranchLeaf, found.get().getProposalType());

        // 5. Test pageable queries
        Page<BranchJoinRequest> page = branchJoinRequestRepository
                .findWithAccountByClanIdAndApprovalStatus("CLAN_DEFAULT", JoinRequestApprovalStatus.Pending, PageRequest.of(0, 10));
        assertTrue(page.getTotalElements() >= 1);

        Page<BranchJoinRequest> branchPage = branchJoinRequestRepository
                .findWithAccountByClanIdAndBranchIdsAndApprovalStatus(
                        "CLAN_DEFAULT", List.of("BR_TEST_001"), JoinRequestApprovalStatus.Pending, PageRequest.of(0, 10));
        assertTrue(branchPage.getTotalElements() >= 1);

        assertTrue(branchJoinRequestRepository.existsByUserAccountAccountIdAndApprovalStatus("ACC_APPLICANT", JoinRequestApprovalStatus.Pending));
        assertFalse(branchJoinRequestRepository.existsByUserAccountAccountIdAndApprovalStatus("ACC_APPLICANT", JoinRequestApprovalStatus.Approved));
    }
}
