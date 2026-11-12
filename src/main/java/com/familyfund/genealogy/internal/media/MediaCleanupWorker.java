package com.familyfund.genealogy.internal.media;

import com.familyfund.core.storage.StorageRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.*;
import static com.familyfund.genealogy.internal.media.MemberMediaRepository.*;

@Component
@ConditionalOnProperty(name="app.storage.cleanup-enabled",havingValue="true",matchIfMissing=true)
@RequiredArgsConstructor
@Slf4j
public class MediaCleanupWorker {
    private final MemberMediaRepository repository;
    private final StorageRegistry storage;
    private final PlatformTransactionManager transactions;

    @Scheduled(fixedDelayString="${app.storage.cleanup-interval-ms:60000}",initialDelayString="${app.storage.cleanup-initial-delay-ms:60000}")
    public void run() {
        for (int i=0;i<20;i++) {
            var file = new TransactionTemplate(transactions).execute(status -> {
                var row = repository.one("SELECT f.* FROM stored_files f WHERE f.state IN ('PENDING','DELETE_PENDING') AND f.cleanup_after<now() AND NOT EXISTS (SELECT 1 FROM members m WHERE m.avatar_file_id=f.id) AND NOT EXISTS (SELECT 1 FROM member_attachments a WHERE a.file_id=f.id AND a.deleted_at IS NULL) ORDER BY f.cleanup_after LIMIT 1 FOR UPDATE SKIP LOCKED");
                if (row != null) {
                    repository.update("UPDATE stored_files SET state='DELETE_PENDING',cleanup_after=now()+interval '5 minutes',updated_at=now() WHERE id=?",uuid(row,"id"));
                    repository.update("UPDATE media_upload_requests SET state='FAILED' WHERE file_id=? AND state='PENDING'",uuid(row,"id"));
                }
                return row;
            });
            if (file == null) return;
            try {
                storage.get(string(file,"provider")).delete(string(file,"bucket"),string(file,"object_key"));
                new TransactionTemplate(transactions).executeWithoutResult(status -> repository.update("UPDATE stored_files SET state='DELETED',updated_at=now(),last_delete_error=NULL WHERE id=? AND state='DELETE_PENDING'",uuid(file,"id")));
            } catch (RuntimeException ex) {
                new TransactionTemplate(transactions).executeWithoutResult(status -> repository.update("UPDATE stored_files SET delete_attempts=delete_attempts+1,last_delete_error='STORAGE_DELETE_FAILED',cleanup_after=now()+least(3600,power(2,least(delete_attempts,10))*60)*interval '1 second' WHERE id=?",uuid(file,"id")));
                log.warn("Media cleanup retry scheduled for file {}",uuid(file,"id"));
            }
        }
    }
}
