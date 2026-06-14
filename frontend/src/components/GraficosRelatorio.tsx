import { useRef, useEffect } from 'react';
import {
  Chart,
  DoughnutController,
  BarController,
  ArcElement,
  BarElement,
  CategoryScale,
  LinearScale,
  Tooltip,
} from 'chart.js';

Chart.register(
  DoughnutController,
  BarController,
  ArcElement,
  BarElement,
  CategoryScale,
  LinearScale,
  Tooltip,
);

interface GraficosRelatorioProps {
  relatorio: {
    boletos?: { valor?: number; pago?: number; pendente?: number; valorPago?: number; valorPendente?: number };
    pix?: { valor?: number; pago?: number; pendente?: number; valorPago?: number; valorPendente?: number };
    cheques?: { valor?: number; compensado?: number; pendente?: number; valorCompensado?: number; valorPendente?: number };
  } | null;
  tipo: string;
}

const CORES_ROSCA = ['#1D9E75', '#185FA5', '#BA7517'];
const CORES_BARRAS = ['#1D9E75', '#185FA5', '#D85A30'];
const LABELS_ROSCA = ['Boletos', 'PIX', 'Cheques'];
const LABELS_BARRAS = ['Pago', 'Compensado', 'Pendente'];

function brl(valor: number) {
  return `R$ ${valor.toLocaleString('pt-BR', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`;
}

function BarChart({ data }: { data: number[] }) {
  const canvasRef = useRef<HTMLCanvasElement>(null);

  useEffect(() => {
    if (!canvasRef.current) return;

    const chart = new Chart(canvasRef.current, {
      type: 'bar',
      data: {
        labels: LABELS_BARRAS,
        datasets: [{
          data,
          backgroundColor: CORES_BARRAS,
          borderRadius: 6,
          borderSkipped: false,
        }],
      },
      options: {
        indexAxis: 'y',
        responsive: true,
        maintainAspectRatio: false,
        plugins: {
          legend: { display: false },
          tooltip: {
            callbacks: {
              label: (ctx) => brl((ctx.parsed as { x: number }).x),
            },
          },
        },
        scales: {
          x: {
            beginAtZero: true,
            ticks: {
              callback: (value) => brl(value as number),
              font: { size: 11 },
            },
            grid: { color: 'rgba(128,128,128,0.1)' },
          },
          y: {
            ticks: { font: { size: 12 } },
            grid: { display: false },
          },
        },
      },
    });

    return () => chart.destroy();
  }, [data]);

  return (
    <>
      <div className="relative w-full" style={{ height: 180 }}>
        <canvas ref={canvasRef} />
      </div>
      <div className="flex flex-wrap gap-2.5 mt-3 text-xs text-slate-500">
        {LABELS_BARRAS.map((label, i) => (
          data[i] > 0 && (
            <span key={label} className="flex items-center gap-1.5">
              <span className="w-2.5 h-2.5 rounded-full" style={{ backgroundColor: CORES_BARRAS[i] }} />
              {label}: <strong>{brl(data[i])}</strong>
            </span>
          )
        ))}
      </div>
    </>
  );
}

export default function GraficosRelatorio({ relatorio, tipo }: GraficosRelatorioProps) {
  const canvasRosca = useRef<HTMLCanvasElement>(null);
  const chartRosca = useRef<Chart<'doughnut'> | null>(null);

  if (!relatorio) return null;

  const valoresTipo = [
    relatorio?.boletos?.valor ?? 0,
    relatorio?.pix?.valor ?? 0,
    relatorio?.cheques?.valor ?? 0,
  ];

  const doughnutData = valoresTipo.map((v, i) => {
    if (tipo === 'TODOS') return v;
    if (tipo === 'BOLETO' && i === 0) return v;
    if (tipo === 'PIX' && i === 1) return v;
    if (tipo === 'CHEQUE' && i === 2) return v;
    return 0;
  });

  const isCheque = tipo === 'CHEQUE' || tipo === 'TODOS';
  const isBoletoPix = tipo === 'BOLETO' || tipo === 'PIX' || tipo === 'TODOS';

  const barData = [
    isBoletoPix ? (relatorio?.boletos?.valorPago ?? 0) + (relatorio?.pix?.valorPago ?? 0) : 0,
    isCheque ? (relatorio?.cheques?.valorCompensado ?? 0) : 0,
    ((tipo === 'TODOS' || tipo === 'BOLETO') ? (relatorio?.boletos?.valorPendente ?? 0) : 0) +
    ((tipo === 'TODOS' || tipo === 'PIX') ? (relatorio?.pix?.valorPendente ?? 0) : 0) +
    (isCheque ? (relatorio?.cheques?.valorPendente ?? 0) : 0),
  ];

  useEffect(() => {
    if (!canvasRosca.current) return;

    if (chartRosca.current) {
      chartRosca.current.data.datasets[0].data = doughnutData;
      chartRosca.current.update();
      return;
    }

    chartRosca.current = new Chart(canvasRosca.current, {
      type: 'doughnut',
      data: {
        labels: LABELS_ROSCA,
        datasets: [{
          data: doughnutData,
          backgroundColor: CORES_ROSCA,
          borderWidth: 0,
        }],
      },
      options: {
        cutout: '65%',
        responsive: true,
        maintainAspectRatio: false,
        plugins: {
          legend: { display: false },
          tooltip: {
            callbacks: {
              label: (ctx) => `${ctx.label}: ${brl(ctx.parsed as number)}`,
            },
          },
        },
      },
    });

    return () => {
      chartRosca.current?.destroy();
      chartRosca.current = null;
    };
  }, [doughnutData]);

  return (
    <div className="flex flex-col gap-3 mt-6">
      <div className="bg-white rounded-2xl border border-slate-100 shadow-sm p-4">
        <h3 className="text-xs font-medium text-slate-400 uppercase tracking-wider mb-3">
          Distribuição por tipo
        </h3>
        <div className="relative w-full" style={{ height: 200 }}>
          <canvas ref={canvasRosca} />
        </div>
        <div className="flex flex-wrap gap-2.5 mt-3 text-xs text-slate-500">
          {LABELS_ROSCA.map((label, i) => (
            doughnutData[i] > 0 && (
              <span key={label} className="flex items-center gap-1.5">
                <span className="w-2.5 h-2.5 rounded-full" style={{ backgroundColor: CORES_ROSCA[i] }} />
                {label}: <strong>{brl(doughnutData[i])}</strong>
              </span>
            )
          ))}
        </div>
      </div>

      <div className="bg-white rounded-2xl border border-slate-100 shadow-sm p-4">
        <h3 className="text-xs font-medium text-slate-400 uppercase tracking-wider mb-3">
          Valores por status
        </h3>
        <BarChart key={JSON.stringify(barData)} data={barData} />
      </div>
    </div>
  );
}
