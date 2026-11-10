package com.campuspulse.service.org;

import com.campuspulse.entity.org.Department;
import com.campuspulse.entity.org.Program;
import com.campuspulse.entity.user.AppUser;
import com.campuspulse.repository.org.DepartmentRepository;
import com.campuspulse.repository.org.ProgramRepository;
import com.campuspulse.repository.user.AppUserRepository;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StudentImportServiceTest {

    private final AppUserRepository users = mock(AppUserRepository.class);
    private final DepartmentRepository departments = mock(DepartmentRepository.class);
    private final ProgramRepository programs = mock(ProgramRepository.class);
    private final TenantService tenant = new TenantService(null) {
        @Override
        public Long actingInstitutionId(AppUser user) {
            return user.getInstitutionId();
        }
    };
    private final PasswordEncoder encoder = mock(PasswordEncoder.class);
    private final StudentImportService service =
            new StudentImportService(users, departments, programs, tenant, encoder);

    private AppUser admin;

    @BeforeEach
    void setUp() {
        admin = user(1L, "ADMIN", 1L, null);
        Department cse = new Department();
        ReflectionTestUtils.setField(cse, "id", 3L);
        cse.setInstitutionId(1L);
        cse.setCode("CSE");
        cse.setName("Computer Science and Engineering");
        Program be = new Program();
        ReflectionTestUtils.setField(be, "id", 7L);
        be.setDepartmentId(3L);
        be.setCode("CSE");
        be.setRegulation("2021");
        be.setName("B.E. CSE");

        when(departments.findAllByInstitutionIdOrderByNameAsc(1L)).thenReturn(List.of(cse));
        when(programs.findAllByDepartmentIdOrderByNameAsc(3L)).thenReturn(List.of(be));
        when(users.findByEmail(anyString())).thenReturn(Optional.empty());
        when(users.findAll()).thenReturn(List.of());
        when(encoder.encode(anyString())).thenReturn("hash");
    }

    private static AppUser user(Long id, String role, Long college, Long department) {
        AppUser user = new AppUser();
        user.setId(id);
        user.setRole(role);
        user.setInstitutionId(college);
        user.setDepartmentId(department);
        return user;
    }

    private static MockMultipartFile csv(String body) {
        return new MockMultipartFile("file", "students.csv", "text/csv", body.getBytes(StandardCharsets.UTF_8));
    }

    private static final String HEADER = "name,email,roll_number,department_code,program_code,regulation,batch_year\n";

    @Test
    void previewValidatesRowsWithoutSaving() throws Exception {
        var result = service.importFile(csv(HEADER
                + "Asha Raman,asha@college.edu,21CS001,CSE,CSE,2021,2021\n"
                + "No Dept,nodept@college.edu,21CS002,XYZ,,,2021\n"
                + "Dup,asha@college.edu,21CS003,CSE,,,\n"
                + "\"Iyer, Vikram\",vikram@college.edu,21CS004,cse,cse,,twenty\n"), admin, false);

        assertEquals(1, result.created());
        assertEquals(3, result.errors());
        assertEquals("CREATE", result.rows().get(0).action());
        assertEquals("B.E. CSE", result.rows().get(0).program());
        assertTrue(result.rows().get(1).message().contains("Unknown department code XYZ"));
        assertTrue(result.rows().get(2).message().contains("twice"));
        assertEquals("Iyer, Vikram", result.rows().get(3).name());
        assertTrue(result.rows().get(3).message().contains("batch_year"));
        assertNull(result.rows().get(0).temporaryPassword(), "no password in preview");
        verify(users, never()).save(any());
    }

    @Test
    void applyCreatesAccountWithOneTimePassword() throws Exception {
        var result = service.importFile(csv(HEADER + "Asha Raman,ASHA@college.edu,21CS001,CSE,CSE,2021,2021\n"), admin, true);
        assertEquals(1, result.created());
        assertNotNull(result.rows().get(0).temporaryPassword());
        verify(users).save(any(AppUser.class));
    }

    @Test
    void hodOnlyImportsIntoOwnDepartment() throws Exception {
        AppUser hodOfOther = user(9L, "HOD", 1L, 4L);
        var result = service.importFile(csv(HEADER + "Asha,asha@college.edu,,CSE,,,\n"), hodOfOther, false);
        assertEquals(1, result.errors());
        assertTrue(result.rows().get(0).message().contains("HOD"));
    }

    @Test
    void templateHasStudentsAndCodesSheets() throws Exception {
        byte[] file = service.template(1L);
        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(file))) {
            assertEquals("Students", workbook.getSheetAt(0).getSheetName());
            assertEquals("name", workbook.getSheetAt(0).getRow(0).getCell(0).getStringCellValue());
            assertEquals("CSE", workbook.getSheet("Codes").getRow(1).getCell(0).getStringCellValue());
        }
        // The template itself can be read back by the importer.
        var rows = StudentImportService.readXlsx(new ByteArrayInputStream(file));
        assertEquals(2, rows.size());
        assertEquals("asha.raman@college.edu", rows.get(0).get("email"));
    }
}
