<script setup lang="ts">
import { ref, shallowRef, computed, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { FiUploadCloud, FiCheck, FiInfo, FiAlertCircle } from 'vue-icons-plus/fi'
import { uploadService } from '@/services/upload'
import { processService } from '@/services/process'
import Stepper from '@/components/Stepper.vue'

// Passos
const currentStep = ref(1)
const currentProcessoId = ref<number | null>(null)
const route = useRoute()
const reprocessId = ref<string | null>(null)

const nextStep = () => {
  if (currentStep.value < 3) {
    currentStep.value++
  }
}

const prevStep = () => {
  if (currentStep.value > 1) {
    currentStep.value--
  }
}

const nomeCamada = ref('')
const orgaoEmissor = ref('')
const anoReferencia = ref('')
const epsg = ref('')
const conjuntoDados = ref('')
const descricao = ref('')
// Expansão de metadados (US05)
const competencia = ref('')
const formato = ref('')
const classificacaoAcesso = ref('')
const coberturaEspacial = ref('')
const coberturaTemporalInicio = ref('')
const coberturaTemporalFim = ref('')

const formatoOptions = ['GeoJSON', 'Shapefile (ZIP)', 'CSV', 'GeoPackage', 'KML']
const classificacaoOptions = ['Público', 'Restrito', 'Confidencial']

interface FormatoConfig {
  extensions: string[]
  label: string
  tag: string
}

const formatoConfigMap: Record<string, FormatoConfig> = {
  GeoJSON: {
    extensions: ['.geojson', '.json'],
    label: 'GeoJSON (.geojson, .json)',
    tag: '.geojson / .json',
  },
  'Shapefile (ZIP)': {
    extensions: ['.zip'],
    label: 'Shapefile compactado (.zip)',
    tag: '.zip (Shapefile)',
  },
  CSV: {
    extensions: ['.csv'],
    label: 'Tabela delimitada (.csv)',
    tag: '.csv',
  },
  GeoPackage: {
    extensions: ['.gpkg'],
    label: 'GeoPackage (.gpkg)',
    tag: '.gpkg',
  },
  KML: {
    extensions: ['.kml', '.kmz'],
    label: 'Keyhole Markup Language (.kml, .kmz)',
    tag: '.kml / .kmz',
  },
}

const formatoConfigAtual = computed<FormatoConfig | null>(() => {
  if (!formato.value) return null
  return formatoConfigMap[formato.value] || null
})

const allowedExtensions = computed(() => {
  if (formatoConfigAtual.value) {
    return formatoConfigAtual.value.extensions
  }
  return ['.zip', '.geojson', '.json', '.csv', '.gpkg', '.kml', '.kmz']
})

const acceptAttribute = computed(() => allowedExtensions.value.join(','))

const formOptions = ref({
  anos: [] as string[],
  epsgs: [] as { id: string; label: string }[],
  orgaos: [] as { id: string | number; label: string }[],
  conjuntos: [] as { id: string | number; label: string }[],
})

onMounted(async () => {
  try {
    formOptions.value = await uploadService.getUploadOptions()
  } catch (err) {
    console.error('Falha ao obter opcoes do formulario', err)
  }

  if (route.query.reprocessId) {
    reprocessId.value = route.query.reprocessId as string
    currentProcessoId.value = Number(reprocessId.value)
    try {
      const details = await processService.getProcessDetails(reprocessId.value)
      nomeCamada.value = details.layerName || 'Camada_' + reprocessId.value
      orgaoEmissor.value = details.source || '1'
      anoReferencia.value = details.year || '2026'
      epsg.value = details.epsg || '4674'
      conjuntoDados.value = details.dataset || '1'
      descricao.value = details.description || ''
      competencia.value = '2026-10'
      formato.value = 'GeoJSON'
      classificacaoAcesso.value = 'Público'
      coberturaEspacial.value = 'Estado de Goiás'
    } catch (err) {
      console.error('Falha ao obter detalhes do processo para reprocessamento', err)
      nomeCamada.value = 'Camada_Corrigida_' + reprocessId.value
      orgaoEmissor.value = '1'
      anoReferencia.value = '2026'
      epsg.value = '4674'
      conjuntoDados.value = '1'
      competencia.value = '2026-10'
      formato.value = 'GeoJSON'
      classificacaoAcesso.value = 'Público'
      coberturaEspacial.value = 'Estado de Goiás'
    }
  }
})

const arquivoSelecionado = shallowRef<File | null>(null)
const isUploading = ref(false)
const erroMensagem = ref<{ titulo: string; texto: string } | null>(null)
const isDragover = ref(false)
const fileInputRef = ref<HTMLInputElement | null>(null)
const uploadResult = ref<any>(null)
const processamentoStatus = ref<any>(null)
const processamentoErro = ref<string | null>(null)

const isStep1Valid = computed(
  () =>
    Boolean(reprocessId.value) ||
    (nomeCamada.value.trim().length > 0 &&
      orgaoEmissor.value !== '' &&
      anoReferencia.value !== '' &&
      epsg.value !== '' &&
      conjuntoDados.value !== '' &&
      competencia.value !== '' &&
      formato.value !== '' &&
      classificacaoAcesso.value !== '' &&
      coberturaEspacial.value.trim().length > 0 &&
      (!coberturaTemporalInicio.value ||
        !coberturaTemporalFim.value ||
        coberturaTemporalInicio.value <= coberturaTemporalFim.value)),
)
const isStep2Valid = computed(() => arquivoSelecionado.value !== null)

const formatFileSize = (bytes: number) => {
  if (bytes === 0) return '0 Bytes'
  const k = 1024
  const sizes = ['Bytes', 'KB', 'MB', 'GB']
  const i = Math.floor(Math.log(bytes) / Math.log(k))
  return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i]
}

