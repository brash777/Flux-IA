package com.fluxia.backend.shared;

import org.springframework.http.HttpStatus;

/**
 * Excepcion base de la aplicacion: lleva consigo el codigo HTTP con el
 * que debe responderse y un codigo de error estable para el cliente.
 *
 * <p>La idea es que ningun controlador tenga que armar respuestas de
 * error a mano: lanza una de estas y {@link GlobalExceptionHandler} la
 * traduce a JSON de forma uniforme.
 */
public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    protected ApiException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }

    /** 404: el recurso no existe, o no es de este usuario. */
    public static ApiException notFound(String message) {
        return new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", message);
    }

    /** 400: la peticion es invalida por una regla de negocio. */
    public static ApiException badRequest(String code, String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, code, message);
    }

    /** 409: choca con algo que ya existe, como un correo repetido. */
    public static ApiException conflict(String code, String message) {
        return new ApiException(HttpStatus.CONFLICT, code, message);
    }

    /** 401: credenciales o token invalidos. */
    public static ApiException unauthorized(String code, String message) {
        return new ApiException(HttpStatus.UNAUTHORIZED, code, message);
    }

    /** 503: una dependencia externa no esta disponible o configurada. */
    public static ApiException unavailable(String code, String message) {
        return new ApiException(HttpStatus.SERVICE_UNAVAILABLE, code, message);
    }
}
