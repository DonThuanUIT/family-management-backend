package com.familyfund.event.internal.service;

import com.familyfund.core.exception.ResourceNotFoundException;
import com.familyfund.core.security.CurrentUserProvider;
import com.familyfund.core.tenant.TenantContext;
import com.familyfund.event.api.dto.EventStatus;
import com.familyfund.event.api.dto.RegistrationStatus;
import com.familyfund.event.internal.dto.response.EventLogisticsSummaryResponse;
import com.familyfund.event.internal.entity.Event;
import com.familyfund.event.internal.entity.FamilyRegistration;
import com.familyfund.event.internal.mapper.RegistrationMapper;
import com.familyfund.event.internal.repository.EventPersonnelRepository;
import com.familyfund.event.internal.repository.EventRepository;
import com.familyfund.event.internal.repository.FamilyRegistrationRepository;
import com.familyfund.event.internal.repository.RegistrationMemberDetailRepository;
import com.familyfund.event.internal.util.RegistrationIdGenerator;
import com.familyfund.genealogy.api.GenealogyQueryService;
import com.familyfund.genealogy.api.dto.BranchSummaryDto;
import com.familyfund.genealogy.api.dto.MemberSummaryDto;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("US-42 Event Logistics Summary & Export Tests")
class RegistrationServiceUs42Test {

    private static final String CLAN_ID = "CLAN_01";
    private static final String EVENT_ID = "EVT_42";

    @Mock
    private EventRepository eventRepository;
    @Mock
    private FamilyRegistrationRepository familyRegistrationRepository;
    @Mock
    private RegistrationMemberDetailRepository registrationMemberDetailRepository;
    @Mock
    private EventPersonnelRepository eventPersonnelRepository;
    @Mock
    private RegistrationMapper registrationMapper;
    @Mock
    private GenealogyQueryService genealogyQueryService;
    @Mock
    private CurrentUserProvider currentUserProvider;
    @Mock
    private RegistrationIdGenerator registrationIdGenerator;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private RegistrationServiceImpl registrationService;

    private final EventLogisticsExportService exportService = new EventLogisticsExportService();

    private Event event;
    private FamilyRegistration regBranch1;
    private FamilyRegistration regBranch2;
    private FamilyRegistration regCancelled;

