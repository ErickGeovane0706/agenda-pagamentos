import { Link } from 'react-router-dom';
import { FileText, ArrowLeft } from 'lucide-react';

/**
 * Termos de uso. O checkbox do cadastro aponta para cá — antes desta página
 * existir, ele pedia aceite de um documento inexistente.
 *
 * Linguagem simples não é escolha estética: a LGPD exige informação clara e
 * adequada (art. 9º), e texto que o dono do mercadinho não entende enfraquece
 * o próprio consentimento que se está coletando.
 *
 * Cada número aqui vem do código: 30 dias e 1 loja de `criarTrial`, os preços
 * de `assinatura.preco-*`, os 5 dias de carência de `assinatura.carencia-dias`,
 * e o modo somente-leitura do `AssinaturaGateFilter`. Se algum deles mudar no
 * application.yml, este texto precisa mudar junto.
 *
 * TODO: o e-mail é uma caixa dedicada, mas ainda é Gmail. Trocar por um do
 * domínio (diadepagar.com.br) quando o encaminhamento estiver de pé.
 */

function Secao({ titulo, children }: { titulo: string; children: React.ReactNode }) {
  return (
    <section>
      <h2 className="text-base font-bold text-slate-900 mb-2">{titulo}</h2>
      <div className="space-y-2">{children}</div>
    </section>
  );
}

