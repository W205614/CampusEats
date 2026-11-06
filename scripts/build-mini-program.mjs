import fs from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { spawnSync } from 'node:child_process';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const appid = process.env.WECHAT_APPID || '';
const origin = process.env.PUBLIC_BASE_URL || '';
if (!/^wx[0-9a-f]{16}$/.test(appid)) throw new Error('WECHAT_APPID must be the real wx AppID.');
const url = new URL(origin);
if (url.protocol !== 'https:' || url.origin !== origin)
  throw new Error('PUBLIC_BASE_URL must be an HTTPS origin without a trailing slash.');
if (!process.env.npm_execpath)
  throw new Error(
    'Run this entry with npm run build:mp-weixin:production --prefix frontend/client.',
  );
const build = spawnSync(process.execPath, [process.env.npm_execpath, 'run', 'build:mp-weixin'], {
  cwd: path.join(root, 'frontend/client'),
  env: { ...process.env, VITE_API_BASE: origin },
  stdio: 'inherit',
});
if (build.status !== 0) process.exit(build.status || 1);
const target = path.join(root, 'frontend/client/dist/build/mp-weixin/project.config.json');
const project = JSON.parse(await fs.readFile(target, 'utf8'));
project.appid = appid;
project.setting = { ...project.setting, urlCheck: true };
await fs.writeFile(target, JSON.stringify(project, null, 2) + '\n');
console.log(
  'Built production mini-program with AppID and HTTPS API origin; import frontend/client/dist/build/mp-weixin into WeChat DevTools.',
);
