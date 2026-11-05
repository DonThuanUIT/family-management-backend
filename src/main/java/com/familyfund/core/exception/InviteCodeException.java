package com.familyfund.core.exception;

/**
 * Raised when an invitation code / position link code cannot be used.
 *
 * <p>Extends {@link BadRequestException} so the existing HTTP 400 behaviour is unchanged, and adds a stable,
 * machine-readable {@link #getCode() code} so a client can tell "expired" from "exhausted" from "revoked"
 * without parsing the Vietnamese message text.</p>
 */
public class InviteCodeException extends BadRequestException {

    /** The code exists but was deactivated (is_active = false) or already consumed. */
    public static final String REVOKED = "INVITE_CODE_REVOKED";
    /** The code exists and is active but expires_at is in the past. */
    public static final String EXPIRED = "INVITE_CODE_EXPIRED";
    /** The code exists and is active but used_count already reached max_uses. */
    public static final String EXHAUSTED = "INVITE_CODE_EXHAUSTED";

    private final String code;

    public InviteCodeException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }

    /**
     * @param label human readable kind of code ("mời", "liên kết vị trí")
     * @param value the raw code the caller used
     */
    public static InviteCodeException revoked(String label, String value) {
        return new InviteCodeException(REVOKED,
                "Mã " + label + " '" + value + "' đã bị thu hồi hoặc đã được sử dụng");
    }

    public static InviteCodeException expired(String label, String value) {
        return new InviteCodeException(EXPIRED,
                "Mã " + label + " '" + value + "' đã hết hạn sử dụng");
    }

    public static InviteCodeException exhausted(String label, String value) {
        return new InviteCodeException(EXHAUSTED,
                "Mã " + label + " '" + value + "' đã hết lượt sử dụng");
    }
}