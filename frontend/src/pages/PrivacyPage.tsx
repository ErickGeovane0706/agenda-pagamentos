import { Link } from 'react-router-dom';
import { Shield, ArrowLeft } from 'lucide-react';

export default function PrivacyPage() {
  return (
    <div className="min-h-screen bg-slate-50">
      <header className="bg-white border-b border-slate-200 px-6 py-4">
        <div className="max-w-3xl mx-auto flex items-center gap-3">
          <Link to="/login" className="p-2 rounded-lg hover:bg-slate-100 text-slate-500">
            <ArrowLeft className="w-4 h-4" />
          </Link>
          <Shield className="w-5 h-5 text-[#0c4a6e]" />
          <h1 className="font-bold text-slate-900">Política de Privacidade</h1>
        </div>
      </header>

      <main className="max-w-3xl mx-auto px-4 py-8">
        <div className="bg-white rounded-2xl shadow-sm border border-slate-100 p-8 space-y-6 text-sm text-slate-600 leading-relaxed">
          <p className="text-xs text-slate-400">Última atualização: Junho de 2026</p>

          <section>
            <h2 className="text-base font-bold text-slate-900 mb-2">1. Dados coletados</h2>
            <ul className="list-disc pl-5 space-y-1">
              <li><strong>Usuários:</strong> nome, e-mail</li>
              <li><strong>Empresas:</strong> nome, dados de lojas (CNPJ opcional)</li>
              <li><strong>Pagamentos:</strong> fornecedor, valor, vencimento, status</li>
              <li><strong>Arquivos:</strong> PDFs e imagens de boletos</li>
              <li><strong>Logs de auditoria:</strong> ações realizadas no sistema</li>
            </ul>
          </section>

          <section>
            <h2 className="text-base font-bold text-slate-900 mb-2">2. Finalidade dos dados</h2>
            <ul className="list-disc pl-5 space-y-1">
              <li><strong>Autenticação:</strong> controle de acesso ao sistema</li>
              <li><strong>Gestão financeira:</strong> controle de pagamentos da empresa</li>
              <li><strong>Segurança:</strong> registro de auditoria para rastreabilidade</li>
            </ul>
          </section>

          <section>
            <h2 className="text-base font-bold text-slate-900 mb-2">3. Compartilhamento</h2>
            <p>Seus dados podem ser compartilhados com:</p>
            <ul className="list-disc pl-5 space-y-1">
              <li><strong>Cloudflare R2:</strong> armazenamento de arquivos</li>
              <li><strong>Railway:</strong> hospedagem do sistema</li>
            </ul>
          </section>

          <section>
            <h2 className="text-base font-bold text-slate-900 mb-2">4. Retenção</h2>
            <ul className="list-disc pl-5 space-y-1">
              <li><strong>Dados financeiros:</strong> 5 anos (obrigação fiscal)</li>
              <li><strong>Logs de auditoria:</strong> 2 anos</li>
              <li>Após o prazo, os dados são anonimizados</li>
            </ul>
          </section>

          <section>
            <h2 className="text-base font-bold text-slate-900 mb-2">5. Seus direitos (LGPD)</h2>
            <ul className="list-disc pl-5 space-y-1">
              <li>Solicitar exclusão dos seus dados pessoais</li>
              <li>Solicitar exportação dos seus dados</li>
            </ul>
            <p className="mt-2">Para exercer seus direitos, entre em contato pelo e-mail: erickgeovane2002@gmail.com</p>
          </section>
        </div>
      </main>
    </div>
  );
}
