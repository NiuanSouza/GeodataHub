/**
 * @file /mocks para testes automatizados.
 *
 * REGRA DE ARQUITETURA:
 * Nenhum mock deve ser hardcoded no código de produção (serviços, views ou componentes).
 * Todos os mocks devem residir exclusivamente aqui (/mocks) para uso em testes automatizados.
 */

// ==========================================
// TIPOS — espelho exato do contrato da API
// ==========================================

/** Item de checklist retornado pela API para as etapas de Validação e Tratamento */
export interface ProcessStageCheck {
  id: string
  label: string
  description: string
  status: 'success' | 'error' | 'pending'
  detail: string
}

/** Linha de resultado analítico retornada pela API para a etapa de Publicação */
export interface ProcessAnalyticsRow {
  param: string
  result: string
  reference: string
  status: 'success' | 'error' | 'warning'
  statusLabel: string
}

/**
 * Objeto principal de um processo.
 * Alimentado por: GET /api/processes/{id}
 *
 * Campos de resposta esperados (JSON):
 * {
 *   "id": "string",                   // ex: "#993A-B12"
 *   "dataset": "string",              // nome do conjunto de dados
 *   "stage": "Ingestão"|"Validação"|"Tratamento"|"Publicação",
 *   "status": "Concluída"|"Em andamento"|"Aguardando validação"|"Falhou",
 *   "dateTime": "string",                 // ex: "07/09/2026"
 *   "layerName": "string",            // nome da camada
 *   "source": "string",               // órgão/fonte dos dados
 *   "year": "string",                 // ano base
 *   "epsg": "string",                 // sistema de referência, ex: "EPSG:4674 (SIRGAS 2000)"
 *   "description": "string",
 *   "originalFileUrl": "string",      // URL para download do arquivo original
 *   "integrityHash": "string",        // hash MD5/SHA do arquivo original (exibido apenas na etapa Ingestão)
 *   "mapCoordinates": [[lat, lng]],   // polígono de delimitação
 *   "logs": {                         // histórico de logs por etapa
 *     "Ingestão": ["string"],
 *     "Validação": ["string"],
 *     "Tratamento": ["string"],
 *     "Publicação": ["string"]
 *   },
 *   "pauseReason": "string|null",     // motivo de pausa (apenas se status = Falhou ou Aguardando validação)
 *   "validationChecks": [             // checklist de Validação (ver ProcessStageCheck)
 *     { "id", "label", "description", "status": "success"|"error"|"pending", "detail" }
 *   ],
 *   "treatmentChecks": [...],         // checklist de Tratamento (mesma estrutura)
 *   "analyticsData": [                // tabela de resultados analíticos (ver ProcessAnalyticsRow)
 *     { "param", "result", "reference", "status": "success"|"error"|"warning", "statusLabel" }
 *   ]
 * }
 */
export interface QuarantineRecord {
  recordId: string
  motivo: string
  etapaOrigem: string
  execucaoId: string
  dataRejeicao: string
  campo: string
  valorEncontrado: string
  valorEsperado: string
}

export interface ProcessLogDetails {
  id: string
  dataSource?: 'API' | 'DEMONSTRACAO'
  dataset: string
  stage: string
  status: 'Concluída' | 'Em andamento' | 'Aguardando validação' | 'Falhou' | 'Fechado'
  dateTime: string
  layerName: string
  source: string
  year: string
  epsg: string
  epsgDetected?: string
  description: string
  /** Metadados expandidos (US05) */
  competencia?: string
  formato?: string
  classificacaoAcesso?: string
  coberturaEspacial?: string
  coberturaTemporalInicio?: string
  coberturaTemporalFim?: string
  /** Hash do arquivo original — exibido apenas na tela de Ingestão, NÃO no dashboard */
  integrityHash?: string
  originalFileUrl: string
  mapCoordinates: [number, number][]
  logs: Record<string, string[]>
  /** Motivo de pausa em quarentena — presente apenas se status = 'Falhou' ou 'Aguardando validação' */
  pauseReason?: string
  /** Editor/auditor alocado para corrigir erro de validação ou tratamento */
  assignedEditor?: string
  /** Checks automáticos da etapa de Validação — populados pelo backend */
  validationChecks?: ProcessStageCheck[]
  /** Checks automáticos da etapa de Tratamento — populados pelo backend */
  treatmentChecks?: ProcessStageCheck[]
  /** Resultados analíticos calculados pelo backend — exibidos na etapa de Publicação */
  analyticsData?: ProcessAnalyticsRow[]
  /** Registros na quarentena */
  quarantineRecords?: QuarantineRecord[]
}

