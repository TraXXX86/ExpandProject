import { computed } from 'vue';

/** Derived views of the shared reactive model, without network side effects. */
export function useDataViews(ctx) {
  const dataRequestQuery = computed(() => {
    if (ctx.currentPage.value === 'table') return { searchMode: ctx.searchMode.value, q: ctx.tableSearch.value || '', type: (ctx.tableTypeFilter.value || []).join(',') };
    if (ctx.currentPage.value === 'search') return { searchMode: ctx.searchMode.value, q: ctx.fullTextQuery.value || '', type: (ctx.fullTextTypeFilter.value || []).join(',') };
    return { searchMode: ctx.searchMode.value, q: ctx.dataQuery.value || '', type: ctx.dataType.value || '' };
  });

  const createObjectTypeOptions = computed(() =>
    ctx.modelDetails.value.objectTypes
      .map((type) => ({ title: type.name, value: type.name }))
      .sort((a, b) => a.title.localeCompare(b.title))
  );

  const createLinkTypeOptions = computed(() =>
    ctx.modelDetails.value.linkTypes
      .map((link) => {
        const label = ctx.getLinkTypeLabel(link.name);
        return {
          title: label === link.name ? link.name : `${label} (${link.name})`,
          value: link.name
        };
      })
      .sort((a, b) => a.title.localeCompare(b.title))
  );

  const filteredObjects = computed(() => {
    const filter = ctx.objectFilter.value.trim().toLowerCase();
    if (!filter) {
      return ctx.dataObjects.value;
    }
    return ctx.dataObjects.value.filter((object) => {
      const id = object.id !== null && object.id !== undefined ? String(object.id) : '';
      return (
        object.type.toLowerCase().includes(filter)
        || id.toLowerCase().includes(filter)
      );
    });
  });

  const rootObjectOptions = computed(() => {
    const query = ctx.rootObjectQuery.value.trim().toLowerCase();
    return ctx.dataObjects.value
      .filter((object) => {
        if (!query) {
          return true;
        }
        const id = object.id !== null && object.id !== undefined ? String(object.id) : '';
        const label = ctx.getObjectPrimaryLabel(object);
        const primaryValues = ctx.getObjectPrimaryAttributes(object)
          .map((attribute) => String(attribute.value || '').toLowerCase())
          .join(' ');
        const haystack = `${object.type} ${id} ${label} ${primaryValues}`.toLowerCase();
        return haystack.includes(query);
      })
      .slice(0, 120)
      .map((object) => ({
        title: ctx.formatObjectOptionLabel(object),
        value: object.idKey,
        subtitle: `${object.type} • ID ${object.id ?? 'N/A'}`
      }));
  });

  const selectedRootObject = computed(() => {
    if (!ctx.selectedRootObjectKey.value) {
      return null;
    }
    return objectIndex.value.get(ctx.selectedRootObjectKey.value) || null;
  });

  const tableTypeOptions = computed(() => {
    const types = new Set(ctx.dataSummary.value?.objectTypes || []);
    ctx.dataObjects.value.forEach((object) => {
      if (object.type) {
        types.add(object.type);
      }
    });
    return Array.from(types)
      .sort()
      .map((type) => ({ title: type, value: type }));
  });

  const filteredTableRows = computed(() => {
    const typeFilter = Array.isArray(ctx.tableTypeFilter.value)
      ? ctx.tableTypeFilter.value.map((type) => String(type).toLowerCase())
      : [];
    const search = ctx.tableSearch.value.trim().toLowerCase();
    const attributeKey = ctx.tableAttributeKey.value.trim().toLowerCase();
    const attributeKeyOperator = ctx.tableAttributeKeyOperator.value;
    const attributeValue = ctx.tableAttributeValue.value.trim().toLowerCase();
    const attributeValueOperator = ctx.tableAttributeValueOperator.value;

    return ctx.pageObjects.value
      .filter((object) => {
        if (typeFilter.length && !typeFilter.includes(object.type.toLowerCase())) {
          return false;
        }

        if (!attributeKey && !attributeValue) {
          return true;
        }

        const attributes = Array.isArray(object.attributes) ? object.attributes : [];
        return attributes.some((attr) => {
          const keyValue = String(attr.key || '').toLowerCase();
          const valueValue = String(attr.value || '').toLowerCase();
          const keyMatch = !attributeKey
            || (attributeKeyOperator === 'equals' ? keyValue === attributeKey : keyValue.includes(attributeKey));
          const valueMatch = !attributeValue
            || (attributeValueOperator === 'equals' ? valueValue === attributeValue : valueValue.includes(attributeValue));
          return keyMatch && valueMatch;
        });
      })
      .map((object) => {
        const attributes = Array.isArray(object.attributes) ? object.attributes : [];
        const attributePreview = attributes.slice(0, 3).map((attr) => ({
          key: attr.key,
          label: ctx.getAttributeLabel(object.type, attr.key),
          value: attr.value
        }));
        return {
          key: object.key,
          id: object.id ?? 'N/A',
          idKey: object.idKey,
          type: object.type,
          primaryLabel: ctx.getObjectPrimaryLabel(object),
          attributeCount: attributes.length,
          attributePreview,
          attributes
        };
      });
  });

  const selectedObjectType = computed(() => {
    if (!ctx.selectedObject.value) {
      return null;
    }
    return ctx.modelDetails.value.objectTypes.find((type) => type.name === ctx.selectedObject.value.type) || null;
  });

  const tableSelectedObjectType = computed(() => {
    if (!ctx.tableSelectedObject.value) {
      return null;
    }
    return ctx.modelDetails.value.objectTypes.find((type) => type.name === ctx.tableSelectedObject.value.type) || null;
  });

  const attributeTabs = computed(() => {
    if (!ctx.selectedObject.value) {
      return [];
    }

    const typeDef = selectedObjectType.value;
    const objectAttributes = Array.isArray(ctx.selectedObject.value.attributes)
      ? ctx.selectedObject.value.attributes
      : [];
    const valueMap = new Map(objectAttributes.map((attr) => [attr.key, attr.value]));
    const definitionMap = new Map();

    if (typeDef && Array.isArray(typeDef.attributes)) {
      typeDef.attributes.forEach((attr) => {
        definitionMap.set(attr.name, attr);
      });
    }

    const used = new Set();
    const tabs = [];
    const groupDefs = typeDef && Array.isArray(typeDef.attributeGroups) ? typeDef.attributeGroups : [];

    const sortedGroups = [...groupDefs].sort((a, b) => {
      const orderA = a.order ?? 9999;
      const orderB = b.order ?? 9999;
      if (orderA !== orderB) {
        return orderA - orderB;
      }
      return String(a.name || '').localeCompare(String(b.name || ''));
    });

    sortedGroups.forEach((group, groupIndex) => {
      const groupAttributes = Array.isArray(group.attributes) ? group.attributes : [];
      const sortedAttributes = [...groupAttributes].sort((a, b) => {
        const orderA = a.order ?? a.index ?? 9999;
        const orderB = b.order ?? b.index ?? 9999;
        if (orderA !== orderB) {
          return orderA - orderB;
        }
        return String(a.name || '').localeCompare(String(b.name || ''));
      });

      const rows = [];
      sortedAttributes.forEach((ref) => {
        if (!ref.name) {
          return;
        }
        const definition = definitionMap.get(ref.name);
        rows.push({
          key: ref.name,
          label: ctx.formatAttributeLabel(ref.name, definition),
          value: valueMap.get(ref.name) ?? '',
          type: definition?.type || '',
          required: Boolean(definition?.required),
          description: definition?.description || ''
        });
        used.add(ref.name);
      });

      if (rows.length) {
        tabs.push({
          key: `group-${groupIndex}-${group.name || 'groupe'}`,
          label: group.name || 'Groupe',
          attributes: rows
        });
      }
    });

    const remaining = [];
    definitionMap.forEach((definition, name) => {
      if (used.has(name)) {
        return;
      }
      remaining.push({
        key: name,
        label: ctx.formatAttributeLabel(name, definition),
        value: valueMap.get(name) ?? '',
        type: definition?.type || '',
        required: Boolean(definition?.required),
        description: definition?.description || ''
      });
      used.add(name);
    });

    objectAttributes.forEach((attr) => {
      if (used.has(attr.key)) {
        return;
      }
      remaining.push({
        key: attr.key,
        label: attr.key,
        value: attr.value ?? '',
        type: '',
        required: false,
        description: 'Attribut non défini dans le modèle'
      });
      used.add(attr.key);
    });

    if (remaining.length) {
      remaining.sort((a, b) => String(a.key).localeCompare(String(b.key)));
      tabs.push({
        key: 'group-autres',
        label: 'Autres',
        attributes: remaining
      });
    }

    if (!tabs.length && objectAttributes.length) {
      tabs.push({
        key: 'group-attributs',
        label: 'Attributs',
        attributes: objectAttributes.map((attr) => ({
          key: attr.key,
          label: attr.key,
          value: attr.value ?? '',
          type: '',
          required: false,
          description: ''
        }))
      });
    }

    return tabs;
  });

  const tableAttributeTabs = computed(() => {
    if (!ctx.tableSelectedObject.value) {
      return [];
    }

    const typeDef = tableSelectedObjectType.value;
    const objectAttributes = Array.isArray(ctx.tableSelectedObject.value.attributes)
      ? ctx.tableSelectedObject.value.attributes
      : [];
    const valueMap = new Map(objectAttributes.map((attr) => [attr.key, attr.value]));
    const definitionMap = new Map();

    if (typeDef && Array.isArray(typeDef.attributes)) {
      typeDef.attributes.forEach((attr) => {
        definitionMap.set(attr.name, attr);
      });
    }

    const used = new Set();
    const tabs = [];
    const groupDefs = typeDef && Array.isArray(typeDef.attributeGroups) ? typeDef.attributeGroups : [];

    const sortedGroups = [...groupDefs].sort((a, b) => {
      const orderA = a.order ?? 9999;
      const orderB = b.order ?? 9999;
      if (orderA !== orderB) {
        return orderA - orderB;
      }
      return String(a.name || '').localeCompare(String(b.name || ''));
    });

    sortedGroups.forEach((group, groupIndex) => {
      const groupAttributes = Array.isArray(group.attributes) ? group.attributes : [];
      const sortedAttributes = [...groupAttributes].sort((a, b) => {
        const orderA = a.order ?? a.index ?? 9999;
        const orderB = b.order ?? b.index ?? 9999;
        if (orderA !== orderB) {
          return orderA - orderB;
        }
        return String(a.name || '').localeCompare(String(b.name || ''));
      });

      const rows = [];
      sortedAttributes.forEach((ref) => {
        if (!ref.name) {
          return;
        }
        const definition = definitionMap.get(ref.name);
        rows.push({
          key: ref.name,
          label: ctx.formatAttributeLabel(ref.name, definition),
          value: valueMap.get(ref.name) ?? '',
          type: definition?.type || '',
          required: Boolean(definition?.required),
          description: definition?.description || ''
        });
        used.add(ref.name);
      });

      if (rows.length) {
        tabs.push({
          key: `table-group-${groupIndex}-${group.name || 'groupe'}`,
          label: group.name || 'Groupe',
          attributes: rows
        });
      }
    });

    const remaining = [];
    definitionMap.forEach((definition, name) => {
      if (used.has(name)) {
        return;
      }
      remaining.push({
        key: name,
        label: ctx.formatAttributeLabel(name, definition),
        value: valueMap.get(name) ?? '',
        type: definition?.type || '',
        required: Boolean(definition?.required),
        description: definition?.description || ''
      });
      used.add(name);
    });

    objectAttributes.forEach((attr) => {
      if (used.has(attr.key)) {
        return;
      }
      remaining.push({
        key: attr.key,
        label: attr.key,
        value: attr.value ?? '',
        type: '',
        required: false,
        description: 'Attribut non défini dans le modèle'
      });
      used.add(attr.key);
    });

    if (remaining.length) {
      remaining.sort((a, b) => String(a.key).localeCompare(String(b.key)));
      tabs.push({
        key: 'table-group-autres',
        label: 'Autres',
        attributes: remaining
      });
    }

    if (!tabs.length && objectAttributes.length) {
      tabs.push({
        key: 'table-group-attributs',
        label: 'Attributs',
        attributes: objectAttributes.map((attr) => ({
          key: attr.key,
          label: attr.key,
          value: attr.value ?? '',
          type: '',
          required: false,
          description: ''
        }))
      });
    }

    return tabs;
  });

  const objectTypeGroups = computed(() => {
    const groups = new Map();
    filteredObjects.value.forEach((object) => {
      const type = object.type || 'Objet';
      if (!groups.has(type)) {
        groups.set(type, []);
      }
      groups.get(type).push(object);
    });
    return Array.from(groups.entries())
      .sort(([typeA], [typeB]) => typeA.localeCompare(typeB))
      .map(([type, objects]) => {
        const sorted = [...objects].sort((a, b) => {
          const keyA = String(a.id ?? a.idKey);
          const keyB = String(b.id ?? b.idKey);
          return keyA.localeCompare(keyB);
        });
        return {
          type,
          count: sorted.length,
          objects: sorted
        };
      });
  });

  const modelTypeIndex = computed(() => {
    const index = new Map();
    ctx.modelDetails.value.objectTypes.forEach((type) => {
      if (type.name) {
        index.set(type.name, type);
      }
    });
    return index;
  });

  const createObjectAttributeDefs = computed(() => {
    if (!ctx.createObjectType.value) {
      return [];
    }
    const chain = [];
    const visited = new Set();
    let current = modelTypeIndex.value.get(ctx.createObjectType.value);
    while (current && current.name && !visited.has(current.name)) {
      visited.add(current.name);
      chain.push(current);
      const parentName = current.parent;
      if (!parentName) {
        break;
      }
      current = modelTypeIndex.value.get(parentName);
    }

    const merged = new Map();
    chain.reverse().forEach((type) => {
      const attributes = Array.isArray(type.attributes) ? type.attributes : [];
      attributes.forEach((attr) => {
        if (attr && attr.name) {
          merged.set(attr.name, attr);
        }
      });
    });

    return Array.from(merged.values())
      .map((attr) => ({
        ...attr,
        label: ctx.formatAttributeLabel(attr.name, attr) || attr.name
      }))
      .sort((a, b) => String(a.label).localeCompare(String(b.label)));
  });

  const createLinkDefinition = computed(
    () => ctx.modelDetails.value.linkTypes.find((link) => link.name === ctx.createLinkType.value) || null
  );

  const createLinkSourceOptions = computed(() => {
    if (!createLinkDefinition.value || !ctx.dataObjects.value.length) {
      return [];
    }
    const allowed = Array.isArray(createLinkDefinition.value.sources)
      ? createLinkDefinition.value.sources
      : [];
    if (!allowed.length) {
      return [];
    }
    return ctx.dataObjects.value
      .filter((object) => ctx.isTypeAllowed(object.type, allowed))
      .map((object) => ({
        title: ctx.formatObjectOptionLabel(object),
        value: object.id
      }))
      .sort((a, b) => String(a.title).localeCompare(String(b.title)));
  });

  const createLinkTargetOptions = computed(() => {
    if (!createLinkDefinition.value || !ctx.dataObjects.value.length) {
      return [];
    }
    const allowed = Array.isArray(createLinkDefinition.value.targets)
      ? createLinkDefinition.value.targets
      : [];
    if (!allowed.length) {
      return [];
    }
    return ctx.dataObjects.value
      .filter((object) => ctx.isTypeAllowed(object.type, allowed))
      .map((object) => ({
        title: ctx.formatObjectOptionLabel(object),
        value: object.id
      }))
      .sort((a, b) => String(a.title).localeCompare(String(b.title)));
  });

  const graphNodes = computed(() => ctx.buildGraphNodes());

  const graphEdges = computed(() => ctx.buildGraphEdges());

  const tableHasDetails = computed(() => Boolean(ctx.tableSelectedObject.value && ctx.showTableDetailPanel.value));

  const representativeAttributesByType = computed(() => {
    const map = new Map();
    ctx.modelDetails.value.objectTypes.forEach((type) => {
      const refs = Array.isArray(type.representativeAttributes)
        ? type.representativeAttributes
        : [];
      const sorted = [...refs].sort((a, b) => {
        const orderA = a.order ?? a.index ?? 0;
        const orderB = b.order ?? b.index ?? 0;
        if (orderA === orderB) {
          return String(a.name || '').localeCompare(String(b.name || ''));
        }
        return orderA - orderB;
      });
      map.set(
        type.name,
        sorted.map((ref) => ref.name).filter(Boolean)
      );
    });
    return map;
  });

  const searchableAttributesByType = computed(() => {
    const map = new Map();
    ctx.modelDetails.value.objectTypes.forEach((type) => {
      const searchable = new Set();
      if (Array.isArray(type.attributes)) {
        type.attributes.forEach((attr) => {
          if (attr.searchable) {
            searchable.add(attr.name);
          }
        });
      }
      map.set(type.name, searchable);
    });
    return map;
  });

  const fullTextTypeOptions = computed(() => tableTypeOptions.value);

  const fullTextResults = computed(() => {
    const query = ctx.fullTextQuery.value.trim().toLowerCase();
    if (!query) {
      return [];
    }

    const matchesQuery = value => ctx.searchMode.value === 'fulltext'
      ? query.split(/\s+/).some(token => value.includes(token))
      : value.includes(query);
    const typeFilter = Array.isArray(ctx.fullTextTypeFilter.value)
      ? ctx.fullTextTypeFilter.value.map((type) => String(type).toLowerCase())
      : [];

    return ctx.pageObjects.value
      .filter((object) => {
        if (typeFilter.length && !typeFilter.includes(object.type.toLowerCase())) {
          return false;
        }
        const searchable = searchableAttributesByType.value.get(object.type);
        if (!searchable || searchable.size === 0) {
          return false;
        }
        const attributes = Array.isArray(object.attributes) ? object.attributes : [];
        return attributes.some((attr) => {
          if (!searchable.has(attr.key)) {
            return false;
          }
          const value = String(attr.value || '').toLowerCase();
          return matchesQuery(value);
        });
      })
      .map((object) => {
        const searchable = searchableAttributesByType.value.get(object.type) || new Set();
        const attributes = Array.isArray(object.attributes) ? object.attributes : [];
        const matches = attributes
          .filter((attr) => searchable.has(attr.key))
          .filter((attr) => matchesQuery(String(attr.value || '').toLowerCase()))
          .map((attr) => ({
            key: attr.key,
            label: ctx.getAttributeLabel(object.type, attr.key),
            value: attr.value
          }));

        return {
          key: object.key,
          id: object.id ?? 'N/A',
          idKey: object.idKey,
          type: object.type,
          primaryLabel: ctx.getObjectPrimaryLabel(object),
          matches
        };
      });
  });

  const objectIndex = computed(() => {
    const map = new Map();
    ctx.dataObjects.value.forEach((object) => {
      map.set(object.idKey, object);
    });
    return map;
  });

  const relationsByObjectKey = computed(() => {
    const map = new Map();

    const appendRelation = (sourceKey, targetKey, link, direction) => {
      if (!sourceKey || !targetKey) {
        return;
      }
      const source = objectIndex.value.get(sourceKey);
      const target = objectIndex.value.get(targetKey);
      if (!source || !target) {
        return;
      }

      const key = `${link.key}:${direction}:${targetKey}`;
      const list = map.get(sourceKey) || [];
      list.push({
        key,
        type: link.type,
        targetKey,
        target,
        direction,
        directionLabel: ctx.directionToLabel(direction),
        directionIcon: ctx.directionToIcon(direction),
        targetLabel: ctx.formatObjectOptionLabel(target)
      });
      map.set(sourceKey, list);
    };

    ctx.dataLinks.value.forEach((link) => {
      const directed = ctx.isDirectedLink(link.type);
      const outDirection = directed ? 'out' : 'both';
      const inDirection = directed ? 'in' : 'both';

      if (link.fromKey && link.toKey && link.fromKey === link.toKey) {
        appendRelation(link.fromKey, link.toKey, link, outDirection);
        return;
      }

      appendRelation(link.fromKey, link.toKey, link, outDirection);
      appendRelation(link.toKey, link.fromKey, link, inDirection);
    });

    map.forEach((relations, sourceKey) => {
      relations.sort((a, b) => {
        if (a.type !== b.type) {
          return a.type.localeCompare(b.type);
        }
        if (a.direction !== b.direction) {
          return a.direction.localeCompare(b.direction);
        }
        const typeA = a.target.type || '';
        const typeB = b.target.type || '';
        if (typeA !== typeB) {
          return typeA.localeCompare(typeB);
        }
        const idA = String(a.target.id ?? a.target.idKey);
        const idB = String(b.target.id ?? b.target.idKey);
        return idA.localeCompare(idB);
      });
      map.set(sourceKey, relations);
    });

    return map;
  });

  const explorerTree = computed(() => {
    const rootKey = ctx.selectedRootObjectKey.value;
    const root = rootKey ? objectIndex.value.get(rootKey) : null;
    if (!rootKey || !root) {
      return null;
    }
    return ctx.buildExplorerNode(rootKey, rootKey, 0, new Set([rootKey]));
  });

  const linkedObjects = computed(() => {
    if (!ctx.selectedObject.value) {
      return [];
    }
    return relationsByObjectKey.value.get(ctx.selectedObject.value.idKey) || [];
  });

  const linkedRelationTabs = computed(() => {
    if (!linkedObjects.value.length) {
      return [];
    }

    const map = new Map();
    linkedObjects.value.forEach((relation) => {
      const type = relation.type || 'Lien';
      if (!map.has(type)) {
        map.set(type, {
          key: `link-type-${type}`,
          type,
          label: ctx.getLinkTypeLabel(type),
          items: []
        });
      }
      map.get(type).items.push(relation);
    });

    return Array.from(map.values())
      .sort((a, b) => {
        const byLabel = String(a.label || '').localeCompare(String(b.label || ''));
        if (byLabel !== 0) {
          return byLabel;
        }
        return String(a.type || '').localeCompare(String(b.type || ''));
      })
      .map((tab) => ({
        ...tab,
        count: tab.items.length,
        items: tab.items.sort((a, b) => {
          if (a.direction !== b.direction) {
            return a.direction.localeCompare(b.direction);
          }
          const typeA = a.target.type || '';
          const typeB = b.target.type || '';
          if (typeA !== typeB) {
            return typeA.localeCompare(typeB);
          }
          const idA = String(a.target.id ?? a.target.idKey);
          const idB = String(b.target.id ?? b.target.idKey);
          return idA.localeCompare(idB);
        })
      }));
  });

  const linkedGroups = computed(() => {
    if (!linkedObjects.value.length) {
      return [];
    }
    const map = new Map();
    linkedObjects.value.forEach((relation) => {
      const key = `${relation.type}-${relation.direction}`;
      if (!map.has(key)) {
        map.set(key, {
          key,
          title: `${relation.type} • ${relation.directionLabel}`,
          icon: relation.directionIcon,
          items: []
        });
      }
      map.get(key).items.push(relation);
    });

    return Array.from(map.values()).map((group) => ({
      ...group,
      items: group.items.sort((a, b) => {
        const typeA = a.target.type || '';
        const typeB = b.target.type || '';
        if (typeA !== typeB) {
          return typeA.localeCompare(typeB);
        }
        const idA = String(a.target.id ?? a.target.idKey);
        const idB = String(b.target.id ?? b.target.idKey);
        return idA.localeCompare(idB);
      })
    }));
  });

  const tableSelectedLinks = computed(() => {
    if (!ctx.tableSelectedObject.value) {
      return [];
    }
    return relationsByObjectKey.value.get(ctx.tableSelectedObject.value.idKey) || [];
  });

  return { dataRequestQuery, createObjectTypeOptions, createLinkTypeOptions, filteredObjects, rootObjectOptions, selectedRootObject, tableTypeOptions, filteredTableRows, selectedObjectType, tableSelectedObjectType, attributeTabs, tableAttributeTabs, objectTypeGroups, modelTypeIndex, createObjectAttributeDefs, createLinkDefinition, createLinkSourceOptions, createLinkTargetOptions, graphNodes, graphEdges, tableHasDetails, representativeAttributesByType, searchableAttributesByType, fullTextTypeOptions, fullTextResults, objectIndex, relationsByObjectKey, explorerTree, linkedObjects, linkedRelationTabs, linkedGroups, tableSelectedLinks };
}
