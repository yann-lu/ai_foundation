import { useState, type FormEvent } from 'react'
import { login } from '@/api/auth'
import { Button } from '@/components/ui/button'
import { Sparkles, Sun, Moon } from 'lucide-react'
import { useTheme } from '@/lib/useTheme'

export default function Login({ onLogin }: { onLogin: () => void }) {
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')
  const { theme, toggle } = useTheme()

  async function handleSubmit(e: FormEvent) {
    e.preventDefault()
    if (!username || !password) return
    setLoading(true)
    setError('')
    try {
      const res = await login(username, password)
      localStorage.setItem('x-admin-token', res.token)
      localStorage.setItem('admin-nickname', res.nickname)
      onLogin()
    } catch (err) {
      setError(err instanceof Error ? err.message : '登录失败')
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="bg-background relative flex h-dvh items-center justify-center overflow-hidden">
      <div
        className="pointer-events-none absolute inset-0 opacity-40"
        style={{
          background:
            'radial-gradient(ellipse 80% 50% at 50% -10%, var(--primary), transparent 70%)',
        }}
      />
      <button
        onClick={toggle}
        className="text-muted-foreground hover:text-foreground absolute top-4 right-4 z-10 rounded-lg p-2 transition-colors"
        title="切换主题"
      >
        {theme === 'dark' ? <Sun className="size-5" /> : <Moon className="size-5" />}
      </button>

      <form
        onSubmit={handleSubmit}
        className="bg-card/80 border-border relative w-full max-w-sm space-y-5 rounded-2xl border p-8 shadow-2xl backdrop-blur-xl"
      >
        <div className="flex flex-col items-center gap-3">
          <div className="bg-primary/10 flex size-14 items-center justify-center rounded-2xl">
            <Sparkles className="text-primary size-7" />
          </div>
          <div className="text-center">
            <h1 className="text-foreground text-xl font-semibold">
              AI Foundation
            </h1>
            <p className="text-muted-foreground text-sm">Playground 调试台</p>
          </div>
        </div>

        <div className="space-y-3">
          <input
            value={username}
            onChange={(e) => setUsername(e.target.value)}
            placeholder="用户名"
            autoComplete="username"
            className="border-input bg-background/50 text-foreground placeholder:text-muted-foreground h-11 w-full rounded-lg border px-3.5 text-sm outline-none transition-colors focus:border-primary focus:ring-2 focus:ring-primary/30"
          />
          <input
            type="password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            placeholder="密码"
            autoComplete="current-password"
            className="border-input bg-background/50 text-foreground placeholder:text-muted-foreground h-11 w-full rounded-lg border px-3.5 text-sm outline-none transition-colors focus:border-primary focus:ring-2 focus:ring-primary/30"
          />
        </div>

        {error && (
          <p className="text-destructive text-center text-sm">{error}</p>
        )}

        <Button type="submit" disabled={loading} className="h-11 w-full">
          {loading ? '登录中…' : '登录'}
        </Button>
      </form>
    </div>
  )
}
