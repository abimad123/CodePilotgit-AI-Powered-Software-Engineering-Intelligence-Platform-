package dev.codepilotgit.common;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.dao.DataIntegrityViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.List;

@RestControllerAdvice
public class ApiErrors {
    private static final Logger log = LoggerFactory.getLogger(ApiErrors.class);
    public record Detail(String field, String reason) {}
    public record Error(String code, String message, String requestId, List<Detail> details) {}
    public record Envelope(Error error) {}

    @ExceptionHandler(ApiException.class)
    ResponseEntity<Envelope> known(ApiException ex, HttpServletRequest request) {
        return ResponseEntity.status(ex.status).body(new Envelope(new Error(ex.code, ex.getMessage(), requestId(request), List.of())));
    }
    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<Envelope> validation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        List<Detail> details = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> new Detail(e.getField(), e.getDefaultMessage())).toList();
        return ResponseEntity.badRequest().body(new Envelope(new Error("VALIDATION_ERROR", "The request could not be accepted.", requestId(request), details)));
    }
    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    ResponseEntity<Envelope> malformed(Exception ex, HttpServletRequest request) {
        return ResponseEntity.badRequest().body(new Envelope(new Error("VALIDATION_ERROR", "The request could not be accepted.", requestId(request), List.of())));
    }
    @ExceptionHandler(Exception.class)
    ResponseEntity<Envelope> unexpected(Exception ex, HttpServletRequest request) {
        log.error("Unhandled request failure requestId={} type={}", requestId(request), ex.getClass().getName());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new Envelope(new Error("INTERNAL_ERROR", "An unexpected error occurred.", requestId(request), List.of())));
    }
    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<Envelope> conflict(DataIntegrityViolationException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new Envelope(new Error("CONFLICT", "Resource conflicts with existing data.", requestId(request), List.of())));
    }
    private String requestId(HttpServletRequest request) { return String.valueOf(request.getAttribute("requestId")); }
}
