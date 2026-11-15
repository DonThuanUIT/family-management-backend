package com.familyfund.identity.internal.service;

import com.familyfund.core.exception.BadRequestException;
import com.familyfund.core.exception.DuplicateResourceException;
import com.familyfund.core.exception.ResourceNotFoundException;
import com.familyfund.core.security.CurrentUserProvider;
import com.familyfund.genealogy.api.GenealogyQueryService;
import com.familyfund.genealogy.api.dto.MemberProfileDetailsDto;
import com.familyfund.identity.internal.dto.request.ChangePasswordRequest;
import com.familyfund.identity.internal.dto.request.UpdateContactProfileRequest;
import com.familyfund.identity.internal.dto.response.UserProfileResponse;
import com.familyfund.identity.internal.entity.AccountStatus;
import com.familyfund.identity.internal.entity.SystemRole;
import com.familyfund.identity.internal.entity.UserAccount;
import com.familyfund.identity.internal.repository.UserAccountRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDate;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProfileServiceImplTest {

    @Mock
    private UserAccountRepository userAccountRepository;

    @Mock
    private CurrentUserProvider currentUserProvider;

    @Mock
    private GenealogyQueryService genealogyQueryService;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private ProfileServiceImpl profileService;

    private UserAccount testAccount;
    private MemberProfileDetailsDto testDemographic;

    @BeforeEach
    void setUp() {
        testAccount = UserAccount.builder()
                .accountId("ACC_001")
                .memberId("MEM_001")
                .phoneNumber("0912345678")
                .email("tuan.le@familyfund.vn")
                .address("Hà Đông, Hà Nội")
                .passwordHash("$2a$10$oldEncodedPasswordHash")
                .accountStatus(AccountStatus.Active)
                .roles(Set.of(new SystemRole("ROLE_MEMBER", "Thành viên", null)))
                .build();

        testDemographic = new MemberProfileDetailsDto(
                "MEM_001",
                "Lê Hữu Tuấn",
                LocalDate.of(1990, 8, 15),
                "Male",
                5,
                "CLAN_DEFAULT",
                "BR_CHI_2",
                "Chi 2 - Nhánh Giáp"
        );
    }

    @Test
    @DisplayName("getMyProfile trả về đầy đủ thông tin tài khoản và thông tin phả hệ")
    void getMyProfile_Success() {
        when(currentUserProvider.currentAccountId()).thenReturn(Optional.of("ACC_001"));
        when(userAccountRepository.findById("ACC_001")).thenReturn(Optional.of(testAccount));
        when(genealogyQueryService.findMemberProfileDetails("MEM_001")).thenReturn(Optional.of(testDemographic));

        UserProfileResponse response = profileService.getMyProfile();

        assertThat(response).isNotNull();
        assertThat(response.accountId()).isEqualTo("ACC_001");
        assertThat(response.phoneNumber()).isEqualTo("0912345678");
        assertThat(response.email()).isEqualTo("tuan.le@familyfund.vn");
        assertThat(response.address()).isEqualTo("Hà Đông, Hà Nội");
        assertThat(response.fullName()).isEqualTo("Lê Hữu Tuấn");
        assertThat(response.dateOfBirth()).isEqualTo(LocalDate.of(1990, 8, 15));
        assertThat(response.generation()).isEqualTo(5);
        assertThat(response.branchName()).isEqualTo("Chi 2 - Nhánh Giáp");
        assertThat(response.roles()).contains("ROLE_MEMBER");
    }

    @Test
    @DisplayName("updateContactProfile thành công khi dữ liệu hợp lệ và không trùng lặp")
    void updateContactProfile_Success() {
        UpdateContactProfileRequest request = new UpdateContactProfileRequest(
                "0987654321",
                "new.email@familyfund.vn",
                "Quận Ba Đình, Hà Nội"
        );

        when(currentUserProvider.currentAccountId()).thenReturn(Optional.of("ACC_001"));
        when(userAccountRepository.findById("ACC_001")).thenReturn(Optional.of(testAccount));
        when(userAccountRepository.existsByPhoneNumberAndAccountIdNot("0987654321", "ACC_001")).thenReturn(false);
        when(userAccountRepository.existsByEmailAndAccountIdNot("new.email@familyfund.vn", "ACC_001")).thenReturn(false);
        when(userAccountRepository.save(any(UserAccount.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(genealogyQueryService.findMemberProfileDetails("MEM_001")).thenReturn(Optional.of(testDemographic));

        UserProfileResponse response = profileService.updateContactProfile(request);

        assertThat(response.phoneNumber()).isEqualTo("0987654321");
        assertThat(response.email()).isEqualTo("new.email@familyfund.vn");
        assertThat(response.address()).isEqualTo("Quận Ba Đình, Hà Nội");
        assertThat(response.fullName()).isEqualTo("Lê Hữu Tuấn"); // Vẫn giữ nguyên phả hệ
        verify(userAccountRepository).save(testAccount);
    }

    @Test
    @DisplayName("updateContactProfile ném DuplicateResourceException khi số điện thoại trùng với tài khoản khác")
    void updateContactProfile_DuplicatePhone_ThrowsException() {
        UpdateContactProfileRequest request = new UpdateContactProfileRequest(
                "0987654321",
                "new.email@familyfund.vn",
                "Quận Ba Đình, Hà Nội"
        );

        when(currentUserProvider.currentAccountId()).thenReturn(Optional.of("ACC_001"));
        when(userAccountRepository.findById("ACC_001")).thenReturn(Optional.of(testAccount));
        when(userAccountRepository.existsByPhoneNumberAndAccountIdNot("0987654321", "ACC_001")).thenReturn(true);

        assertThatThrownBy(() -> profileService.updateContactProfile(request))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("Số điện thoại này đã được sử dụng");

        verify(userAccountRepository, never()).save(any());
    }

    @Test
    @DisplayName("updateContactProfile ném DuplicateResourceException khi email trùng với tài khoản khác")
    void updateContactProfile_DuplicateEmail_ThrowsException() {
        UpdateContactProfileRequest request = new UpdateContactProfileRequest(
                "0912345678",
                "duplicate@familyfund.vn",
                "Quận Ba Đình, Hà Nội"
        );

        when(currentUserProvider.currentAccountId()).thenReturn(Optional.of("ACC_001"));
        when(userAccountRepository.findById("ACC_001")).thenReturn(Optional.of(testAccount));
        when(userAccountRepository.existsByPhoneNumberAndAccountIdNot("0912345678", "ACC_001")).thenReturn(false);
        when(userAccountRepository.existsByEmailAndAccountIdNot("duplicate@familyfund.vn", "ACC_001")).thenReturn(true);

        assertThatThrownBy(() -> profileService.updateContactProfile(request))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("Hòm thư điện tử (email) này đã được sử dụng");

        verify(userAccountRepository, never()).save(any());
    }

    @Test
    @DisplayName("changePassword thành công khi mật khẩu cũ đúng và mật khẩu mới hợp lệ")
    void changePassword_Success() {
        ChangePasswordRequest request = new ChangePasswordRequest(
                "OldPass@123",
                "NewPass@456",
                "NewPass@456"
        );

        when(currentUserProvider.currentAccountId()).thenReturn(Optional.of("ACC_001"));
        when(userAccountRepository.findById("ACC_001")).thenReturn(Optional.of(testAccount));
        when(passwordEncoder.matches("OldPass@123", testAccount.getPasswordHash())).thenReturn(true);
        when(passwordEncoder.matches("NewPass@456", testAccount.getPasswordHash())).thenReturn(false);
        when(passwordEncoder.encode("NewPass@456")).thenReturn("$2a$10$newEncodedHash");

        profileService.changePassword(request);

        assertThat(testAccount.getPasswordHash()).isEqualTo("$2a$10$newEncodedHash");
        verify(userAccountRepository).save(testAccount);
    }

    @Test
    @DisplayName("changePassword ném BadRequestException khi xác nhận mật khẩu không khớp")
    void changePassword_MismatchConfirm_ThrowsException() {
        ChangePasswordRequest request = new ChangePasswordRequest(
                "OldPass@123",
                "NewPass@456",
                "WrongConfirm@456"
        );

        when(currentUserProvider.currentAccountId()).thenReturn(Optional.of("ACC_001"));
        when(userAccountRepository.findById("ACC_001")).thenReturn(Optional.of(testAccount));

        assertThatThrownBy(() -> profileService.changePassword(request))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Xác nhận mật khẩu mới không trùng khớp");

        verify(passwordEncoder, never()).encode(anyString());
        verify(userAccountRepository, never()).save(any());
    }

    @Test
    @DisplayName("changePassword ném BadRequestException khi mật khẩu hiện tại sai")
    void changePassword_WrongCurrentPassword_ThrowsException() {
        ChangePasswordRequest request = new ChangePasswordRequest(
                "IncorrectPassword",
                "NewPass@456",
                "NewPass@456"
        );

        when(currentUserProvider.currentAccountId()).thenReturn(Optional.of("ACC_001"));
        when(userAccountRepository.findById("ACC_001")).thenReturn(Optional.of(testAccount));
        when(passwordEncoder.matches("IncorrectPassword", testAccount.getPasswordHash())).thenReturn(false);

        assertThatThrownBy(() -> profileService.changePassword(request))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Mật khẩu hiện tại không chính xác");

        verify(userAccountRepository, never()).save(any());
    }

    @Test
    @DisplayName("changePassword ném BadRequestException khi mật khẩu mới trùng mật khẩu cũ")
    void changePassword_SamePassword_ThrowsException() {
        ChangePasswordRequest request = new ChangePasswordRequest(
                "CurrentPass@123",
                "CurrentPass@123",
                "CurrentPass@123"
        );

        when(currentUserProvider.currentAccountId()).thenReturn(Optional.of("ACC_001"));
        when(userAccountRepository.findById("ACC_001")).thenReturn(Optional.of(testAccount));
        when(passwordEncoder.matches("CurrentPass@123", testAccount.getPasswordHash())).thenReturn(true);

        assertThatThrownBy(() -> profileService.changePassword(request))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Mật khẩu mới không được trùng với mật khẩu hiện tại");

        verify(userAccountRepository, never()).save(any());
    }
}