const triggerFileInput = () => {
  if (!isUploading.value) {
    fileInputRef.value?.click()
  }
}

const processSelectedFile = (file: File) => {
  const isValid = allowedExtensions.value.some((ext) => file.name.toLowerCase().endsWith(ext))
  if (isValid) {
    arquivoSelecionado.value = file
    erroMensagem.value = null
  } else {
    const esperado = formatoConfigAtual.value
      ? formatoConfigAtual.value.label
      : allowedExtensions.value.join(', ')
    erroMensagem.value = {
      titulo: 'Formato de Arquivo Incompatível',
      texto: `O arquivo selecionado deve estar no formato exigido: ${esperado}.`,
    }
    arquivoSelecionado.value = null
  }
}

const handleFileSelect = (event: Event) => {
  const target = event.target as HTMLInputElement
  if (target.files && target.files.length > 0) {
    const file = target.files[0]
    if (file) {
      processSelectedFile(file)
    }
  }
}

const extrairMensagemErro = (err: any): { titulo: string; texto: string } => {
  const errorData = err?.response?.data || err
  if (typeof errorData === 'string') {
    return {
      titulo: 'Falha na Operação',
      texto: errorData,
    }
  }
  if (typeof errorData === 'object' && errorData !== null) {
    const titulo = errorData.titulo || errorData.erro || errorData.error || 'Falha na Operação'
    const texto =
      errorData.mensagem ||
      errorData.message ||
      errorData.detail ||
      errorData.detalhes ||
      (errorData.erro && errorData.erro !== titulo ? errorData.erro : null) ||
      'Ocorreu um erro durante a operação.'
    return { titulo, texto }
  }
  return {
    titulo: 'Falha na Operação',
    texto: 'Ocorreu um erro desconhecido.',
  }
}

const handleDrop = (event: DragEvent) => {
  isDragover.value = false
  if (isUploading.value) return

  if (event.dataTransfer?.files && event.dataTransfer.files.length > 0) {
    const file = event.dataTransfer.files[0]
    if (file) {
      processSelectedFile(file)
    }
  }
}

