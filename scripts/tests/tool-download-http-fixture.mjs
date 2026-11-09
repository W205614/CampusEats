import { createServer } from 'node:http';
import { writeFileSync, appendFileSync } from 'node:fs';

const [ready, events] = process.argv.slice(2);
const counts = new Map();
const server = createServer((request, response) => {
  const count = (counts.get(request.url) || 0) + 1;
  counts.set(request.url, count);
  appendFileSync(events, `${request.url}\n`);
  const code = request.url === '/missing' ? 404
    : request.url === '/unavailable' ? 503
    : request.url === '/transient' && count <= 2 ? [502, 504][count - 1]
    : 200;
  response.writeHead(code, { 'Content-Type': 'application/octet-stream' });
  response.end(request.url === '/corrupt' ? 'corrupt tool fixture' : 'verified tool fixture');
});
server.listen(0, '127.0.0.1', () => writeFileSync(ready, String(server.address().port)));
