-- V29: Inserção de órgãos, conjuntos de dados e camadas de referência iniciais (GEOESPACIAIS)
-- Restrição estrita: apenas comandos INSERT (sem ALTER TABLE, CREATE ou DROP)

-- 1. Inserir órgãos emissores presentes na pasta GEOESPACIAIS
INSERT INTO orgao (nome, sigla, descricao, ativo) VALUES
    ('Fundação Nacional dos Povos Indígenas', 'FUNAI', 'Órgão indigenista oficial (Terras Indígenas)', true),
    ('Instituto Brasileiro do Meio Ambiente e dos Recursos Naturais Renováveis', 'IBAMA', 'Fiscalização ambiental e áreas embargadas', true),
    ('Instituto Chico Mendes de Conservação da Biodiversidade', 'ICMBio', 'Gestão de Unidades de Conservação Federais', true),
    ('Serviço Florestal Brasileiro', 'SFB', 'Cadastro Nacional de Florestas Públicas e SICAR', true),
    ('Ministério do Meio Ambiente e Mudança do Clima', 'MMA', 'Cadastro Nacional de Unidades de Conservação (CNUC)', true),
    ('Sistema Nacional de Cadastro Ambiental Rural', 'SICAR', 'Base INCAR de imóveis rurais, APP e Reserva Legal', true)
ON CONFLICT (nome) DO NOTHING;

-- 2. Inserir conjuntos de dados correspondentes às categorias da pasta GEOESPACIAIS
INSERT INTO conjunto (nome, descricao, ativo) VALUES
    ('Terras indígenas', 'Poligonais de terras indígenas homologadas e em regularização (FUNAI)', true),
    ('Áreas embargadas', 'Polígonos de termos de embargo e infrações ambientais (IBAMA)', true),
    ('Unidades de conservação', 'Limites de Unidades de Conservação federais e estaduais (ICMBio / MMA CNUC)', true),
    ('Florestas públicas', 'Cadastro Nacional de Florestas Públicas (SFB CNFP)', true),
    ('Assentamentos e Terras quilombolas', 'Projetos de assentamento e territórios quilombolas (INCRA)', true),
    ('Queimadas e Desmatamento', 'Focos de queimadas BDQueimadas e polígonos PRODES de desmatamento (INPE)', true),
    ('Limites de biomas', 'Delimitação territorial dos biomas brasileiros 1:250.000 e 1:5.000.000 (IBGE)', true)
ON CONFLICT (nome) DO NOTHING;