// ==========================================
// HELPERS — blocos de dados reutilizáveis
// ==========================================

export const validationChecksOk: ProcessStageCheck[] = [
  {
    id: 'limites',
    label: 'Check Limites Estaduais',
    description: 'Verifica se nenhum polígono ultrapassa a divisa de estado.',
    status: 'success',
    detail: 'Nenhum polígono ultrapassa a divisa de estado.',
  },
  {
    id: 'duplicidade',
    label: 'Check Duplicidade CAR',
    description: 'Verifica unicidade dos números de recibo na base.',
    status: 'success',
    detail: 'Números de recibo únicos na base.',
  },
  {
    id: 'sobreposicao',
    label: 'Check Sobreposição (Áreas Protegidas)',
    description: 'Verifica conflitos com Unidades de Conservação ou Terras Indígenas.',
    status: 'success',
    detail: 'Sem conflito com Unidades de Conservação ou Terras Indígenas.',
  },
  {
    id: 'geometria',
    label: 'Check Integridade Geométrica',
    description: 'Verifica topologia e geometrias válidas nos polígonos.',
    status: 'success',
    detail: 'Todas as geometrias são válidas.',
  },
]

const validationChecksError: ProcessStageCheck[] = [
  {
    id: 'limites',
    label: 'Check Limites Estaduais',
    description: 'Verifica se nenhum polígono ultrapassa a divisa de estado.',
    status: 'success',
    detail: 'Nenhum polígono ultrapassa a divisa de estado.',
  },
  {
    id: 'duplicidade',
    label: 'Check Duplicidade CAR',
    description: 'Verifica unicidade dos números de recibo na base.',
    status: 'success',
    detail: 'Números de recibo únicos na base.',
  },
  {
    id: 'sobreposicao',
    label: 'Check Sobreposição (Áreas Protegidas)',
    description: 'Verifica conflitos com Unidades de Conservação ou Terras Indígenas.',
    status: 'error',
    detail: 'Sobreposição detectada no polígono 45.',
  },
  {
    id: 'geometria',
    label: 'Check Integridade Geométrica',
    description: 'Verifica topologia e geometrias válidas nos polígonos.',
    status: 'pending',
    detail: 'Aguardando resolução do erro de sobreposição.',
  },
]

export const treatmentChecksOk = (epsg: string): ProcessStageCheck[] => [
  {
    id: 'reproj',
    label: 'Reprojeção de Coordenadas',
    description: 'Reprojetar geometrias para o sistema de referência padrão (EPSG:4674).',
    status: 'success',
    detail: `Dados reprojetados com sucesso para ${epsg}.`,
  },
  {
    id: 'normaliz',
    label: 'Normalização de Atributos',
    description: 'Padronizar nomes de colunas e tipos de dados conforme schema interno.',
    status: 'success',
    detail: 'Todos os atributos normalizados sem inconsistências.',
  },
  {
    id: 'topology',
    label: 'Correção Topológica',
    description: 'Reparar auto-interseções, buracos e geometrias inválidas.',
    status: 'success',
    detail: 'Topologia corrigida com sucesso.',
  },
  {
    id: 'simplif',
    label: 'Simplificação de Geometrias',
    description: 'Reduzir complexidade mantendo fidelidade topológica (Douglas-Peucker).',
    status: 'success',
    detail: 'Geometrias simplificadas com tolerância de 0.5m.',
  },
]

