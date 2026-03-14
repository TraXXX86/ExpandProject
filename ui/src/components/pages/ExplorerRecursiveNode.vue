<template>
  <div
    ref="nodeRef"
    class="explorer-node"
    :style="{ '--explorer-depth': node.depth }"
    @contextmenu.prevent="openContextMenu"
  >
    <v-card
      class="explorer-node-card"
      variant="outlined"
      rounded="lg"
      @click="selectNode"
      @contextmenu.prevent.stop="openContextMenu"
    >
      <v-card-text class="py-2 px-3">
        <div class="explorer-node-row">
          <v-btn
            v-if="hasExpandableRelations"
            icon
            size="x-small"
            variant="text"
            class="explorer-expand-btn"
            :loading="isLoadingRelations"
            :disabled="state.isNeighborsLoaded?.(node.object.idKey) && !hasChildren"
            @click.stop="toggleExpand"
          >
            <v-icon :icon="isExpanded ? 'mdi-chevron-down' : 'mdi-chevron-right'" />
          </v-btn>
          <span v-else class="explorer-expand-placeholder" aria-hidden="true" />

          <span class="material-symbols-outlined type-icon explorer-node-icon" aria-hidden="true">
            {{ state.getTypeIconName(node.object.type) }}
          </span>

          <div class="explorer-node-primary" role="button" tabindex="0" @keyup.enter="selectNode">
            <template v-if="primaryAttributes.length">
              <span
                v-for="attribute in primaryAttributes"
                :key="attribute.key"
                class="explorer-primary-value"
              >
                {{ attribute.value }}
              </span>
            </template>
            <span v-else class="explorer-primary-empty">Aucun attribut principal</span>
          </div>
        </div>
      </v-card-text>
    </v-card>

    <teleport to="body">
      <div
        v-if="contextMenuVisible"
        ref="menuRef"
        class="explorer-context-menu"
        :style="contextMenuStyle"
        @click.stop
      >
        <div class="explorer-context-title">Types de liens a parcourir</div>
        <div v-if="relationTypeItems.length" class="explorer-context-list">
          <label
            v-for="relationType in relationTypeItems"
            :key="relationType.code"
            class="explorer-context-item"
          >
            <input
              type="checkbox"
              :checked="state.isNodeRelationSelected(node.nodePath, relationType.code, node.object.idKey)"
              @change="toggleRelationSelection(relationType.code, $event)"
            >
            <span class="explorer-context-item-label">
              {{ relationType.label }}
            </span>
          </label>
        </div>
        <div v-else class="explorer-context-empty">Aucun lien disponible.</div>
      </div>
    </teleport>

    <div v-if="isExpanded" class="explorer-children">
      <div v-for="group in groupedChildren" :key="group.key" class="explorer-child">
        <div class="explorer-branch-label">
          {{ group.label }} ({{ group.directionLabel }})
        </div>
        <div
          v-for="child in group.items"
          :key="child.key"
          class="explorer-branch-item"
        >
          <div v-if="child.cycle" class="explorer-cycle-note">
            Cycle detecte sur cette branche, expansion arretee.
          </div>
          <div v-else-if="child.depthLimit" class="explorer-cycle-note">
            Profondeur maximale atteinte.
          </div>
          <ExplorerRecursiveNode v-else-if="child.node" :node="child.node" :state="state" />
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue';

defineOptions({ name: 'ExplorerRecursiveNode' });

const props = defineProps({
  node: {
    type: Object,
    required: true
  },
  state: {
    type: Object,
    required: true
  }
});

const node = props.node;
const state = props.state;

const nodeRef = ref(null);
const menuRef = ref(null);
const contextMenuVisible = ref(false);
const contextMenuX = ref(0);
const contextMenuY = ref(0);
const isLoadingRelations = ref(false);

