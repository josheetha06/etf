import { Button } from '@mantine/core';
import { Link } from 'react-router';
import { Empty } from '../components/QueryState';

export function NotFoundPage() {
  return (
    <Empty
      title="We couldn't find that page"
      action={
        <Button component={Link} to="/" variant="light">
          Back to overview
        </Button>
      }
    >
      The link may be out of date, or the record belongs to another company.
    </Empty>
  );
}
