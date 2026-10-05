import { computed } from 'vue';

/** Derived views of the shared reactive model, without network side effects. */
export function useAccessViews(ctx) {
  const userOptions = computed(() =>
    ctx.users.value.map((user) => ({
      title: user.displayName ? `${user.displayName} (${user.username})` : user.username,
      value: user.username
    }))
  );

  const isPlatformAdmin = computed(() => Boolean(ctx.accessProfile.value.platformAdmin));

  const canAccessUserPortal = computed(() => Boolean(ctx.accessProfile.value.portalUser || isPlatformAdmin.value));

  const canAccessModelAdminPortal = computed(
    () => Boolean(ctx.accessProfile.value.portalModelAdmin || isPlatformAdmin.value)
  );

  const selectedModelPermission = computed(() => ctx.getModelPermission(ctx.selectedModelKey.value));

  const canReadCurrentModelData = computed(() => ctx.canReadModelData(ctx.selectedModelKey.value));

  const canCreateCurrentModelData = computed(() => ctx.canCreateModelData(ctx.selectedModelKey.value));

  const canUpdateCurrentModelData = computed(() => ctx.canUpdateModelData(ctx.selectedModelKey.value));

  const canDeleteCurrentModelData = computed(() => ctx.canDeleteModelData(ctx.selectedModelKey.value));

  const canManageAccess = computed(() => Boolean(ctx.authMeta.value.actorPlatformAdmin));

  const isPortalSelected = computed(() => Boolean(ctx.activePortal.value));

  const isUserPortal = computed(() => ctx.activePortal.value === 'user');

  const isModelAdminPortal = computed(() => ctx.activePortal.value === 'model-admin');

  const portalLabel = computed(() => {
    if (isUserPortal.value) {
      return 'Portail métier';
    }
    if (isModelAdminPortal.value) {
      return 'Portail administration du modèle';
    }
    return 'Connexion';
  });

  const canUploadModel = computed(
    () => Boolean(ctx.getFirstFile(ctx.modelFile.value) && canAccessModelAdminPortal.value)
  );

  const canUploadData = computed(
    () => Boolean(ctx.getFirstFile(ctx.dataFile.value) && ctx.selectedModelKey.value && canCreateCurrentModelData.value)
  );

  const neo4jChipLabel = computed(() => {
    if (ctx.healthStatus.value.neo4j === 'ok') {
      return 'OK';
    }
    if (ctx.healthStatus.value.neo4j === 'ko') {
      return 'KO';
    }
    return '—';
  });

  const neo4jChipColor = computed(() => {
    if (ctx.healthStatus.value.neo4j === 'ok') {
      return 'success';
    }
    if (ctx.healthStatus.value.neo4j === 'ko') {
      return 'error';
    }
    return 'secondary';
  });

  return { userOptions, isPlatformAdmin, canAccessUserPortal, canAccessModelAdminPortal, selectedModelPermission, canReadCurrentModelData, canCreateCurrentModelData, canUpdateCurrentModelData, canDeleteCurrentModelData, canManageAccess, isPortalSelected, isUserPortal, isModelAdminPortal, portalLabel, canUploadModel, canUploadData, neo4jChipLabel, neo4jChipColor };
}
