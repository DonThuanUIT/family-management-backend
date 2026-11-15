package com.familyfund.event.internal.service;

import com.familyfund.event.internal.dto.response.EventLogisticsSummaryResponse;
import com.familyfund.event.internal.dto.response.EventLogisticsSummaryResponse.HouseholdLogisticsItem;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Generates attendance & banquet allocation exports (.xlsx and .csv) for Event Organizers (US-42).
 */
@Service
public class EventLogisticsExportService {

    private static final DateTimeFormatter DATE_TIME_FMT =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withZone(ZoneId.of("Asia/Ho_Chi_Minh"));

    public byte[] exportLogisticsReport(EventLogisticsSummaryResponse summary, String format) {
        if ("csv".equalsIgnoreCase(format)) {
            return exportCsv(summary);
        }
        return exportExcel(summary);
    }

    private byte[] exportExcel(EventLogisticsSummaryResponse summary) {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("DiemDanh_ChiaCo");

            Font titleFont = workbook.createFont();
            titleFont.setBold(true);
            titleFont.setFontHeightInPoints((short) 14);
            CellStyle titleStyle = workbook.createCellStyle();
            titleStyle.setFont(titleFont);

            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerFont.setColor(IndexedColors.WHITE.getIndex());
            CellStyle headerStyle = workbook.createCellStyle();
            headerStyle.setFont(headerFont);
            headerStyle.setFillForegroundColor(IndexedColors.DARK_RED.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            headerStyle.setAlignment(HorizontalAlignment.CENTER);
            headerStyle.setBorderBottom(BorderStyle.THIN);
            headerStyle.setBorderTop(BorderStyle.THIN);
            headerStyle.setBorderLeft(BorderStyle.THIN);
            headerStyle.setBorderRight(BorderStyle.THIN);

            CellStyle boldLabelStyle = workbook.createCellStyle();
            Font boldFont = workbook.createFont();
            boldFont.setBold(true);
            boldLabelStyle.setFont(boldFont);

            CellStyle dataStyle = workbook.createCellStyle();
            dataStyle.setBorderBottom(BorderStyle.THIN);
            dataStyle.setBorderTop(BorderStyle.THIN);
            dataStyle.setBorderLeft(BorderStyle.THIN);
            dataStyle.setBorderRight(BorderStyle.THIN);
            dataStyle.setVerticalAlignment(VerticalAlignment.CENTER);

            int rowIdx = 0;
            Row titleRow = sheet.createRow(rowIdx++);
            Cell titleCell = titleRow.createCell(0);
            titleCell.setCellValue("DANH SÁCH ĐIỂM DANH & CHIA CỖ - " + safe(summary.eventName()));
            titleCell.setCellStyle(titleStyle);
            sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, 10));

            Row filterRow = sheet.createRow(rowIdx++);
            filterRow.createCell(0).setCellValue("Phạm vi nhánh:");
            filterRow.getCell(0).setCellStyle(boldLabelStyle);
            filterRow.createCell(1).setCellValue(
                    summary.branchName() != null && !summary.branchName().isBlank()
                            ? summary.branchName()
                            : "Tất cả chi nhánh"
            );

            rowIdx++;
            Row kpiHeader = sheet.createRow(rowIdx++);
            String[] kpiLabels = {"Tổng hộ GĐ", "Tổng cá nhân", "Suất cỗ mặn", "Suất cỗ chay", "Chỗ xe đưa đón"};
            for (int i = 0; i < kpiLabels.length; i++) {
                Cell c = kpiHeader.createCell(i);
                c.setCellValue(kpiLabels[i]);
                c.setCellStyle(boldLabelStyle);
            }
            Row kpiValues = sheet.createRow(rowIdx++);
            kpiValues.createCell(0).setCellValue(summary.totalRegistrations());
            kpiValues.createCell(1).setCellValue(summary.totalAttendees());
            kpiValues.createCell(2).setCellValue(summary.totalSavoryMeals());
            kpiValues.createCell(3).setCellValue(summary.totalVegetarianMeals());
            kpiValues.createCell(4).setCellValue(summary.totalShuttleSeats());

