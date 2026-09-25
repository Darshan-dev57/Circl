import { useCallback, useEffect, useState } from 'react'
import { api } from './api'

/** GET a path and keep the result. Pass null as the path to skip the request. */
export function useApi(path) {
  const [version, setVersion] = useState(0)
  const key = path === null ? null : `${path}#${version}`
  // the result remembers which request it answers, so "loading" is simply "not answered yet"
  const [result, setResult] = useState({ key: null, data: null, error: null })

  useEffect(() => {
    if (key === null) return
    const controller = new AbortController()
    api(path, { signal: controller.signal })
      .then((data) => setResult({ key, data, error: null }))
      .catch((error) => {
        if (error.name !== 'AbortError') setResult((r) => ({ key, data: r.data, error }))
      })
    return () => controller.abort()
  }, [key, path])

  const reload = useCallback(() => setVersion((v) => v + 1), [])
  return { data: result.data, error: result.error, loading: key !== null && result.key !== key, reload }
}

/**
 * Seat numbers pushed by the server (Server-Sent Events). EventSource reconnects on its own;
 * if the stream is refused we fall back to asking every 15 seconds.
 */
export function useLiveSeats(activityId) {
  const [seats, setSeats] = useState(null)
  const [live, setLive] = useState(false)

  useEffect(() => {
    if (!activityId) return
    let poll = null
    const source = new EventSource(`/api/v1/activities/${activityId}/seats`)
    source.addEventListener('seats', (e) => {
      setSeats(JSON.parse(e.data))
      setLive(true)
    })
    source.onerror = () => {
      setLive(false)
      if (source.readyState === EventSource.CLOSED && !poll) {
        const load = () => api(`/activities/${activityId}`).then(setSeats).catch(() => {})
        poll = setInterval(load, 15000)
      }
    }
    return () => {
      source.close()
      clearInterval(poll)
    }
  }, [activityId])

  return { seats, live }
}

/** Re-renders every `ms` so countdowns stay correct. */
export function useNow(ms = 1000) {
  const [now, setNow] = useState(() => Date.now())
  useEffect(() => {
    const id = setInterval(() => setNow(Date.now()), ms)
    return () => clearInterval(id)
  }, [ms])
  return now
}

export function useDebounced(value, ms) {
  const [debounced, setDebounced] = useState(value)
  useEffect(() => {
    const id = setTimeout(() => setDebounced(value), ms)
    return () => clearTimeout(id)
  }, [value, ms])
  return debounced
}
