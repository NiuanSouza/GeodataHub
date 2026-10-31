<script setup lang="ts">
import { ref, computed, onMounted, onBeforeUnmount, watch, nextTick } from 'vue'
import L from 'leaflet'
import 'leaflet/dist/leaflet.css'
import api from '../services/api'
import { useRouter } from 'vue-router'

const props = withDefaults(
  defineProps<{
    processoId: string | number
    height?: string
    title?: string
    isPublished?: boolean
    status?: string
  }>(),
  {
    height: '340px',
    title: 'Pré-visualização Geoespacial da Carga',
    isPublished: false,
  },
)

const canOpenFullMap = computed(() => {
  if (props.isPublished) return true
  const st = (props.status || '').toLowerCase()
  return st === 'concluída' || st === 'concluida' || st === 'publicado' || st === 'fechado'
})

const router = useRouter()
const mapContainer = ref<HTMLElement | null>(null)
let map: L.Map | null = null
let geoJsonLayer: L.GeoJSON | null = null

const isLoading = ref(true)
const totalFeicoes = ref(0)
const areaTotalHa = ref<number | null>(null)
const errorMsg = ref('')

const carregarGeometrias = async () => {
  if (!props.processoId) return
  isLoading.value = true
  errorMsg.value = ''

  try {
    // 1. Tenta carregar da revisão de publicação
    try {
      const res = await api.get(`/publicacao/${props.processoId}/revisao`)
      const geojson = res.data?.feicoesGeoJson
      if (geojson && geojson.features && geojson.features.length > 0) {
        isLoading.value = false
        renderizarMapa(geojson)
        totalFeicoes.value = geojson.features.length
        return
      }
    } catch (e) {
      // Tenta rota alternativa
    }

    // 2. Tenta carregar via /carga/{id}/revisao-publicacao
    try {
      const res = await api.get(`/carga/${props.processoId}/revisao-publicacao`)
      const geojson = res.data?.feicoesGeoJson
      if (geojson && geojson.features && geojson.features.length > 0) {
        isLoading.value = false
        renderizarMapa(geojson)
        totalFeicoes.value = geojson.features.length
        return
      }
    } catch (e) {
      // Segue para fallback
    }

    // 3. Fallback para /mapa/camadas?processoId=
    const fallbackRes = await api.get('/mapa/camadas', {
      params: { processoId: props.processoId },
    })
    if (fallbackRes.data && fallbackRes.data.features && fallbackRes.data.features.length > 0) {
      isLoading.value = false
      renderizarMapa(fallbackRes.data)
      totalFeicoes.value = fallbackRes.data.features.length
      return
    }

    errorMsg.value = 'Nenhuma geometria encontrada para esta carga.'
  } catch (err: any) {
    errorMsg.value = 'Geometrias ainda não processadas ou indisponíveis.'
  } finally {
    isLoading.value = false
  }
}

const renderizarMapa = async (geojson: GeoJSON.GeoJsonObject) => {
  await nextTick()
  if (!mapContainer.value) return

  if (!map) {
    map = L.map(mapContainer.value, {
      zoomControl: true,
      attributionControl: false,
    }).setView([-15.78, -47.93], 5)

    L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
      maxZoom: 19,
    }).addTo(map)
  }

  map.invalidateSize()

  if (geoJsonLayer) {
    map.removeLayer(geoJsonLayer)
    geoJsonLayer = null
  }

  let totalArea = 0

  geoJsonLayer = L.geoJSON(geojson, {
    style: {
      color: '#f26522',
      weight: 2,
      fillColor: '#f26522',
      fillOpacity: 0.35,
    },
    onEachFeature: (feature, layer) => {
      const props = feature.properties || {}
      const cod = props.codigoImovel || props.identificador || `Feição #${props.id || ''}`
      const area = props.areaHectares ? Number(props.areaHectares) : 0
      if (area > 0) totalArea += area

      layer.bindTooltip(cod)
      layer.bindPopup(`
        <div style="font-size: 0.85rem;">
          <strong style="color: #f26522;">${cod}</strong><br/>
          ${area > 0 ? `Área: ${area.toFixed(4)} ha<br/>` : ''}
          Feição ID: #${props.id || ''}
        </div>
      `)
    },
  }).addTo(map)

  if (totalArea > 0) {
    areaTotalHa.value = Number(totalArea.toFixed(4))
  }

  setTimeout(() => {
    map?.invalidateSize()
    if (geoJsonLayer && geoJsonLayer.getLayers().length > 0 && geoJsonLayer.getBounds().isValid()) {
      map?.fitBounds(geoJsonLayer.getBounds().pad(0.12), { maxZoom: 15 })
    }
  }, 150)
}

