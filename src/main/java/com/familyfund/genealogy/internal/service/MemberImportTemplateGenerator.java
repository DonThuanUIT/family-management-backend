package com.familyfund.genealogy.internal.service;

import com.familyfund.genealogy.internal.entity.FamilyBranch;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

/**
 * Tạo file Excel mẫu (.xlsx) chuẩn cấu trúc nhập hàng loạt thành viên (US-31).
 */
@Component
public class MemberImportTemplateGenerator {

    private static final String[] HEADERS = {
            MemberSpreadsheetParser.COL_MEMBER_ID,
            MemberSpreadsheetParser.COL_FULL_NAME,
            MemberSpreadsheetParser.COL_DATE_OF_BIRTH,
            MemberSpreadsheetParser.COL_GENDER,
            MemberSpreadsheetParser.COL_GENERATION,
            MemberSpreadsheetParser.COL_BRANCH_ID,
            MemberSpreadsheetParser.COL_STATUS,
            MemberSpreadsheetParser.COL_DEATH_DATE,
            MemberSpreadsheetParser.COL_BURIAL_PLACE
    };

    public byte[] generateTemplate(List<FamilyBranch> availableBranches) {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            CellStyle headerStyle = createHeaderStyle(workbook);

            Sheet dataSheet = workbook.createSheet("Members");
            Row headerRow = dataSheet.createRow(0);
            for (int i = 0; i < HEADERS.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(HEADERS[i]);
                cell.setCellStyle(headerStyle);
                dataSheet.setColumnWidth(i, 22 * 256);
            }

            String sampleBranchId = (availableBranches != null && !availableBranches.isEmpty())
                    ? availableBranches.get(0).getBranchId()
                    : "BR_ROOT";
            int sampleGeneration = (availableBranches != null && !availableBranches.isEmpty())
                    ? availableBranches.get(0).getGenerationLevel()
                    : 1;

            writeSampleRow(dataSheet, 1, new String[]{
                    "", "Nguyễn Văn Mẫu", "1990-05-15", "Male",
                    String.valueOf(sampleGeneration), sampleBranchId, "Living", "", ""
            });
            writeSampleRow(dataSheet, 2, new String[]{
                    "", "Nguyễn Thị Hương", "10/08/1955", "Female",
                    String.valueOf(sampleGeneration), sampleBranchId, "Deceased", "24/03/2020", "Nghĩa trang quê nhà"
            });

            Sheet guideSheet = workbook.createSheet("HuongDan");
            Row guideHeader = guideSheet.createRow(0);
            String[] guideCols = {"Tên cột", "Bắt buộc", "Định dạng / Giá trị hợp lệ", "Ghi chú"};
            for (int i = 0; i < guideCols.length; i++) {
                Cell cell = guideHeader.createCell(i);
                cell.setCellValue(guideCols[i]);
                cell.setCellStyle(headerStyle);
                guideSheet.setColumnWidth(i, 30 * 256);
            }

            String[][] guideRows = {
                    {"member_id", "Không", "Tối đa 20 ký tự, không khoảng trắng", "Để trống để hệ thống tự sinh mã MEM...; nếu nhập phải không trùng"},
                    {"full_name", "Có", "Chuỗi tối đa 100 ký tự", "Họ và tên đầy đủ"},
                    {"date_of_birth", "Không", "yyyy-MM-dd hoặc dd/MM/yyyy", "Không lớn hơn ngày hiện tại"},
                    {"gender", "Có", "Male / Female / Other (hoặc Nam / Nữ / Khác)", "Giới tính thành viên"},
                    {"generation", "Có", "Số nguyên >= 1", "Phải >= đời gốc của nhánh (generation_level)"},
                    {"branch_id", "Có", "Mã nhánh hợp lệ trong dòng họ", "Xem danh sách mã nhánh bên dưới"},
                    {"status", "Không", "Living / Deceased (mặc định Living)", "Tình trạng sống hoặc đã mất"},
                    {"death_date", "Khi Deceased", "yyyy-MM-dd hoặc dd/MM/yyyy", "Tự động tính ngày giỗ âm lịch khi status = Deceased"},
                    {"burial_place", "Không", "Tối đa 255 ký tự", "Nơi an táng (nếu đã mất)"}
            };

            int rowIdx = 1;
            for (String[] g : guideRows) {
                writeSampleRow(guideSheet, rowIdx++, g);
            }

            if (availableBranches != null && !availableBranches.isEmpty()) {
                rowIdx++;
                Row branchSection = guideSheet.createRow(rowIdx++);
                branchSection.createCell(0).setCellValue("Danh sách mã nhánh hiện có của dòng họ:");

                Row branchHeader = guideSheet.createRow(rowIdx++);
                String[] branchCols = {"branch_id", "Tên nhánh", "Đời gốc (generation_level)"};
                for (int i = 0; i < branchCols.length; i++) {
                    Cell cell = branchHeader.createCell(i);
                    cell.setCellValue(branchCols[i]);
                    cell.setCellStyle(headerStyle);
                }

                for (FamilyBranch branch : availableBranches) {
                    writeSampleRow(guideSheet, rowIdx++, new String[]{
                            branch.getBranchId(),
                            branch.getBranchName(),
                            String.valueOf(branch.getGenerationLevel())
                    });
                }
            }

            workbook.write(out);
            return out.toByteArray();
        } catch (IOException ex) {
            throw new IllegalStateException("Không thể tạo file Excel mẫu", ex);
        }
    }

    private static CellStyle createHeaderStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        return style;
    }

    private static void writeSampleRow(Sheet sheet, int rowIndex, String[] values) {
        Row row = sheet.createRow(rowIndex);
        for (int i = 0; i < values.length; i++) {
            row.createCell(i).setCellValue(values[i] != null ? values[i] : "");
        }
    }
}
