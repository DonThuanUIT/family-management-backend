package com.familyfund.contribution.internal.service;

import com.familyfund.contribution.internal.dto.response.ElectronicReceiptResponse;
import com.familyfund.contribution.internal.dto.response.InKindElectronicReceiptResponse;
import com.familyfund.contribution.internal.dto.response.MemberAllocationResponse;
import com.lowagie.text.*;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Service generating standardized electronic receipt PDFs using OpenPDF (US-49).
 */
@Slf4j
@Service
public class ReceiptPdfService {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss")
            .withZone(ZoneId.of("Asia/Ho_Chi_Minh"));
    private static final DecimalFormat CURRENCY_FORMATTER = new DecimalFormat("#,##0");

    private BaseFont getVietnameseBaseFont() {
        String[] candidatePaths = {
                "C:\\Windows\\Fonts\\arial.ttf",
                "C:\\Windows\\Fonts\\times.ttf",
                "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf",
                "/usr/share/fonts/TTF/DejaVuSans.ttf",
                "/Library/Fonts/Arial.ttf"
        };

        for (String path : candidatePaths) {
            try {
                if (new File(path).exists()) {
                    return BaseFont.createFont(path, BaseFont.IDENTITY_H, BaseFont.EMBEDDED);
                }
            } catch (Exception e) {
                log.debug("Could not load font from {}: {}", path, e.getMessage());
            }
        }

        try {
            return BaseFont.createFont(BaseFont.HELVETICA, BaseFont.CP1252, BaseFont.NOT_EMBEDDED);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to initialize PDF fallback font", e);
        }
    }

    public byte[] generateCashReceiptPdf(ElectronicReceiptResponse receipt, String clanName) {
        return buildReceiptPdf(
                receipt.receiptNumber(),
                clanName,
                receipt.eventName(),
                receipt.payerMemberName(),
                receipt.payerMemberId(),
                receipt.receiverAccountId(),
                "Tiền mặt / Chuyển khoản (" + (receipt.paymentMethod() != null ? receipt.paymentMethod().name() : "Cash") + ")",
                receipt.amount(),
                receipt.amountInWords(),
                null, // No in-kind details
                receipt.contributionDate(),
                receipt.allocations()
        );
    }

    public byte[] generateInKindReceiptPdf(InKindElectronicReceiptResponse receipt, String clanName) {
        String inKindDesc = String.format("%s - %s %s (Quy đổi: %s)",
                receipt.itemName(),
                receipt.quantity() != null ? receipt.quantity().stripTrailingZeros().toPlainString() : "1",
                receipt.unit() != null ? receipt.unit() : "món",
                receipt.conversionMethod() != null ? receipt.conversionMethod().name() : "N/A"
        );

        return buildReceiptPdf(
                receipt.receiptNumber(),
                clanName,
                receipt.eventName(),
                receipt.payerMemberName(),
                receipt.payerMemberId(),
                receipt.receiverAccountId(),
                "Hiện vật cúng tiến",
                receipt.convertedMonetaryValue(),
                receipt.convertedMonetaryValueInWords(),
                inKindDesc,
                receipt.contributionDate(),
                receipt.allocations()
        );
    }

    private byte[] buildReceiptPdf(
            String receiptNumber,
            String clanName,
            String eventName,
            String payerName,
            String payerMemberId,
            String receiverAccountId,
            String contributionCategory,
            BigDecimal amount,
            String amountInWords,
            String inKindDetails,
            Instant contributionDate,
            List<MemberAllocationResponse> allocations
    ) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Document document = new Document(PageSize.A5, 30, 30, 25, 25);
            PdfWriter.getInstance(document, out);
            document.open();

            BaseFont baseFont = getVietnameseBaseFont();
            Font titleFont = new Font(baseFont, 14, Font.BOLD, new Color(139, 0, 0));
            Font subTitleFont = new Font(baseFont, 9, Font.NORMAL, Color.DARK_GRAY);
            Font sectionFont = new Font(baseFont, 10, Font.BOLD, Color.BLACK);
            Font regularFont = new Font(baseFont, 9, Font.NORMAL, Color.BLACK);
            Font boldFont = new Font(baseFont, 9, Font.BOLD, Color.BLACK);
            Font italicFont = new Font(baseFont, 8, Font.ITALIC, Color.GRAY);

            // Clan Header
            String displayClan = (clanName != null && !clanName.isBlank()) ? clanName.toUpperCase() : "DÒNG HỌ GIA TỘC";
            Paragraph clanHeader = new Paragraph(displayClan, titleFont);
            clanHeader.setAlignment(Element.ALIGN_CENTER);
            document.add(clanHeader);

            Paragraph nationalMotto = new Paragraph("CỘNG HÒA XÃ HỘI CHỦ NGHĨA VIỆT NAM\nĐộc lập - Tự do - Hạnh phúc", subTitleFont);
            nationalMotto.setAlignment(Element.ALIGN_CENTER);
            document.add(nationalMotto);

