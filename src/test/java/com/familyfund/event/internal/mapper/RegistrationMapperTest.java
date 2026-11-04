package com.familyfund.event.internal.mapper;

import com.familyfund.event.api.dto.RegistrationStatus;
import com.familyfund.event.api.dto.RegistrationSummaryDto;
import com.familyfund.event.internal.dto.response.RegistrationMemberDetailResponse;
import com.familyfund.event.internal.dto.response.RegistrationResponse;
import com.familyfund.event.internal.dto.response.RegistrationSummaryResponse;
import com.familyfund.event.internal.entity.FamilyRegistration;
import com.familyfund.event.internal.entity.RegistrationMemberDetail;
import com.familyfund.event.internal.repository.RegistrationAggregateProjection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("RegistrationMapper MapStruct Test")
class RegistrationMapperTest {

    private RegistrationMapper registrationMapper;

    @BeforeEach
    void setUp() {
        registrationMapper = Mappers.getMapper(RegistrationMapper.class);
    }

    @Test
    @DisplayName("Maps FamilyRegistration entity to RegistrationResponse DTO")
    void testToResponse() {
        Instant now = Instant.now();
        FamilyRegistration registration = FamilyRegistration.builder()
                .registrationId("REG_001")
                .eventId("EVT_001")
                .representativeMemberId("MEM_001")
                .totalAttendees(4)
                .vegetarianMeals(1)
                .savoryMeals(3)
                .dietaryRequirements("1 người ăn chay")
                .transportationNotes("Đi xe chung")
                .registrationDate(now)
                .status(RegistrationStatus.Confirmed)
                .build();

        RegistrationResponse response = registrationMapper.toResponse(registration);

        assertNotNull(response);
        assertEquals("REG_001", response.registrationId());
        assertEquals("EVT_001", response.eventId());
        assertEquals("MEM_001", response.representativeMemberId());
        assertEquals(4, response.totalAttendees());
        assertEquals(1, response.vegetarianMeals());
        assertEquals(3, response.savoryMeals());
        assertEquals("1 người ăn chay", response.dietaryRequirements());
        assertEquals("Đi xe chung", response.transportationNotes());
        assertEquals(now, response.registrationDate());
        assertEquals(RegistrationStatus.Confirmed, response.status());
        assertNull(response.representativeMemberName());
        assertNull(response.attendingMembers());
    }

    @Test
    @DisplayName("Maps FamilyRegistration entity to RegistrationResponse with enriched names and attending members")
    void testToResponseWithEnrichedDetails() {
        Instant now = Instant.now();
        FamilyRegistration registration = FamilyRegistration.builder()
                .registrationId("REG_002")
                .eventId("EVT_001")
                .representativeMemberId("MEM_001")
                .totalAttendees(2)
                .vegetarianMeals(0)
                .savoryMeals(2)
                .status(RegistrationStatus.Confirmed)
                .registrationDate(now)
                .build();

        List<RegistrationMemberDetailResponse> memberResponses = List.of(
                new RegistrationMemberDetailResponse("MEM_001", "Nguyễn Văn Trưởng", false),
                new RegistrationMemberDetailResponse("MEM_002", "Nguyễn Thị Vợ", false)
        );

        RegistrationResponse response = registrationMapper.toResponse(
                registration,
                "Nguyễn Văn Trưởng",
                memberResponses
        );

        assertNotNull(response);
        assertEquals("REG_002", response.registrationId());
        assertEquals("Nguyễn Văn Trưởng", response.representativeMemberName());
        assertNotNull(response.attendingMembers());
        assertEquals(2, response.attendingMembers().size());
        assertEquals("Nguyễn Văn Trưởng", response.attendingMembers().get(0).fullName());
    }

    @Test
    @DisplayName("Maps RegistrationMemberDetail entity to RegistrationMemberDetailResponse")
    void testToMemberDetailResponse() {
        RegistrationMemberDetail detail = RegistrationMemberDetail.builder()
                .registrationId("REG_001")
                .memberId("MEM_002")
                .isCheckedIn(true)
                .build();

        RegistrationMemberDetailResponse response = registrationMapper.toMemberDetailResponse(
                detail, "Nguyễn Văn Con"
        );

        assertNotNull(response);
        assertEquals("MEM_002", response.memberId());
        assertEquals("Nguyễn Văn Con", response.fullName());
        assertTrue(response.isCheckedIn());
    }

    @Test
    @DisplayName("Maps RegistrationAggregateProjection to RegistrationSummaryResponse and RegistrationSummaryDto")
    void testProjectionMapping() {
        RegistrationAggregateProjection projection = new RegistrationAggregateProjection() {
            @Override
            public long getTotalRegistrations() {
                return 15;
            }

            @Override
            public long getTotalAttendees() {
                return 52;
            }

            @Override
            public long getTotalVegetarianMeals() {
                return 6;
            }

            @Override
            public long getTotalSavoryMeals() {
                return 46;
            }
        };

        RegistrationSummaryResponse summaryResponse = registrationMapper.toSummaryResponse("EVT_001", projection);
        assertNotNull(summaryResponse);
        assertEquals("EVT_001", summaryResponse.eventId());
        assertEquals(15, summaryResponse.totalRegistrations());
        assertEquals(52, summaryResponse.totalAttendees());
        assertEquals(6, summaryResponse.totalVegetarianMeals());
        assertEquals(46, summaryResponse.totalSavoryMeals());

        RegistrationSummaryDto summaryDto = registrationMapper.toSummaryDto("EVT_001", projection);
        assertNotNull(summaryDto);
        assertEquals("EVT_001", summaryDto.eventId());
        assertEquals(15, summaryDto.totalRegistrations());
        assertEquals(52, summaryDto.totalAttendees());
        assertEquals(6, summaryDto.totalVegetarianMeals());
        assertEquals(46, summaryDto.totalSavoryMeals());
    }

    @Test
    @DisplayName("Handles null projection gracefully with zero values")
    void testNullProjectionHandling() {
        RegistrationSummaryResponse response = registrationMapper.toSummaryResponse("EVT_EMPTY", null);
        assertNotNull(response);
        assertEquals(0, response.totalRegistrations());
        assertEquals(0, response.totalAttendees());

        RegistrationSummaryDto dto = registrationMapper.toSummaryDto("EVT_EMPTY", null);
        assertNotNull(dto);
        assertEquals(0, dto.totalRegistrations());
        assertEquals(0, dto.totalAttendees());
    }
}
