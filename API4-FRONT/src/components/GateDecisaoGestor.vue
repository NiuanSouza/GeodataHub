<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue'
import { FiCheckCircle, FiXCircle, FiAlertTriangle, FiShield, FiInfo } from 'vue-icons-plus/fi'
import { processService } from '../services/process'
import { userService } from '../services/user'

const props = defineProps<{
  processoId: string | number
  etapa: 'VALIDACAO' | 'TRATAMENTO' | 'PUBLICACAO'
  readOnly?: boolean
  currentStatus?: string
  currentStage?: string
  processamentoConcluido?: boolean
  progressoProcessamento?: number
}>()

const emit = defineEmits<{
  (e: 'decisao-registrada', resultado: any): void
}>()

const currentUser = computed(() => userService.getCurrentUser())
const isGestorOrAdmin = computed(
  () => userService.isAdmin() || (currentUser.value.role as string) === 'Gestor',
)

const isSubmitting = ref(false)
const isLoadingHistorico = ref(true)
const showRejectModal = ref(false)
const parecerRejeicao = ref('')
const erroMsg = ref<string | null>(null)
const sucessoMsg = ref<string | null>(null)
const decisaoTomada = ref<'APROVADO' | 'REPROVADO' | null>(null)
const decisaoHistorico = ref<{
  decisao: 'APROVADO' | 'REPROVADO'
  gestorNome?: string
  parecer?: string
  dataDecisao?: string
} | null>(null)

const carregarDecisaoPrevia = async () => {
  if (!props.processoId) {
    isLoadingHistorico.value = false
    return
  }
  isLoadingHistorico.value = true
  try {
    const historico = await processService.getDecisoesGestor(props.processoId)
    const ultimaDaEtapa = historico.find(
      (h) => h.etapa === props.etapa,
    )
    if (ultimaDaEtapa) {
      decisaoHistorico.value = {
        decisao: ultimaDaEtapa.decisao,
        gestorNome: ultimaDaEtapa.gestorNome,
        parecer: ultimaDaEtapa.parecer,
        dataDecisao: ultimaDaEtapa.dataDecisao,
      }
      if (ultimaDaEtapa.decisao === 'REPROVADO') {
        const statusNorm = (props.currentStatus || '').toLowerCase()
        const isEmTrabalho =
          statusNorm.includes('andamento') ||
          statusNorm.includes('valida') ||
          statusNorm.includes('pendente') ||
          !isEtapaConcluida.value

        // Se a carga recebeu novo arquivo ou está em andamento, destrava o gate para reavaliação do Gestor
        if (isEmTrabalho && isStageMatching.value && !isEtapaConcluida.value) {
          decisaoTomada.value = null
        } else {
          decisaoTomada.value = 'REPROVADO'
        }
      } else {
        decisaoTomada.value = ultimaDaEtapa.decisao
      }
    } else {
      decisaoHistorico.value = null
      decisaoTomada.value = null
    }
  } catch (err) {
    // Ignora erro silencioso de carregar histórico prévio
  } finally {
    isLoadingHistorico.value = false
  }
}

onMounted(() => {
  carregarDecisaoPrevia()
})

watch(
  [() => props.processoId, () => props.currentStatus, () => props.currentStage],
  () => {
    carregarDecisaoPrevia()
  },
)

const etapaTitulo = computed(() => {
  switch (props.etapa) {
    case 'VALIDACAO':
      return 'Validação'
    case 'TRATAMENTO':
      return 'Tratamento'
    case 'PUBLICACAO':
      return 'Publicação'
    default:
      return props.etapa
  }
})

const proximaEtapaNome = computed(() => {
  switch (props.etapa) {
    case 'VALIDACAO':
      return 'Tratamento'
    case 'TRATAMENTO':
      return 'Publicação'
    case 'PUBLICACAO':
      return 'Homologação Oficial (Versão 1.0)'
    default:
      return ''
  }
})

const isProcessamento100 = computed(() => {
  if (props.processamentoConcluido === false) return false
  if (props.progressoProcessamento !== undefined && props.progressoProcessamento < 100) return false
  return true
})

const isEtapaConcluida = computed(() => {
  const st = (props.currentStatus || '').toUpperCase()
  return st === 'CONCLUÍDA' || st === 'CONCLUIDA' || st === 'FECHADO'
})

const isStageMatching = computed(() => {
  if (!props.currentStage) return true
  const current = props.currentStage
    .toUpperCase()
    .normalize('NFD')
    .replace(/[\u0300-\u036f]/g, '')
  const target = props.etapa
    .toUpperCase()
    .normalize('NFD')
    .replace(/[\u0300-\u036f]/g, '')
  return current === target
})

