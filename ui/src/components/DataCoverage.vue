<template>
  <v-card v-if="state.selectedModelKey && state.canReadCurrentModelData" variant="outlined" class="mb-5" rounded="lg">
    <v-card-text>
      <div class="d-flex align-center flex-wrap" style="gap: 12px">
        <span role="status" aria-live="polite">
          {{ state.isLoadingData ? 'Chargement des données…' : `Objets ${state.dataSummary?.objectCount ? state.dataOffset + 1 : 0}–${state.dataOffset + (state.dataSummary?.objectCount || 0)} sur ${state.dataSummary?.totalObjects || 0}` }}
        </span>
        <v-btn size="small" :disabled="state.isLoadingData || state.dataOffset === 0" @click="state.refreshData(state.selectedModelKey, Math.max(0, state.dataOffset - state.dataLimit))">Précédent</v-btn>
        <v-btn size="small" :disabled="state.isLoadingData || !state.dataHasMore" @click="state.refreshData(state.selectedModelKey, state.dataOffset + state.dataLimit)">Suivant</v-btn>
        <v-btn size="small" :loading="state.isLoadingData" @click="state.refreshData(state.selectedModelKey)">Rafraîchir</v-btn>
      </div>
      <div class="text-caption mt-2">La recherche porte sur les champs du modèle autorisés pour la recherche. Les tableaux et sélecteurs affichent les objets chargés. Les liens de la page relient ces objets. L’explorateur charge les voisins à l’ouverture d’une branche (100 voisins maximum par objet).</div>
      <v-alert v-if="state.dataSummary?.hasMoreLinks" type="warning" class="mt-2" density="compact">Liens partiels : cette page dépasse la limite de 5 000 liens.</v-alert>
      <v-row v-if="!['table', 'search'].includes(state.currentPage)" class="mt-2">
        <v-col cols="12" md="8"><v-text-field v-model="state.dataQuery" label="Rechercher dans les champs du modèle" clearable hide-details density="compact" /></v-col>
        <v-col cols="12" md="4"><v-select v-model="state.dataType" :items="state.tableTypeOptions" label="Type d’objet" clearable hide-details density="compact" /></v-col>
      </v-row>
      <v-select class="mt-3" v-model="state.searchMode" :items="[{ title: 'Contient (sous-chaîne)', value: 'contains' }, { title: 'Recherche plein texte (mots)', value: 'fulltext' }]" label="Mode de recherche" density="compact" hide-details style="max-width: 420px" />
      <div v-if="state.currentPage === 'table'" class="text-caption mt-2">Les filtres par nom et valeur d’attribut s’appliquent à cette page.</div>
    </v-card-text>
  </v-card>
</template>
<script setup>
defineProps({ state: { type: Object, required: true } });
</script>
