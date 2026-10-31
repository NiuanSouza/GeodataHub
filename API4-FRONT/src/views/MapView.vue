<script setup lang="ts">
import { nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import L from 'leaflet'
import 'leaflet/dist/leaflet.css'
import { mapService } from '../services/map'

interface MapLocation {
  id: string
  name: string
  type: string
  latitude: number
  longitude: number
  status: 'Processado' | 'Em validação' | 'Aguardando' | 'Concluída'
}

interface TerritoryProperties {
  id?: number | string
  processoId?: number | string
  codigoImovel?: string
  anoSafra?: string
  name?: string
  category?: string
  status?: string
  source?: string
}

const route = useRoute()
const router = useRouter()
const mapElement = ref<HTMLElement | null>(null)
const searchTerm = ref('')
const selectedLocation = ref<MapLocation | null>(null)
const selectedCoordinates = ref({ latitude: -15.78, longitude: -47.93 })
const mapError = ref('')

const locations = ref<MapLocation[]>([])

interface BaseLayerItem {
  id: string
  name: string
  type: string
  processoId?: string | number
  color: string
  visible: boolean
  featureCount: number
  layer?: L.GeoJSON
  bounds?: L.LatLngBounds
}

const baseLayers = ref<BaseLayerItem[]>([])

const DEFAULT_PALETTE = [
  '#10b981', // Verde Esmeralda
  '#3b82f6', // Azul Royal
  '#8b5cf6', // Roxo Violeta
  '#f59e0b', // Âmbar / Laranja
  '#ec4899', // Rosa
  '#06b6d4', // Ciano
  '#ef4444', // Vermelho
  '#14b8a6', // Verde Água
  '#84cc16', // Lima
  '#6366f1', // Índigo
]

let map: L.Map | null = null
let markers: L.LayerGroup | null = null
let resizeObserver: ResizeObserver | null = null

const markerIcon = L.divIcon({
  className: 'map-marker-wrapper',
  html: '<span class="map-marker"></span>',
  iconSize: [22, 22],
  iconAnchor: [11, 11],
})

const selectLocation = (location: MapLocation) => {
  selectedLocation.value = location
  selectedCoordinates.value = { latitude: location.latitude, longitude: location.longitude }
  map?.flyTo([location.latitude, location.longitude], 8, { duration: 0.8 })
}

const clearBaseLayers = () => {
  baseLayers.value.forEach((b) => {
    if (b.layer && map) {
      map.removeLayer(b.layer)
    }
  })
}

const buildBaseLayersFromGeoJson = (geojson: any) => {
  clearBaseLayers()
  if (!geojson || !Array.isArray(geojson.features) || geojson.features.length === 0) {
    return
  }

  // Agrupa feições por processoId ou categoria
  const groups = new Map<string, any[]>()
  geojson.features.forEach((feature: any) => {
    const key = String(
      feature.properties?.processoId ||
      feature.properties?.category ||
      feature.properties?.source ||
      'base_padrao'
    )
    if (!groups.has(key)) {
      groups.set(key, [])
    }
    groups.get(key)!.push(feature)
  })

  const allBounds: L.LatLngBounds[] = []

  // Primeiro, atualiza as bases que já existem em baseLayers
  baseLayers.value.forEach((baseItem, idx) => {
    // Procura feições que correspondam a essa base pelo ID ou processoId
    const matchingKey = Array.from(groups.keys()).find((k) =>
      String(k) === String(baseItem.id) ||
      String(k) === String(baseItem.processoId) ||
      baseItem.name.toLowerCase().includes(String(k).toLowerCase())
    )

    if (matchingKey) {
      const features = groups.get(matchingKey)!
      groups.delete(matchingKey) // Marcado como consumido

      const color = baseItem.color || DEFAULT_PALETTE[idx % DEFAULT_PALETTE.length]
      const layer = L.geoJSON(
        { type: 'FeatureCollection', features },
        {
          style: () => ({
            color,
            weight: 2,
            opacity: 1,
            fillColor: color,
            fillOpacity: 0.3,
          }),
          onEachFeature: (feature, fLayer) => {
            const props = feature.properties || {}
            const title = props.codigoImovel || props.name || `Feição #${props.id || ''}`
            const details = [
              props.areaHectares ? `Área: ${props.areaHectares} ha` : null,
              props.category ? `Categoria: ${props.category}` : null,
              props.anoSafra ? `Safra: ${props.anoSafra}` : null,
              props.processoId ? `Processo: #${props.processoId}` : null,
              baseItem.name ? `Base: ${baseItem.name}` : null,
            ].filter(Boolean)

            fLayer.bindTooltip(title)
            fLayer.bindPopup(
              `<div style="font-size: 0.85rem; line-height: 1.4;">
                <strong style="color: ${color}; font-size: 0.95rem;">${title}</strong><br />
                ${details.join('<br />')}
                ${props.processoId ? `<div style="margin-top: 8px; border-top: 1px solid #eee; padding-top: 4px;">
                  <a href="/processo/${props.processoId}" style="color: #0284c7; text-decoration: underline; font-weight: 600;">Ver Detalhes do Processo</a>
                </div>` : ''}
              </div>`
            )
          },
        }
      )

      if (map && baseItem.visible) {
        layer.addTo(map)
      }

      const bounds = layer.getBounds().isValid() ? layer.getBounds() : undefined
      if (bounds) allBounds.push(bounds)

      baseItem.layer = layer
      baseItem.bounds = bounds
      baseItem.featureCount = features.length
    }
  })

  // Se houver grupos GeoJSON adicionais que não estavam em locations.value, adiciona-os
  groups.forEach((features, key) => {
    const idx = baseLayers.value.length
    const color = DEFAULT_PALETTE[idx % DEFAULT_PALETTE.length]
    const locMatch = locations.value.find((l) => String(l.id) === key)
    const baseName =
      locMatch?.name ||
      features[0]?.properties?.name ||
      features[0]?.properties?.category ||
      `Base #${key}`
    const baseType =
      locMatch?.type ||
      (features[0]?.properties?.anoSafra ? `Safra ${features[0].properties.anoSafra}` : 'Camada Territorial')

    const layer = L.geoJSON(
      { type: 'FeatureCollection', features },
      {
        style: () => ({
          color,
          weight: 2,
          opacity: 1,
          fillColor: color,
          fillOpacity: 0.3,
        }),
        onEachFeature: (feature, fLayer) => {
          const props = feature.properties || {}
          const title = props.codigoImovel || props.name || `Feição #${props.id || ''}`
          const details = [
            props.areaHectares ? `Área: ${props.areaHectares} ha` : null,
            props.category ? `Categoria: ${props.category}` : null,
            props.anoSafra ? `Safra: ${props.anoSafra}` : null,
            props.processoId ? `Processo: #${props.processoId}` : null,
            baseName ? `Base: ${baseName}` : null,
          ].filter(Boolean)

          fLayer.bindTooltip(title)
          fLayer.bindPopup(
            `<div style="font-size: 0.85rem; line-height: 1.4;">
              <strong style="color: ${color}; font-size: 0.95rem;">${title}</strong><br />
              ${details.join('<br />')}
              ${props.processoId ? `<div style="margin-top: 8px; border-top: 1px solid #eee; padding-top: 4px;">
                <a href="/processo/${props.processoId}" style="color: #0284c7; text-decoration: underline; font-weight: 600;">Ver Detalhes do Processo</a>
              </div>` : ''}
            </div>`
          )
        },
      }
    )

    if (map) {
      layer.addTo(map)
    }

    const bounds = layer.getBounds().isValid() ? layer.getBounds() : undefined
    if (bounds) allBounds.push(bounds)

    baseLayers.value.push({
      id: key,
      name: baseName,
      type: baseType,
      processoId: features[0]?.properties?.processoId,
      color,
      visible: true,
      featureCount: features.length,
      layer,
      bounds,
    })
  })

  // Ajusta zoom do mapa para englobar todas as camadas ativas
  if (map && allBounds.length > 0) {
    const combined = allBounds[0]
    for (let i = 1; i < allBounds.length; i++) {
      combined.extend(allBounds[i])
    }
    if (combined.isValid()) {
      map.fitBounds(combined.pad(0.12), { maxZoom: 14 })
    }
  }
}

const toggleBaseVisibility = (base: BaseLayerItem) => {
  if (!map || !base.layer) return
  if (base.visible) {
    if (!map.hasLayer(base.layer)) {
      base.layer.addTo(map)
    }
  } else {
    if (map.hasLayer(base.layer)) {
      map.removeLayer(base.layer)
    }
  }
}

const updateBaseColor = (base: BaseLayerItem) => {
  if (!base.layer) return
  base.layer.setStyle({
    color: base.color,
    fillColor: base.color,
  })
}

const zoomToBase = (base: BaseLayerItem) => {
  if (!map) return
  if (base.bounds && base.bounds.isValid()) {
    map.fitBounds(base.bounds.pad(0.12), { maxZoom: 14 })
  } else {
    const loc = locations.value.find((l) => l.id === base.id)
    if (loc) selectLocation(loc)
  }
}

const toggleAllBases = (visible: boolean) => {
  baseLayers.value.forEach((base) => {
    base.visible = visible
    toggleBaseVisibility(base)
  })
}

const findLocation = async () => {
  const normalizedTerm = searchTerm.value.trim().toLowerCase()
  if (!normalizedTerm) return

  const location = locations.value.find((item) => item.name.toLowerCase().includes(normalizedTerm))
  if (location) {
    selectLocation(location)
  }

  // US09: Atualiza as camadas territoriais filtradas por busca textual no servidor
  try {
    const fetchedTerritories = await mapService.getTerritories({ busca: searchTerm.value.trim() })
    buildBaseLayersFromGeoJson(fetchedTerritories)
  } catch (err) {
    console.warn('Falha ao filtrar camadas no mapa:', err)
  }

  const coordinates = normalizedTerm.split(',').map(Number)
  if (coordinates.length === 2 && coordinates.every((coordinate) => !Number.isNaN(coordinate))) {
    const latitude = coordinates[0]
    const longitude = coordinates[1]
    if (
      latitude !== undefined &&
      longitude !== undefined &&
      latitude >= -90 &&
      latitude <= 90 &&
      longitude >= -180 &&
      longitude <= 180
    ) {
      selectedLocation.value = null
      selectedCoordinates.value = { latitude, longitude }
      map?.flyTo([latitude, longitude], 8, { duration: 0.8 })
      return
    }
  }

  if (location) {
    mapError.value = ''
  }
}

const locateUser = () => {
  if (!navigator.geolocation) {
    mapError.value = 'A geolocalização não está disponível neste navegador.'
    return
  }

  navigator.geolocation.getCurrentPosition(
    ({ coords }) => {
      selectedLocation.value = null
      selectedCoordinates.value = { latitude: coords.latitude, longitude: coords.longitude }
      map?.flyTo([coords.latitude, coords.longitude], 12, { duration: 0.8 })
      mapError.value = ''
    },
    () => {
      mapError.value = 'Não foi possível obter sua localização atual.'
    },
  )
}

const limparFiltroProcesso = async () => {
  router.push({ path: '/mapa' })
  searchTerm.value = ''
  try {
    const fetchedTerritories = await mapService.getTerritories()
    buildBaseLayersFromGeoJson(fetchedTerritories)
  } catch (err) {
    console.warn('Erro ao restaurar territórios:', err)
  }
}

const carregarCamadasElocais = async () => {
  const rawLocations = await mapService.getPublishedLocations()
  locations.value = Array.isArray(rawLocations)
    ? rawLocations.map((loc: any) => ({
        id: String(loc.id ?? ''),
        name: String(loc.name ?? loc.nome ?? `Base #${loc.id}`),
        type: String(loc.type ?? loc.tipo ?? 'Base Territorial'),
        latitude: Number(loc.latitude ?? loc.lat ?? -15.78),
        longitude: Number(loc.longitude ?? loc.lng ?? -47.93),
        status: (loc.status ?? 'Concluída') as any,
      }))
    : []

  // Inicializa baseLayers com todas as bases publicadas para evitar que sumam da listagem
  baseLayers.value = locations.value.map((loc, idx) => ({
    id: String(loc.id),
    name: loc.name,
    type: loc.type,
    processoId: loc.id,
    color: DEFAULT_PALETTE[idx % DEFAULT_PALETTE.length],
    visible: true,
    featureCount: 0,
  }))

  const targetProcessoId = (route.query?.processoId || route.query?.id)
    ? Number(route.query.processoId || route.query.id)
    : undefined
  const requestedBase = String(route.query?.base || '').trim()
  const requestedYear = String(route.query?.year || '').trim()

  const initialFilters: any = {}
  if (targetProcessoId) {
    initialFilters.processoId = targetProcessoId
  } else {
    if (requestedBase) initialFilters.busca = requestedBase
    if (requestedYear) initialFilters.anoSafra = requestedYear
  }

  const fetchedTerritories = await mapService.getTerritories(initialFilters)
  buildBaseLayersFromGeoJson(fetchedTerritories)

  if (markers && map) {
    markers.clearLayers()
    locations.value.forEach((location) => {
      if (!Number.isNaN(location.latitude) && !Number.isNaN(location.longitude)) {
        const marker = L.marker([location.latitude, location.longitude], { icon: markerIcon })
          .bindTooltip(location.name)
          .on('click', () => selectLocation(location))
        marker.addTo(markers as L.LayerGroup)
      }
    })
  }

  // Se filtrou por processoId específico e encontrou polígonos, o fitBounds já ajustou o foco ideal.
  // Evita disparar flyTo para centroide genérico conflitante.
  const hasPolygonBounds = baseLayers.value.some((b) => b.bounds && b.bounds.isValid())

  if ((requestedBase || requestedYear) && !targetProcessoId) {
    if (requestedBase) searchTerm.value = requestedBase
    const match = locations.value.find(
      (item) =>
        item.name.toLowerCase().includes(requestedBase.toLowerCase()) ||
        item.type.toLowerCase().includes(requestedBase.toLowerCase()),
    )
    if (match) {
      selectLocation(match)
    } else if (locations.value[0]) {
      selectLocation(locations.value[0])
    }
  } else if (targetProcessoId && !hasPolygonBounds) {
    // Fallback: se a carga não tiver polígonos carregados, centraliza no centroide da base correspondente
    const match = locations.value.find((item) => String(item.id) === String(targetProcessoId))
    if (match) {
      selectLocation(match)
    }
  }
}

onMounted(async () => {
  const scrollContainer = document.querySelector('.content-scroll-container')
  if (scrollContainer) {
    scrollContainer.scrollTop = 0
  }

  if (!mapElement.value) return

  map = L.map(mapElement.value, { zoomControl: false }).setView([-15.78, -47.93], 4)
  L.control.zoom({ position: 'bottomright' }).addTo(map)

  const satelliteLayer = L.tileLayer(
    'https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}',
    {
      attribution: 'Tiles &copy; Esri',
      maxZoom: 19,
    },
  ).addTo(map)
  const streetsLayer = L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
    attribution: '&copy; OpenStreetMap contributors',
    maxZoom: 19,
  })

  L.control
    .layers(
      {
        Satélite: satelliteLayer,
        'Mapa de ruas': streetsLayer,
      },
      {},
      { position: 'topright' },
    )
    .addTo(map)

  markers = L.layerGroup().addTo(map)

  await carregarCamadasElocais()

  // Força o Leaflet a recalcular as dimensões exatas da janela/grid após a renderização do DOM
  await nextTick()
  setTimeout(() => {
    map?.invalidateSize()
  }, 100)

  // Observa redimensionamentos do container para manter o Leaflet alinhado
  if (typeof ResizeObserver !== 'undefined' && mapElement.value) {
    resizeObserver = new ResizeObserver(() => {
      map?.invalidateSize()
    })
    resizeObserver.observe(mapElement.value)
  }

  map.on('click', ({ latlng }) => {
    selectedLocation.value = null
    selectedCoordinates.value = { latitude: latlng.lat, longitude: latlng.lng }
    mapError.value = ''
  })
})

