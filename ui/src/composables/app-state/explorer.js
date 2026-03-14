import { computed } from 'vue';

export function useExplorerState(state) {
  state.explorerTree = computed(() => {
    const rootKey = state.selectedRootObjectKey.value;
    const root = rootKey ? state.objectIndex.value.get(rootKey) : null;
    if (!rootKey || !root) {
      return null;
    }
    return buildExplorerNode(rootKey, rootKey, 0, new Set([rootKey]));
  });

  state.linkedObjects = computed(() => {
    if (!state.selectedObject.value) {
      return [];
    }
    return state.relationsByObjectKey.value.get(state.selectedObject.value.idKey) || [];
  });

  state.linkedRelationTabs = computed(() => {
    if (!state.linkedObjects.value.length) {
      return [];
    }

    const map = new Map();
    state.linkedObjects.value.forEach((relation) => {
      const type = relation.type || 'Lien';
      if (!map.has(type)) {
        map.set(type, {
          key: `link-type-${type}`,
          type,
          label: state.getLinkTypeLabel(type),
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

  state.linkedGroups = computed(() => {
    if (!state.linkedObjects.value.length) {
      return [];
    }
    const map = new Map();
    state.linkedObjects.value.forEach((relation) => {
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

  function selectObject(object) {
    state.selectedObject.value = object;
  }

  function setRootObjectByKey(key) {
    if (!key) {
      state.selectedRootObjectKey.value = '';
      state.treeLinkSelections.value = {};
      state.treeExpandedNodes.value = {};
      return;
    }
    const target = state.objectIndex.value.get(String(key));
    if (!target) {
      return;
    }
    const isSameRoot = state.selectedRootObjectKey.value === target.idKey;
    state.selectedRootObjectKey.value = target.idKey;
    state.selectedObject.value = target;
    if (!isSameRoot) {
      state.treeLinkSelections.value = {};
      state.treeExpandedNodes.value = {};
    }
  }

  function resetExplorerTraversal() {
    state.treeLinkSelections.value = {};
    state.treeExpandedNodes.value = {};
  }

  function clearNodeSelections(pathPrefix = '') {
    if (!pathPrefix) {
      state.treeLinkSelections.value = {};
      state.treeExpandedNodes.value = {};
      return;
    }
    const next = {};
    Object.entries(state.treeLinkSelections.value).forEach(([path, selected]) => {
      if (!path.startsWith(pathPrefix)) {
        next[path] = selected;
      }
    });
    state.treeLinkSelections.value = next;
    clearNodeExpansions(pathPrefix);
  }

  function clearNodeExpansions(pathPrefix = '') {
    if (!pathPrefix) {
      state.treeExpandedNodes.value = {};
      return;
    }
    const next = {};
    Object.entries(state.treeExpandedNodes.value).forEach(([path, expanded]) => {
      if (!path.startsWith(pathPrefix)) {
        next[path] = expanded;
      }
    });
    state.treeExpandedNodes.value = next;
  }

  function isNodeExpanded(nodePath) {
    return Boolean(state.treeExpandedNodes.value[nodePath]);
  }

  function toggleNodeExpanded(nodePath) {
    if (!nodePath) {
      return;
    }
    state.treeExpandedNodes.value = {
      ...state.treeExpandedNodes.value,
      [nodePath]: !state.treeExpandedNodes.value[nodePath]
    };
  }

  function getObjectKeyFromNodePath(nodePath) {
    if (!nodePath) {
      return '';
    }
    const segments = String(nodePath).split('>');
    const last = segments[segments.length - 1] || '';
    if (!last.includes(':')) {
      return last;
    }
    const parts = last.split(':');
    return parts[parts.length - 1] || '';
  }

  function getNodeRelationsByPath(nodePath, objectKey = '') {
    const resolvedObjectKey = objectKey ? String(objectKey) : getObjectKeyFromNodePath(nodePath);
    if (!resolvedObjectKey) {
      return [];
    }
    return state.relationsByObjectKey.value.get(resolvedObjectKey) || [];
  }

  function getNodeRelationTypes(nodePath, objectKey = '') {
    return Array.from(
      new Set(
        getNodeRelationsByPath(nodePath, objectKey)
          .map((relation) => relation.type)
          .filter(Boolean)
      )
    ).sort((a, b) => a.localeCompare(b));
  }

  function getSelectedNodeRelationTypes(nodePath, availableTypes) {
    const selected = state.treeLinkSelections.value[nodePath];
    if (Array.isArray(selected)) {
      return selected;
    }
    return availableTypes;
  }

  function isNodeRelationSelected(nodePath, relationType, objectKey = '') {
    if (!nodePath || !relationType) {
      return false;
    }
    const availableTypes = getNodeRelationTypes(nodePath, objectKey);
    if (!availableTypes.includes(relationType)) {
      return false;
    }
    const selectedTypes = getSelectedNodeRelationTypes(nodePath, availableTypes);
    return selectedTypes.includes(relationType);
  }

  function toggleNodeRelation(nodePath, relationType, checked, objectKey = '') {
    if (!nodePath || !relationType) {
      return;
    }

    const availableTypes = getNodeRelationTypes(nodePath, objectKey);
    if (!availableTypes.includes(relationType)) {
      return;
    }

    const current = new Set(getSelectedNodeRelationTypes(nodePath, availableTypes));
    if (checked) {
      current.add(relationType);
    } else {
      current.delete(relationType);
    }

    const nextSelected = Array.from(current).sort((a, b) => a.localeCompare(b));
    const next = { ...state.treeLinkSelections.value };
    const allSelected = nextSelected.length === availableTypes.length
      && availableTypes.every((type) => current.has(type));

    if (allSelected) {
      delete next[nodePath];
    } else {
      next[nodePath] = nextSelected;
    }

    state.treeLinkSelections.value = next;

    if (!checked) {
      const relatedBranches = getNodeRelationsByPath(nodePath, objectKey)
        .filter((relation) => relation.type === relationType)
        .map((relation) => `${nodePath}>${relation.key}`);
      relatedBranches.forEach((prefix) => clearNodeSelections(prefix));
    }
  }

  function selectObjectByKey(key) {
    const target = state.objectIndex.value.get(String(key));
    if (target) {
      state.selectedObject.value = target;
    }
  }

  function viewObjectFromTable(key) {
    const target = state.objectIndex.value.get(String(key));
    if (target) {
      state.tableSelectedObject.value = target;
      state.showTableDetailPanel.value = true;
    }
  }

  function openInExplorerFromTable() {
    if (!state.tableSelectedObject.value) {
      return;
    }
    setRootObjectByKey(state.tableSelectedObject.value.idKey);
    state.currentPage.value = 'navigate';
  }

  function selectModelObjectByName(name) {
    if (!name) {
      return;
    }
    const match = state.modelDetails.value.objectTypes.find((type) => type.name === name);
    if (match) {
      state.selectedModelObject.value = match;
      state.modelObjectFilter.value = name;
    }
  }

  function selectModelLinkByName(name) {
    if (!name) {
      return;
    }
    const match = state.modelDetails.value.linkTypes.find((link) => link.name === name);
    if (match) {
      state.selectedModelLink.value = match;
      state.modelLinkFilter.value = name;
    }
  }

  function buildExplorerNode(objectKey, nodePath, depth, visitedKeys) {
    const object = state.objectIndex.value.get(objectKey);
    if (!object) {
      return null;
    }

    const relations = state.relationsByObjectKey.value.get(objectKey) || [];
    const availableTypes = Array.from(
      new Set(relations.map((relation) => relation.type).filter(Boolean))
    );
    const selected = Array.isArray(state.treeLinkSelections.value[nodePath])
      ? state.treeLinkSelections.value[nodePath]
      : availableTypes;
    const selectedSet = new Set(selected);
    const depthLimitReached = depth >= state.explorerMaxDepth;

    const children = relations
      .filter((relation) => selectedSet.has(relation.type))
      .map((relation) => {
        const childPath = `${nodePath}>${relation.key}`;
        if (depthLimitReached) {
          return {
            key: childPath,
            edge: relation,
            cycle: false,
            depthLimit: true,
            node: null
          };
        }
        if (visitedKeys.has(relation.targetKey)) {
          return {
            key: childPath,
            edge: relation,
            cycle: true,
            depthLimit: false,
            node: null
          };
        }
        const branchVisited = new Set(visitedKeys);
        branchVisited.add(relation.targetKey);
        return {
          key: childPath,
          edge: relation,
          cycle: false,
          depthLimit: false,
          node: buildExplorerNode(relation.targetKey, childPath, depth + 1, branchVisited)
        };
      });

    return {
      key: `${nodePath}:${object.idKey}`,
      nodePath,
      depth,
      object,
      relations,
      children,
      visitedKeys: Array.from(visitedKeys)
    };
  }

  function getExplorerSubtree(objectKey, nodePath, depth = 0, visitedKeys = []) {
    const key = String(objectKey || '');
    if (!key) {
      return null;
    }
    const path = nodePath || key;
    const visited = new Set(
      Array.isArray(visitedKeys) && visitedKeys.length
        ? visitedKeys.map((entry) => String(entry))
        : [key]
    );
    if (!visited.has(key)) {
      visited.add(key);
    }
    return buildExplorerNode(key, path, depth, visited);
  }

  Object.assign(state, {
    selectObject,
    setRootObjectByKey,
    resetExplorerTraversal,
    clearNodeSelections,
    clearNodeExpansions,
    isNodeExpanded,
    toggleNodeExpanded,
    isNodeRelationSelected,
    toggleNodeRelation,
    getExplorerSubtree,
    selectObjectByKey,
    viewObjectFromTable,
    openInExplorerFromTable,
    selectModelObjectByName,
    selectModelLinkByName
  });

  return state;
}
