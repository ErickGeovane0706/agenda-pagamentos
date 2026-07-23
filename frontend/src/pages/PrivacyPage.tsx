import { Link } from 'react-router-dom';
import { Shield, ArrowLeft } from 'lucide-react';

/**
 * Política de privacidade. Reescrita em 23/07/2026 para acompanhar o cadastro
 * público, que trouxe quatro operadores novos (Resend, Asaas, OpenAI,
 * Anthropic) — a versão anterior citava só Cloudflare e Railway.
 *
 * Duas correções importantes em relação ao texto antigo:
 *
 * 1. A retenção prometia "dados financeiros por 5 anos, logs por 2, depois
 *    anonimizados". NADA no sistema faz isso — só existe anonimização a pedido
 *    (SchedulerService.processarExclusoes, 03:00). Promessa não cumprida em
 *    política de privacidade é prova contra o controlador, não proteção.
 *
 * 2. Todos os operadores ficam fora do Brasil, o que é transferência
 *    internacional e precisa ser informada (LGPD art. 33).
 *
 * Os direitos são exercíveis na própria interface (Configurações → Meus dados,
 * sobre /api/lgpd/portabilidade e /corrigir; exclusão na aba Perfil). O e-mail
 * ficou como canal para o que a tela não cobre.
 *
 * TODO quando houver domínio próprio: trocar o e-mail de contato por
 * privacidade@<dominio> — o Gmail atual expõe uma caixa pessoal a scrapers.
 */

