package com.familyfund.core.storage;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties("app.storage")
@org.springframework.validation.annotation.Validated
@Getter @Setter
public class StorageProperties {
    private String supabaseUrl = "";
    private String supabaseServiceKey = "";
    @jakarta.validation.constraints.Pattern(regexp="[a-zA-Z0-9_-]{1,100}")
    private String bucket = "member-media";
    @jakarta.validation.constraints.Min(1)
    private long quotaBytes = 500L * 1024 * 1024;
    @jakarta.validation.constraints.Min(1)
    private int maxAttachments = 20;
    @jakarta.validation.constraints.Min(1)
    @jakarta.validation.constraints.Max(300)
    private int timeoutSeconds = 60;
}
