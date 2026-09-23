import { Bell } from 'lucide-react'
import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { api } from '../api'
import { useAuth } from '../auth-context'
import { CATEGORIES, countdown, seatsText, when } from '../format'
import { useApi, useNow } from '../hooks'

const ATTENDANCE = {
  RSVP: 'Going',
  RECONFIRMED: 'Confirmed',
  CHECKED_IN: 'Checked in',
  ATTENDED: 'Attended',
  NO_SHOW: 'Marked no-show',
  CANCELLED: 'Left',
}

function Offers() {
  const now = useNow(1000)
  const { data: offers, reload } = useApi('/me/offers')
  const [titles, setTitles] = useState({})
  const [error, setError] = useState(null)

  useEffect(() => {
    if (!offers?.length) return
    Promise.all(offers.map((o) => api(`/activities/${o.activityId}`).catch(() => null))).then((list) =>
      setTitles(Object.fromEntries(list.filter(Boolean).map((a) => [a.id, a.title]))),
    )
  }, [offers])

  if (!offers?.length) return null

  async function answer(offer, verb) {
    setError(null)
    try {
      await api(`/waitlist/offers/${offer.offerId}/${verb}`, { method: 'POST' })
      reload()
    } catch (err) {
      setError(err.message)
    }
  }

  return (
    <section className="section section--offer" aria-labelledby="offers-heading">
      <h2 id="offers-heading">A seat opened up</h2>
      <ul className="list">
        {offers.map((o) => {
          const left = new Date(o.claimDeadline).getTime() - now
          return (
            <li key={o.offerId} className="list__item">
              <div>
                <Link to={`/activities/${o.activityId}`} className="list__title">{titles[o.activityId] ?? 'Activity'}</Link>
                <p className="muted small">
                  {o.partySize > 1 ? `${o.partySize} seats` : '1 seat'} held for you · <span className="countdown countdown--inline">{countdown(left)}</span> left
                </p>
              </div>
              <div className="panel__row">
                <button className="button button--small" onClick={() => answer(o, 'claim')} disabled={left <= 0}>Claim</button>
                <button className="button button--small button--quiet" onClick={() => answer(o, 'decline')}>Pass</button>
              </div>
            </li>
          )
        })}
      </ul>
      {error && <p className="note note--error" role="alert">{error}</p>}
    </section>
  )
}

function Appeal({ participantId }) {
  const [open, setOpen] = useState(false)
  const [reason, setReason] = useState('')
  const [state, setState] = useState(null)

  async function send(e) {
    e.preventDefault()
    try {
      await api(`/me/attendance/${participantId}/appeal`, { method: 'POST', body: { reason } })
      setState({ ok: 'Sent. The host has until the window closes to decide; your score waits until then.' })
    } catch (err) {
      setState({ error: err.message })
    }
  }

  if (state?.ok) return <p className="note note--good small">{state.ok}</p>
  if (!open) return <button className="button button--quiet button--small" onClick={() => setOpen(true)}>Appeal</button>
  return (
    <form className="appeal" onSubmit={send}>
      <label className="field">
        <span>What happened?</span>
        <textarea value={reason} onChange={(e) => setReason(e.target.value)} maxLength={500} rows={3} required />
      </label>
      <div className="panel__row">
        <button className="button button--small" disabled={!reason.trim()}>Send appeal</button>
        <button type="button" className="button button--small button--quiet" onClick={() => setOpen(false)}>Never mind</button>
      </div>
      {state?.error && <p className="note note--error" role="alert">{state.error}</p>}
    </form>
  )
}