-- 3. Inserir registros iniciais na tabela camada_referencia para cruzamento espacial no Cálculo Analítico
INSERT INTO camada_referencia (fonte, nome_camada, identificador_origem, geom, atributos, data_referencia)
SELECT * FROM (
    VALUES
        (
            'INCAR_APP',
            'Área de Preservação Permanente (SICAR/INCAR)',
            'APP-GO-5208707-001',
            ST_GeomFromText('POLYGON((-49.2800 -16.6700, -49.2500 -16.6700, -49.2500 -16.7000, -49.2800 -16.7000, -49.2800 -16.6700))', 4674),
            '{"uf": "GO", "municipio": "Goiânia", "tipo": "APP Hidrográfica"}'::jsonb,
            DATE '2026-01-15'
        ),
        (
            'INCAR_RESERVA_LEGAL',
            'Reserva Legal Averbada (SICAR/INCAR)',
            'RL-GO-5208707-001',
            ST_GeomFromText('POLYGON((-49.2700 -16.6750, -49.2400 -16.6750, -49.2400 -16.6950, -49.2700 -16.6950, -49.2700 -16.6750))', 4674),
            '{"uf": "GO", "municipio": "Goiânia", "situacao": "Averbada"}'::jsonb,
            DATE '2026-01-15'
        ),
        (
            'INPE_DESMATAMENTO_CERRADO',
            'Desmatamento Anual Cerrado PRODES (INPE)',
            'PRODES-CER-2026-0001',
            ST_GeomFromText('POLYGON((-49.2650 -16.6780, -49.2450 -16.6780, -49.2450 -16.6920, -49.2650 -16.6920, -49.2650 -16.6780))', 4674),
            '{"bioma": "Cerrado", "ano": 2026, "fonte": "PRODES"}'::jsonb,
            DATE '2026-07-17'
        ),
        (
            'INPE_DESMATAMENTO_MATA_ATLANTICA',
            'Desmatamento Anual Mata Atlântica PRODES (INPE)',
            'PRODES-MA-2026-0001',
            ST_GeomFromText('POLYGON((-51.4800 -25.3800, -51.4500 -25.3800, -51.4500 -25.4100, -51.4800 -25.4100, -51.4800 -25.3800))', 4674),
            '{"bioma": "Mata Atlântica", "ano": 2026, "fonte": "PRODES"}'::jsonb,
            DATE '2026-08-06'
        ),
        (
            'IBAMA_EMBARGO',
            'Áreas Embargadas (IBAMA)',
            'TAD-PR-91211-001',
            ST_GeomFromText('POLYGON((-51.4750 -25.3850, -51.4550 -25.3850, -51.4550 -25.4050, -51.4750 -25.4050, -51.4750 -25.3850))', 4674),
            '{"uf": "PR", "orgao": "IBAMA", "status": "Embargado"}'::jsonb,
            DATE '2026-08-01'
        ),
        (
            'FUNAI_TERRA_INDIGENA',
            'Terras Indígenas (FUNAI)',
            'TI-FUNAI-40101',
            ST_GeomFromText('POLYGON((-52.5500 -25.4200, -52.5000 -25.4200, -52.5000 -25.4700, -52.5500 -25.4700, -52.5500 -25.4200))', 4674),
            '{"terrai_nom": "Rio das Cobras", "fase_ti": "Regularizada", "uf_sigla": "PR"}'::jsonb,
            DATE '2026-06-01'
        ),
        (
            'ICMBIO_UC_FEDERAL',
            'Unidades de Conservação Federais (ICMBio)',
            'UC-FED-0000.00.0191',
            ST_GeomFromText('POLYGON((-54.4500 -25.5500, -54.3800 -25.5500, -54.3800 -25.6200, -54.4500 -25.6200, -54.4500 -25.5500))', 4674),
            '{"nome_uc": "Parque Nacional do Iguaçu", "grupo": "PI", "esfera": "Federal"}'::jsonb,
            DATE '2026-08-01'
        ),
        (
            'INCRA_QUILOMBOLA',
            'Áreas de Quilombolas (INCRA)',
            'QUIL-INCRA-001',
            ST_GeomFromText('POLYGON((-49.9500 -24.7500, -49.9200 -24.7500, -49.9200 -24.7800, -49.9500 -24.7800, -49.9500 -24.7500))', 4674),
            '{"nm_comunid": "Paiol de Telha", "uf": "PR", "esfera": "Federal"}'::jsonb,
            DATE '2026-05-01'
        ),
        (
            'INCRA_ASSENTAMENTO',
            'Assentamentos Brasil (INCRA)',
            'SIPRA-PR0001000',
            ST_GeomFromText('POLYGON((-52.2000 -25.3000, -52.1500 -25.3000, -52.1500 -25.3500, -52.2000 -25.3500, -52.2000 -25.3000))', 4674),
            '{"nome_proje": "PA Ireno Alves dos Santos", "uf": "PR"}'::jsonb,
            DATE '2026-05-01'
        ),
        (
            'SFB_FLORESTA_PUBLICA',
            'Cadastro Nacional de Florestas Públicas 2025 (SFB)',
            'CNFP-2025-0001',
            ST_GeomFromText('POLYGON((-49.3000 -16.6600, -49.2600 -16.6600, -49.2600 -16.6900, -49.3000 -16.6900, -49.3000 -16.6600))', 4674),
            '{"estagio": "Destinada", "bioma": "Cerrado", "ano": 2025}'::jsonb,
            DATE '2025-12-01'
        ),
        (
            'INPE_QUEIMADA',
            'Focos de Queimadas BDQueimadas (INPE)',
            'BDQ-2026-0001',
            ST_GeomFromText('POLYGON((-49.2560 -16.6810, -49.2540 -16.6810, -49.2540 -16.6830, -49.2560 -16.6830, -49.2560 -16.6810))', 4674),
            '{"satelite": "AQUA_M-T", "bioma": "Cerrado", "municipio": "GOIANIA"}'::jsonb,
            DATE '2026-09-01'
        ),
        (
            'MMA_UC_CNUC',
            'Cadastro Nacional de Unidades de Conservação (MMA)',
            'CNUC-0000.00.0001',
            ST_GeomFromText('POLYGON((-49.2550 -15.9850, -49.2450 -15.9850, -49.2450 -15.9950, -49.2550 -15.9950, -49.2550 -15.9850))', 4674),
            '{"nome_uc": "APA da Bacia do Rio Descoberto", "esfera": "Federal", "uf": "GO"}'::jsonb,
            DATE '2026-07-01'
        )
) AS v(fonte, nome_camada, identificador_origem, geom, atributos, data_referencia)
WHERE NOT EXISTS (
    SELECT 1
      FROM camada_referencia cr
     WHERE cr.fonte = v.fonte
       AND cr.identificador_origem = v.identificador_origem
);
