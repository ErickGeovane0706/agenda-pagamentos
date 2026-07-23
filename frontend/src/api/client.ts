/// <reference types="vite/client" />
import axios, { AxiosError, InternalAxiosRequestConfig } from 'axios';

interface CustomConfig extends InternalAxiosRequestConfig {
  _retry?: boolean;
}

const api = axios.create({
  baseURL: import.meta.env.VITE_API_URL || '/api',
  timeout: 30000,
  withCredentials: true,
});

type PendingRequest = {
  resolve: (value: unknown) => void;
  reject: (reason: unknown) => void;
};

let isRefreshing = false;
let pendingRequests: PendingRequest[] = [];

/** Onde a tela de contratação lê o motivo do bloqueio que acabou de acontecer. */
export const MOTIVO_402 = 'agenda-motivo-402';

api.interceptors.response.use(
  (response) => response,
  async (error: AxiosError) => {
    const originalRequest = error.config as CustomConfig | undefined;

    // 402: o trial venceu, a empresa está inadimplente, ou a operação passou do
    // contratado (a 2ª loja no trial). São o mesmo momento comercial — o cliente
    // decidiu usar mais do que tem — e vão todos para a tela de contratação.
    // Sem isto a tela apenas morre em silêncio, no instante exato em que ele
    // estava disposto a pagar.
    if (error.response?.status === 402) {
      const mensagem = (error.response.data as { mensagem?: string } | undefined)?.mensagem;
      sessionStorage.setItem(MOTIVO_402, mensagem ?? '');
      // O guard evita laço caso a própria tela de contratação receba 402.
      if (window.location.pathname !== '/assinar') window.location.href = '/assinar';
      return Promise.reject(error);
    }

    if (error.response?.status !== 401 || originalRequest?._retry) {
      return Promise.reject(error);
    }

    if (isRefreshing) {
      return new Promise((resolve, reject) => {
        pendingRequests.push({ resolve, reject });
      }).then(() => api(originalRequest!));
    }

    if (originalRequest) originalRequest._retry = true;
    isRefreshing = true;

    try {
      await api.post('/auth/refresh');
      pendingRequests.forEach((p) => p.resolve(undefined));
      pendingRequests = [];
      return api(originalRequest!);
    } catch {
      pendingRequests.forEach((p) => p.reject(error));
      pendingRequests = [];
      const { useAuthStore } = await import('../store/authStore');
      useAuthStore.getState().logout();
      window.location.href = '/login';
      return Promise.reject(error);
    } finally {
      isRefreshing = false;
    }
  }
);

export default api;
