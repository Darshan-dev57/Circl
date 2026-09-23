import { ArrowLeft, MapPin, Minus, Plus, ShieldCheck } from 'lucide-react'
import { useEffect, useRef, useState } from 'react'
import { Link, useLocation, useNavigate, useParams, useSearchParams } from 'react-router-dom'
import { api } from '../api'
import { useAuth } from '../auth-context'
import ActivityMap from '../components/ActivityMap'
import HostTools from '../components/HostTools'
import SeatRing from '../components/SeatRing'
import { CATEGORIES, countdown, when } from '../format'
import { useApi, useLiveSeats, useNow } from '../hooks'

function PartyStepper({ value, onChange }) {
  return (
    <div className="stepper">
      <span id="party-label">People, including you</span>
      <div className="stepper__controls" role="group" aria-labelledby="party-label">
        <button className="icon-button" onClick={() => onChange(value - 1)} disabled={value <= 1} aria-label="One less">
          <Minus size={16} />
        </button>
        <output aria-live="polite">{value}</output>
        <button className="icon-button" onClick={() => onChange(value + 1)} disabled={value >= 4} aria-label="One more">
          <Plus size={16} />
        </button>
      </div>
    </div>
  )
}

function CheckIn({ activityId, code: fromLink, onDone }) {
  const [code, setCode] = useState(fromLink ?? '')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState(null)
  async function submit(e) {
    e.preventDefault()
    setBusy(true)
    setError(null)
    try {
      await api(`/activities/${activityId}/checkin`, { method: 'POST', body: { code: code.trim() } })
      onDone()
    } catch (err) {
      setError(err.message)
    } finally {
      setBusy(false)
    }
  }
  return (
    <form className="checkin" onSubmit={submit}>
      <label className="field">
        <span>Check-in code</span>
        <input value={code} onChange={(e) => setCode(e.target.value)} placeholder="Scan the host's QR or paste the code" required />
      </label>
      <button className="button" disabled={busy || !code.trim()}>{busy ? 'Checking in…' : "I'm here"}</button>
      {error && <p className="note note--error" role="alert">{error}</p>}
    </form>
  )
}

function SeatPanel({ activity, seats, live, me, status, reloadStatus }) {
  const navigate = useNavigate()
  const location = useLocation()
  const [params] = useSearchParams()
  const now = useNow(1000)
  const [party, setParty] = useState(1)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState(null)
  const [message, setMessage] = useState(null)
  // one key per join attempt: a double click or a retry after a timeout reuses it
  const joinKey = useRef(null)

  const capacity = seats?.capacity ?? activity.capacity
  const taken = seats?.seatsTaken ?? activity.seatsTaken
  const left = Math.max(0, capacity - taken)
  const cancelled = (seats?.status ?? activity.status) === 'CANCELLED'
  const isHost = me && me.id === activity.hostId
  const mine = status?.status === 'JOINED' ? status.partySize : 0
  const startsAt = new Date(activity.startsAt).getTime()
  const checkinOpen = now >= startsAt - 30 * 60000 && now <= startsAt + 15 * 60000

  async function act(fn, success) {
    setBusy(true)
    setError(null)
    setMessage(null)
    try {
      const result = await fn()
      setMessage(success(result))
      reloadStatus()
    } catch (err) {
      setError(err.message)
    } finally {
      setBusy(false)
    }
  }

  function join() {
    joinKey.current ??= crypto.randomUUID()
    act(
      () => api(`/activities/${activity.id}/join`, { method: 'POST', body: { partySize: party }, headers: { 'Idempotency-Key': joinKey.current } }),
      (r) => {
        joinKey.current = null
        return r.status === 'JOINED' ? "You're in. See you there." : `Added to the waitlist at #${r.waitlistPosition}.`
      },
    )
  }

  const leave = () => act(() => api(`/activities/${activity.id}/participants/me`, { method: 'DELETE' }), () => 'Done. Your seat went back to the pool.')
  const claim = () => act(() => api(`/waitlist/offers/${status.offerId}/claim`, { method: 'POST' }), () => "Claimed. You're in.")
  const decline = () => act(() => api(`/waitlist/offers/${status.offerId}/decline`, { method: 'POST' }), () => 'Offer passed to the next person.')
  const confirm = () => act(() => api(`/activities/${activity.id}/confirm`, { method: 'POST' }), () => 'Thanks, the host knows you are coming.')

  let action
  if (cancelled) {
    action = <p className="panel__state">This activity was cancelled by the host.</p>
  } else if (!me) {
    action = (
      <button className="button button--wide" onClick={() => navigate('/login', { state: { from: location.pathname } })}>
        Sign in to join
      </button>
    )
  } else if (isHost) {
    action = <p className="panel__state">You are hosting this one.</p>
  } else if (!status) {
    action = <div className="skeleton-line" aria-hidden="true" />
  } else if (status.status === 'JOINED') {
    action = (
      <>
        <p className="panel__state panel__state--good">
          You&apos;re in{status.partySize > 1 ? `, party of ${status.partySize}` : ''}.
          {status.attendanceStatus === 'RECONFIRMED' && ' Confirmed.'}
          {status.attendanceStatus === 'CHECKED_IN' && ' Checked in.'}
        </p>
        {checkinOpen && status.attendanceStatus !== 'CHECKED_IN' && (
          <CheckIn activityId={activity.id} code={params.get('code')} onDone={() => { setMessage('Checked in. Have fun.'); reloadStatus() }} />
        )}
        <div className="panel__row">
          {status.attendanceStatus === 'RSVP' && now < startsAt && (
            <button className="button button--quiet" onClick={confirm} disabled={busy}>Confirm I&apos;m coming</button>
          )}
          {now < startsAt && (
            <button className="button button--quiet button--danger" onClick={leave} disabled={busy}>Leave</button>
          )}
        </div>
      </>
    )
  } else if (status.status === 'WAITLISTED') {
    action = (
      <>
        <p className="panel__state">You&apos;re #{status.waitlistPosition} in line.</p>
        <p className="muted small">If a seat frees up it is held for you for 15 minutes.</p>
        <button className="button button--quiet button--danger" onClick={leave} disabled={busy}>Leave the waitlist</button>
      </>
    )
  } else if (status.status === 'OFFERED') {
    const msLeft = new Date(status.claimDeadline).getTime() - now
    action = (
      <>
        <p className="panel__state panel__state--good">
          {status.partySize > 1 ? `${status.partySize} seats are` : 'A seat is'} held for you.
        </p>
        <p className="countdown" aria-live="off">{countdown(msLeft)} <span className="muted small">left to claim</span></p>
        <div className="panel__row">
          <button className="button" onClick={claim} disabled={busy || msLeft <= 0}>Claim</button>
          <button className="button button--quiet" onClick={decline} disabled={busy}>Pass</button>
        </div>
      </>
    )
  } else {
    action = (
      <>
        <PartyStepper value={party} onChange={setParty} />
        <button className="button button--wide" onClick={join} disabled={busy}>
          {busy ? 'Joining…' : left >= party ? 'Join' : 'Join the waitlist'}
        </button>
        {left < party && left > 0 && <p className="muted small">Only {left} left, so your group of {party} waits for the next free seats together.</p>}
      </>
    )
  }

  return (
    <section className="panel" aria-labelledby="seats-heading">
      <h2 id="seats-heading" className="visually-hidden">Seats</h2>
      <SeatRing capacity={capacity} taken={taken} mine={mine} size={196}>
        {left > 0 ? (
          <>
            <span className="ring__number">{left}</span>
            <span className="ring__label">of {capacity} left</span>
          </>
        ) : (
          <>
            <span className="ring__number ring__number--full">Full</span>
            <span className="ring__label">waitlist open</span>
          </>
        )}
      </SeatRing>
      <p className={live ? 'live live--on' : 'live'}>{live ? 'Updating live' : 'Connecting…'}</p>
      {action}
      {message && <p className="note note--good" role="status">{message}</p>}
      {error && <p className="note note--error" role="alert">{error}</p>}
    </section>
  )
}