const handleUpload = async () => {
  if (!isStep1Valid.value || !isStep2Valid.value) return

  erroMensagem.value = null
  processamentoErro.value = null
  processamentoStatus.value = null
  isUploading.value = true

  try {
    // 1. Reprocessamento de carga existente em quarentena
    if (reprocessId.value) {
      const pId = Number(reprocessId.value)
      if (arquivoSelecionado.value) {
        const response = await uploadService.reprocessarCarga(pId, arquivoSelecionado.value)
        uploadResult.value = response
        currentProcessoId.value = pId
        currentStep.value = 3
      }
      return
    }

    // 2. Upload de nova carga
    const metadataPayload = {
      nomeCamada: nomeCamada.value,
      orgaoEmissor: orgaoEmissor.value,
      anoReferencia: anoReferencia.value,
      epsg: epsg.value,
      conjuntoDados: conjuntoDados.value,
      descricao: descricao.value,
      competencia: competencia.value,
      formato: formato.value,
      classificacaoAcesso: classificacaoAcesso.value,
      coberturaEspacial: coberturaEspacial.value,
      coberturaTemporalInicio: coberturaTemporalInicio.value,
      coberturaTemporalFim: coberturaTemporalFim.value,
    }

    let processoId = currentProcessoId.value
    if (!processoId) {
      const processo = await uploadService.cadastrarMetadados(metadataPayload)
      processoId = processo.id
      currentProcessoId.value = processoId
    }

    if (arquivoSelecionado.value && processoId) {
      const response = await uploadService.uploadArquivo(processoId, arquivoSelecionado.value)
      uploadResult.value = response
      currentStep.value = 3
    }
  } catch (err: any) {
    erroMensagem.value = extrairMensagemErro(err)
  } finally {
    isUploading.value = false
  }
}

const resetForm = () => {
  currentStep.value = 1
  currentProcessoId.value = null
  reprocessId.value = null
  processamentoStatus.value = null
  processamentoErro.value = null
  arquivoSelecionado.value = null
  nomeCamada.value = ''
  orgaoEmissor.value = ''
  anoReferencia.value = ''
  epsg.value = ''
  conjuntoDados.value = ''
  descricao.value = ''
  competencia.value = ''
  formato.value = ''
  classificacaoAcesso.value = ''
  coberturaEspacial.value = ''
  coberturaTemporalInicio.value = ''
  coberturaTemporalFim.value = ''
}
</script>

