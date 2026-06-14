import { clsx } from 'clsx';

export function SkeletonLine({ className }: { className?: string }) {
  return (
    <div className={clsx('h-4 bg-slate-200 rounded-md animate-pulse', className)} />
  );
}

export function SkeletonCard() {
  return (
    <div className="bg-white rounded-2xl border border-slate-100 shadow-sm overflow-hidden">
      <div className="h-2 w-full bg-slate-200 animate-pulse" />
      <div className="p-5 space-y-3">
        <div className="flex items-center gap-4">
          <div className="w-12 h-12 rounded-xl bg-slate-200 animate-pulse flex-shrink-0" />
          <div className="flex-1 space-y-2">
            <SkeletonLine className="w-3/4" />
            <SkeletonLine className="w-1/2" />
          </div>
        </div>
      </div>
    </div>
  );
}

export function SkeletonTable({ rows = 5 }: { rows?: number }) {
  return (
    <div className="bg-white rounded-2xl shadow-sm border border-slate-100 overflow-hidden">
      <div className="p-4 border-b border-slate-100">
        <SkeletonLine className="w-1/4 h-5" />
      </div>
      <div className="divide-y divide-slate-50">
        {Array.from({ length: rows }).map((_, i) => (
          <div key={i} className="flex items-center gap-4 p-4">
            <SkeletonLine className="flex-1" />
            <SkeletonLine className="w-20" />
            <SkeletonLine className="w-28" />
            <SkeletonLine className="w-24" />
            <SkeletonLine className="w-32" />
            <SkeletonLine className="w-20" />
          </div>
        ))}
      </div>
    </div>
  );
}
