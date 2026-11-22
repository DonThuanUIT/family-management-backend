package com.familyfund.identity.internal.service;

import com.familyfund.identity.internal.entity.AccountStatus;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountPasswordVerificationServiceImplTest {

    @Mock
    private UserAccountRepository userAccountRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private AccountPasswordVerificationServiceImpl service;

    @Test
    @DisplayName("Trả về true khi tài khoản Active và mật khẩu khớp BCrypt")
    void verifyActiveAccountPassword_validActiveAccount_returnsTrue() {
        UserAccount account = UserAccount.builder()
                .accountId("ACC_HEAD")
                .accountStatus(AccountStatus.Active)
                .passwordHash("$2a$10$hashedValue")
                .build();

        when(userAccountRepository.findById("ACC_HEAD")).thenReturn(Optional.of(account));
        when(passwordEncoder.matches("ClanHead@123", "$2a$10$hashedValue")).thenReturn(true);

        assertThat(service.verifyActiveAccountPassword("ACC_HEAD", "ClanHead@123")).isTrue();
    }

    @Test
    @DisplayName("Trả về false khi mật khẩu không khớp hoặc tài khoản bị khóa / chưa kích hoạt")
    void verifyActiveAccountPassword_wrongPasswordOrInactive_returnsFalse() {
        UserAccount activeAccount = UserAccount.builder()
                .accountId("ACC_HEAD")
                .accountStatus(AccountStatus.Active)
                .passwordHash("$2a$10$hashedValue")
                .build();

        when(userAccountRepository.findById("ACC_HEAD")).thenReturn(Optional.of(activeAccount));
        when(passwordEncoder.matches("WrongPass", "$2a$10$hashedValue")).thenReturn(false);

        assertThat(service.verifyActiveAccountPassword("ACC_HEAD", "WrongPass")).isFalse();

        UserAccount suspendedAccount = UserAccount.builder()
                .accountId("ACC_SUSPENDED")
                .accountStatus(AccountStatus.Suspended)
                .passwordHash("$2a$10$hashedValue")
                .build();
        when(userAccountRepository.findById("ACC_SUSPENDED")).thenReturn(Optional.of(suspendedAccount));

        assertThat(service.verifyActiveAccountPassword("ACC_SUSPENDED", "ClanHead@123")).isFalse();
    }

    @Test
    @DisplayName("Trả về false ngay lập tức khi accountId hoặc password rỗng / null")
    void verifyActiveAccountPassword_blankInputs_returnsFalse() {
        assertThat(service.verifyActiveAccountPassword(null, "pass")).isFalse();
        assertThat(service.verifyActiveAccountPassword("   ", "pass")).isFalse();
        assertThat(service.verifyActiveAccountPassword("ACC_1", null)).isFalse();
        assertThat(service.verifyActiveAccountPassword("ACC_1", "   ")).isFalse();

        verifyNoInteractions(userAccountRepository, passwordEncoder);
    }
}
