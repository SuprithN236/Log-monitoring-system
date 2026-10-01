import { AuthScreen } from './components/AuthScreen.tsx';
import { Dashboard } from './components/Dashboard.tsx';
import { AuthProvider, useAuth } from './context/AuthContext.tsx';

function AuthGate() {
  const { status } = useAuth();

  if (status === 'checking') {
    return (
      <div className="flex min-h-screen items-center justify-center" aria-busy="true">
        <div className="h-8 w-8 animate-spin rounded-full border-2 border-slate-700 border-t-blue-500" />
      </div>
    );
  }
  return status === 'signed-in' ? <Dashboard /> : <AuthScreen />;
}

export default function App() {
  return (
    <AuthProvider>
      <AuthGate />
    </AuthProvider>
  );
}