<template>
  <main class="upload-view">
    <div class="upload-header">
      <h1>Nova Carga de Dados</h1>
    </div>

    <Stepper
      :currentStep="currentStep"
      :steps="['Origem dos Dados', 'Upload do Arquivo', 'Confirmação']"
    />

    <!-- Passo 1: Metadados -->
    <div v-show="currentStep === 1" class="upload-card step-card animate-in">
      <div
        v-if="reprocessId"
        class="alert-info mb-4"
        style="
          background-color: rgba(59, 130, 246, 0.1);
          border-left: 3px solid #3b82f6;
          padding: 1rem;
          border-radius: 4px;
          display: flex;
          align-items: center;
          gap: 0.5rem;
        "
      >
        <FiInfo size="18" style="color: #3b82f6" />
        <span style="color: var(--color-text)"
          >Reprocessando Carga #{{ reprocessId }} - O arquivo enviado substituirá os registros
          rejeitados na quarentena.</span
        >
      </div>

      <div class="card-header">
        <FiInfo size="20" class="card-header-icon" />
        <h2>Dados de Origem</h2>
      </div>
      <p class="card-description">
        Cadastre a procedência e metadados do arquivo para garantir a governança dos dados.
      </p>

      <div class="form-grid">
        <div class="form-group form-group--full">
          <label class="label" for="upload-nome">Nome da Fonte de Dados / Camada *</label>
          <input
            id="upload-nome"
            v-model="nomeCamada"
            type="text"
            class="input"
            placeholder="Ex: Dados Climaticos de Goiania"
            required
          />
        </div>

        <div class="form-group">
          <label class="label" for="upload-orgao">Órgão Emissor *</label>
          <select id="upload-orgao" v-model="orgaoEmissor" class="input">
            <option value="">Selecione o órgão</option>
            <option v-for="item in formOptions.orgaos" :key="item.id" :value="item.id">
              {{ item.label }}
            </option>
          </select>
        </div>

        <div class="form-group">
          <label class="label" for="upload-conjunto">Conjunto de Dados *</label>
          <select id="upload-conjunto" v-model="conjuntoDados" class="input">
            <option value="">Selecione o conjunto</option>
            <option v-for="item in formOptions.conjuntos" :key="item.id" :value="item.id">
              {{ item.label }}
            </option>
          </select>
        </div>

        <div class="form-group">
          <label class="label" for="upload-ano">Ano de Referência *</label>
          <select id="upload-ano" v-model="anoReferencia" class="input">
            <option value="">Selecione o ano</option>
            <option v-for="ano in formOptions.anos" :key="ano" :value="ano">{{ ano }}</option>
          </select>
        </div>

        <div class="form-group">
          <label class="label" for="upload-epsg">Sistema de Coordenadas (EPSG) *</label>
          <select id="upload-epsg" v-model="epsg" class="input">
            <option value="">Selecione o EPSG</option>
            <option v-for="item in formOptions.epsgs" :key="item.id" :value="item.id">
              {{ item.label }}
            </option>
          </select>
        </div>

        <div class="form-group">
          <label class="label" for="upload-competencia">Competência *</label>
          <input id="upload-competencia" v-model="competencia" type="month" class="input" />
        </div>

        <div class="form-group">
          <label class="label" for="upload-formato">Formato *</label>
          <select id="upload-formato" v-model="formato" class="input">
            <option value="">Selecione o formato</option>
            <option v-for="f in formatoOptions" :key="f" :value="f">{{ f }}</option>
          </select>
        </div>

        <div class="form-group">
          <label class="label" for="upload-classificacao">Classificação de Acesso *</label>
          <select id="upload-classificacao" v-model="classificacaoAcesso" class="input">
            <option value="">Selecione a classificação</option>
            <option v-for="c in classificacaoOptions" :key="c" :value="c">{{ c }}</option>
          </select>
        </div>

        <div class="form-group">
          <label class="label" for="upload-cobertura-espacial">Cobertura Espacial *</label>
          <input
            id="upload-cobertura-espacial"
            v-model="coberturaEspacial"
            type="text"
            class="input"
            placeholder="Ex: Estado de Goiás, Bioma Cerrado"
          />
        </div>

        <div class="form-group">
          <label class="label" for="upload-cobertura-ini">Cobertura Temporal (início)</label>
          <input id="upload-cobertura-ini" v-model="coberturaTemporalInicio" type="date" class="input" />
        </div>

        <div class="form-group">
          <label class="label" for="upload-cobertura-fim">Cobertura Temporal (fim)</label>
          <input id="upload-cobertura-fim" v-model="coberturaTemporalFim" type="date" class="input" />
        </div>

        <div class="form-group form-group--full">
          <label class="label" for="upload-descricao">Descrição (opcional)</label>
          <textarea
            id="upload-descricao"
            v-model="descricao"
            class="input textarea"
            rows="3"
            placeholder="Observações sobre o arquivo ou a base de dados..."
          ></textarea>
        </div>
      </div>

      <div class="step-actions">
        <span></span>
        <button class="btn" :disabled="!isStep1Valid" @click="nextStep">Avançar</button>
      </div>
    </div>

    <!-- Passo 2: Upload de Arquivo -->
    <div v-show="currentStep === 2" class="upload-card step-card animate-in">
      <div class="card-header">
        <FiUploadCloud size="20" class="card-header-icon" />
        <h2>Upload do Arquivo</h2>
      </div>
      <p class="card-description">
        <template v-if="formatoConfigAtual">
          Envie o arquivo no formato selecionado na etapa anterior: <strong>{{ formatoConfigAtual.label }}</strong>.
        </template>
        <template v-else>
          Arraste ou selecione o arquivo geoespacial (formatos suportados: .zip, .geojson, .csv, .gpkg, .kml, .kmz).
        </template>
      </p>

      <!-- Mensagem de Erro -->
      <Transition name="fade">
        <div v-if="erroMensagem" class="alert-error">
          <div class="alert-error__header">
            <FiAlertCircle size="18" />
            <strong>{{ erroMensagem.titulo }}</strong>
          </div>
          <p style="margin: 0; font-size: 0.9rem">{{ erroMensagem.texto }}</p>
        </div>
      </Transition>

      <!-- Área de Drop -->
      <div
        class="dropzone"
        :class="{ 'dropzone--dragover': isDragover, 'dropzone--disabled': isUploading }"
        @dragover.prevent="isDragover = true"
        @dragleave.prevent="isDragover = false"
        @drop.prevent="handleDrop"
        @click="triggerFileInput"
        style="cursor: pointer; position: relative"
      >
        <input
          id="file-upload"
          ref="fileInputRef"
          type="file"
          :accept="acceptAttribute"
          @change="handleFileSelect"
          :disabled="isUploading"
          style="display: none"
          required
        />
        <div class="dropzone__content">
          <div class="dropzone__icon-wrapper">
            <FiUploadCloud size="48" class="dropzone__icon" />
          </div>
          <template v-if="!arquivoSelecionado">
            <p class="dropzone__title">Arraste o arquivo aqui</p>
            <p class="dropzone__subtitle">ou clique para selecionar</p>
            <div class="dropzone__formats">
              <span v-if="formatoConfigAtual" class="format-tag format-tag-active">
                {{ formatoConfigAtual.tag }}
              </span>
              <template v-else>
                <span class="format-tag">.geojson / .json</span>
                <span class="format-tag">.zip (Shapefile / CSV)</span>
                <span class="format-tag">.csv</span>
                <span class="format-tag">.gpkg (GeoPackage)</span>
                <span class="format-tag">.kml / .kmz</span>
              </template>
            </div>
          </template>
          <template v-else>
            <p class="dropzone__title" style="color: var(--vis-brand-orange)">
              {{ arquivoSelecionado.name }}
            </p>
            <p class="dropzone__subtitle">
              {{ formatFileSize(arquivoSelecionado.size) }}
            </p>
            <div class="dropzone__formats">
              <span
                class="format-tag"
                style="background: rgba(16, 185, 129, 0.1); color: var(--vis-c-success)"
                >Selecionado</span
              >
            </div>
          </template>
        </div>
      </div>

      <div class="step-actions">
        <button class="btn btn_outline" :disabled="isUploading" @click="prevStep">Recuar</button>
        <button class="btn" :disabled="!isStep2Valid || isUploading" @click="handleUpload">
          <span v-if="!isUploading">Fazer Upload</span>
          <span v-else style="display: flex; align-items: center; gap: 0.5rem">
            <span
              class="spinner"
              style="
                width: 16px;
                height: 16px;
                border: 2px solid rgba(255, 255, 255, 0.3);
                border-top-color: white;
                border-radius: 50%;
                animation: spin 1s linear infinite;
              "
            ></span>
            Processando...
          </span>
        </button>
      </div>
    </div>

    <!-- Passo 3: Confirmação -->
    <div v-show="currentStep === 3" class="upload-card step-card animate-in">
      <div class="confirmation">
        <div class="confirmation__icon-wrapper">
          <FiCheck size="40" class="confirmation__icon" />
        </div>
        <h2 class="confirmation__title">Arquivo recebido com sucesso!</h2>
        <p class="confirmation__subtitle">
          A carga <strong>{{ nomeCamada }}</strong> foi recebida e entrou na esteira de validação.
        </p>

        <div v-if="uploadResult" class="upload-details">
          <div class="detail-row">
            <span>ID do Processo:</span>
            <strong>#{{ currentProcessoId || uploadResult.processo?.id || uploadResult.id }}</strong>
          </div>
          <div class="detail-row">
            <span>Formato Detectado:</span>
            <strong style="text-transform: uppercase">{{
              uploadResult.extensao || uploadResult.formato
            }}</strong>
          </div>
          <div class="detail-row">
            <span>Tamanho do Arquivo:</span>
            <strong>{{
              uploadResult.tamanhoBytes
                ? formatFileSize(uploadResult.tamanhoBytes)
                : uploadResult.tamanho
            }}</strong>
          </div>
          <div class="detail-row">
            <span>Hash de Integridade (SHA-256):</span>
            <strong class="hash-text" :title="uploadResult.hashSha256">{{
              uploadResult.hashSha256 || 'Calculando...'
            }}</strong>
          </div>

          <div class="epsg-box mt-3">
            <div class="epsg-box-item">
              <span class="epsg-label">EPSG Declarado</span>
              <strong class="epsg-val">{{
                formOptions.epsgs.find((e) => e.id === epsg)?.label || epsg || 'N/A'
              }}</strong>
            </div>
            <div class="epsg-box-item">
              <span class="epsg-label">Status da Carga</span>
              <strong
                class="epsg-val"
                :style="{
                  color: processamentoStatus?.quarentena
                    ? 'var(--color-danger, #ef4444)'
                    : processamentoStatus?.situacaoAtualId === 3
                    ? '#f59e0b'
                    : 'var(--vis-c-success, #10b981)'
                }"
              >
                {{
                  processamentoStatus?.quarentena
                    ? 'Em Quarentena / Análise'
                    : processamentoStatus?.situacaoAtualId === 3
                    ? 'Com Ressalva de EPSG'
                    : processamentoStatus
                    ? 'Processado com Sucesso'
                    : 'Enviada para processamento'
                }}
              </strong>
            </div>
          </div>

          <div
            v-if="processamentoErro"
            class="alert-warning mt-3"
            style="
              background: rgba(239, 68, 68, 0.08);
              border-left: 3px solid #ef4444;
              padding: 0.75rem 1rem;
              border-radius: 4px;
              color: var(--color-heading);
              font-size: 0.9rem;
            "
          >
            <strong>Aviso da Esteira:</strong> {{ processamentoErro }}
          </div>
        </div>

        <div class="confirmation__actions">
          <router-link
            v-if="currentProcessoId || uploadResult?.processo?.id || uploadResult?.id"
            :to="`/processo/${currentProcessoId || uploadResult?.processo?.id || uploadResult?.id}`"
            class="btn"
          >
            Acompanhar Processamento na Esteira
          </router-link>
          <button v-else type="button" class="btn" disabled style="opacity: 0.6; cursor: not-allowed;">
            Acompanhar Processamento na Esteira
          </button>
          <button class="btn btn_outline" @click="resetForm">+ Nova Carga</button>
          <router-link to="/" class="btn btn_outline">Voltar ao Dashboard</router-link>
        </div>
      </div>
    </div>
  </main>
