export function ValorMonetario({ valor, className }: { valor: number; className?: string }) {
  return (
    <span className={className}>
      {new Intl.NumberFormat('pt-BR', {
        style: 'currency',
        currency: 'BRL'
      }).format(valor)}
    </span>
  );
}