    @BeforeEach
    void setUp() {
        TenantContext.setClanId(CLAN_ID);

        event = Event.builder()
                .eventId(EVENT_ID)
                .clanId(CLAN_ID)
                .eventName("Giỗ Tổ Dòng Họ 2026")
                .status(EventStatus.RegistrationOpen)
                .build();

        regBranch1 = FamilyRegistration.builder()
                .registrationId("REG_01")
                .eventId(EVENT_ID)
                .representativeMemberId("MEM_01")
                .totalAttendees(4)
                .savoryMeals(3)
                .vegetarianMeals(1)
                .shuttleSeats(2)
                .dietaryRequirements("1 suất ăn chay trường")
                .transportationNotes("Xe đưa đón 2 chỗ từ Hà Nội")
                .status(RegistrationStatus.Confirmed)
                .registrationDate(Instant.parse("2026-10-01T08:00:00Z"))
                .build();

        regBranch2 = FamilyRegistration.builder()
                .registrationId("REG_02")
                .eventId(EVENT_ID)
                .representativeMemberId("MEM_02")
                .totalAttendees(3)
                .savoryMeals(3)
                .vegetarianMeals(0)
                .shuttleSeats(0)
                .transportationNotes("Đăng ký xe chung 3 chỗ")
                .status(RegistrationStatus.Confirmed)
                .registrationDate(Instant.parse("2026-10-02T09:00:00Z"))
                .build();

        regCancelled = FamilyRegistration.builder()
                .registrationId("REG_03")
                .eventId(EVENT_ID)
                .representativeMemberId("MEM_03")
                .totalAttendees(5)
                .savoryMeals(5)
                .vegetarianMeals(0)
                .shuttleSeats(5)
                .status(RegistrationStatus.Cancelled)
                .registrationDate(Instant.parse("2026-10-03T10:00:00Z"))
                .build();
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("getEventLogisticsSummary aggregates all non-cancelled households including shuttle seats")
    void getEventLogisticsSummary_allBranches() {
        when(eventRepository.findByEventIdAndClanId(EVENT_ID, CLAN_ID)).thenReturn(Optional.of(event));
        when(genealogyQueryService.findAllBranchesByClanId(CLAN_ID)).thenReturn(List.of(
                new BranchSummaryDto("BR_01", CLAN_ID, "Chi Nhất", 1, "MEM_01", "Nguyễn Văn A"),
                new BranchSummaryDto("BR_02", CLAN_ID, "Chi Nhì", 1, "MEM_02", "Nguyễn Văn B")
        ));
        when(familyRegistrationRepository.findAllByEvent_EventIdAndEvent_ClanIdOrderByRegistrationDateDesc(EVENT_ID, CLAN_ID))
                .thenReturn(List.of(regBranch1, regBranch2, regCancelled));
        when(genealogyQueryService.findMemberSummary("MEM_01"))
                .thenReturn(Optional.of(new MemberSummaryDto("MEM_01", "Nguyễn Văn A", CLAN_ID, "BR_01")));
        when(genealogyQueryService.findMemberSummary("MEM_02"))
                .thenReturn(Optional.of(new MemberSummaryDto("MEM_02", "Nguyễn Văn B", CLAN_ID, "BR_02")));

        EventLogisticsSummaryResponse result = registrationService.getEventLogisticsSummary(EVENT_ID, null);

        assertThat(result.eventId()).isEqualTo(EVENT_ID);
        assertThat(result.eventName()).isEqualTo("Giỗ Tổ Dòng Họ 2026");
        assertThat(result.branchId()).isNull();
        assertThat(result.totalRegistrations()).isEqualTo(2);
        assertThat(result.totalAttendees()).isEqualTo(7);
        assertThat(result.totalSavoryMeals()).isEqualTo(6);
        assertThat(result.totalVegetarianMeals()).isEqualTo(1);
        assertThat(result.totalShuttleSeats()).isEqualTo(5); // 2 explicit + 3 parsed from notes
        assertThat(result.availableBranches()).hasSize(2);
        assertThat(result.registrations()).hasSize(2);
    }

    @Test
    @DisplayName("getEventLogisticsSummary filters accurately by branchId")
    void getEventLogisticsSummary_filterByBranch() {
        when(eventRepository.findByEventIdAndClanId(EVENT_ID, CLAN_ID)).thenReturn(Optional.of(event));
        when(genealogyQueryService.findAllBranchesByClanId(CLAN_ID)).thenReturn(List.of(
                new BranchSummaryDto("BR_01", CLAN_ID, "Chi Nhất", 1, "MEM_01", "Nguyễn Văn A"),
                new BranchSummaryDto("BR_02", CLAN_ID, "Chi Nhì", 1, "MEM_02", "Nguyễn Văn B")
        ));
        when(familyRegistrationRepository.findAllByEvent_EventIdAndEvent_ClanIdOrderByRegistrationDateDesc(EVENT_ID, CLAN_ID))
                .thenReturn(List.of(regBranch1, regBranch2, regCancelled));
        when(genealogyQueryService.findMemberSummary("MEM_01"))
                .thenReturn(Optional.of(new MemberSummaryDto("MEM_01", "Nguyễn Văn A", CLAN_ID, "BR_01")));
        when(genealogyQueryService.findMemberSummary("MEM_02"))
                .thenReturn(Optional.of(new MemberSummaryDto("MEM_02", "Nguyễn Văn B", CLAN_ID, "BR_02")));

        EventLogisticsSummaryResponse result = registrationService.getEventLogisticsSummary(EVENT_ID, "BR_01");

        assertThat(result.branchId()).isEqualTo("BR_01");
        assertThat(result.branchName()).isEqualTo("Chi Nhất");
        assertThat(result.totalRegistrations()).isEqualTo(1);
        assertThat(result.totalAttendees()).isEqualTo(4);
        assertThat(result.totalSavoryMeals()).isEqualTo(3);
        assertThat(result.totalVegetarianMeals()).isEqualTo(1);
        assertThat(result.totalShuttleSeats()).isEqualTo(2);
        assertThat(result.registrations()).hasSize(1);
        assertThat(result.registrations().getFirst().representativeName()).isEqualTo("Nguyễn Văn A");
    }

    @Test
    @DisplayName("getEventLogisticsSummary throws ResourceNotFoundException when branchId does not exist")
    void getEventLogisticsSummary_unknownBranch_throwsNotFound() {
        when(eventRepository.findByEventIdAndClanId(EVENT_ID, CLAN_ID)).thenReturn(Optional.of(event));
        when(genealogyQueryService.findAllBranchesByClanId(CLAN_ID)).thenReturn(List.of());
        when(genealogyQueryService.findBranchByIdAndClanId("BR_UNKNOWN", CLAN_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> registrationService.getEventLogisticsSummary(EVENT_ID, "BR_UNKNOWN"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("exportLogisticsReport generates valid XLSX and UTF-8 BOM CSV files")
    void exportLogisticsReport_xlsxAndCsv() throws Exception {
        EventLogisticsSummaryResponse summary = new EventLogisticsSummaryResponse(
                EVENT_ID,
                "Giỗ Tổ Dòng Họ 2026",
                "BR_01",
                "Chi Nhất",
                1,
                4,
                3,
                1,
                2,
                List.of(new EventLogisticsSummaryResponse.BranchOption("BR_01", "Chi Nhất")),
                List.of(new EventLogisticsSummaryResponse.HouseholdLogisticsItem(
                        "REG_01",
                        "MEM_01",
                        "Nguyễn Văn A",
                        "BR_01",
                        "Chi Nhất",
                        4,
                        3,
                        1,
                        2,
                        "1 chay",
                        "Xe 2 chỗ",
                        RegistrationStatus.Confirmed,
                        Instant.parse("2026-10-01T08:00:00Z")
                ))
        );

        byte[] xlsxBytes = exportService.exportLogisticsReport(summary, "xlsx");
        try (Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(xlsxBytes))) {
            assertThat(wb.getSheetName(0)).isEqualTo("DiemDanh_ChiaCo");
            assertThat(wb.getSheetAt(0).getRow(0).getCell(0).getStringCellValue())
                    .contains("Giỗ Tổ Dòng Họ 2026");
            assertThat(wb.getSheetAt(0).getRow(7).getCell(2).getStringCellValue())
                    .isEqualTo("Nguyễn Văn A");
        }

        byte[] csvBytes = exportService.exportLogisticsReport(summary, "csv");
        String csv = new String(csvBytes, StandardCharsets.UTF_8);
        assertThat(csv).contains("Nguyễn Văn A");
        assertThat(csv).contains("Chi Nhất");
    }
}
