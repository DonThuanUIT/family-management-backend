package com.familyfund.genealogy.internal.media;

import com.familyfund.core.storage.MediaException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.*;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.Semaphore;

@Component
public class MediaFileValidator {
    private final Semaphore processing = new Semaphore(2);
    public record Validated(byte[] bytes, String mimeType, String extension, String originalFilename, String checksum) {}
    public Validated validate(MultipartFile file, boolean avatar) {
        long max = (avatar ? 5L : 10L) * 1024 * 1024;
        if (file == null || file.isEmpty()) throw MediaException.invalid("Tệp không được để trống");
        if (file.getSize() > max) throw new MediaException(413, "FILE_TOO_LARGE", "Tệp vượt dung lượng cho phép");
        if (!processing.tryAcquire()) throw new MediaException(429, "UPLOAD_RATE_LIMITED", "Đang xử lý nhiều tệp, vui lòng thử lại");
        try {
            String name = filename(file.getOriginalFilename());
            String ext = name.substring(name.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
            String mime = file.getContentType();
            byte[] bytes;
            try (InputStream stream = file.getInputStream()) { bytes = stream.readNBytes((int) max + 1); }
            if (bytes.length > max) throw new MediaException(413, "FILE_TOO_LARGE", "Tệp vượt dung lượng cho phép");
            if (!avatar && ext.equals("pdf") && "application/pdf".equals(mime)) {
                validatePdf(bytes);
                return new Validated(bytes, mime, "pdf", name, sha256(bytes));
            }
            boolean jpeg = Set.of("jpg", "jpeg").contains(ext) && "image/jpeg".equals(mime)
                    && bytes.length >= 3 && bytes[0] == (byte) 0xff && bytes[1] == (byte) 0xd8 && bytes[2] == (byte) 0xff;
            boolean png = ext.equals("png") && "image/png".equals(mime) && bytes.length >= 8
                    && Arrays.equals(Arrays.copyOf(bytes, 8), new byte[]{(byte)137,80,78,71,13,10,26,10});
            if (!jpeg && !png) throw new MediaException(415, "UNSUPPORTED_MEDIA_TYPE", "Chỉ hỗ trợ JPEG, PNG và PDF cho tư liệu");
            try (var input = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
                var readers = ImageIO.getImageReaders(input);
                if (!readers.hasNext()) throw MediaException.invalid("Ảnh không hợp lệ");
                var reader = readers.next();
                try {
                    reader.setInput(input);
                    int width = reader.getWidth(0), height = reader.getHeight(0);
                    if (width < 1 || height < 1 || (long) width * height > 20_000_000
                            || (avatar && (width < 128 || height < 128 || width != height)))
                        throw MediaException.invalid("Ảnh đại diện cần vuông, tối thiểu 128 px; ảnh tối đa 20 megapixel");
                    BufferedImage original = orient(reader.read(0), jpeg ? orientation(bytes) : 1);
                    int w = avatar ? 512 : original.getWidth(), h = avatar ? 512 : original.getHeight();
                    BufferedImage clean = new BufferedImage(w, h, avatar || jpeg ? BufferedImage.TYPE_INT_RGB : BufferedImage.TYPE_INT_ARGB);
                    Graphics2D graphics = clean.createGraphics();
                    try {
                        if (avatar || jpeg) { graphics.setColor(Color.WHITE); graphics.fillRect(0, 0, w, h); }
                        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                        graphics.drawImage(original, 0, 0, w, h, null);
                    } finally { graphics.dispose(); }
                    var output = new ByteArrayOutputStream();
                    String outputExt = avatar || jpeg ? "jpg" : "png";
                    if (!ImageIO.write(clean, outputExt, output)) throw MediaException.invalid("Không thể xử lý ảnh");
                    byte[] result = output.toByteArray();
                    if (result.length > 10 * 1024 * 1024) throw new MediaException(413, "FILE_TOO_LARGE", "Ảnh sau xử lý vượt dung lượng cho phép");
                    return new Validated(result, outputExt.equals("jpg") ? "image/jpeg" : "image/png", outputExt, name, sha256(result));
                } finally { reader.dispose(); }
            }
        } catch (IOException | IllegalArgumentException ex) { throw MediaException.invalid("Tệp hỏng hoặc không thể xử lý"); }
        finally { processing.release(); }
    }
    public static String filename(String value) {
        if (value == null) return "document";
        String name = value.replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1).replaceAll("[\\p{Cntrl}]", "").trim();
        if (name.isBlank() || name.length() > 255) throw MediaException.invalid("Tên tệp không hợp lệ");
        return name;
    }
    public static String sha256(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (java.security.NoSuchAlgorithmException ex) { throw new IllegalStateException(ex); }
    }
    private void validatePdf(byte[] bytes) throws IOException {
        if (bytes.length < 10 || !new String(bytes, 0, 5, StandardCharsets.US_ASCII).equals("%PDF-")
                || !new String(bytes, Math.max(0, bytes.length - 1024), Math.min(1024, bytes.length), StandardCharsets.ISO_8859_1).contains("%%EOF"))
            throw MediaException.invalid("PDF không hợp lệ");
        try (var document = Loader.loadPDF(bytes)) {
            if (document.isEncrypted() || document.getNumberOfPages() < 1 || document.getNumberOfPages() > 500)
                throw MediaException.invalid("PDF mã hóa hoặc vượt giới hạn 500 trang");
            var visited = Collections.newSetFromMap(new IdentityHashMap<COSBase, Boolean>());
            Deque<COSBase> queue = new ArrayDeque<>();
            queue.add(document.getDocumentCatalog().getCOSObject());
            Set<String> forbidden = Set.of("JavaScript", "JS", "Launch", "EmbeddedFiles", "EmbeddedFile", "Filespec", "RichMedia", "XFA", "OpenAction", "AA", "SubmitForm", "ImportData", "GoToR", "GoToE", "Rendition", "Movie", "Sound");
            int nodes = 0;
            while (!queue.isEmpty()) {
                if (++nodes > 100_000) throw MediaException.invalid("Cấu trúc PDF quá phức tạp");
                COSBase node = queue.remove();
                if (!visited.add(node)) continue;
                if (node instanceof COSObject object) { if (object.getObject() != null) queue.add(object.getObject()); }
                else if (node instanceof COSDictionary dict) {
                    for (COSName key : dict.keySet()) {
                        if (forbidden.contains(key.getName())) throw MediaException.invalid("PDF chứa hành động hoặc tệp nhúng không được phép");
                        COSBase item = dict.getDictionaryObject(key);
                        if (item instanceof COSName name && forbidden.contains(name.getName())) throw MediaException.invalid("PDF chứa nội dung chủ động không được phép");
                        if (item != null) queue.add(item);
                    }
                } else if (node instanceof COSArray array) { for (COSBase item : array) if (item != null) queue.add(item); }
            }
        }
    }
    // Read only the EXIF orientation tag; never retain metadata in output images.
    private int orientation(byte[] bytes) {
        try {
            for (int offset = 2; offset + 4 < bytes.length;) {
                if ((bytes[offset] & 255) != 255) break;
                int marker = bytes[offset + 1] & 255;
                if (marker == 0xda || marker == 0xd9) break;
                int length = ((bytes[offset + 2] & 255) << 8) | (bytes[offset + 3] & 255);
                if (length < 2 || offset + 2 + length > bytes.length) break;
                int start = offset + 4;
                if (marker == 0xe1 && length > 16 && new String(bytes, start, 6, StandardCharsets.ISO_8859_1).equals("Exif\0\0")) {
                    ByteBuffer tiff = ByteBuffer.wrap(bytes, start + 6, length - 8).slice();
                    tiff.order(tiff.get(0) == 'I' && tiff.get(1) == 'I' ? ByteOrder.LITTLE_ENDIAN : ByteOrder.BIG_ENDIAN);
                    int ifd = tiff.getInt(4), count = Short.toUnsignedInt(tiff.getShort(ifd));
                    for (int i = 0; i < Math.min(count, 512); i++) {
                        int at = ifd + 2 + i * 12;
                        if (Short.toUnsignedInt(tiff.getShort(at)) == 0x112) return Short.toUnsignedInt(tiff.getShort(at + 8));
                    }
                }
                offset += 2 + length;
            }
        } catch (IndexOutOfBoundsException | IllegalArgumentException ex) { throw MediaException.invalid("Metadata ảnh không hợp lệ"); }
        return 1;
    }
    private BufferedImage orient(BufferedImage image, int orientation) {
        if (orientation < 2 || orientation > 8) return image;
        int w = image.getWidth(), h = image.getHeight();
        BufferedImage result = new BufferedImage(orientation >= 5 ? h : w, orientation >= 5 ? w : h, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
            int dx = switch (orientation) { case 2,3 -> w-1-x; case 5,8 -> y; case 6,7 -> h-1-y; default -> x; };
            int dy = switch (orientation) { case 3,4 -> h-1-y; case 5,6 -> x; case 7,8 -> w-1-x; default -> y; };
            result.setRGB(dx, dy, image.getRGB(x, y));
        }
        return result;
    }
}
