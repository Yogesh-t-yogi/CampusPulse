package com.campuspulse.service.org;

import com.campuspulse.entity.org.Department;
import com.campuspulse.entity.org.Program;
import com.campuspulse.entity.user.AppUser;
import com.campuspulse.repository.org.DepartmentRepository;
import com.campuspulse.repository.org.ProgramRepository;
import com.campuspulse.repository.user.AppUserRepository;
import com.campuspulse.service.auth.TempPasswords;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Bulk onboarding from Excel (.xlsx) or CSV (Phase 6.5).
 * Columns (header names, any order): name*, email*, roll_number, department_code*,
 * program_code, regulation, batch_year, role (STUDENT by default).
 * New email → account with a one-time password (must be changed at first sign-in).
 * Email already in this college → details updated, password unchanged.
 */
@Service
public class StudentImportService {

    public static final int MAX_ROWS = 2000;
    public static final long MAX_BYTES = 5L * 1024 * 1024;
    static final List<String> COLUMNS = List.of(
            "name", "email", "roll_number", "department_code", "program_code", "regulation", "batch_year", "role");

    public record RowResult(
            int row, String name, String email, String rollNumber,
            String department, String program, String action, String message,
            String temporaryPassword) {
    }

    public record ImportResult(boolean applied, int created, int updated, int errors, List<RowResult> rows) {
    }

    private final AppUserRepository users;
    private final DepartmentRepository departments;
    private final ProgramRepository programs;
    private final TenantService tenantService;
    private final PasswordEncoder passwordEncoder;

    public StudentImportService(
            AppUserRepository users,
            DepartmentRepository departments,
            ProgramRepository programs,
            TenantService tenantService,
            PasswordEncoder passwordEncoder) {
        this.users = users;
        this.departments = departments;
        this.programs = programs;
        this.tenantService = tenantService;
        this.passwordEncoder = passwordEncoder;
    }

    // ---------------------------------------------------------------- import

