import { MapPin, Search } from 'lucide-react'
import { useEffect, useId, useState } from 'react'
import { useDebounced } from '../hooks'

// Photon is a free OpenStreetMap geocoder with no key; lat/lon (Bengaluru) only ranks nearby places first
const PHOTON = 'https://photon.komoot.io/api'
const BIAS = { lat: 12.97, lng: 77.59 }

const ZOOM_BY_TYPE = { state: 8, county: 11, city: 12, district: 14, locality: 14, street: 16, house: 17 }

function toPlace(feature) {
  const p = feature.properties
  const [lng, lat] = feature.geometry.coordinates
  const name = p.name || [p.housenumber, p.street].filter(Boolean).join(' ')
  const area = [p.district, p.city, p.state].filter((part) => part && part !== name)
  return {
    id: `${p.osm_type}${p.osm_id}`,
    name,
    detail: [...new Set(area)].join(', '),
    lat,
    lng,
    zoom: ZOOM_BY_TYPE[p.type] ?? 15,
    inIndia: p.countrycode === 'IN',
  }
}

async function searchPlaces(query, signal) {
  const url = `${PHOTON}?q=${encodeURIComponent(query)}&lat=${BIAS.lat}&lon=${BIAS.lng}&limit=8&lang=en`
  const res = await fetch(url, { signal })
  if (!res.ok) throw new Error('Place search is not responding')
  const places = (await res.json()).features.map(toPlace).filter((p) => p.name)
  const local = places.filter((p) => p.inIndia)
  const seen = new Set()
  return (local.length ? local : places)
    .filter((p) => {
      const key = `${p.name}|${p.detail}`
      if (seen.has(key)) return false
      seen.add(key)
      return true
    })
    .slice(0, 6)
}

/**
 * Search box with suggestions. onPick gets { name, detail, lat, lng, zoom }.
 */
export default function PlaceSearch({ onPick, placeholder = 'Search a place', label = 'Search a place' }) {
  const [text, setText] = useState('')
  const [open, setOpen] = useState(false)
  const [active, setActive] = useState(-1)
  const [picked, setPicked] = useState(null)
  // like useApi: the answer remembers its query, so "loading" is just "not answered yet"
  const [answer, setAnswer] = useState({ query: null, places: [], failed: false })
  const listId = useId()

  const query = useDebounced(text.trim(), 400)
  const wanted = query.length >= 3 && query !== picked

  useEffect(() => {
    if (!wanted) return
    const controller = new AbortController()
    searchPlaces(query, controller.signal)
      .then((places) => setAnswer({ query, places, failed: false }))
      .catch((err) => {
        if (err.name !== 'AbortError') setAnswer({ query, places: [], failed: true })
      })
    return () => controller.abort()
  }, [query, wanted])

  const results = wanted && answer.query === query ? answer.places : []
  const status = !wanted ? 'idle' : answer.query !== query ? 'loading' : answer.failed ? 'error' : results.length ? 'idle' : 'empty'

  function pick(place) {
    setText(place.name)
    setPicked(place.name)
    setOpen(false)
    setActive(-1)
    onPick(place)
  }

  function onKeyDown(e) {
    if (e.key === 'ArrowDown' && results.length) {
      e.preventDefault()
      setOpen(true)
      setActive((i) => (i + 1) % results.length)
    } else if (e.key === 'ArrowUp' && results.length) {
      e.preventDefault()
      setActive((i) => (i <= 0 ? results.length - 1 : i - 1))
    } else if (e.key === 'Enter' && open && results.length) {
      e.preventDefault()
      pick(results[Math.max(active, 0)])
    } else if (e.key === 'Escape') {
      setOpen(false)
    }
  }

  const showList = open && wanted && (results.length > 0 || status !== 'idle')

  return (
    <div className="place-search">
      <Search size={16} className="place-search__icon" aria-hidden="true" />
      <input
        type="search"
        value={text}
        placeholder={placeholder}
        aria-label={label}
        role="combobox"
        aria-expanded={showList}
        aria-controls={listId}
        aria-autocomplete="list"
        aria-activedescendant={active >= 0 ? `${listId}-${active}` : undefined}
        autoComplete="off"
        onChange={(e) => {
          setText(e.target.value)
          setOpen(true)
          setActive(-1)
        }}
        onFocus={() => setOpen(true)}
        onBlur={() => setOpen(false)}
        onKeyDown={onKeyDown}
      />
      {showList && (
        <ul className="place-search__list" id={listId} role="listbox">
          {results.map((place, i) => (
            <li
              key={place.id}
              id={`${listId}-${i}`}
              role="option"
              aria-selected={i === active}
              className={i === active ? 'place-search__item place-search__item--active' : 'place-search__item'}
              // mousedown runs before the input's blur closes the list
              onMouseDown={(e) => {
                e.preventDefault()
                pick(place)
              }}
            >
              <MapPin size={15} aria-hidden="true" />
              <span>
                <strong>{place.name}</strong>
                {place.detail && <small>{place.detail}</small>}
              </span>
            </li>
          ))}
          {results.length === 0 && (
            <li className="place-search__status">
              {status === 'loading' ? 'Searching…' : status === 'error' ? 'Search is not responding, try again.' : 'No places found.'}
            </li>
          )}
        </ul>
      )}
    </div>
  )
}
