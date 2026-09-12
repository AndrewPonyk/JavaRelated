export function trackEvent(name, payload = {}) {
  const detail = {
    name,
    payload,
    timestamp: new Date().toISOString()
  };

  window.dispatchEvent(new CustomEvent('analytics:event', { detail }));

  if (typeof window.gtag === 'function') {
    window.gtag('event', name, payload);
  }
}
