import { readFileSync } from 'node:fs';
import type { IncomingMessage, ServerResponse } from 'node:http';

export function demoImage() {
  return {
    name: 'campuseats-demo-image',
    apply: 'serve' as const,
    configureServer(server: {
      middlewares: {
        use(
          handler: (request: IncomingMessage, response: ServerResponse, next: () => void) => void,
        ): void;
      };
    }) {
      const image = readFileSync(new URL('../deploy/dish.svg', import.meta.url));
      server.middlewares.use((request, response, next) => {
        if (
          !['GET', 'HEAD'].includes(request.method || '') ||
          request.url?.split('?')[0] !== '/demo-images/dish.svg'
        ) {
          next();
          return;
        }
        response.setHeader('Content-Type', 'image/svg+xml');
        response.setHeader('Content-Length', image.length);
        response.end(request.method === 'HEAD' ? undefined : image);
      });
    },
  };
}
