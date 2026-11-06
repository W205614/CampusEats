import { createHash } from 'node:crypto';
import fs from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

interface SourceFile { path: string; sha256: string }
interface SourceManifest { baselineSha: string; runtimeRevision: string; files: SourceFile[] }
export interface CandidateSource {
  sourceRevision: string;
  sourceRevisionType: 'git-commit' | 'workspace-manifest-blob';
  baselineSha?: string;
  workspaceFingerprint?: string;
  runtimeRevision?: string;
}

const defaultRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../../..');
function requireSource(condition: unknown, message: string): asserts condition {
  if (!condition) throw new Error('Candidate source verification failed: ' + message);
}

export async function candidateSource(
  environment: NodeJS.ProcessEnv = process.env,
  workspaceRoot: string = defaultRoot,
): Promise<CandidateSource> {
  const revision = environment.CANDIDATE_SHA || environment.GITHUB_SHA || '';
  requireSource(/^[a-f0-9]{40}$/.test(revision), 'a real 40-character source identity is required');
  const manifestPath = environment.CANDIDATE_SOURCE_MANIFEST;
  if (!manifestPath) return { sourceRevision: revision, sourceRevisionType: 'git-commit' };

  const raw = await fs.readFile(path.resolve(workspaceRoot, manifestPath));
  const blob = createHash('sha1').update('blob ' + raw.byteLength + '\0').update(raw).digest('hex');
  requireSource(blob === revision, 'manifest Git blob identity differs from CANDIDATE_SHA');
  const manifest = JSON.parse(raw.toString('utf8')) as SourceManifest;
  requireSource(/^[a-f0-9]{40}$/.test(manifest.baselineSha), 'baselineSha must identify the base Git commit');
  requireSource(typeof manifest.runtimeRevision === 'string' && manifest.runtimeRevision.length > 0,
    'runtimeRevision must explicitly label the built working tree');
  requireSource(Array.isArray(manifest.files) && manifest.files.length > 0, 'a non-empty source file manifest is required');
  const root = await fs.realpath(workspaceRoot);
  const seen = new Set<string>();
  for (const file of manifest.files) {
    requireSource(typeof file.path === 'string' && file.path.length > 0 && !path.isAbsolute(file.path),
      'manifest entries must use repository-relative file paths');
    requireSource(typeof file.sha256 === 'string' && /^[a-f0-9]{64}$/.test(file.sha256), 'source SHA-256 is invalid');
    const resolved = path.resolve(root, file.path);
    const relative = path.relative(root, resolved);
    requireSource(relative !== '' && relative !== '..' && !relative.startsWith('..' + path.sep) && !path.isAbsolute(relative),
      'source path escapes the workspace');
    const real = await fs.realpath(resolved);
    const realRelative = path.relative(root, real);
    requireSource(realRelative !== '' && realRelative !== '..' && !realRelative.startsWith('..' + path.sep) && !path.isAbsolute(realRelative),
      'source symlink escapes the workspace');
    const identity = process.platform === 'win32' ? real.toLowerCase() : real;
    requireSource(!seen.has(identity), 'source file is listed more than once');
    seen.add(identity);
    const actual = createHash('sha256').update(await fs.readFile(real)).digest('hex');
    requireSource(actual === file.sha256, 'working tree file changed: ' + file.path);
  }
  return {
    sourceRevision: revision,
    sourceRevisionType: 'workspace-manifest-blob',
    baselineSha: manifest.baselineSha,
    workspaceFingerprint: createHash('sha256').update(raw).digest('hex'),
    runtimeRevision: manifest.runtimeRevision,
  };
}
