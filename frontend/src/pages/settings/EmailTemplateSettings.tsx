import { Alert, Badge, Button, Card, Grid, Group, NavLink, Paper, ScrollArea, Stack, Text, Textarea, TextInput, Title } from '@mantine/core';
import { IconEye, IconRestore } from '@tabler/icons-react';
import { useRef, useState } from 'react';
import { useEmailTemplates, usePreviewEmail, useResetEmailTemplate, useSaveEmailTemplate } from '../../api/hooks';
import type { EmailTemplate } from '../../api/types';
import { Loading } from '../../components/QueryState';
import { notifyError, notifySuccess } from '../../lib/notify';

/** The emails the portal sends, with the wording each company can change. */
export function EmailTemplateSettings({ admin }: { admin: boolean }) {
  const { data, isLoading } = useEmailTemplates();
  const [selected, setSelected] = useState<string | null>(null);
  if (isLoading || !data) return <Loading />;
  const template = data.find((t) => t.key === selected) ?? data[0];
  return (
    <Grid gap="lg">
      <Grid.Col span={{ base: 12, md: 4 }}>
        <Card padding="xs">
          <Text size="xs" c="dimmed" px="sm" pt="xs" pb={6}>
            {data.length} emails
          </Text>
          <ScrollArea.Autosize mah={560}>
            {data.map((t) => (
              <NavLink
                key={t.key}
                label={t.label}
                description={t.description}
                active={t.key === template?.key}
                onClick={() => setSelected(t.key)}
                rightSection={
                  t.customised ? (
                    <Badge size="xs" variant="light">
                      Changed
                    </Badge>
                  ) : undefined
                }
                styles={{ root: { borderRadius: 6 }, description: { lineClamp: 2 } }}
              />
            ))}
          </ScrollArea.Autosize>
        </Card>
      </Grid.Col>
      <Grid.Col span={{ base: 12, md: 8 }}>{template && <TemplateEditor key={template.key + template.subject + template.body} template={template} admin={admin} />}</Grid.Col>
    </Grid>
  );
}

function TemplateEditor({ template, admin }: { template: EmailTemplate; admin: boolean }) {
  const [subject, setSubject] = useState(template.subject);
  const [body, setBody] = useState(template.body);
  const [preview, setPreview] = useState<{ subject: string; body: string } | null>(null);
  const bodyRef = useRef<HTMLTextAreaElement>(null);
  const save = useSaveEmailTemplate();
  const reset = useResetEmailTemplate();
  const show = usePreviewEmail();
  const dirty = subject !== template.subject || body !== template.body;
  const unknown = [...`${subject} ${body}`.matchAll(/\{\{\s*([a-zA-Z0-9_]+)\s*\}\}/g)]
    .map((m) => m[1])
    .filter((p) => !template.placeholders.includes(p));

  const insert = (placeholder: string) => {
    const token = `{{${placeholder}}}`;
    const el = bodyRef.current;
    if (!el) {
      setBody(body + token);
      return;
    }
    const start = el.selectionStart ?? body.length;
    const end = el.selectionEnd ?? body.length;
    setBody(body.slice(0, start) + token + body.slice(end));
    requestAnimationFrame(() => {
      el.focus();
      el.setSelectionRange(start + token.length, start + token.length);
    });
  };

  return (
    <Card>
      <Group justify="space-between" align="flex-start" mb="sm">
        <div>
          <Title order={4} className="section-title">
            {template.label}
          </Title>
          <Text size="sm" c="dimmed">
            {template.description}
          </Text>
        </div>
        {template.customised && (
          <Badge variant="light" color="ett">
            Your wording
          </Badge>
        )}
      </Group>
      <Stack>
        <TextInput label="Subject" value={subject} onChange={(e) => setSubject(e.currentTarget.value)} maxLength={200} disabled={!admin} />
        <Textarea
          ref={bodyRef}
          label="Message"
          autosize
          minRows={10}
          maxRows={22}
          maxLength={8000}
          value={body}
          onChange={(e) => setBody(e.currentTarget.value)}
          disabled={!admin}
          styles={{ input: { fontSize: 14, lineHeight: 1.55 } }}
        />
        {admin && (
          <div>
            <Text size="xs" c="dimmed" mb={4}>
              Click to insert. Each one is filled in when the email is sent.
            </Text>
            <Group gap={6}>
              {template.placeholders.map((p) => (
                <Badge
                  key={p}
                  variant="outline"
                  color="gray"
                  style={{ cursor: 'pointer', textTransform: 'none', fontFamily: 'var(--mantine-font-family-monospace)' }}
                  onClick={() => insert(p)}
                >
                  {`{{${p}}}`}
                </Badge>
              ))}
            </Group>
          </div>
        )}
        {unknown.length > 0 && (
          <Alert color="red" variant="light">
            <Text size="sm">
              This email can't use {unknown.map((u) => `{{${u}}}`).join(', ')}. Use one of the ones listed above.
            </Text>
          </Alert>
        )}
        {preview && (
          <Paper withBorder radius="md" p="md" bg="gray.0">
            <Text size="xs" c="dimmed" mb={4}>
              Preview with sample details
            </Text>
            <Text fw={600} size="sm" mb="xs">
              {preview.subject}
            </Text>
            <Text size="sm" style={{ whiteSpace: 'pre-wrap' }}>
              {preview.body}
            </Text>
          </Paper>
        )}
        <Group justify="space-between">
          <Group gap="xs">
            <Button
              variant="default"
              leftSection={<IconEye size={16} />}
              loading={show.isPending}
              disabled={unknown.length > 0}
              onClick={() => show.mutate({ key: template.key, subject, body }, { onSuccess: setPreview, onError: (e) => notifyError(e) })}
            >
              Preview
            </Button>
            {admin && template.customised && (
              <Button
                variant="subtle"
                color="gray"
                leftSection={<IconRestore size={16} />}
                loading={reset.isPending}
                onClick={() =>
                  reset.mutate(template.key, {
                    onSuccess: () => notifySuccess('Back to the standard wording.'),
                    onError: (e) => notifyError(e),
                  })
                }
              >
                Use standard wording
              </Button>
            )}
          </Group>
          {admin && (
            <Group gap="xs">
              {dirty && (
                <Button
                  variant="subtle"
                  color="gray"
                  onClick={() => {
                    setSubject(template.subject);
                    setBody(template.body);
                  }}
                >
                  Undo changes
                </Button>
              )}
              <Button
                disabled={!dirty || unknown.length > 0 || !subject.trim() || !body.trim()}
                loading={save.isPending}
                onClick={() =>
                  save.mutate(
                    { key: template.key, subject, body },
                    { onSuccess: () => notifySuccess('Email saved. It is used from now on.'), onError: (e) => notifyError(e) },
                  )
                }
              >
                Save email
              </Button>
            </Group>
          )}
        </Group>
      </Stack>
    </Card>
  );
}
