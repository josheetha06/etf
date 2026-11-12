import { Card, Text, Timeline } from '@mantine/core';
import { IconRobot, IconUser, IconUserCheck } from '@tabler/icons-react';
import { useTimeline } from '../../api/hooks';
import { Loading } from '../../components/QueryState';
import { istLong } from '../../lib/format';

/** Everything that happened to this application, newest first. */
export function TimelineTab({ applicationId }: { applicationId: string }) {
  const { data, isLoading } = useTimeline(applicationId);
  if (isLoading) return <Loading />;
  return (
    <Card>
      <Timeline bulletSize={26} lineWidth={2}>
        {data?.map((event, index) => (
          <Timeline.Item
            key={`${event.at}-${index}`}
            bullet={event.actorKind === 'system' ? <IconRobot size={14} /> : event.actorKind === 'candidate' ? <IconUserCheck size={14} /> : <IconUser size={14} />}
            color={event.actorKind === 'system' ? 'violet' : 'ett'}
            title={
              <Text size="sm">
                <b>{event.actorName}</b> {event.text}
              </Text>
            }
          >
            <Text size="xs" c="dimmed">
              {istLong(event.at)}
            </Text>
          </Timeline.Item>
        ))}
      </Timeline>
    </Card>
  );
}
