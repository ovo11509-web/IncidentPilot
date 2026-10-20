import assert from "node:assert/strict";
import {test} from "node:test";
import {mkdtemp, mkdir, writeFile} from "node:fs/promises";
import path from "node:path";
import http from "node:http";
import {createLocalServer} from "./local-web.mjs";

test("production SPA routes and proxy preserve JSON failures; no file escape or foreign host", async () => {
  const scratch = path.resolve("bootstrap/target/local-demo-tests");
  await mkdir(scratch, {recursive:true});
  const directory = await mkdtemp(path.join(scratch, "proxy-"));
  await writeFile(path.join(directory, "index.html"), "<title>IncidentPilot</title>");
  await writeFile(path.join(directory, "asset.js"), "export default 1");
  const upstream = http.createServer((req, res) => {
    if (req.url === "/api/ragent/events") {
      res.writeHead(200, {"Content-Type": "text/event-stream"});
      res.end("data: complete\n\n");
    } else {res.writeHead(409, {"Content-Type": "application/json", "X-Request-Id": "review-request"}); res.end('{"code":"CONFLICT"}');}
  });
  await new Promise(resolve => upstream.listen(0, "127.0.0.1", resolve));
  const server = createLocalServer({dist: directory, backendPort: upstream.address().port, instanceId: "test-instance"});
  await new Promise(resolve => server.listen(0, "127.0.0.1", resolve));
  const base = `http://127.0.0.1:${server.address().port}`;
  try {
    assert.match(await (await fetch(`${base}/admin/incidents/42/review`)).text(), /IncidentPilot/);
    const failure = await fetch(`${base}/api/ragent/incident/v1/workbench`);
    assert.equal(failure.status, 409); assert.match(failure.headers.get("content-type"), /json/);
    assert.equal(failure.headers.get("x-request-id"), "review-request");
    assert.deepEqual(await failure.json(), {code: "CONFLICT"});
    const stream = await fetch(`${base}/api/ragent/events`);
    assert.match(stream.headers.get("content-type"), /event-stream/);
    assert.equal(await stream.text(), "data: complete\n\n");
    assert.equal((await fetch(`${base}/missing.js`)).status, 404);
    assert.equal((await fetch(`${base}/%2e%2e%2foutside`)).status, 400);
    const rejected = await new Promise(resolve => {const req=http.get(base, {headers:{host:"attacker.invalid"}}, res => {res.resume(); resolve(res.statusCode);}); req.on("error", e=>{throw e;});});
    assert.equal(rejected, 403);
    assert.equal((await (await fetch(`${base}/__local_demo/status`)).json()).instanceId, "test-instance");
  } finally {await new Promise(resolve => server.close(resolve)); await new Promise(resolve => upstream.close(resolve));}
});

test("SSE streams the first event before completion and disconnect cancels upstream", async () => {
  const scratch=path.resolve("bootstrap/target/local-demo-tests");
  await mkdir(scratch,{recursive:true});
  const directory=await mkdtemp(path.join(scratch,"stream-"));
  await writeFile(path.join(directory,"index.html"),"<title>IncidentPilot</title>");
  let closed;
  const cancellation=new Promise(resolve=>{closed=resolve;});
  const upstream=http.createServer((req,res)=>{
    res.writeHead(200,{"Content-Type":"text/event-stream"});
    res.flushHeaders(); res.write("data: first\n\n");
    const timer=setInterval(()=>res.write("data: next\n\n"),100);
    res.on("close",()=>{clearInterval(timer);closed();});
  });
  await new Promise(resolve=>upstream.listen(0,"127.0.0.1",resolve));
  const server=createLocalServer({dist:directory,backendPort:upstream.address().port,instanceId:"stream-test"});
  await new Promise(resolve=>server.listen(0,"127.0.0.1",resolve));
  const abort=new AbortController();
  try {
    const response=await fetch(`http://127.0.0.1:${server.address().port}/api/ragent/events`,{signal:abort.signal});
    const chunk=await response.body.getReader().read();
    assert.equal(chunk.done,false); assert.match(new TextDecoder().decode(chunk.value),/data: first/);
    abort.abort();
    await Promise.race([cancellation,new Promise((_,reject)=>setTimeout(()=>reject(new Error("upstream stayed open")),2000))]);
  } finally {abort.abort();await new Promise(resolve=>server.close(resolve));await new Promise(resolve=>upstream.close(resolve));}
});
