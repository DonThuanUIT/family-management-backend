package com.familyfund.identity.internal.service;

import com.familyfund.core.exception.BadRequestException;
import com.familyfund.core.security.JwtTokenProvider;
import com.familyfund.genealogy.api.GenealogyQueryService;
import com.familyfund.genealogy.api.dto.MemberSummaryDto;
import com.familyfund.identity.internal.dto.request.LoginRequest;
import com.familyfund.identity.internal.dto.request.RefreshTokenRequest;
import com.familyfund.identity.internal.dto.response.AuthTokenResponse;
import com.familyfund.identity.internal.entity.AccountStatus;
import com.familyfund.identity.internal.entity.SystemRole;
import com.familyfund.identity.internal.entity.UserAccount;
import com.familyfund.identity.internal.repository.UserAccountRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuthServiceImpl implements AuthService {

    private final UserAccountRepository userAccountRepository;
    private final GenealogyQueryService genealogyQueryService;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

    @Override
    public AuthTokenResponse login(LoginRequest request) {
        String identifier = request.loginIdentifier().trim();
        Optional<UserAccount> userAccountOpt = userAccountRepository.findByPhoneNumber(identifier);
        if (userAccountOpt.isEmpty()) {
            userAccountOpt = userAccountRepository.findByEmail(identifier);
        }

        UserAccount userAccount = userAccountOpt.orElseThrow(() ->
                new BadRequestException("Số điện thoại/Email hoặc mật khẩu không chính xác"));

        if (!passwordEncoder.matches(request.password().trim(), userAccount.getPasswordHash())) {
            throw new BadRequestException("Số điện thoại/Email hoặc mật khẩu không chính xác");
        }

        if (userAccount.getAccountStatus() != AccountStatus.Active) {
            throw new BadRequestException("Tài khoản đang ở trạng thái: " + userAccount.getAccountStatus() +
                    ". Vui lòng liên hệ quản trị viên để được kích hoạt.");
        }

        return buildAuthTokenResponse(userAccount, null);
    }

    @Override
    public AuthTokenResponse refreshToken(RefreshTokenRequest request) {
        String token = request.refreshToken().trim();
        if (!jwtTokenProvider.validateToken(token)) {
            throw new BadRequestException("Refresh token không hợp lệ hoặc đã hết hạn");
        }

        String accountId = jwtTokenProvider.getAccountId(token);
        UserAccount userAccount = userAccountRepository.findById(accountId)
                .orElseThrow(() -> new BadRequestException("Tài khoản không tồn tại"));

        if (userAccount.getAccountStatus() != AccountStatus.Active) {
            throw new BadRequestException("Tài khoản không hoạt động");
        }

        return buildAuthTokenResponse(userAccount, token);
    }

    private AuthTokenResponse buildAuthTokenResponse(UserAccount userAccount, String existingRefreshToken) {
        MemberSummaryDto member = null;
        if (userAccount.getMemberId() != null) {
            member = genealogyQueryService.findMemberSummary(userAccount.getMemberId()).orElse(null);
        }

        String clanId = member != null ? member.clanId() : null;
        String memberId = member != null ? member.memberId() : userAccount.getMemberId();
        String fullName = member != null ? member.fullName() : userAccount.getPhoneNumber();

        Set<String> roles = userAccount.getRoles().stream()
                .map(SystemRole::getRoleId)
                .collect(Collectors.toSet());

        String accessToken = jwtTokenProvider.generateAccessToken(
                userAccount.getAccountId(),
                clanId,
                memberId,
                fullName,
                roles
        );

        String refreshToken = existingRefreshToken != null
                ? existingRefreshToken
                : jwtTokenProvider.generateRefreshToken(userAccount.getAccountId());

        log.info("Issued JWT tokens for account [{}], clan [{}], roles {}",
                userAccount.getAccountId(), clanId, roles);

        return new AuthTokenResponse(
                accessToken,
                refreshToken,
                "Bearer",
                jwtTokenProvider.getAccessTokenExpirationSeconds(),
                userAccount.getAccountId(),
                userAccount.getPhoneNumber(),
                userAccount.getEmail(),
                fullName,
                clanId,
                memberId,
                roles
        );
    }
}
