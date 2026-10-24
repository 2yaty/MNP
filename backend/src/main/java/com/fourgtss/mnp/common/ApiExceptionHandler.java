package com.fourgtss.mnp.common;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import com.fourgtss.mnp.auth.UnauthenticatedException;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.validation.method.ParameterValidationResult;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Maps exceptions to RFC 9457 problem details. Extending {@link ResponseEntityExceptionHandler}
 * gives Spring MVC's own errors (malformed JSON, unsupported method, ...) the same format.
 */
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(UnauthenticatedException.class)
    ProblemDetail handleUnauthenticated(UnauthenticatedException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, exception.getMessage());
    }

    @ExceptionHandler(ForbiddenException.class)
    ProblemDetail handleForbidden(ForbiddenException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, exception.getMessage());
    }

    @ExceptionHandler(NotFoundException.class)
    ProblemDetail handleNotFound(NotFoundException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, exception.getMessage());
    }

    @ExceptionHandler(ConflictException.class)
    ProblemDetail handleConflict(ConflictException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, exception.getMessage());
    }

    @ExceptionHandler(BusinessRuleViolationException.class)
    ProblemDetail handleBusinessRuleViolation(BusinessRuleViolationException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_CONTENT, exception.getMessage());
    }

    /** Invalid request body: adds an {@code errors} map (field → message) so clients can tell which field failed. */
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException exception,
                                                                  HttpHeaders headers, HttpStatusCode status,
                                                                  WebRequest request) {
        Map<String, String> errors = new LinkedHashMap<>();
        for (FieldError fieldError : exception.getBindingResult().getFieldErrors()) {
            errors.putIfAbsent(fieldError.getField(), messageOf(fieldError));
        }
        return handleExceptionInternal(exception, validationFailed(exception.getBody(), errors), headers, status,
                request);
    }

    /** Invalid path or query parameter: the same {@code errors} map, keyed by parameter name. */
    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(HandlerMethodValidationException exception,
                                                                            HttpHeaders headers, HttpStatusCode status,
                                                                            WebRequest request) {
        Map<String, String> errors = new LinkedHashMap<>();
        for (ParameterValidationResult result : exception.getParameterValidationResults()) {
            String parameter = result.getMethodParameter().getParameterName();
            result.getResolvableErrors().forEach(error -> errors.putIfAbsent(parameter, messageOf(error)));
        }
        return handleExceptionInternal(exception, validationFailed(exception.getBody(), errors), headers, status,
                request);
    }

    private static ProblemDetail validationFailed(ProblemDetail problem, Map<String, String> errors) {
        problem.setDetail("Request validation failed");
        problem.setProperty("errors", errors);
        return problem;
    }

    private static String messageOf(MessageSourceResolvable error) {
        return Objects.requireNonNullElse(error.getDefaultMessage(), "is invalid");
    }
}
