package dev.linkpulse.common;

import java.net.URI;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
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

    private static final Logger LOG = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private static final URI VALIDATION_ERROR = URI.create("/problems/validation-error");
    private static final URI MALFORMED_REQUEST = URI.create("/problems/malformed-request");
    private static final URI INTERNAL_ERROR = URI.create("/problems/internal-error");

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

    /**
     * Corpo ilegível (JSON malformado ou campo em formato errado, como {@code expiresAt} sem
     * offset) vira {@code /problems/malformed-request}.
     *
     * <p>O {@code detail} é fixo: a mensagem da exceção nunca vai para o cliente, porque expõe
     * detalhes do parser (classe, linha, coluna, tipo Java).
     */
    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(
            HttpMessageNotReadableException ex, HttpHeaders headers, HttpStatusCode status,
            WebRequest request) {
        ProblemDetail body = ProblemDetail.forStatusAndDetail(status,
                "O corpo não é um JSON válido ou tem campo em formato errado. O expiresAt exige "
                        + "ISO-8601 com offset, por exemplo 2026-12-31T23:59:59Z.");
        body.setType(MALFORMED_REQUEST);
        body.setTitle("Corpo da requisição inválido");
        return handleExceptionInternal(ex, body, headers, status, request);
    }

    /**
     * Qualquer exceção sem handler específico vira um 500 genérico.
     *
     * <p>A stack fica só no log do servidor, sem request nem corpo (a URL de destino pode ter
     * tokens). As exceções do Spring MVC e as {@code ErrorResponseException} continuam nos
     * handlers herdados, que vencem por serem mais específicos.
     *
     * @param ex exceção não tratada
     * @return 500 com type {@code /problems/internal-error}
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleUnexpected(Exception ex) {
        LOG.error("Erro inesperado ao processar a requisição", ex);
        ProblemDetail body = ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR, "Erro inesperado. Tente novamente mais tarde.");
        body.setType(INTERNAL_ERROR);
        body.setTitle("Erro interno");
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }
}
