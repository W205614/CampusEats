import fs from 'node:fs/promises';
import { pathToFileURL } from 'node:url';
export function checkAudit(audit) {
  const counts = audit?.metadata?.vulnerabilities;
  const findings = audit?.vulnerabilities;
  if (audit?.error || !counts || !findings || typeof findings !== 'object' || Array.isArray(findings))
    throw new Error('Audit did not return a valid report.');
  const severities = ['info', 'low', 'moderate', 'high', 'critical'];
  if ([...severities, 'total'].some(key => !Number.isSafeInteger(counts[key]) || counts[key] < 0))
    throw new Error('Audit severity counts are missing or invalid.');
  if (severities.reduce((sum, key) => sum + counts[key], 0) !== counts.total || Object.keys(findings).length !== counts.total)
    throw new Error('Audit counts disagree with findings.');
  if (counts.total !== 0) throw new Error('Vulnerable dependencies: ' + Object.keys(findings).join(', '));
  return 'Audit checked: zero known vulnerabilities; no exceptions.';
}
if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href)
  console.log(checkAudit(JSON.parse(await fs.readFile('.local/audit-client.json', 'utf8'))));
