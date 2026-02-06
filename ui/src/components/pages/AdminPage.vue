<template>
  <v-row class="mb-6">
    <v-col cols="12">
      <div class="kicker">Administration</div>
      <h2 class="headline" style="font-size: clamp(1.6rem, 2.5vw, 2.4rem);">
        Supprimez un modèle et ses données associées.
      </h2>
      <p class="subhead">
        Cette action efface le modèle stocké et toutes les données importées liées à ce modèle.
      </p>
    </v-col>
  </v-row>

  <v-row>
    <v-col cols="12" md="6">
      <v-card class="card-animate delay-1" elevation="4" rounded="xl">
        <v-card-title class="section-title">Suppression du modèle</v-card-title>
        <v-card-text>
          <v-select
            v-model="state.adminModelKey"
            :items="state.modelOptions"
            label="Modèle à supprimer"
            prepend-icon="mdi-database-remove"
            variant="outlined"
            density="comfortable"
            clearable
          />
          <div class="file-hint">
            Sélectionnez un modèle chargé en base pour le supprimer.
          </div>

          <v-alert
            class="mt-4"
            type="warning"
            variant="tonal"
            density="comfortable"
            icon="mdi-alert-circle-outline"
            border="start"
          >
            La suppression du modèle entraîne la suppression de toutes les données associées.
          </v-alert>

          <div class="d-flex flex-wrap align-center mt-4" style="gap: 12px;">
            <v-btn
              color="error"
              variant="flat"
              :disabled="!state.adminModelKey"
              @click="state.deleteModel"
            >
              Supprimer via l'API
            </v-btn>
            <v-btn
              color="secondary"
              variant="tonal"
              :disabled="!state.adminModelKey"
              @click="state.copyAdminCommand"
            >
              Copier la commande CLI
            </v-btn>
            <v-btn
              color="primary"
              variant="tonal"
              :disabled="!state.adminModelKey"
              @click="state.adminCommandVisible = !state.adminCommandVisible"
            >
              {{ state.adminCommandVisible ? 'Masquer la commande' : 'Voir la commande' }}
            </v-btn>
          </div>

          <v-alert
            v-if="state.adminStatus"
            class="mt-4"
            :type="state.adminStatus.type"
            variant="tonal"
            density="comfortable"
            border="start"
          >
            {{ state.adminStatus.message }}
          </v-alert>
        </v-card-text>
      </v-card>
    </v-col>

    <v-col cols="12" md="6">
      <v-card class="card-animate delay-2" elevation="4" rounded="xl">
        <v-card-title class="section-title">Commande CLI</v-card-title>
        <v-card-text>
          <div v-if="state.adminCommandVisible" class="code-block">
            <pre><code>{{ state.adminCommand }}</code></pre>
          </div>
          <div v-else class="text-medium-emphasis">
            Cliquez sur “Voir la commande” pour afficher la suppression CLI.
          </div>
          <v-divider class="my-4" />
          <div class="text-caption text-medium-emphasis">
            Cette commande utilise le CLI Java pour supprimer le modèle et ses données dans Neo4j.
            Assurez-vous que les variables <code>NEO4J_*</code> sont correctement définies.
          </div>
        </v-card-text>
      </v-card>
    </v-col>
  </v-row>
</template>

<script setup>
const props = defineProps({
  state: {
    type: Object,
    required: true
  }
});

const state = props.state;
</script>
