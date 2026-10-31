package com.familyfund.contribution.internal.service;

import com.familyfund.contribution.internal.dto.request.CreateEventItemBenchmarkRequest;
import com.familyfund.contribution.internal.dto.response.EventItemBenchmarkResponse;
import com.familyfund.contribution.internal.entity.EventItemBenchmark;
import com.familyfund.contribution.internal.repository.EventItemBenchmarkRepository;
import com.familyfund.contribution.internal.util.BenchmarkIdGenerator;
import com.familyfund.core.exception.BusinessRuleException;
import com.familyfund.core.exception.ResourceNotFoundException;
import com.familyfund.core.tenant.TenantContext;
import com.familyfund.event.api.EventQueryService;
import com.familyfund.event.api.dto.EventStatus;
import com.familyfund.event.api.dto.EventSummaryDto;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EventItemBenchmarkServiceImplTest {

    @Mock
    private EventItemBenchmarkRepository benchmarkRepository;

    @Mock
    private BenchmarkIdGenerator benchmarkIdGenerator;

    @Mock
    private EventQueryService eventQueryService;

    @InjectMocks
    private EventItemBenchmarkServiceImpl benchmarkService;

    private static final String CLAN_ID = "CLAN_01";
    private static final String EVENT_ID = "EVT_001";

    @BeforeEach
    void setUp() {
        TenantContext.setClanId(CLAN_ID);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("Should create event item benchmark successfully")
    void createBenchmark_success() {
        CreateEventItemBenchmarkRequest request = new CreateEventItemBenchmarkRequest(
                "Heo quay",
                "con",
                new BigDecimal("3500000"),
                "Heo quay 10-12kg"
        );

        EventSummaryDto eventSummary = new EventSummaryDto(EVENT_ID, CLAN_ID, "Giỗ Tổ 2026", EventStatus.Draft, Instant.now());

        when(eventQueryService.findEventSummary(EVENT_ID)).thenReturn(Optional.of(eventSummary));
        when(benchmarkIdGenerator.generate()).thenReturn("BMK12345678");
        when(benchmarkRepository.existsById("BMK12345678")).thenReturn(false);
        when(benchmarkRepository.save(any(EventItemBenchmark.class))).thenAnswer(invocation -> invocation.getArgument(0));

        EventItemBenchmarkResponse response = benchmarkService.createBenchmark(EVENT_ID, request);

        assertThat(response).isNotNull();
        assertThat(response.benchmarkId()).isEqualTo("BMK12345678");
        assertThat(response.itemName()).isEqualTo("Heo quay");
        assertThat(response.unitPrice()).isEqualByComparingTo(new BigDecimal("3500000"));
    }

    @Test
    @DisplayName("Should throw BusinessRuleException when configuring benchmark for a closed event")
    void createBenchmark_closedEvent_throwsBusinessRuleException() {
        CreateEventItemBenchmarkRequest request = new CreateEventItemBenchmarkRequest(
                "Heo quay",
                "con",
                new BigDecimal("3500000"),
                null
        );

        EventSummaryDto closedEvent = new EventSummaryDto(EVENT_ID, CLAN_ID, "Giỗ Tổ 2026", EventStatus.Closed, Instant.now());
        when(eventQueryService.findEventSummary(EVENT_ID)).thenReturn(Optional.of(closedEvent));

        assertThatThrownBy(() -> benchmarkService.createBenchmark(EVENT_ID, request))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Cannot configure benchmarks for a closed event");
    }

    @Test
    @DisplayName("Should retrieve benchmarks by event ID")
    void getBenchmarksByEvent_success() {
        EventSummaryDto eventSummary = new EventSummaryDto(EVENT_ID, CLAN_ID, "Giỗ Tổ 2026", EventStatus.Ongoing, Instant.now());
        when(eventQueryService.findEventSummary(EVENT_ID)).thenReturn(Optional.of(eventSummary));

        EventItemBenchmark b1 = EventItemBenchmark.builder()
                .benchmarkId("BMK_001")
                .eventId(EVENT_ID)
                .clanId(CLAN_ID)
                .itemName("Heo quay")
                .unit("con")
                .unitPrice(new BigDecimal("3500000"))
                .build();

        when(benchmarkRepository.findByEventIdOrderByCreatedAtAsc(EVENT_ID)).thenReturn(List.of(b1));

        List<EventItemBenchmarkResponse> responses = benchmarkService.getBenchmarksByEvent(EVENT_ID);

        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).itemName()).isEqualTo("Heo quay");
    }

    @Test
    @DisplayName("Should delete benchmark successfully")
    void deleteBenchmark_success() {
        String benchmarkId = "BMK_001";
        EventItemBenchmark b1 = EventItemBenchmark.builder()
                .benchmarkId(benchmarkId)
                .eventId(EVENT_ID)
                .clanId(CLAN_ID)
                .itemName("Heo quay")
                .unit("con")
                .unitPrice(new BigDecimal("3500000"))
                .build();

        EventSummaryDto eventSummary = new EventSummaryDto(EVENT_ID, CLAN_ID, "Giỗ Tổ 2026", EventStatus.Ongoing, Instant.now());

        when(benchmarkRepository.findByBenchmarkIdAndEventId(benchmarkId, EVENT_ID)).thenReturn(Optional.of(b1));
        when(eventQueryService.findEventSummary(EVENT_ID)).thenReturn(Optional.of(eventSummary));

        benchmarkService.deleteBenchmark(EVENT_ID, benchmarkId);

        verify(benchmarkRepository).delete(b1);
    }
}
