import { ChevronLeft, ChevronRight } from 'lucide-react';
import { clsx } from 'clsx';

interface PaginationProps {
  page: number;
  totalPages: number;
  total: number;
  pageSize: number;
  onChange: (page: number) => void;
}

export function Pagination({ page, totalPages, total, pageSize, onChange }: PaginationProps) {
  if (totalPages <= 1) return null;

  return (
    <div className="flex items-center justify-between px-4 py-3 bg-white border-t border-slate-100">
      <span className="text-xs text-slate-500">
        {total} registro{total !== 1 ? 's' : ''}
      </span>
      <div className="flex items-center gap-1">
        <button
          onClick={() => onChange(page - 1)}
          disabled={page <= 0}
          className={clsx(
            'p-1.5 rounded-lg transition-colors',
            page <= 0
              ? 'text-slate-300 cursor-not-allowed'
              : 'text-slate-500 hover:bg-slate-100'
          )}
        >
          <ChevronLeft className="w-4 h-4" />
        </button>
        {Array.from({ length: totalPages }).map((_, i) => (
          <button
            key={i}
            onClick={() => onChange(i)}
            className={clsx(
              'min-w-[32px] h-8 rounded-lg text-sm font-medium transition-colors',
              i === page
                ? 'bg-[#0c4a6e] text-white'
                : 'text-slate-600 hover:bg-slate-100'
            )}
          >
            {i + 1}
          </button>
        ))}
        <button
          onClick={() => onChange(page + 1)}
          disabled={page >= totalPages - 1}
          className={clsx(
            'p-1.5 rounded-lg transition-colors',
            page >= totalPages - 1
              ? 'text-slate-300 cursor-not-allowed'
              : 'text-slate-500 hover:bg-slate-100'
          )}
        >
          <ChevronRight className="w-4 h-4" />
        </button>
      </div>
    </div>
  );
}
