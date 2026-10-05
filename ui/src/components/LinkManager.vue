<template>
  <v-card class="mt-6" variant="outlined" rounded="lg">
    <v-card-title>Modifier les liens chargés</v-card-title>
    <v-card-text>
      <v-select v-model="selectedId" :items="items" label="Lien" clearable />
      <template v-if="selected">
        <v-text-field v-for="attribute in attributeDefinitions" :key="attribute.name" v-model="attributeValues[attribute.name]" :label="`${state.formatAttributeLabel?.(attribute.name, attribute) || attribute.name}${attribute.required ? ' *' : ''}`" :hint="attribute.description || ''" persistent-hint />
        <div v-if="!attributeDefinitions.length" class="text-caption mb-3">Ce type de lien ne définit pas d’attribut modifiable.</div>
        <v-alert v-if="error" type="error" class="my-3">{{ error }}</v-alert>
        <div class="d-flex flex-wrap" style="gap: 12px">
          <v-btn :disabled="!state.canUpdateCurrentModelData" :loading="state.isMutatingLink" @click="save">Enregistrer</v-btn>
          <v-btn color="error" :disabled="!state.canDeleteCurrentModelData || state.isMutatingLink" @click="state.mutateLink(selected)">Supprimer</v-btn>
        </div>
      </template>
      <div v-if="!items.length" class="text-medium-emphasis">Aucun lien dans la page chargée.</div>
    </v-card-text>
  </v-card>
</template>
<script setup>
import { computed, ref, watch } from 'vue';
const props = defineProps({ state: { type: Object, required: true } });
const selectedId = ref(null);
const attributeValues = ref({});
const error = ref('');
function objectLabel(key) { return props.state.getObjectPrimaryLabel?.(props.state.objectIndex?.get(key)) || `Objet ${key}`; }
const items = computed(() => props.state.dataLinks.filter(link => link.uuid || link.id != null).map(link => ({ title: `${link.type} : ${objectLabel(link.fromKey)} → ${objectLabel(link.toKey)}`, value: link.uuid || link.id })));
const selected = computed(() => props.state.dataLinks.find(link => (link.uuid || link.id) === selectedId.value));
const attributeDefinitions = computed(() => {
  const defined = props.state.modelDetails?.linkTypes?.find(type => type.name === selected.value?.type)?.attributes || [];
  const names = new Set(defined.map(attribute => attribute.name));
  return [...defined, ...(selected.value?.attributes || []).filter(attribute => !names.has(attribute.key)).map(attribute => ({ name: attribute.key }))];
});
watch(selected, link => { attributeValues.value = Object.fromEntries((link?.attributes || []).map(attribute => [attribute.key, attribute.value])); error.value = ''; });
function save() {
  try {
    if (attributeDefinitions.value.some(attribute => attribute.required && !String(attributeValues.value[attribute.name] ?? '').trim())) throw new Error('Renseignez les attributs obligatoires.');
    const attributes = attributeDefinitions.value.map(attribute => ({ key: attribute.name, value: attributeValues.value[attribute.name] ?? '' })).filter(attribute => String(attribute.value).trim());
    error.value = '';
    props.state.mutateLink(selected.value, attributes);
  } catch (cause) { error.value = cause.message; }
}
</script>
