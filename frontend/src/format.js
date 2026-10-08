export const CATEGORIES = {
  CRICKET: 'Cricket',
  BADMINTON: 'Badminton',
  FOOTBALL: 'Football',
  COFFEE: 'Coffee',
  TREK: 'Trek',
  STUDY: 'Study',
}

const time = new Intl.DateTimeFormat('en-IN', { hour: 'numeric', minute: '2-digit' })
const day = new Intl.DateTimeFormat('en-IN', { weekday: 'short', day: 'numeric', month: 'short' })

function startOfDay(d) {
  return new Date(d.getFullYear(), d.getMonth(), d.getDate()).getTime()
}

/** "Today, 6:30 pm", "Tomorrow, 7:00 am", "Sat 12 Oct, 6:30 pm" */
export function when(iso) {
  const d = new Date(iso)
  const days = Math.round((startOfDay(d) - startOfDay(new Date())) / 86400000)
  const label = days === 0 ? 'Today' : days === 1 ? 'Tomorrow' : day.format(d)
  return `${label}, ${time.format(d)}`
}

export function distance(m) {
  if (m == null) return null
  return m < 1000 ? `${Math.round(m / 10) * 10} m` : `${(m / 1000).toFixed(1)} km`
}

export function countdown(ms) {
  if (ms <= 0) return '0:00'
  const s = Math.floor(ms / 1000)
  return `${Math.floor(s / 60)}:${String(s % 60).padStart(2, '0')}`
}

export function seatsText(left, capacity) {
  if (left <= 0) return 'Full'
  if (left === capacity) return `${capacity} seats`
  return `${left} of ${capacity} left`
}
