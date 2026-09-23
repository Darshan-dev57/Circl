import { LocateFixed } from 'lucide-react'
import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { useAuth } from '../auth-context'
import ActivityMap from '../components/ActivityMap'
import SeatRing from '../components/SeatRing'
import { CATEGORIES, distance, seatsText, when } from '../format'
import { useApi, useDebounced } from '../hooks'

// Koramangala, Bengaluru: where most of the demo data lives
const START = { lat: 12.9352, lng: 77.6245 }
const RADII = [1, 3, 5, 10]

function variantOf(a, selected) {
  if (a.id === selected) return 'selected'
  if (a.seatsLeft === 0) return 'full'
  return a.seatsLeft <= 2 ? 'low' : 'open'
}

function Row({ activity, selected, onHover }) {
  const ref = useRef(null)
  useEffect(() => {
    if (selected) ref.current?.scrollIntoView({ block: 'nearest', behavior: 'smooth' })
  }, [selected])
  const taken = activity.capacity - activity.seatsLeft
  return (
    <li ref={ref}>
      <Link
        to={`/activities/${activity.id}`}
        className={selected ? 'row row--selected' : 'row'}
        onMouseEnter={() => onHover(activity.id)}
        onFocus={() => onHover(activity.id)}
      >
        <SeatRing capacity={activity.capacity} taken={taken} size={34} />
        <span className="row__body">
          <span className="row__title">{activity.title}</span>
          <span className="row__meta">
            {CATEGORIES[activity.category]} · {when(activity.startsAt)}
            {activity.distanceM != null && <> · {distance(activity.distanceM)}</>}
          </span>
        </span>
        <span className={activity.seatsLeft === 0 ? 'row__seats row__seats--full' : activity.seatsLeft <= 2 ? 'row__seats row__seats--low' : 'row__seats'}>
          {seatsText(activity.seatsLeft, activity.capacity)}
        </span>
      </Link>
    </li>
  )
}

export default function Explore() {
  const { user } = useAuth()
  const navigate = useNavigate()
  const [center, setCenter] = useState(START)
  const [recenter, setRecenter] = useState(null)
  const [you, setYou] = useState(null)
  const [radius, setRadius] = useState(3)
  const [category, setCategory] = useState('')
  const [selected, setSelected] = useState(null)
  const [locating, setLocating] = useState(false)
  const [locateError, setLocateError] = useState(null)

  const searchAt = useDebounced(center, 350)
  const path = `/activities/nearby?lat=${searchAt.lat.toFixed(3)}&lng=${searchAt.lng.toFixed(3)}&radiusKm=${radius}&limit=50${category ? `&category=${category}` : ''}`
  const { data, error, loading, reload } = useApi(path)
  const activities = useMemo(() => data ?? [], [data])

  const pins = useMemo(
    () => activities.map((a) => ({ id: a.id, lat: a.lat, lng: a.lng, title: a.title, label: a.seatsLeft, variant: variantOf(a, selected) })),
    [activities, selected],
  )

  const onMove = useCallback((c) => setCenter({ lat: c.lat, lng: c.lng }), [])
  const onSelect = useCallback((id) => setSelected(id), [])

  function locate() {
    if (!navigator.geolocation) {
      setLocateError('This browser cannot share a location.')
      return
    }
    setLocating(true)
    setLocateError(null)
    navigator.geolocation.getCurrentPosition(
      (pos) => {
        const here = { lat: pos.coords.latitude, lng: pos.coords.longitude }
        setYou([here.lat, here.lng])
        setRecenter([here.lat, here.lng])
        setCenter(here)
        setLocating(false)
      },
      () => {
        setLocateError('Location was blocked, so the map stays where it is.')
        setLocating(false)
      },
      { enableHighAccuracy: false, timeout: 8000 },
    )
  }

  return (
    <main className="explore">
      <aside className="explore__panel">
        <div className="explore__head">
          <h1>Happening near you</h1>
          <p className="muted">Games, study groups and plans within {radius} km of the map centre. Seats go fast.</p>
        </div>

        <div className="filters" role="group" aria-label="Category">
          <button className={category === '' ? 'chip chip--on' : 'chip'} onClick={() => setCategory('')} aria-pressed={category === ''}>
            All
          </button>
          {Object.entries(CATEGORIES).map(([key, label]) => (
            <button key={key} className={category === key ? 'chip chip--on' : 'chip'} onClick={() => setCategory(key)} aria-pressed={category === key}>
              {label}
            </button>
          ))}
        </div>

        <div className="explore__tools">
          <label className="inline-field">
            <span>Radius</span>
            <select value={radius} onChange={(e) => setRadius(Number(e.target.value))}>
              {RADII.map((r) => (
                <option key={r} value={r}>{r} km</option>
              ))}
            </select>
          </label>
          <button className="button button--quiet button--small" onClick={locate} disabled={locating}>
            <LocateFixed size={16} aria-hidden="true" /> {locating ? 'Finding you…' : 'Use my location'}
          </button>
        </div>
        {locateError && <p className="note note--warn">{locateError}</p>}

        <div className="explore__list" aria-busy={loading}>
          {error ? (
            <div className="empty">
              <p>Could not load activities: {error.message}</p>
              <button className="button button--small" onClick={reload}>Try again</button>
            </div>
          ) : loading && !data ? (
            <ul className="rows" aria-hidden="true">
              {[0, 1, 2, 3].map((i) => (
                <li key={i} className="row row--skeleton"><span /><span /></li>
              ))}
            </ul>
          ) : activities.length === 0 ? (
            <div className="empty">
              <p><strong>Nothing within {radius} km right now.</strong></p>
              <p className="muted">Drag the map somewhere busier or widen the radius.</p>
              {user?.role === 'HOST' && (
                <button className="button button--small" onClick={() => navigate('/host/new')}>Host the first one here</button>
              )}
            </div>
          ) : (
            <ul className="rows">
              {activities.map((a) => (
                <Row key={a.id} activity={a} selected={a.id === selected} onHover={setSelected} />
              ))}
            </ul>
          )}
        </div>
      </aside>

      <ActivityMap
        className="explore__map"
        center={[START.lat, START.lng]}
        pins={pins}
        you={you}
        recenter={recenter}
        onMove={onMove}
        onSelect={onSelect}
      />
    </main>
  )
}
