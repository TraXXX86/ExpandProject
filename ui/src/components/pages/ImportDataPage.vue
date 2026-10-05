<template>
  <div class="kicker">Import de données</div>
  <h1 class="headline">Du fichier aux données, en toute visibilité.</h1>
  <p class="text-body-1 text-medium-emphasis mt-3 mb-6">Associez vos colonnes, examinez les ajouts et les modifications, puis confirmez l’import.</p>
  <ImportWizard :state="state" />
</template>
<script setup>
import ImportWizard from '../ImportWizard.vue';
defineProps({ state: { type: Object, required: true } });
</script>