function Secao({ titulo, children }: { titulo: string; children: React.ReactNode }) {
  return (
    <section>
      <h2 className="text-base font-bold text-slate-900 mb-2">{titulo}</h2>
      <div className="space-y-2">{children}</div>
    </section>
  );
}

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
          <p className="text-xs text-slate-400">Última atualização: 23 de julho de 2026</p>

          <p>
            Esta política explica quais dados a Agenda de Pagamentos guarda, por que guarda e
            com quem divide. Está em linguagem simples porque de nada adianta você aceitar algo
            que não dá para entender.
          </p>

          <Secao titulo="1. Quem é o responsável">
            <p>
              O sistema é operado por <strong>Erick Geovane Silvestre da Silva</strong>,
              inscrita no CNPJ sob o nº <strong>66.337.547/0001-95</strong>, que é a
              controladora dos dados tratados aqui.
            </p>
            <p>
              Contato para assuntos de privacidade:{' '}
              <a href="mailto:privacidade@diadepagar.com.br" className="text-[#0c4a6e] underline">
                privacidade@diadepagar.com.br
              </a>.
            </p>
          </Secao>

          <Secao titulo="2. O que guardamos">
            <ul className="list-disc pl-5 space-y-1">
              <li><strong>Você:</strong> nome, e-mail e senha (guardada embaralhada, não conseguimos lê-la).</li>
              <li><strong>Seu negócio:</strong> nome, lojas e, se você for assinar, CPF ou CNPJ e telefone.</li>
              <li><strong>Suas contas:</strong> fornecedor, valor, vencimento, situação, e o banco no caso de cheques.</li>
              <li><strong>Arquivos:</strong> PDFs e fotos de boletos que você enviar.</li>
              <li><strong>WhatsApp:</strong> se ativar os lembretes, o número que você informar e as mensagens trocadas com o assistente.</li>
              <li><strong>Registro de ações:</strong> quem fez o quê e quando, para segurança e rastreabilidade.</li>
            </ul>
            <p>
              Não pedimos dados de cartão. Quando você assina, o pagamento acontece no Asaas —
              nós não vemos nem guardamos nada do seu meio de pagamento.
            </p>
          </Secao>

          <Secao titulo="3. Para que usamos">
            <ul className="list-disc pl-5 space-y-1">
              <li>Deixar você entrar na sua conta e proteger o acesso.</li>
              <li>Mostrar e organizar as contas do seu negócio.</li>
              <li>Enviar os lembretes que você pediu.</li>
              <li>Cobrar a assinatura, quando houver.</li>
              <li>Descobrir e corrigir falhas do sistema — quando algo quebra, recebemos um aviso técnico para arrumar.</li>
            </ul>
            <p className="font-medium text-slate-800">
              Não vendemos seus dados, não usamos para publicidade e não repassamos para quem
              não está listado abaixo.
            </p>
          </Secao>

          <Secao titulo="4. Com quem dividimos">
            <p>
              Só com empresas que fazem o sistema funcionar, e só com a parte que cada uma
              precisa:
            </p>
            <ul className="list-disc pl-5 space-y-1">
              <li><strong>Railway</strong> — hospeda o sistema e o banco de dados.</li>
              <li><strong>Cloudflare R2</strong> — guarda os arquivos que você envia.</li>
              <li><strong>Resend</strong> — entrega os e-mails do sistema (confirmação de cadastro, recuperação de senha). Recebe seu e-mail e seu nome.</li>
              <li><strong>Asaas</strong> — processa a cobrança da assinatura. Recebe nome do negócio, CPF ou CNPJ e telefone.</li>
              <li><strong>Meta (WhatsApp)</strong> — entrega as mensagens de lembrete. Recebe o número e o conteúdo enviado.</li>
              <li><strong>OpenAI</strong> — transcreve os áudios que você mandar para o assistente no WhatsApp.</li>
              <li><strong>Anthropic</strong> — interpreta o que você escreve para o assistente e monta a resposta.</li>
              <li><strong>Sentry</strong> — recebe aviso quando algo dá errado no sistema, para conseguirmos corrigir. Recebe dado técnico da falha (mensagem de erro, tela em que aconteceu); configuramos para não enviar seu IP nem o conteúdo do que você digitou.</li>
            </ul>
            <p>
              Também podemos entregar dados por ordem judicial ou exigência legal.
            </p>
          </Secao>

          <Secao titulo="5. Dados que saem do Brasil">
            <p>
              Todas as empresas acima ficam fora do país: a maior parte nos Estados Unidos, e o
              serviço de monitoramento de erros na Alemanha. Ao usar o sistema, seus dados são
              processados no exterior por elas, dentro do que a LGPD permite e apenas para as
              finalidades desta política.
            </p>
          </Secao>

          <Secao titulo="6. Assistente no WhatsApp">
            <p>
              Se você conversar com o assistente, o texto da mensagem (ou a transcrição do
              áudio) é enviado para a Anthropic e, no caso de áudio, para a OpenAI, para ser
              interpretado. Isso inclui o que você escrever ali — evite mandar informação
              sensível que não tenha a ver com suas contas.
            </p>
            <p>
              Se preferir não usar isso, basta não conversar com o assistente: o restante do
              sistema funciona normalmente.
            </p>
          </Secao>

          <Secao titulo="7. Por quanto tempo guardamos">
            <p>
              Enquanto sua conta existir, seus dados ficam guardados — é o que permite você
              consultar o histórico do seu negócio.
            </p>
            <p>
              Quando você pede exclusão, os dados pessoais são apagados ou substituídos por
              informação anônima no processamento seguinte, que roda todo dia de madrugada.
              Registros financeiros podem ser mantidos de forma anonimizada, sem ligação com
              você, para obrigações legais e contábeis.
            </p>
            <p>
              Cadastro iniciado e não confirmado é apagado sozinho em 48 horas.
            </p>
          </Secao>

          <Secao titulo="8. Cookies">
            <p>
              Usamos apenas os cookies necessários para manter você conectado com segurança.
              Não usamos cookie de propaganda nem de rastreamento entre sites.
            </p>
          </Secao>

          <Secao titulo="9. Segurança">
            <p>
              As senhas são guardadas embaralhadas, o acesso é por conexão criptografada, e cada
              empresa só enxerga os próprios dados. Nenhum sistema é 100% seguro, mas tratamos
              isso a sério e corrigimos o que encontramos.
            </p>
          </Secao>

          <Secao titulo="10. Seus direitos">
            <p>A LGPD garante que você pode:</p>
            <ul className="list-disc pl-5 space-y-1">
              <li>Saber quais dados temos sobre você.</li>
              <li>Pedir cópia deles em formato que dê para levar para outro sistema.</li>
              <li>Corrigir o que estiver errado.</li>
              <li>Pedir a exclusão dos seus dados pessoais.</li>
              <li>Retirar o consentimento dos lembretes quando quiser.</li>
            </ul>
            <p>
              Você mesmo faz a maior parte disso dentro do sistema, em{' '}
              <strong>Configurações → Meus dados</strong>: baixar tudo o que temos sobre você
              num arquivo e pedir correção do que estiver errado. A exclusão fica em{' '}
              <strong>Configurações → Perfil</strong>.
            </p>
            <p>
              Para o que não estiver ali, escreva para{' '}
              <a href="mailto:privacidade@diadepagar.com.br" className="text-[#0c4a6e] underline">
                privacidade@diadepagar.com.br
              </a>. Respondemos em até 15 dias.
            </p>
          </Secao>

          <Secao titulo="11. Mudanças nesta política">
            <p>
              Se algo importante mudar, avisamos por e-mail ou dentro do sistema. A data no topo
              sempre mostra a última alteração.
            </p>
          </Secao>

          <p className="text-xs text-slate-400 pt-2">
            Veja também os{' '}
            <Link to="/termos" className="text-[#0c4a6e] underline">Termos de Uso</Link>.
          </p>
        </div>
      </main>
    </div>
  );
}
