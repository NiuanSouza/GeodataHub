import { createRouter, createWebHistory } from 'vue-router'
import DashboardView from '../views/DashboardView.vue'
import StyleguideView from '../views/StyleguideView.vue'
import LoginView from '../views/LoginView.vue'
import UploadView from '../views/UploadView.vue'
import ProcessLogView from '../views/ProcessLogView.vue'
import MapView from '../views/MapView.vue'
import CargaListView from '../views/CargaListView.vue'
import TerritorialAnalysisView from '../views/TerritorialAnalysisView.vue'
import { userService, type UserRole } from '../services/user'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: '/',
      name: 'dashboard',
      component: DashboardView,
      meta: { allowedRoles: ['Operador', 'Auditor', 'Analista', 'Administrador'] },
    },
    {
      path: '/analise-territorial',
      name: 'analise-territorial',
      component: TerritorialAnalysisView,
      meta: { allowedRoles: ['Analista', 'Administrador'] },
    },
    {
      path: '/auditor',
      name: 'auditor',
      component: DashboardView,
      meta: { allowedRoles: ['Auditor', 'Administrador'] },
    },
    {
      path: '/catalogo',
      name: 'catalogo',
      component: CargaListView,
      meta: { allowedRoles: ['Operador', 'Auditor', 'Analista', 'Administrador'] },
    },
    {
      path: '/processo',
      alias: ['/processos'],
      redirect: '/catalogo',
    },
    {
      path: '/processo/:id',
      alias: ['/processos/:id'],
      name: 'process-log',
      component: ProcessLogView,
      meta: { allowedRoles: ['Operador', 'Auditor', 'Analista', 'Administrador'] },
    },
    {
      path: '/upload',
      name: 'upload',
      component: UploadView,
      meta: { allowedRoles: ['Operador', 'Administrador'] },
    },
    {
      path: '/mapa',
      name: 'mapa',
      component: MapView,
      meta: { allowedRoles: ['Operador', 'Auditor', 'Analista', 'Administrador'] },
    },
    {
      path: '/styleguide',
      name: 'styleguide',
      component: StyleguideView,
    },
    {
      path: '/login',
      name: 'login',
      component: LoginView,
      meta: { hideMenu: true },
    },
  ],
})

export default router

router.beforeEach((to) => {
  // 1. Se a rota de destino já for o login, permite o acesso direto
  if (to.name === 'login') return true

  // 2. Busca o usuário atual simulado
  const currentUser = userService.getCurrentUser()

  // 3. Verifica se nenhum usuário foi selecionado
  // (valida tanto se currentUser for nulo/indefinido quanto se não houver role/id definido)
  if (!currentUser || !currentUser.role) {
    return {
      name: 'login',
      query: { redirect: to.fullPath }, // Opcional: salva a rota para voltar após selecionar o login
    }
  }

  const currentRole = currentUser.role

  // 4. Administrador tem acesso a todas as telas do sistema
  if (userService.isAdmin()) {
    return true
  }

  const allowedRoles = to.meta.allowedRoles as UserRole[] | undefined
  const homeByRole: Record<string, string> = {
    Auditor: 'auditor',
    Analista: 'analise-territorial',
    Operador: 'dashboard',
    Administrador: 'dashboard',
  }
  const home = homeByRole[currentRole] ?? 'dashboard'

  if (allowedRoles && !userService.canAccess(allowedRoles)) {
    return { name: home }
  }

  if (to.name === 'dashboard' && currentRole !== 'Operador') {
    return { name: home }
  }

  return true
})
