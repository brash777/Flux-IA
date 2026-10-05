package com.fluxia.backend.shared;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Traduce cualquier excepcion a una respuesta JSON uniforme.
 *
 * <p>Regla de seguridad que conviene poder sustentar: al cliente nunca
 * se le manda el mensaje de una excepcion inesperada. Esos mensajes
 * suelen filtrar nombres de tablas, rutas o consultas SQL. Se registran
 * en el log del servidor y al cliente se le responde un texto generico.
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
                        "Hay campos invalidos en la peticion.", fields, request.getRequestURI()));
    }

    /** Contrasena incorrecta en el inicio de sesion. */
    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ApiErrorResponse> handleBadCredentials(HttpServletRequest request) {
        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(ApiErrorResponse.of(
                        "INVALID_CREDENTIALS",
                        "El correo o la contrasena no son correctos.",
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

    /** Red de contencion: cualquier cosa no prevista. */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("Error inesperado en {} {}", request.getMethod(), request.getRequestURI(), ex);
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiErrorResponse.of(
                        "INTERNAL_ERROR",
                        "Ocurrio un error inesperado. Intenta de nuevo en unos minutos.",
                        request.getRequestURI()));
    }
}
