import { create } from 'zustand';
import { persist } from 'zustand/middleware';
import { Usuario } from '../types';

interface AuthState {
    usuario: Usuario | null;
    lojaAtiva: string | null;
    hasHydrated: boolean;
    setAuth: (usuario: Usuario) => void;
    setLojaAtiva: (lojaId: string) => void;
    logout: () => void;
    setHasHydrated: (value: boolean) => void;
}

export const useAuthStore = create<AuthState>()(
    persist(
        (set) => ({
            usuario: null,
            lojaAtiva: null,
            hasHydrated: false,

            setAuth: (usuario) => set({ usuario }),

            setLojaAtiva: (lojaId) => set({ lojaAtiva: lojaId }),

            logout: () => set({ usuario: null, lojaAtiva: null }),

            setHasHydrated: (value) => set({ hasHydrated: value }),
        }),
        {
            name: 'agenda-auth',
            // O flag precisa subir SEMPRE, inclusive quando a re-hidratação
            // falha — aí o zustand chama isto com state undefined. A versão
            // com `state?.` engolia esse caso e deixava hasHydrated false para
            // sempre: o ProtectedRoute ficava eternamente em LoadingScreen, o
            // login parecia não funcionar (ele funciona; a tela é que não
            // avança) e tentar de novo não resolvia, porque nada re-dispara a
            // hidratação. Só recarregar a página.
            onRehydrateStorage: () => (_state, error) => {
                if (error) {
                    console.error('[auth] falha ao re-hidratar a sessão', error);
                }
                useAuthStore.setState({ hasHydrated: true });
            },
        }
    )
);