export default function TermosPage() {
  return (
    <div className="min-h-screen bg-slate-50">
      <header className="bg-white border-b border-slate-200 px-6 py-4">
        <div className="max-w-3xl mx-auto flex items-center gap-3">
          <Link to="/login" className="p-2 rounded-lg hover:bg-slate-100 text-slate-500">
            <ArrowLeft className="w-4 h-4" />
          </Link>
          <FileText className="w-5 h-5 text-[#0c4a6e]" />
          <h1 className="font-bold text-slate-900">Termos de Uso</h1>
        </div>
      </header>

      <main className="max-w-3xl mx-auto px-4 py-8">
        <div className="bg-white rounded-2xl shadow-sm border border-slate-100 p-8 space-y-6 text-sm text-slate-600 leading-relaxed">
          <p className="text-xs text-slate-400">Última atualização: 23 de julho de 2026</p>

          <p>
            Este texto explica as regras para usar a Agenda de Pagamentos. Escrevemos em
            linguagem simples de propósito: se algo aqui não estiver claro, escreva para a
            gente antes de aceitar.
          </p>

          <p>
            O serviço é oferecido por <strong>Erick Geovane Silvestre da Silva</strong>,
            CNPJ <strong>66.337.547/0001-95</strong>. Ao criar sua conta, você aceita estas
            regras.
          </p>

          <Secao titulo="1. O que o sistema faz — e o que ele não faz">
            <p>
              A Agenda de Pagamentos organiza as contas do seu negócio: você registra boletos,
              Pix e cheques, e o sistema mostra o que vence e avisa você.
            </p>
            <p className="font-medium text-slate-800">
              O sistema não paga nada por você. Ele não movimenta dinheiro, não tem acesso à
              sua conta bancária e não emite boleto. Quem paga é você, no seu banco. O que
              fazemos é organizar a informação e lembrar.
            </p>
            <p>
              Por isso, conta paga em atraso continua sendo responsabilidade sua, mesmo que o
              lembrete tenha falhado — internet cai, WhatsApp fica fora do ar, celular perde
              sinal. Use o sistema como apoio, não como única garantia.
            </p>
          </Secao>

          <Secao titulo="2. Quem pode usar">
            <p>
              Você precisa ter 18 anos ou mais e ser o dono do negócio ou alguém autorizado por
              ele. Ao criar a conta, você confirma que as informações que deu são verdadeiras.
            </p>
          </Secao>

          <Secao titulo="3. Sua conta e sua senha">
            <p>
              A senha é sua e não deve ser compartilhada. Tudo que for feito com o seu login é
              tratado como feito por você. Se desconfiar que alguém entrou na sua conta, troque
              a senha e avise a gente.
            </p>
            <p>
              Você pode criar outros usuários para a sua equipe. Quem você convida passa a ver
              os dados do seu negócio — pense antes de convidar.
            </p>
          </Secao>

          <Secao titulo="4. Teste grátis">
            <p>
              Toda conta nova começa com <strong>30 dias grátis</strong>, com <strong>1 loja</strong>.
              Não pedimos cartão para começar e não cobramos nada automaticamente quando o teste
              acaba: se você não assinar, a conta simplesmente para de aceitar novos lançamentos.
            </p>
          </Secao>

          <Secao titulo="5. Preço e cobrança">
            <ul className="list-disc pl-5 space-y-1">
              <li><strong>R$ 79,00 por mês</strong>, já com 1 loja incluída.</li>
              <li><strong>R$ 29,00 por mês</strong> para cada loja a mais.</li>
              <li>A cobrança é mensal e feita pelo Asaas, por Pix ou boleto.</li>
              <li>Se você contratar uma loja a mais no meio do mês, ela libera na hora e o valor novo entra na próxima cobrança.</li>
            </ul>
            <p>
              Se o preço mudar, avisamos com pelo menos 30 dias de antecedência, e o preço novo
              só vale para as cobranças seguintes. Você pode cancelar antes se não concordar.
            </p>
          </Secao>

          <Secao titulo="6. Se o pagamento atrasar">
            <p>
              Você tem <strong>5 dias de tolerância</strong> depois do vencimento. Passado esse
              prazo, a conta entra em <strong>modo somente leitura</strong>: você continua vendo
              tudo o que já registrou, mas não consegue lançar nem editar até regularizar.
            </p>
            <p className="font-medium text-slate-800">
              Seus dados não são apagados por falta de pagamento. Eles ficam guardados e voltam
              a ficar disponíveis assim que você pagar.
            </p>
          </Secao>

          <Secao titulo="7. Cancelamento">
            <p>
              Você pode cancelar quando quiser, sem multa e sem precisar justificar. O acesso
              continua até o fim do período que você já pagou. Não devolvemos valor proporcional
              de mês já iniciado.
            </p>
            <p>
              Se quiser apagar seus dados de vez, isso é um pedido separado do cancelamento —
              veja como na <Link to="/privacidade" className="text-[#0c4a6e] underline">Política de Privacidade</Link>.
            </p>
          </Secao>

          <Secao titulo="8. Avisos no WhatsApp">
            <p>
              Se você ativar os lembretes, enviamos mensagens no número que você informar. Você
              desliga quando quiser, nas configurações.
            </p>
            <p>
              O WhatsApp é da Meta e tem regras próprias. Uma delas: fora de uma conversa
              recente, só podemos enviar modelos de mensagem aprovados por eles. Isso limita o
              que conseguimos escrever e pode atrasar ou impedir um aviso — não é falha sua nem
              nossa.
            </p>
            <p>
              Há um limite mensal de mensagens por conta, para o custo não fugir do controle.
              Ao chegar perto do limite, avisamos você.
            </p>
          </Secao>

          <Secao titulo="9. O que não pode">
            <ul className="list-disc pl-5 space-y-1">
              <li>Usar o sistema para algo ilegal.</li>
              <li>Tentar invadir, sobrecarregar ou copiar o sistema.</li>
              <li>Cadastrar dados de terceiros sem que eles saibam.</li>
              <li>Revender o acesso ou compartilhar sua conta com outro negócio.</li>
            </ul>
            <p>
              Se isso acontecer, podemos suspender a conta. Em caso grave, imediatamente; nos
              demais, avisamos antes e damos prazo para corrigir.
            </p>
          </Secao>

          <Secao titulo="10. Falhas e indisponibilidade">
            <p>
              Fazemos o possível para o sistema estar sempre no ar, mas não prometemos
              funcionamento sem nenhuma interrupção. Pode haver manutenção, falha de internet ou
              problema em serviços de terceiros que usamos.
            </p>
            <p>
              Nossa responsabilidade em qualquer situação fica limitada ao valor que você pagou
              nos últimos 12 meses. Não respondemos por lucro que você deixou de ter, multa de
              conta paga em atraso ou prejuízo indireto.
            </p>
          </Secao>

          <Secao titulo="11. Seus dados são seus">
            <p>
              O que você registra no sistema pertence a você. Não vendemos seus dados e não os
              usamos para anunciar nada. Você pode exportar suas informações a qualquer momento.
            </p>
            <p>
              O que fazemos com eles está detalhado na{' '}
              <Link to="/privacidade" className="text-[#0c4a6e] underline">Política de Privacidade</Link>,
              que faz parte destes termos.
            </p>
          </Secao>

          <Secao titulo="12. Mudanças nestes termos">
            <p>
              Se mudarmos algo importante, avisamos por e-mail ou dentro do sistema antes de
              valer. Continuar usando depois disso significa que você concorda com o texto novo.
              Se não concordar, pode cancelar.
            </p>
          </Secao>

          <Secao titulo="13. Lei e foro">
            <p>
              Valem as leis brasileiras. Antes de qualquer medida judicial, as duas partes se
              comprometem a tentar resolver o problema conversando, por e-mail.
            </p>
            <p>
              Se houver disputa, fica eleito o foro do domicílio do usuário.
            </p>
          </Secao>

          <Secao titulo="14. Falar com a gente">
            <p>
              Dúvida sobre estes termos, escreva para{' '}
              <a href="mailto:privacidade.diadepagar@gmail.com" className="text-[#0c4a6e] underline">
                privacidade.diadepagar@gmail.com
              </a>.
            </p>
          </Secao>
        </div>
      </main>
    </div>
  );
}
