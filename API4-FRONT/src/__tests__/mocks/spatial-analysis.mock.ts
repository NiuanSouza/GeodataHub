/**
 * @file /mocks para testes automatizados.
 *
 * REGRA DE ARQUITETURA:
 * Nenhum mock deve ser hardcoded no código de produção (serviços, views ou componentes).
 * Todos os mocks devem residir exclusivamente aqui (/mocks) para uso em testes automatizados.
 */

import type { SpatialAnalysisDocument, SpatialAnalysisResponse } from '../spatialAnalysis'

const carGeometry: GeoJSON.Polygon = {
  type: 'Polygon',
  coordinates: [[
    [-47.96, -15.79], [-47.93, -15.775], [-47.90, -15.784], [-47.885, -15.80],
    [-47.865, -15.805], [-47.858, -15.83], [-47.872, -15.854], [-47.90, -15.862],
    [-47.925, -15.85], [-47.95, -15.855], [-47.968, -15.831], [-47.955, -15.81],
    [-47.96, -15.79],
  ]],
}

const alertGeometry: GeoJSON.Polygon = {
  type: 'Polygon',
  coordinates: [[
    [-47.924, -15.806], [-47.915, -15.799], [-47.902, -15.803], [-47.896, -15.812],
    [-47.90, -15.824], [-47.911, -15.829], [-47.923, -15.821], [-47.928, -15.814],
    [-47.924, -15.806],
  ]],
}

const appGeometry: GeoJSON.Polygon = {
  type: 'Polygon',
  coordinates: [[
    [-47.95, -15.836], [-47.936, -15.83], [-47.923, -15.835], [-47.915, -15.842],
    [-47.90, -15.846], [-47.888, -15.854], [-47.896, -15.861], [-47.911, -15.856],
    [-47.925, -15.85], [-47.939, -15.854], [-47.95, -15.848], [-47.95, -15.836],
  ]],
}

const indigenousTerritoryGeometry: GeoJSON.MultiPolygon = {
  type: 'MultiPolygon',
  coordinates: [
    [[
      [-47.976, -15.78], [-47.963, -15.771], [-47.948, -15.775], [-47.94, -15.789],
      [-47.946, -15.802], [-47.961, -15.806], [-47.974, -15.798], [-47.981, -15.789],
      [-47.976, -15.78],
    ]],
    [[
      [-47.872, -15.875], [-47.858, -15.868], [-47.845, -15.875], [-47.842, -15.889],
      [-47.851, -15.9], [-47.866, -15.897], [-47.877, -15.887], [-47.872, -15.875],
    ]],
  ],
}

const collection = (
  properties: Record<string, unknown>,
  geometry: GeoJSON.Geometry,
): GeoJSON.FeatureCollection<GeoJSON.Geometry, Record<string, unknown>> => ({
  type: 'FeatureCollection',
  features: [{ type: 'Feature', properties, geometry }],
})

export const mockSpatialDocuments: SpatialAnalysisDocument[] = [
  {
    id: 'car-brasilia-2024',
    name: 'Cadastro Ambiental Rural — Região de Brasília',
    category: 'CAR',
    source: 'SICAR',
    year: '2024',
    zone: 'TRATADA',
    status: 'DISPONIVEL',
    featureCount: 1,
    geojson: collection(
      { codigoCar: 'DF-5300108-8A1B2C3D4E5F', areaImovelHa: 1200.4321, safra: 2024 },
      carGeometry,
    ),
  },
  {
    id: 'alerts-brasilia-2024',
    name: 'Alertas ambientais — Distrito Federal',
    category: 'ALERTA',
    source: 'IBAMA',
    year: '2024',
    zone: 'TRATADA',
    status: 'DISPONIVEL',
    featureCount: 1,
    geojson: collection({ nome: 'Alerta de desmatamento', safra: 2024 }, alertGeometry),
  },
  {
    id: 'app-brasilia-2024',
    name: 'Áreas de Preservação Permanente — Brasília',
    category: 'APP',
    source: 'ANA',
    year: '2024',
    zone: 'TRATADA',
    status: 'DISPONIVEL',
    featureCount: 1,
    geojson: collection({ nome: 'Faixa de APP', safra: 2024 }, appGeometry),
  },
  {
    id: 'rl-brasilia-2024',
    name: 'Reservas Legais — Brasília',
    category: 'RESERVA_LEGAL',
    source: 'SICAR',
    year: '2024',
    zone: 'TRATADA',
    status: 'DISPONIVEL',
    featureCount: 1,
    geojson: collection({ nome: 'Reserva legal', safra: 2024 }, appGeometry),
  },
  {
    id: 'terra-indigena-brasilia-2024',
    name: 'Território indígena — exemplo demonstrativo',
    category: 'TERRA_INDIGENA',
    source: 'Base demonstrativa',
    year: '2024',
    zone: 'TRATADA',
    status: 'DISPONIVEL',
    featureCount: 1,
    geojson: collection({ nome: 'Território indígena demonstrativo', safra: 2024 }, indigenousTerritoryGeometry),
  },
]

export const mockSpatialAnalysisResponse: SpatialAnalysisResponse = {
  id: 'analise-demonstracao-001',
  status: 'CONCLUIDA',
  createdAt: '17/10/2026 14:30',
  results: [
    {
      id: 'resultado-car-001',
      codigoCar: 'DF-5300108-8A1B2C3D4E5F',
      safra: '2024',
      areaImovelHa: 1200.4321,
      areaSobrepostaHa: 84.2345,
      percentualSobreposicao: 7.02,
      status: 'PROCESSADO',
      geometry: alertGeometry,
    },
  ],
}
