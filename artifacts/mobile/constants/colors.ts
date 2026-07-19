/**
 * Semantic design tokens for the mobile app.
 *
 * These tokens mirror the naming conventions used in web artifacts (index.css)
 * so that multi-artifact projects share a cohesive visual identity.
 *
 * Replace the placeholder values below with values that match the project's
 * brand. If a sibling web artifact exists, read its index.css and convert the
 * HSL values to hex so both artifacts use the same palette.
 *
 * To add dark mode, add a `dark` key with the same token names.
 * The useColors() hook will automatically pick it up.
 */

const colors = {
  light: {
    background: '#0A1628',
    foreground: '#F0F4FF',
    card: '#12213A',
    cardForeground: '#F0F4FF',
    primary: '#F59E0B',
    primaryForeground: '#0A1628',
    secondary: '#1E3A5F',
    secondaryForeground: '#F0F4FF',
    muted: '#1A2E4A',
    mutedForeground: '#8BA3C7',
    accent: '#3B82F6',
    accentForeground: '#FFFFFF',
    destructive: '#EF4444',
    destructiveForeground: '#FFFFFF',
    border: '#1E3A5F',
    input: '#1E3A5F',
    tint: '#F59E0B',
    text: '#F0F4FF',
  },
  dark: {
    background: '#0A1628',
    foreground: '#F0F4FF',
    card: '#12213A',
    cardForeground: '#F0F4FF',
    primary: '#F59E0B',
    primaryForeground: '#0A1628',
    secondary: '#1E3A5F',
    secondaryForeground: '#F0F4FF',
    muted: '#1A2E4A',
    mutedForeground: '#8BA3C7',
    accent: '#3B82F6',
    accentForeground: '#FFFFFF',
    destructive: '#EF4444',
    destructiveForeground: '#FFFFFF',
    border: '#1E3A5F',
    input: '#1E3A5F',
    tint: '#F59E0B',
    text: '#F0F4FF',
  },
  radius: 12,
};

export default colors;