function MyActivities() {
  const [when_, setWhen] = useState('upcoming')
  const [cursor, setCursor] = useState(null)
  const [earlier, setEarlier] = useState([])
  const { data, loading, error } = useApi(`/me/activities?when=${when_}&size=10${cursor ? `&cursor=${encodeURIComponent(cursor)}` : ''}`)
  // while the next page loads, data still holds the previous one
  const pages = [...earlier, ...(loading ? [] : data?.items ?? [])]

  // keyset paging: the next page starts after the last row we already have
  function more() {
    setEarlier(pages)
    setCursor(data.nextCursor)
  }

  function switchTo(next) {
    setWhen(next)
    setCursor(null)
    setEarlier([])
  }

  return (
    <section className="section" aria-labelledby="mine-heading">
      <div className="section__head">
        <h2 id="mine-heading">Your bookings</h2>
        <div className="tabs" role="tablist">
          <button role="tab" aria-selected={when_ === 'upcoming'} className={when_ === 'upcoming' ? 'tab tab--on' : 'tab'} onClick={() => switchTo('upcoming')}>Upcoming</button>
          <button role="tab" aria-selected={when_ === 'past'} className={when_ === 'past' ? 'tab tab--on' : 'tab'} onClick={() => switchTo('past')}>Past</button>
        </div>
      </div>
      {error && <p className="note note--error">{error.message}</p>}
      {loading && pages.length === 0 ? (
        <div className="skeleton-line" aria-hidden="true" />
      ) : pages.length === 0 ? (
        <div className="empty">
          <p>{when_ === 'upcoming' ? 'Nothing booked yet.' : 'Nothing here yet.'}</p>
          {when_ === 'upcoming' && <Link to="/" className="button button--small">Find something nearby</Link>}
        </div>
      ) : (
        <ul className="list">
          {pages.map((a) => (
            <li key={a.participantId} className="list__item">
              <div>
                <Link to={`/activities/${a.activityId}`} className="list__title">{a.title}</Link>
                <p className="muted small">
                  {CATEGORIES[a.category]} · {when(a.startsAt)}
                  {a.partySize > 1 && <> · party of {a.partySize}</>}
                </p>
              </div>
              <div className="list__end">
                <span className={a.attendanceStatus === 'NO_SHOW' ? 'status status--bad' : 'status'}>
                  {a.activityStatus === 'CANCELLED' ? 'Cancelled' : ATTENDANCE[a.attendanceStatus] ?? a.attendanceStatus}
                </span>
                {a.attendanceStatus === 'NO_SHOW' && <Appeal participantId={a.participantId} />}
              </div>
            </li>
          ))}
        </ul>
      )}
      {data?.nextCursor && (
        <button className="button button--quiet button--small" onClick={more} disabled={loading}>
          Show more
        </button>
      )}
    </section>
  )
}

function Hosting() {
  const { data } = useApi('/me/hosting')
  if (!data) return null
  return (
    <section className="section" aria-labelledby="hosting-heading">
      <h2 id="hosting-heading">You are hosting</h2>
      {data.length === 0 ? (
        <div className="empty">
          <p>No activities yet.</p>
          <Link to="/host/new" className="button button--small">Host one</Link>
        </div>
      ) : (
        <ul className="list">
          {data.map((a) => (
            <li key={a.id} className="list__item">
              <div>
                <Link to={`/activities/${a.id}`} className="list__title">{a.title}</Link>
                <p className="muted small">{CATEGORIES[a.category]} · {when(a.startsAt)}</p>
              </div>
              <span className="status">{seatsText(a.seatsLeft, a.capacity)}</span>
            </li>
          ))}
        </ul>
      )}
    </section>
  )
}

function Notifications() {
  const { data, reload } = useApi('/me/notifications')
  if (!data) return null
  const unread = data.filter((n) => !n.readAt).length

  async function read(n) {
    if (n.readAt) return
    await api(`/me/notifications/${n.id}/read`, { method: 'POST' }).catch(() => {})
    reload()
  }

  return (
    <section className="section" aria-labelledby="inbox-heading">
      <h2 id="inbox-heading">
        <Bell size={18} aria-hidden="true" /> Updates {unread > 0 && <span className="count">{unread} new</span>}
      </h2>
      {data.length === 0 ? (
        <p className="muted">Joins, waitlist offers and reminders show up here.</p>
      ) : (
        <ul className="inbox">
          {data.slice(0, 20).map((n) => (
            <li key={n.id} className={n.readAt ? 'inbox__item' : 'inbox__item inbox__item--unread'}>
              <Link to={n.activityId ? `/activities/${n.activityId}` : '#'} onClick={() => read(n)}>
                <span>{n.message}</span>
                <time className="muted small" dateTime={n.createdAt}>{when(n.createdAt)}</time>
              </Link>
            </li>
          ))}
        </ul>
      )}
    </section>
  )
}

function Reliability() {
  const { data } = useApi('/me/reliability')
  if (!data) return null
  return (
    <section className="section reliability" aria-labelledby="score-heading">
      <h2 id="score-heading">Reliability</h2>
      <p className="reliability__score"><span>{data.score}</span>/100</p>
      <p className="muted small">
        {data.committed === 0
          ? 'Everyone starts at 50. It moves once you finish your first activity.'
          : `${data.checkedIn} of ${data.committed} turned up${data.noShows > 0 ? `, ${data.noShows} no-show${data.noShows > 1 ? 's' : ''}` : ''}.`}{' '}
        Some hosts only take people above a score, and a no-show only counts once the 48 hour appeal window has closed.
      </p>
    </section>
  )
}

export default function Bookings() {
  const { user } = useAuth()
  return (
    <main className="page bookings">
      <div className="bookings__main">
        <h1>Hi {user.name.split(' ')[0]}</h1>
        <Offers />
        <MyActivities />
        {user.role === 'HOST' && <Hosting />}
      </div>
      <aside className="bookings__side">
        <Reliability />
        <Notifications />
      </aside>
    </main>
  )
}
