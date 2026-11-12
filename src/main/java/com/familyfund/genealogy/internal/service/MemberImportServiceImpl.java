package com.familyfund.genealogy.internal.service;

import com.familyfund.core.exception.ResourceNotFoundException;
import com.familyfund.core.security.CurrentUserProvider;
import com.familyfund.core.security.SystemRoles;
import com.familyfund.core.tenant.TenantContext;
import com.familyfund.genealogy.api.GenealogyQueryService;
import com.familyfund.genealogy.internal.dto.request.ConfirmMemberImportRequest;
import com.familyfund.genealogy.internal.dto.request.ImportMemberRowRequest;
import com.familyfund.genealogy.internal.dto.response.ImportMemberPreviewRow;
import com.familyfund.genealogy.internal.dto.response.ImportMembersConfirmResponse;
import com.familyfund.genealogy.internal.dto.response.ImportMembersPreviewResponse;
import com.familyfund.genealogy.internal.dto.response.ImportSkippedRow;
import com.familyfund.genealogy.internal.dto.response.MemberResponse;
import com.familyfund.genealogy.internal.entity.FamilyBranch;
import com.familyfund.genealogy.internal.entity.Member;
import com.familyfund.genealogy.internal.mapper.MemberMapper;
import com.familyfund.genealogy.internal.repository.ClanRepository;
import com.familyfund.genealogy.internal.repository.FamilyBranchRepository;
import com.familyfund.genealogy.internal.repository.MemberRepository;
import com.familyfund.genealogy.internal.security.MemberPrivacyPolicy;
import com.familyfund.genealogy.internal.security.PrivacyEvaluationContext;
import com.familyfund.genealogy.internal.util.MemberIdGenerator;
import com.familyfund.genealogy.internal.util.VietnameseLunarCalendarConverter;
import com.familyfund.genealogy.internal.util.VietnameseLunarDate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Triển khai nghiệp vụ tải mẫu, xem trước (preview) và xác nhận nhập hàng loạt thành viên từ Excel/CSV (US-31).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MemberImportServiceImpl implements MemberImportService {

    private static final int MAX_MEMBER_ID_LENGTH = 20;
    private static final int MAX_FULL_NAME_LENGTH = 100;
    private static final int MAX_BURIAL_PLACE_LENGTH = 255;
    private static final int MAX_ID_ATTEMPTS = 5;

    private static final DateTimeFormatter STRICT_ISO_DATE =
            DateTimeFormatter.ISO_LOCAL_DATE.withResolverStyle(ResolverStyle.STRICT);
    private static final DateTimeFormatter STRICT_VN_DATE =
            DateTimeFormatter.ofPattern("dd/MM/uuuu").withResolverStyle(ResolverStyle.STRICT);

    private final MemberSpreadsheetParser spreadsheetParser;
    private final MemberImportTemplateGenerator templateGenerator;
    private final ClanRepository clanRepository;
    private final FamilyBranchRepository branchRepository;
    private final MemberRepository memberRepository;
    private final MemberIdGenerator memberIdGenerator;
    private final CurrentUserProvider currentUserProvider;
    private final GenealogyQueryService genealogyQueryService;
    private final VietnameseLunarCalendarConverter lunarCalendarConverter;
    private final MemberMapper memberMapper;
    private final MemberPrivacyPolicy memberPrivacyPolicy;

    @Override
    @Transactional(readOnly = true)
    public byte[] generateTemplate() {
        String clanId = TenantContext.getClanId();
        List<FamilyBranch> allBranches = branchRepository.findByClanIdOrderByGenerationLevelAsc(clanId);
        List<FamilyBranch> scopedBranches = filterBranchesForCaller(clanId, allBranches);
        return templateGenerator.generateTemplate(scopedBranches);
    }

    @Override
    @Transactional(readOnly = true)
    public ImportMembersPreviewResponse previewImport(MultipartFile file) {
        String clanId = TenantContext.getClanId();
        List<ParsedMemberRow> parsedRows = spreadsheetParser.parse(file);

        List<String> candidateIds = parsedRows.stream()
                .map(ParsedMemberRow::memberId)
                .filter(id -> id != null && !id.isBlank())
                .map(String::trim)
                .distinct()
                .toList();

        ImportValidationContext ctx = buildValidationContext(clanId, candidateIds);
        List<ImportMemberPreviewRow> previewRows = new ArrayList<>(parsedRows.size());
        int validCount = 0;

        for (ParsedMemberRow raw : parsedRows) {
            ValidatedRow result = validateParsedRow(raw, ctx);
            if (result.errors().isEmpty()) {
                validCount++;
            }
            previewRows.add(ImportMemberPreviewRow.builder()
                    .rowNumber(raw.rowNumber())
                    .valid(result.errors().isEmpty())
                    .errors(result.errors())
                    .memberId(result.memberId())
                    .fullName(result.fullName())
                    .dateOfBirth(result.dateOfBirth())
                    .rawDateOfBirth(raw.rawDateOfBirth())
                    .gender(result.gender())
                    .generation(result.generation())
                    .branchId(result.branchId())
                    .branchName(result.branch() != null ? result.branch().getBranchName() : null)
                    .status(result.status())
                    .deathDate(result.deathDate())
                    .rawDeathDate(raw.rawDeathDate())
                    .deathDateLunar(result.deathDateLunar())
                    .burialPlace(result.burialPlace())
                    .build());
        }

        return ImportMembersPreviewResponse.builder()
                .fileName(file.getOriginalFilename())
                .totalRows(previewRows.size())
                .validCount(validCount)
                .errorCount(previewRows.size() - validCount)
                .rows(previewRows)
                .build();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ImportMembersConfirmResponse confirmImport(ConfirmMemberImportRequest request) {
        String clanId = TenantContext.getClanId();
        clanRepository.findByIdForUpdate(clanId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy dòng họ với mã: " + clanId));

        List<String> candidateIds = request.rows().stream()
                .map(ImportMemberRowRequest::memberId)
                .filter(id -> id != null && !id.isBlank())
                .map(String::trim)
                .distinct()
                .toList();

        ImportValidationContext ctx = buildValidationContext(clanId, candidateIds);
        List<Member> membersToSave = new ArrayList<>();
        List<ImportSkippedRow> skippedRows = new ArrayList<>();

        for (int i = 0; i < request.rows().size(); i++) {
            ImportMemberRowRequest rowReq = request.rows().get(i);
            int rowNum = rowReq.rowNumber() != null ? rowReq.rowNumber() : (i + 1);
            ParsedMemberRow normalized = new ParsedMemberRow(
                    rowNum,
                    trimToNull(rowReq.memberId()),
                    trimToNull(rowReq.fullName()),
                    rowReq.dateOfBirth() != null ? rowReq.dateOfBirth().toString() : null,
                    rowReq.dateOfBirth(),
                    trimToNull(rowReq.gender()),
                    rowReq.generation() != null ? String.valueOf(rowReq.generation()) : null,
                    trimToNull(rowReq.branchId()),
                    trimToNull(rowReq.status()),
                    rowReq.deathDate() != null ? rowReq.deathDate().toString() : null,
                    rowReq.deathDate(),
                    trimToNull(rowReq.burialPlace())
            );

            ValidatedRow validated = validateParsedRow(normalized, ctx);
            if (!validated.errors().isEmpty()) {
                skippedRows.add(ImportSkippedRow.builder()
                        .rowNumber(rowNum)
                        .fullName(normalized.fullName())
                        .errors(validated.errors())
                        .build());
                continue;
            }

            String assignedMemberId = validated.memberId() != null
                    ? validated.memberId()
                    : generateUniqueMemberId(ctx.existingMemberIds, ctx.seenMemberIdsInBatch);
            ctx.seenMemberIdsInBatch.add(assignedMemberId);

            membersToSave.add(Member.builder()
                    .memberId(assignedMemberId)
                    .fullName(validated.fullName())
                    .dateOfBirth(validated.dateOfBirth())
                    .gender(validated.gender())
                    .generation(validated.generation())
                    .status(validated.status())
                    .deathDate(validated.deathDate())
                    .deathDateLunar(validated.deathDateLunar())
                    .burialPlace(validated.burialPlace())
                    .branch(validated.branch())
                    .isPlaceholder(true)
                    .build());
        }

        List<Member> savedMembers = membersToSave.isEmpty()
                ? Collections.emptyList()
                : memberRepository.saveAllAndFlush(membersToSave);

        PrivacyEvaluationContext privacyCtx = buildPrivacyContext(clanId);
        List<MemberResponse> insertedResponses = savedMembers.stream()
                .map(m -> memberPrivacyPolicy.applyPrivacy(memberMapper.toResponse(m), null, privacyCtx))
                .toList();

        log.info("Bulk member import completed for clan={}: inserted={}, skipped={}",
                clanId, insertedResponses.size(), skippedRows.size());

        return ImportMembersConfirmResponse.builder()
                .insertedCount(insertedResponses.size())
                .skippedCount(skippedRows.size())
                .insertedMembers(insertedResponses)
                .skippedRows(skippedRows)
                .build();
    }

    private ImportValidationContext buildValidationContext(String clanId, Collection<String> candidateMemberIds) {
        Map<String, FamilyBranch> branchesById = branchRepository.findByClanIdOrderByGenerationLevelAsc(clanId)
                .stream()
                .collect(Collectors.toMap(FamilyBranch::getBranchId, Function.identity(), (a, b) -> a));

        boolean isUnrestrictedClanManager = currentUserProvider.hasAuthority(SystemRoles.CLAN_HEAD)
                || currentUserProvider.hasAuthority(SystemRoles.ADMIN);

        Set<String> managedBranchIds = Collections.emptySet();
        if (!isUnrestrictedClanManager) {
            if (!currentUserProvider.hasAuthority(SystemRoles.BRANCH_HEAD)) {
                throw new AccessDeniedException("Không có quyền nhập hàng loạt thành viên");
            }
            String callerMemberId = currentUserProvider.currentMemberId()
                    .orElseThrow(() -> new AccessDeniedException("Tài khoản trưởng nhánh chưa được liên kết với hồ sơ thành viên"));
            managedBranchIds = new HashSet<>(genealogyQueryService.getBranchIdsManagedByMember(clanId, callerMemberId));
        }

        Set<String> existingMemberIds = candidateMemberIds.isEmpty()
                ? new HashSet<>()
                : new HashSet<>(memberRepository.findExistingMemberIds(candidateMemberIds));

        Set<ProfileKey> existingProfileKeys = memberRepository.findByBranch_ClanId(clanId)
                .stream()
                .map(m -> new ProfileKey(
                        m.getFullName().trim().toLowerCase(Locale.ROOT),
                        m.getGender(),
                        m.getGeneration(),
                        m.getDateOfBirth()))
                .collect(Collectors.toCollection(HashSet::new));

        return new ImportValidationContext(
                branchesById,
                isUnrestrictedClanManager,
                managedBranchIds,
                existingMemberIds,
                existingProfileKeys
        );
    }

    private ValidatedRow validateParsedRow(ParsedMemberRow raw, ImportValidationContext ctx) {
        List<String> errors = new ArrayList<>();
        LocalDate today = LocalDate.now();

        // 1. Validate member_id (optional)
        String memberId = trimToNull(raw.memberId());
        if (memberId != null) {
            if (memberId.length() > MAX_MEMBER_ID_LENGTH) {
                errors.add("Mã thành viên '" + memberId + "' vượt quá " + MAX_MEMBER_ID_LENGTH + " ký tự");
            } else if (memberId.chars().anyMatch(Character::isWhitespace)) {
                errors.add("Mã thành viên '" + memberId + "' không được chứa khoảng trắng");
            } else if (ctx.existingMemberIds.contains(memberId)) {
                errors.add("Mã thành viên '" + memberId + "' đã tồn tại trong hệ thống");
            } else if (!ctx.seenMemberIdsInBatch.add(memberId)) {
                errors.add("Mã thành viên '" + memberId + "' bị trùng lặp với dòng khác trong file");
            }
        }

        // 2. Validate full_name
        String fullName = trimToNull(raw.fullName());
        if (fullName == null) {
            errors.add("Thiếu họ và tên (full_name)");
        } else if (fullName.length() > MAX_FULL_NAME_LENGTH) {
            errors.add("Họ và tên vượt quá " + MAX_FULL_NAME_LENGTH + " ký tự");
        }

        // 3. Validate gender
        String gender = normalizeGender(raw.gender());
        if (raw.gender() == null || raw.gender().isBlank()) {
            errors.add("Thiếu giới tính (gender)");
        } else if (gender == null) {
            errors.add("Giới tính '" + raw.gender() + "' không hợp lệ (chỉ hỗ trợ Male/Female/Other hoặc Nam/Nữ/Khác)");
        }

        // 4. Validate branch_id & RBAC branch scope
        String branchId = trimToNull(raw.branchId());
        FamilyBranch branch = null;
        if (branchId == null) {
            errors.add("Thiếu mã nhánh (branch_id)");
        } else if (branchId.length() > MAX_MEMBER_ID_LENGTH) {
            errors.add("Mã nhánh '" + branchId + "' vượt quá " + MAX_MEMBER_ID_LENGTH + " ký tự");
        } else {
            branch = ctx.branchesById.get(branchId);
            if (branch == null) {
                errors.add("Mã nhánh '" + branchId + "' không tồn tại trong dòng họ");
            } else if (!ctx.isUnrestrictedClanManager && !ctx.managedBranchIds.contains(branchId)) {
                errors.add("Bạn không có quyền nhập thành viên vào nhánh '" + branchId + "'");
            }
        }

        // 5. Validate generation
        Integer generation = parseGeneration(raw.rawGeneration(), errors);
        if (generation != null && branch != null && generation < branch.getGenerationLevel()) {
            errors.add("Đời của thành viên (" + generation + ") không được nhỏ hơn đời gốc của nhánh (" + branch.getGenerationLevel() + ")");
        }

        // 6. Validate date_of_birth
        LocalDate dateOfBirth = parseDateField(raw.rawDateOfBirth(), raw.nativeDateOfBirth(), "ngày sinh", today, errors);

        // 7. Validate status, death_date & burial_place
        String status = normalizeStatus(raw.status());
        if (raw.status() != null && !raw.status().isBlank() && status == null) {
            errors.add("Tình trạng '" + raw.status() + "' không hợp lệ (chỉ hỗ trợ Living hoặc Deceased)");
        }
        if (status == null && (raw.status() == null || raw.status().isBlank())) {
            status = Member.STATUS_LIVING;
        }

        LocalDate deathDate = null;
        String deathDateLunar = null;
        String burialPlace = null;
        if (Member.STATUS_DECEASED.equals(status)) {
            if (raw.nativeDeathDate() == null && (raw.rawDeathDate() == null || raw.rawDeathDate().isBlank())) {
                errors.add("Thiếu ngày mất (death_date) khi tình trạng là Deceased");
            } else {
                deathDate = parseDateField(raw.rawDeathDate(), raw.nativeDeathDate(), "ngày mất", today, errors);
                if (deathDate != null && dateOfBirth != null && deathDate.isBefore(dateOfBirth)) {
                    errors.add("Ngày mất không thể trước ngày sinh");
                }
                if (deathDate != null) {
                    VietnameseLunarDate lunar = lunarCalendarConverter.convertSolarToLunar(deathDate);
                    deathDateLunar = lunar != null ? lunar.toShortAnniversaryString() : null;
                }
            }
            burialPlace = trimToNull(raw.burialPlace());
            if (burialPlace != null && burialPlace.length() > MAX_BURIAL_PLACE_LENGTH) {
                errors.add("Nơi an táng vượt quá " + MAX_BURIAL_PLACE_LENGTH + " ký tự");
            }
        }

        // 8. Duplicate profile check (US-03 invariant)
        if (fullName != null && gender != null && generation != null) {
            ProfileKey profileKey = new ProfileKey(fullName.toLowerCase(Locale.ROOT), gender, generation, dateOfBirth);
            if (ctx.existingProfileKeys.contains(profileKey)) {
                errors.add("Hồ sơ thành viên bị trùng (cùng họ tên, giới tính, đời và ngày sinh) với thành viên đã có trong dòng họ");
            } else if (!ctx.seenProfileKeysInBatch.add(profileKey)) {
                errors.add("Hồ sơ thành viên bị trùng với một dòng khác trong file");
            }
        }

        return new ValidatedRow(
                errors,
                memberId,
                fullName,
                dateOfBirth,
                gender,
                generation,
                branchId,
                branch,
                status,
                deathDate,
                deathDateLunar,
                burialPlace
        );
    }

    private List<FamilyBranch> filterBranchesForCaller(String clanId, List<FamilyBranch> allBranches) {
        if (currentUserProvider.hasAuthority(SystemRoles.CLAN_HEAD) || currentUserProvider.hasAuthority(SystemRoles.ADMIN)) {
            return allBranches;
        }
        String callerMemberId = currentUserProvider.currentMemberId().orElse(null);
        if (callerMemberId == null) {
            return Collections.emptyList();
        }
        Set<String> managedIds = new HashSet<>(genealogyQueryService.getBranchIdsManagedByMember(clanId, callerMemberId));
        return allBranches.stream()
                .filter(b -> managedIds.contains(b.getBranchId()))
                .toList();
    }

    private PrivacyEvaluationContext buildPrivacyContext(String clanId) {
        String callerMemberId = currentUserProvider.currentMemberId().orElse(null);
        boolean isClanHead = currentUserProvider.hasAuthority(SystemRoles.CLAN_HEAD);
        Set<String> managedBranchIds = Collections.emptySet();
        if (callerMemberId != null && !isClanHead) {
            managedBranchIds = new HashSet<>(genealogyQueryService.getBranchIdsManagedByMember(clanId, callerMemberId));
        }
        return PrivacyEvaluationContext.builder()
                .callerMemberId(callerMemberId)
                .isClanHead(isClanHead)
                .managedBranchIds(managedBranchIds)
                .isAuthorizedOrganizer(false)
                .build();
    }

    private String generateUniqueMemberId(Set<String> existingMemberIds, Set<String> seenInBatch) {
        for (int attempt = 0; attempt < MAX_ID_ATTEMPTS; attempt++) {
            String candidate = memberIdGenerator.generate();
            if (!existingMemberIds.contains(candidate)
                    && !seenInBatch.contains(candidate)
                    && !memberRepository.existsByMemberId(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("Không thể sinh mã thành viên duy nhất, vui lòng thử lại");
    }

    private static Integer parseGeneration(String rawGeneration, List<String> errors) {
        if (rawGeneration == null || rawGeneration.isBlank()) {
            errors.add("Thiếu đời thứ (generation)");
            return null;
        }
        try {
            int gen = Integer.parseInt(rawGeneration.trim());
            if (gen < 1) {
                errors.add("Đời thứ (generation) phải >= 1");
                return null;
            }
            return gen;
        } catch (NumberFormatException ex) {
            errors.add("Đời thứ '" + rawGeneration + "' phải là số nguyên dương");
            return null;
        }
    }

    private static LocalDate parseDateField(
            String rawValue,
            LocalDate nativeDate,
            String fieldLabel,
            LocalDate today,
            List<String> errors) {
        LocalDate parsed = nativeDate;
        if (parsed == null) {
            if (rawValue == null || rawValue.isBlank()) {
                return null;
            }
            String trimmed = rawValue.trim();
            parsed = tryParseDate(trimmed);
            if (parsed == null) {
                errors.add("Sai định dạng " + fieldLabel + " '" + trimmed + "' (hỗ trợ yyyy-MM-dd hoặc dd/MM/yyyy)");
                return null;
            }
        }
        if (parsed.isAfter(today)) {
            errors.add("Giá trị " + fieldLabel + " không được ở tương lai");
            return null;
        }
        return parsed;
    }

    private static LocalDate tryParseDate(String value) {
        try {
            return LocalDate.parse(value, STRICT_ISO_DATE);
        } catch (DateTimeParseException ignored) {
            try {
                return LocalDate.parse(value, STRICT_VN_DATE);
            } catch (DateTimeParseException ignoredAgain) {
                return null;
            }
        }
    }

    private static String normalizeGender(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return switch (raw.trim().toLowerCase(Locale.ROOT)) {
            case "male", "nam", "m" -> "Male";
            case "female", "nữ", "nu", "f" -> "Female";
            case "other", "khác", "khac", "o" -> "Other";
            default -> null;
        };
    }

    private static String normalizeStatus(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return switch (raw.trim().toLowerCase(Locale.ROOT)) {
            case "living", "còn sống", "con song" -> Member.STATUS_LIVING;
            case "deceased", "đã mất", "da mat" -> Member.STATUS_DECEASED;
            default -> null;
        };
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private record ProfileKey(String fullNameLower, String gender, Integer generation, LocalDate dateOfBirth) {
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof ProfileKey that)) return false;
            return Objects.equals(fullNameLower, that.fullNameLower)
                    && Objects.equals(gender, that.gender)
                    && Objects.equals(generation, that.generation)
                    && Objects.equals(dateOfBirth, that.dateOfBirth);
        }

        @Override
        public int hashCode() {
            return Objects.hash(fullNameLower, gender, generation, dateOfBirth);
        }
    }

    private static final class ImportValidationContext {
        final Map<String, FamilyBranch> branchesById;
        final boolean isUnrestrictedClanManager;
        final Set<String> managedBranchIds;
        final Set<String> existingMemberIds;
        final Set<ProfileKey> existingProfileKeys;
        final Set<String> seenMemberIdsInBatch = new HashSet<>();
        final Set<ProfileKey> seenProfileKeysInBatch = new HashSet<>();

        ImportValidationContext(
                Map<String, FamilyBranch> branchesById,
                boolean isUnrestrictedClanManager,
                Set<String> managedBranchIds,
                Set<String> existingMemberIds,
                Set<ProfileKey> existingProfileKeys) {
            this.branchesById = branchesById;
            this.isUnrestrictedClanManager = isUnrestrictedClanManager;
            this.managedBranchIds = managedBranchIds;
            this.existingMemberIds = existingMemberIds;
            this.existingProfileKeys = existingProfileKeys;
        }
    }

    private record ValidatedRow(
            List<String> errors,
            String memberId,
            String fullName,
            LocalDate dateOfBirth,
            String gender,
            Integer generation,
            String branchId,
            FamilyBranch branch,
            String status,
            LocalDate deathDate,
            String deathDateLunar,
            String burialPlace
    ) {}
}
