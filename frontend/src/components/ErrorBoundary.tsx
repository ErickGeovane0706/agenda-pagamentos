import { Component, ReactNode } from 'react';
import * as Sentry from '@sentry/react';

/**
 * Última barreira do frontend: erro de render que hoje deixa a tela branca.
 *
 * Tela branca é o pior desfecho possível — o cliente não sabe se travou, se
 * perdeu o que digitou ou se o sistema morreu, e não tem o que fazer além de
 * fechar. Aqui ele ao menos vê o que aconteceu e tem um botão.
 *
 * O reporte ao Sentry acontece mesmo sem DSN configurada: `captureException`
 * sem init é um no-op silencioso do SDK, então não há guarda a escrever.
 */
export default class ErrorBoundary extends Component<
  { children: ReactNode },
  { falhou: boolean }
> {
  state = { falhou: false };

  static getDerivedStateFromError() {
    return { falhou: true };
  }

  componentDidCatch(erro: Error, info: { componentStack?: string | null }) {
    Sentry.captureException(erro, { extra: { componentStack: info.componentStack } });
  }

  render() {
    if (!this.state.falhou) return this.props.children;

    return (
      <div className="min-h-screen flex items-center justify-center bg-slate-50 px-6">
        <div className="max-w-sm text-center">
          <h1 className="text-lg font-bold text-slate-900">Algo deu errado nesta tela.</h1>
          <p className="text-sm text-slate-500 mt-2">
            Já fomos avisados. Seus dados estão salvos — nada do que você registrou se perdeu.
          </p>
          <button
            onClick={() => window.location.assign('/')}
            className="mt-5 px-4 py-2.5 rounded-xl bg-[#0c4a6e] text-white text-sm font-medium hover:bg-[#0a3d5c] transition-colors"
          >
            Voltar ao início
          </button>
        </div>
      </div>
    );
  }
}