watch(
  () => [route.query?.processoId, route.query?.id, route.query?.base, route.query?.year],
  async () => {
    if (map) {
      await carregarCamadasElocais()
      map.invalidateSize()
    }
  },
)

onBeforeUnmount(() => {
  if (resizeObserver) {
    resizeObserver.disconnect()
    resizeObserver = null
  }
  clearBaseLayers()
  map?.remove()
  map = null
  markers = null
})
</script>

<template>
  <main class="map-view">
    <div class="page-heading">
      <div>
        <p class="eyebrow">Visualização territorial compartilhada</p>
        <h1>Mapa territorial</h1>
        <p class="page-description">
          Consulte as bases e os limites geográficos disponíveis. As áreas seguem a geometria dos documentos, incluindo contornos irregulares e multipartes.
        </p>
      </div>
      <button class="btn btn_outline locate-button" type="button" @click="locateUser">
        <span aria-hidden="true">⌖</span>
        Minha localização
      </button>
    </div>

    <div v-if="route.query?.processoId || route.query?.id" class="filter-badge-banner" role="status">
      <span>Exibindo delimitações da <strong>Carga #{{ route.query?.processoId || route.query?.id }}</strong></span>
      <button type="button" class="btn-clear-filter" @click="limparFiltroProcesso">Ver todas as cargas</button>
    </div>

    <section class="map-toolbar" aria-label="Ferramentas do mapa">
      <form class="search-form" @submit.prevent="findLocation">
        <label for="map-search">Buscar base ou coordenadas</label>
        <div class="search-controls">
          <input
            id="map-search"
            v-model="searchTerm"
            type="search"
            placeholder="Ex.: Base Brasília ou -15.78, -47.93"
          />
          <button class="btn" type="submit">Buscar</button>
        </div>
      </form>
      <p v-if="mapError" class="map-error" role="alert">{{ mapError }}</p>
    </section>

    <section class="map-layout">
      <div
        ref="mapElement"
        class="map-canvas"
        aria-label="Mapa interativo das bases operacionais e delimitações territoriais"
      ></div>

      <aside class="locations-panel" aria-label="Bases cadastradas">
        <div class="panel-heading">
          <div>
            <p class="eyebrow">Camadas e Delimitações</p>
            <h2>Bases cadastradas</h2>
          </div>
          <span class="location-count">{{ baseLayers.length || locations.length }}</span>
        </div>

        <!-- Atalhos de visualização rápida -->
        <div v-if="baseLayers.length > 0" class="layer-quick-actions">
          <button type="button" class="btn-quick-toggle" @click="toggleAllBases(true)">
            Mostrar todas
          </button>
          <span class="action-divider">|</span>
          <button type="button" class="btn-quick-toggle" @click="toggleAllBases(false)">
            Ocultar todas
          </button>
        </div>

        <div class="location-list">
          <div
            v-if="baseLayers.length === 0 && locations.length === 0"
            class="empty-locations-state"
            style="padding: 1.5rem 0.5rem; text-align: center; color: var(--vis-c-text-dark-2); font-size: 0.82rem;"
          >
            Nenhuma base oficial cadastrada ainda.
          </div>

          <!-- Camadas carregadas com polígonos e controle de cores -->
          <template v-if="baseLayers.length > 0">
            <div
              v-for="base in baseLayers"
              :key="base.id"
              class="base-layer-card"
              :class="{ 'base-layer-inactive': !base.visible }"
            >
              <div class="base-layer-header">
                <!-- Checkbox de ativação/desativação da base -->
                <label class="base-checkbox-label" title="Ativar ou desativar esta base no mapa">
                  <input
                    type="checkbox"
                    v-model="base.visible"
                    class="base-checkbox"
                    @change="toggleBaseVisibility(base)"
                  />
                </label>

                <!-- Seletor de cor com amostra interativa -->
                <div class="color-picker-control" title="Clique para escolher a cor dos polígonos desta base">
                  <input
                    type="color"
                    v-model="base.color"
                    class="native-color-picker"
                    @input="updateBaseColor(base)"
                  />
                  <span
                    class="color-indicator-swatch"
                    :style="{ backgroundColor: base.color }"
                  ></span>
                </div>

                <!-- Informações e zoom na base -->
                <div class="base-layer-info" @click="zoomToBase(base)">
                  <strong>{{ base.name }}</strong>
                  <small>{{ base.type }} &bull; {{ base.featureCount }} polígonos</small>
                </div>

                <button
                  type="button"
                  class="btn-zoom-target"
                  title="Focar nesta base no mapa"
                  @click="zoomToBase(base)"
                >
                  <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                    <circle cx="11" cy="11" r="8"></circle>
                    <line x1="21" y1="21" x2="16.65" y2="16.65"></line>
                  </svg>
                </button>
              </div>
            </div>
          </template>

          <!-- Fallback para lista de pontos se não houver polígonos carregados -->
          <template v-else>
            <button
              v-for="location in locations"
              :key="location.id"
              type="button"
              class="location-item"
              :class="{ 'location-item-active': selectedLocation?.id === location.id }"
              @click="selectLocation(location)"
            >
              <span class="location-dot" :class="`status-${location.status.toLowerCase().replace(' ', '-')}`"></span>
              <span class="location-content">
                <strong>{{ location.name }}</strong>
                <small>{{ location.type }}</small>
                <small>{{ location.latitude.toFixed(4) }}, {{ location.longitude.toFixed(4) }}</small>
              </span>
            </button>
          </template>
        </div>

        <div class="coordinates-card">
          <span>Coordenada selecionada</span>
          <strong>
            {{ selectedCoordinates.latitude.toFixed(4) }}, {{ selectedCoordinates.longitude.toFixed(4) }}
          </strong>
          <small>{{ selectedLocation?.name ?? 'Clique em qualquer ponto do mapa' }}</small>
        </div>
      </aside>
    </section>
  </main>