</template>

<style scoped>
.upload-view {
  display: flex;
  flex-direction: column;
  padding: 2rem;
  max-width: 800px;
  margin: 0 auto;
}

/* Cabeçalho */
.upload-header {
  margin-bottom: 2rem;
}

.upload-header h1 {
  font-size: 1.75rem;
  font-weight: 800;
  color: var(--color-heading);
  margin: 0 0 0.5rem 0;
}

.upload-subtitle {
  color: var(--color-text);
  opacity: 0.7;
  font-size: 1rem;
  margin: 0;
}

/* Cartão */
.upload-card {
  background-color: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: 12px;
  padding: 2rem;
  width: 100%;
  box-sizing: border-box;
  box-shadow:
    0 4px 6px -1px rgba(0, 0, 0, 0.05),
    0 2px 4px -1px rgba(0, 0, 0, 0.03);
}

.card-header {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  margin-bottom: 0.5rem;
}

.card-header h2 {
  font-size: 1.25rem;
  font-weight: 700;
  color: var(--color-heading);
  margin: 0;
}

.card-header-icon {
  color: var(--vis-brand-orange);
}

/* Alertas */
.alert-error {
  margin-bottom: 1rem;
  color: #991b1b;
  background-color: #fef2f2;
  border: 1px solid #fecaca;
  padding: 1rem;
  border-radius: 8px;
}

