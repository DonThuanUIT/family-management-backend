package com.familyfund.contribution.internal.service;

import com.familyfund.contribution.internal.dto.request.CreateEventItemBenchmarkRequest;
import com.familyfund.contribution.internal.dto.response.EventItemBenchmarkResponse;

import java.util.List;

public interface EventItemBenchmarkService {

    EventItemBenchmarkResponse createBenchmark(String eventId, CreateEventItemBenchmarkRequest request);

    List<EventItemBenchmarkResponse> getBenchmarksByEvent(String eventId);

    void deleteBenchmark(String eventId, String benchmarkId);
}
