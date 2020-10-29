package dk.digitalidentity.ooapi.exceptions;

@SuppressWarnings("serial")
public class InvalidEnvironmentException extends RuntimeException {

    public InvalidEnvironmentException(String msg) {
        super(msg);
    }
}