const primaryAttributes = computed(() => state.getObjectPrimaryAttributes(node.object));
const relationTypeItems = computed(() =>
  Array.from(new Set(node.relations.map((relation) => relation.type).filter(Boolean)))
    .sort((a, b) => a.localeCompare(b))
    .map((code) => ({
      code,
      label: state.getLinkTypeLabel(code)
    }))
);
const selectedRelations = computed(() =>
  node.relations.filter((relation) =>
    state.isNodeRelationSelected(node.nodePath, relation.type, node.object.idKey)
  )
);
const renderedChildren = computed(() => {
  if (node.children.length) {
    return node.children;
  }
  if (!selectedRelations.value.length || !state.getExplorerSubtree) {
    return [];
  }

  const baseVisited = Array.isArray(node.visitedKeys) && node.visitedKeys.length
    ? node.visitedKeys
    : [node.object.idKey];

  return selectedRelations.value
    .map((relation) => {
      const childPath = `${node.nodePath}>${relation.key}`;
      if (baseVisited.includes(relation.targetKey)) {
        return {
          key: childPath,
          edge: relation,
          cycle: true,
          depthLimit: false,
          node: null
        };
      }
      const subtree = state.getExplorerSubtree(
        relation.targetKey,
        childPath,
        Number(node.depth || 0) + 1,
        [...baseVisited, relation.targetKey]
      );
      return {
        key: childPath,
        edge: relation,
        cycle: false,
        depthLimit: false,
        node: subtree
      };
    })
    .filter((entry) => entry.node || entry.cycle || entry.depthLimit);
});
const visibleChildren = computed(() =>
  renderedChildren.value.filter((child) => state.showCycleDetection || !child.cycle)
);
const groupedChildren = computed(() => {
  const groups = new Map();

  visibleChildren.value.forEach((child) => {
    const groupKey = `${child.edge.type}|${child.edge.direction}`;
    if (!groups.has(groupKey)) {
      groups.set(groupKey, {
        key: groupKey,
        label: state.getLinkTypeLabel(child.edge.type, child.edge.direction),
        directionLabel: child.edge.directionLabel,
        items: []
      });
    }
    groups.get(groupKey).items.push(child);
  });

  return Array.from(groups.values());
});
const hasChildren = computed(() => visibleChildren.value.length > 0);
const hasExpandableRelations = computed(() =>
  node.relations.length > 0 || !state.isNeighborsLoaded?.(node.object.idKey)
);
const isExpanded = computed(() => state.isNodeExpanded(node.nodePath));

const contextMenuStyle = computed(() => ({
  left: `${contextMenuX.value}px`,
  top: `${contextMenuY.value}px`
}));

function selectNode() {
  state.selectObjectByKey(node.object.idKey);
}

async function ensureNodeRelationsLoaded() {
  if (!state.ensureNeighborsLoaded || !node.object?.idKey) {
    return;
  }
  if (state.isNeighborsLoaded?.(node.object.idKey)) {
    return;
  }
  isLoadingRelations.value = true;
  try {
    await state.ensureNeighborsLoaded(node.object.idKey);
    await nextTick();
  } finally {
    isLoadingRelations.value = false;
  }
}

async function toggleExpand() {
  await ensureNodeRelationsLoaded();
  if (!hasExpandableRelations.value) {
    return;
  }
  if (!selectedRelations.value.length) {
    openContextMenuFromNode();
    return;
  }
  state.toggleNodeExpanded(node.nodePath);
}

async function openContextMenu(event) {
  event?.preventDefault?.();
  await ensureNodeRelationsLoaded();
  if (!relationTypeItems.value.length) {
    return;
  }
  selectNode();
  positionContextMenu(event?.clientX ?? 0, event?.clientY ?? 0);
  contextMenuVisible.value = true;
}

function closeContextMenu() {
  contextMenuVisible.value = false;
}

function openContextMenuFromNode() {
  const rect = nodeRef.value?.getBoundingClientRect?.();
  if (!rect) {
    contextMenuVisible.value = true;
    return;
  }
  positionContextMenu(rect.left + 16, rect.bottom + 8);
  contextMenuVisible.value = true;
}

function toggleRelationSelection(relationType, event) {
  const checked = Boolean(event?.target?.checked);
  state.toggleNodeRelation(node.nodePath, relationType, checked, node.object.idKey);
  if (checked && !state.isNodeExpanded(node.nodePath) && state.toggleNodeExpanded) {
    state.toggleNodeExpanded(node.nodePath);
  }
}

function positionContextMenu(x, y) {
  const margin = 12;
  const estimatedWidth = 360;
  const estimatedHeight = 320;
  const maxX = Math.max(margin, window.innerWidth - estimatedWidth - margin);
  const maxY = Math.max(margin, window.innerHeight - estimatedHeight - margin);
  contextMenuX.value = Math.max(margin, Math.min(x, maxX));
  contextMenuY.value = Math.max(margin, Math.min(y, maxY));
}

function onGlobalPointerDown(event) {
  if (!contextMenuVisible.value) {
    return;
  }
  const menuEl = menuRef.value;
  if (menuEl && menuEl.contains(event.target)) {
    return;
  }
  closeContextMenu();
}

function onGlobalKeydown(event) {
  if (event.key === 'Escape') {
    closeContextMenu();
  }
}

onMounted(() => {
  window.addEventListener('mousedown', onGlobalPointerDown);
  window.addEventListener('scroll', closeContextMenu, true);
  window.addEventListener('resize', closeContextMenu);
  window.addEventListener('keydown', onGlobalKeydown);
});

onBeforeUnmount(() => {
  window.removeEventListener('mousedown', onGlobalPointerDown);
  window.removeEventListener('scroll', closeContextMenu, true);
  window.removeEventListener('resize', closeContextMenu);
  window.removeEventListener('keydown', onGlobalKeydown);
});
</script>
