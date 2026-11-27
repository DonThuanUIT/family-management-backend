package com.familyfund.finance.internal.service;

import com.familyfund.core.exception.BusinessRuleException;
import com.familyfund.core.exception.ResourceNotFoundException;
import com.familyfund.core.security.CurrentUserProvider;
import com.familyfund.core.security.SystemRoles;
import com.familyfund.core.storage.MediaException;
import com.familyfund.core.storage.StorageProperties;
import com.familyfund.core.storage.StorageRegistry;
import com.familyfund.core.tenant.TenantContext;
import com.familyfund.event.api.EventQueryService;
import com.familyfund.event.api.dto.EventRole;
import com.familyfund.finance.internal.dto.response.LedgerEvidenceUploadResponse;
import com.familyfund.finance.internal.entity.ImmutableLedger;
import com.familyfund.finance.internal.repository.ImmutableLedgerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSArray;
import org.apache.pdfbox.cos.COSBase;
import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.cos.COSObject;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.HexFormat;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Semaphore;
import java.util.regex.Pattern;

/**
 * Private-storage implementation of {@link LedgerEvidenceService} using {@link StorageRegistry}
 * and self-contained PDF/image sanitization (US-15).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LedgerEvidenceServiceImpl implements LedgerEvidenceService {

    private static final long MAX_FILE_BYTES = 10L * 1024 * 1024;
    private static final long MAX_IMAGE_PIXELS = 20_000_000L;
    private static final int MAX_PDF_PAGES = 500;
    private static final int MAX_PDF_NODES = 100_000;
    private static final String EVIDENCE_FOLDER = "ledger-evidence";
    private static final String PROXY_URL_PREFIX = "/api/v1/ledger/evidence/";
    private static final Pattern SAFE_CLAN_ID = Pattern.compile("[a-zA-Z0-9_-]{1,20}");
    private static final Pattern SAFE_STORED_FILENAME = Pattern.compile("[a-fA-F0-9-]{36}\\.(jpg|png|pdf)");
    private static final Set<String> FORBIDDEN_PDF_KEYS = Set.of(
            "JavaScript", "JS", "Launch", "EmbeddedFiles", "EmbeddedFile",
            "Filespec", "RichMedia", "XFA", "OpenAction", "AA",
            "SubmitForm", "ImportData", "GoToR", "GoToE", "Rendition", "Movie", "Sound"
    );

    private final StorageRegistry storageRegistry;
    private final StorageProperties storageProperties;
    private final ImmutableLedgerRepository immutableLedgerRepository;
    private final EventQueryService eventQueryService;
    private final CurrentUserProvider currentUserProvider;

    private final Semaphore processingSemaphore = new Semaphore(2);

    @Override
    @Transactional(readOnly = true)
    public LedgerEvidenceUploadResponse uploadEvidence(MultipartFile file, String originalTransactionId) {
        String clanId = requireValidClanId();
        authorizeUploader(clanId, normalizeBlank(originalTransactionId));

        ValidatedEvidence validated = validateAndSanitize(file);
        String storedFileName = UUID.randomUUID() + "." + validated.extension();
        String storageKey = clanId + "/" + EVIDENCE_FOLDER + "/" + storedFileName;

        storageRegistry.current().put(
                storageProperties.getBucket(),
                storageKey,
                validated.bytes(),
                validated.mimeType()
        );

        String proxyUrl = PROXY_URL_PREFIX + storedFileName;
        log.info("US-15: Uploaded ledger evidence {} ({} bytes, sha256={}...) for clan {}",
                storedFileName, validated.bytes().length, validated.checksum().substring(0, 12), clanId);

        return new LedgerEvidenceUploadResponse(
                proxyUrl,
                storedFileName,
                validated.originalFilename(),
                validated.mimeType(),
                validated.bytes().length,
                validated.checksum()
        );
    }

    @Override
    public EvidenceDownload downloadEvidence(String fileName) {
        String clanId = requireValidClanId();
        String normalizedFileName = fileName != null ? fileName.trim() : "";
        if (!SAFE_STORED_FILENAME.matcher(normalizedFileName).matches()) {
            throw MediaException.invalid("Tên tệp biên bản không hợp lệ");
        }

        String storageKey = clanId + "/" + EVIDENCE_FOLDER + "/" + normalizedFileName;
        InputStream stream = storageRegistry.current().open(storageProperties.getBucket(), storageKey);
        String ext = normalizedFileName.substring(normalizedFileName.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
        String mimeType = switch (ext) {
            case "pdf" -> "application/pdf";
            case "png" -> "image/png";
            default -> "image/jpeg";
        };
        return new EvidenceDownload(stream, normalizedFileName, mimeType);
    }

    private String requireValidClanId() {
        String clanId = normalizeBlank(TenantContext.getClanId());
        if (clanId == null) {
            throw new BusinessRuleException("Thiếu thông tin dòng họ (X-Clan-Id)");
        }
        if (!SAFE_CLAN_ID.matcher(clanId).matches()) {
            throw new BusinessRuleException("Mã dòng họ không hợp lệ để lưu trữ biên bản");
        }
        return clanId;
    }

    private void authorizeUploader(String clanId, String txId) {
        boolean isClanLeader = currentUserProvider.hasAuthority(SystemRoles.ADMIN)
                || currentUserProvider.hasAuthority(SystemRoles.CLAN_HEAD);

        if (txId != null) {
            ImmutableLedger originalTx = immutableLedgerRepository.findByTransactionIdAndClanId(txId, clanId)
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Giao dịch sổ cái gốc", "originalTransactionId", txId));
            if (isClanLeader) {
                return;
            }
            Optional<String> memberIdOpt = currentUserProvider.currentMemberId();
            if (memberIdOpt.isPresent() && originalTx.getEventId() != null) {
                Set<EventRole> eventRoles =
                        eventQueryService.getMemberRolesInEvent(originalTx.getEventId(), memberIdOpt.get());
                if (eventRoles.contains(EventRole.EventTreasurer)
                        || eventRoles.contains(EventRole.HeadOfOrganizer)) {
                    return;
                }
            }
            throw new AccessDeniedException(
                    "Chỉ Thủ quỹ sự kiện, Trưởng ban tổ chức hoặc Trưởng tộc mới có quyền tải lên biên bản bù trừ sổ cái");
        }

        if (isClanLeader) {
            return;
        }
        if (currentUserProvider.hasAuthority(SystemRoles.BRANCH_HEAD)) {
            return;
        }
        Optional<String> memberIdOpt = currentUserProvider.currentMemberId();
        if (memberIdOpt.isPresent()
                && eventQueryService.hasEventRoleInClan(clanId, memberIdOpt.get(), EventRole.EventTreasurer)) {
            return;
        }
        throw new AccessDeniedException(
                "Chỉ Trưởng tộc, Trưởng chi, Thủ quỹ sự kiện hoặc Quản trị viên mới có quyền tải lên chứng từ Sổ cái");
    }

    private ValidatedEvidence validateAndSanitize(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw MediaException.invalid("Tệp biên bản không được để trống");
        }
        if (file.getSize() > MAX_FILE_BYTES) {
            throw new MediaException(413, "FILE_TOO_LARGE", "Tệp biên bản vượt quá dung lượng tối đa 10 MB");
        }
        if (!processingSemaphore.tryAcquire()) {
            throw new MediaException(429, "UPLOAD_RATE_LIMITED", "Hệ thống đang xử lý nhiều tệp, vui lòng thử lại sau");
        }

        try {
            String originalName = sanitizeFilename(file.getOriginalFilename());
            int dotIdx = originalName.lastIndexOf('.');
            String ext = dotIdx >= 0
                    ? originalName.substring(dotIdx + 1).toLowerCase(Locale.ROOT)
                    : "";
            String contentType = file.getContentType() != null
                    ? file.getContentType().toLowerCase(Locale.ROOT)
                    : "";

            byte[] rawBytes;
            try (InputStream stream = file.getInputStream()) {
                rawBytes = stream.readNBytes((int) MAX_FILE_BYTES + 1);
            }
            if (rawBytes.length == 0) {
                throw MediaException.invalid("Tệp biên bản không được để trống");
            }
            if (rawBytes.length > MAX_FILE_BYTES) {
                throw new MediaException(413, "FILE_TOO_LARGE", "Tệp biên bản vượt quá dung lượng tối đa 10 MB");
            }

            if ("pdf".equals(ext) && isAllowedMime(contentType, "application/pdf")) {
                validatePdf(rawBytes);
                return new ValidatedEvidence(rawBytes, "application/pdf", "pdf", originalName, sha256(rawBytes));
            }

            boolean isJpegCandidate = ("jpg".equals(ext) || "jpeg".equals(ext))
                    && isAllowedMime(contentType, "image/jpeg", "image/jpg")
                    && rawBytes.length >= 3
                    && rawBytes[0] == (byte) 0xff
                    && rawBytes[1] == (byte) 0xd8
                    && rawBytes[2] == (byte) 0xff;

            boolean isPngCandidate = "png".equals(ext)
                    && isAllowedMime(contentType, "image/png")
                    && rawBytes.length >= 8
                    && Arrays.equals(
                            Arrays.copyOf(rawBytes, 8),
                            new byte[]{(byte) 137, 80, 78, 71, 13, 10, 26, 10});

            if (!isJpegCandidate && !isPngCandidate) {
                throw new MediaException(415, "UNSUPPORTED_MEDIA_TYPE",
                        "Chỉ hỗ trợ tệp PDF, JPG hoặc PNG cho biên bản đối soát");
            }

            return sanitizeImage(rawBytes, isJpegCandidate, originalName);
        } catch (MediaException ex) {
            throw ex;
        } catch (IOException | IllegalArgumentException ex) {
            throw MediaException.invalid("Tệp biên bản bị hỏng hoặc không thể xử lý");
        } finally {
            processingSemaphore.release();
        }
    }

    private ValidatedEvidence sanitizeImage(byte[] rawBytes, boolean jpeg, String originalName) throws IOException {
        try (var input = ImageIO.createImageInputStream(new ByteArrayInputStream(rawBytes))) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) {
                throw MediaException.invalid("Tệp ảnh biên bản không hợp lệ");
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(input);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if (width < 1 || height < 1 || (long) width * height > MAX_IMAGE_PIXELS) {
                    throw MediaException.invalid("Kích thước ảnh vượt quá giới hạn 20 megapixel");
                }

                BufferedImage oriented = applyOrientation(reader.read(0), jpeg ? readExifOrientation(rawBytes) : 1);
                int w = oriented.getWidth();
                int h = oriented.getHeight();
                BufferedImage clean = new BufferedImage(
                        w,
                        h,
                        jpeg ? BufferedImage.TYPE_INT_RGB : BufferedImage.TYPE_INT_ARGB
                );
                Graphics2D graphics = clean.createGraphics();
                try {
                    if (jpeg) {
                        graphics.setColor(Color.WHITE);
                        graphics.fillRect(0, 0, w, h);
                    }
                    graphics.setRenderingHint(
                            RenderingHints.KEY_INTERPOLATION,
                            RenderingHints.VALUE_INTERPOLATION_BICUBIC
                    );
                    graphics.drawImage(oriented, 0, 0, w, h, null);
                } finally {
                    graphics.dispose();
                }

                String outputExt = jpeg ? "jpg" : "png";
                String mimeType = jpeg ? "image/jpeg" : "image/png";
                var out = new ByteArrayOutputStream();
                if (!ImageIO.write(clean, outputExt, out)) {
                    throw MediaException.invalid("Không thể xử lý tệp ảnh biên bản");
                }
                byte[] cleanBytes = out.toByteArray();
                if (cleanBytes.length > MAX_FILE_BYTES) {
                    throw new MediaException(413, "FILE_TOO_LARGE", "Ảnh sau xử lý vượt dung lượng tối đa 10 MB");
                }
                return new ValidatedEvidence(cleanBytes, mimeType, outputExt, originalName, sha256(cleanBytes));
            } finally {
                reader.dispose();
            }
        }
    }

    private void validatePdf(byte[] bytes) throws IOException {
        if (bytes.length < 10
                || !new String(bytes, 0, 5, StandardCharsets.US_ASCII).equals("%PDF-")
                || !new String(
                        bytes,
                        Math.max(0, bytes.length - 1024),
                        Math.min(1024, bytes.length),
                        StandardCharsets.ISO_8859_1)
                        .contains("%%EOF")) {
            throw MediaException.invalid("Tệp PDF biên bản không hợp lệ");
        }
        try (var document = Loader.loadPDF(bytes)) {
            if (document.isEncrypted() || document.getNumberOfPages() < 1 || document.getNumberOfPages() > MAX_PDF_PAGES) {
                throw MediaException.invalid("PDF được đặt mật khẩu hoặc vượt quá giới hạn 500 trang");
            }
            var visited = Collections.newSetFromMap(new IdentityHashMap<COSBase, Boolean>());
            Deque<COSBase> queue = new ArrayDeque<>();
            queue.add(document.getDocumentCatalog().getCOSObject());
            int nodes = 0;
            while (!queue.isEmpty()) {
                if (++nodes > MAX_PDF_NODES) {
                    throw MediaException.invalid("Cấu trúc PDF quá phức tạp");
                }
                COSBase node = queue.remove();
                if (!visited.add(node)) {
                    continue;
                }
                if (node instanceof COSObject object) {
                    if (object.getObject() != null) {
                        queue.add(object.getObject());
                    }
                } else if (node instanceof COSDictionary dict) {
                    for (COSName key : dict.keySet()) {
                        if (FORBIDDEN_PDF_KEYS.contains(key.getName())) {
                            throw MediaException.invalid("PDF chứa mã thực thi hoặc tệp nhúng không được phép");
                        }
                        COSBase item = dict.getDictionaryObject(key);
                        if (item instanceof COSName cosName && FORBIDDEN_PDF_KEYS.contains(cosName.getName())) {
                            throw MediaException.invalid("PDF chứa nội dung chủ động không được phép");
                        }
                        if (item != null) {
                            queue.add(item);
                        }
                    }
                } else if (node instanceof COSArray array) {
                    for (COSBase item : array) {
                        if (item != null) {
                            queue.add(item);
                        }
                    }
                }
            }
        }
    }

    private static int readExifOrientation(byte[] bytes) {
        try {
            for (int offset = 2; offset + 4 < bytes.length; ) {
                if ((bytes[offset] & 255) != 255) {
                    break;
                }
                int marker = bytes[offset + 1] & 255;
                if (marker == 0xda || marker == 0xd9) {
                    break;
                }
                int length = ((bytes[offset + 2] & 255) << 8) | (bytes[offset + 3] & 255);
                if (length < 2 || offset + 2 + length > bytes.length) {
                    break;
                }
                int start = offset + 4;
                if (marker == 0xe1 && length > 16
                        && new String(bytes, start, 6, StandardCharsets.ISO_8859_1).equals("Exif\0\0")) {
                    ByteBuffer tiff = ByteBuffer.wrap(bytes, start + 6, length - 8).slice();
                    tiff.order(tiff.get(0) == 'I' && tiff.get(1) == 'I'
                            ? ByteOrder.LITTLE_ENDIAN
                            : ByteOrder.BIG_ENDIAN);
                    int ifd = tiff.getInt(4);
                    int count = Short.toUnsignedInt(tiff.getShort(ifd));
                    for (int i = 0; i < Math.min(count, 512); i++) {
                        int at = ifd + 2 + i * 12;
                        if (Short.toUnsignedInt(tiff.getShort(at)) == 0x112) {
                            return Short.toUnsignedInt(tiff.getShort(at + 8));
                        }
                    }
                }
                offset += 2 + length;
            }
        } catch (IndexOutOfBoundsException | IllegalArgumentException ex) {
            throw MediaException.invalid("Metadata ảnh biên bản không hợp lệ");
        }
        return 1;
    }

    private static BufferedImage applyOrientation(BufferedImage image, int orientation) {
        if (orientation < 2 || orientation > 8) {
            return image;
        }
        int w = image.getWidth();
        int h = image.getHeight();
        BufferedImage result = new BufferedImage(
                orientation >= 5 ? h : w,
                orientation >= 5 ? w : h,
                BufferedImage.TYPE_INT_ARGB
        );
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int dx = switch (orientation) {
                    case 2, 3 -> w - 1 - x;
                    case 5, 8 -> y;
                    case 6, 7 -> h - 1 - y;
                    default -> x;
                };
                int dy = switch (orientation) {
                    case 3, 4 -> h - 1 - y;
                    case 5, 6 -> x;
                    case 7, 8 -> w - 1 - x;
                    default -> y;
                };
                result.setRGB(dx, dy, image.getRGB(x, y));
            }
        }
        return result;
    }

    private static boolean isAllowedMime(String contentType, String... expectedMimes) {
        if (contentType == null || contentType.isBlank() || "application/octet-stream".equals(contentType)) {
            return true;
        }
        for (String expected : expectedMimes) {
            if (expected.equals(contentType)) {
                return true;
            }
        }
        return false;
    }

    private static String sanitizeFilename(String value) {
        if (value == null) {
            return "evidence";
        }
        String name = value.replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1).replaceAll("[\\p{Cntrl}]", "").trim();
        if (name.isBlank() || name.length() > 255) {
            throw MediaException.invalid("Tên tệp biên bản không hợp lệ");
        }
        return name;
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static String normalizeBlank(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private record ValidatedEvidence(
            byte[] bytes,
            String mimeType,
            String extension,
            String originalFilename,
            String checksum
    ) {}
}
