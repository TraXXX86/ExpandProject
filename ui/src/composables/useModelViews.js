import { computed } from 'vue';

/** Derived views of the shared reactive model, without network side effects. */
export function useModelViews(ctx) {
  const modelLanguages = computed(() => {
    const languages = Array.isArray(ctx.modelDetails.value.languages)
      ? ctx.modelDetails.value.languages
      : [];
    return languages.map((language) => ({
      title: language.label || language.code || '',
      value: language.code || ''
    }));
  });

  const defaultLanguage = computed(() => ctx.modelDetails.value.defaultLanguage || '');

  const modelOptions = computed(() =>
    ctx.models.value.map((model) => ({
      title: model.version ? `${model.name} v${model.version}` : model.name,
      value: model.key
    }))
  );

  const selectedModel = computed(
    () => ctx.models.value.find((model) => model.key === ctx.selectedModelKey.value) || null
  );

  const adminModel = computed(
    () => ctx.models.value.find((model) => model.key === ctx.adminModelKey.value) || null
  );

  const filteredModelObjects = computed(() => {
    const filter = ctx.modelObjectFilter.value.trim().toLowerCase();
    if (!filter) {
      return ctx.modelDetails.value.objectTypes;
    }
    return ctx.modelDetails.value.objectTypes.filter((type) =>
      type.name.toLowerCase().includes(filter)
    );
  });

  const filteredModelLinks = computed(() => {
    const filter = ctx.modelLinkFilter.value.trim().toLowerCase();
    if (!filter) {
      return ctx.modelDetails.value.linkTypes;
    }
    return ctx.modelDetails.value.linkTypes.filter((link) => {
      const code = String(link.name || '').toLowerCase();
      const label = String(ctx.getLinkTypeLabel(link.name) || '').toLowerCase();
      return code.includes(filter) || label.includes(filter);
    });
  });

  const modelGroupTabs = computed(() => {
    if (!ctx.selectedModelObject.value) {
      return [];
    }

    const attributes = Array.isArray(ctx.selectedModelObject.value.attributes)
      ? ctx.selectedModelObject.value.attributes
      : [];
    const attributeMap = new Map(attributes.map((attr) => [attr.name, attr]));
    const groups = Array.isArray(ctx.selectedModelObject.value.attributeGroups)
      ? ctx.selectedModelObject.value.attributeGroups
      : [];

    const sortedGroups = [...groups].sort((a, b) => {
      const orderA = a.order ?? 9999;
      const orderB = b.order ?? 9999;
      if (orderA !== orderB) {
        return orderA - orderB;
      }
      return String(a.name || '').localeCompare(String(b.name || ''));
    });

    const used = new Set();
    const tabs = sortedGroups.map((group, groupIndex) => {
      const groupAttributes = Array.isArray(group.attributes) ? group.attributes : [];
      const sortedAttributes = [...groupAttributes].sort((a, b) => {
        const orderA = a.order ?? a.index ?? 9999;
        const orderB = b.order ?? b.index ?? 9999;
        if (orderA !== orderB) {
          return orderA - orderB;
        }
        return String(a.name || '').localeCompare(String(b.name || ''));
      });

      const rows = sortedAttributes
        .filter((ref) => ref.name)
        .map((ref) => {
          const definition = attributeMap.get(ref.name);
          used.add(ref.name);
        return {
          name: ref.name,
          label: ctx.formatAttributeLabel(ref.name, definition),
          order: ref.order ?? ref.index ?? '',
          type: definition?.type || '',
          required: Boolean(definition?.required),
          defaultValue: definition?.defaultValue || ''
        };
      });

      return {
        key: `model-group-${groupIndex}-${group.name || 'groupe'}`,
        label: group.name || 'Groupe',
        attributes: rows
      };
    });

    const remaining = attributes.filter((attr) => !used.has(attr.name));
    if (remaining.length) {
      tabs.push({
        key: 'model-group-autres',
        label: 'Non groupés',
      attributes: remaining.map((attr) => ({
        name: attr.name,
        label: ctx.formatAttributeLabel(attr.name, attr),
        order: '',
        type: attr.type || '',
        required: Boolean(attr.required),
        defaultValue: attr.defaultValue || ''
      }))
      });
    }

    return tabs;
  });

  const adminCommand = computed(() => {
    if (!adminModel.value) {
      return 'java -jar importpackage.jar --delete-model <modelName> [modelVersion]';
    }
    const name = adminModel.value.name;
    const version = adminModel.value.version || '';
    if (version) {
      return `java -jar importpackage.jar --delete-model \"${name}\" \"${version}\"`;
    }
    return `java -jar importpackage.jar --delete-model \"${name}\"`;
  });

  return { modelLanguages, defaultLanguage, modelOptions, selectedModel, adminModel, filteredModelObjects, filteredModelLinks, modelGroupTabs, adminCommand };
}
