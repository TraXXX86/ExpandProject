import { h } from 'vue';
import { aliases, mdi } from 'vuetify/iconsets/mdi-svg';
import { mdiAccessPoint, mdiAccount, mdiAccountArrowRightOutline, mdiAccountCancelOutline, mdiAccountGroupOutline, mdiAccountSwitchOutline, mdiAlertCircleOutline, mdiArrowLeft, mdiArrowLeftRight, mdiArrowRight, mdiChevronDown, mdiChevronRight, mdiClose, mdiCogOutline, mdiConnection, mdiCubeOutline, mdiDatabaseCheck, mdiDatabaseEyeOutline, mdiDatabaseRefresh, mdiDatabaseRemove, mdiDeleteOutline, mdiEyeOutline, mdiFileCode, mdiFileTree, mdiFilterOutline, mdiGraphOutline, mdiInformationOutline, mdiKeyOutline, mdiLinkVariant, mdiLockOutline, mdiLogin, mdiMagnify, mdiOpenInNew, mdiRestore, mdiShapeOutline, mdiShieldCrownOutline, mdiSourceBranch, mdiTarget, mdiTextBoxSearchOutline, mdiTuneVariant, mdiUploadOutline } from '@mdi/js';

const icons = {
  'mdi-access-point': mdiAccessPoint,
  'mdi-account': mdiAccount,
  'mdi-account-arrow-right-outline': mdiAccountArrowRightOutline,
  'mdi-account-cancel-outline': mdiAccountCancelOutline,
  'mdi-account-group-outline': mdiAccountGroupOutline,
  'mdi-account-switch-outline': mdiAccountSwitchOutline,
  'mdi-alert-circle-outline': mdiAlertCircleOutline,
  'mdi-arrow-left': mdiArrowLeft,
  'mdi-arrow-left-right': mdiArrowLeftRight,
  'mdi-arrow-right': mdiArrowRight,
  'mdi-chevron-down': mdiChevronDown,
  'mdi-chevron-right': mdiChevronRight,
  'mdi-close': mdiClose,
  'mdi-cog-outline': mdiCogOutline,
  'mdi-connection': mdiConnection,
  'mdi-cube-outline': mdiCubeOutline,
  'mdi-database-check': mdiDatabaseCheck,
  'mdi-database-eye-outline': mdiDatabaseEyeOutline,
  'mdi-database-refresh': mdiDatabaseRefresh,
  'mdi-database-remove': mdiDatabaseRemove,
  'mdi-delete-outline': mdiDeleteOutline,
  'mdi-eye-outline': mdiEyeOutline,
  'mdi-file-code': mdiFileCode,
  'mdi-file-tree': mdiFileTree,
  'mdi-filter-outline': mdiFilterOutline,
  'mdi-graph-outline': mdiGraphOutline,
  'mdi-information-outline': mdiInformationOutline,
  'mdi-key-outline': mdiKeyOutline,
  'mdi-link-variant': mdiLinkVariant,
  'mdi-lock-outline': mdiLockOutline,
  'mdi-login': mdiLogin,
  'mdi-magnify': mdiMagnify,
  'mdi-open-in-new': mdiOpenInNew,
  'mdi-restore': mdiRestore,
  'mdi-shape-outline': mdiShapeOutline,
  'mdi-shield-crown-outline': mdiShieldCrownOutline,
  'mdi-source-branch': mdiSourceBranch,
  'mdi-target': mdiTarget,
  'mdi-text-box-search-outline': mdiTextBoxSearchOutline,
  'mdi-tune-variant': mdiTuneVariant,
  'mdi-upload-outline': mdiUploadOutline
};
export const iconConfig = {
  defaultSet: 'mdi', aliases,
  sets: { mdi: { component: props => h(mdi.component, { ...props, icon: icons[props.icon] || props.icon }) } }
};
