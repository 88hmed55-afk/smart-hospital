import { HeartPulse } from 'lucide-react';

export default function Brand({ compact = false }: { compact?: boolean }) {
  return (
    <div className={`brand ${compact ? 'brand-compact' : ''}`}>
      <div className="brand-mark" aria-hidden="true">
        <HeartPulse size={24} strokeWidth={1.9} />
      </div>
      <div className="brand-copy">
        <strong>SMART HOSPITAL</strong>
        {!compact && <small>منظومة إدارة المستشفى الذكية</small>}
      </div>
    </div>
  );
}
