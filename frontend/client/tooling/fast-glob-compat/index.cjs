const tiny = require('tinyglobby');

// Deliberately support the pinned compiler's API, rather than impersonating all of fast-glob.
const optionNames = new Set(['cwd', 'absolute', 'onlyDirectories', 'onlyFiles', 'ignore', 'dot', 'followSymbolicLinks']);
function patterns(value) {
  const list = typeof value === 'string' ? [value] : value;
  if (!Array.isArray(list) || list.some(p => typeof p !== 'string')) throw new TypeError('Glob patterns must be strings');
  for (const p of list) {
    if (p.length > 4096) throw new RangeError('Glob pattern exceeds 4096 characters');
    let braces = 0, groups = 0;
    for (let i = 0; i < p.length; i++) {
      if (p[i] === '\\') { i++; continue; }
      if (p[i] === '{') braces++;
      if (p[i] === '}') braces = Math.max(0, braces - 1);
      if (p[i] === '(') groups++;
      if (p[i] === ')') groups = Math.max(0, groups - 1);
      if (braces > 32 || groups > 32) throw new RangeError('Glob nesting exceeds 32 levels');
    }
  }
  return list;
}
function options(value = {}) {
  for (const key of Object.keys(value)) if (!optionNames.has(key)) throw new Error(`Unsupported uni-app glob option: ${key}`);
  // fast-glob does not implicitly expand a literal directory.
  return { expandDirectories: false, ...value };
}
// tinyglobby marks directory matches with '/', fast-glob's default does not.
const results = entries => entries.map(file => file.endsWith('/') ? file.slice(0, -1) : file);
async function glob(input, opts) { return results(await tiny.glob(patterns(input), options(opts))); }
glob.sync = (input, opts) => results(tiny.globSync(patterns(input), options(opts)));
glob.async = glob;
glob.validatePatterns = patterns;
module.exports = glob;
