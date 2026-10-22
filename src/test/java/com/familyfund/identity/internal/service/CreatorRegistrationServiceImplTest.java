package com.familyfund.identity.internal.service;

import com.familyfund.core.exception.BadRequestException;
import com.familyfund.genealogy.api.GenealogyBootstrapService;
import com.familyfund.genealogy.api.dto.ClanBootstrapCommand;
import com.familyfund.genealogy.api.dto.ClanBootstrapResult;
import com.familyfund.identity.internal.dto.request.RegisterCreatorRequest;
import com.familyfund.identity.internal.dto.response.CreatorRegistrationResponse;
import com.familyfund.identity.internal.entity.SystemRole;
import com.familyfund.identity.internal.entity.UserAccount;
import com.familyfund.identity.internal.repository.SystemRoleRepository;
import com.familyfund.identity.internal.repository.UserAccountRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CreatorRegistrationServiceImplTest {

    @Mock
    private UserAccountRepository userAccountRepository;

    @Mock
    private SystemRoleRepository systemRoleRepository;

    @Mock
    private GenealogyBootstrapService genealogyBootstrapService;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private CreatorRegistrationServiceImpl registrationService;

    @Test
    @DisplayName("registerCreator should succeed and return creator response with ROLE_CLAN_HEAD")
    void registerCreator_Success() {
        RegisterCreatorRequest request = new RegisterCreatorRequest(
                "Dòng họ Trần",
                "Trần Hưng Đạo",
                "Nam Định",
                1250,
                "Hào khí Đông A",
                "Trần Quốc Tuấn",
                "0912345678",
                "Secret@123",
                "Male",
                "tranquoctuan@example.com",
                LocalDate.of(1980, 1, 1),
                5
        );

        when(userAccountRepository.existsByPhoneNumber("0912345678")).thenReturn(false);
        when(userAccountRepository.existsByEmail("tranquoctuan@example.com")).thenReturn(false);

        ClanBootstrapResult mockBootstrapResult = new ClanBootstrapResult(
                "CLAN_TRAN001",
                "Dòng họ Trần",
                "Trần Hưng Đạo",
                "Nam Định",
                1250,
                "Hào khí Đông A",
                "BR_ROOT001",
                "Nhánh Gốc",
                "MEM_CREATOR01",
                "Trần Quốc Tuấn",
                "Male",
                5,
                LocalDate.of(1980, 1, 1)
        );
        when(genealogyBootstrapService.bootstrapClanAndCreator(any(ClanBootstrapCommand.class)))
                .thenReturn(mockBootstrapResult);

        SystemRole clanHeadRole = new SystemRole("ROLE_CLAN_HEAD", "Trưởng Tộc", "Quyền cao nhất dòng họ");
        when(systemRoleRepository.findById("ROLE_CLAN_HEAD")).thenReturn(Optional.of(clanHeadRole));

        when(passwordEncoder.encode("Secret@123")).thenReturn("$2a$10$hashedPassword");
        when(userAccountRepository.save(any(UserAccount.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CreatorRegistrationResponse response = registrationService.registerCreator(request);

        assertThat(response).isNotNull();
        assertThat(response.account().phoneNumber()).isEqualTo("0912345678");
        assertThat(response.account().email()).isEqualTo("tranquoctuan@example.com");
        assertThat(response.account().roles()).contains("ROLE_CLAN_HEAD");
        assertThat(response.clan().clanId()).isEqualTo("CLAN_TRAN001");
        assertThat(response.member().memberId()).isEqualTo("MEM_CREATOR01");
        assertThat(response.member().fullName()).isEqualTo("Trần Quốc Tuấn");

        verify(userAccountRepository).save(any(UserAccount.class));
    }

    @Test
    @DisplayName("registerCreator should throw BadRequestException if phone number already exists")
    void registerCreator_DuplicatePhone_ThrowsException() {
        RegisterCreatorRequest request = new RegisterCreatorRequest(
                "Dòng họ Trần",
                null,
                null,
                null,
                null,
                "Trần Văn A",
                "0912345678",
                "Secret@123",
                "Male",
                null,
                null,
                null
        );

        when(userAccountRepository.existsByPhoneNumber("0912345678")).thenReturn(true);

        assertThatThrownBy(() -> registrationService.registerCreator(request))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Số điện thoại '0912345678' đã được đăng ký tài khoản");

        verify(genealogyBootstrapService, never()).bootstrapClanAndCreator(any());
        verify(userAccountRepository, never()).save(any());
    }

    @Test
    @DisplayName("registerCreator should throw BadRequestException if email already exists")
    void registerCreator_DuplicateEmail_ThrowsException() {
        RegisterCreatorRequest request = new RegisterCreatorRequest(
                "Dòng họ Trần",
                null,
                null,
                null,
                null,
                "Trần Văn A",
                "0988888888",
                "Secret@123",
                "Male",
                "duplicate@example.com",
                null,
                null
        );

        when(userAccountRepository.existsByPhoneNumber("0988888888")).thenReturn(false);
        when(userAccountRepository.existsByEmail("duplicate@example.com")).thenReturn(true);

        assertThatThrownBy(() -> registrationService.registerCreator(request))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Địa chỉ email 'duplicate@example.com' đã được đăng ký tài khoản");

        verify(genealogyBootstrapService, never()).bootstrapClanAndCreator(any());
        verify(userAccountRepository, never()).save(any());
    }
}