.alert-error__header {
  display: flex;
  gap: 0.5rem;
  align-items: center;
  margin-bottom: 0.25rem;
}

.alert-error__text {
  margin: 0;
  font-size: 0.9rem;
}

.card-description {
  color: var(--color-text);
  opacity: 0.7;
  font-size: 0.9rem;
  margin: 0 0 1.5rem 0;
  line-height: 1.5;
}

/* Grid do Formulário */
.form-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 1.25rem;
}

.form-group {
  display: flex;
  flex-direction: column;
}

.form-group--full {
  grid-column: 1 / -1;
}

.form-group .label {
  margin-bottom: 0.4rem;
}

.input:not(.textarea) {
  height: 42px;
  box-sizing: border-box;
}

.textarea {
  resize: vertical;
  min-height: 80px;
  font-family: inherit;
}

/* Área de Drop */
.dropzone {
  border: 2px dashed var(--color-border);
  border-radius: 12px;
  padding: 3rem 2rem;
  background-color: var(--color-surface-soft, var(--vis-c-light-soft));
  text-align: center;
}

.dropzone__content {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 0.75rem;
}

.dropzone__icon-wrapper {
  width: 80px;
  height: 80px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, rgba(242, 101, 34, 0.1), rgba(242, 101, 34, 0.05));
  margin-bottom: 0.5rem;
}

.dropzone__icon {
  color: var(--vis-brand-orange);
  opacity: 0.8;
}

.dropzone__title {
  font-size: 1.1rem;
  font-weight: 700;
  color: var(--color-heading);
  margin: 0;
}

