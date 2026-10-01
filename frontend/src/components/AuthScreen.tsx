import { useState, type FormEvent } from 'react';
import { useAuth } from '../context/AuthContext.tsx';

type Mode = 'sign-in' | 'sign-up';

const MIN_PASSWORD = 8;
const MAX_PASSWORD = 72;

export function AuthScreen() {
  const { signIn, signUp } = useAuth();
  const [mode, setMode] = useState<Mode>('sign-in');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);

  const isSignUp = mode === 'sign-up';

  function switchMode(next: Mode) {
    setMode(next);
    setError(null);
    setPassword('');
    setConfirmPassword('');
  }

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    setError(null);
    if (isSignUp && password !== confirmPassword) {
      setError('Passwords do not match');
      return;
    }
    setIsSubmitting(true);
    try {
      if (isSignUp) await signUp(email.trim(), password);
      else await signIn(email.trim(), password);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Something went wrong. Please try again.');
      setIsSubmitting(false);
    }
  }

  const inputClass =
    'mt-1.5 block w-full rounded-lg border border-slate-700 bg-slate-950 px-3 py-2 text-sm text-slate-100 placeholder:text-slate-600 focus:border-blue-500 focus:outline-none focus:ring-2 focus:ring-blue-500/30';

  return (
    <div className="flex min-h-screen items-center justify-center px-4 py-12">
      <div className="w-full max-w-sm">
        <div className="mb-8 text-center">
          <div className="inline-flex items-center gap-2 text-xs font-medium uppercase tracking-widest text-emerald-400">
            <span className="h-2 w-2 rounded-full bg-emerald-500" />
            Log Monitoring
          </div>
          <h1 className="mt-2 text-2xl font-semibold text-slate-50">
            {isSignUp ? 'Create your account' : 'Sign in to the console'}
          </h1>
          <p className="mt-1 text-sm text-slate-400">
            {isSignUp ? 'Start watching your services in real time.' : 'Welcome back. Your logs are waiting.'}
          </p>
        </div>

        <div className="rounded-xl border border-slate-800 bg-slate-900/70 p-6 shadow-xl shadow-black/30">
          <div role="tablist" aria-label="Account" className="mb-6 grid grid-cols-2 rounded-lg bg-slate-950 p-1 text-sm">
            {(['sign-in', 'sign-up'] as const).map((tab) => (
              <button
                key={tab}
                type="button"
                role="tab"
                aria-selected={mode === tab}
                onClick={() => switchMode(tab)}
                className={`rounded-md px-3 py-1.5 font-medium transition-colors ${
                  mode === tab ? 'bg-slate-800 text-slate-100 shadow' : 'text-slate-400 hover:text-slate-200'
                }`}
              >
                {tab === 'sign-in' ? 'Sign in' : 'Create account'}
              </button>
            ))}
          </div>

          <form onSubmit={handleSubmit} className="space-y-4">
            <label className="block text-sm font-medium text-slate-300">
              Email
              <input
                type="email"
                required
                autoComplete="email"
                maxLength={254}
                value={email}
                onChange={(event) => setEmail(event.target.value)}
                placeholder="you@example.com"
                className={inputClass}
              />
            </label>

            <label className="block text-sm font-medium text-slate-300">
              Password
              <input
                type="password"
                required
                minLength={isSignUp ? MIN_PASSWORD : undefined}
                maxLength={MAX_PASSWORD}
                autoComplete={isSignUp ? 'new-password' : 'current-password'}
                value={password}
                onChange={(event) => setPassword(event.target.value)}
                className={inputClass}
              />
              {isSignUp && <span className="mt-1 block text-xs font-normal text-slate-500">At least {MIN_PASSWORD} characters.</span>}
            </label>

            {isSignUp && (
              <label className="block text-sm font-medium text-slate-300">
                Confirm password
                <input
                  type="password"
                  required
                  minLength={MIN_PASSWORD}
                  maxLength={MAX_PASSWORD}
                  autoComplete="new-password"
                  value={confirmPassword}
                  onChange={(event) => setConfirmPassword(event.target.value)}
                  className={inputClass}
                />
              </label>
            )}

            {error && (
              <p role="alert" className="rounded-lg border border-red-900/60 bg-red-950/40 px-3 py-2 text-sm text-red-300">
                {error}
              </p>
            )}

            <button
              type="submit"
              disabled={isSubmitting}
              className="flex w-full items-center justify-center gap-2 rounded-lg bg-blue-600 px-4 py-2.5 text-sm font-semibold text-white transition-colors hover:bg-blue-500 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-blue-400 disabled:cursor-not-allowed disabled:opacity-60"
            >
              {isSubmitting && (
                <svg className="h-4 w-4 animate-spin" viewBox="0 0 24 24" fill="none" aria-hidden="true">
                  <circle cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="3" className="opacity-25" />
                  <path d="M22 12a10 10 0 0 0-10-10" stroke="currentColor" strokeWidth="3" strokeLinecap="round" />
                </svg>
              )}
              {isSignUp ? 'Create account' : 'Sign in'}
            </button>
          </form>
        </div>

        <p className="mt-6 text-center text-sm text-slate-500">
          {isSignUp ? 'Already have an account?' : 'New here?'}{' '}
          <button
            type="button"
            onClick={() => switchMode(isSignUp ? 'sign-in' : 'sign-up')}
            className="font-medium text-blue-400 hover:text-blue-300"
          >
            {isSignUp ? 'Sign in' : 'Create an account'}
          </button>
        </p>
      </div>
    </div>
  );
}