            rowIdx++;
            String[] headers = {
                    "STT", "Mã ĐK", "Đại diện hộ", "Chi / Nhánh",
                    "Số người", "Cỗ mặn", "Cỗ chay", "Chỗ xe đưa đón",
                    "Ghi chú ăn uống", "Ghi chú phương tiện", "Điểm danh"
            };
            Row tableHeader = sheet.createRow(rowIdx++);
            for (int i = 0; i < headers.length; i++) {
                Cell cell = tableHeader.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            List<HouseholdLogisticsItem> items = summary.registrations();
            for (int i = 0; i < items.size(); i++) {
                HouseholdLogisticsItem item = items.get(i);
                Row row = sheet.createRow(rowIdx++);
                createCell(row, 0, String.valueOf(i + 1), dataStyle);
                createCell(row, 1, safe(item.registrationId()), dataStyle);
                createCell(row, 2, safe(item.representativeName()), dataStyle);
                createCell(row, 3, safe(item.branchName()), dataStyle);
                createNumericCell(row, 4, item.totalAttendees(), dataStyle);
                createNumericCell(row, 5, item.savoryMeals(), dataStyle);
                createNumericCell(row, 6, item.vegetarianMeals(), dataStyle);
                createNumericCell(row, 7, item.shuttleSeats(), dataStyle);
                createCell(row, 8, safe(item.dietaryRequirements()), dataStyle);
                createCell(row, 9, safe(item.transportationNotes()), dataStyle);
                createCell(row, 10, "[  ]", dataStyle);
            }

            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
                int currentWidth = sheet.getColumnWidth(i);
                sheet.setColumnWidth(i, Math.min(currentWidth + 768, 12000));
            }

            workbook.write(out);
            return out.toByteArray();
        } catch (IOException ex) {
            throw new IllegalStateException("Không thể tạo file Excel danh sách hậu cần sự kiện", ex);
        }
    }

    private byte[] exportCsv(EventLogisticsSummaryResponse summary) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            // Write UTF-8 BOM so Microsoft Excel renders Vietnamese characters properly
            out.write(new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF});
            try (OutputStreamWriter writer = new OutputStreamWriter(out, StandardCharsets.UTF_8);
                 CSVPrinter printer = new CSVPrinter(writer, CSVFormat.DEFAULT.builder()
                         .setHeader(
                                 "STT", "Mã ĐK", "Đại diện hộ", "Chi / Nhánh",
                                 "Số người", "Cỗ mặn", "Cỗ chay", "Chỗ xe đưa đón",
                                 "Ghi chú ăn uống", "Ghi chú phương tiện", "Trạng thái", "Ngày đăng ký"
                         ).build())) {
                List<HouseholdLogisticsItem> items = summary.registrations();
                for (int i = 0; i < items.size(); i++) {
                    HouseholdLogisticsItem item = items.get(i);
                    printer.printRecord(
                            i + 1,
                            safe(item.registrationId()),
                            safe(item.representativeName()),
                            safe(item.branchName()),
                            item.totalAttendees(),
                            item.savoryMeals(),
                            item.vegetarianMeals(),
                            item.shuttleSeats(),
                            safe(item.dietaryRequirements()),
                            safe(item.transportationNotes()),
                            item.status() != null ? item.status().name() : "",
                            item.registrationDate() != null ? DATE_TIME_FMT.format(item.registrationDate()) : ""
                    );
                }
                printer.flush();
            }
            return out.toByteArray();
        } catch (IOException ex) {
            throw new IllegalStateException("Không thể tạo file CSV danh sách hậu cần sự kiện", ex);
        }
    }

    private static void createCell(Row row, int col, String value, CellStyle style) {
        Cell cell = row.createCell(col);
        cell.setCellValue(value);
        cell.setCellStyle(style);
    }

    private static void createNumericCell(Row row, int col, int value, CellStyle style) {
        Cell cell = row.createCell(col);
        cell.setCellValue(value);
        cell.setCellStyle(style);
    }

    private static String safe(String value) {
        return value != null ? value : "";
    }
}
