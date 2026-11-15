package com.familyfund.finance.api.dto;

/**
 * Lifecycle status of an advance voucher (US-46).
 *
 * <ul>
 *   <li>{@link #ChoGiaoTien} - Created by event organizer, awaiting cash disbursement by treasurer.</li>
 *   <li>{@link #ChuaTatToan} - Cash disbursed by treasurer, awaiting expense settlement.</li>
 *   <li>{@link #DaTatToan} - Fully settled via expense settlement voucher.</li>
 *   <li>{@link #DaHuy} - Cancelled prior to cash disbursement.</li>
 * </ul>
 */
public enum AdvanceVoucherStatus {
    ChoGiaoTien,
    ChuaTatToan,
    DaTatToan,
    DaHuy
}
