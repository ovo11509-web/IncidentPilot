import { createServer } from "vite";
import { existsSync, unlinkSync, writeFileSync } from "node:fs";
import { resolve } from "node:path";
const [appPort,vitePort]=process.argv.slice(2,4).map(Number);
const proof=process.argv[4];
if(!Number.isInteger(appPort)||!Number.isInteger(vitePort))throw new Error("isolated ports required");
const server=await createServer({server:{host:"127.0.0.1",port:vitePort,strictPort:true,proxy:{"/api":{target:`http://127.0.0.1:${appPort}`,changeOrigin:true,selfHandleResponse:true,
  configure(proxy) {proxy.on("proxyRes",(upstream,request,response)=>{
    const marker=proof && resolve(proof,"DROP_SELECT_RESPONSE");
    if(marker && existsSync(marker) && request.method==="POST" && /\/recommendations\/[^/]+\/actions$/.test(request.url || "")) {
      unlinkSync(marker);const chunks=[];
      upstream.on("data",chunk=>chunks.push(chunk));upstream.on("end",()=>{
        const payload=JSON.parse(Buffer.concat(chunks).toString());
        writeFileSync(resolve(proof,"lost-response.json"),JSON.stringify({httpStatus:upstream.statusCode,code:payload.code,actionId:payload.data?.actionId,scope:"real upstream response consumed, browser connection intentionally closed"},null,2));
        response.destroy();
      });
    } else {response.writeHead(upstream.statusCode,upstream.headers);upstream.pipe(response);}
  });}
}}}});
await server.listen();
server.printUrls();
