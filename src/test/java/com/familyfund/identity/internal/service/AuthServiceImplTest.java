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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    @Mock
    private UserAccountRepository userAccountRepository;

    @Mock
    private GenealogyQueryService genealogyQueryService;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @InjectMocks
    private AuthServiceImpl authService;

    @Test
    @DisplayName("login with valid phone and password should succeed and return AuthTokenResponse with ROLE_CLAN_HEAD")
    void login_WithPhoneNumber_Success() {
        LoginRequest request = new LoginRequest("0912345678", "Secret@123");

        SystemRole role = new SystemRole("ROLE_CLAN_HEAD", "Trưởng Tộc", "Quyền cao nhất dòng họ");
        UserAccount account = UserAccount.builder()
                .accountId("ACC_001")
                .phoneNumber("0912345678")
                .email("test@example.com")
                .passwordHash("$2a$10$hashedPass")
                .accountStatus(AccountStatus.Active)
                .memberId("MEM_001")
                .roles(Set.of(role))
                .build();

        when(userAccountRepository.findByPhoneNumber("0912345678")).thenReturn(Optional.of(account));
        when(passwordEncoder.matches("Secret@123", "$2a$10$hashedPass")).thenReturn(true);

        MemberSummaryDto member = new MemberSummaryDto("MEM_001", "Trần Hưng Đạo", "CLAN_TRAN", "BR_001");
        when(genealogyQueryService.findMemberSummary("MEM_001")).thenReturn(Optional.of(member));

        when(jwtTokenProvider.generateAccessToken(eq("ACC_001"), eq("CLAN_TRAN"), eq("MEM_001"), eq("Trần Hưng Đạo"), anySet()))
                .thenReturn("access.jwt.token");
        when(jwtTokenProvider.generateRefreshToken("ACC_001"))
                .thenReturn("refresh.jwt.token");
        when(jwtTokenProvider.getAccessTokenExpirationSeconds()).thenReturn(3600L);

        AuthTokenResponse response = authService.login(request);

        assertThat(response).isNotNull();
        assertThat(response.accessToken()).isEqualTo("access.jwt.token");
        assertThat(response.refreshToken()).isEqualTo("refresh.jwt.token");
        assertThat(response.accountId()).isEqualTo("ACC_001");
        assertThat(response.clanId()).isEqualTo("CLAN_TRAN");
        assertThat(response.roles()).containsExactly("ROLE_CLAN_HEAD");
        assertThat(response.fullName()).isEqualTo("Trần Hưng Đạo");
    }

    @Test
    @DisplayName("login with wrong password should throw BadRequestException")
    void login_WrongPassword_ThrowsException() {
        LoginRequest request = new LoginRequest("0912345678", "WrongPass");

        UserAccount account = UserAccount.builder()
                .accountId("ACC_001")
                .phoneNumber("0912345678")
                .passwordHash("$2a$10$hashedPass")
                .accountStatus(AccountStatus.Active)
                .build();

        when(userAccountRepository.findByPhoneNumber("0912345678")).thenReturn(Optional.of(account));
        when(passwordEncoder.matches("WrongPass", "$2a$10$hashedPass")).thenReturn(false);

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Số điện thoại/Email hoặc mật khẩu không chính xác");
    }

    @Test
    @DisplayName("login with non-existent user should throw BadRequestException")
    void login_UserNotFound_ThrowsException() {
        LoginRequest request = new LoginRequest("0999999999", "Pass123");

        when(userAccountRepository.findByPhoneNumber("0999999999")).thenReturn(Optional.empty());
        when(userAccountRepository.findByEmail("0999999999")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Số điện thoại/Email hoặc mật khẩu không chính xác");
    }

    @Test
    @DisplayName("login with inactive account should throw BadRequestException")
    void login_InactiveAccount_ThrowsException() {
        LoginRequest request = new LoginRequest("0912345678", "Secret@123");

        UserAccount account = UserAccount.builder()
                .accountId("ACC_001")
                .phoneNumber("0912345678")
                .passwordHash("$2a$10$hashedPass")
                .accountStatus(AccountStatus.Suspended)
                .build();

        when(userAccountRepository.findByPhoneNumber("0912345678")).thenReturn(Optional.of(account));
        when(passwordEncoder.matches("Secret@123", "$2a$10$hashedPass")).thenReturn(true);

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Tài khoản đang ở trạng thái: Suspended");
    }

    @Test
    @DisplayName("refreshToken with valid token should renew access token")
    void refreshToken_Success() {
        RefreshTokenRequest request = new RefreshTokenRequest("valid.refresh.token");

        when(jwtTokenProvider.validateToken("valid.refresh.token")).thenReturn(true);
        when(jwtTokenProvider.getAccountId("valid.refresh.token")).thenReturn("ACC_001");

        UserAccount account = UserAccount.builder()
                .accountId("ACC_001")
                .phoneNumber("0912345678")
                .accountStatus(AccountStatus.Active)
                .roles(Set.of(new SystemRole("ROLE_CLAN_HEAD", "Trưởng Tộc", "Quyền cao nhất")))
                .build();
        when(userAccountRepository.findById("ACC_001")).thenReturn(Optional.of(account));

        when(jwtTokenProvider.generateAccessToken(eq("ACC_001"), any(), any(), any(), anySet()))
                .thenReturn("new.access.token");
        when(jwtTokenProvider.getAccessTokenExpirationSeconds()).thenReturn(3600L);

        AuthTokenResponse response = authService.refreshToken(request);

        assertThat(response.accessToken()).isEqualTo("new.access.token");
        assertThat(response.refreshToken()).isEqualTo("valid.refresh.token");
    }

    @Test
    @DisplayName("refreshToken with invalid token should throw BadRequestException")
    void refreshToken_InvalidToken_ThrowsException() {
        RefreshTokenRequest request = new RefreshTokenRequest("invalid.token");

        when(jwtTokenProvider.validateToken("invalid.token")).thenReturn(false);

        assertThatThrownBy(() -> authService.refreshToken(request))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Refresh token không hợp lệ hoặc đã hết hạn");
    }
}
