const API_BASE_URL = process.env.API_BASE_URL ?? "https://api.inkvpn.com";

export function subscriptionUrl(subscriptionKey: string): string {
  return `${API_BASE_URL}/sub/${subscriptionKey}`;
}

/** Deep link that opens InkVPN and imports the subscription. */
export function generateImportDeepLink(subscriptionKey: string): string {
  return `inkvpn://add/${subscriptionUrl(subscriptionKey)}`;
}

export function fallbackInstallUrl(subscriptionKey: string): string {
  const base = process.env.APP_DOWNLOAD_URL ?? "https://inkvpn.com/install";
  return `${base}?key=${subscriptionKey}`;
}
