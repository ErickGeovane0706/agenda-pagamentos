import { create } from 'zustand';
import { persist } from 'zustand/middleware';
import { Usuario } from '../types';

interface AuthState {
  token: string | null;
  usuario: Usuario | null;
  lojaAtiva: string | null;
  setAuth: (token: string, usuario: Usuario) => void;
  setLojaAtiva: (lojaId: string) => void;
  logout: () => void;
}

export const useAuthStore = create<AuthState>()(
  persist(
    (set) => ({
      token: null,
      usuario: null,
      lojaAtiva: null,

      setAuth: (token, usuario) => {
        localStorage.setItem('token', token);
        set({ token, usuario });
      },

      setLojaAtiva: (lojaId) => set({ lojaAtiva: lojaId }),

      logout: () => {
        localStorage.removeItem('token');
        localStorage.removeItem('usuario');
        set({ token: null, usuario: null, lojaAtiva: null });
      },
    }),
    { name: 'agenda-auth' }
  )
);
