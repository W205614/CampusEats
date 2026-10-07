import fs from "node:fs/promises";
const audit = JSON.parse(await fs.readFile(".local/audit-client.json", "utf8"));
if (!audit.metadata) throw new Error("Audit did not return a report.");
const review = JSON.parse(
  await fs.readFile("frontend/client/audit-exceptions.json", "utf8"),
);
if (Date.now() > Date.parse(review.expiresOn + "T23:59:59Z"))
  throw new Error("Build dependency exceptions require review.");
const findings = audit.vulnerabilities;
function reviewed(name, visiting = new Set()) {
  const v = findings[name];
  if (!v) return false;
  if (visiting.has(name)) return true;
  if (v.severity === "critical") return false;
  if (v.severity === "high" && !review.packages.includes(name)) return false;
  visiting.add(name);
  return v.via.every((x) =>
    typeof x === "string"
      ? reviewed(x, visiting)
      : ["low", "moderate"].includes(x.severity) ||
        review.advisories.includes(x.url),
  );
}
const blocked = Object.values(findings).filter(
  (v) =>
    v.severity === "critical" || (v.severity === "high" && !reviewed(v.name)),
);
if (blocked.length)
  throw new Error(
    "Unreviewed high/critical dependencies: " +
      blocked.map((v) => v.name).join(", "),
  );
console.log(
  "Audit checked: " +
    audit.metadata.vulnerabilities.high +
    " high build dependency findings reviewed; expires " +
    review.expiresOn,
);
