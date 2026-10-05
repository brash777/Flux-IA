package com.fluxia.backend.auth;

import com.fluxia.backend.account.Account;
import com.fluxia.backend.account.AccountRepository;
import com.fluxia.backend.account.AccountType;
import com.fluxia.backend.config.FluxProperties;
import com.fluxia.backend.shared.ApiException;
import com.fluxia.backend.user.User;
import com.fluxia.backend.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;

/**
 * Registro, inicio y cierre de sesion, refresco y recuperacion de
 * contrasena. Reemplaza por completo la validacion simulada del
 * prototipo, donde la contrasena era la constante "1234".
 */
@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    /** 32 bytes de aleatoriedad criptografica por token. */
    private static final int TOKEN_BYTES = 32;

    private final UserRepository userRepository;
    private final AccountRepository accountRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final FluxProperties properties;
    private final SecureRandom secureRandom = new SecureRandom();

    public AuthService(UserRepository userRepository,
                       AccountRepository accountRepository,
                       RefreshTokenRepository refreshTokenRepository,
                       PasswordResetTokenRepository passwordResetTokenRepository,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService,
                       FluxProperties properties) {
        this.userRepository = userRepository;
        this.accountRepository = accountRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordResetTokenRepository = passwordResetTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.properties = properties;
    }

    // -----------------------------------------------------------------
    // Registro
    // -----------------------------------------------------------------

    /**
     * Crea la cuenta y, en la misma transaccion, su cuenta de dinero
     * inicial. Sin ella el usuario entraria a una app donde no puede
     * registrar ni un movimiento.
     */
    @Transactional
    public AuthDtos.SessionResponse register(AuthDtos.RegisterRequest request) {
        String email = request.email().trim();

        if (userRepository.existsByEmailIgnoringCase(email)) {
            throw ApiException.conflict("EMAIL_TAKEN",
                    "Ya existe una cuenta con ese correo.");
        }

        User user = userRepository.save(new User(
                email,
                passwordEncoder.encode(request.password()),
                request.fullName().trim()));

        accountRepository.save(new Account(user, "Cuenta principal", AccountType.BANK, "ARS"));

        log.info("Usuario registrado: {}", user.getId());
        return openSession(user);
    }

    // -----------------------------------------------------------------
    // Inicio de sesion
    // -----------------------------------------------------------------

    /**
     * Valida credenciales y abre sesion.
     *
     * <p>Si el correo no existe se verifica la contrasena contra un
     * hash ficticio igual. Suena absurdo, pero sin eso el servidor
     * responderia mas rapido cuando el correo no existe, y esa
     * diferencia de tiempo permite averiguar quien tiene cuenta en la
     * aplicacion. Por el mismo motivo el mensaje de error no distingue
     * entre correo inexistente y contrasena incorrecta.
     */
    @Transactional
    public AuthDtos.SessionResponse login(AuthDtos.LoginRequest request) {
        Optional<User> found = userRepository.findByEmailIgnoringCase(request.email().trim());

        if (found.isEmpty()) {
            passwordEncoder.matches(request.password(),
                    "$2a$10$ZZZZZZZZZZZZZZZZZZZZZeZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZ");
            throw ApiException.unauthorized("INVALID_CREDENTIALS",
                    "El correo o la contrasena no son correctos.");
        }

        User user = found.get();
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw ApiException.unauthorized("INVALID_CREDENTIALS",
                    "El correo o la contrasena no son correctos.");
        }

        return openSession(user);
    }

    // -----------------------------------------------------------------
    // Refresco y cierre de sesion
    // -----------------------------------------------------------------

    /**
     * Cambia un token de refresco por un par nuevo.
     *
     * <p>Se aplica rotacion: el token presentado se revoca y se entrega
     * otro. Asi, si un token viejo se reutiliza, ya no sirve, y eso
     * delata que fue robado.
     */
    @Transactional
    public AuthDtos.SessionResponse refresh(AuthDtos.RefreshRequest request) {
        RefreshToken stored = refreshTokenRepository
                .findByTokenHash(sha256(request.refreshToken()))
                .orElseThrow(() -> ApiException.unauthorized("REFRESH_TOKEN_INVALID",
                        "El token de refresco no es valido. Inicia sesion de nuevo."));

        if (!stored.isUsable()) {
            throw ApiException.unauthorized("REFRESH_TOKEN_INVALID",
                    "El token de refresco vencio o fue revocado. Inicia sesion de nuevo.");
        }

        stored.revoke();
        return openSession(stored.getUser());
    }

    /** Cierra la sesion revocando el token de refresco presentado. */
    @Transactional
    public void logout(String refreshToken) {
        refreshTokenRepository.findByTokenHash(sha256(refreshToken))
                .ifPresent(RefreshToken::revoke);
        // Si el token no existe no se avisa: cerrar una sesion que ya
        // estaba cerrada es el resultado que el cliente queria.
    }

    // -----------------------------------------------------------------
    // Recuperacion de contrasena
    // -----------------------------------------------------------------

    /**
     * Genera un token de recuperacion.
     *
     * <p>Devuelve el token en lugar de enviarlo por correo: todavia no
     * hay servicio de correo configurado. En desarrollo queda en el log
     * para poder probar el flujo completo. <b>Antes de produccion hay
     * que enviarlo por correo y dejar de devolverlo en la respuesta</b>,
     * porque tal como esta cualquiera podria pedir el token de otro.
     */
    @Transactional
    public Optional<String> createPasswordResetToken(AuthDtos.ForgotPasswordRequest request) {
        Optional<User> found = userRepository.findByEmailIgnoringCase(request.email().trim());

        if (found.isEmpty()) {
            // No se revela que el correo no esta registrado: el
            // controlador responde lo mismo en los dos casos.
            log.info("Pedido de recuperacion para un correo no registrado.");
            return Optional.empty();
        }

        User user = found.get();
        passwordResetTokenRepository.invalidatePendingForUser(user.getId(), OffsetDateTime.now());

        String token = randomToken();
        passwordResetTokenRepository.save(new PasswordResetToken(
                user,
                sha256(token),
                OffsetDateTime.now().plus(properties.getPasswordReset().getTokenTtl())));

        log.info("Token de recuperacion generado para el usuario {}", user.getId());
        return Optional.of(token);
    }

    /**
     * Cambia la contrasena usando un token de recuperacion.
     *
     * <p>Al terminar se revocan todas las sesiones del usuario: si
     * alguien habia entrado con la contrasena anterior, queda fuera.
     * Es el objetivo principal de restablecer una contrasena.
     */
    @Transactional
    public void resetPassword(AuthDtos.ResetPasswordRequest request) {
        PasswordResetToken stored = passwordResetTokenRepository
                .findByTokenHash(sha256(request.token()))
                .orElseThrow(() -> ApiException.badRequest("RESET_TOKEN_INVALID",
                        "El enlace de recuperacion no es valido."));

        if (!stored.isUsable()) {
            throw ApiException.badRequest("RESET_TOKEN_INVALID",
                    "El enlace de recuperacion vencio o ya fue usado. Pide uno nuevo.");
        }

        User user = stored.getUser();
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        userRepository.save(user);

        stored.markUsed();
        int revoked = refreshTokenRepository.revokeAllForUser(user.getId(), OffsetDateTime.now());

        log.info("Contrasena restablecida para el usuario {}; {} sesiones revocadas",
                user.getId(), revoked);
    }

    // -----------------------------------------------------------------
    // Internos
    // -----------------------------------------------------------------

    /** Emite el par de tokens y guarda el hash del de refresco. */
    private AuthDtos.SessionResponse openSession(User user) {
        JwtService.IssuedToken access = jwtService.issueAccessToken(
                user.getId(), user.getEmail(), user.getFullName());

        String refreshToken = randomToken();
        OffsetDateTime refreshExpiresAt =
                OffsetDateTime.now().plus(properties.getJwt().getRefreshTokenTtl());

        refreshTokenRepository.save(new RefreshToken(user, sha256(refreshToken), refreshExpiresAt));

        return new AuthDtos.SessionResponse(
                access.value(),
                access.expiresAt(),
                refreshToken,
                refreshExpiresAt.toInstant(),
                AuthDtos.UserResponse.from(user));
    }

    /**
     * Token opaco y aleatorio, en Base64 apto para URL.
     *
     * <p>Se usa SecureRandom y no Math.random ni Random: los
     * generadores comunes son predecibles, y un token de sesion
     * predecible es un token adivinable.
     */
    private String randomToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /**
     * SHA-256 en hexadecimal: 64 caracteres, que es exactamente el
     * largo de las columnas token_hash.
     *
     * <p>Aqui si sirve un hash rapido, al contrario que con las
     * contrasenas. Los tokens son 32 bytes aleatorios: no hay
     * diccionario con el que probarlos, asi que no hace falta BCrypt.
     */
    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            // SHA-256 es obligatorio en toda JVM: esto no puede ocurrir.
            throw new IllegalStateException("SHA-256 no disponible en esta JVM", ex);
        }
    }
}
