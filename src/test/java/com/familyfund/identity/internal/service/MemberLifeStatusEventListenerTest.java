package com.familyfund.identity.internal.service;

import com.familyfund.genealogy.api.event.MemberLifeStatusChangedEvent;
import com.familyfund.identity.internal.entity.AccountStatus;
import com.familyfund.identity.internal.entity.UserAccount;
import com.familyfund.identity.internal.repository.UserAccountRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("US-27: MemberLifeStatusEventListener Unit Tests")
class MemberLifeStatusEventListenerTest {

    private static final String MEMBER_ID = "MEM_001";
    private static final String CLAN_ID = "CLAN_01";
    private static final String ACCOUNT_ID = "ACC_001";

    @Mock
    private UserAccountRepository userAccountRepository;

    private MemberLifeStatusEventListener listener;

    private UserAccount testAccount;

    @BeforeEach
    void setUp() {
        listener = new MemberLifeStatusEventListener(userAccountRepository);

        testAccount = UserAccount.builder()
                .accountId(ACCOUNT_ID)
                .memberId(MEMBER_ID)
                .phoneNumber("0912345678")
                .passwordHash("hashed_secret")
                .accountStatus(AccountStatus.Active)
                .build();
    }

    @Test
    @DisplayName("Locks active user account (sets to Deactivated) when member becomes Deceased")
    void onMemberLifeStatusChanged_deceased_locksAccount() {
        when(userAccountRepository.findByMemberId(MEMBER_ID)).thenReturn(Optional.of(testAccount));

        MemberLifeStatusChangedEvent event = new MemberLifeStatusChangedEvent(
                MEMBER_ID,
                CLAN_ID,
                "Deceased",
                LocalDate.of(2024, 3, 24),
                "15/02 Giáp Thìn"
        );

        listener.onMemberLifeStatusChanged(event);

        ArgumentCaptor<UserAccount> captor = ArgumentCaptor.forClass(UserAccount.class);
        verify(userAccountRepository, times(1)).save(captor.capture());

        UserAccount savedAccount = captor.getValue();
        assertEquals(AccountStatus.Deactivated, savedAccount.getAccountStatus());
    }

    @Test
    @DisplayName("Restores deactivated user account (sets to Active) when member status reverts to Living")
    void onMemberLifeStatusChanged_living_restoresAccount() {
        testAccount.setAccountStatus(AccountStatus.Deactivated);
        when(userAccountRepository.findByMemberId(MEMBER_ID)).thenReturn(Optional.of(testAccount));

        MemberLifeStatusChangedEvent event = new MemberLifeStatusChangedEvent(
                MEMBER_ID,
                CLAN_ID,
                "Living",
                null,
                null
        );

        listener.onMemberLifeStatusChanged(event);

        ArgumentCaptor<UserAccount> captor = ArgumentCaptor.forClass(UserAccount.class);
        verify(userAccountRepository, times(1)).save(captor.capture());

        UserAccount savedAccount = captor.getValue();
        assertEquals(AccountStatus.Active, savedAccount.getAccountStatus());
    }

    @Test
    @DisplayName("Does nothing gracefully when member has no user account")
    void onMemberLifeStatusChanged_noAccount_gracefullyIgnored() {
        when(userAccountRepository.findByMemberId(MEMBER_ID)).thenReturn(Optional.empty());

        MemberLifeStatusChangedEvent event = new MemberLifeStatusChangedEvent(
                MEMBER_ID,
                CLAN_ID,
                "Deceased",
                LocalDate.of(2024, 3, 24),
                "15/02 Giáp Thìn"
        );

        listener.onMemberLifeStatusChanged(event);

        verify(userAccountRepository, never()).save(any());
    }

    @Test
    @DisplayName("Does nothing when event or memberId is null or blank")
    void onMemberLifeStatusChanged_nullEvent_ignored() {
        listener.onMemberLifeStatusChanged(null);
        listener.onMemberLifeStatusChanged(new MemberLifeStatusChangedEvent(null, CLAN_ID, "Deceased", null, null));
        listener.onMemberLifeStatusChanged(new MemberLifeStatusChangedEvent("   ", CLAN_ID, "Deceased", null, null));

        verify(userAccountRepository, never()).findByMemberId(any());
    }
}