const formatarData = (dataStr?: string) => {
  if (!dataStr) return ''
  try {
    const d = new Date(dataStr)
    return isNaN(d.getTime()) ? dataStr : d.toLocaleString('pt-BR')
  } catch {
    return dataStr
  }
}

const formatarNomeGestor = (nome?: string) => {
  if (!nome) return ''
  const trimmed = nome.trim()
  return trimmed.toLowerCase().startsWith('gestor') ? trimmed : `Gestor ${trimmed}`
}

const handleAprovar = async () => {
  if (isSubmitting.value) return
  isSubmitting.value = true
  erroMsg.value = null

  try {
    const gestorLabel = formatarNomeGestor(currentUser.value.name)
    const res = await processService.registrarDecisaoGestor(props.processoId, {
      gestorId: currentUser.value.id || 1,
      etapa: props.etapa,
      decisao: 'APROVADO',
      parecer: `Etapa de ${etapaTitulo.value} aprovada pelo ${gestorLabel}. Liberado para ${proximaEtapaNome.value}.`,
    })

    decisaoTomada.value = 'APROVADO'
    decisaoHistorico.value = {
      decisao: 'APROVADO',
      gestorNome: currentUser.value.name,
      parecer: `Aprovado pelo ${gestorLabel}`,
      dataDecisao: new Date().toISOString(),
    }
    sucessoMsg.value = `Etapa ${etapaTitulo.value} aprovada com sucesso! O processo avançou para ${proximaEtapaNome.value}.`
    emit('decisao-registrada', res)
  } catch (err: any) {
    erroMsg.value = err?.response?.data?.message || err?.message || 'Falha ao registrar aprovação do gestor.'
  } finally {
    isSubmitting.value = false
  }
}

const openRejectModal = () => {
  parecerRejeicao.value = ''
  erroMsg.value = null
  showRejectModal.value = true
}

const closeRejectModal = () => {
  showRejectModal.value = false
  parecerRejeicao.value = ''
}

const handleConfirmarRejeicao = async () => {
  if (!parecerRejeicao.value.trim()) {
    erroMsg.value = 'O parecer técnico é obrigatório em caso de rejeição.'
    return
  }

  isSubmitting.value = true
  erroMsg.value = null

  try {
    const res = await processService.registrarDecisaoGestor(props.processoId, {
      gestorId: currentUser.value.id || 1,
      etapa: props.etapa,
      decisao: 'REPROVADO',
      parecer: parecerRejeicao.value.trim(),
    })

    decisaoTomada.value = 'REPROVADO'
    decisaoHistorico.value = {
      decisao: 'REPROVADO',
      gestorNome: currentUser.value.name,
      parecer: parecerRejeicao.value.trim(),
      dataDecisao: new Date().toISOString(),
    }
    sucessoMsg.value = `Etapa rejeitada. A carga foi enviada para a Quarentena para inspeção e saneamento pelo Auditor.`
    closeRejectModal()
    emit('decisao-registrada', res)
  } catch (err: any) {
    erroMsg.value = err?.response?.data?.message || err?.message || 'Falha ao registrar rejeição do gestor.'
  } finally {
    isSubmitting.value = false
  }
}
</script>

