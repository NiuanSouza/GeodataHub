<script setup lang="ts">
import { computed } from 'vue'
import { RouterLink } from 'vue-router'
import {
  FiGrid,
  FiUploadCloud,
  FiDatabase,
  FiMap,
  FiPieChart,
  FiShield,
  FiRotateCcw,
  FiCheckCircle,
} from 'vue-icons-plus/fi'
import { userService } from '../services/user'

const currentUser = computed(() => userService.getCurrentUser())

const navigationItems = computed(() => {
  const role = currentUser.value.role

  // Administrador tem acesso irrestrito a todas as telas do sistema
  if (role === 'Administrador') {
    return [
      { label: 'Dashboard', to: '/', icon: FiGrid },
      { label: 'Upload de carga', to: '/upload', icon: FiUploadCloud },
      { label: 'Catálogo de Bases', to: '/catalogo', icon: FiDatabase },
      { label: 'Mapa operacional', to: '/mapa', icon: FiMap },
      { label: 'Análise territorial', to: '/analise-territorial', icon: FiPieChart },
      { label: 'Painel do auditor', to: '/auditor', icon: FiShield },
    ]
  }

  if (role === 'Analista') {
    return [
      { label: 'Catálogo de bases', to: '/catalogo', icon: FiDatabase },
      { label: 'Mapa territorial', to: '/mapa', icon: FiMap },
      { label: 'Análise territorial', to: '/analise-territorial', icon: FiPieChart },
    ]
  }

  if (role === 'Auditor') {
    return [
      { label: 'Painel do auditor', to: '/', icon: FiShield },
      { label: 'Catálogo de bases', to: '/catalogo', icon: FiDatabase },
      { label: 'Mapa operacional', to: '/mapa', icon: FiMap },
    ]
  }

  // Operador (padrão)
  return [
    { label: 'Dashboard', to: '/', icon: FiGrid },
    { label: 'Upload de carga', to: '/upload', icon: FiUploadCloud },
    { label: 'Catálogo de bases', to: '/catalogo', icon: FiDatabase },
    { label: 'Mapa operacional', to: '/mapa', icon: FiMap },
  ]
})

const displayRole = computed(() => {
  return currentUser.value.role === 'Administrador' ? 'Gestor' : currentUser.value.role
})
</script>

<template>
  <aside class="sidebar">
    <div class="brand">
      <span class="brand-mark">A4</span>
      <div>
        <strong>{{ currentUser.name }}</strong>
        <span>{{ displayRole }}</span>
      </div>
    </div>

    <div class="sidebar-section-title">Navegação</div>
    <nav class="navigation" aria-label="Navegação principal">
      <RouterLink
        v-for="item in navigationItems"
        :key="item.to"
        :to="item.to"
        class="navigation-link"
        active-class="navigation-link-active"
      >
        <span class="navigation-icon" aria-hidden="true">
          <component :is="item.icon" size="18" />
        </span>
        <span>{{ item.label }}</span>
      </RouterLink>
    </nav>

    <div class="sidebar-footer">
      <RouterLink to="/login" class="login-link">
        <span class="navigation-icon" aria-hidden="true">
          <FiRotateCcw size="17" />
        </span>
        <span>Trocar perfil</span>
      </RouterLink>
    </div>
  </aside>
</template>

<style scoped>
.sidebar {
  width: 252px;
  height: 100vh;
  position: sticky;
  top: 0;
  overflow-y: auto;
  padding: 28px 16px 20px;
  display: flex;
  flex-direction: column;
  flex-shrink: 0;
  background: var(--vis-c-dark);
  color: var(--vis-c-text-light-1);
  border-right: 4px solid var(--vis-brand-orange);
}

.brand {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 0 12px 32px;
}

.brand-mark {
  width: 42px;
  height: 42px;
  display: grid;
  place-items: center;
  background: var(--vis-brand-orange);
  color: white;
  font-size: 0.82rem;
  font-weight: 800;
  letter-spacing: 0.04em;
}

.brand strong,
.brand span:last-child { display: block; }
.brand strong { font-size: 1.1rem; letter-spacing: 0.04em; }
.brand span:last-child { margin-top: 2px; color: var(--vis-c-text-light-2); font-size: 0.72rem; }
.sidebar-section-title { padding: 0 12px 10px; color: var(--vis-brand-orange-light); font-size: 0.68rem; font-weight: 700; letter-spacing: 0.12em; text-transform: uppercase; }
.navigation { display: grid; gap: 5px; }
.navigation-link,
.login-link { min-height: 46px; display: flex; align-items: center; gap: 12px; padding: 0 12px; color: var(--vis-c-text-light-2); font-size: 0.9rem; text-decoration: none; border-left: 3px solid transparent; transition: background-color 150ms ease, color 150ms ease, border-color 150ms ease; }
.navigation-link:hover,
.login-link:hover,
.navigation-link-active { color: white; background: rgba(242, 101, 34, 0.16); border-left-color: var(--vis-brand-orange); }
.navigation-icon { width: 20px; color: var(--vis-brand-orange-light); display: inline-flex; align-items: center; justify-content: center; line-height: 1; flex-shrink: 0; }
.sidebar-footer { margin-top: auto; padding-top: 20px; border-top: 1px solid var(--vis-c-divider-dark-2); }

@media (max-width: 760px) {
  .sidebar { width: 100%; height: auto; position: sticky; top: 0; z-index: 110; padding: 14px 12px; overflow: hidden; border-right: 0; border-bottom: 4px solid var(--vis-brand-orange); }
  .brand { padding: 0 8px 16px; }
  .sidebar-section-title { display: none; }
  .sidebar-footer { display: flex; padding-top: 8px; border-top: 0; margin-top: 0; }
  .navigation { display: flex; gap: 4px; overflow-x: auto; }
  .navigation-link { flex: 0 0 auto; min-height: 40px; padding: 0 10px; border-left: 0; border-bottom: 3px solid transparent; }
  .navigation-link-active { border-bottom-color: var(--vis-brand-orange); }
  .navigation-icon { display: none; }
}
</style>
