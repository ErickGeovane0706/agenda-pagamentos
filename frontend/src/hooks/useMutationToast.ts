import { useMutation, UseMutationOptions } from '@tanstack/react-query';
import { AxiosError } from 'axios';
import { useToastStore, ToastType } from '../store/toastStore';

export function useMutationToast<TData = unknown, TError = AxiosError, TVariables = void, TContext = unknown>(
  options: UseMutationOptions<TData, TError, TVariables, TContext> & {
    successMessage?: string;
    errorMessage?: string;
  }
) {
  const addToast = useToastStore((s) => s.addToast);

  return useMutation({
    ...options,
    onSuccess: (...args) => {
      if (options.successMessage) {
        addToast('success', options.successMessage);
      }
      options.onSuccess?.(...args);
    },
    onError: (error, ...args) => {
      const message = options.errorMessage || 'Ocorreu um erro inesperado';
      const axiosError = error as AxiosError<{ detail?: string; title?: string }>;
      const detail = axiosError.response?.data?.detail || axiosError.response?.data?.title;
      addToast('error', detail || message);
      options.onError?.(error, ...args);
    },
  });
}
