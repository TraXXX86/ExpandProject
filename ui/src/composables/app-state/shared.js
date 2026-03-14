export function getFirstFile(value) {
  if (!value) {
    return null;
  }
  if (Array.isArray(value)) {
    return value[0] || null;
  }
  return value;
}

export async function readJson(response) {
  try {
    return await response.json();
  } catch (error) {
    return null;
  }
}

export function extractErrorMessage(payload, fallback) {
  if (payload?.error) {
    return payload.error;
  }
  const errors = Array.isArray(payload?.errors) ? payload.errors : [];
  if (errors.length) {
    return errors
      .map((err) => err?.message || err?.toString?.() || 'Erreur de validation')
      .join(' • ');
  }
  return fallback;
}
