package com.familyfund.event.internal.service;

import com.familyfund.event.internal.dto.request.RegisterAttendeesRequest;
import com.familyfund.event.internal.dto.request.RequestCancelRegistrationRequest;
import com.familyfund.event.internal.dto.request.RequestModifyRegistrationRequest;
import com.familyfund.event.internal.dto.request.ReviewRegistrationRequest;
import com.familyfund.event.internal.dto.response.RegistrationResponse;
import com.familyfund.event.internal.dto.response.RegistrationSummaryResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

/**
 * Service contract for managing family event attendance registrations,
 * real-time aggregations, and modification/cancellation reviews (US-06).
 */
public interface RegistrationService {

    /**
     * Nộp biểu mẫu đăng ký tham gia sự kiện.
     */
    RegistrationResponse registerAttendees(String eventId, RegisterAttendeesRequest request);

    /**
     * Lấy số liệu tổng hợp cộng dồn cỗ bàn và người tham dự cho Ban tổ chức.
     */
    RegistrationSummaryResponse getRegistrationSummary(String eventId);

    /**
     * Danh sách tất cả hộ gia đình đăng ký tham gia sự kiện.
     */
    List<RegistrationResponse> listRegistrations(String eventId);

    /**
     * Danh sách tất cả hộ gia đình đăng ký có phân trang.
     */
    Page<RegistrationResponse> listRegistrations(String eventId, Pageable pageable);

    /**
     * Chi tiết một lượt đăng ký cụ thể.
     */
    RegistrationResponse getRegistrationById(String eventId, String registrationId);

    /**
     * Xem thông tin đăng ký của chính hộ gia đình người đăng nhập.
     */
    RegistrationResponse getMyRegistration(String eventId);

    /**
     * Người đại diện gửi yêu cầu thay đổi số lượng người/khẩu phần/phương tiện.
     */
    RegistrationResponse requestModification(String eventId, String registrationId, RequestModifyRegistrationRequest request);

    /**
     * Người đại diện gửi yêu cầu hủy đăng ký tham gia.
     */
    RegistrationResponse requestCancellation(String eventId, String registrationId, RequestCancelRegistrationRequest request);

    /**
     * Ban tổ chức phê duyệt (chấp thuận hoặc từ chối) yêu cầu sửa/hủy.
     */
    RegistrationResponse reviewRegistrationRequest(String eventId, String registrationId, ReviewRegistrationRequest request);
}

