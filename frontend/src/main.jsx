import '@fontsource-variable/instrument-sans'
import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom'
import { AuthProvider } from './auth'
import RequireUser from './components/RequireUser'
import Shell from './components/Shell'
import ActivityPage from './pages/ActivityPage'
import AuthPage from './pages/AuthPage'
import Bookings from './pages/Bookings'
import Explore from './pages/Explore'
import NewActivity from './pages/NewActivity'
import './styles.css'

createRoot(document.getElementById('root')).render(
  <StrictMode>
    <BrowserRouter>
      <AuthProvider>
        <Routes>
          <Route element={<Shell />}>
            <Route index element={<Explore />} />
            <Route path="activities/:id" element={<ActivityPage />} />
            <Route path="login" element={<AuthPage mode="login" />} />
            <Route path="signup" element={<AuthPage mode="signup" />} />
            <Route path="bookings" element={<RequireUser><Bookings /></RequireUser>} />
            <Route path="host/new" element={<RequireUser role="HOST"><NewActivity /></RequireUser>} />
            <Route path="*" element={<Navigate to="/" replace />} />
          </Route>
        </Routes>
      </AuthProvider>
    </BrowserRouter>
  </StrictMode>,
)
