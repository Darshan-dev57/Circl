import QRCode from 'qrcode'
import { RefreshCw } from 'lucide-react'
import { useCallback, useEffect, useRef, useState } from 'react'
import { api } from '../api'
import { countdown } from '../format'
import { useApi, useNow } from '../hooks'

const ATTENDANCE = {
  RSVP: 'Going',
  RECONFIRMED: 'Confirmed',
  CHECKED_IN: 'Checked in',
  ATTENDED: 'Attended',
  NO_SHOW: 'No-show',
  CANCELLED: 'Left',
}

function CheckinCode({ activityId }) {
  const now = useNow(1000)
  const [code, setCode] = useState(null)
  const [qr, setQr] = useState(null)
  const [error, setError] = useState(null)
  const loading = useRef(false)

  const load = useCallback(async () => {
    if (loading.current) return
    loading.current = true
    setError(null)
    try {
      const issued = await api(`/activities/${activityId}/checkin-code`)
      // the QR opens this activity with the code filled in, so a phone camera is enough to scan it
      const link = `${window.location.origin}/activities/${activityId}?code=${encodeURIComponent(issued.code)}`
      setQr(await QRCode.toDataURL(link, { margin: 1, width: 220, color: { dark: '#1c1a17', light: '#fffdf9' } }))
      setCode(issued)
    } catch (err) {
      setError(err.message)
    } finally {
      loading.current = false
    }
  }, [activityId])

  // fetch a fresh one as soon as the 60 second code runs out
  useEffect(() => {
    if (!code) return
    const id = setTimeout(load, Math.max(0, new Date(code.expiresAt).getTime() - Date.now()))
    return () => clearTimeout(id)
  }, [code, load])

  const msLeft = code ? new Date(code.expiresAt).getTime() - now : 0

  if (!code) {
    return (
      <div className="host__block">
        <h3>Check-in</h3>
        <p className="muted small">Opens 30 minutes before the start. The code changes every minute, so a screenshot is no use later.</p>
        <button className="button button--quiet button--small" onClick={load}>Show check-in code</button>
        {error && <p className="note note--error" role="alert">{error}</p>}
      </div>
    )
  }
  return (
    <div className="host__block host__qr">
      <h3>Check-in</h3>
      <img src={qr} width="220" height="220" alt="Check-in QR code" />
      <p className="muted small">
        New code in {countdown(msLeft)}
        <button className="icon-button" onClick={load} aria-label="New code now"><RefreshCw size={14} /></button>
      </p>
    </div>
  )
}

export default function HostTools({ activity, seats, onChange }) {
  const { data: people, reload } = useApi(`/activities/${activity.id}/participants`)
  const [capacity, setCapacity] = useState(activity.capacity)
  const [confirming, setConfirming] = useState(false)
  const [error, setError] = useState(null)
  const [saved, setSaved] = useState(false)
  const [reported, setReported] = useState(false)
  // checked every 30 s so the "check-in is down" button shows up once the activity starts
  const now = useNow(30000)
  const started = now >= new Date(activity.startsAt).getTime()
  const cancelled = (seats?.status ?? activity.status) === 'CANCELLED'

  // a new join shows up in the list without a page reload
  useEffect(() => {
    reload()
  }, [seats?.seatsTaken, reload])

  async function saveCapacity(e) {
    e.preventDefault()
    setError(null)
    setSaved(false)
    try {
      await api(`/activities/${activity.id}`, { method: 'PATCH', body: { capacity: Number(capacity) } })
      setSaved(true)
      onChange()
    } catch (err) {
      setError(err.message)
    }
  }

  async function reportCheckinDown() {
    setError(null)
    try {
      await api(`/activities/${activity.id}/attendance/unreliable`, { method: 'POST' })
      setReported(true)
    } catch (err) {
      setError(err.message)
    }
  }

  async function cancel() {
    setError(null)
    try {
      await api(`/activities/${activity.id}`, { method: 'DELETE' })
      onChange()
    } catch (err) {
      setError(err.message)
    }
  }

  return (
    <section className="host" aria-labelledby="host-heading">
      <h2 id="host-heading">Your activity</h2>

      <div className="host__block">
        <h3>Who is coming</h3>
        {!people ? (
          <div className="skeleton-line" aria-hidden="true" />
        ) : people.length === 0 ? (
          <p className="muted">Nobody yet. Share the link, people nearby will see it on the map.</p>
        ) : (
          <table className="table">
            <thead>
              <tr><th>Name</th><th>Group</th><th>Status</th></tr>
            </thead>
            <tbody>
              {people.map((p) => (
                <tr key={p.participantId}>
                  <td>{p.name}</td>
                  <td className="num">{p.partySize}</td>
                  <td>{ATTENDANCE[p.attendanceStatus] ?? p.attendanceStatus}{p.late ? ', late' : ''}</td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>

      {started && !cancelled && (
        <div className="host__block">
          <h3>Check-in trouble?</h3>
          {reported ? (
            <p className="note note--good small" role="status">Noted. Nobody gets a no-show for this one.</p>
          ) : (
            <>
              <p className="muted small">If the code would not load for people, report it and no one is marked as a no-show.</p>
              <button className="button button--quiet button--small" onClick={reportCheckinDown}>Check-in was not working</button>
            </>
          )}
        </div>
      )}

      {!cancelled && <CheckinCode activityId={activity.id} />}

      {!cancelled && !started && (
        <>

          <form className="host__block host__capacity" onSubmit={saveCapacity}>
            <h3>Seats</h3>
            <label className="inline-field">
              <span>Capacity</span>
              <input type="number" min={Math.max(2, activity.seatsTaken)} max="50" value={capacity} onChange={(e) => setCapacity(e.target.value)} />
            </label>
            <button className="button button--quiet button--small" disabled={Number(capacity) === activity.capacity}>Save</button>
            {saved && <span className="muted small" role="status">Saved. Extra seats go to the waitlist first.</span>}
          </form>

          <div className="host__block">
            {confirming ? (
              <div className="panel__row">
                <button className="button button--danger" onClick={cancel}>Yes, cancel it</button>
                <button className="button button--quiet" onClick={() => setConfirming(false)}>Keep it</button>
              </div>
            ) : (
              <button className="button button--quiet button--danger" onClick={() => setConfirming(true)}>Cancel activity</button>
            )}
            <p className="muted small">Everyone who joined gets a notification.</p>
          </div>
        </>
      )}
      {error && <p className="note note--error" role="alert">{error}</p>}
    </section>
  )
}