<template>
  <div class="gate-decisao-container">
    <div
      v-if="isGestorOrAdmin && !readOnly && !isLoadingHistorico && !decisaoTomada && !isEtapaConcluida && isStageMatching"
      class="gate-card card mb-4"
    >
      <!-- Aviso amigável de tentativa anterior rejeitada / saneada após reprocessamento -->
      <div
        v-if="decisaoHistorico && decisaoHistorico.decisao === 'REPROVADO'"
        class="alert alert-warning mb-3"
        style="display: flex; gap: 8px; align-items: flex-start; padding: 0.75rem 1rem; border-radius: 6px; border-left: 4px solid #f59e0b; background: rgba(245, 158, 11, 0.08);"
      >
        <FiAlertTriangle size="18" style="color: #f59e0b; flex-shrink: 0; margin-top: 2px;" />
        <div style="font-size: 0.875rem; color: var(--color-text);">
          <strong>Reavaliação após reprocessamento:</strong> A tentativa anterior desta etapa foi rejeitada
          <template v-if="decisaoHistorico.gestorNome"> pelo {{ formatarNomeGestor(decisaoHistorico.gestorNome) }}</template>
          <template v-if="decisaoHistorico.dataDecisao"> em {{ formatarData(decisaoHistorico.dataDecisao) }}</template>.
          <div v-if="decisaoHistorico.parecer" style="font-style: italic; margin-top: 2px;">
            Parecer anterior: "{{ decisaoHistorico.parecer }}"
          </div>
          <div style="margin-top: 4px; font-weight: 500;">
            Novo arquivo recebido e em validação. Avalie as verificações automáticas abaixo e decida sobre a aprovação.
          </div>
        </div>
      </div>

      <div class="gate-header">
        <div class="gate-title">
          <FiShield size="20" class="gate-icon" />
          <div>
            <h4>Gate de Decisão do Gestor — {{ etapaTitulo }}</h4>
            <p class="gate-desc">
              Como Gestor, avalie a conformidade da etapa antes de liberar para {{ proximaEtapaNome }}.
            </p>
          </div>
        </div>

        <div class="gate-actions" v-if="!decisaoTomada">
          <!-- Indicador e trava de processamento assíncrono (100%) -->
          <div v-if="!isProcessamento100" class="processamento-pendente-box">
            <span class="spinner-border spinner-border-sm" role="status"></span>
            <span>Processando base no PostGIS ({{ progressoProcessamento ?? 0 }}%)... Aprovação liberada em 100%.</span>
          </div>

          <button
            class="btn btn-outline-danger btn-sm"
            :disabled="isSubmitting || !isProcessamento100"
            @click="openRejectModal"
          >
            <FiXCircle size="14" />
            Rejeitar (Quarentena)
          </button>
          <button
            class="btn btn-success btn-sm"
            :disabled="isSubmitting || !isProcessamento100"
            @click="handleAprovar"
          >
            <FiCheckCircle size="14" />
            Aprovar {{ etapaTitulo }}
          </button>
        </div>
      </div>
    </div>

    <!-- Badge / Aviso informativo amigável de decisão prévia -->
    <div
      v-else-if="decisaoHistorico && !isLoadingHistorico"
      class="gate-card card mb-4"
      :class="decisaoHistorico.decisao === 'APROVADO' ? 'historico-aprovado' : 'historico-reprovado'"
    >
      <div class="gate-header">
        <div class="gate-title">
          <FiCheckCircle v-if="decisaoHistorico.decisao === 'APROVADO'" size="20" class="gate-icon-success" />
          <FiXCircle v-else size="20" class="gate-icon-danger" />
          <div>
            <h4>
              Etapa de {{ etapaTitulo }} {{ decisaoHistorico.decisao === 'APROVADO' ? 'aprovada' : 'rejeitada' }}
              <template v-if="decisaoHistorico.gestorNome"> pelo {{ formatarNomeGestor(decisaoHistorico.gestorNome) }}</template>
              <template v-if="decisaoHistorico.dataDecisao"> em {{ formatarData(decisaoHistorico.dataDecisao) }}</template>.
            </h4>
            <p v-if="decisaoHistorico.parecer" class="gate-desc">
              Parecer: <em>"{{ decisaoHistorico.parecer }}"</em>
            </p>
          </div>
        </div>
      </div>
    </div>

    <!-- Feedback Alerts -->
    <div v-if="sucessoMsg" class="alert alert-success mt-3 mb-4">
      <FiCheckCircle size="16" />
      <span>{{ sucessoMsg }}</span>
    </div>
    <div v-if="erroMsg" class="alert alert-danger mt-3 mb-4">
      <FiAlertTriangle size="16" />
      <span>{{ erroMsg }}</span>
    </div>

    <!-- Modal de Rejeição com Parecer Obrigatório -->
    <Teleport to="body">
      <div v-if="showRejectModal" class="modal-backdrop">
        <div class="modal-box">
          <div class="modal-header">
            <h3>Rejeitar Etapa de {{ etapaTitulo }}</h3>
            <button class="btn-close" @click="closeRejectModal">&times;</button>
          </div>
          <div class="modal-body">
            <p class="warning-text">
              <FiAlertTriangle size="18" style="color: #ef4444" />
              Ao rejeitar esta etapa, a carga será enviada imediatamente para a <strong>Quarentena</strong> para atuação de um auditor.
            </p>
            <div class="form-group">
              <label for="parecer-txt"><strong>Parecer Técnico / Justificativa *</strong></label>
              <textarea
                id="parecer-txt"
                v-model="parecerRejeicao"
                class="input textarea"
                rows="4"
                placeholder="Descreva detalhadamente o motivo da não conformidade encontrada..."
                required
              ></textarea>
            </div>
            <p v-if="erroMsg" class="error-text">{{ erroMsg }}</p>
          </div>
          <div class="modal-footer">
            <button class="btn btn_outline" :disabled="isSubmitting" @click="closeRejectModal">
              Cancelar
            </button>
            <button
              class="btn btn-danger"
              :disabled="!parecerRejeicao.trim() || isSubmitting"
              @click="handleConfirmarRejeicao"
            >
              {{ isSubmitting ? 'Enviando...' : 'Confirmar e Enviar para Quarentena' }}
            </button>
          </div>
        </div>
      </div>
    </Teleport>
  </div>
</template>

