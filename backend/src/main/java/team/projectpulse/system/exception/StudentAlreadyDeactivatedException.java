package team.projectpulse.system.exception;

/**
 * A deactivation of a student who is already deactivated (UC-STU-deactivate-student, extension 6a). Mapped to
 * {@code 409}: the request is well formed but conflicts with the student's current state.
 */
public class StudentAlreadyDeactivatedException extends RuntimeException {

    public StudentAlreadyDeactivatedException() {
        super("This student is already deactivated.");
    }

}