    /** apply=false: check only (preview). apply=true: create/update; a row with an error is skipped. */
    @Transactional
    public ImportResult importFile(MultipartFile file, AppUser actor, boolean apply) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Choose an Excel (.xlsx) or CSV file.");
        }
        if (file.getSize() > MAX_BYTES) {
            throw new IllegalArgumentException("The file must be 5 MB or smaller.");
        }
        List<Map<String, String>> rows = read(file);
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("The file has no student rows.");
        }
        if (rows.size() > MAX_ROWS) {
            throw new IllegalArgumentException("Import at most " + MAX_ROWS + " rows at a time.");
        }

        Long college = tenantService.actingInstitutionId(actor);
        if (college == null) {
            throw new IllegalArgumentException("Choose a college first.");
        }
        Map<String, Department> departmentsByCode = new HashMap<>();
        departments.findAllByInstitutionIdOrderByNameAsc(college)
                .forEach(d -> departmentsByCode.put(d.getCode().toUpperCase(Locale.ROOT), d));

        // Roll numbers already used in this college (upper-case → user id).
        Map<String, Long> rollOwners = new HashMap<>();
        users.findAll().stream()
                .filter(u -> Objects.equals(u.getInstitutionId(), college) && u.getRollNumber() != null)
                .forEach(u -> rollOwners.put(u.getRollNumber().toUpperCase(Locale.ROOT), u.getId()));

        Set<String> emailsInFile = new HashSet<>();
        Set<String> rollsInFile = new HashSet<>();
        List<RowResult> results = new ArrayList<>();
        int created = 0;
        int updated = 0;
        int errors = 0;

        for (int i = 0; i < rows.size(); i++) {
            Map<String, String> row = rows.get(i);
            int rowNumber = i + 2; // header is row 1
            String name = row.getOrDefault("name", "");
            String email = row.getOrDefault("email", "").toLowerCase(Locale.ROOT);
            String roll = blankToNull(row.get("roll_number"));
            String departmentCode = row.getOrDefault("department_code", "").toUpperCase(Locale.ROOT);
            String programCode = row.getOrDefault("program_code", "").toUpperCase(Locale.ROOT);
            String regulation = blankToNull(row.get("regulation"));
            String role = row.getOrDefault("role", "").isBlank() ? "STUDENT" : row.get("role").toUpperCase(Locale.ROOT);

            String problem = null;
            Department department = departmentsByCode.get(departmentCode);
            Program program = null;
            Integer batchYear = null;
            AppUser existing = email.isEmpty() ? null : users.findByEmail(email).orElse(null);

            if (name.isBlank()) problem = "Name is missing.";
            else if (!email.matches("[^@\\s]+@[^@\\s]+\\.[^@\\s]+")) problem = "Email is missing or not valid.";
            else if (!emailsInFile.add(email)) problem = "This email appears twice in the file.";
            else if (roll != null && !rollsInFile.add(roll.toUpperCase(Locale.ROOT))) problem = "This roll number appears twice in the file.";
            else if (department == null) problem = departmentCode.isEmpty()
                    ? "department_code is missing." : "Unknown department code " + departmentCode + ".";
            else if (!"STUDENT".equals(role) && !"FACULTY".equals(role)) problem = "Role must be STUDENT or FACULTY.";
            else if (existing != null && !Objects.equals(existing.getInstitutionId(), college)) problem = "This email belongs to another college.";
            else if (existing != null && TenantService.rank(existing.getRole()) >= TenantService.rank("HOD")) problem = "This email belongs to an HOD or admin; change them in Users.";

            if (problem == null) {
                try {
                    tenantService.requireCanAssignRole(actor, role);
                    tenantService.requireDepartmentManager(actor, department);
                } catch (org.springframework.web.server.ResponseStatusException e) {
                    problem = e.getReason() == null ? "You can't add people to this department." : e.getReason();
                }
            }
            if (problem == null && !programCode.isEmpty()) {
                List<Program> matches = programs.findAllByDepartmentIdOrderByNameAsc(department.getId()).stream()
                        .filter(p -> p.getCode().equalsIgnoreCase(programCode))
                        .filter(p -> regulation == null || regulation.equalsIgnoreCase(p.getRegulation()))
                        .toList();
                if (matches.isEmpty()) problem = "Unknown program " + programCode
                        + (regulation == null ? "" : " (" + regulation + ")") + " in " + departmentCode + ".";
                else if (matches.size() > 1) problem = "Program " + programCode + " has several regulations; add the regulation column.";
                else program = matches.get(0);
            }
            if (problem == null && row.get("batch_year") != null && !row.get("batch_year").isBlank()) {
                try {
                    batchYear = Integer.valueOf(row.get("batch_year").replaceAll("\\.0$", "").trim());
                    if (batchYear < 1990 || batchYear > 2100) problem = "batch_year looks wrong.";
                } catch (NumberFormatException e) {
                    problem = "batch_year must be a year like 2024.";
                }
            }
            if (problem == null && roll != null) {
                Long ownerOfRoll = rollOwners.get(roll.toUpperCase(Locale.ROOT));
                if (ownerOfRoll != null && (existing == null || !ownerOfRoll.equals(existing.getId()))) {
                    problem = "Roll number " + roll + " is already used by another account.";
                }
            }

            String programName = program == null ? null : program.getName();
            String departmentName = department == null ? departmentCode : department.getName();
            if (problem != null) {
                errors++;
                results.add(new RowResult(rowNumber, name, email, roll, departmentName, programName, "ERROR", problem, null));
                continue;
            }

            String password = null;
            if (apply) {
                AppUser user = existing != null ? existing : new AppUser();
                if (existing == null) {
                    user.setEmail(email);
                    user.setInstitutionId(college);
                    user.setRole(role);
                    password = TempPasswords.generate();
                    user.setPassword(passwordEncoder.encode(password));
                    user.setMustChangePassword(true);
                }
                user.setName(name.trim());
                user.setRollNumber(roll);
                user.setDepartmentId(department.getId());
                user.setProgramId(program == null ? null : program.getId());
                user.setBatchYear(batchYear);
                users.save(user);
            }
            if (existing == null) created++;
            else updated++;
            results.add(new RowResult(rowNumber, name, email, roll, departmentName, programName,
                    existing == null ? "CREATE" : "UPDATE",
                    existing == null ? "New account" : "Details updated (password unchanged)",
                    password));
        }
        return new ImportResult(apply, created, updated, errors, results);
    }

    // ---------------------------------------------------------------- reading

    List<Map<String, String>> read(MultipartFile file) throws IOException {
        String filename = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase(Locale.ROOT);
        try (InputStream in = file.getInputStream()) {
            if (filename.endsWith(".xlsx")) {
                return readXlsx(in);
            }
            if (filename.endsWith(".csv")) {
                return readCsv(in);
            }
        }
        throw new IllegalArgumentException("Upload an .xlsx or .csv file.");
    }

    static List<Map<String, String>> readXlsx(InputStream in) throws IOException {
        try (Workbook workbook = new XSSFWorkbook(in)) {
            Sheet sheet = workbook.getSheetAt(0);
            DataFormatter formatter = new DataFormatter();
            List<String> headers = null;
            List<Map<String, String>> rows = new ArrayList<>();
            for (Row row : sheet) {
                List<String> values = new ArrayList<>();
                for (int c = 0; c < Math.max(row.getLastCellNum(), 0); c++) {
                    Cell cell = row.getCell(c);
                    values.add(cell == null ? "" : formatter.formatCellValue(cell).trim());
                }
                if (values.stream().allMatch(String::isBlank)) {
                    continue;
                }
                if (headers == null) {
                    headers = values.stream().map(StudentImportService::headerKey).toList();
                    requireHeaders(headers);
                    continue;
                }
                rows.add(toRow(headers, values));
            }
            return rows;
        } catch (org.apache.poi.EmptyFileException | org.apache.poi.openxml4j.exceptions.NotOfficeXmlFileException e) {
            throw new IllegalArgumentException("This is not a valid .xlsx file.");
        }
    }

    static List<Map<String, String>> readCsv(InputStream in) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
        List<String> headers = null;
        List<Map<String, String>> rows = new ArrayList<>();
        String line;
        while ((line = reader.readLine()) != null) {
            List<String> values = splitCsvLine(line);
            if (values.stream().allMatch(String::isBlank)) {
                continue;
            }
            if (headers == null) {
                values.set(0, values.get(0).replaceFirst("^﻿", ""));
                headers = values.stream().map(StudentImportService::headerKey).toList();
                requireHeaders(headers);
                continue;
            }
            rows.add(toRow(headers, values));
        }
        return rows;
    }

    // Simple CSV: commas, double quotes for fields that contain commas ("" = one quote). No multi-line fields.
    static List<String> splitCsvLine(String line) {
        List<String> fields = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                if (quoted && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    field.append('"');
                    i++;
                } else {
                    quoted = !quoted;
                }
            } else if (c == ',' && !quoted) {
                fields.add(field.toString().trim());
                field.setLength(0);
            } else {
                field.append(c);
            }
        }
        fields.add(field.toString().trim());
        return fields;
    }

    private static String headerKey(String header) {
        return header.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_").replaceAll("^_|_$", "");
    }

    private static void requireHeaders(List<String> headers) {
        for (String required : List.of("name", "email", "department_code")) {
            if (!headers.contains(required)) {
                throw new IllegalArgumentException(
                        "Missing column \"" + required + "\". Download the template for the right columns.");
            }
        }
    }

    private static Map<String, String> toRow(List<String> headers, List<String> values) {
        Map<String, String> row = new LinkedHashMap<>();
        for (int c = 0; c < headers.size(); c++) {
            row.put(headers.get(c), c < values.size() ? values.get(c).trim() : "");
        }
        return row;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    // ---------------------------------------------------------------- sheets

    /** Template: "Students" sheet with headers + 2 example rows; "Codes" sheet with this college's codes. */
    public byte[] template(Long college) throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            CellStyle bold = boldStyle(workbook);
            Sheet students = workbook.createSheet("Students");
            writeRow(students, 0, COLUMNS, bold);
            writeRow(students, 1, List.of("Asha Raman", "asha.raman@college.edu", "21CS001", "CSE", "CSE", "2021", "2021", "STUDENT"), null);
            writeRow(students, 2, List.of("Vikram Iyer", "vikram.iyer@college.edu", "21CS002", "CSE", "CSE", "2021", "2021", "STUDENT"), null);
            for (int c = 0; c < COLUMNS.size(); c++) students.setColumnWidth(c, 22 * 256);

            Sheet codes = workbook.createSheet("Codes");
            writeRow(codes, 0, List.of("department_code", "department", "program_code", "regulation", "program"), bold);
            int r = 1;
            if (college != null) {
                for (Department department : departments.findAllByInstitutionIdOrderByNameAsc(college)) {
                    List<Program> list = programs.findAllByDepartmentIdOrderByNameAsc(department.getId());
                    if (list.isEmpty()) {
                        writeRow(codes, r++, List.of(department.getCode(), department.getName(), "", "", ""), null);
                    }
                    for (Program program : list) {
                        writeRow(codes, r++, List.of(department.getCode(), department.getName(), program.getCode(),
                                program.getRegulation() == null ? "" : program.getRegulation(), program.getName()), null);
                    }
                }
            }
            for (int c = 0; c < 5; c++) codes.setColumnWidth(c, 30 * 256);

            Sheet help = workbook.createSheet("How to fill");
            String[] lines = {
                    "One person per row on the Students sheet. Keep the header row.",
                    "Required: name, email, department_code. Codes are listed on the Codes sheet.",
                    "program_code (+ regulation if the program has more than one) puts a student in a program.",
                    "role: STUDENT (default) or FACULTY.",
                    "An email already in this college updates that person (password unchanged).",
                    "New people get a one-time password; download the credentials sheet after importing.",
                    "Delete the two example rows before importing."};
            for (int i = 0; i < lines.length; i++) writeRow(help, i, List.of(lines[i]), i == 0 ? bold : null);
            help.setColumnWidth(0, 110 * 256);
            return bytes(workbook);
        }
    }

    /** Credentials sheet for the people just created (passwords come from the import response). */
    public byte[] credentialsSheet(List<Map<String, String>> people) throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            CellStyle bold = boldStyle(workbook);
            Sheet sheet = workbook.createSheet("Credentials");
            writeRow(sheet, 0, List.of("name", "email", "roll_number", "one_time_password"), bold);
            int r = 1;
            for (Map<String, String> person : people) {
                writeRow(sheet, r++, List.of(
                        person.getOrDefault("name", ""),
                        person.getOrDefault("email", ""),
                        person.getOrDefault("rollNumber", "") == null ? "" : person.getOrDefault("rollNumber", ""),
                        person.getOrDefault("temporaryPassword", "")), null);
            }
            writeRow(sheet, r + 1, List.of("Each person must set a new password at first sign-in. Share privately; delete this file afterwards."), null);
            for (int c = 0; c < 4; c++) sheet.setColumnWidth(c, 30 * 256);
            return bytes(workbook);
        }
    }

    private static CellStyle boldStyle(Workbook workbook) {
        Font font = workbook.createFont();
        font.setBold(true);
        CellStyle style = workbook.createCellStyle();
        style.setFont(font);
        return style;
    }

    private static void writeRow(Sheet sheet, int index, List<String> values, CellStyle style) {
        Row row = sheet.createRow(index);
        for (int c = 0; c < values.size(); c++) {
            Cell cell = row.createCell(c);
            cell.setCellValue(values.get(c) == null ? "" : values.get(c));
            if (style != null) cell.setCellStyle(style);
        }
    }

    private static byte[] bytes(Workbook workbook) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        workbook.write(out);
        return out.toByteArray();
    }
}
