import { createTheme, type CSSVariablesResolver } from '@mantine/core';
import { brand } from './brand';
import { shades } from './lib/shades';

export const theme = createTheme({
  primaryColor: 'ett',
  primaryShade: 6,
  colors: {
    ett: shades(brand.primary, 6),
    accent: shades(brand.accent, 4),
    navy: shades(brand.heading, 9),
  },
  fontFamily: "'IBM Plex Sans', system-ui, -apple-system, 'Segoe UI', sans-serif",
  fontFamilyMonospace: "'IBM Plex Mono', ui-monospace, monospace",
  headings: {
    fontFamily: "'IBM Plex Sans', system-ui, sans-serif",
    fontWeight: '600',
  },
  defaultRadius: 'md',
  cursorType: 'pointer',
  components: {
    Button: { defaultProps: { radius: 'md' } },
    Card: { defaultProps: { withBorder: true, radius: 'lg', padding: 'lg' } },
    Paper: { defaultProps: { radius: 'lg' } },
    Badge: { defaultProps: { radius: 'sm', variant: 'light' } },
    Table: { defaultProps: { verticalSpacing: 'sm', highlightOnHover: true } },
  },
});

/** Exposes the brand colours to plain CSS (styles.css). */
export const cssVariablesResolver: CSSVariablesResolver = () => ({
  variables: {
    '--brand-heading': brand.heading,
    '--brand-text': brand.text,
    '--brand-muted': brand.muted,
    '--brand-surface': brand.surface,
  },
  light: {},
  dark: {},
});
