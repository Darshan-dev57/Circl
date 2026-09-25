import { useState } from 'react'
import { Link, Navigate, useLocation, useNavigate } from 'react-router-dom'
import { useAuth } from '../auth-context'

export default function AuthPage({ mode }) {
  const { user, login, signup } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const [form, setForm] = useState({ name: '', email: '', password: '', host: false })
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState(null)
  const isSignup = mode === 'signup'
  const goTo = location.state?.from ?? '/'

  if (user) return <Navigate to={goTo} replace />

  const set = (key) => (e) => setForm((f) => ({ ...f, [key]: e.target.type === 'checkbox' ? e.target.checked : e.target.value }))

  async function submit(e) {
    e.preventDefault()
    setBusy(true)
    setError(null)
    try {
      if (isSignup) {
        await signup({ name: form.name, email: form.email, password: form.password, role: form.host ? 'HOST' : 'PARTICIPANT' })
      } else {
        await login(form.email, form.password)
      }
      navigate(goTo, { replace: true })
    } catch (err) {
      setError(err.message)
    } finally {
      setBusy(false)
    }
  }

  return (
    <main className="page auth">
      <form className="auth__form" onSubmit={submit} noValidate={false}>
        <h1>{isSignup ? 'Make an account' : 'Welcome back'}</h1>
        <p className="muted">
          {isSignup ? 'Join games and plans near you. Takes ten seconds.' : 'Sign in to join activities and see your bookings.'}
        </p>

        {isSignup && (
          <label className="field">
            <span>Name</span>
            <input value={form.name} onChange={set('name')} autoComplete="name" maxLength={60} required />
          </label>
        )}
        <label className="field">
          <span>Email</span>
          <input type="email" value={form.email} onChange={set('email')} autoComplete="email" required />
        </label>
        <label className="field">
          <span>Password</span>
          <input
            type="password"
            value={form.password}
            onChange={set('password')}
            autoComplete={isSignup ? 'new-password' : 'current-password'}
            minLength={isSignup ? 8 : undefined}
            required
          />
          {isSignup && <small className="muted">At least 8 characters.</small>}
        </label>
        {isSignup && (
          <label className="check">
            <input type="checkbox" checked={form.host} onChange={set('host')} />
            <span>I also want to host activities</span>
          </label>
        )}

        {error && <p className="note note--error" role="alert">{error}</p>}
        <button className="button button--wide" disabled={busy}>
          {busy ? 'One moment…' : isSignup ? 'Create account' : 'Sign in'}
        </button>
        <p className="muted small">
          {isSignup ? (
            <>Already have an account? <Link to="/login" state={location.state}>Sign in</Link></>
          ) : (
            <>New here? <Link to="/signup" state={location.state}>Make an account</Link></>
          )}
        </p>
      </form>
    </main>
  )
}
