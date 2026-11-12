import { Card } from '@mantine/core';
import { useNavigate, useParams } from 'react-router';
import { useReschedule } from '../api/hooks';
import { RescheduleForm } from '../components/candidate/RescheduleModal';
import { PageHeader } from '../components/PageHeader';

/** A candidate's request for a different time, on its own page (linked from emails and older notifications). */
export function ReschedulePage() {
  const { requestId = '' } = useParams();
  const navigate = useNavigate();
  const { data } = useReschedule(requestId);
  return (
    <>
      <PageHeader eyebrow="Interviews" title="Request for a different time" />
      <Card maw={900}>
        <RescheduleForm
          requestId={requestId}
          onClose={() => navigate(data ? `/applications/${data.interview.applicationId}` : '/interviews?tab=requests')}
        />
      </Card>
    </>
  );
}
