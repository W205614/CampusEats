const fs = require('node:fs');
const path = require('node:path');
const crypto = require('node:crypto');
const patches = require('./compat-patches.json');
const hash = text => crypto.createHash('sha256').update(text).digest('hex');

function applyPatches(root = path.resolve(__dirname, '..'), checkOnly = false) {
  const writes = [];
  // Validate the entire set before changing anything. Unknown upstream bytes fail closed.
  for (const patch of patches) {
    const folder = path.join(root, 'node_modules', patch.package);
    const manifest = JSON.parse(fs.readFileSync(path.join(folder, 'package.json'), 'utf8'));
    if (manifest.version !== patch.version) throw new Error(`Review compatibility patch for ${patch.package}@${manifest.version}`);
    const target = path.join(folder, patch.file);
    const current = fs.readFileSync(target, 'utf8');
    if (hash(current) === patch.after) continue;
    if (hash(current) !== patch.before) throw new Error(`Unexpected upstream content: ${patch.package}/${patch.file}`);
    if (checkOnly) throw new Error(`Compatibility patch missing: ${patch.package}/${patch.file}`);
    let changed = current;
    for (const [from, to] of patch.edits) {
      if (changed.split(from).length !== 2) throw new Error(`Ambiguous compatibility patch: ${patch.file}`);
      changed = changed.replace(from, to);
    }
    if (hash(changed) !== patch.after) throw new Error(`Compatibility patch hash differs: ${patch.file}`);
    writes.push({ target, changed });
  }
  for (const { target, changed } of writes) fs.writeFileSync(target, changed);
  return writes.length;
}
if (require.main === module) console.log(`Applied ${applyPatches()} reviewed uni-app compatibility patches.`);
module.exports = { applyPatches, patches };