.dropzone__subtitle {
  font-size: 0.9rem;
  color: var(--color-text);
  opacity: 0.6;
  margin: 0;
}

.dropzone__formats {
  display: flex;
  flex-wrap: wrap;
  gap: 0.5rem;
  justify-content: center;
  margin-top: 0.5rem;
}

.format-tag {
  display: inline-block;
  padding: 0.2rem 0.5rem;
  border-radius: 4px;
  font-size: 0.7rem;
  font-weight: 600;
  font-family: monospace;
  background-color: rgba(242, 101, 34, 0.08);
  color: var(--vis-brand-orange);
  letter-spacing: 0.03em;
}

.format-tag-active {
  background-color: rgba(242, 101, 34, 0.16);
  border: 1px solid var(--vis-brand-orange);
  font-weight: 700;
  font-size: 0.78rem;
  padding: 0.3rem 0.7rem;
}

/* Ações do Passo */
.step-actions {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: 2rem;
  padding-top: 1.5rem;
  border-top: 1px solid var(--color-border);
}

/* Confirmação */
.confirmation {
  display: flex;
  flex-direction: column;
  align-items: center;
  text-align: center;
}

.confirmation__icon-wrapper {
  width: 72px;
  height: 72px;
  border-radius: 50%;
  background: linear-gradient(135deg, rgba(16, 185, 129, 0.15), rgba(16, 185, 129, 0.05));
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 1.5rem;
  animation: scaleIn 0.5s cubic-bezier(0.34, 1.56, 0.64, 1);
}

@keyframes scaleIn {
  from {
    transform: scale(0);
    opacity: 0;
  }
  to {
    transform: scale(1);
    opacity: 1;
  }
}

.confirmation__icon {
  color: var(--vis-c-success);
}

.confirmation__title {
  font-size: 1.5rem;
  font-weight: 800;
  color: var(--color-heading);
  margin: 0 0 0.5rem 0;
}

.confirmation__subtitle {
  color: var(--color-text);
  opacity: 0.7;
  margin: 0 0 2rem 0;
  font-size: 1rem;
}

.confirmation__actions {
  display: flex;
  gap: 1rem;
  flex-wrap: wrap;
  justify-content: center;
}

.upload-details {
  background: var(--color-background);
  border: 1px solid var(--color-border);
  border-radius: 8px;
  padding: 1.5rem;
  margin-bottom: 2rem;
  width: 100%;
  text-align: left;
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
}

.detail-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 0.9rem;
  padding-bottom: 0.5rem;
  border-bottom: 1px solid var(--color-border);
}

.detail-row:last-child {
  border-bottom: none;
  padding-bottom: 0;
}

.detail-row span {
  color: var(--color-text);
  opacity: 0.8;
}

.detail-row strong {
  color: var(--color-heading);
}

.epsg-box {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 1rem;
  margin-top: 0.5rem;
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: 6px;
  padding: 1rem;
}

.epsg-box-item {
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
  padding: 0.5rem;
  border-radius: 4px;
}

.epsg-label {
  font-size: 0.8rem;
  color: var(--color-text);
  opacity: 0.7;
}

.epsg-val {
  font-size: 0.95rem;
  color: var(--color-heading);
}

.warning-bg {
  background-color: rgba(234, 179, 8, 0.1);
  border-left: 3px solid var(--vis-c-warning, #eab308);
}

.text-warning {
  color: #a16207;
}

.hash-text {
  max-width: 200px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  background: rgba(0, 0, 0, 0.05);
  padding: 0.2rem 0.4rem;
  border-radius: 4px;
  font-family: monospace;
}

/* Animações */
.animate-in {
  animation: fadeIn 0.4s ease-out;
}

@keyframes fadeIn {
  from {
    opacity: 0;
    transform: translateY(8px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

/* Responsividade */
@media (max-width: 640px) {
  .form-grid {
    grid-template-columns: 1fr;
  }

  .stepper {
    padding: 0;
  }

  .step__label {
    font-size: 0.65rem;
  }

  .confirmation__actions {
    flex-direction: column;
    width: 100%;
  }

  .confirmation__actions .btn {
    width: 100%;
  }

  .epsg-box {
    grid-template-columns: 1fr;
  }
}
</style>
