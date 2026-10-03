// Minimal zero-dependency static file server for the built app.
// Usage: deno run --allow-read --allow-net serve.ts [dir=dist] [port=8080]

const dir = Deno.args[0] ?? "dist";
const port = Number(Deno.args[1] ?? 8080);

const TYPES: Record<string, string> = {
  ".html": "text/html; charset=utf-8",
  ".js": "text/javascript; charset=utf-8",
  ".css": "text/css; charset=utf-8",
  ".json": "application/json; charset=utf-8",
  ".wasm": "application/wasm",
  ".svg": "image/svg+xml",
  ".png": "image/png",
  ".ico": "image/x-icon",
};

Deno.serve({ port }, async (req) => {
  const path = new URL(req.url).pathname;
  let file = `${dir}${path === "/" ? "/index.html" : path}`;
  try {
    const info = await Deno.stat(file);
    if (info.isDirectory) file = `${file}/index.html`;
  } catch {
    // fall through to 404
  }
  try {
    const bytes = await Deno.readFile(file);
    const ext = file.slice(file.lastIndexOf("."));
    return new Response(bytes, {
      headers: {
        "content-type": TYPES[ext] ?? "application/octet-stream",
        "cache-control": "no-store", // dev server; the PWA SW owns caching later
      },
    });
  } catch {
    return new Response("404", { status: 404 });
  }
});