export default function ActivityPage() {
  const { id } = useParams()
  const { user } = useAuth()
  const { data: activity, error, loading, reload } = useApi(`/activities/${id}`)
  const { seats, live } = useLiveSeats(id)
  const { data: status, reload: reloadStatus } = useApi(user ? `/activities/${id}/participants/me` : null)

  // the pushed numbers are newer than the page we loaded
  useEffect(() => {
    if (seats?.status === 'CANCELLED') reload()
  }, [seats?.status, reload])

  if (error) {
    return (
      <main className="page">
        <p className="empty">{error.status === 404 ? 'This activity does not exist any more.' : error.message}</p>
        <Link to="/" className="button button--quiet">Back to the map</Link>
      </main>
    )
  }
  if (loading && !activity) return <main className="page"><div className="skeleton-block" aria-busy="true" /></main>

  const isHost = user && user.id === activity.hostId
  const minutes = Math.round((new Date(activity.endsAt) - new Date(activity.startsAt)) / 60000)

  return (
    <main className="page activity">
      <div className="activity__main">
        <Link to="/" className="back"><ArrowLeft size={16} aria-hidden="true" /> All activities</Link>
        <p className="activity__meta">
          {CATEGORIES[activity.category]} · {when(activity.startsAt)} · {minutes >= 60 ? `${(minutes / 60).toFixed(minutes % 60 ? 1 : 0)} h` : `${minutes} min`}
        </p>
        <h1>{activity.title}</h1>
        {activity.description && <p className="activity__description">{activity.description}</p>}
        {activity.minReliability > 0 && (
          <p className="activity__rule">
            <ShieldCheck size={16} aria-hidden="true" /> Open to people with a reliability score of {activity.minReliability} or more.
          </p>
        )}

        <div className="activity__place">
          <ActivityMap
            className="mini-map"
            center={[activity.lat, activity.lng]}
            zoom={15}
            pins={[{ id: activity.id, lat: activity.lat, lng: activity.lng, label: '', variant: 'selected', title: activity.title }]}
          />
          <a className="link" href={`https://www.google.com/maps/search/?api=1&query=${activity.lat},${activity.lng}`} target="_blank" rel="noreferrer">
            <MapPin size={16} aria-hidden="true" /> Directions
          </a>
        </div>

        {isHost && <HostTools activity={activity} seats={seats} onChange={reload} />}
      </div>

      <SeatPanel activity={activity} seats={seats} live={live} me={user} status={status} reloadStatus={reloadStatus} />
    </main>
  )
}
