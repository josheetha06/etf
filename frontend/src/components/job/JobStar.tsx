import { ActionIcon, Tooltip } from '@mantine/core';
import { IconStar, IconStarFilled } from '@tabler/icons-react';
import { useStarJob } from '../../api/hooks';
import { notifyError } from '../../lib/notify';

/** A personal star: starred jobs come first and have their own tab. Other people don't see your stars. */
export function JobStar({ jobId, starred, size = 'md' }: { jobId: string; starred: boolean; size?: 'sm' | 'md' | 'lg' }) {
  const star = useStarJob();
  const label = starred ? 'Remove star' : 'Star this job (only you see your stars)';
  return (
    <Tooltip label={label}>
      <ActionIcon
        variant="subtle"
        color={starred ? 'yellow' : 'gray'}
        size={size}
        aria-label={label}
        aria-pressed={starred}
        loading={star.isPending}
        onClick={(e) => {
          e.stopPropagation();
          star.mutate({ id: jobId, starred: !starred }, { onError: (err) => notifyError(err, "The star wasn't saved") });
        }}
        onKeyDown={(e) => e.stopPropagation()}
      >
        {starred ? <IconStarFilled size={size === 'lg' ? 22 : 18} /> : <IconStar size={size === 'lg' ? 22 : 18} />}
      </ActionIcon>
    </Tooltip>
  );
}
