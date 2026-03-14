export const userPortalPages = ['navigate', 'table', 'search', 'create', 'import-data'];
export const modelAdminPortalPages = ['model', 'import-model', 'admin'];

export function normalizePortal(portal) {
  if (portal === 'user' || portal === 'data') {
    return 'user';
  }
  if (portal === 'model-admin' || portal === 'admin') {
    return 'model-admin';
  }
  return '';
}

export function getPortalPages(portal) {
  if (portal === 'user') {
    return userPortalPages;
  }
  if (portal === 'model-admin') {
    return modelAdminPortalPages;
  }
  return [];
}

export function getPortalDefaultPage(portal) {
  if (portal === 'user') {
    return 'navigate';
  }
  if (portal === 'model-admin') {
    return 'model';
  }
  return 'navigate';
}

export function normalizePageForPortal(portal, page) {
  const pages = getPortalPages(portal);
  if (!pages.length) {
    return '';
  }
  return pages.includes(page) ? page : getPortalDefaultPage(portal);
}

export function parseRouteState(route) {
  const routeName = String(route?.name || '');
  const portal = normalizePortal(firstValue(route?.params?.portal));
  const page = routeName === 'portal-page'
    ? normalizePageForPortal(portal, firstValue(route?.params?.page))
    : '';

  return {
    screen: routeName,
    portal,
    page,
    modelKey: cleanString(route?.query?.model),
    language: cleanString(route?.query?.lang),
    rootObjectKey: cleanString(route?.query?.root),
    tableSearch: cleanString(route?.query?.tableSearch),
    tableTypes: cleanArray(route?.query?.tableTypes),
    tableAttributeKey: cleanString(route?.query?.tableAttributeKey),
    tableAttributeKeyOperator: cleanString(route?.query?.tableAttributeKeyOperator),
    tableAttributeValue: cleanString(route?.query?.tableAttributeValue),
    tableAttributeValueOperator: cleanString(route?.query?.tableAttributeValueOperator),
    fullTextQuery: cleanString(route?.query?.fullTextQuery),
    fullTextTypes: cleanArray(route?.query?.fullTextTypes)
  };
}

export function buildRouteLocation(state) {
  if (!state?.isAuthenticated) {
    return { name: 'login' };
  }

  const portal = normalizePortal(state?.activePortal);
  if (!portal) {
    return { name: 'portal-selector' };
  }

  const page = normalizePageForPortal(portal, cleanString(state?.currentPage));
  const query = {};

  appendQueryValue(query, 'model', state?.selectedModelKey);
  appendQueryValue(query, 'lang', state?.displayLanguage);

  if (page === 'navigate') {
    appendQueryValue(query, 'root', state?.selectedRootObjectKey);
  }

  if (page === 'table') {
    appendQueryValue(query, 'tableSearch', state?.tableSearch);
    appendQueryArray(query, 'tableTypes', state?.tableTypeFilter);
    appendQueryValue(query, 'tableAttributeKey', state?.tableAttributeKey);
    appendQueryValue(query, 'tableAttributeKeyOperator', state?.tableAttributeKeyOperator, 'contains');
    appendQueryValue(query, 'tableAttributeValue', state?.tableAttributeValue);
    appendQueryValue(query, 'tableAttributeValueOperator', state?.tableAttributeValueOperator, 'contains');
  }

  if (page === 'search') {
    appendQueryValue(query, 'fullTextQuery', state?.fullTextQuery);
    appendQueryArray(query, 'fullTextTypes', state?.fullTextTypeFilter);
  }

  return {
    name: 'portal-page',
    params: {
      portal,
      page
    },
    query
  };
}

function appendQueryValue(query, key, value, defaultValue = '') {
  const normalized = cleanString(value);
  if (!normalized || normalized === defaultValue) {
    return;
  }
  query[key] = normalized;
}

function appendQueryArray(query, key, value) {
  const normalized = cleanArray(value);
  if (!normalized.length) {
    return;
  }
  query[key] = normalized.join(',');
}

function cleanString(value) {
  const normalized = firstValue(value).trim();
  return normalized;
}

function cleanArray(value) {
  if (Array.isArray(value)) {
    return value
      .flatMap((entry) => String(entry || '').split(','))
      .map((entry) => entry.trim())
      .filter(Boolean);
  }

  const normalized = cleanString(value);
  if (!normalized) {
    return [];
  }

  return normalized
    .split(',')
    .map((entry) => entry.trim())
    .filter(Boolean);
}

function firstValue(value) {
  if (Array.isArray(value)) {
    return String(value[0] || '');
  }
  return String(value || '');
}
