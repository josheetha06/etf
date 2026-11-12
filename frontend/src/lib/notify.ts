import { notifications } from '@mantine/notifications';
import { ApiError, errorReference } from '../api/client';

export function notifySuccess(message: string, title?: string) {
  notifications.show({ color: 'ett', title, message, autoClose: 4000 });
}

/** Shows what went wrong in plain words. Field problems are listed; errors on our side carry a reference. */
export function notifyError(error: unknown, title = "That didn't work") {
  let message = error instanceof Error ? error.message : 'Something went wrong. Please try again.';
  if (error instanceof ApiError && Object.keys(error.fields).length > 0 && error.code === 'validation_failed') {
    message = Object.values(error.fields).slice(0, 3).join(' ');
  }
  const reference = errorReference(error);
  if (reference) {
    message += ` (Reference ${reference})`;
  }
  notifications.show({ color: 'red', title, message, autoClose: 8000 });
}