<style scoped>
.gate-card {
  background: linear-gradient(to right, rgba(59, 130, 246, 0.04), rgba(59, 130, 246, 0.01));
  border: 1px solid rgba(59, 130, 246, 0.25);
  border-radius: 10px;
  padding: 1.25rem 1.5rem;
}

.historico-aprovado {
  background: linear-gradient(to right, rgba(16, 185, 129, 0.06), rgba(16, 185, 129, 0.02));
  border: 1px solid rgba(16, 185, 129, 0.35);
}

.historico-reprovado {
  background: linear-gradient(to right, rgba(239, 68, 68, 0.06), rgba(239, 68, 68, 0.02));
  border: 1px solid rgba(239, 68, 68, 0.35);
}

.gate-icon-success {
  color: var(--vis-c-success, #10b981);
  flex-shrink: 0;
}

.gate-icon-danger {
  color: #ef4444;
  flex-shrink: 0;
}

.gate-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  flex-wrap: wrap;
  gap: 1rem;
}

.gate-title {
  display: flex;
  align-items: center;
  gap: 0.75rem;
}

.gate-icon {
  color: #3b82f6;
  flex-shrink: 0;
}

.gate-title h4 {
  margin: 0;
  font-size: 1.05rem;
  font-weight: 700;
  color: var(--color-heading);
}

.gate-desc {
  margin: 0.2rem 0 0 0;
  font-size: 0.85rem;
  color: var(--color-text);
  opacity: 0.8;
}

.gate-actions {
  display: flex;
  gap: 0.75rem;
  align-items: center;
}

.btn-success {
  background-color: var(--vis-c-success, #10b981);
  color: white;
  border: none;
  display: flex;
  align-items: center;
  gap: 0.4rem;
  padding: 0.45rem 1rem;
  font-size: 0.85rem;
  font-weight: 600;
  border-radius: 6px;
  cursor: pointer;
  transition: opacity 0.2s;
}

.btn-success:hover:not(:disabled) {
  opacity: 0.9;
}

.btn-outline-danger {
  background: transparent;
  color: #ef4444;
  border: 1px solid #ef4444;
  display: flex;
  align-items: center;
  gap: 0.4rem;
  padding: 0.45rem 1rem;
  font-size: 0.85rem;
  font-weight: 600;
  border-radius: 6px;
  cursor: pointer;
}

.btn-outline-danger:hover:not(:disabled) {
  background: rgba(239, 68, 68, 0.08);
}

.btn-danger {
  background-color: #ef4444;
  color: white;
  border: none;
  padding: 0.5rem 1rem;
  border-radius: 6px;
  font-weight: 600;
  cursor: pointer;
}

.alert {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  padding: 0.75rem 1rem;
  border-radius: 6px;
  font-size: 0.9rem;
}

.alert-success {
  background: rgba(16, 185, 129, 0.1);
  color: var(--vis-c-success, #10b981);
  border-left: 3px solid var(--vis-c-success, #10b981);
}

.alert-danger {
  background: rgba(239, 68, 68, 0.1);
  color: #ef4444;
  border-left: 3px solid #ef4444;
}

/* Modal */
.modal-backdrop {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.6);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
  backdrop-filter: blur(2px);
}

.modal-box {
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: 12px;
  width: 90%;
  max-width: 520px;
  overflow: hidden;
  box-shadow: 0 10px 25px rgba(0, 0, 0, 0.2);
}

.modal-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 1.25rem 1.5rem;
  border-bottom: 1px solid var(--color-border);
}

.modal-header h3 {
  margin: 0;
  font-size: 1.15rem;
  font-weight: 700;
}

.btn-close {
  background: transparent;
  border: none;
  font-size: 1.5rem;
  cursor: pointer;
  color: var(--color-text);
  line-height: 1;
}

.modal-body {
  padding: 1.5rem;
  display: flex;
  flex-direction: column;
  gap: 1rem;
}

.warning-text {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  margin: 0;
  font-size: 0.9rem;
  color: var(--color-text);
}

.textarea {
  width: 100%;
  min-height: 90px;
  margin-top: 0.4rem;
  box-sizing: border-box;
}

.processamento-pendente-box {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  font-size: 0.82rem;
  color: #f59e0b;
  font-weight: 500;
  background: rgba(245, 158, 11, 0.1);
  padding: 0.35rem 0.75rem;
  border-radius: 6px;
  border: 1px solid rgba(245, 158, 11, 0.25);
}

.modal-footer {
  display: flex;
  justify-content: flex-end;
  gap: 0.75rem;
  padding: 1rem 1.5rem;
  border-top: 1px solid var(--color-border);
  background: var(--color-surface-soft, rgba(0, 0, 0, 0.02));
}

.error-text {
  color: #ef4444;
  font-size: 0.85rem;
  margin: 0;
}
</style>
