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
            onRehydrateStorage: () => (state) => {
                state?.setHasHydrated(true);
            },
        }
    )
);