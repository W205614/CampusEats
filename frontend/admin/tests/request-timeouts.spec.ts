import { test, expect } from '@playwright/test';
import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { runInNewContext } from 'node:vm';
import ts from 'typescript';

// Execute the real wrappers with transport doubles; no browser or business writes.
function wrapper(file: string, transport: 'lost' | 'body' | 'malformed' = 'lost', payload: unknown = null) {
  const timeouts: number[] = [];
  let calls = 0;
  class ApiError extends Error {
    constructor(public code: string, message: string, public requestId: string, public status: number) {
      super(message);
    }
  }
  const session = { session: null, set() {} };
  const exports: Record<string, any> = {};
  const source = readFileSync(resolve(file), 'utf8').replaceAll('import.meta.env', '({})');
  const compiled = ts.transpileModule(source, {
    compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022 },
  }).outputText;
  runInNewContext(compiled, {
    exports, Headers, FormData,
    require(name: string) {
      if (name.includes('api-error')) return { ApiError };
      if (name.includes('session')) return { useSession: () => session };
      if (name === 'element-plus') return { ElMessage: { error() {} } };
      throw new Error('Unexpected module ' + name);
    },
    AbortSignal: { timeout(ms: number) { timeouts.push(ms); return new AbortController().signal; } },
    async fetch() {
      calls++;
      if (transport === 'lost') throw new Error('connection lost');
      return { ok: true, status: 200, json: async () => {
        if (transport === 'body') throw new Error('response body lost');
        return payload;
      } };
    },
    uni: { request(options: { timeout: number; fail(): void; success(response: unknown): void }) {
      calls++;
      timeouts.push(options.timeout);
      if (transport === 'malformed') options.success({ statusCode: 200, data: payload });
      else options.fail();
    } },
  });
  return { exports, timeouts, calls: () => calls };
}

for (const [name, file, fn] of [
  ['admin', 'src/api.ts', 'api'],
  ['platform', 'src/platform-api.ts', 'request'],
  ['client', '../client/src/api.ts', 'api'],
] as const) {
  test(`${name} malformed responses settle writes without replay or false success`, async () => {
    for (const payload of [null, 'upstream unavailable', {}, { code: 'OK' }]) {
      const w = wrapper(file, 'malformed', payload);
      const result = name === 'admin'
        ? w.exports[fn]('/orders/1/action', { method: 'POST', body: '{}' })
        : w.exports[fn]('/orders/1/action', 'POST', {});
      const outcome = await Promise.race([
        result.then(() => 'unexpected success', (e: unknown) => e),
        new Promise(resolve => setTimeout(() => resolve('unsettled request'), 1000)),
      ]);
      expect(outcome).toMatchObject({ code: 'NETWORK', message: '操作结果尚未确认，请刷新查看', status: 0 });
      expect(w.calls()).toBe(1);
    }
  });
  test(`${name} transport failure leaves writes unresolved and never replays them`, async () => {
    const w = wrapper(file);
    const write = name === 'admin'
      ? () => w.exports[fn]('/orders/1/action', { method: 'POST', body: '{}' })
      : () => w.exports[fn]('/orders/1/action', 'POST', {});
    const error = await write().catch((e: unknown) => e);
    expect(error).toMatchObject({ code: 'NETWORK', message: '操作结果尚未确认，请刷新查看', status: 0 });
    expect(w.calls()).toBe(1);
    expect(w.timeouts).toEqual([25000]);
    const readError = await w.exports[fn]('/orders').catch((e: unknown) => e);
    expect(readError).toMatchObject({ message: '网络连接失败，请重试' });
    expect(w.calls()).toBe(2);
  });
  if (name !== 'client') {
    test(`${name} response body disconnect also preserves an unknown write result`, async () => {
      const w = wrapper(file, 'body');
      const write = name === 'admin'
        ? w.exports[fn]('/orders/1/action', { method: 'POST', body: '{}' })
        : w.exports[fn]('/orders/1/action', 'POST', {});
      const error = await write.catch((e: unknown) => e);
      expect(error).toMatchObject({ code: 'NETWORK', message: '操作结果尚未确认，请刷新查看' });
      expect(w.calls()).toBe(1);
    });
  }
}

for (const [name, file, fn] of [
  ['admin', 'src/api.ts', 'api'],
  ['platform', 'src/platform-api.ts', 'request'],
  ['client', '../client/src/api.ts', 'api'],
] as const) {
  test(`${name} delayed 401 preserves a newer session and current 401 still signs out without replay`, async () => {
    const session = {
      session: { token: 'old-session' } as { token: string } | null,
      set(value: { token: string } | null) { this.session = value; },
    };
    let finish!: () => void, calls = 0;
    const exports: Record<string, any> = {};
    const source = readFileSync(resolve(file), 'utf8').replaceAll('import.meta.env', '({})');
    class ApiError extends Error {
      constructor(public code: string, message: string, public requestId: string, public status: number) { super(message); }
    }
    runInNewContext(ts.transpileModule(source, {
      compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022 },
    }).outputText, {
      exports, Headers, FormData, AbortSignal,
      require(module: string) {
        if (module.includes('api-error')) return { ApiError };
        if (module.includes('session')) return { useSession: () => session };
        if (module === 'element-plus') return { ElMessage: { error() {} } };
        throw new Error('Unexpected module ' + module);
      },
      fetch() {
        calls++;
        return new Promise(resolve => { finish = () => resolve({ ok: false, status: 401,
          json: async () => ({ code: 'UNAUTHORIZED', message: 'expired' }) }); });
      },
      uni: { request(options: { success(value: unknown): void }) {
        calls++;
        finish = () => options.success({ statusCode: 401, data: { code: 'UNAUTHORIZED', message: 'expired' } });
      } },
    });
    const oldRequest = exports[fn]('/orders').catch((error: unknown) => error);
    session.session = { token: 'new-session' };
    finish();
    expect(await oldRequest).toMatchObject({ code: 'UNAUTHORIZED', status: 401 });
    expect(session.session?.token).toBe('new-session');
    expect(calls).toBe(1);
    const currentRequest = exports[fn]('/orders').catch((error: unknown) => error);
    finish();
    expect(await currentRequest).toMatchObject({ code: 'UNAUTHORIZED', status: 401 });
    expect(session.session).toBeNull();
    expect(calls).toBe(2);
  });
}
