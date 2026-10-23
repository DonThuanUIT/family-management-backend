package com.familyfund.identity.internal.service;

import com.familyfund.core.exception.BadRequestException;
import com.familyfund.genealogy.api.GenealogyBootstrapService;
import com.familyfund.genealogy.api.dto.ClanBootstrapCommand;
import com.familyfund.genealogy.api.dto.ClanBootstrapResult;
import com.familyfund.identity.internal.dto.request.RegisterCreatorRequest;
import com.familyfund.identity.internal.dto.response.ClanResponse;
import com.familyfund.identity.internal.dto.response.CreatorRegistrationResponse;
import com.familyfund.identity.internal.dto.response.MemberResponse;
import com.familyfund.identity.internal.dto.response.UserAccountResponse;
import com.familyfund.identity.internal.entity.AccountStatus;
import com.familyfund.identity.internal.entity.SystemRole;
import com.familyfund.identity.internal.entity.UserAccount;
import com.familyfund.identity.internal.repository.SystemRoleRepository;
import com.familyfund.identity.internal.repository.UserAccountRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class CreatorRegistrationServiceImpl implements CreatorRegistrationService {

    public static final String ROLE_CLAN_HEAD = "ROLE_CLAN_HEAD";

    private final UserAccountRepository userAccountRepository;
    private final SystemRoleRepository systemRoleRepository;
    private final GenealogyBootstrapService genealogyBootstrapService;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CreatorRegistrationResponse registerCreator(RegisterCreatorRequest request) {
        String cleanPhone = request.phoneNumber().trim();
        if (userAccountRepository.existsByPhoneNumber(cleanPhone)) {
            throw new BadRequestException("Số điện thoại '" + cleanPhone + "' đã được đăng ký tài khoản");
        }

        String cleanEmail = cleanString(request.email());
        if (cleanEmail != null && userAccountRepository.existsByEmail(cleanEmail)) {
            throw new BadRequestException("Địa chỉ email '" + cleanEmail + "' đã được đăng ký tài khoản");
        }

        // 1. Bootstrap Clan, root branch, ancestor (if any), and creator member in genealogy module
        ClanBootstrapCommand bootstrapCommand = new ClanBootstrapCommand(
                request.clanName(),
                request.ancestorName(),
                request.originLocation(),
                request.establishedYear(),
                request.description(),
                request.fullName(),
                request.gender(),
                request.generation(),
                request.dateOfBirth()
        );
        ClanBootstrapResult bootstrapResult = genealogyBootstrapService.bootstrapClanAndCreator(bootstrapCommand);

        // 2. Resolve default CLAN_HEAD role
        SystemRole clanHeadRole = systemRoleRepository.findById(ROLE_CLAN_HEAD)
                .orElseGet(() -> systemRoleRepository.save(SystemRole.builder()
                        .roleId(ROLE_CLAN_HEAD)
                        .roleName("Trưởng Tộc")
                        .description("Quyền cao nhất dòng họ")
                        .build()));

        Set<SystemRole> assignedRoles = new HashSet<>();
        assignedRoles.add(clanHeadRole);

        // 3. Create and persist UserAccount
        String accountId = "ACC_" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        String passwordHash = passwordEncoder.encode(request.password().trim());

        UserAccount userAccount = UserAccount.builder()
                .accountId(accountId)
                .memberId(bootstrapResult.creatorMemberId())
                .phoneNumber(cleanPhone)
                .email(cleanEmail)
                .passwordHash(passwordHash)
                .accountStatus(AccountStatus.Active)
                .roles(assignedRoles)
                .build();

        UserAccount savedAccount = userAccountRepository.save(userAccount);

        log.info("Registered creator account [{}] linked to member [{}] for new clan [{}]",
                savedAccount.getAccountId(), savedAccount.getMemberId(), bootstrapResult.clanId());

        // 4. Construct comprehensive response
        UserAccountResponse accountResponse = new UserAccountResponse(
                savedAccount.getAccountId(),
                savedAccount.getPhoneNumber(),
                savedAccount.getEmail(),
                savedAccount.getAccountStatus().name(),
                savedAccount.getRoles().stream().map(SystemRole::getRoleId).collect(Collectors.toSet()),
                savedAccount.getCreatedAt()
        );

        ClanResponse clanResponse = new ClanResponse(
                bootstrapResult.clanId(),
                bootstrapResult.clanName(),
                bootstrapResult.ancestorName(),
                bootstrapResult.originLocation(),
                bootstrapResult.establishedYear(),
                bootstrapResult.description()
        );

        MemberResponse memberResponse = new MemberResponse(
                bootstrapResult.creatorMemberId(),
                bootstrapResult.creatorFullName(),
                bootstrapResult.creatorGender(),
                bootstrapResult.creatorGeneration(),
                bootstrapResult.creatorDateOfBirth(),
                bootstrapResult.rootBranchId()
        );

        return new CreatorRegistrationResponse(accountResponse, clanResponse, memberResponse);
    }

    private String cleanString(String input) {
        if (input == null || input.isBlank()) {
            return null;
        }
        return input.trim();
    }
}
