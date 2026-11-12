/**
 * Brand colours for the whole portal. Change these values to re-skin it: hover states, light badges,
 * borders and the logo mark are all worked out from them.
 *
 * Keep public/favicon.svg and the email colours (ETT_BRAND_COLOR, ETT_BRAND_HEADING_COLOR) in line.
 */
export const brand = {
  /** Buttons, links, active navigation, scores. */
  primary: '#0a7476',
  /** Highlights on dark backgrounds and the second colour of the logo mark. */
  accent: '#9dd5d5',
  /** Headings, the sign-in panel and the name in the header. */
  heading: '#0b2545',
  /** Body text. */
  text: '#24354b',
  /** Secondary text. */
  muted: '#5c6a7f',
  /** Page background behind cards. */
  surface: '#f4f6f9',
} as const;
