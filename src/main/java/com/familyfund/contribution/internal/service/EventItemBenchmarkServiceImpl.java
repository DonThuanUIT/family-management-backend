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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class EventItemBenchmarkServiceImpl implements EventItemBenchmarkService {

    private final EventItemBenchmarkRepository benchmarkRepository;
    private final BenchmarkIdGenerator benchmarkIdGenerator;
    private final EventQueryService eventQueryService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public EventItemBenchmarkResponse createBenchmark(String eventId, CreateEventItemBenchmarkRequest request) {
        String currentClanId = TenantContext.getClanId();

        EventSummaryDto eventSummary = eventQueryService.findEventSummary(eventId)
                .orElseThrow(() -> new ResourceNotFoundException("Event not found with ID: " + eventId));

        if (currentClanId != null && !currentClanId.isBlank() && !currentClanId.equals(eventSummary.clanId())) {
            throw new BusinessRuleException("Event does not belong to the current clan");
        }

        if (eventSummary.status() == EventStatus.Closed) {
            throw new BusinessRuleException("Cannot configure benchmarks for a closed event");
        }

        String clanIdToUse = (currentClanId != null && !currentClanId.isBlank()) ? currentClanId : eventSummary.clanId();

        String benchmarkId = generateUniqueBenchmarkId();

        EventItemBenchmark benchmark = EventItemBenchmark.builder()
                .benchmarkId(benchmarkId)
                .eventId(eventId)
                .clanId(clanIdToUse)
                .itemName(request.itemName().trim())
                .unit(request.unit().trim())
                .unitPrice(request.unitPrice())
                .note(request.note() != null ? request.note().trim() : null)
                .createdAt(Instant.now())
                .build();

        benchmark = benchmarkRepository.save(benchmark);
        log.info("Created event item benchmark {} for event {}", benchmarkId, eventId);

        return toResponse(benchmark);
    }

    @Override
    @Transactional(readOnly = true)
    public List<EventItemBenchmarkResponse> getBenchmarksByEvent(String eventId) {
        String currentClanId = TenantContext.getClanId();

        EventSummaryDto eventSummary = eventQueryService.findEventSummary(eventId)
                .orElseThrow(() -> new ResourceNotFoundException("Event not found with ID: " + eventId));

        if (currentClanId != null && !currentClanId.isBlank() && !currentClanId.equals(eventSummary.clanId())) {
            throw new BusinessRuleException("Event does not belong to the current clan");
        }

        return benchmarkRepository.findByEventIdOrderByCreatedAtAsc(eventId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteBenchmark(String eventId, String benchmarkId) {
        EventItemBenchmark benchmark = benchmarkRepository.findByBenchmarkIdAndEventId(benchmarkId, eventId)
                .orElseThrow(() -> new ResourceNotFoundException("Benchmark not found with ID: " + benchmarkId + " for event: " + eventId));

        EventSummaryDto eventSummary = eventQueryService.findEventSummary(eventId)
                .orElseThrow(() -> new ResourceNotFoundException("Event not found with ID: " + eventId));

        if (eventSummary.status() == EventStatus.Closed) {
            throw new BusinessRuleException("Cannot delete benchmark for a closed event");
        }

        benchmarkRepository.delete(benchmark);
        log.info("Deleted benchmark {} from event {}", benchmarkId, eventId);
    }

    private String generateUniqueBenchmarkId() {
        for (int i = 0; i < 5; i++) {
            String candidate = benchmarkIdGenerator.generate();
            if (!benchmarkRepository.existsById(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("Failed to generate a unique benchmark ID after multiple attempts");
    }

    private EventItemBenchmarkResponse toResponse(EventItemBenchmark benchmark) {
        return new EventItemBenchmarkResponse(
                benchmark.getBenchmarkId(),
                benchmark.getEventId(),
                benchmark.getClanId(),
                benchmark.getItemName(),
                benchmark.getUnit(),
                benchmark.getUnitPrice(),
                benchmark.getNote(),
                benchmark.getCreatedAt()
        );
    }
}
