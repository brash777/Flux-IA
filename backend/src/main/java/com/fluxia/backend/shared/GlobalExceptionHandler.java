package com.fluxia.backend.shared;

import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Traduce cualquier excepcion a una respuesta JSON uniforme.
 *
 * <p>Regla de seguridad que conviene poder sustentar: al cliente nunca
 * se le manda el mensaje de una excepcion inesperada. Esos mensajes
 * suelen filtrar nombres de tablas, rutas o consultas SQL. Se registran
 * en el log del servidor y al cliente se le responde un texto generico.
 *
 * <p>Los errores del cliente (una ruta que no existe, un periodo mal
 * escrito, un JSON roto) tienen su propio manejador. Sin ellos caerian en
 * la red de contencion y se responderian como 500, culpando al servidor de
 * un error que es de la peticion. Ninguno repite el valor recibido: se
 * nombra el campo y lo que se espera en el.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** Errores de negocio que la aplicacion lanza a proposito. */
    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiErrorResponse> handleApi(ApiException ex, HttpServletRequest request) {
        return ResponseEntity
                .status(ex.getStatus())
                .body(ApiErrorResponse.of(ex.getCode(), ex.getMessage(), request.getRequestURI()));
    }

    /** Fallos de @Valid: se devuelve el detalle campo por campo. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidation(
            MethodArgumentNotValidException ex, HttpServletRequest request) {

        Map<String, String> fields = new LinkedHashMap<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            // Si un campo falla dos reglas, se conserva el primer mensaje.
            fields.putIfAbsent(error.getField(), error.getDefaultMessage());
        }
        return ResponseEntity
                .badRequest()
                .body(ApiErrorResponse.ofFields(
                        "Hay campos inválidos en la petición.", fields, request.getRequestURI()));
    }

    /** Contrasena incorrecta en el inicio de sesion. */
    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ApiErrorResponse> handleBadCredentials(HttpServletRequest request) {
        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(ApiErrorResponse.of(
                        "INVALID_CREDENTIALS",
                        "El correo o la contraseña no son correctos.",
                        request.getRequestURI()));
    }

    /** Token valido, pero sin permiso sobre el recurso pedido. */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiErrorResponse> handleAccessDenied(HttpServletRequest request) {
        return ResponseEntity
                .status(HttpStatus.FORBIDDEN)
                .body(ApiErrorResponse.of(
                        "FORBIDDEN",
                        "No tienes permiso para acceder a este recurso.",
                        request.getRequestURI()));
    }

    /** Parametro de la URL con el tipo equivocado: period=SEMANA, un id que no es UUID. */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiErrorResponse> handleTypeMismatch(
            MethodArgumentTypeMismatchException ex, HttpServletRequest request) {

        String message = "El parámetro «" + ex.getName() + "» no tiene un valor válido. "
                + expectedFormat(ex.getRequiredType());
        return ResponseEntity
                .badRequest()
                .body(ApiErrorResponse.of("INVALID_PARAMETER", message.trim(), request.getRequestURI()));
    }

    /** Falta un parametro obligatorio de la URL. */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiErrorResponse> handleMissingParameter(
            MissingServletRequestParameterException ex, HttpServletRequest request) {

        return ResponseEntity
                .badRequest()
                .body(ApiErrorResponse.of(
                        "MISSING_PARAMETER",
                        "Falta el parámetro «" + ex.getParameterName() + "».",
                        request.getRequestURI()));
    }

    /**
     * Cuerpo ilegible. Si Jackson sabe que campo fallo (un monto "abc", un
     * tipo que no existe), se responde como error de validacion de ese
     * campo, que es lo que el formulario sabe mostrar.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorResponse> handleUnreadable(
            HttpMessageNotReadableException ex, HttpServletRequest request) {

        if (ex.getCause() instanceof InvalidFormatException invalid) {
            String field = fieldPath(invalid.getPath());
            if (!field.isEmpty()) {
                String message = ("Valor no válido. " + expectedFormat(invalid.getTargetType())).trim();
                return ResponseEntity
                        .badRequest()
                        .body(ApiErrorResponse.ofFields(
                                "Hay campos inválidos en la petición.",
                                Map.of(field, message),
                                request.getRequestURI()));
            }
        }
        return ResponseEntity
                .badRequest()
                .body(ApiErrorResponse.of(
                        "MALFORMED_BODY",
                        "El cuerpo de la petición no es un JSON válido.",
                        request.getRequestURI()));
    }

    /** Ruta que no existe. */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleNoResource(HttpServletRequest request) {
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(ApiErrorResponse.of("NOT_FOUND", "La ruta pedida no existe.", request.getRequestURI()));
    }

    /** Ruta que existe, pero no con ese metodo (PUT donde solo hay PATCH). */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiErrorResponse> handleMethodNotSupported(
            HttpRequestMethodNotSupportedException ex, HttpServletRequest request) {

        return ResponseEntity
                .status(HttpStatus.METHOD_NOT_ALLOWED)
                .body(ApiErrorResponse.of(
                        "METHOD_NOT_ALLOWED",
                        "El método " + ex.getMethod() + " no está permitido en esta ruta.",
                        request.getRequestURI()));
    }

    /** Cuerpo enviado con un tipo de contenido que no es JSON. */
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiErrorResponse> handleMediaType(HttpServletRequest request) {
        return ResponseEntity
                .status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
                .body(ApiErrorResponse.of(
                        "UNSUPPORTED_MEDIA_TYPE",
                        "El cuerpo debe enviarse como JSON (Content-Type: application/json).",
                        request.getRequestURI()));
    }

    /** Red de contencion: cualquier cosa no prevista. */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("Error inesperado en {} {}", request.getMethod(), request.getRequestURI(), ex);
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiErrorResponse.of(
                        "INTERNAL_ERROR",
                        "Ocurrió un error inesperado. Intenta de nuevo en unos minutos.",
                        request.getRequestURI()));
    }

    /** Que formato se esperaba, dicho para quien escribe la peticion. */
    private static String expectedFormat(Class<?> type) {
        if (type == null) {
            return "";
        }
        if (type.isEnum()) {
            return "Valores admitidos: " + Arrays.stream(type.getEnumConstants())
                    .map(Object::toString)
                    .collect(Collectors.joining(", ")) + ".";
        }
        if (type == UUID.class) {
            return "Debe ser un identificador UUID.";
        }
        if (type == OffsetDateTime.class) {
            return "Usa el formato ISO-8601, por ejemplo 2026-10-01T15:30:00-03:00.";
        }
        if (type == BigDecimal.class) {
            return "Debe ser un número, por ejemplo 2450.50.";
        }
        return "";
    }

    /** "history[0].role" a partir de la ruta que arma Jackson. */
    private static String fieldPath(List<JsonMappingException.Reference> path) {
        StringBuilder field = new StringBuilder();
        for (JsonMappingException.Reference reference : path) {
            if (reference.getFieldName() != null) {
                if (!field.isEmpty()) {
                    field.append('.');
                }
                field.append(reference.getFieldName());
            } else if (reference.getIndex() >= 0) {
                field.append('[').append(reference.getIndex()).append(']');
            }
        }
        return field.toString();
    }
}
