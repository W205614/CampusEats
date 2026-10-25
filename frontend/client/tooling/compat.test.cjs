const assert = require('node:assert/strict');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const { createRequire } = require('node:module');
const { once } = require('node:events');
const { setTimeout: delay } = require('node:timers/promises');
const test = require('node:test');
const glob = require('fast-glob');
const { watch } = require('fast-glob/watch');
const { applyPatches, patches } = require('./apply-compat-patches.cjs');

function fixture(t) {
  const root = fs.mkdtempSync(path.join(os.tmpdir(), 'campuseats [copy] '));
  t.after(() => {
    assert.equal(path.dirname(root), path.resolve(os.tmpdir()));
    assert.ok(path.basename(root).startsWith('campuseats [copy] '));
    fs.rmSync(root, { recursive: true, force: true });
  });
  return root;
}
function put(root, name, content = name) {
  const file = path.join(root, name);
  fs.mkdirSync(path.dirname(file), { recursive: true });
  fs.writeFileSync(file, content);
}
async function until(check) {
  const deadline = Date.now() + 10000;
  while (!check()) {
    if (Date.now() > deadline) assert.fail('Expected file watcher effect did not arrive');
    await delay(30);
  }
}

test('all pinned compiler consumers resolve the replacement glob API', () => {
  for (const name of ['uni-cli-shared', 'uni-cloud', 'vite-plugin-uni']) {
    const from = createRequire(require.resolve('@dcloudio/' + name + '/package.json'));
    assert.equal(from('fast-glob'), glob);
  }
  assert.equal(applyPatches(undefined, true), 0);
});

test('glob preserves compiler scans, ignores and literal directory semantics', async t => {
  const cwd = fixture(t);
  for (const name of ['static/a.png', 'static/b.jpg', 'static/no.txt', 'uni_modules/mod/static/c.png', 'uni_modules/mod/utssdk/a.ts', '.hidden.json']) put(cwd, name);
  const pattern = ['static/*.{png,jpg}', 'uni_modules/*/static/**/*'];
  const expected = ['static/a.png', 'static/b.jpg', 'uni_modules/mod/static/c.png'];
  assert.deepEqual(glob.sync(pattern, { cwd }).sort(), expected);
  assert.deepEqual((await glob(pattern, { cwd })).sort(), expected);
  assert.deepEqual(glob.sync('static', { cwd }), []);
  assert.deepEqual(glob.sync('uni_modules/*/utssdk', { cwd, onlyDirectories: true }), ['uni_modules/mod/utssdk']);
  assert.deepEqual(glob.sync('**/*.png', { cwd, ignore: ['**/uni_modules/**'], absolute: true }), [path.join(cwd, 'static/a.png').split(path.sep).join('/')]);
  assert.deepEqual(glob.sync(['static/*', '!**/*.txt'], { cwd }).sort(), expected.slice(0, 2));
  assert.deepEqual(glob.sync('*.json', { cwd }), []);
  assert.deepEqual(glob.sync('*.json', { cwd, dot: true }), ['.hidden.json']);
  assert.throws(() => glob.sync('{'.repeat(40) + 'x' + '}'.repeat(40)), /nesting/);
  assert.throws(() => glob.sync('}'.repeat(40) + '{'.repeat(40) + 'x'), /nesting/);
  assert.throws(() => glob.sync('x'.repeat(4097)), /4096/);
  assert.throws(() => glob.sync('*', { deep: 2 }), /Unsupported/);
  assert.throws(() => watch('../*', { cwd }), /inside cwd/);
  assert.throws(() => watch('..\\*', { cwd }), /inside cwd/);
});

test('real uni-app FileWatcher copies initial, added, changed and removed resources', async t => {
  const root = fixture(t), cwd = path.join(root, 'src'), dest = path.join(root, 'dist');
  put(cwd, 'static/first.txt', 'initial');
  put(cwd, 'static/mp-weixin/excluded.txt');
  put(cwd, 'unrelated.txt');
  const { FileWatcher } = require('@dcloudio/uni-cli-shared/dist/watcher.js');
  const copier = new FileWatcher({ src: ['static/**/*', 'uni_modules/*/static/**/*'], dest });
  let ready;
  const loaded = new Promise(resolve => { ready = resolve; });
  const native = copier.watch({ cwd, persistent: true, readyTimeout: 25, ignored: file => file.includes(path.join('static', 'mp-weixin')) }, ready);
  t.after(() => copier.close());
  native.on('error', error => { throw error; });
  await Promise.race([loaded, delay(10000, undefined, { ref: false }).then(() => { throw new Error('Copy watcher ready timed out'); })]);
  assert.equal(fs.readFileSync(path.join(dest, 'static/first.txt'), 'utf8'), 'initial');
  assert.equal(fs.existsSync(path.join(dest, 'unrelated.txt')), false);
  assert.equal(fs.existsSync(path.join(dest, 'static/mp-weixin')), false);
  put(cwd, 'uni_modules/new/static/nested/new.txt', 'added');
  await until(() => fs.existsSync(path.join(dest, 'uni_modules/new/static/nested/new.txt')));
  put(cwd, 'static/first.txt', 'changed');
  await until(() => fs.readFileSync(path.join(dest, 'static/first.txt'), 'utf8') === 'changed');
  fs.unlinkSync(path.join(cwd, 'static/first.txt'));
  await until(() => !fs.existsSync(path.join(dest, 'static/first.txt')));
});

