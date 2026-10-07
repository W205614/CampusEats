import {createApp} from 'vue';
import {createPinia} from 'pinia';
import {ElButton,ElDatePicker,ElDialog,ElDivider,ElDrawer,ElForm,ElFormItem,ElInput,ElInputNumber,ElOption,ElPagination,ElRadio,ElRadioGroup,ElSelect,ElSwitch,ElTable,ElTableColumn,ElTag,ElUpload,provideGlobalConfig} from 'element-plus';
import zhCn from 'element-plus/es/locale/lang/zh-cn';
import 'element-plus/dist/index.css';import App from './App.vue';import {router} from './router';import './style.css';
const app=createApp(App).use(createPinia()).use(router);
provideGlobalConfig({locale:zhCn}, app, true);
for(const component of [ElButton,ElDatePicker,ElDialog,ElDivider,ElDrawer,ElForm,ElFormItem,ElInput,ElInputNumber,ElOption,ElPagination,ElRadio,ElRadioGroup,ElSelect,ElSwitch,ElTable,ElTableColumn,ElTag,ElUpload])app.use(component);
app.mount('#app');
