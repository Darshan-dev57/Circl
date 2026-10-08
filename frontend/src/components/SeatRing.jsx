/**
 * Seats drawn as dots around a circle: filled = taken, hollow = free, accent = yours.
 * Small rings (list rows, pins) draw a simple arc instead because 50 dots would blur together.
 */
export default function SeatRing({ capacity, taken, mine = 0, size = 180, children }) {
  const c = size / 2
  const free = Math.max(0, capacity - taken)

  if (size < 64) {
    const r = c - 3
    const length = 2 * Math.PI * r
    const share = capacity ? Math.min(1, taken / capacity) : 0
    return (
      <svg className="ring ring--small" width={size} height={size} viewBox={`0 0 ${size} ${size}`} aria-hidden="true">
        <circle cx={c} cy={c} r={r} className="ring__track" />
        <circle
          cx={c}
          cy={c}
          r={r}
          className={free === 0 ? 'ring__fill ring__fill--full' : 'ring__fill'}
          strokeDasharray={`${share * length} ${length}`}
          transform={`rotate(-90 ${c} ${c})`}
        />
      </svg>
    )
  }

  const r = c - 10
  const dot = Math.max(3, Math.min(11, (Math.PI * r) / capacity - 1.5))
  const seats = Array.from({ length: capacity }, (_, i) => {
    const angle = (i / capacity) * 2 * Math.PI - Math.PI / 2
    const state = i < taken - mine ? 'taken' : i < taken ? 'mine' : 'free'
    return { x: c + r * Math.cos(angle), y: c + r * Math.sin(angle), state }
  })

  return (
    <div className="ring" style={{ width: size, height: size }}>
      <svg width={size} height={size} viewBox={`0 0 ${size} ${size}`} role="img"
        aria-label={`${taken} of ${capacity} seats taken`}>
        <circle cx={c} cy={c} r={r} className="ring__track ring__track--thin" />
        {seats.map((s, i) => (
          <circle key={i} cx={s.x} cy={s.y} r={dot} className={`seat seat--${s.state}`} />
        ))}
      </svg>
      <div className="ring__center">{children}</div>
    </div>
  )
}
