import { useState } from 'react'
import { useTheme } from './lib/useTheme'
import Login from './views/Login'
import Playground from './views/Playground'

export default function App() {
  useTheme()
  const [loggedIn, setLoggedIn] = useState(
    () => !!localStorage.getItem('x-admin-token'),
  )

  if (!loggedIn) return <Login onLogin={() => setLoggedIn(true)} />

  const nickname = localStorage.getItem('admin-nickname') || 'Admin'
  return (
    <Playground
      nickname={nickname}
      onLogout={() => {
        localStorage.removeItem('x-admin-token')
        localStorage.removeItem('admin-nickname')
        setLoggedIn(false)
      }}
    />
  )
}