            document.add(new Paragraph("----------------------------------------", subTitleFont));

            // Receipt Title
            Paragraph receiptTitle = new Paragraph("PHIẾU THU ĐIỆN TỬ", titleFont);
            receiptTitle.setAlignment(Element.ALIGN_CENTER);
            receiptTitle.setSpacingBefore(5);
            receiptTitle.setSpacingAfter(3);
            document.add(receiptTitle);

            Paragraph codePara = new Paragraph("Mã phiếu thu: " + receiptNumber, boldFont);
            codePara.setAlignment(Element.ALIGN_CENTER);
            codePara.setSpacingAfter(10);
            document.add(codePara);

            // Details Table
            PdfPTable table = new PdfPTable(2);
            table.setWidthPercentage(100);
            table.setWidths(new float[]{35f, 65f});

            addTableRow(table, "Sự kiện:", eventName != null ? eventName : "Sự kiện chung", boldFont, regularFont);
            addTableRow(table, "Người nộp:", (payerName != null ? payerName : "N/A") + " (Mã: " + payerMemberId + ")", boldFont, regularFont);
            addTableRow(table, "Hình thức:", contributionCategory, boldFont, regularFont);

            if (inKindDetails != null) {
                addTableRow(table, "Chi tiết hiện vật:", inKindDetails, boldFont, regularFont);
            }

            String formattedAmount = (amount != null ? CURRENCY_FORMATTER.format(amount) : "0") + " VNĐ";
            addTableRow(table, "Giá trị đóng góp:", formattedAmount, boldFont, boldFont);
            addTableRow(table, "Bằng chữ:", amountInWords != null ? amountInWords : "N/A", boldFont, regularFont);

            String formattedDate = contributionDate != null ? DATE_FORMATTER.format(contributionDate) : "N/A";
            addTableRow(table, "Thời gian ghi nhận:", formattedDate, boldFont, regularFont);
            addTableRow(table, "Thủ quỹ tiếp nhận:", receiverAccountId != null ? receiverAccountId : "Hệ thống", boldFont, regularFont);

            document.add(table);

            // Allocations (if any)
            if (allocations != null && !allocations.isEmpty()) {
                Paragraph allocTitle = new Paragraph("\nPhân bổ công đức thành viên trong gia đình:", sectionFont);
                allocTitle.setSpacingAfter(5);
                document.add(allocTitle);

                PdfPTable allocTable = new PdfPTable(3);
                allocTable.setWidthPercentage(100);
                allocTable.setWidths(new float[]{30f, 40f, 30f});

                addCellHeader(allocTable, "Mã TV", boldFont);
                addCellHeader(allocTable, "Họ và tên", boldFont);
                addCellHeader(allocTable, "Số tiền phân bổ", boldFont);

                for (MemberAllocationResponse alloc : allocations) {
                    addCell(allocTable, alloc.memberId(), regularFont);
                    addCell(allocTable, alloc.memberName() != null ? alloc.memberName() : "N/A", regularFont);
                    addCell(allocTable, (alloc.recordedAmount() != null ? CURRENCY_FORMATTER.format(alloc.recordedAmount()) : "0") + " VNĐ", regularFont);
                }
                document.add(allocTable);
            }

            // Signature block
            Paragraph signPara = new Paragraph("\nNgày cấp phiếu: " + DATE_FORMATTER.format(Instant.now()), italicFont);
            signPara.setAlignment(Element.ALIGN_RIGHT);
            document.add(signPara);

            Paragraph footerThanks = new Paragraph("\nBan Quản lý Dòng họ xin chân thành cảm tạ tấm lòng công đức của quý thành viên!", italicFont);
            footerThanks.setAlignment(Element.ALIGN_CENTER);
            document.add(footerThanks);

            document.close();
            return out.toByteArray();
        } catch (Exception e) {
            log.error("Failed to generate receipt PDF for receipt number {}: {}", receiptNumber, e.getMessage(), e);
            throw new RuntimeException("Error rendering electronic receipt PDF", e);
        }
    }

    private void addTableRow(PdfPTable table, String label, String value, Font labelFont, Font valueFont) {
        PdfPCell cell1 = new PdfPCell(new Phrase(label, labelFont));
        cell1.setBorder(Rectangle.NO_BORDER);
        cell1.setPadding(3);
        table.addCell(cell1);

        PdfPCell cell2 = new PdfPCell(new Phrase(value, valueFont));
        cell2.setBorder(Rectangle.NO_BORDER);
        cell2.setPadding(3);
        table.addCell(cell2);
    }

    private void addCellHeader(PdfPTable table, String text, Font font) {
        PdfPCell cell = new PdfPCell(new Phrase(text, font));
        cell.setBackgroundColor(new Color(240, 240, 240));
        cell.setPadding(4);
        table.addCell(cell);
    }

    private void addCell(PdfPTable table, String text, Font font) {
        PdfPCell cell = new PdfPCell(new Phrase(text, font));
        cell.setPadding(4);
        table.addCell(cell);
    }
}
