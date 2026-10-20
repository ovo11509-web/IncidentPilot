import http from "node:http";
import fs from "node:fs";
import {readFile, stat, realpath} from "node:fs/promises";
import path from "node:path";
import {pathToFileURL} from "node:url";

const types = {".html":"text/html; charset=utf-8", ".js":"text/javascript; charset=utf-8", ".css":"text/css; charset=utf-8", ".svg":"image/svg+xml", ".png":"image/png", ".ico":"image/x-icon", ".json":"application/json", ".woff2":"font/woff2"};
export function createLocalServer({dist, backendPort, instanceId}) {
  const root = fs.realpathSync(dist);
  return http.createServer(async (req, res) => {
    if (!req.url.startsWith("/") || req.url.startsWith("//")) {res.writeHead(400).end(); return;}
    const host = req.headers.host;
    if (host !== `127.0.0.1:${res.socket.localPort}` && host !== `localhost:${res.socket.localPort}`) {res.writeHead(403).end(); return;}
    let url;
    try {url = new URL(req.url, `http://${host}`);} catch {res.writeHead(400).end(); return;}
    if (url.pathname === "/__local_demo/status" && req.method === "GET") {
      res.writeHead(200, {"Content-Type":"application/json", "Cache-Control":"no-store"}).end(JSON.stringify({instanceId, ready:true})); return;
    }
    if (url.pathname.startsWith("/api/ragent/")) {
      const headers = {...req.headers, host:`127.0.0.1:${backendPort}`};
      delete headers.connection; delete headers["proxy-authorization"];
      const proxy = http.request({host:"127.0.0.1", port:backendPort, path:req.url, method:req.method, headers}, response => {
        res.writeHead(response.statusCode, response.headers); response.pipe(res);
      });
      proxy.setTimeout(120000, () => proxy.destroy(new Error("LOCAL_PROXY_TIMEOUT")));
      proxy.on("error", () => {if (!res.headersSent) res.writeHead(502, {"Content-Type":"application/json"}).end('{"code":"LOCAL_BACKEND_UNAVAILABLE"}'); else res.destroy();});
      req.on("aborted", () => proxy.destroy());
      res.on("close", () => {if (!res.writableEnded) proxy.destroy();});
      req.pipe(proxy); return;
    }
    if (req.method !== "GET" && req.method !== "HEAD") {res.writeHead(405).end(); return;}
    let decoded;
    try {decoded = decodeURIComponent(url.pathname);} catch {res.writeHead(400).end(); return;}
    if (decoded.includes("\\") || decoded.split("/").includes("..") || decoded.includes("\0")) {res.writeHead(400).end(); return;}
    let file = path.resolve(root, "." + decoded);
    if (file !== root && !file.startsWith(root + path.sep)) {res.writeHead(400).end(); return;}
    try {
      if (!(await stat(file)).isFile()) throw new Error("not-file");
    } catch {
      if (path.extname(decoded)) {res.writeHead(404).end(); return;}
      file = path.join(root, "index.html");
    }
    try {
      const resolved = await realpath(file);
      if (!resolved.startsWith(root + path.sep)) {res.writeHead(403).end(); return;}
      res.writeHead(200, {"Content-Type":types[path.extname(file)] || "application/octet-stream", "Cache-Control":path.basename(file)==="index.html" ? "no-store" : "public, max-age=3600"});
      if (req.method === "HEAD") res.end(); else fs.createReadStream(file).on("error", () => res.destroy()).pipe(res);
    } catch {res.writeHead(404).end();}
  });
}
if (process.argv[1] && import.meta.url === pathToFileURL(path.resolve(process.argv[1])).href) {
  const config = JSON.parse(await readFile(process.argv[2], "utf8"));
  createLocalServer(config).listen(config.webPort, "127.0.0.1", () => process.stdout.write("LOCAL_WEB_READY\n"));
}
