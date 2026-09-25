import L from 'leaflet'
import { useEffect, useMemo } from 'react'
import { MapContainer, Marker, TileLayer, useMap, useMapEvents } from 'react-leaflet'
import 'leaflet/dist/leaflet.css'

// standard OpenStreetMap tiles (fine for a small project, no key); the CSS mutes their colours
const TILES = 'https://tile.openstreetmap.org/{z}/{x}/{y}.png'
const ATTRIBUTION = '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors'

// one icon object per look, so leaflet does not rebuild markers on every render
const icons = new Map()
function pinIcon(label, variant) {
  const key = `${label}|${variant}`
  if (!icons.has(key)) {
    icons.set(
      key,
      L.divIcon({
        className: '',
        html: `<span class="pin pin--${variant}">${label}</span>`,
        iconSize: [34, 34],
        iconAnchor: [17, 17],
      }),
    )
  }
  return icons.get(key)
}

const youIcon = L.divIcon({ className: '', html: '<span class="you"></span>', iconSize: [18, 18], iconAnchor: [9, 9] })

function Recenter({ center }) {
  const map = useMap()
  useEffect(() => {
    if (center) map.setView(center, map.getZoom(), { animate: true })
  }, [center, map])
  return null
}

function Watch({ onMove, onPick }) {
  useMapEvents({
    moveend: (e) => onMove?.(e.target.getCenter()),
    click: (e) => onPick?.(e.latlng),
  })
  return null
}

/**
 * pins: [{ id, lat, lng, label, variant }]  variant = open | low | full | selected
 */
export default function ActivityMap({ center, zoom = 14, pins = [], you, onSelect, onMove, onPick, recenter, className }) {
  const markers = useMemo(
    () =>
      pins.map((p) => (
        <Marker
          key={p.id}
          position={[p.lat, p.lng]}
          icon={pinIcon(p.label, p.variant)}
          keyboard
          title={p.title}
          zIndexOffset={p.variant === 'selected' ? 1000 : 0}
          eventHandlers={{ click: () => onSelect?.(p.id) }}
        />
      )),
    [pins, onSelect],
  )

  return (
    <MapContainer center={center} zoom={zoom} className={className} scrollWheelZoom zoomControl={false}>
      <TileLayer url={TILES} attribution={ATTRIBUTION} maxZoom={19} />
      {you && <Marker position={you} icon={youIcon} interactive={false} />}
      {markers}
      <Watch onMove={onMove} onPick={onPick} />
      {recenter && <Recenter center={recenter} />}
    </MapContainer>
  )
}
