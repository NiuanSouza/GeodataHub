/**
 * @file /mocks para testes automatizados.
 *
 * REGRA DE ARQUITETURA:
 * Nenhum mock deve ser hardcoded no código de produção (serviços, views ou componentes).
 * Todos os mocks devem residir exclusivamente aqui (/mocks) para uso em testes automatizados.
 */

export const mockCurrentUser = {
  id: 1,
  name: 'João Silva',
  role: 'Operador'
};
