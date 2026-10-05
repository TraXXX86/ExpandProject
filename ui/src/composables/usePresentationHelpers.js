/** Model-aware labels, formatting and payload normalization. */
export function usePresentationHelpers(ctx) {
  function getFirstFile(value) {
    if (!value) {
      return null;
    }
    if (Array.isArray(value)) {
      return value[0] || null;
    }
    return value;
  }

  function resolveAttributeLabel(definition, language, fallbackLanguage) {
    if (!definition || !definition.labels) {
      return '';
    }
    const labels = definition.labels;
    const requested = language || ctx.displayLanguage.value;
    const fallback = fallbackLanguage || ctx.defaultLanguage.value;
    if (requested && labels[requested]) {
      return labels[requested];
    }
    if (fallback && labels[fallback]) {
      return labels[fallback];
    }
    return '';
  }

  function formatAttributeLabel(name, definition) {
    const label = resolveAttributeLabel(definition);
    return label || name || '';
  }

  function sanitizeMaterialIconName(iconName) {
    if (!iconName) {
      return '';
    }
    const normalized = String(iconName).trim().toLowerCase().replace(/[\s-]+/g, '_');
    if (!normalized || !/^[a-z0-9_]+$/.test(normalized)) {
      return '';
    }
    return normalized;
  }

  function getTypeIconName(typeName) {
    if (!typeName) {
      return 'category';
    }
    const typeDef = ctx.modelDetails.value.objectTypes.find((type) => type.name === typeName);
    const iconName = sanitizeMaterialIconName(typeDef?.icon);
    return iconName || 'category';
  }

  function getAttributeDefinition(typeName, attributeName) {
    if (!typeName || !attributeName) {
      return null;
    }
    const typeDef = ctx.modelDetails.value.objectTypes.find((type) => type.name === typeName);
    if (!typeDef || !Array.isArray(typeDef.attributes)) {
      return null;
    }
    return typeDef.attributes.find((attr) => attr.name === attributeName) || null;
  }

  function getAttributeLabel(typeName, attributeName) {
    const definition = getAttributeDefinition(typeName, attributeName);
    const label = resolveAttributeLabel(definition);
    return label || attributeName || '';
  }

  function getLinkTypeDefinition(linkTypeName) {
    if (!linkTypeName) {
      return null;
    }
    return ctx.modelDetails.value.linkTypes.find((link) => link.name === linkTypeName) || null;
  }

  function getLinkTypeLabel(linkTypeName, direction = 'both') {
    if (!linkTypeName) {
      return '';
    }
    const definition = getLinkTypeDefinition(linkTypeName);
    if (!definition) {
      return linkTypeName;
    }

    const sourceLabel = resolveAttributeLabel({
      labels: definition.sourceLabels || {}
    });
    const targetLabel = resolveAttributeLabel({
      labels: definition.targetLabels || {}
    });
    const commonLabel = resolveAttributeLabel({
      labels: definition.labels || {}
    });

    if (direction === 'out' && sourceLabel) {
      return sourceLabel;
    }
    if (direction === 'in' && targetLabel) {
      return targetLabel;
    }
    if (commonLabel) {
      return commonLabel;
    }
    if (direction === 'out' && sourceLabel) {
      return sourceLabel;
    }
    if (direction === 'in' && targetLabel) {
      return targetLabel;
    }
    return linkTypeName;
  }

  function getUserPortalTitle() {
    const labels = ctx.modelDetails.value?.userPortalLabels || {};
    const requested = ctx.displayLanguage.value;
    const fallback = ctx.defaultLanguage.value;

    if (requested && labels[requested]) {
      return labels[requested];
    }
    if (fallback && labels[fallback]) {
      return labels[fallback];
    }

    const firstLabel = Object.values(labels).find((value) => {
      if (value === null || value === undefined) {
        return false;
      }
      return String(value).trim().length > 0;
    });
    if (firstLabel) {
      return String(firstLabel);
    }

    if (ctx.selectedModel.value?.name) {
      return ctx.selectedModel.value.name;
    }
    return 'Portail métier';
  }

  function getRepresentativeAttributeKeys(typeName) {
    if (!typeName) {
      return [];
    }
    const refs = ctx.representativeAttributesByType.value.get(typeName);
    return Array.isArray(refs) ? refs : [];
  }

  function getObjectPrimaryAttributes(object) {
    if (!object) {
      return [];
    }
    const keys = getRepresentativeAttributeKeys(object.type);
    if (!keys.length) {
      return [];
    }
    const attributes = Array.isArray(object.attributes) ? object.attributes : [];
    const map = new Map(attributes.map((attr) => [attr.key, attr.value]));
    return keys
      .map((key) => ({
        key,
        label: getAttributeLabel(object.type, key),
        value: map.get(key)
      }))
      .filter((attr) => attr.value !== undefined && attr.value !== null && String(attr.value).trim() !== '');
  }

  function getObjectPrimaryLabel(object) {
    const primaryAttributes = getObjectPrimaryAttributes(object);
    if (!primaryAttributes.length) {
      return '';
    }
    const values = primaryAttributes
      .map((attr) => String(attr.value).trim())
      .filter(Boolean);
    return values.join(' ');
  }

  function formatObjectOptionLabel(object) {
    if (!object) {
      return '';
    }
    const label = getObjectPrimaryLabel(object);
    const type = object.type || 'Objet';
    const id = object.id ?? 'N/A';
    if (label) {
      return `${label} • ${type} #${id}`;
    }
    return `${type} #${id}`;
  }

  function isTypeOrSubtype(candidate, allowed) {
    if (!candidate || !allowed) {
      return false;
    }
    if (candidate === allowed) {
      return true;
    }
    const visited = new Set();
    let current = ctx.modelTypeIndex.value.get(candidate);
    while (current && current.parent) {
      const parent = current.parent;
      if (!parent || visited.has(parent)) {
        return false;
      }
      if (parent === allowed) {
        return true;
      }
      visited.add(parent);
      current = ctx.modelTypeIndex.value.get(parent);
    }
    return false;
  }

  function isTypeAllowed(candidate, allowedTypes) {
    if (!candidate || !Array.isArray(allowedTypes) || !allowedTypes.length) {
      return false;
    }
    return allowedTypes.some((allowed) => isTypeOrSubtype(candidate, allowed));
  }

  async function readJson(response) {
    try {
      return await response.json();
    } catch (error) {
      return null;
    }
  }

  function extractErrorMessage(payload, fallback) {
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

  return { getFirstFile, resolveAttributeLabel, formatAttributeLabel, sanitizeMaterialIconName, getTypeIconName, getAttributeDefinition, getAttributeLabel, getLinkTypeDefinition, getLinkTypeLabel, getUserPortalTitle, getRepresentativeAttributeKeys, getObjectPrimaryAttributes, getObjectPrimaryLabel, formatObjectOptionLabel, isTypeOrSubtype, isTypeAllowed, readJson, extractErrorMessage };
}