const treatmentChecksInProgress = (epsg: string): ProcessStageCheck[] => [
  {
    id: 'reproj',
    label: 'Reprojeção de Coordenadas',
    description: 'Reprojetar geometrias para o sistema de referência padrão (EPSG:4674).',
    status: 'success',
    detail: `Dados reprojetados com sucesso para ${epsg}.`,
  },
  {
    id: 'normaliz',
    label: 'Normalização de Atributos',
    description: 'Padronizar nomes de colunas e tipos de dados conforme schema interno.',
    status: 'success',
    detail: 'Todos os atributos normalizados sem inconsistências.',
  },
  {
    id: 'topology',
    label: 'Correção Topológica',
    description: 'Reparar auto-interseções, buracos e geometrias inválidas.',
    status: 'pending',
    detail: 'Processando correção topológica...',
  },
  {
    id: 'simplif',
    label: 'Simplificação de Geometrias',
    description: 'Reduzir complexidade mantendo fidelidade topológica (Douglas-Peucker).',
    status: 'pending',
    detail: 'Aguardando etapa anterior.',
  },
]

export const analyticsDataOk: ProcessAnalyticsRow[] = [
  {
    param: 'Cálculo de Área Total Contínua',
    result: '12.540 ha',
    reference: '—',
    status: 'success',
    statusLabel: 'Calculado',
  },
  {
    param: 'Proporção de Reserva Legal',
    result: '20,0% (Exato)',
    reference: 'Cód. Florestal (Bioma Cerrado)',
    status: 'success',
    statusLabel: 'Regular',
  },
  {
    param: 'Área de APP Hídrica',
    result: '1.254 ha',
    reference: 'Art. 4º — Lei 12.651/2012',
    status: 'success',
    statusLabel: 'Conforme',
  },
  {
    param: 'Sobreposição com Áreas Embargadas',
    result: '0 ha',
    reference: 'Cadastro IBAMA',
    status: 'success',
    statusLabel: 'Livre',
  },
]

// ==========================================
// REGISTROS MOCK — 1 por status possível
// ==========================================
// Cada registro usa o mesmo ID do dashboard para que a navegação
// "Ver Log Completo" → tela de detalhes funcione corretamente.

