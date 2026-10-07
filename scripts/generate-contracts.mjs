import fs from "node:fs/promises";
import path from "node:path";
import { fileURLToPath, pathToFileURL } from "node:url";
const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");
const contract = path.join(root, "frontend/contracts/openapi.json");
if (process.argv.includes("--fetch")) {
  const env = Object.fromEntries(
    (await fs.readFile(path.join(root, ".env"), "utf8"))
      .split(/\r?\n/)
      .filter((l) => l.includes("=") && !l.startsWith("#"))
      .map((l) => [l.slice(0, l.indexOf("=")), l.slice(l.indexOf("=") + 1)]),
  );
  const base =
    process.env.BASE_URL || "http://localhost:" + (env.WEB_PORT || 18083);
  const auth = await fetch(base + "/api/v1/admin/auth/login", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({
      username: process.env.ADMIN_USERNAME || "admin",
      password: process.env.ADMIN_PASSWORD || env.DEMO_ADMIN_PASSWORD,
    }),
  }).then((r) => r.json());
  if (auth.code !== "OK") throw new Error("Schema login failed: " + auth.code);
  const response = await fetch(base + "/v3/api-docs", {
    headers: { Authorization: "Bearer " + auth.data.token },
  });
  if (!response.ok) throw new Error("Schema fetch failed: " + response.status);
  const spec = await response.json();
  delete spec.servers;
  await fs.writeFile(contract, JSON.stringify(spec, null, 2) + "\n");
}
const { default: openapiTS, astToString } = await import(
  pathToFileURL(
    path.join(
      root,
      "frontend/admin/node_modules/openapi-typescript/dist/index.mjs",
    ),
  ).href
);
const ast = await openapiTS(new URL(pathToFileURL(contract).href));
await fs.writeFile(
  path.join(root, "frontend/contracts/openapi.ts"),
  astToString(ast),
);
console.log("Generated frontend/contracts/openapi.ts from the API schema.");
