import { useCallback, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { api } from '../api'
import ActivityMap from '../components/ActivityMap'
import { CATEGORIES } from '../format'

const START = [12.9352, 77.6245]

// datetime-local wants local time without a zone, e.g. 2026-10-10T18:30
function localInput(date) {
  const pad = (n) => String(n).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}`
}

function tomorrowEvening() {
  const d = new Date()
  d.setDate(d.getDate() + 1)
  d.setHours(18, 30, 0, 0)
  return localInput(d)
}

export default function NewActivity() {
  const navigate = useNavigate()
  const [form, setForm] = useState({
    title: '',
    category: 'CRICKET',
    description: '',
    startsAt: tomorrowEvening(),
    durationMinutes: 120,
    capacity: 10,
    minReliability: 0,
  })
  const [spot, setSpot] = useState(null)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState(null)

  const set = (key) => (e) => setForm((f) => ({ ...f, [key]: e.target.value }))
  const onPick = useCallback((latlng) => setSpot([latlng.lat, latlng.lng]), [])

  async function submit(e) {
    e.preventDefault()
    if (!spot) {
      setError('Tap the map to drop a pin where people should meet.')
      return
    }
    setBusy(true)
    setError(null)
    try {
      const created = await api('/activities', {
        method: 'POST',
        body: {
          title: form.title,
          category: form.category,
          description: form.description || null,
          startsAt: new Date(form.startsAt).toISOString(),
          durationMinutes: Number(form.durationMinutes),
          capacity: Number(form.capacity),
          minReliability: Number(form.minReliability),
          lat: spot[0],
          lng: spot[1],
        },
      })
      navigate(`/activities/${created.id}`)
    } catch (err) {
      setError(err.message)
    } finally {
      setBusy(false)
    }
  }

  return (
    <main className="page new-activity">
      <form className="new-activity__form" onSubmit={submit}>
        <h1>Host something</h1>
        <p className="muted">People within a few km see it on their map and can grab a seat.</p>

        <label className="field">
          <span>What is it?</span>
          <input value={form.title} onChange={set('title')} placeholder="Sunday morning cricket, tennis ball" maxLength={80} required />
        </label>

        <fieldset className="field">
          <legend>Kind</legend>
          <div className="filters">
            {Object.entries(CATEGORIES).map(([key, label]) => (
              <label key={key} className={form.category === key ? 'chip chip--on' : 'chip'}>
                <input type="radio" name="category" value={key} checked={form.category === key} onChange={set('category')} className="visually-hidden" />
                {label}
              </label>
            ))}
          </div>
        </fieldset>

        <div className="field-row">
          <label className="field">
            <span>Starts</span>
            <input type="datetime-local" value={form.startsAt} onChange={set('startsAt')} required />
          </label>
          <label className="field">
            <span>Length</span>
            <select value={form.durationMinutes} onChange={set('durationMinutes')}>
              {[30, 60, 90, 120, 180, 240].map((m) => (
                <option key={m} value={m}>{m < 60 ? `${m} min` : `${m / 60} h`}</option>
              ))}
            </select>
          </label>
        </div>

        <div className="field-row">
          <label className="field">
            <span>Seats</span>
            <input type="number" min="2" max="50" value={form.capacity} onChange={set('capacity')} required />
          </label>
          <label className="field">
            <span>Minimum reliability</span>
            <input type="number" min="0" max="100" step="5" value={form.minReliability} onChange={set('minReliability')} />
          </label>
        </div>

        <label className="field">
          <span>Details <small className="muted">(optional)</small></span>
          <textarea value={form.description} onChange={set('description')} rows={4} maxLength={1000}
            placeholder="Bring your own racket. We split the court fee." />
        </label>

        {error && <p className="note note--error" role="alert">{error}</p>}
        <button className="button button--wide" disabled={busy}>{busy ? 'Posting…' : 'Post activity'}</button>
      </form>

      <div className="new-activity__map">
        <p className="map-hint">{spot ? 'Pin dropped. Tap again to move it.' : 'Tap the map where people should meet.'}</p>
        <ActivityMap
          className="pick-map"
          center={START}
          zoom={14}
          onPick={onPick}
          pins={spot ? [{ id: 'new', lat: spot[0], lng: spot[1], label: '', variant: 'selected', title: 'Meeting point' }] : []}
        />
      </div>
    </main>
  )
}
