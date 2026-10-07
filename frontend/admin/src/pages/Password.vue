<script setup lang="ts">
import { ref } from "vue";
import { api, body, notifyError } from "../api";
import { useSession } from "../stores/session";
import { ElMessage } from "element-plus";
const oldPassword = ref(""),
  password = ref(""),
  busy = ref(false),
  store = useSession();
async function save() {
  busy.value = true;
  try {
    await api("/auth/password", {
      method: "POST",
      body: body({ oldPassword: oldPassword.value, password: password.value }),
    });
    store.set(null);
    ElMessage.success("密码已更新，请重新登录");
  } catch (e) {
    notifyError(e);
  } finally {
    busy.value = false;
  }
}
</script>
<template>
  <div class="page-heading">
    <div>
      <h2>修改密码</h2>
      <p class="muted">更新后所有旧会话立即失效。</p>
    </div>
  </div>
  <div class="panel" style="max-width: 500px">
    <el-form label-position="top" @submit.prevent="save"
      ><el-form-item label="原密码"
        ><el-input
          v-model="oldPassword"
          type="password"
          show-password /></el-form-item
      ><el-form-item label="新密码（12至128个字符）"
        ><el-input
          v-model="password"
          type="password"
          show-password /></el-form-item
      ><el-button type="primary" native-type="submit" :loading="busy"
        >更新并重新登录</el-button
      ></el-form
    >
  </div>
</template>
