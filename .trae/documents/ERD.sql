-- EXTENSIONS
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- =========================
-- TENANT
-- =========================
CREATE TABLE t_tenant (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    nome VARCHAR(255) NOT NULL,
    nif VARCHAR(50),
    tipo_entidade VARCHAR(50),
    email VARCHAR(255),
    telefone VARCHAR(50),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- =========================
-- USERS & SECURITY
-- =========================
CREATE TABLE t_user (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    nome VARCHAR(255) NOT NULL,
    email VARCHAR(255) UNIQUE NOT NULL,
    password_hash TEXT NOT NULL,
    ativo BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    CONSTRAINT fk_user_tenant FOREIGN KEY (tenant_id) REFERENCES t_tenant(id)
);

CREATE TABLE t_role (
    id SERIAL PRIMARY KEY,
    nome VARCHAR(100) UNIQUE NOT NULL
);

CREATE TABLE t_permission (
    id SERIAL PRIMARY KEY,
    nome VARCHAR(100) UNIQUE NOT NULL
);

CREATE TABLE t_user_role (
    user_id UUID,
    role_id INT,
    PRIMARY KEY (user_id, role_id),
    FOREIGN KEY (user_id) REFERENCES t_user(id),
    FOREIGN KEY (role_id) REFERENCES t_role(id)
);

CREATE TABLE t_role_permission (
    role_id INT,
    permission_id INT,
    PRIMARY KEY (role_id, permission_id),
    FOREIGN KEY (role_id) REFERENCES t_role(id),
    FOREIGN KEY (permission_id) REFERENCES t_permission(id)
);

-- =========================
-- CLIENTES
-- =========================
CREATE TABLE t_cliente (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    tipo VARCHAR(50),
    nome VARCHAR(255) NOT NULL,
    nif VARCHAR(50),
    email VARCHAR(255),
    telefone VARCHAR(50),
    morada TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (tenant_id) REFERENCES t_tenant(id)
);

-- =========================
-- PROCESSOS
-- =========================
CREATE TABLE t_processo (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    cliente_id UUID NOT NULL,
    numero_processo VARCHAR(100),
    tipo_processo VARCHAR(100),
    area_juridica VARCHAR(100),
    tribunal VARCHAR(255),
    estado VARCHAR(50),
    data_inicio DATE,
    data_fim DATE,
    descricao TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (tenant_id) REFERENCES t_tenant(id),
    FOREIGN KEY (cliente_id) REFERENCES t_cliente(id)
);

CREATE TABLE t_parte (
    id SERIAL PRIMARY KEY,
    processo_id UUID NOT NULL,
    nome VARCHAR(255),
    tipo VARCHAR(50),
    FOREIGN KEY (processo_id) REFERENCES t_processo(id)
);

-- =========================
-- FASES
-- =========================
CREATE TABLE t_fase_processual (
    id SERIAL PRIMARY KEY,
    nome VARCHAR(100) NOT NULL
);

CREATE TABLE t_processo_fase (
    id SERIAL PRIMARY KEY,
    processo_id UUID NOT NULL,
    fase_id INT NOT NULL,
    data_inicio DATE,
    data_fim DATE,
    ativa BOOLEAN DEFAULT TRUE,
    FOREIGN KEY (processo_id) REFERENCES t_processo(id),
    FOREIGN KEY (fase_id) REFERENCES t_fase_processual(id)
);

-- =========================
-- AGENDA
-- =========================
CREATE TABLE t_evento (
    id SERIAL PRIMARY KEY,
    tenant_id UUID NOT NULL,
    processo_id UUID,
    tipo VARCHAR(50),
    titulo VARCHAR(255),
    descricao TEXT,
    data_inicio TIMESTAMP,
    data_fim TIMESTAMP,
    prioridade VARCHAR(20),
    concluido BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (tenant_id) REFERENCES t_tenant(id),
    FOREIGN KEY (processo_id) REFERENCES t_processo(id)
);

CREATE TABLE t_notificacao (
    id SERIAL PRIMARY KEY,
    user_id UUID NOT NULL,
    evento_id INT,
    tipo VARCHAR(50),
    enviado BOOLEAN DEFAULT FALSE,
    data_envio TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES t_user(id),
    FOREIGN KEY (evento_id) REFERENCES t_evento(id)
);

-- =========================
-- DOCUMENTOS
-- =========================
CREATE TABLE t_documento (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    processo_id UUID,
    cliente_id UUID,
    nome VARCHAR(255),
    tipo VARCHAR(50),
    caminho_arquivo TEXT,
    versao INT DEFAULT 1,
    tamanho BIGINT,
    mime_type VARCHAR(100),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (tenant_id) REFERENCES t_tenant(id),
    FOREIGN KEY (processo_id) REFERENCES t_processo(id),
    FOREIGN KEY (cliente_id) REFERENCES t_cliente(id)
);

CREATE TABLE t_documento_historico (
    id SERIAL PRIMARY KEY,
    documento_id UUID NOT NULL,
    versao INT,
    caminho_arquivo TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (documento_id) REFERENCES t_documento(id)
);

-- =========================
-- MOVIMENTAÇÕES
-- =========================
CREATE TABLE t_movimentacao (
    id SERIAL PRIMARY KEY,
    processo_id UUID NOT NULL,
    tipo VARCHAR(50),
    descricao TEXT,
    data TIMESTAMP,
    prazo_id INT,
    FOREIGN KEY (processo_id) REFERENCES t_processo(id),
    FOREIGN KEY (prazo_id) REFERENCES t_evento(id)
);

-- =========================
-- FINANCEIRO
-- =========================
CREATE TABLE t_honorario (
    id SERIAL PRIMARY KEY,
    processo_id UUID NOT NULL,
    valor_total NUMERIC(12,2),
    descricao TEXT,
    data_acordo DATE,
    FOREIGN KEY (processo_id) REFERENCES t_processo(id)
);

CREATE TABLE t_pagamento (
    id SERIAL PRIMARY KEY,
    honorario_id INT NOT NULL,
    valor_pago NUMERIC(12,2),
    data_pagamento DATE,
    metodo VARCHAR(50),
    FOREIGN KEY (honorario_id) REFERENCES t_honorario(id)
);

CREATE TABLE t_conta_corrente (
    id SERIAL PRIMARY KEY,
    cliente_id UUID NOT NULL,
    saldo NUMERIC(12,2) DEFAULT 0,
    FOREIGN KEY (cliente_id) REFERENCES t_cliente(id)
);

-- =========================
-- AUDITORIA
-- =========================
CREATE TABLE t_audit_log (
    id SERIAL PRIMARY KEY,
    user_id UUID,
    entidade VARCHAR(100),
    entidade_id VARCHAR(100),
    acao VARCHAR(50),
    dados_anteriores JSONB,
    dados_novos JSONB,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES t_user(id)
);
CREATE INDEX idx_user_tenant ON t_user(tenant_id);
CREATE INDEX idx_cliente_tenant ON t_cliente(tenant_id);
CREATE INDEX idx_processo_tenant ON t_processo(tenant_id);
CREATE INDEX idx_processo_cliente ON t_processo(cliente_id);
CREATE INDEX idx_evento_processo ON t_evento(processo_id);
CREATE INDEX idx_documento_processo ON t_documento(processo_id);
