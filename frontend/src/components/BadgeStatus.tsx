import { clsx } from 'clsx';

const configs: Record<string, { label: string; classes: string }> = {
  PENDENTE:     { label: 'Pendente',     classes: 'bg-[#ffedd5] text-[#9a3412] border-[#ffedd5]' },
  PAGO:         { label: 'Pago',         classes: 'bg-[#e0f2fe] text-[#0c4a6e] border-[#e0f2fe]' },
  VENCIDO:      { label: 'Vencido',      classes: 'bg-[#fee2e2] text-[#991b1b] border-[#fee2e2]' },
  CANCELADO:    { label: 'Cancelado',    classes: 'bg-slate-100 text-slate-600 border-slate-200' },
  COMPENSADO:   { label: 'Compensado',   classes: 'bg-[#e0f2fe] text-[#0c4a6e] border-[#e0f2fe]' },
  DEVOLVIDO:    { label: 'Devolvido',    classes: 'bg-[#fee2e2] text-[#991b1b] border-[#fee2e2]' },
};

export function BadgeStatus({ status, className }: { status: string; className?: string }) {
  const config = configs[status]
    ?? { label: status, classes: 'bg-gray-100 text-gray-700 border-gray-200' };

  return (
    <span className={clsx(
      'inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium border',
      config.classes,
      className
    )}>
      {config.label}
    </span>
  );
}
