import {
  Alert,
  Anchor,
  Box,
  Button,
  Code,
  Grid,
  Group,
  List,
  Paper,
  PasswordInput,
  PinInput,
  Stack,
  Text,
  TextInput,
  ThemeIcon,
  Title,
} from '@mantine/core';
import { useForm } from '@mantine/form';
import { IconCheck, IconShieldLock } from '@tabler/icons-react';
import { QRCodeSVG } from 'qrcode.react';
import { useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { Navigate, useLocation, useNavigate } from 'react-router';
import { useLogin, useMe, usePublicBranding, useVerifyCode } from '../api/hooks';
import type { PasswordStepResult } from '../api/types';
import { BrandMark } from '../components/BrandMark';
import { ClientLogo } from '../components/ClientLogo';

export function LoginPage() {
  const { data: me } = useMe();
  const navigate = useNavigate();
  const location = useLocation();
  const login = useLogin();
  const { data: branding } = usePublicBranding();
  const verify = useVerifyCode();
  const client = useQueryClient();
  const [step, setStep] = useState<PasswordStepResult | null>(null);
  const [code, setCode] = useState('');
  const form = useForm({
    initialValues: { email: '', password: '' },
    validate: {
      email: (value) => (/^\S+@\S+\.\S+$/.test(value) ? null : 'Enter your work email'),
      password: (value) => (value ? null : 'Enter your password'),
    },
  });

  if (me) {
    return <Navigate to={(location.state as { from?: string } | null)?.from ?? '/'} replace />;
  }

  const submitPassword = form.onSubmit((values) => {
    if (login.isPending) return;
    login.mutate(values, {
      onSuccess: async (result) => {
        if (result.nextStep === 'done') {
          // The panel login lands on /panel: RequireAuth sends it there from any other page.
          await client.invalidateQueries({ queryKey: ['me'] });
          navigate((location.state as { from?: string } | null)?.from ?? '/', { replace: true });
        } else {
          setStep(result);
        }
      },
    });
  });

  const submitCode = (value: string) => {
    verify.mutate(value, {
      onSuccess: () => navigate((location.state as { from?: string } | null)?.from ?? '/', { replace: true }),
      onError: () => setCode(''),
    });
  };

  return (
    <Grid gap={0} mih="100vh">
      <Grid.Col span={{ base: 12, md: 5 }} visibleFrom="md" bg="navy.9" p={48} mih="100vh">
        <Stack justify="space-between" h="100%" c="white">
          <Group gap="sm">
            <BrandMark size={36} />
            <Text fw={700} size="lg">
              Epic Talent Trust
            </Text>
          </Group>
          <Stack gap="lg">
            <Title order={1} className="page-title" c="white" fz={40}>
              Verified Talent. Trusted Interviews.
            </Title>
            <Text c="gray.3" size="lg">
              Hire from verified candidates. The AI agent does the first pass; your team makes every decision.
            </Text>
            <List spacing="sm" c="gray.2" icon={<ThemeIcon color="ett.4" radius="xl" size={22}><IconCheck size={14} /></ThemeIcon>}>
              <List.Item>AI shortlisting that your team confirms</List.Item>
              <List.Item>Identity checked twice: on camera and in person</List.Item>
              <List.Item>A clear report after every round</List.Item>
            </List>
          </Stack>
          <Text size="xs" c="gray.5">
            © Epic Talent Trust. Authorised users only.
          </Text>
        </Stack>
      </Grid.Col>
      <Grid.Col span={{ base: 12, md: 7 }}>
        <Box maw={440} mx="auto" px="md" py={{ base: 40, md: 96 }}>
          {branding?.companyName && (
            <Group justify="center" mb="xl">
              <ClientLogo branding={branding} height={52} publicLogo />
            </Group>
          )}
          <Paper p={{ base: 'lg', sm: 32 }} withBorder shadow="sm">
            {!step && (
              <form onSubmit={submitPassword} noValidate>
                <Stack>
                  <div>
                    <Title order={2} className="page-title">
                      Sign in to the hiring portal
                    </Title>
                    <Text c="dimmed" size="sm" mt={4}>
                      Use your work email. Two-step verification is on for every HR account.
                    </Text>
                  </div>
                  {login.error && <Alert color="red">{login.error.message}</Alert>}
                  <TextInput label="Work email" type="email" autoComplete="username" {...form.getInputProps('email')} />
                  <PasswordInput label="Password" autoComplete="current-password" {...form.getInputProps('password')} />
                  <Button type="submit" loading={login.isPending} fullWidth size="md">
                    Continue
                  </Button>
                  <Text size="xs" c="dimmed" ta="center">
                    Forgotten your password? Ask your HR admin or ETT support to reset it.
                  </Text>
                </Stack>
              </form>
            )}

            {step && (
              <Stack>
                <Group gap="sm">
                  <ThemeIcon size={40} radius="xl" variant="light">
                    <IconShieldLock size={22} />
                  </ThemeIcon>
                  <div>
                    <Title order={3}>{step.nextStep === 'totp_setup' ? 'Set up two-step verification' : 'Enter your code'}</Title>
                    <Text size="sm" c="dimmed">
                      {form.values.email}
                    </Text>
                  </div>
                </Group>
                {step.nextStep === 'totp_setup' && step.otpauthUri && (
                  <Stack gap="xs">
                    <Text size="sm">
                      Scan this with an authenticator app such as Google Authenticator, Microsoft Authenticator or 1Password.
                    </Text>
                    <Group justify="center" py="xs">
                      <QRCodeSVG value={step.otpauthUri} size={168} />
                    </Group>
                    <Text size="xs" c="dimmed">
                      Can't scan? Enter this key in the app:
                    </Text>
                    <Code block style={{ letterSpacing: 1, textAlign: 'center', whiteSpace: 'pre-wrap', wordBreak: 'break-word' }}>
                      {step.secret?.match(/.{1,4}/g)?.join(' ')}
                    </Code>
                  </Stack>
                )}
                <Text size="sm">Enter the 6-digit code from your authenticator app.</Text>
                {verify.error && <Alert color="red">{verify.error.message}</Alert>}
                <Group justify="center">
                  <PinInput
                    length={6}
                    type="number"
                    oneTimeCode
                    autoFocus
                    value={code}
                    onChange={setCode}
                    onComplete={submitCode}
                    disabled={verify.isPending}
                    aria-label="Six-digit code"
                  />
                </Group>
                <Button onClick={() => submitCode(code)} disabled={code.length !== 6} loading={verify.isPending} fullWidth>
                  Sign in
                </Button>
                <Anchor
                  size="sm"
                  ta="center"
                  component="button"
                  type="button"
                  onClick={() => {
                    setStep(null);
                    setCode('');
                  }}
                >
                  Use a different account
                </Anchor>
              </Stack>
            )}
          </Paper>
        </Box>
      </Grid.Col>
    </Grid>
  );
}
