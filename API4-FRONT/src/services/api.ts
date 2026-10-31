/**
 * Configuração de cliente HTTP (Axios) para comunicação com a API REST.
 *
 * REGRA DE ARQUITETURA:
 * Nenhum mock é hardcoded em produção. Mocks devem residir exclusivamente
 * no diretório /mocks para uso em testes automatizados.
 */
import axios from 'axios'
import { userService } from './user'

// Flag legada mantida como false para compatibilidade com asserções de testes existentes.
// Nenhum serviço de produção deve utilizar fallback de mock.
export const mockactive = false

/**
 * Resolve dinamicamente a URL base da API.
 * Prioriza a injeção em tempo de execução via window.__ENV__ (carregada do .env pelo executável),
 * com fallback para import.meta.env em tempo de build/desenvolvimento.
 */
export const getApiBaseUrl = (): string => {
  if (typeof window !== 'undefined') {
    const runtimeUrl = (window as any).__ENV__?.VITE_API_BASE_URL || (window as any).__ENV__?.API_BASE_URL
    if (runtimeUrl && typeof runtimeUrl === 'string' && runtimeUrl.trim() !== '') {
      return runtimeUrl.trim()
    }
  }
  return import.meta.env.VITE_API_BASE_URL || ''
}

const api = axios.create({
  baseURL: getApiBaseUrl().replace(/\/+$/, '') + '/api',
  headers: {
    'Content-Type': 'application/json',
  },
})

// Adiciona credenciais Basic Auth de acordo com o perfil autenticado no sistema
api.interceptors.request.use((config) => {
  try {
    const currentBase = getApiBaseUrl().replace(/\/+$/, '')
    config.baseURL = currentBase + '/api'
    api.defaults.baseURL = currentBase + '/api'

    const user = userService.getCurrentUser()
    const roleCredentials: Record<string, string> = {
      Operador: 'operador:Troca123',
      Auditor: 'auditor:Troca123',
      Analista: 'analista:Troca123',
      Administrador: 'admin:Troca123',
    }
    const cred = roleCredentials[user?.role] || 'admin:Troca123'
    if (typeof btoa === 'function') {
      config.headers.Authorization = `Basic ${btoa(cred)}`
    }
  } catch {
    // Fallback silencioso caso userService não esteja disponível
  }
  return config
})

export default api
