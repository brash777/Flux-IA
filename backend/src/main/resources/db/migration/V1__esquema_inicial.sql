-- =====================================================================
-- Flux IA - Esquema inicial
--
-- Convencion clave: los montos SIEMPRE se guardan positivos y la
-- direccion del dinero vive en la columna "type" (INCOME / EXPENSE).
-- Asi el saldo es una sola expresion SQL y un gasto mal firmado no
-- puede inflar el saldo. Toda cifra de la app sale de esta tabla.
-- =====================================================================

-- gen_random_uuid() viene en esta extension (incluida en PostgreSQL 13+).
CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- ---------------------------------------------------------------------
-- usuarios
-- ---------------------------------------------------------------------
CREATE TABLE users (
    id             UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    email          VARCHAR(255) NOT NULL,
    password_hash  VARCHAR(255) NOT NULL,
    full_name      VARCHAR(120) NOT NULL,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- El correo es unico sin distinguir mayusculas: "Diego@x.com" y
-- "diego@x.com" son la misma cuenta.
CREATE UNIQUE INDEX ux_users_email_lower ON users (lower(email));

-- ---------------------------------------------------------------------
-- cuentas (efectivo, banco, tarjeta)
-- ---------------------------------------------------------------------
CREATE TABLE accounts (
    id          UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    name        VARCHAR(80)  NOT NULL,
    type        VARCHAR(20)  NOT NULL,
    -- VARCHAR y no CHAR: CHAR rellena con espacios a la derecha, lo que
    -- obliga a recortar el valor en cada lectura.
    currency    VARCHAR(3)   NOT NULL DEFAULT 'ARS',
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_accounts_type     CHECK (type IN ('CASH', 'BANK', 'CARD')),
    CONSTRAINT ux_accounts_user_name UNIQUE (user_id, name)
);

CREATE INDEX ix_accounts_user ON accounts (user_id);

-- ---------------------------------------------------------------------
-- categorias
--
-- user_id NULL significa "categoria del sistema": la comparten todos
-- los usuarios y nadie puede editarla. Con user_id, es una categoria
-- propia que el usuario creo.
-- ---------------------------------------------------------------------
CREATE TABLE categories (
    id          UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID         NULL REFERENCES users (id) ON DELETE CASCADE,
    slug        VARCHAR(40)  NOT NULL,
    name        VARCHAR(80)  NOT NULL,
    icon        VARCHAR(16)  NOT NULL DEFAULT '',
    color       VARCHAR(9)   NOT NULL DEFAULT '#6c63ff',
    kind        VARCHAR(10)  NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_categories_kind CHECK (kind IN ('INCOME', 'EXPENSE'))
);

-- Dos indices parciales en lugar de un UNIQUE comun, porque en
-- PostgreSQL dos filas con user_id NULL no se consideran duplicadas.
CREATE UNIQUE INDEX ux_categories_system_slug
    ON categories (slug) WHERE user_id IS NULL;
CREATE UNIQUE INDEX ux_categories_user_slug
    ON categories (user_id, slug) WHERE user_id IS NOT NULL;

-- ---------------------------------------------------------------------
-- transacciones: la unica fuente de verdad de todas las cifras
-- ---------------------------------------------------------------------
CREATE TABLE transactions (
    id           UUID           PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id      UUID           NOT NULL REFERENCES users (id)      ON DELETE CASCADE,
    account_id   UUID           NOT NULL REFERENCES accounts (id)   ON DELETE CASCADE,
    category_id  UUID           NOT NULL REFERENCES categories (id) ON DELETE RESTRICT,
    description  VARCHAR(140)   NOT NULL,
    amount       NUMERIC(14, 2) NOT NULL,
    type         VARCHAR(10)    NOT NULL,
    occurred_at  TIMESTAMPTZ    NOT NULL,
    created_at   TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ    NOT NULL DEFAULT now(),
    CONSTRAINT ck_transactions_type   CHECK (type IN ('INCOME', 'EXPENSE')),
    CONSTRAINT ck_transactions_amount CHECK (amount > 0)
);

-- Sostiene el listado de movimientos, que siempre es "los mios, por fecha".
CREATE INDEX ix_transactions_user_date     ON transactions (user_id, occurred_at DESC);
-- Sostiene el reporte de gastos por categoria.
CREATE INDEX ix_transactions_user_category ON transactions (user_id, category_id);
CREATE INDEX ix_transactions_account       ON transactions (account_id);

-- ---------------------------------------------------------------------
-- refresh tokens: permiten cerrar sesion de verdad
--
-- Se guarda el hash, no el token. Si alguien lee la base de datos no
-- puede usar los tokens que encuentre.
-- ---------------------------------------------------------------------
CREATE TABLE refresh_tokens (
    id          UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    token_hash  VARCHAR(64)  NOT NULL UNIQUE,
    expires_at  TIMESTAMPTZ  NOT NULL,
    revoked_at  TIMESTAMPTZ  NULL,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX ix_refresh_tokens_user ON refresh_tokens (user_id);

-- ---------------------------------------------------------------------
-- tokens de recuperacion de contrasena (mismo criterio: solo el hash)
-- ---------------------------------------------------------------------
CREATE TABLE password_reset_tokens (
    id          UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    token_hash  VARCHAR(64)  NOT NULL UNIQUE,
    expires_at  TIMESTAMPTZ  NOT NULL,
    used_at     TIMESTAMPTZ  NULL,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX ix_password_reset_tokens_user ON password_reset_tokens (user_id);
