
package mn.edu.must.sqat;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CourseRegistrationServiceTest {

    @Mock
    StudentRepository repository;

    @Mock
    EmailSender emailSender;

    CourseRegistrationService service;

    private final Student activeStudent =
            new Student("S001", "student@example.com", true,
                    List.of("CS101"));

    @BeforeEach
    void setUp() {
        service = new CourseRegistrationService(repository, emailSender);
    }

    @Test
    void register_ActiveStudentWithPrerequisites_ReturnsOkAndSavesAndSendsEmail() {
        when(repository.findStudent("S001")).thenReturn(activeStudent);
        when(repository.courseExists("CS201")).thenReturn(true);
        when(repository.prerequisites("CS201")).thenReturn(List.of("CS101"));

        String result = service.register("S001", "CS201");

        assertEquals("OK", result);
        verify(repository).saveRegistration("S001", "CS201");
        verify(emailSender).send(
                eq("student@example.com"),
                anyString(),
                contains("CS201"));
    }

    @Test
    void register_StudentDoesNotExist_ReturnsErrorWithoutSavingOrSendingEmail() {
        when(repository.findStudent("S999")).thenReturn(null);

        String result = service.register("S999", "CS201");

        assertEquals("ERROR_NO_STUDENT", result);
        verify(repository, never()).saveRegistration(anyString(), anyString());
        verifyNoInteractions(emailSender);
    }

    @Test
    void register_InactiveStudent_ReturnsErrorWithoutEmailInteraction() {
        Student inactiveStudent =
                new Student("S002", "inactive@example.com", false, List.of());

        when(repository.findStudent("S002")).thenReturn(inactiveStudent);

        String result = service.register("S002", "CS201");

        assertEquals("ERROR_INACTIVE_STUDENT", result);
        verifyNoInteractions(emailSender);
        verify(repository, never()).saveRegistration(anyString(), anyString());
    }

    @Test
    void register_PrerequisitesAreMissing_ReturnsErrorWithoutSaving() {
        when(repository.findStudent("S001")).thenReturn(
                new Student("S001", "student@example.com", true, List.of()));
        when(repository.courseExists("CS201")).thenReturn(true);
        when(repository.prerequisites("CS201")).thenReturn(List.of("CS101"));

        String result = service.register("S001", "CS201");

        assertEquals("ERROR_PREREQUISITES", result);
        verify(repository, never()).saveRegistration(anyString(), anyString());
        verifyNoInteractions(emailSender);
    }

    @Test
    void register_EmailSenderThrowsException_StillReturnsOkAndKeepsRegistration() {
        when(repository.findStudent("S001")).thenReturn(activeStudent);
        when(repository.courseExists("CS201")).thenReturn(true);
        when(repository.prerequisites("CS201")).thenReturn(List.of("CS101"));

        doThrow(new RuntimeException("SMTP unavailable"))
                .when(emailSender)
                .send(anyString(), anyString(), anyString());

        String result = service.register("S001", "CS201");

        assertEquals("OK", result);
        verify(repository).saveRegistration("S001", "CS201");
        verify(emailSender).send(anyString(), anyString(), anyString());
    }

    @Test
    void register_NullStudentIdOrCourseId_ThrowsIllegalArgumentExceptionWithoutInteractions() {
        assertAll(
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> service.register(null, "CS201")),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> service.register("S001", null))
        );

        verifyNoInteractions(repository, emailSender);
    }

    @Test
    void register_ValidRegistration_EmailBodyContainsCourseId() {
        when(repository.findStudent("S001")).thenReturn(activeStudent);
        when(repository.courseExists("CS201")).thenReturn(true);
        when(repository.prerequisites("CS201")).thenReturn(List.of("CS101"));

        ArgumentCaptor<String> bodyCaptor = ArgumentCaptor.forClass(String.class);

        String result = service.register("S001", "CS201");

        assertEquals("OK", result);
        verify(emailSender).send(
                eq("student@example.com"),
                anyString(),
                bodyCaptor.capture());

        assertTrue(bodyCaptor.getValue().contains("CS201"));
    }

    @ParameterizedTest(name = "[{index}] student={0}, active={1}, courseExists={2} -> {3}")
    @CsvSource({
            "missing, true,  true,  ERROR_NO_STUDENT",
            "present, false, true,  ERROR_INACTIVE_STUDENT",
            "present, true,  false, ERROR_NO_COURSE"
    })
    void register_InvalidRegistrationState_ReturnsExpectedError(
            String studentState,
            boolean active,
            boolean courseExists,
            String expectedResult) {

        Student student = "missing".equals(studentState)
                ? null
                : new Student("S001", "student@example.com", active, List.of("CS101"));

        lenient().when(repository.findStudent("S001")).thenReturn(student);
        lenient().when(repository.courseExists("CS201")).thenReturn(courseExists);
        lenient().when(repository.prerequisites("CS201")).thenReturn(List.of("CS101"));

        String result = service.register("S001", "CS201");

        assertEquals(expectedResult, result);
        verify(repository, never()).saveRegistration(anyString(), anyString());
        verifyNoInteractions(emailSender);
    }
}
