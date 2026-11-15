package com.familyfund.genealogy.internal.service;

import com.familyfund.core.exception.BadRequestException;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CreationHelper;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("US-31: MemberSpreadsheetParser Unit Tests")
class MemberSpreadsheetParserTest {

    private final MemberSpreadsheetParser parser = new MemberSpreadsheetParser();

    @Test
    @DisplayName("Đọc file .xlsx hợp lệ gồm ngày kiểu native Excel và bỏ qua dòng trống")
    void parse_validXlsx_returnsParsedRows() throws Exception {
        byte[] bytes;
        try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet("Members");
            Row header = sheet.createRow(0);
            String[] cols = {"member_id", "full_name", "date_of_birth", "gender", "generation", "branch_id", "status", "death_date", "burial_place"};
            for (int i = 0; i < cols.length; i++) {
                header.createCell(i).setCellValue(cols[i]);
            }

            Row row1 = sheet.createRow(1);
            row1.createCell(0).setCellValue("MEM_01");
            row1.createCell(1).setCellValue("Nguyễn Văn An");
            Cell dobCell = row1.createCell(2);
            dobCell.setCellValue(LocalDate.of(1990, 5, 15));
            CellStyle dateStyle = wb.createCellStyle();
            CreationHelper ch = wb.getCreationHelper();
            dateStyle.setDataFormat(ch.createDataFormat().getFormat("yyyy-mm-dd"));
            dobCell.setCellStyle(dateStyle);
            row1.createCell(3).setCellValue("Male");
            row1.createCell(4).setCellValue(3);
            row1.createCell(5).setCellValue("BR_01");
            row1.createCell(6).setCellValue("Living");

            // Blank row at index 2 should be skipped
            sheet.createRow(2);

            Row row3 = sheet.createRow(3);
            row3.createCell(1).setCellValue("Nguyễn Thị Bình");
            row3.createCell(2).setCellValue("31/02/1988");
            row3.createCell(3).setCellValue("Female");
            row3.createCell(4).setCellValue(3);
            row3.createCell(5).setCellValue("");

            wb.write(out);
            bytes = out.toByteArray();
        }

        MockMultipartFile file = new MockMultipartFile(
                "file", "members.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                bytes);

        List<ParsedMemberRow> rows = parser.parse(file);
        assertThat(rows).hasSize(2);
        assertThat(rows.get(0).rowNumber()).isEqualTo(2);
        assertThat(rows.get(0).memberId()).isEqualTo("MEM_01");
        assertThat(rows.get(0).nativeDateOfBirth()).isEqualTo(LocalDate.of(1990, 5, 15));
        assertThat(rows.get(0).rawGeneration()).isEqualTo("3");
        assertThat(rows.get(1).rowNumber()).isEqualTo(4);
        assertThat(rows.get(1).rawDateOfBirth()).isEqualTo("31/02/1988");
        assertThat(rows.get(1).branchId()).isNull();
    }

    @Test
    @DisplayName("Đọc file .csv có UTF-8 BOM và dấu phẩy trong ngoặc kép")
    void parse_validCsvWithBom_returnsParsedRows() {
        String csv = "\uFEFFmember_id,full_name,date_of_birth,gender,generation,branch_id,status,death_date,burial_place\n"
                + ",\"Nguyễn Văn Cường\",20/04/1960,Male,2,BR_ROOT,Deceased,2020-01-10,\"Nghĩa trang, Hưng Yên\"\n";
        MockMultipartFile file = new MockMultipartFile(
                "file", "members.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8));

        List<ParsedMemberRow> rows = parser.parse(file);
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).fullName()).isEqualTo("Nguyễn Văn Cường");
        assertThat(rows.get(0).burialPlace()).isEqualTo("Nghĩa trang, Hưng Yên");
        assertThat(rows.get(0).rawDateOfBirth()).isEqualTo("20/04/1960");
    }

    @Test
    @DisplayName("Từ chối file sai định dạng hoặc thiếu cột bắt buộc")
    void parse_invalidFormatOrMissingHeader_throwsBadRequest() {
        MockMultipartFile txtFile = new MockMultipartFile("file", "members.txt", "text/plain", "hello".getBytes());
        assertThatThrownBy(() -> parser.parse(txtFile))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Định dạng file không được hỗ trợ");

        String missingBranchCsv = "full_name,gender,generation\nNguyen Van A,Male,1\n";
        MockMultipartFile csvFile = new MockMultipartFile(
                "file", "members.csv", "text/csv", missingBranchCsv.getBytes(StandardCharsets.UTF_8));
        assertThatThrownBy(() -> parser.parse(csvFile))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("branch_id");
    }
}
