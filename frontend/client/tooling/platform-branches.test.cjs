const assert = require('node:assert/strict');
const { readFileSync } = require('node:fs');
const { resolve } = require('node:path');
const { runInNewContext } = require('node:vm');
const test = require('node:test');
const { transformSync } = require('esbuild');
const { initPreContext, preJs } = require('@dcloudio/uni-cli-shared/dist/preprocess');
const { initDefine } = require('@dcloudio/uni-cli-shared/dist/env/define');

const input = resolve(__dirname, '../src');
function compile(file, platform) {
  const previousPlatform = process.env.UNI_PLATFORM;
  const previousInput = process.env.UNI_INPUT_DIR;
  process.env.UNI_PLATFORM = platform;
  process.env.UNI_INPUT_DIR = input;
  try {
    initPreContext(platform);
    return transformSync(preJs(readFileSync(resolve(input, file), 'utf8')), {
      loader: 'ts',
      format: 'cjs',
      minifySyntax: true,
      define: {
        'process.env.UNI_PLATFORM': initDefine()['process.env.UNI_PLATFORM'],
        'import.meta.env': '{}',
      },
    }).code;
  } finally {
    if (previousPlatform === undefined) delete process.env.UNI_PLATFORM;
    else process.env.UNI_PLATFORM = previousPlatform;
    if (previousInput === undefined) delete process.env.UNI_INPUT_DIR;
    else process.env.UNI_INPUT_DIR = previousInput;
  }
}
function execute(code, globals = {}, modules = {}) {
  const module = { exports: {} };
  runInNewContext(code, {
    module,
    exports: module.exports,
    URL,
    require(name) {
      assert.ok(Object.hasOwn(modules, name), 'Unexpected dependency: ' + name);
      return modules[name];
    },
    ...globals,
  });
  return module.exports;
}

for (const platform of ['h5', 'mp-weixin']) {
  test(`${platform} image URLs use the platform origin without accessing the other platform`, () => {
    const code = compile('api.ts', platform);
    assert.doesNotMatch(code, /process\.env\.UNI_PLATFORM/);
    const api = execute(code, platform === 'h5' ? { window: { location: { origin: 'https://campus.example' } } } : {}, {
      './stores/session': {},
      '../../contracts/api-error': {},
    });
    assert.equal(api.image('/uploads/menu.png'), platform === 'h5'
      ? 'https://campus.example/uploads/menu.png' : 'http://localhost:18083/uploads/menu.png');
    assert.equal(api.image('https://cdn.example/menu.png'), 'https://cdn.example/menu.png');
  });

  test(`${platform} session restores from its own storage`, () => {
    const saved = { token: 'synthetic-session' };
    const session = execute(compile('stores/session.ts', platform), platform === 'h5'
      ? { sessionStorage: { getItem: () => JSON.stringify(saved) } }
      : { uni: { getStorageSync: () => saved } }, {
      pinia: { defineStore: (_id, setup) => setup },
      vue: { ref: (value) => ({ value }) },
    });
    assert.equal(session.useSession().session.value.token, saved.token);
  });
}

for (const [platform, agent, expected] of [
  ['h5', 'MicroMessenger', 'JSAPI'],
  ['h5', 'Android Mobile', 'H5'],
  ['h5', 'Desktop Browser', 'NATIVE'],
  ['mp-weixin', undefined, 'MINI_PROGRAM'],
]) {
  test(`${platform} payment selects ${expected} before creating the intent`, async () => {
    class ApiError extends Error {
      code = 'PAYMENT_NOT_FOUND';
    }
    let channel;
    const payment = execute(compile('payments.ts', platform), agent ? { navigator: { userAgent: agent } } : {}, {
      './api': {
        ApiError,
        async api(_path, method, data) {
          if (method !== 'POST') throw new ApiError();
          channel = data.channel;
          return { state: 'SUCCEEDED' };
        },
      },
    });
    await payment.startPayment('checkout', 'version');
    assert.equal(channel, expected);
  });
}

test('mini-program payment compilation removes the browser QR dependency', () => {
  assert.doesNotMatch(compile('payments.ts', 'mp-weixin'), /qrcode|navigator|window/);
  assert.match(compile('payments.ts', 'h5'), /qrcode/);
});
