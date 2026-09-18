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
import ProdutosPage from './pages/ProdutosPage';
import VendaPage from './pages/VendaPage';
import RelatorioVendasPage from './pages/RelatorioVendasPage';
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

/**
 * O que o domínio nu serve. Até aqui, `/` caía dentro do ProtectedRoute e
 * jogava todo visitante deslogado no formulário de login — inclusive quem
 * digitou `diadepagar.com.br` depois de ver o anúncio, que é justamente quem
 * ainda não tem conta. A landing existia em `/comecar`, uma URL que nada no
 * app linkava e ninguém digita.
 *
 * Espera a hidratação antes de decidir: sem isso, quem já está logado vê a
 * landing piscar por um quadro antes de ser mandado para o app.
 */
function Raiz() {
  const usuario = useAuthStore((s) => s.usuario);
  const hasHydrated = useAuthStore((s) => s.hasHydrated);

  if (!hasHydrated) return <LoadingScreen />;
  // Quem está no meio do onboarding é desviado para o wizard pelo
  // ProtectedRoute de /lojas — um salto a mais, sem laço.
  return usuario ? <Navigate to="/lojas" replace /> : <LandingPage />;
}

export default function App() {
  return (
      <QueryClientProvider client={queryClient}>
        <BrowserRouter>
          <ToastContainer />
          <Routes>
            <Route path="/" element={<Raiz />} />
            {/* Mantida como apelido: é a URL que já circulou e pode estar em
                anúncio ou link antigo. A canônica passa a ser `/`. */}
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

            {/* Rota de layout SEM path: só agrupa o que exige login, sem
                reivindicar `/` — que agora é da landing. Os filhos continuam
                resolvendo para /lojas, /agenda etc. */}
            <Route element={
              <ProtectedRoute>
                <Layout />
              </ProtectedRoute>
            }>
              <Route path="lojas" element={<SelecionarLojaPage />} />
              <Route path="agenda" element={<AgendaPage />} />
              <Route path="relatorios" element={<RelatoriosPage />} />
              <Route path="configuracoes" element={<ConfiguracoesPage />} />
              <Route path="empresas" element={<EmpresasPage />} />
              {/* Módulo de Estoque/PDV. Não existe guarda de rota por
                  pdvHabilitado de propósito: quem chegar aqui sem o módulo
                  recebe 403 do PdvGateFilter na primeira chamada. Repetir a
                  regra no front criaria dois lugares para ela divergir, e o
                  que vale é o do servidor. */}
              <Route path="produtos" element={<ProdutosPage />} />
              <Route path="venda" element={<VendaPage />} />
              <Route path="venda/relatorio" element={<RelatorioVendasPage />} />
            </Route>
          </Routes>
        </BrowserRouter>
      </QueryClientProvider>
  );
}