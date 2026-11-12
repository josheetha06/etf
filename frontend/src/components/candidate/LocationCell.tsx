import { Badge, Text, Tooltip } from '@mantine/core';
import type { LocationSummary } from '../../api/types';
import { locationSource, locationStatus } from '../../lib/labels';

/** A candidate's location in a list: the place, and where it came from (CV, AI interview or HR). */
export function LocationCell({ location }: { location: LocationSummary | null | undefined }) {
  if (!location) return <Text size="sm">—</Text>;
  if (location.status === 'known') {
    return (
      <Tooltip label={`“${location.text}”`} disabled={!location.text || location.text === location.label} withinPortal>
        <div>
          <Text size="sm" lineClamp={1}>
            {location.label}
          </Text>
          {location.source && (
            <Text size="xs" c={location.source === 'ai_interview' ? 'violet.7' : 'dimmed'}>
              {locationSource[location.source]}
            </Text>
          )}
        </div>
      </Tooltip>
    );
  }
  const tone = locationStatus[location.status];
  return (
    <div>
      {location.text && (
        <Text size="sm" lineClamp={1}>
          {location.text}
        </Text>
      )}
      <Badge size="xs" variant="light" color={tone.color} style={{ whiteSpace: 'nowrap' }}>
        {tone.label}
      </Badge>
    </div>
  );
}
