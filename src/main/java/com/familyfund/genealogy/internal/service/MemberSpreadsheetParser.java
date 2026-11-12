package com.familyfund.genealogy.internal.service;

import com.familyfund.core.exception.BadRequestException;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.poi.openxml4j.util.ZipSecureFile;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Bộ đọc và chuẩn hóa file Excel (.xlsx) và CSV (.csv) cho tính năng nhập hàng loạt thành viên (US-31).
 */
@Component
public class MemberSpreadsheetParser {

    public static final long MAX_FILE_SIZE_BYTES = 5L * 1024L * 1024L;
    public static final int MAX_DATA_ROWS = 1000;

    static final String COL_MEMBER_ID = "member_id";
    static final String COL_FULL_NAME = "full_name";
    static final String COL_DATE_OF_BIRTH = "date_of_birth";
    static final String COL_GENDER = "gender";
    static final String COL_GENERATION = "generation";
    static final String COL_BRANCH_ID = "branch_id";
    static final String COL_STATUS = "status";
    static final String COL_DEATH_DATE = "death_date";
    static final String COL_BURIAL_PLACE = "burial_place";

    private static final Set<String> REQUIRED_HEADERS = Set.of(
            COL_FULL_NAME,
            COL_GENDER,
            COL_GENERATION,
            COL_BRANCH_ID
    );

    public List<ParsedMemberRow> parse(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("File tải lên không được rỗng");
        }
        if (file.getSize() > MAX_FILE_SIZE_BYTES) {
            throw new BadRequestException("Dung lượng file vượt quá giới hạn tối đa 5 MB");
        }

        String originalName = file.getOriginalFilename();
        String lowerName = originalName == null ? "" : originalName.trim().toLowerCase(Locale.ROOT);