test('watch add, unwatch and re-add retain future matching files', async t => {
  const cwd = fixture(t);
  put(cwd, 'static/one.txt');
  const events = [];
  const watcher = watch('static/**/*', { cwd });
  t.after(() => watcher.close());
  watcher.on('all', (event, file) => events.push([event, file.split(path.sep).join('/')]));
  await once(watcher, 'ready');
  watcher.add('extra/**/*');
  put(cwd, 'extra/new/deep.txt');
  await until(() => events.some(([e, f]) => e === 'add' && f === 'extra/new/deep.txt'));
  watcher.unwatch('static/**/*');
  // Wait for a real event on an included path, then confirm no excluded event escaped.
  put(cwd, 'static/no.txt');
  put(cwd, 'extra/barrier.txt');
  await until(() => events.some(([, f]) => f === 'extra/barrier.txt'));
  assert.equal(events.some(([, f]) => f === 'static/no.txt'), false);
  watcher.add('static/**/*');
  put(cwd, 'static/back.txt');
  await until(() => events.some(([e, f]) => e === 'add' && f === 'static/back.txt'));
});

test('patches are idempotent and reject all writes on upstream drift', t => {
  const root = fixture(t);
  const originals = [];
  for (const p of patches) {
    const dir = path.join(root, 'node_modules', p.package);
    put(dir, 'package.json', JSON.stringify({ version: p.version }));
    let source = fs.readFileSync(path.join(__dirname, '../node_modules', p.package, p.file), 'utf8');
    for (const [before, after] of p.edits) source = source.replace(after, before);
    originals.push(source);
    put(dir, p.file, source);
  }
  assert.throws(() => applyPatches(root, true), /missing/);
  const last = patches.at(-1), lastFile = path.join(root, 'node_modules', last.package, last.file);
  fs.appendFileSync(lastFile, '// drift');
  assert.throws(() => applyPatches(root), /Unexpected upstream/);
  assert.equal(fs.readFileSync(path.join(root, 'node_modules', patches[0].package, patches[0].file), 'utf8'), originals[0]);
  fs.writeFileSync(lastFile, originals.at(-1));
  assert.equal(applyPatches(root), patches.length);
  assert.equal(applyPatches(root), 0);
  put(path.join(root, 'node_modules', last.package), 'package.json', JSON.stringify({ version: 'future' }));
  assert.throws(() => applyPatches(root), /Review compatibility patch/);
});

test('actual WeChat automator decodes a QR image through Jimp v1', async () => {
  const image = fs.readFileSync(path.join(__dirname, 'fixtures/qr.png'));
  const { Jimp } = require('jimp');
  const bitmap = (await Jimp.read(image)).bitmap;
  assert.ok(bitmap.width > 100 && bitmap.height > 100);
  const automator = require('@dcloudio/uni-mp-weixin/lib/uni.automator.js');
  const result = await automator.adapter['Tool.enableRemoteDebug'].reflect(async (name, args, flag) => {
    assert.equal(name, 'Tool.enableRemoteDebug');
    assert.equal(flag, false);
    return { qrCode: image.toString('base64') };
  }, {});
  assert.deepEqual(result, { qrCode: 'campuseats-toolchain-compatibility' });
});

test('Jimp CommonJS and ESM image transforms retain validation with the patched Zod dependency', async () => {
  for (const { Jimp, ResizeStrategy } of [require('jimp'), await import('jimp')]) {
    const source = new Jimp({ width: 6, height: 4, color: 0x000000ff });
    for (let y = 0; y < 4; y++) for (let x = 0; x < 6; x++)
      source.setPixelColor((((x + 1) << 24) | ((y + 1) << 16) | 0xff) >>> 0, x, y);
    const decoded = await Jimp.read(await source.getBuffer('image/png'));
    assert.equal(decoded.getPixelColor(2, 1), source.getPixelColor(2, 1));
    const resized = decoded.clone().resize({ w: 12, h: 8, mode: ResizeStrategy.NEAREST_NEIGHBOR });
    assert.deepEqual([resized.bitmap.width, resized.bitmap.height], [12, 8]);
    assert.equal(resized.getPixelColor(0, 0), source.getPixelColor(0, 0));
    const cropped = decoded.clone().crop({ x: 1, y: 1, w: 3, h: 2 });
    assert.deepEqual([cropped.bitmap.width, cropped.bitmap.height], [3, 2]);
    assert.equal(cropped.getPixelColor(0, 0), source.getPixelColor(1, 1));
    for (const method of ['cover', 'contain']) {
      const result = decoded.clone()[method]({ w: 8, h: 8 });
      assert.deepEqual([result.bitmap.width, result.bitmap.height], [8, 8]);
    }
    const rotated = decoded.clone().rotate(90);
    assert.deepEqual([rotated.bitmap.width, rotated.bitmap.height], [4, 6]);
    assert.equal(decoded.clone().flip({ horizontal: true }).getPixelColor(0, 0), source.getPixelColor(5, 0));
    assert.equal(decoded.clone().flip({ vertical: true }).getPixelColor(0, 0), source.getPixelColor(0, 3));

    // The print plugin exercises its optional function schema and callback binding.
    const font = { chars: { A: { x: 0, y: 0, width: 1, height: 1, xoffset: 0, yoffset: 0, xadvance: 2, page: 0 } },
      kernings: {}, common: { lineHeight: 2 }, pages: [new Jimp({ width: 1, height: 1, color: 0xffffffff })] };
    const printed = decoded.clone();
    let completed;
    printed.print({ font, x: 1, y: 1, text: 'A', cb: function (point) { completed = { point, image: this }; } });
    assert.deepEqual(completed.point, { x: 3, y: 3 });
    assert.equal(completed.image, printed);
    assert.equal(printed.getPixelColor(1, 1), 0xffffffff);
    assert.throws(() => decoded.clone().resize({ w: '12' }), { name: 'ZodError' });
    assert.throws(() => decoded.clone().print({ font, x: 0, y: 0, text: 'A', cb: 'invalid' }), { name: 'ZodError' });
  }
});
