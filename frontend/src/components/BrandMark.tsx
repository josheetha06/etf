import { brand } from '../brand';

/** The ETT logo mark, drawn in the brand colours. */
export function BrandMark({ size = 30 }: { size?: number }) {
  return (
    <svg width={size} height={size} viewBox="0 0 64 64" role="img" aria-label="Epic Talent Trust">
      <rect width="64" height="64" rx="14" fill={brand.primary} />
      <path d="M18 20h20v6H25v4h11v6H25v4h13v6H18z" fill="#fff" />
      <path d="M41 20h7v26h-7z" fill={brand.accent} />
    </svg>
  );
}
