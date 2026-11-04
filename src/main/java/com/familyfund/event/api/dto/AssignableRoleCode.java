package com.familyfund.event.api.dto;

/**
 * Roles assignable via US-03 Dual-RBAC management.
 */
public enum AssignableRoleCode {
    ROLE_BRANCH_HEAD("ROLE_BRANCH_HEAD", "Trưởng Chi / Nhánh", RoleScopeType.BRANCH),
    HeadOfOrganizer("HeadOfOrganizer", "Trưởng Ban Tổ Chức", RoleScopeType.EVENT),
    OrganizerMember("OrganizerMember", "Thành Viên Ban Tổ Chức", RoleScopeType.EVENT),
    EventTreasurer("EventTreasurer", "Thủ Quỹ Sự Kiện", RoleScopeType.EVENT);

    private final String code;
    private final String displayName;
    private final RoleScopeType supportedScope;

    AssignableRoleCode(String code, String displayName, RoleScopeType supportedScope) {
        this.code = code;
        this.displayName = displayName;
        this.supportedScope = supportedScope;
    }

    public String getCode() {
        return code;
    }

    public String getDisplayName() {
        return displayName;
    }

    public RoleScopeType getSupportedScope() {
        return supportedScope;
    }

    public static AssignableRoleCode fromCode(String code) {
        if (code == null) return null;
        for (AssignableRoleCode r : values()) {
            if (r.code.equalsIgnoreCase(code.trim())) {
                return r;
            }
        }
        return null;
    }
}
