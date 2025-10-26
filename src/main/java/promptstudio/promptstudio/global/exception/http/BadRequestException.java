package promptstudio.promptstudio.global.exception.http;

public class BadRequestException extends RuntimeException{
    public BadRequestException(String message) {
        super(message);
    }
}