export const mockProcessDetails: Record<string, ProcessLogDetails> = {
  // ─── STATUS: Concluída ──────────────────────────────────────────────────────
  '#993A-B12': {
    id: '#993A-B12',
    dataset: 'Imóveis rurais',
    stage: 'Publicação',
    status: 'Concluída',
    dateTime: '07/09/2026 13:00',
    layerName: 'Imóveis Rurais 2023',
    source: 'IBGE',
    year: '2023',
    epsg: 'EPSG:4674 (SIRGAS 2000)',
    description: 'Atualização da base de imóveis rurais do exercício de 2023.',
    integrityHash: 'sha256:a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2',
    originalFileUrl: '#',
    mapCoordinates: [
      [-15.793, -47.882],
      [-15.793, -47.8],
      [-15.85, -47.8],
      [-15.85, -47.882],
    ],
    logs: {
      Ingestão: ['[2026-09-07 10:00:00] Arquivo recebido. Hash verificado. Ingestão concluída.'],
      Validação: ['[2026-09-07 10:05:00] Todas as verificações passaram. Validação concluída.'],
      Tratamento: ['[2026-09-07 10:10:00] Reprojeção, normalização e simplificação concluídas.'],
      Publicação: [
        '[2026-09-07 10:15:00] Cálculo analítico concluído.',
        '[2026-09-07 10:20:00] Dados publicados na base de produção. Processo concluído.',
      ],
    },
    validationChecks: validationChecksOk,
    treatmentChecks: treatmentChecksOk('EPSG:4674 (SIRGAS 2000)'),
    analyticsData: analyticsDataOk,
  },

  // ─── STATUS: Em andamento (parado em Tratamento) ────────────────────────────
  '#994C-F88': {
    id: '#994C-F88',
    dataset: 'Malha municipal',
    stage: 'Tratamento',
    status: 'Em andamento',
    dateTime: '07/09/2026 13:00',
    layerName: 'Malha Municipal 2022',
    source: 'Prefeitura Municipal',
    year: '2022',
    epsg: 'EPSG:31983 (SIRGAS 2000 / UTM zone 23S)',
    description: 'Malha municipal atualizada fornecida pela prefeitura para o exercício de 2022.',
    integrityHash: 'sha256:9f8e7d6c5b4a3f2e1d0c9b8a7f6e5d4c3b2a1f0e9d8c7b6a5f4e3d2c1b0a9f8',
    originalFileUrl: '#',
    mapCoordinates: [
      [-23.55, -46.633],
      [-23.55, -46.6],
      [-23.58, -46.6],
      [-23.58, -46.633],
    ],
    logs: {
      Ingestão: ['[2026-09-07 11:00:00] Arquivo recebido. Hash verificado. Ingestão concluída.'],
      Validação: ['[2026-09-07 11:05:00] Todas as verificações passaram. Validação concluída.'],
      Tratamento: [
        '[2026-09-07 11:10:00] Reprojeção concluída.',
        '[2026-09-07 11:12:00] Normalização concluída.',
        '[2026-09-07 11:14:00] Correção topológica em andamento...',
      ],
      Publicação: [],
    },
    validationChecks: validationChecksOk,
    treatmentChecks: treatmentChecksInProgress('EPSG:31983 (SIRGAS 2000 / UTM zone 23S)'),
  },

  // ─── STATUS: Aguardando validação (quarentena por sobreposição) ─────────────────────
  '#995X-Z01': {
    id: '#995X-Z01',
    dataset: 'Reserva legal',
    stage: 'Validação',
    status: 'Aguardando validação',
    dateTime: '07/09/2026 13:00',
    layerName: 'Reserva Legal 2023',
    source: 'Órgão Estadual ABC',
    year: '2023',
    epsg: 'EPSG:4674 (SIRGAS 2000)',
    epsgDetected: 'EPSG:4674 (SIRGAS 2000)',
    description: 'Dados de reserva legal submetidos pelo órgão estadual para o exercício de 2023.',
    integrityHash: 'sha256:8f4e3b2a1c5d6e7f8a9b0c1d2e3f4a5b6c7d8e9f0a1b2c3d4e5f6a7b8c9d0e1f',
    pauseReason: 'Sobreposição detectada no polígono 45. Necessário alocar editor.',
    originalFileUrl: '#',
    mapCoordinates: [
      [-19.916, -43.934],
      [-19.916, -43.9],
      [-19.95, -43.9],
      [-19.95, -43.934],
    ],
    logs: {
      Ingestão: [
        '[2026-09-07 12:00:00] Arquivo recebido. Hash verificado. Ingestão concluída com sucesso.',
      ],
      Validação: [
        '[2026-09-07 12:05:00] Check de limites estaduais: OK.',
        '[2026-09-07 12:05:01] Check de duplicidade CAR: OK.',
        '[2026-09-07 12:05:02] Check de sobreposição: ERRO — Polígono 45 sobrepõe Unidade de Conservação.',
        '[2026-09-07 12:05:03] Processo paralisado. Necessário alocar editor para correção manual.',
      ],
      Tratamento: [],
      Publicação: [],
    },
    validationChecks: validationChecksError,
    quarantineRecords: [
      {
        recordId: 'POL-45',
        motivo: 'Sobreposição com Unidade de Conservação',
        etapaOrigem: 'Validação',
        execucaoId: 'EXEC-995X-Z01-01',
        dataRejeicao: '07/09/2026 12:05:02',
        campo: 'geom',
        valorEncontrado: 'Polygon(...)',
        valorEsperado: 'Sem intersecção',
      }
    ],
  },

  // ─── STATUS: Falhou (erro na etapa de Validação — Ingestão concluída com sucesso) ──
  '#996R-T55': {
    id: '#996R-T55',
    dataset: 'Uso e cobertura do solo',
    stage: 'Validação',
    status: 'Falhou',
    dateTime: '07/09/2026 13:00',
    layerName: 'Uso Solo MapBiomas 2021',
    source: 'MapBiomas',
    year: '2021',
    epsg: 'EPSG:4326',
    epsgDetected: 'EPSG:4326',
    description:
      'Dados brutos de uso e cobertura do solo provenientes do MapBiomas (coleção 2021).',
    integrityHash: 'sha256:x9y8z7w6v5u4t3s2r1q0p9o8n7m6l5k4j3i2h1g0f9e8d7c6b5a4z3y2x1w0v9u',
    pauseReason:
      'Erro de integridade geométrica no shapefile detectado na Validação. Necessário alocar editor.',
    originalFileUrl: '#',
    mapCoordinates: [
      [-3.119, -60.021],
      [-3.119, -60.0],
      [-3.15, -60.0],
      [-3.15, -60.021],
    ],
    logs: {
      Ingestão: [
        '[2026-09-07 10:15:00] Arquivo recebido e descompactado com sucesso após upload.',
        '[2026-09-07 10:15:02] Hash verificado. Ingestão concluída com sucesso.',
      ],
      Validação: [
        '[2026-09-07 10:15:05] Iniciando testes de validação espacial...',
        '[2026-09-07 10:15:08] ERRO — Geometria inválida detectada no polígono 12.',
        '[2026-09-07 10:15:10] Processo paralisado. Necessário alocar editor para correção.',
      ],
      Tratamento: [],
      Publicação: [],
    },
    validationChecks: [
      {
        id: 'limites',
        label: 'Check Limites Estaduais',
        description: 'Verifica se nenhum polígono ultrapassa a divisa de estado.',
        status: 'success',
        detail: 'Limites estaduais verificados e conformes.',
      },
      {
        id: 'geometria',
        label: 'Check Integridade Geométrica',
        description: 'Verifica topologia e geometrias válidas nos polígonos.',
        status: 'error',
        detail: 'Geometria inválida detectada no polígono 12.',
      },
    ],
    quarantineRecords: [
      {
        recordId: 'POL-12',
        motivo: 'Geometria inválida (Auto-intersecção)',
        etapaOrigem: 'Validação',
        execucaoId: 'EXEC-996R-T55-01',
        dataRejeicao: '07/09/2026 10:15:08',
        campo: 'geom',
        valorEncontrado: 'Invalid Polygon',
        valorEsperado: 'Valid Polygon',
      }
    ],
  },

  // ─── STATUS: Em andamento (aguardando validação para publicação dos dados) ───
  '#881A-B01': {
    id: '#881A-B01',
    dataset: 'APP Hidrográfica',
    stage: 'Publicação',
    status: 'Em andamento',
    dateTime: '07/09/2026 13:00',
    layerName: 'APP Hidrográfica 2023',
    source: 'ANA',
    year: '2023',
    epsg: 'EPSG:4674 (SIRGAS 2000)',
    epsgDetected: 'EPSG:4674 (SIRGAS 2000)',
    description: 'Delimitação de APP.',
    integrityHash: 'sha256:123abc456def789ghi012jkl345mno678pqr901stu234vwx567yz',
    originalFileUrl: '#',
    mapCoordinates: [
      [-15.793, -47.882],
      [-15.793, -47.8],
      [-15.85, -47.8],
      [-15.85, -47.882],
    ],
    logs: {
      Ingestão: ['[2026-09-07 09:30:00] Ingestão concluída com sucesso após upload.'],
      Validação: ['[2026-09-07 09:35:00] Validação concluída sem erros.'],
      Tratamento: ['[2026-09-07 09:40:00] Tratamento concluído com sucesso.'],
      Publicação: [
        '[2026-09-07 09:45:00] Cálculo concluído. Aguardando validação do auditor para consolidar publicação...',
      ],
    },
    validationChecks: validationChecksOk,
    treatmentChecks: treatmentChecksOk('EPSG:4674 (SIRGAS 2000)'),
    analyticsData: analyticsDataOk,
  },

  // ─── STATUS: Falhou (erro crítico na etapa de Tratamento) ───────────────────
  '#882A-C02': {
    id: '#882A-C02',
    dataset: 'Imóveis rurais',
    stage: 'Tratamento',
    status: 'Falhou',
    dateTime: '07/09/2026 13:00',
    layerName: 'Imóveis Rurais 2022',
    source: 'INCRA',
    year: '2022',
    epsg: 'EPSG:31983 (SIRGAS 2000 / UTM zone 23S)',
    epsgDetected: 'EPSG:31983 (SIRGAS 2000 / UTM zone 23S)',
    description: 'Atualização da base INCRA',
    integrityHash: 'sha256:456def789ghi012jkl345mno678pqr901stu234vwx567yz123abc',
    pauseReason:
      'Falha na correção topológica. Auto-interseções irreparáveis. Necessário alocar editor.',
    originalFileUrl: '#',
    mapCoordinates: [
      [-23.55, -46.633],
      [-23.55, -46.6],
      [-23.58, -46.6],
      [-23.58, -46.633],
    ],
    logs: {
      Ingestão: ['[2026-09-07 08:45:00] Ingestão concluída com sucesso após upload.'],
      Validação: ['[2026-09-07 08:50:00] Validação concluída sem erros.'],
      Tratamento: [
        '[2026-09-07 08:55:00] Falha grave detectada durante a correção topológica. Auto-interseções irreparáveis. Necessário alocar editor.',
      ],
      Publicação: [],
    },
    validationChecks: validationChecksOk,
    treatmentChecks: [
      ...treatmentChecksOk('EPSG:31983 (SIRGAS 2000 / UTM zone 23S)').slice(0, 2),
      {
        id: 'topology',
        label: 'Correção Topológica',
        description: 'Reparar auto-interseções, buracos e geometrias inválidas.',
        status: 'error',
        detail: 'Auto-interseções irreparáveis.',
      },
      {
        id: 'simplif',
        label: 'Simplificação de Geometrias',
        description: 'Reduzir complexidade mantendo fidelidade topológica (Douglas-Peucker).',
        status: 'pending',
        detail: 'Aguardando resolução do erro de topologia.',
      },
    ],
    quarantineRecords: [
      {
        recordId: 'IMV-882',
        motivo: 'Auto-interseção irreparável',
        etapaOrigem: 'Cálculo/Publicação',
        execucaoId: 'EXEC-882A-C02-01',
        dataRejeicao: '07/09/2026 08:55:00',
        campo: 'geom',
        valorEncontrado: 'Self-intersection at Point(X, Y)',
        valorEsperado: 'Geometria simples',
      }
    ],
  },

  // ─── STATUS: Em andamento (editor alocado para corrigir erro de tratamento) ─
  '#883X-Z03': {
    id: '#883X-Z03',
    dataset: 'Reserva legal',
    stage: 'Tratamento',
    status: 'Em andamento',
    dateTime: '07/09/2026 13:00',
    layerName: 'Reserva Legal 2021',
    source: 'Órgão Ambiental DEF',
    year: '2021',
    epsg: 'EPSG:4674 (SIRGAS 2000)',
    description: 'Processo com editor alocado para correção de inconsistências topológicas.',
    assignedEditor: 'Carlos Mendes (Editor)',
    pauseReason: 'Editor alocado para correção manual de inconsistências topológicas.',
    integrityHash: 'sha256:789ghi012jkl345mno678pqr901stu234vwx567yz123abc456def',
    originalFileUrl: '#',
    mapCoordinates: [
      [-19.916, -43.934],
      [-19.916, -43.9],
      [-19.95, -43.9],
      [-19.95, -43.934],
    ],
    logs: {
      Ingestão: ['[2026-09-07 08:15:00] Ingestão concluída com sucesso após upload.'],
      Validação: ['[2026-09-07 08:20:00] Validação concluída.'],
      Tratamento: [
        '[2026-09-07 08:25:00] Inconsistência topológica identificada.',
        '[2026-09-07 08:30:00] Editor Carlos Mendes alocado. Processo em andamento para correção do erro.',
      ],
      Publicação: [],
    },
    validationChecks: validationChecksOk,
    treatmentChecks: treatmentChecksInProgress('EPSG:4674 (SIRGAS 2000)'),
  },

  // ─── STATUS: Em andamento (Ingestão concluída após upload; Validação em processamento)
  '#884R-T04': {
    id: '#884R-T04',
    dataset: 'Malha municipal',
    stage: 'Validação',
    status: 'Em andamento',
    dateTime: '07/09/2026 13:00',
    layerName: 'Malha Municipal 2022',
    source: 'IBGE',
    year: '2022',
    epsg: 'EPSG:4326',
    description: 'Arquivo ingerido com sucesso após upload. Validação em processamento.',
    integrityHash: 'sha256:012jkl345mno678pqr901stu234vwx567yz123abc456def789ghi',
    originalFileUrl: '#',
    mapCoordinates: [
      [-23.55, -46.633],
      [-23.55, -46.6],
      [-23.58, -46.6],
      [-23.58, -46.633],
    ],
    logs: {
      Ingestão: [
        '[2026-09-07 08:00:00] Upload concluído com sucesso. Ingestão finalizada na Zona Bruta.',
      ],
      Validação: ['[2026-09-07 08:02:00] Executando validação de regras espaciais...'],
      Tratamento: [],
      Publicação: [],
    },
  },
}
