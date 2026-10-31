const path = require('node:path');
const { applyPatches } = require('../../client/tooling/apply-compat-patches.cjs');
const patches = require('./compat-patches.json');

console.log(`Applied ${applyPatches(path.resolve(__dirname, '..'), false, patches)} reviewed Redocly compatibility patch.`);
