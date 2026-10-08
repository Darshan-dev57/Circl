// Small fetch wrapper. The access token lives only in memory; the refresh token is an
// HttpOnly cookie that the browser sends to /api/v1/auth by itself.

let accessToken = null
let refreshing = null
let onSessionLost = () => {}

export class ApiError extends Error {
  constructor(status, problem) {
    // validation problems list each field, which is more useful than "Request has invalid fields"
    const fields = problem?.errors?.map((e) => `${e.field} ${e.message}`).join(', ')
    super(fields || problem?.detail || problem?.title || `Request failed (${status})`)
    this.status = status
    this.problem = problem
  }
}

export function setAccessToken(token) {
  accessToken = token
}

export function whenSessionLost(callback) {
  onSessionLost = callback
}

// parallel requests that all get a 401 share one refresh call
export function refreshSession() {
  if (!refreshing) {
    refreshing = fetch('/api/v1/auth/refresh', { method: 'POST' })
      .then(async (res) => {
        if (!res.ok) {
          accessToken = null
          return null
        }
        const tokens = await res.json()
        accessToken = tokens.accessToken
        return tokens
      })
      .catch(() => null)
      .finally(() => {
        refreshing = null
      })
  }
  return refreshing
}

export async function api(path, { method = 'GET', body, headers = {}, signal, retry = true } = {}) {
  const res = await fetch(`/api/v1${path}`, {
    method,
    signal,
    headers: {
      ...(body !== undefined && { 'Content-Type': 'application/json' }),
      ...(accessToken && { Authorization: `Bearer ${accessToken}` }),
      ...headers,
    },
    body: body !== undefined ? JSON.stringify(body) : undefined,
  })

  if (res.status === 401 && retry && accessToken && !path.startsWith('/auth/')) {
    if (await refreshSession()) {
      return api(path, { method, body, headers, signal, retry: false })
    }
    onSessionLost()
  }
  if (!res.ok) {
    let problem = null
    try {
      problem = await res.json()
    } catch {
      // not json, keep the status
    }
    throw new ApiError(res.status, problem)
  }
  return res.status === 204 ? null : res.json()
}
