import { Outlet, NavLink, Link, useNavigate } from 'react-router-dom';
import { useAuthStore } from '../store/authStore';
import { useQueryClient } from '@tanstack/react-query';
import { Store, Calendar, BarChart3, Settings, LogOut, Building2, Shield, Globe } from 'lucide-react';
import { clsx } from 'clsx';
import AvisoAssinatura from './AvisoAssinatura';

export default function Layout() {
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const { usuario, logout } = useAuthStore();

  const navItems = [
    { to: '/lojas', label: 'Lojas', icon: Store },
    { to: '/agenda', label: 'Agenda', icon: Calendar },
    { to: '/relatorios', label: 'Relatórios', icon: BarChart3 },
    { to: '/configuracoes', label: 'Config.', icon: Settings },
    ...(usuario?.perfil === 'MASTER' ? [{ to: '/empresas' as const, label: 'Empresas' as const, icon: Globe }] : []),
  ];

  const handleLogout = () => {
    queryClient.clear();
    logout();
    navigate('/login');
  };

  return (
    <div className="min-h-screen bg-slate-50 flex">
      {/* Sidebar - Desktop */}
      <aside className="hidden md:flex md:flex-col md:w-64 md:fixed md:inset-y-0 bg-white border-r border-slate-200">
        <div className="flex items-center gap-3 px-6 py-5 border-b border-slate-100">
          <div className="w-9 h-9 bg-[#0c4a6e] rounded-xl flex items-center justify-center flex-shrink-0">
            <Building2 className="w-5 h-5 text-white" />
          </div>
          <div>
            <h1 className="font-bold text-slate-900 text-sm leading-tight">Agenda</h1>
            <p className="text-xs text-slate-400">{usuario?.nome}</p>
          </div>
        </div>

        <nav className="flex-1 px-3 py-4 space-y-1">
          {navItems.map(({ to, label, icon: Icon }) => (
            <NavLink
              key={to}
              to={to}
              className={({ isActive }) => clsx(
                'flex items-center gap-3 px-3 py-2.5 rounded-xl text-sm font-medium transition-colors',
                isActive
                  ? 'bg-[#e0f2fe] text-[#0c4a6e]'
                  : 'text-slate-600 hover:bg-slate-50 hover:text-slate-800'
              )}
            >
              <Icon className="w-4 h-4" />
              {label}
            </NavLink>
          ))}
        </nav>

        <div className="px-3 py-3 border-t border-slate-100 space-y-1">
          <Link to="/privacidade"
            className="flex items-center gap-3 px-3 py-2 rounded-xl text-xs text-slate-400 hover:text-slate-600 hover:bg-slate-50 transition-colors">
            <Shield className="w-3.5 h-3.5" />
            Privacidade
          </Link>
          <button
            onClick={handleLogout}
            className="flex items-center gap-3 px-3 py-2.5 rounded-xl text-sm font-medium text-slate-500 hover:bg-red-50 hover:text-red-600 transition-colors w-full"
          >
            <LogOut className="w-4 h-4" />
            Sair
          </button>
        </div>
      </aside>

      {/* Mobile header */}
      <div className="md:pl-64 flex flex-col flex-1">
        <AvisoAssinatura />
        <main className="flex-1 pb-20 md:pb-0">
          <Outlet />
        </main>

        {/* Bottom nav - Mobile */}
        <nav className="md:hidden fixed bottom-0 left-0 right-0 bg-white border-t border-slate-200 pb-[env(safe-area-inset-bottom)] z-50">
          <div className="flex items-center justify-around px-2 py-1">
            {navItems.map(({ to, label, icon: Icon }) => (
              <NavLink
                key={to}
                to={to}
                className={({ isActive }) => clsx(
                  'flex flex-col items-center gap-0.5 px-3 py-2 text-xs font-medium rounded-lg transition-colors min-w-[64px]',
                  isActive
                    ? 'text-[#0c4a6e]'
                    : 'text-slate-500 hover:text-slate-700'
                )}
              >
                <Icon className="w-5 h-5" />
                {label}
              </NavLink>
            ))}
          </div>
          <Link to="/privacidade"
            className="block text-center py-1 text-[10px] text-slate-300 hover:text-slate-500 transition-colors">
            Política de Privacidade
          </Link>
        </nav>
      </div>
    </div>
  );
}