</template>

<style scoped>
.map-view {
  display: grid;
  gap: 24px;
}

.page-heading,
.panel-heading,
.search-controls {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
}

.eyebrow {
  margin-bottom: 4px;
  color: var(--color-brand);
  font-size: 0.7rem;
  font-weight: 800;
  letter-spacing: 0.12em;
  text-transform: uppercase;
}

h1,
h2 {
  color: var(--color-heading);
}

h1 {
  font-size: clamp(1.8rem, 3vw, 2.6rem);
  font-weight: 750;
}

h2 {
  font-size: 1.1rem;
  font-weight: 700;
}

.page-description {
  margin-top: 4px;
  color: var(--color-text);
}

.filter-badge-banner {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0.65rem 1rem;
  background: rgba(242, 101, 34, 0.12);
  border: 1px solid rgba(242, 101, 34, 0.35);
  border-radius: 6px;
  font-size: 0.88rem;
  color: var(--color-heading);
}

.btn-clear-filter {
  background: transparent;
  border: 1px solid var(--vis-brand-orange, #f26522);
  color: var(--vis-brand-orange, #f26522);
  font-size: 0.78rem;
  font-weight: 600;
  padding: 0.25rem 0.6rem;
  border-radius: 4px;
  cursor: pointer;
  transition: all 0.2s;
}

.btn-clear-filter:hover {
  background: var(--vis-brand-orange, #f26522);
  color: #fff;
}

.locate-button {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  white-space: nowrap;
}

.map-toolbar {
  padding: 18px 20px;
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-top: 3px solid var(--color-brand);
}

.search-form {
  display: grid;
  gap: 8px;
}

.search-form label {
  color: var(--color-heading);
  font-size: 0.78rem;
  font-weight: 700;
}

.search-controls {
  justify-content: initial;
}

.search-controls input {
  width: min(620px, 100%);
  min-height: 42px;
  padding: 0 12px;
  color: var(--color-text);
  background: var(--color-surface-soft);
  border: 1px solid var(--color-border);
}

.map-error {
  margin-top: 8px;
  color: var(--vis-c-danger);
  font-size: 0.82rem;
}

.map-layout {
  height: calc(100vh - 240px);
  min-height: 560px;
  display: grid;
  grid-template-columns: minmax(0, 1fr) 310px;
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  overflow: hidden;
}

.map-canvas {
  height: 100%;
  min-height: 560px;
  z-index: 0;
}

.locations-panel {
  display: flex;
  flex-direction: column;
  padding: 22px 18px;
  border-left: 1px solid var(--color-border);
  min-width: 0;
  max-width: 310px;
  height: 100%;
  overflow: hidden;
}

.location-count {
  min-width: 28px;
  min-height: 28px;
  display: grid;
  place-items: center;
  color: white;
  background: var(--color-brand);
  border-radius: 50%;
  font-size: 0.8rem;
  font-weight: 800;
}

.location-list {
  display: flex;
  flex-direction: column;
  gap: 4px;
  margin-top: 18px;
  overflow-y: auto;
  overflow-x: hidden;
  max-height: 100%;
  padding-right: 4px;
}

/* Controles de Camadas e Cores */
.layer-quick-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 10px;
  font-size: 0.76rem;
}

.btn-quick-toggle {
  background: transparent;
  border: none;
  color: var(--vis-brand-orange, #f26522);
  font-size: 0.76rem;
  font-weight: 600;
  cursor: pointer;
  padding: 0;
  text-decoration: underline;
}

.btn-quick-toggle:hover {
  opacity: 0.8;
}

.action-divider {
  color: var(--color-border);
}

.base-layer-card {
  padding: 8px 10px;
  border-radius: 6px;
  background: var(--color-surface-soft, rgba(0, 0, 0, 0.03));
  border: 1px solid var(--color-border);
  transition: all 0.2s ease;
  margin-bottom: 6px;
}

.base-layer-card:hover {
  border-color: var(--color-brand);
}

.base-layer-inactive {
  opacity: 0.55;
  background: transparent;
}

.base-layer-header {
  display: flex;
  align-items: center;
  gap: 8px;
}

.base-checkbox-label {
  display: flex;
  align-items: center;
  cursor: pointer;
  margin: 0;
}

.base-checkbox {
  width: 16px;
  height: 16px;
  accent-color: var(--vis-brand-orange, #f26522);
  cursor: pointer;
}

.color-picker-control {
  position: relative;
  width: 22px;
  height: 22px;
  flex-shrink: 0;
  cursor: pointer;
}

.native-color-picker {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  opacity: 0;
  cursor: pointer;
}

.color-indicator-swatch {
  display: block;
  width: 20px;
  height: 20px;
  border-radius: 50%;
  border: 2px solid #ffffff;
  box-shadow: 0 0 0 1px rgba(0, 0, 0, 0.2);
  pointer-events: none;
  transition: transform 0.15s ease;
}

.color-picker-control:hover .color-indicator-swatch {
  transform: scale(1.15);
}

.base-layer-info {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 2px;
  cursor: pointer;
}

.base-layer-info strong {
  font-size: 0.82rem;
  color: var(--color-heading);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.base-layer-info small {
  font-size: 0.72rem;
  color: var(--color-text);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.btn-zoom-target {
  background: transparent;
  border: none;
  color: var(--color-text);
  padding: 4px;
  border-radius: 4px;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: color 0.2s;
}

.btn-zoom-target:hover {
  color: var(--vis-brand-orange, #f26522);
}

.location-item {
  width: 100%;
  display: flex;
  align-items: flex-start;
  gap: 10px;
  padding: 12px 8px;
  color: var(--color-text);
  text-align: left;
  background: transparent;
  border: 1px solid transparent;
  cursor: pointer;
}

.location-item:hover,
.location-item-active {
  background: var(--color-surface-soft);
  border-color: var(--color-border);
}

.location-dot {
  width: 9px;
  height: 9px;
  margin-top: 5px;
  flex-shrink: 0;
  background: var(--vis-c-success);
  border-radius: 50%;
}

.status-em-validação {
  background: var(--vis-c-warning);
}

.status-aguardando {
  background: var(--vis-c-info);
}

.location-content {
  display: grid;
  gap: 2px;
}

.location-content strong {
  color: var(--color-heading);
  font-size: 0.88rem;
}

.location-content small,
.coordinates-card span,
.coordinates-card small {
  color: var(--color-text);
  font-size: 0.74rem;
}

.coordinates-card {
  display: grid;
  gap: 4px;
  margin-top: auto;
  padding: 14px;
  background: var(--color-surface-soft);
  border-left: 3px solid var(--color-brand);
  min-width: 0;
  overflow: hidden;
}

.coordinates-card small {
  word-break: break-word;
  overflow: hidden;
  text-overflow: ellipsis;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
}

.coordinates-card strong {
  color: var(--color-heading);
  font-size: 0.86rem;
}

@media (max-width: 900px) {
  .map-layout {
    grid-template-columns: 1fr;
  }

  .locations-panel {
    border-top: 1px solid var(--color-border);
    border-left: 0;
  }

  .location-list {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}

@media (max-width: 620px) {
  .page-heading {
    align-items: flex-start;
    flex-direction: column;
  }

  .locate-button {
    width: 100%;
    justify-content: center;
  }

  .search-controls {
    align-items: stretch;
    flex-direction: column;
  }

  .search-controls input {
    width: 100%;
  }

  .location-list {
    grid-template-columns: 1fr;
  }
}
</style>