const abrirNoMapaCompleto = () => {
  router.push(`/mapa?processoId=${props.processoId}`)
}

watch(() => props.processoId, () => {
  carregarGeometrias()
})

onMounted(() => {
  carregarGeometrias()
})

onBeforeUnmount(() => {
  if (map) {
    map.remove()
    map = null
    geoJsonLayer = null
  }
})
</script>

<template>
  <div class="processo-mini-mapa-card">
    <div class="mini-mapa-header">
      <div class="header-info">
        <h4>{{ title }}</h4>
        <span v-if="totalFeicoes > 0" class="badge-count">
          {{ totalFeicoes }} feição(ões){{ areaTotalHa ? ` · ${areaTotalHa} ha` : '' }}
        </span>
      </div>
      <button
        v-if="canOpenFullMap"
        type="button"
        class="btn-ver-mapa"
        @click="abrirNoMapaCompleto"
        title="Visualizar no mapa com ferramentas completas"
      >
        <span>Abrir no Mapa Completo</span>
        <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <path d="M18 13v6a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2h6"></path>
          <polyline points="15 3 21 3 21 9"></polyline>
          <line x1="10" y1="14" x2="21" y2="3"></line>
        </svg>
      </button>
      <span v-else class="badge-preview-tag" title="Disponível no mapa completo após a publicação oficial">
        Pré-visualização
      </span>
    </div>

    <div v-if="isLoading" class="mini-mapa-loading" :style="{ height }">
      <div class="spinner"></div>
      <span>Carregando geometrias no mapa...</span>
    </div>

    <div v-else-if="errorMsg" class="mini-mapa-empty" :style="{ height }">
      <svg width="32" height="32" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5">
        <polygon points="1 6 1 22 8 18 16 22 23 18 23 2 16 6 8 2 1 6"></polygon>
        <line x1="8" y1="2" x2="8" y2="18"></line>
        <line x1="16" y1="6" x2="16" y2="22"></line>
      </svg>
      <p>{{ errorMsg }}</p>
    </div>

    <div
      ref="mapContainer"
      class="mini-mapa-canvas"
      :style="{ height }"
      v-show="!isLoading && !errorMsg"
    ></div>
  </div>
</template>

<style scoped>
.processo-mini-mapa-card {
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: 8px;
  overflow: hidden;
  margin-bottom: 1.5rem;
}

.mini-mapa-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0.85rem 1.15rem;
  background: var(--color-surface-soft, rgba(0, 0, 0, 0.02));
  border-bottom: 1px solid var(--color-border);
}

.header-info {
  display: flex;
  align-items: center;
  gap: 0.75rem;
}

.header-info h4 {
  margin: 0;
  font-size: 0.95rem;
  font-weight: 700;
  color: var(--color-heading);
}

.badge-count {
  font-size: 0.76rem;
  font-weight: 600;
  padding: 0.2rem 0.55rem;
  background: rgba(242, 101, 34, 0.12);
  color: var(--vis-brand-orange, #f26522);
  border-radius: 999px;
}

.badge-preview-tag {
  font-size: 0.75rem;
  font-weight: 600;
  padding: 0.25rem 0.65rem;
  background: var(--color-surface-soft, rgba(0, 0, 0, 0.05));
  color: var(--color-text, #64748b);
  border: 1px dashed var(--color-border, #cbd5e1);
  border-radius: 999px;
}

.btn-ver-mapa {
  display: inline-flex;
  align-items: center;
  gap: 0.4rem;
  font-size: 0.8rem;
  font-weight: 600;
  color: var(--vis-brand-orange, #f26522);
  background: transparent;
  border: 1px solid var(--vis-brand-orange, #f26522);
  padding: 0.35rem 0.75rem;
  border-radius: 5px;
  cursor: pointer;
  transition: all 0.2s ease;
}

.btn-ver-mapa:hover {
  background: var(--vis-brand-orange, #f26522);
  color: #fff;
}

.mini-mapa-canvas {
  width: 100%;
  z-index: 1;
}

.mini-mapa-loading,
.mini-mapa-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 0.65rem;
  color: var(--vis-c-text-dark-2);
  background: var(--color-background);
  font-size: 0.85rem;
}

.mini-mapa-empty svg {
  opacity: 0.5;
}

.spinner {
  width: 24px;
  height: 24px;
  border: 3px solid rgba(242, 101, 34, 0.2);
  border-top-color: var(--vis-brand-orange, #f26522);
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}
</style>
