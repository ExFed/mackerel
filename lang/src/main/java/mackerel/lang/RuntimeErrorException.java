package mackerel.lang;

import lombok.Getter;

public class RuntimeErrorException extends RuntimeException {
    @Getter
    private final Token token;

    RuntimeErrorException(Token token, String message) {
        super(message);
        this.token = token;
    }
}
