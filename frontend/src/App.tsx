import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { useAuthStore } from './store/authStore';

import LoginPage from './pages/LoginPage';
import LandingPage from './pages/LandingPage';
import RegistroPage from './pages/registro/RegistroPage';
import ConfirmarRegistroPage from './pages/registro/ConfirmarRegistroPage';
import BemVindoPage from './pages/registro/BemVindoPage';
import AssinarPage from './pages/AssinarPage';
import EsqueciSenhaPage from './pages/EsqueciSenhaPage';
import RedefinirSenhaPage from './pages/RedefinirSenhaPage';
import SelecionarLojaPage from './pages/SelecionarLojaPage';
import AgendaPage from './pages/AgendaPage';
import RelatoriosPage from './pages/RelatoriosPage';
import PrivacyPage from './pages/PrivacyPage';
import TermosPage from './pages/TermosPage';
import ConfiguracoesPage from './pages/ConfiguracoesPage';
import EmpresasPage from './pages/EmpresasPage';
import Layout from './components/Layout';
import { ToastContainer } from './components/ToastContainer';

const queryClient = new QueryClient({
  defaultOptions: {
    queries: { staleTime: 1000 * 60 * 5, retry: 1 },
  }
});

function LoadingScreen() {
  return (
      <div style={{
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        height: '100vh',
        width: '100vw',
        backgroundColor: '#1e3a8a',
      }}>
        <div style={{
          width: '40px',
          height: '40px',
          border: '4px solid rgba(255,255,255,0.3)',
          borderTopColor: '#fff',
          borderRadius: '50%',
          animation: 'spin 0.8s linear infinite',
        }} />
        <style>{`
        @keyframes spin {
          to { transform: rotate(360deg); }
        }
      `}</style>
      </div>
  );
}

/**
 * @param ignorarOnboarding rota que PODE ser vista com o onboarding pendente —
 *        só o próprio wizard, senão o desvio abaixo vira laço.
 */
function ProtectedRoute({ children, ignorarOnboarding }:
                        { children: React.ReactNode; ignorarOnboarding?: boolean }) {
  const usuario = useAuthStore((s) => s.usuario);
  const hasHydrated = useAuthStore((s) => s.hasHydrated);

  if (!hasHydrated) {
    return <LoadingScreen />;
  }

  if (!usuario) return <Navigate to="/login" replace />;

  // Quem parou no meio do wizard volta para ele em qualquer entrada no app —
  // é o que faz o onboarding retomar de onde parou. O teste de campo
  // preenchido cobre a sessão salva ANTES deste campo existir: sem ele, um
  // usuário antigo com localStorage velho ficaria preso indo e voltando.
  if (!ignorarOnboarding && usuario.onboardingEtapa && usuario.onboardingEtapa !== 'CONCLUIDO') {
    return <Navigate to="/bem-vindo" replace />;
  }

  return <>{children}</>;
}

export default function App() {
  return (
      <QueryClientProvider client={queryClient}>
        <BrowserRouter>
          <ToastContainer />
          <Routes>
            <Route path="/comecar" element={<LandingPage />} />
            <Route path="/login" element={<LoginPage />} />
            <Route path="/registro" element={<RegistroPage />} />
            <Route path="/registro/confirmar" element={<ConfirmarRegistroPage />} />
            <Route path="/esqueci-senha" element={<EsqueciSenhaPage />} />
            <Route path="/redefinir-senha" element={<RedefinirSenhaPage />} />
            <Route path="/privacidade" element={<PrivacyPage />} />
            <Route path="/termos" element={<TermosPage />} />

            <Route path="/bem-vindo" element={
              <ProtectedRoute ignorarOnboarding>
                <BemVindoPage />
              </ProtectedRoute>
            } />

            {/* Também ignora o onboarding: quem está bloqueado e com o wizard
                pendente receberia 402 no wizard e voltaria para cá — laço. */}
            <Route path="/assinar" element={
              <ProtectedRoute ignorarOnboarding>
                <AssinarPage />
              </ProtectedRoute>
            } />

            <Route path="/" element={
              <ProtectedRoute>
                <Layout />
              </ProtectedRoute>
            }>
              <Route index element={<Navigate to="/lojas" replace />} />
              <Route path="lojas" element={<SelecionarLojaPage />} />
              <Route path="agenda" element={<AgendaPage />} />
              <Route path="relatorios" element={<RelatoriosPage />} />
              <Route path="configuracoes" element={<ConfiguracoesPage />} />
              <Route path="empresas" element={<EmpresasPage />} />
            </Route>
          </Routes>
        </BrowserRouter>
      </QueryClientProvider>
  );
}