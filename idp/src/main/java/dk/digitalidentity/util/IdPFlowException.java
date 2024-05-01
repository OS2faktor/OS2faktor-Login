package dk.digitalidentity.util;

import lombok.Getter;

@Getter
public class IdPFlowException extends Exception {
	private static final long serialVersionUID = -928155153536856803L;
	private String helpMessage;
	private String errorCode;

    public IdPFlowException(String errorMessage) {
        super(errorMessage);
    }
    
    public IdPFlowException(String errorMessage, String errorCode, String helpMessage) {
        super(errorMessage);
        this.errorCode = errorCode;
        this.helpMessage = helpMessage;
    }

    public IdPFlowException(String errorMessage, Exception exception) {
        super(errorMessage, exception);
    }
    
    public IdPFlowException(String errorMessage, String errorCode, String helpMessage, Exception exception) {
        super(errorMessage, exception);
        this.errorCode = errorCode;
        this.helpMessage = helpMessage;
    }
}
