package com.familyfund.genealogy.internal.media;

import com.familyfund.core.storage.MediaException;

public enum MediaVisibility {
    CLAN, BRANCH, RESTRICTED;
    public static MediaVisibility parse(String value) {
        try { return valueOf(value); }
        catch (RuntimeException ex) { throw new MediaException(400, "INVALID_VISIBILITY", "Phạm vi chia sẻ không hợp lệ"); }
    }
}
