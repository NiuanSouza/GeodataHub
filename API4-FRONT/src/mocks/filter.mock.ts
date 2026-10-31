/**
 * @file /mocks para testes automatizados.
 *
 * REGRA DE ARQUITETURA:
 * Nenhum mock deve ser hardcoded no código de produção (serviços, views ou componentes).
 * Todos os mocks devem residir exclusivamente aqui (/mocks) para uso em testes automatizados.
 */

export const mockFilterOptions = {
  conjuntos: [
    { id: 1, label: 'Imóveis rurais' },
    { id: 2, label: 'Malha municipal' },
    { id: 3, label: 'Reserva legal' },
    { id: 4, label: 'Uso e cobertura do solo' },
    { id: 5, label: 'APP Hidrográfica' },
  ],
  etapas: [
    { id: 1, label: 'Ingestão' },
    { id: 2, label: 'Tratamento' },
    { id: 3, label: 'Validação' },
    { id: 4, label: 'Cálculo Analítico' },
    { id: 5, label: 'Publicação' },
  ],
  situacoes: [
    { id: 1, label: 'Em andamento' },
    { id: 2, label: 'Aguardando validação' },
    { id: 3, label: 'Com ressalva' },
    { id: 4, label: 'Falhou' },
    { id: 5, label: 'Concluída' },
  ],
}

export const mockAvailableEditors = [
  'Carlos Mendes (Topologia)',
  'Mariana Silva (Validação Geométrica)',
  'Roberto Alves (Auditor Geral)',
  'Fernanda Lima (Atributos e Schema)',
]
