import type { MantineColorsTuple } from '@mantine/core';

type Hsl = [number, number, number];

function hexToHsl(hex: string): Hsl {
  const value = hex.replace('#', '');
  const full = value.length === 3 ? [...value].map((c) => c + c).join('') : value;
  const [r, g, b] = [0, 2, 4].map((i) => parseInt(full.slice(i, i + 2), 16) / 255);
  const max = Math.max(r, g, b);
  const min = Math.min(r, g, b);
  const l = (max + min) / 2;
  if (max === min) return [0, 0, l];
  const d = max - min;
  const s = l > 0.5 ? d / (2 - max - min) : d / (max + min);
  const h = max === r ? (g - b) / d + (g < b ? 6 : 0) : max === g ? (b - r) / d + 2 : (r - g) / d + 4;
  return [h * 60, s, l];
}

function hslToHex([h, s, l]: Hsl): string {
  const k = (n: number) => (n + h / 30) % 12;
  const a = s * Math.min(l, 1 - l);
  const channel = (n: number) => l - a * Math.max(-1, Math.min(k(n) - 3, Math.min(9 - k(n), 1)));
  return '#' + [0, 8, 4].map((n) => Math.round(channel(n) * 255).toString(16).padStart(2, '0')).join('');
}

/**
 * Builds the ten shades Mantine expects from one brand colour, keeping its hue. The colour itself sits at
 * `index`: lighter shades rise towards a soft tint and lose some saturation, darker shades deepen.
 */
export function shades(hex: string, index = 6): MantineColorsTuple {
  const [h, s, l] = hexToHsl(hex);
  const curve = 0.15 * index;
  const result: string[] = [];
  for (let i = 0; i < 10; i++) {
    if (i < index) {
      const t = (index - i) / index;
      result.push(hslToHex([h, s * (0.48 + 0.52 * (1 - t) ** 6), l + (0.95 - l) * t ** curve]));
    } else if (i === index) {
      result.push(hex.toLowerCase());
    } else {
      result.push(hslToHex([h, s, l * (1 - 0.14 * (i - index))]));
    }
  }
  return result as unknown as MantineColorsTuple;
}
