package com.familyfund.finance.api.dto;

/**
 * Expense category for major expenditures funded by the clan common fund (US-60).
 *
 * <ul>
 *   <li>{@link #TempleMaintenance} - Tu sửa, bảo trì từ đường / nhà thờ họ.</li>
 *   <li>{@link #ReliefSupport} - Cứu trợ, thăm hỏi con cháu gặp khó khăn.</li>
 *   <li>{@link #Scholarship} - Khen thưởng, quỹ khuyến học dòng họ.</li>
 *   <li>{@link #Ceremony} - Nghi lễ, đại lễ dòng họ cấp tộc.</li>
 *   <li>{@link #Other} - Các khoản chi lớn hợp lệ khác của dòng họ.</li>
 * </ul>
 */
public enum CommonFundExpenseCategory {
    TempleMaintenance,
    ReliefSupport,
    Scholarship,
    Ceremony,
    Other
}
