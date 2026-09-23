import { useCallback, useEffect, useMemo, useState } from 'react'
import { api, refreshSession, setAccessToken, whenSessionLost } from './api'
import { AuthContext } from './auth-context'

export function AuthProvider({ children }) {
  const [user, setUser] = useState(null)
  const [ready, setReady] = useState(false)

  useEffect(() => {
    whenSessionLost(() => setUser(null))
    // a refresh cookie from last time logs you straight back in
    refreshSession().then((tokens) => {
      setUser(tokens?.user ?? null)
      setReady(true)
    })
  }, [])

  const start = useCallback((tokens) => {
    setAccessToken(tokens.accessToken)
    setUser(tokens.user)
    return tokens.user
  }, [])

  const login = useCallback(
    (email, password) => api('/auth/login', { method: 'POST', body: { email, password } }).then(start),
    [start],
  )

  const signup = useCallback(
    (form) => api('/auth/signup', { method: 'POST', body: form }).then(start),
    [start],
  )

  const logout = useCallback(async () => {
    try {
      await api('/auth/logout', { method: 'POST' })
    } finally {
      setAccessToken(null)
      setUser(null)
    }
  }, [])

  const reloadUser = useCallback(() => api('/me').then(setUser), [])

  const value = useMemo(
    () => ({ user, ready, login, signup, logout, reloadUser }),
    [user, ready, login, signup, logout, reloadUser],
  )
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}
