const { EventEmitter } = require('node:events');
const path = require('node:path');
const chokidar = require('chokidar');
const picomatch = require('picomatch');
const glob = require('./index.cjs');
const { isDynamicPattern } = require('tinyglobby');

// uni-app FileWatcher always supplies relative copy patterns and an explicit input cwd.
// Watch directories so newly created matching files are seen, not only today's glob results.
function watch(input, options = {}) {
  if (!options.cwd) throw new Error('uni-app copy watcher requires cwd');
  if (options.ignored && typeof options.ignored !== 'function') throw new Error('uni-app copy ignored must be a function');
  const cwd = path.resolve(options.cwd);
  let includes = [], excludes = [];
  const positive = new Set(), negative = new Set();
  const watcher = new EventEmitter();
  watcher.options = { ...options, cwd };
  function compile(list) {
    return glob.validatePatterns(list).map(p => {
      const value = p.replace(/\\/g, '/').replace(/^\.\//, '');
      if (path.isAbsolute(value) || value.split('/').includes('..')) throw new Error('Copy patterns must stay inside cwd');
      const exact = picomatch(value, { dot: options.dot !== false });
      const subtree = !isDynamicPattern(value) && picomatch(value === '.' ? '**/*' : value + '/**/*', { dot: options.dot !== false });
      return file => exact(file) || (subtree && subtree(file));
    });
  }
  function addPatterns(value) {
    const list = glob.validatePatterns(value);
    compile(list.map(p => p.startsWith('!') ? p.slice(1) : p));
    for (const p of list) {
      if (p.startsWith('!')) negative.add(p.slice(1));
      else { positive.add(p); negative.delete(p); }
    }
    includes = compile([...positive]);
    excludes = compile([...negative]);
  }
  addPatterns(input);
  const relative = file => path.relative(cwd, path.resolve(cwd, file)).split(path.sep).join('/');
  function accepts(file) {
    const rel = relative(file);
    return !rel.startsWith('../') && includes.some(match => match(rel)) && !excludes.some(match => match(rel));
  }
  const native = chokidar.watch(cwd, {
    ...options,
    cwd,
    ignored: (file, stat) => {
      if (options.ignored?.(path.resolve(cwd, file), stat)) return true;
      return stat?.isFile() && !accepts(file);
    },
  });
  native.on('all', (event, file, stat) => {
    if (['add', 'change', 'unlink'].includes(event) && accepts(file)) {
      watcher.emit(event, file, stat);
      watcher.emit('all', event, file, stat);
    }
  });
  native.on('ready', () => watcher.emit('ready'));
  native.on('error', error => watcher.emit('error', error));
  watcher.close = () => native.close();
  watcher.getWatched = () => native.getWatched();
  watcher.add = value => {
    addPatterns(value);
    for (const file of glob.sync(value, { cwd, dot: options.dot !== false, absolute: true })) native.add(file);
    return watcher;
  };
  watcher.unwatch = value => {
    for (const p of glob.validatePatterns(value)) { positive.delete(p); negative.add(p); }
    includes = compile([...positive]);
    excludes = compile([...negative]);
    return watcher;
  };
  return watcher;
}
module.exports = { watch };
