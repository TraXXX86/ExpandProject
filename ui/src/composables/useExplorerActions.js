/** Explorer traversal and graph projection. */
export function useExplorerActions(ctx) {
  function selectObject(object) {
    ctx.selectedObject.value = object;
  }

  function setRootObjectByKey(key) {
    if (!key) {
      ctx.selectedRootObjectKey.value = '';
      ctx.treeLinkSelections.value = {};
      ctx.treeExpandedNodes.value = {};
      return;
    }
    const target = ctx.objectIndex.value.get(String(key));
    if (!target) {
      return;
    }
    const isSameRoot = ctx.selectedRootObjectKey.value === target.idKey;
    ctx.selectedRootObjectKey.value = target.idKey;
    ctx.selectedObject.value = target;
    if (!isSameRoot) {
      ctx.treeLinkSelections.value = {};
      ctx.treeExpandedNodes.value = {};
    }
  }

  function resetExplorerTraversal() {
    ctx.treeLinkSelections.value = {};
    ctx.treeExpandedNodes.value = {};
  }

  function clearNodeSelections(pathPrefix = '') {
    if (!pathPrefix) {
      ctx.treeLinkSelections.value = {};
      ctx.treeExpandedNodes.value = {};
      return;
    }
    const next = {};
    Object.entries(ctx.treeLinkSelections.value).forEach(([path, selected]) => {
      if (!path.startsWith(pathPrefix)) {
        next[path] = selected;
      }
    });
    ctx.treeLinkSelections.value = next;
    clearNodeExpansions(pathPrefix);
  }

  function clearNodeExpansions(pathPrefix = '') {
    if (!pathPrefix) {
      ctx.treeExpandedNodes.value = {};
      return;
    }
    const next = {};
    Object.entries(ctx.treeExpandedNodes.value).forEach(([path, expanded]) => {
      if (!path.startsWith(pathPrefix)) {
        next[path] = expanded;
      }
    });
    ctx.treeExpandedNodes.value = next;
  }

  function isNodeExpanded(nodePath) {
    return Boolean(ctx.treeExpandedNodes.value[nodePath]);
  }

  function toggleNodeExpanded(nodePath) {
    if (!nodePath) {
      return;
    }
    ctx.treeExpandedNodes.value = {
      ...ctx.treeExpandedNodes.value,
      [nodePath]: !ctx.treeExpandedNodes.value[nodePath]
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
    return ctx.relationsByObjectKey.value.get(resolvedObjectKey) || [];
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
    const selected = ctx.treeLinkSelections.value[nodePath];
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
    const next = { ...ctx.treeLinkSelections.value };
    const allSelected = nextSelected.length === availableTypes.length
      && availableTypes.every((type) => current.has(type));

    if (allSelected) {
      delete next[nodePath];
    } else {
      next[nodePath] = nextSelected;
    }

    ctx.treeLinkSelections.value = next;

    if (!checked) {
      const relatedBranches = getNodeRelationsByPath(nodePath, objectKey)
        .filter((relation) => relation.type === relationType)
        .map((relation) => `${nodePath}>${relation.key}`);
      relatedBranches.forEach((prefix) => clearNodeSelections(prefix));
    }
  }

  function selectObjectByKey(key) {
    const target = ctx.objectIndex.value.get(String(key));
    if (target) {
      ctx.selectedObject.value = target;
    }
  }

  function viewObjectFromTable(key) {
    const target = ctx.objectIndex.value.get(String(key));
    if (target) {
      ctx.tableSelectedObject.value = target;
      ctx.showTableDetailPanel.value = true;
    }
  }

  function openInExplorerFromTable() {
    if (!ctx.tableSelectedObject.value) {
      return;
    }
    setRootObjectByKey(ctx.tableSelectedObject.value.idKey);
    ctx.currentPage.value = 'navigate';
  }

  function buildExplorerNode(objectKey, nodePath, depth, visitedKeys) {
    const object = ctx.objectIndex.value.get(objectKey);
    if (!object) {
      return null;
    }

    const relations = ctx.relationsByObjectKey.value.get(objectKey) || [];
    const availableTypes = Array.from(
      new Set(relations.map((relation) => relation.type).filter(Boolean))
    );
    const selected = Array.isArray(ctx.treeLinkSelections.value[nodePath])
      ? ctx.treeLinkSelections.value[nodePath]
      : availableTypes;
    const selectedSet = new Set(selected);
    const depthLimitReached = depth >= ctx.explorerMaxDepth;

    const children = (ctx.treeExpandedNodes.value[nodePath] ? relations : [])
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

  function buildRelation(link, direction, targetKey, index) {
    return {
      key: `${link.type}-${index}-${direction}`,
      type: link.type,
      targetKey,
      direction,
      directionLabel: directionToLabel(direction),
      directionIcon: directionToIcon(direction)
    };
  }

  function directionToLabel(direction) {
    if (direction === 'out') {
      return 'sortant';
    }
    if (direction === 'in') {
      return 'entrant';
    }
    return 'bidirectionnel';
  }

  function directionToIcon(direction) {
    if (direction === 'out') {
      return 'mdi-arrow-right';
    }
    if (direction === 'in') {
      return 'mdi-arrow-left';
    }
    return 'mdi-arrow-left-right';
  }

  function buildGraphNodes() {
    const nodes = ctx.modelDetails.value.objectTypes;
    if (!nodes.length) {
      return [];
    }
    const centerX = 320;
    const centerY = 200;
    const radius = Math.max(120, Math.min(160, nodes.length * 12));
    const step = (Math.PI * 2) / nodes.length;

    return nodes.map((node, index) => {
      const angle = index * step - Math.PI / 2;
      const x = centerX + Math.cos(angle) * radius;
      const y = centerY + Math.sin(angle) * radius;
      const label = node.name.length > 6 ? `${node.name.slice(0, 6)}...` : node.name;
      return {
        key: `node-${node.name}-${index}`,
        name: node.name,
        label,
        x,
        y
      };
    });
  }

  function buildGraphEdges() {
    if (!ctx.graphNodes.value.length) {
      return [];
    }
    const nodeMap = new Map(ctx.graphNodes.value.map((node) => [node.name, node]));
    const edges = [];

    ctx.modelDetails.value.linkTypes.forEach((link, linkIndex) => {
      link.sources.forEach((source) => {
        link.targets.forEach((target) => {
          const from = nodeMap.get(source);
          const to = nodeMap.get(target);
          if (!from || !to) {
            return;
          }
          const self = source === target;
          edges.push({
            key: `${link.name}-${source}-${target}-${linkIndex}`,
            name: link.name,
            from,
            to,
            directed: link.directed,
            self,
            path: self ? buildSelfLoopPath(from) : '',
            label: `${link.name} (${source}${link.directed ? ' → ' : ' ↔ '}${target})`
          });
        });
      });
    });

    return edges;
  }

  function buildSelfLoopPath(node) {
    const radius = 22;
    const loop = 36;
    const startX = node.x + radius;
    const startY = node.y - radius;
    const c1X = node.x + loop;
    const c1Y = node.y - loop * 1.4;
    const c2X = node.x + loop * 1.8;
    const c2Y = node.y + loop * 0.4;
    const endX = node.x;
    const endY = node.y + radius;
    return `M ${startX} ${startY} C ${c1X} ${c1Y}, ${c2X} ${c2Y}, ${endX} ${endY}`;
  }

  return { selectObject, setRootObjectByKey, resetExplorerTraversal, clearNodeSelections, clearNodeExpansions, isNodeExpanded, toggleNodeExpanded, getObjectKeyFromNodePath, getNodeRelationsByPath, getNodeRelationTypes, getSelectedNodeRelationTypes, isNodeRelationSelected, toggleNodeRelation, selectObjectByKey, viewObjectFromTable, openInExplorerFromTable, buildExplorerNode, getExplorerSubtree, buildRelation, directionToLabel, directionToIcon, buildGraphNodes, buildGraphEdges, buildSelfLoopPath };
}
