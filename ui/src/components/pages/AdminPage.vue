<template>
  <v-row class="mb-6">
    <v-col cols="12">
      <div class="kicker">Administration</div>
      <h2 class="headline" style="font-size: clamp(1.6rem, 2.5vw, 2.4rem);">
        Gérez les modèles, les utilisateurs et leurs droits.
      </h2>
      <p class="subhead">
        Le portail administration permet de gérer la maintenance du modèle et la matrice d'accès par utilisateur.
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
              :disabled="!state.adminModelKey || !state.canAccessModelAdminPortal"
              @click="state.deleteModel"
            >
              Supprimer via l'API
            </v-btn>
            <v-btn
              color="secondary"
              variant="tonal"
              :disabled="!state.adminModelKey || !state.canAccessModelAdminPortal"
              @click="state.copyAdminCommand"
            >
              Copier la commande CLI
            </v-btn>
            <v-btn
              color="primary"
              variant="tonal"
              :disabled="!state.adminModelKey || !state.canAccessModelAdminPortal"
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

  <v-row class="mt-8">
    <v-col cols="12" v-if="state.canManageAccess">
      <v-card class="card-animate delay-1" elevation="4" rounded="xl">
        <v-card-title class="section-title">Utilisateurs et accès portail</v-card-title>
        <v-card-text>
          <v-row>
            <v-col cols="12" md="5">
              <v-select
                v-model="state.adminAccessUserKey"
                :items="state.userOptions"
                label="Utilisateur"
                prepend-icon="mdi-account"
                variant="outlined"
                density="comfortable"
                clearable
                :loading="state.isLoadingUsers"
              />
            </v-col>
            <v-col cols="12" md="4">
              <v-text-field
                v-model="state.newAccessUsername"
                label="Nouvel identifiant"
                hint="A-Z a-z 0-9 . _ -"
                persistent-hint
                variant="outlined"
                density="comfortable"
              />
            </v-col>
            <v-col cols="12" md="3">
              <v-text-field
                v-model="state.newAccessDisplayName"
                label="Nom affiché"
                variant="outlined"
                density="comfortable"
              />
            </v-col>
          </v-row>

          <div class="d-flex flex-wrap align-center" style="gap: 12px;">
            <v-btn
              color="primary"
              variant="flat"
              :loading="state.isSavingAccessUser"
              @click="state.createAccessUser"
            >
              Créer l'utilisateur
            </v-btn>
            <v-btn
              color="secondary"
              variant="tonal"
              :disabled="!state.adminAccessUserKey"
              @click="state.impersonateUser(state.adminAccessUserKey)"
            >
              Impersoner l'utilisateur sélectionné
            </v-btn>
            <v-btn
              v-if="state.authMeta.impersonating"
              color="warning"
              variant="tonal"
              @click="state.stopImpersonation"
            >
              Arrêter l'impersonation
            </v-btn>
          </div>

          <v-divider class="my-4" />

          <v-row>
            <v-col cols="12" md="4">
              <v-text-field
                v-model="state.adminAccessForm.username"
                label="Identifiant"
                variant="outlined"
                density="comfortable"
                disabled
              />
            </v-col>
            <v-col cols="12" md="4">
              <v-text-field
                v-model="state.adminAccessForm.displayName"
                label="Nom affiché"
                variant="outlined"
                density="comfortable"
              />
            </v-col>
            <v-col cols="12" md="4">
              <v-text-field
                v-model="state.adminAccessForm.password"
                label="Nouveau mot de passe"
                type="password"
                hint="Laisser vide pour conserver"
                persistent-hint
                variant="outlined"
                density="comfortable"
              />
            </v-col>
          </v-row>

          <div class="d-flex flex-wrap" style="gap: 24px;">
            <v-switch
              v-model="state.adminAccessForm.portalUser"
              label="Accès portail métier"
              color="primary"
              inset
            />
            <v-switch
              v-model="state.adminAccessForm.portalModelAdmin"
              label="Accès portail administration"
              color="secondary"
              inset
            />
            <v-switch
              v-model="state.adminAccessForm.platformAdmin"
              label="Admin plateforme"
              color="accent"
              inset
            />
          </div>

          <div class="d-flex flex-wrap align-center" style="gap: 12px;">
            <v-btn
              color="primary"
              variant="flat"
              :loading="state.isSavingAccessUser"
              :disabled="!state.adminAccessForm.username"
              @click="state.saveAccessUser"
            >
              Sauvegarder le profil
            </v-btn>
            <v-btn
              color="secondary"
              variant="tonal"
              :disabled="!state.adminAccessForm.username"
              @click="state.impersonateUser(state.adminAccessForm.username)"
            >
              Impersoner
            </v-btn>
            <v-btn
              color="error"
              variant="tonal"
              :loading="state.isDeletingAccessUser"
              :disabled="!state.adminAccessForm.username || state.adminAccessForm.username === 'admin'"
              @click="state.deleteAccessUser"
            >
              Supprimer l'utilisateur
            </v-btn>
          </div>

          <v-alert
            v-if="state.adminAccessStatus"
            class="mt-4"
            :type="state.adminAccessStatus.type"
            variant="tonal"
            density="comfortable"
            border="start"
          >
            {{ state.adminAccessStatus.message }}
          </v-alert>
          <v-alert
            v-if="state.authMeta.impersonating"
            class="mt-4"
            type="warning"
            variant="tonal"
            density="comfortable"
            border="start"
          >
            Session en impersonation : {{ state.authMeta.effectiveUsername }}
            (admin source : {{ state.authMeta.actorUsername }}).
          </v-alert>
        </v-card-text>
      </v-card>
    </v-col>

    <v-col cols="12" v-else>
      <v-alert type="info" variant="tonal" border="start">
        Vous n'avez pas le rôle administrateur plateforme pour gérer les utilisateurs et permissions.
      </v-alert>
    </v-col>
  </v-row>

  <v-row class="mt-8" v-if="state.canManageAccess">
    <v-col cols="12">
      <v-card class="card-animate delay-2" elevation="4" rounded="xl">
        <v-card-title class="section-title">Droits par modèle (CRUD données)</v-card-title>
        <v-card-text>
          <div class="text-medium-emphasis mb-3">
            Définissez les modèles visibles par l'utilisateur et ses droits CRUD sur les données de chaque modèle.
          </div>

          <v-table density="compact">
            <thead>
              <tr>
                <th>Modèle</th>
                <th>Visible</th>
                <th>R</th>
                <th>C</th>
                <th>U</th>
                <th>D</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="permission in state.adminAccessPermissions" :key="permission.modelKey">
                <td>
                  {{ permission.modelName }}
                  <span v-if="permission.modelVersion" class="text-medium-emphasis">v{{ permission.modelVersion }}</span>
                </td>
                <td><v-checkbox v-model="permission.visible" hide-details density="compact" /></td>
                <td><v-checkbox v-model="permission.canRead" hide-details density="compact" /></td>
                <td><v-checkbox v-model="permission.canCreate" hide-details density="compact" /></td>
                <td><v-checkbox v-model="permission.canUpdate" hide-details density="compact" /></td>
                <td><v-checkbox v-model="permission.canDelete" hide-details density="compact" /></td>
              </tr>
            </tbody>
          </v-table>

          <div class="d-flex flex-wrap align-center mt-4" style="gap: 12px;">
            <v-btn
              color="primary"
              variant="flat"
              :loading="state.isSavingAccessPermissions"
              :disabled="!state.adminAccessForm.username"
              @click="state.saveAccessPermissions"
            >
              Sauvegarder les permissions
            </v-btn>
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
