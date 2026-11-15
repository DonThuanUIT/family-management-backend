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
import com.familyfund.identity.internal.entity.SystemRole;
import com.familyfund.identity.internal.entity.UserAccount;
import com.familyfund.identity.internal.repository.UserAccountRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Implementation of {@link ProfileService} for US-22.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ProfileServiceImpl implements ProfileService {

    private final UserAccountRepository userAccountRepository;
    private final CurrentUserProvider currentUserProvider;
    private final GenealogyQueryService genealogyQueryService;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional(readOnly = true)
    public UserProfileResponse getMyProfile() {
        UserAccount account = getAuthenticatedUserAccount();
        return buildUserProfileResponse(account);
    }

    @Override
    @Transactional
    public UserProfileResponse updateContactProfile(UpdateContactProfileRequest request) {
        UserAccount account = getAuthenticatedUserAccount();
        String accountId = account.getAccountId();

        String newPhone = request.phoneNumber().trim();
        String newEmail = (request.email() != null && !request.email().isBlank())
                ? request.email().trim()
                : null;
        String newAddress = (request.address() != null && !request.address().isBlank())
                ? request.address().trim()
                : null;

        // 1. Chống trùng số điện thoại với tài khoản khác
        if (userAccountRepository.existsByPhoneNumberAndAccountIdNot(newPhone, accountId)) {
            throw new DuplicateResourceException("Số điện thoại này đã được sử dụng bởi tài khoản khác trong hệ thống");
        }

        // 2. Chống trùng email với tài khoản khác
        if (newEmail != null && userAccountRepository.existsByEmailAndAccountIdNot(newEmail, accountId)) {
            throw new DuplicateResourceException("Hòm thư điện tử (email) này đã được sử dụng bởi tài khoản khác trong hệ thống");
        }

        account.setPhoneNumber(newPhone);
        account.setEmail(newEmail);
        account.setAddress(newAddress);

        UserAccount updated = userAccountRepository.save(account);
        log.info("Cập nhật thông tin liên lạc thành công cho tài khoản accountId={}", accountId);

        return buildUserProfileResponse(updated);
    }

    @Override
    @Transactional
    public void changePassword(ChangePasswordRequest request) {
        UserAccount account = getAuthenticatedUserAccount();

        // 1. Kiểm tra xác nhận mật khẩu
        if (!request.newPassword().equals(request.confirmPassword())) {
            throw new BadRequestException("Xác nhận mật khẩu mới không trùng khớp với mật khẩu mới");
        }

        // 2. Kiểm tra mật khẩu hiện tại
        if (!passwordEncoder.matches(request.currentPassword(), account.getPasswordHash())) {
            throw new BadRequestException("Mật khẩu hiện tại không chính xác");
        }

        // 3. Mật khẩu mới không được trùng mật khẩu cũ
        if (passwordEncoder.matches(request.newPassword(), account.getPasswordHash())) {
            throw new BadRequestException("Mật khẩu mới không được trùng với mật khẩu hiện tại");
        }

        // 4. Mã hóa và lưu mật khẩu mới
        account.setPasswordHash(passwordEncoder.encode(request.newPassword().trim()));
        userAccountRepository.save(account);
        log.info("Đổi mật khẩu thành công cho tài khoản accountId={}", account.getAccountId());
    }

    private UserAccount getAuthenticatedUserAccount() {
        String accountId = currentUserProvider.currentAccountId()
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy thông tin tài khoản đăng nhập hiện tại"));

        return userAccountRepository.findById(accountId)
                .orElseThrow(() -> new ResourceNotFoundException("Tài khoản người dùng không tồn tại trên hệ thống"));
    }

    private UserProfileResponse buildUserProfileResponse(UserAccount account) {
        Set<String> roleNames = account.getRoles() != null
                ? account.getRoles().stream().map(SystemRole::getRoleId).collect(Collectors.toSet())
                : Collections.emptySet();

        Optional<MemberProfileDetailsDto> memberProfile = Optional.empty();
        if (account.getMemberId() != null && !account.getMemberId().isBlank()) {
            memberProfile = genealogyQueryService.findMemberProfileDetails(account.getMemberId());
        }

        return UserProfileResponse.builder()
                .accountId(account.getAccountId())
                .memberId(account.getMemberId())
                .clanId(memberProfile.map(MemberProfileDetailsDto::clanId).orElse(null))
                .accountStatus(account.getAccountStatus() != null ? account.getAccountStatus().name() : null)
                .roles(roleNames)
                .phoneNumber(account.getPhoneNumber())
                .email(account.getEmail())
                .address(account.getAddress())
                .fullName(memberProfile.map(MemberProfileDetailsDto::fullName).orElse(null))
                .dateOfBirth(memberProfile.map(MemberProfileDetailsDto::dateOfBirth).orElse(null))
                .gender(memberProfile.map(MemberProfileDetailsDto::gender).orElse(null))
                .generation(memberProfile.map(MemberProfileDetailsDto::generation).orElse(null))
                .branchId(memberProfile.map(MemberProfileDetailsDto::branchId).orElse(null))
                .branchName(memberProfile.map(MemberProfileDetailsDto::branchName).orElse(null))
                .build();
    }
}
