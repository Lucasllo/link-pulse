package dev.linkpulse.common;

import java.net.URI;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Tradução centralizada de erros para Problem Details (RFC 9457).
 *
 * <p>Substitui o handler do Boot: os handlers herdados do {@link ResponseEntityExceptionHandler}
 * continuam tratando as exceções do Spring MVC e toda {@code ErrorResponseException}, como as do
 * {@code LinkProblems} (404, 409, 410 e 400 de alias). Aqui ficam só os casos em que o contrato
 * da API muda o corpo padrão.
 *
 * <p>Os {@code type} são URIs relativas, não resolvíveis, no formato {@code /problems/<slug>}
 * (RFC 9457, seção 3.1.1). O {@code detail} nunca leva SQL, stack trace, nome de classe de
 * exceção nem nome de constraint.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final URI VALIDATION_ERROR = URI.create("/problems/validation-error");

    /**
     * Erros de Bean Validation no corpo viram {@code /problems/validation-error} com
     * {@code errors[{field, message}]}, o mesmo formato do {@code LinkProblems.invalidField}.
     */
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatusCode status,
            WebRequest request) {
        ProblemDetail body = ex.getBody();
        body.setType(VALIDATION_ERROR);
        body.setTitle("Requisição inválida");
        body.setDetail("Um ou mais campos são inválidos.");
        List<Map<String, String>> errors = ex.getBindingResult().getFieldErrors().stream()
                .sorted(Comparator.comparing(FieldError::getField)
                        .thenComparing(fe -> String.valueOf(fe.getDefaultMessage())))
                .map(fe -> Map.of(
                        "field", fe.getField(),
                        "message", String.valueOf(fe.getDefaultMessage())))
                .toList();
        body.setProperty("errors", errors);
        return handleExceptionInternal(ex, body, headers, status, request);
    }
}
