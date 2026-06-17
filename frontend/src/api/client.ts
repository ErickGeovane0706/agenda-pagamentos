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

api.interceptors.response.use(
  (response) => response,
  async (error: AxiosError) => {
    const originalRequest = error.config as CustomConfig | undefined;
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
