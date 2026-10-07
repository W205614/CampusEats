import fs from "node:fs/promises";
import path from "node:path";
import crypto from "node:crypto";
const dir = path.resolve(".local/loadtest");
await fs.mkdir(dir, { recursive: true });
const source = await fs.readFile(".env", "utf8");
const config = Object.fromEntries(
  source
    .split(/\r?\n/)
    .filter((l) => l.includes("=") && !l.startsWith("#"))
    .map((l) => [l.slice(0, l.indexOf("=")), l.slice(l.indexOf("=") + 1)]),
);
for (const key of [
  "MYSQL_ROOT_PASSWORD",
  "MYSQL_PASSWORD",
  "REDIS_PASSWORD",
  "JWT_ADMIN_SECRET",
  "DEMO_ADMIN_PASSWORD",
])
  config[key] = crypto.randomBytes(32).toString("hex");
config.WEB_PORT = "18084";
config.ALLOWED_ORIGINS = "http://localhost:18084,http://127.0.0.1:18084";
await fs.writeFile(
  path.join(dir, "load.env"),
  Object.entries(config)
    .map(([k, v]) => k + "=" + v)
    .join("\n") + "\n",
);
const now = Math.floor(Date.now() / 1000),
  rows = [],
  users = [],
  addresses = [],
  sessions = [];
const sign = (v) => Buffer.from(JSON.stringify(v)).toString("base64url");
for (let i = 0; i < 100; i++) {
  const id = 900001 + i,
    jti = crypto.randomUUID();
  users.push("(" + id + ",'load-" + id + "','压测用户" + i + "',NOW())");
  addresses.push("(" + id + "," + id + ",1,'101','压测用户','13800000000',0)");
  sessions.push(
    "('" + jti + "','USER'," + id + ",0,DATE_ADD(NOW(),INTERVAL 2 HOUR))",
  );
  const header = sign({ alg: "HS256", typ: "JWT" }),
    payload = sign({
      iss: "campuseats",
      sub: "USER:" + id,
      jti,
      iat: now,
      exp: now + 7200,
    }),
    message = header + "." + payload,
    signature = crypto
      .createHmac("sha256", config.JWT_ADMIN_SECRET)
      .update(message)
      .digest("base64url");
  rows.push({
    userId: String(id),
    addressId: String(id),
    token: message + "." + signature,
  });
}
let sql =
  "SET NAMES utf8mb4;\nUPDATE shop_settings SET open=true,hours='[\"00:00-24:00\"]';\nUPDATE dish SET default_quota=100000;\n";
sql +=
  "INSERT INTO user(id,openid,name,create_time) VALUES " +
  users.join(",") +
  ";\n";
sql +=
  "INSERT INTO address_book(id,user_id,building_id,room,consignee,phone,is_default) VALUES " +
  addresses.join(",") +
  ";\n";
sql +=
  "INSERT INTO auth_session(jti,subject_type,subject_id,auth_version,expires_at) VALUES " +
  sessions.join(",") +
  ";\n";
for (let offset = 0; offset < 10000; offset += 1000) {
  const orders = [],
    details = [];
  for (let n = offset; n < offset + 1000; n++) {
    const id = 1000001 + n,
      user = 900001 + (n % 100);
    orders.push(
      "(" +
        id +
        ",'LOAD-HISTORY-" +
        id +
        "',5," +
        user +
        "," +
        user +
        ",DATE_SUB(NOW(),INTERVAL " +
        ((n % 29) + 1) +
        " DAY),1,65.00,'一号宿舍楼 101','压测用户','13800000000','ORIGINAL')",
    );
    for (let j = 0; j < 5; j++)
      details.push("('番茄鸡蛋饭'," + id + ",2,'{}',1,12.00)");
  }
  sql +=
    "INSERT INTO orders(id,number,status,user_id,address_book_id,order_time,pay_status,amount,address,consignee,phone,snapshot_source) VALUES " +
    orders.join(",") +
    ";\n";
  sql +=
    "INSERT INTO order_detail(name,order_id,dish_id,dish_flavor,number,amount) VALUES " +
    details.join(",") +
    ";\n";
}
await fs.writeFile(path.join(dir, "fixture.sql"), sql);
await fs.writeFile(path.join(dir, "users.json"), JSON.stringify(rows));
console.log(
  "Generated an isolated 100-user, 10000-order, 50000-detail fixture.",
);
