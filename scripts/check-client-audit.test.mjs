import assert from 'node:assert/strict';
import test from 'node:test';
import { checkAudit } from './check-client-audit.mjs';
const clean = () => ({ vulnerabilities: {}, metadata: { vulnerabilities: { info: 0, low: 0, moderate: 0, high: 0, critical: 0, total: 0 } } });
test('zero findings pass without an exception file', () => assert.match(checkAudit(clean()), /zero known vulnerabilities/));
for (const severity of ['info', 'low', 'moderate', 'high', 'critical']) {
  test(`a ${severity} finding fails the strict gate`, () => {
    const report = clean();
    report.metadata.vulnerabilities[severity] = 1;
    report.metadata.vulnerabilities.total = 1;
    report.vulnerabilities.braces = { name: 'braces', severity, via: ['upstream'] };
    assert.throws(() => checkAudit(report), /Vulnerable dependencies: braces/);
  });
}
test('missing, failed and malformed reports fail closed', () => {
  for (const report of [{}, null, { ...clean(), error: { code: 'EAUDIT' } }, { ...clean(), vulnerabilities: [] }])
    assert.throws(() => checkAudit(report), /valid report/);
  const bad = clean();
  delete bad.metadata.vulnerabilities.high;
  assert.throws(() => checkAudit(bad), /counts are missing/);
});
test('hidden findings and inconsistent severity totals fail closed', () => {
  const hidden = clean();
  hidden.vulnerabilities.phin = { severity: 'moderate' };
  assert.throws(() => checkAudit(hidden), /counts disagree/);
  const inconsistent = clean();
  inconsistent.metadata.vulnerabilities.high = 1;
  assert.throws(() => checkAudit(inconsistent), /counts disagree/);
});
