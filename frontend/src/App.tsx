import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { useAuthStore } from './store/authStore';

import LoginPage from './pages/LoginPage';
import SelecionarLojaPage from './pages/SelecionarLojaPage';
import AgendaPage from './pages/AgendaPage';
import RelatoriosPage from './pages/RelatoriosPage';
import PrivacyPage from './pages/PrivacyPage';
import ConfiguracoesPage from './pages/ConfiguracoesPage';
import EmpresasPage from './pages/EmpresasPage';
import Layout from './components/Layout';
import { ToastContainer } from './components/ToastContainer';

const queryClient = new QueryClient({
  defaultOptions: {
    queries: { staleTime: 1000 * 60 * 5, retry: 1 },
  }
});

function ProtectedRoute({ children }: { children: React.ReactNode }) {
  const token = useAuthStore((s) => s.token);
  if (!token) return <Navigate to="/login" replace />;
  return <>{children}</>;
}

export default function App() {
  return (
    <QueryClientProvider client={queryClient}>
      <BrowserRouter>
        <ToastContainer />
        <Routes>
          <Route path="/login" element={<LoginPage />} />
          <Route path="/privacidade" element={<PrivacyPage />} />

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