        if (lowerName.endsWith(".xlsx")) {
            return parseXlsx(file);
        }
        if (lowerName.endsWith(".csv")) {
            return parseCsv(file);
        }
        throw new BadRequestException("Định dạng file không được hỗ trợ. Vui lòng sử dụng file .xlsx hoặc .csv");
    }

    private List<ParsedMemberRow> parseXlsx(MultipartFile file) {
        ZipSecureFile.setMinInflateRatio(0.01);
        try (InputStream is = file.getInputStream(); Workbook workbook = new XSSFWorkbook(is)) {
            if (workbook.getNumberOfSheets() == 0) {
                throw new BadRequestException("File Excel không có sheet dữ liệu nào");
            }
            Sheet sheet = workbook.getSheetAt(0);
            int firstRowNum = sheet.getFirstRowNum();
            Row headerRow = sheet.getRow(firstRowNum);
            if (headerRow == null) {
                throw new BadRequestException("File Excel thiếu hàng tiêu đề (header)");
            }

            DataFormatter formatter = new DataFormatter(Locale.ROOT);
            Map<String, Integer> headerIndexMap = new HashMap<>();
            for (int c = headerRow.getFirstCellNum(); c < headerRow.getLastCellNum(); c++) {
                if (c < 0) {
                    continue;
                }
                Cell cell = headerRow.getCell(c);
                String headerName = normalizeHeader(formatter.formatCellValue(cell));
                if (!headerName.isEmpty()) {
                    headerIndexMap.putIfAbsent(headerName, c);
                }
            }
            validateRequiredHeaders(headerIndexMap.keySet());

            List<ParsedMemberRow> rows = new ArrayList<>();
            int lastRowNum = sheet.getLastRowNum();
            for (int r = firstRowNum + 1; r <= lastRowNum; r++) {
                Row row = sheet.getRow(r);
                if (row == null || isRowBlank(row, formatter)) {
                    continue;
                }
                if (rows.size() >= MAX_DATA_ROWS) {
                    throw new BadRequestException("Số lượng dòng dữ liệu vượt quá giới hạn tối đa " + MAX_DATA_ROWS + " dòng mỗi lần nhập");
                }

                Cell dobCell = getCell(row, headerIndexMap.get(COL_DATE_OF_BIRTH));
                Cell deathCell = getCell(row, headerIndexMap.get(COL_DEATH_DATE));

                rows.add(new ParsedMemberRow(
                        r + 1,
                        readCellString(row, headerIndexMap.get(COL_MEMBER_ID), formatter),
                        readCellString(row, headerIndexMap.get(COL_FULL_NAME), formatter),
                        readCellString(row, headerIndexMap.get(COL_DATE_OF_BIRTH), formatter),
                        extractNativeExcelDate(dobCell),
                        readCellString(row, headerIndexMap.get(COL_GENDER), formatter),
                        readCellString(row, headerIndexMap.get(COL_GENERATION), formatter),
                        readCellString(row, headerIndexMap.get(COL_BRANCH_ID), formatter),
                        readCellString(row, headerIndexMap.get(COL_STATUS), formatter),
                        readCellString(row, headerIndexMap.get(COL_DEATH_DATE), formatter),
                        extractNativeExcelDate(deathCell),
                        readCellString(row, headerIndexMap.get(COL_BURIAL_PLACE), formatter)
                ));
            }
            if (rows.isEmpty()) {
                throw new BadRequestException("File Excel không có dòng dữ liệu thành viên nào");
            }
            return rows;
        } catch (BadRequestException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BadRequestException("Không thể đọc file Excel (.xlsx): " + ex.getMessage());
        }
    }

    private List<ParsedMemberRow> parseCsv(MultipartFile file) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            reader.mark(4);
            int firstChar = reader.read();
            if (firstChar != 0xFEFF && firstChar != -1) {
                reader.reset();
            }

            CSVFormat format = CSVFormat.DEFAULT.builder()
                    .setHeader()
                    .setSkipHeaderRecord(true)
                    .setTrim(true)
                    .setIgnoreEmptyLines(true)
                    .get();

            try (CSVParser csvParser = format.parse(reader)) {
                Map<String, Integer> rawHeaders = csvParser.getHeaderMap();
                if (rawHeaders == null || rawHeaders.isEmpty()) {
                    throw new BadRequestException("File CSV thiếu hàng tiêu đề (header)");
                }
                Map<String, String> normalizedToOriginalHeader = new HashMap<>();
                for (String key : rawHeaders.keySet()) {
                    String normalized = normalizeHeader(key);
                    if (!normalized.isEmpty()) {
                        normalizedToOriginalHeader.putIfAbsent(normalized, key);
                    }
                }
                validateRequiredHeaders(normalizedToOriginalHeader.keySet());

                List<ParsedMemberRow> rows = new ArrayList<>();
                for (CSVRecord record : csvParser) {
                    if (isCsvRecordBlank(record)) {
                        continue;
                    }
                    if (rows.size() >= MAX_DATA_ROWS) {
                        throw new BadRequestException("Số lượng dòng dữ liệu vượt quá giới hạn tối đa " + MAX_DATA_ROWS + " dòng mỗi lần nhập");
                    }

                    rows.add(new ParsedMemberRow(
                            (int) record.getRecordNumber() + 1,
                            readCsvField(record, normalizedToOriginalHeader.get(COL_MEMBER_ID)),
                            readCsvField(record, normalizedToOriginalHeader.get(COL_FULL_NAME)),
                            readCsvField(record, normalizedToOriginalHeader.get(COL_DATE_OF_BIRTH)),
                            null,
                            readCsvField(record, normalizedToOriginalHeader.get(COL_GENDER)),
                            readCsvField(record, normalizedToOriginalHeader.get(COL_GENERATION)),
                            readCsvField(record, normalizedToOriginalHeader.get(COL_BRANCH_ID)),
                            readCsvField(record, normalizedToOriginalHeader.get(COL_STATUS)),
                            readCsvField(record, normalizedToOriginalHeader.get(COL_DEATH_DATE)),
                            null,
                            readCsvField(record, normalizedToOriginalHeader.get(COL_BURIAL_PLACE))
                    ));
                }
                if (rows.isEmpty()) {
                    throw new BadRequestException("File CSV không có dòng dữ liệu thành viên nào");
                }
                return rows;
            }
        } catch (BadRequestException ex) {
            throw ex;
        } catch (IOException ex) {
            throw new BadRequestException("Không thể đọc file CSV: " + ex.getMessage());
        }
    }

    private static void validateRequiredHeaders(Set<String> presentHeaders) {
        List<String> missing = REQUIRED_HEADERS.stream()
                .filter(req -> !presentHeaders.contains(req))
                .sorted()
                .toList();
        if (!missing.isEmpty()) {
            throw new BadRequestException("File thiếu cột tiêu đề bắt buộc: " + String.join(", ", missing));
        }
    }

    private static String normalizeHeader(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.replace("\uFEFF", "").trim().toLowerCase(Locale.ROOT);
    }

    private static boolean isRowBlank(Row row, DataFormatter formatter) {
        for (int c = row.getFirstCellNum(); c < row.getLastCellNum(); c++) {
            if (c < 0) {
                continue;
            }
            String value = formatter.formatCellValue(row.getCell(c));
            if (value != null && !value.trim().isEmpty()) {
                return false;
            }
        }
        return true;
    }

    private static boolean isCsvRecordBlank(CSVRecord record) {
        for (String value : record) {
            if (value != null && !value.trim().isEmpty()) {
                return false;
            }
        }
        return true;
    }

    private static Cell getCell(Row row, Integer colIndex) {
        if (colIndex == null || colIndex < 0) {
            return null;
        }
        return row.getCell(colIndex);
    }

    private static String readCellString(Row row, Integer colIndex, DataFormatter formatter) {
        Cell cell = getCell(row, colIndex);
        if (cell == null) {
            return null;
        }
        String formatted = formatter.formatCellValue(cell);
        return formatted == null ? null : trimToNull(formatted);
    }

    private static LocalDate extractNativeExcelDate(Cell cell) {
        if (cell == null) {
            return null;
        }
        if (cell.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)) {
            return cell.getLocalDateTimeCellValue().toLocalDate();
        }
        return null;
    }

    private static String readCsvField(CSVRecord record, String headerName) {
        if (headerName == null || !record.isMapped(headerName)) {
            return null;
        }
        return trimToNull(record.get(headerName));
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
