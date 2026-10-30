package com.familyfund.identity.internal.service;

import com.familyfund.core.exception.BadRequestException;
import com.familyfund.genealogy.api.GenealogyBootstrapService;
import com.familyfund.genealogy.api.dto.ClanBootstrapCommand;
import com.familyfund.genealogy.api.dto.ClanBootstrapResult;
import com.familyfund.identity.internal.dto.request.RegisterCreatorRequest;
import com.familyfund.identity.internal.dto.response.CreatorRegistrationResponse;
import com.familyfund.identity.internal.entity.AccountStatus;
import com.familyfund.identity.internal.entity.SystemRole;
import com.familyfund.identity.internal.entity.UserAccount;
import com.familyfund.identity.internal.repository.SystemRoleRepository;
import com.familyfund.identity.internal.repository.UserAccountRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
    @DisplayName("Normal: registerCreator with full valid payload should succeed and assign ROLE_CLAN_HEAD")
    void registerCreator_FullPayload_Success() {
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
        assertThat(response.account().accountStatus()).isEqualTo("Active");
        assertThat(response.account().roles()).containsExactly("ROLE_CLAN_HEAD");
        assertThat(response.clan().clanId()).isEqualTo("CLAN_TRAN001");
        assertThat(response.clan().clanName()).isEqualTo("Dòng họ Trần");
        assertThat(response.clan().ancestorName()).isEqualTo("Trần Hưng Đạo");
        assertThat(response.clan().originLocation()).isEqualTo("Nam Định");
        assertThat(response.clan().establishedYear()).isEqualTo(1250);
        assertThat(response.member().memberId()).isEqualTo("MEM_CREATOR01");
        assertThat(response.member().fullName()).isEqualTo("Trần Quốc Tuấn");
        assertThat(response.member().gender()).isEqualTo("Male");
        assertThat(response.member().generation()).isEqualTo(5);
        assertThat(response.member().branchId()).isEqualTo("BR_ROOT001");

        verify(userAccountRepository).save(any(UserAccount.class));
    }

    @Test
    @DisplayName("Normal: registerCreator with minimal payload (all optional fields null) should succeed")
    void registerCreator_MinimalPayload_Success() {
        RegisterCreatorRequest request = new RegisterCreatorRequest(
                "Dòng họ Lê",
                null,
                null,
                null,
                null,
                "Lê Văn Minh",
                "0987654321",
                "Secret@123",
                "Female",
                null,
                null,
                null
        );

        when(userAccountRepository.existsByPhoneNumber("0987654321")).thenReturn(false);

        ClanBootstrapResult mockBootstrapResult = new ClanBootstrapResult(
                "CLAN_LE001",
                "Dòng họ Lê",
                null,
                null,
                null,
                null,
                "BR_ROOT002",
                "Nhánh Gốc",
                "MEM_CREATOR02",
                "Lê Văn Minh",
                "Female",
                1,
                null
        );
        when(genealogyBootstrapService.bootstrapClanAndCreator(any(ClanBootstrapCommand.class)))
                .thenReturn(mockBootstrapResult);

        SystemRole clanHeadRole = new SystemRole("ROLE_CLAN_HEAD", "Trưởng Tộc", "Quyền cao nhất dòng họ");
        when(systemRoleRepository.findById("ROLE_CLAN_HEAD")).thenReturn(Optional.of(clanHeadRole));
        when(passwordEncoder.encode("Secret@123")).thenReturn("$2a$10$hashedPassword");
        when(userAccountRepository.save(any(UserAccount.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CreatorRegistrationResponse response = registrationService.registerCreator(request);

        assertThat(response).isNotNull();
        assertThat(response.account().email()).isNull();
        assertThat(response.clan().ancestorName()).isNull();
        assertThat(response.clan().originLocation()).isNull();
        assertThat(response.clan().establishedYear()).isNull();
        assertThat(response.clan().description()).isNull();
        assertThat(response.member().dateOfBirth()).isNull();
        assertThat(response.member().generation()).isEqualTo(1);

        verify(userAccountRepository, never()).existsByEmail(anyString());
    }

    @Test
    @DisplayName("Edge case: Auto-create ROLE_CLAN_HEAD if not yet existing in system_roles table")
    void registerCreator_RoleNotFound_AutoCreatesAndSavesRole() {
        RegisterCreatorRequest request = new RegisterCreatorRequest(
                "Dòng họ Vũ",
                null,
                null,
                null,
                null,
                "Vũ Văn Bình",
                "0911223344",
                "Secret@123",
                "Male",
                null,
                null,
                null
        );

        when(userAccountRepository.existsByPhoneNumber("0911223344")).thenReturn(false);

        ClanBootstrapResult mockBootstrapResult = new ClanBootstrapResult(
                "CLAN_VU001",
                "Dòng họ Vũ",
                null,
                null,
                null,
                null,
                "BR_ROOT003",
                "Nhánh Gốc",
                "MEM_CREATOR03",
                "Vũ Văn Bình",
                "Male",
                1,
                null
        );
        when(genealogyBootstrapService.bootstrapClanAndCreator(any(ClanBootstrapCommand.class)))
                .thenReturn(mockBootstrapResult);

        // Simulate role not yet populated in database
        when(systemRoleRepository.findById("ROLE_CLAN_HEAD")).thenReturn(Optional.empty());
        when(systemRoleRepository.save(any(SystemRole.class))).thenAnswer(inv -> inv.getArgument(0));
        when(passwordEncoder.encode("Secret@123")).thenReturn("$2a$10$hashedPassword");
        when(userAccountRepository.save(any(UserAccount.class))).thenAnswer(inv -> inv.getArgument(0));

        CreatorRegistrationResponse response = registrationService.registerCreator(request);

        assertThat(response).isNotNull();
        assertThat(response.account().roles()).contains("ROLE_CLAN_HEAD");

        // Verify fallback role creation was triggered
        ArgumentCaptor<SystemRole> roleCaptor = ArgumentCaptor.forClass(SystemRole.class);
        verify(systemRoleRepository).save(roleCaptor.capture());
        SystemRole savedRole = roleCaptor.getValue();
        assertThat(savedRole.getRoleId()).isEqualTo("ROLE_CLAN_HEAD");
        assertThat(savedRole.getRoleName()).isEqualTo("Trưởng Tộc");
        assertThat(savedRole.getDescription()).isEqualTo("Quyền cao nhất dòng họ");
    }

    @Test
    @DisplayName("Edge case: Phone, email and password containing leading/trailing whitespace should be trimmed")
    void registerCreator_TrimsWhitespaceFields() {
        RegisterCreatorRequest request = new RegisterCreatorRequest(
                "  Dòng họ Nguyễn  ",
                null,
                null,
                null,
                null,
                "  Nguyễn Văn A  ",
                "   0912345678   ",
                "   Secret@123   ",
                "Male",
                "   nguyenvana@example.com   ",
                null,
                null
        );

        when(userAccountRepository.existsByPhoneNumber("0912345678")).thenReturn(false);
        when(userAccountRepository.existsByEmail("nguyenvana@example.com")).thenReturn(false);

        ClanBootstrapResult mockBootstrapResult = new ClanBootstrapResult(
                "CLAN_NGUYEN001",
                "Dòng họ Nguyễn",
                null,
                null,
                null,
                null,
                "BR_ROOT004",
                "Nhánh Gốc",
                "MEM_CREATOR04",
                "Nguyễn Văn A",
                "Male",
                1,
                null
        );
        when(genealogyBootstrapService.bootstrapClanAndCreator(any(ClanBootstrapCommand.class)))
                .thenReturn(mockBootstrapResult);

        SystemRole clanHeadRole = new SystemRole("ROLE_CLAN_HEAD", "Trưởng Tộc", "Quyền cao nhất dòng họ");
        when(systemRoleRepository.findById("ROLE_CLAN_HEAD")).thenReturn(Optional.of(clanHeadRole));
        when(passwordEncoder.encode("Secret@123")).thenReturn("$2a$10$hashedPassword");
        when(userAccountRepository.save(any(UserAccount.class))).thenAnswer(inv -> inv.getArgument(0));

        CreatorRegistrationResponse response = registrationService.registerCreator(request);

        assertThat(response).isNotNull();
        // Verify trimmed values saved to UserAccount
        ArgumentCaptor<UserAccount> accountCaptor = ArgumentCaptor.forClass(UserAccount.class);
        verify(userAccountRepository).save(accountCaptor.capture());
        UserAccount savedAccount = accountCaptor.getValue();
        assertThat(savedAccount.getPhoneNumber()).isEqualTo("0912345678");
        assertThat(savedAccount.getEmail()).isEqualTo("nguyenvana@example.com");

        // Verify trimmed password was encoded
        verify(passwordEncoder).encode("Secret@123");
    }

    @Test
    @DisplayName("Edge case: Blank string email ('   ') should be sanitized to null and bypass email duplicate check")
    void registerCreator_BlankEmail_TreatedAsNullAndSkipsDuplicateCheck() {
        RegisterCreatorRequest request = new RegisterCreatorRequest(
                "Dòng họ Phạm",
                null,
                null,
                null,
                null,
                "Phạm Văn C",
                "0933333333",
                "Secret@123",
                "Male",
                "     ",
                null,
                null
        );

        when(userAccountRepository.existsByPhoneNumber("0933333333")).thenReturn(false);

        ClanBootstrapResult mockBootstrapResult = new ClanBootstrapResult(
                "CLAN_PHAM001",
                "Dòng họ Phạm",
                null,
                null,
                null,
                null,
                "BR_ROOT005",
                "Nhánh Gốc",
                "MEM_CREATOR05",
                "Phạm Văn C",
                "Male",
                1,
                null
        );
        when(genealogyBootstrapService.bootstrapClanAndCreator(any(ClanBootstrapCommand.class)))
                .thenReturn(mockBootstrapResult);

        SystemRole clanHeadRole = new SystemRole("ROLE_CLAN_HEAD", "Trưởng Tộc", "Quyền cao nhất dòng họ");
        when(systemRoleRepository.findById("ROLE_CLAN_HEAD")).thenReturn(Optional.of(clanHeadRole));
        when(passwordEncoder.encode("Secret@123")).thenReturn("$2a$10$hashedPassword");
        when(userAccountRepository.save(any(UserAccount.class))).thenAnswer(inv -> inv.getArgument(0));

        CreatorRegistrationResponse response = registrationService.registerCreator(request);

        assertThat(response).isNotNull();
        assertThat(response.account().email()).isNull();

        // Verify existsByEmail was never called because blank string turned into null
        verify(userAccountRepository, never()).existsByEmail(anyString());
    }

    @Test
    @DisplayName("Abnormal: Duplicate phone number (exact match) should throw BadRequestException")
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
    @DisplayName("Abnormal: Duplicate phone number with whitespace padding should still be detected and throw BadRequestException")
    void registerCreator_DuplicatePhoneWithWhitespace_ThrowsException() {
        RegisterCreatorRequest request = new RegisterCreatorRequest(
                "Dòng họ Trần",
                null,
                null,
                null,
                null,
                "Trần Văn A",
                "   0912345678   ",
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
    @DisplayName("Abnormal: Duplicate email should throw BadRequestException")
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

    @Test
    @DisplayName("Abnormal: Duplicate email with whitespace padding should be trimmed and throw BadRequestException")
    void registerCreator_DuplicateEmailWithWhitespace_ThrowsException() {
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
                "   duplicate@example.com   ",
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

    @Test
    @DisplayName("Abnormal: If genealogy bootstrap fails with an exception, user account must NOT be created (transaction rollback guarantee)")
    void registerCreator_GenealogyServiceThrowsException_PropagatesAndDoesNotSaveAccount() {
        RegisterCreatorRequest request = new RegisterCreatorRequest(
                "Dòng họ Hoàng",
                null,
                null,
                null,
                null,
                "Hoàng Văn Nam",
                "0977665544",
                "Secret@123",
                "Male",
                "hoang@example.com",
                null,
                null
        );

        when(userAccountRepository.existsByPhoneNumber("0977665544")).thenReturn(false);
        when(userAccountRepository.existsByEmail("hoang@example.com")).thenReturn(false);

        when(genealogyBootstrapService.bootstrapClanAndCreator(any(ClanBootstrapCommand.class)))
                .thenThrow(new IllegalStateException("Failed to bootstrap clan entities in database"));

        assertThatThrownBy(() -> registrationService.registerCreator(request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Failed to bootstrap clan entities in database");

        verify(userAccountRepository, never()).save(any());
    }
}
