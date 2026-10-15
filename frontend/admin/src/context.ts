import { ref } from 'vue';
export const selectedStore = ref(localStorage.getItem('campus-store') || '');
