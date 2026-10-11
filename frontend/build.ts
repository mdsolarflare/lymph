// Build: bundle the TSX app for the browser, stage static assets in dist/,
// then generate the cache-revision manifest + stamp sw.js (PWA update model).
// The wasm-pack "web" target's glue resolves `lymph_core_bg.wasm` relative to
// import.meta.url — i.e. relative to app.js — so the .wasm must sit in dist/
// next to the bundle.

const HERE = new URL(".", import.meta.url);
const DIST = new URL("dist/", HERE);

async function run(args: string[]) {
  const p = new Deno.Command(Deno.execPath(), {
    args,
    cwd: HERE,
    stdout: "inherit",
    stderr: "inherit",
  });
  const status = await p.spawn().status;
  if (!status.success) throw new Error(`deno ${args.join(" ")} exited ${status.code}`);
}

await Deno.mkdir(new URL("icons/", DIST), { recursive: true });

// 1. Bundle the app (esbuild under the hood; Preact + TSX verified working).
await run(["bundle", "--platform=browser", "src/main.tsx", "-o", "dist/app.js"]);

// 2. Stage static assets + PWA shell next to the bundle.
for (const [from, to] of [
  ["index.html", "dist/index.html"],
  ["src/styles.css", "dist/styles.css"],
  ["../core/pkg/lymph_core_bg.wasm", "dist/lymph_core_bg.wasm"],
  ["favicon.svg", "dist/favicon.svg"],
  ["manifest.webmanifest", "dist/manifest.webmanifest"],
  ["sw.js", "dist/sw.js"],
  ["icons/icon-192.png", "dist/icons/icon-192.png"],
  ["icons/icon-512.png", "dist/icons/icon-512.png"],
  ["icons/icon-512-maskable.png", "dist/icons/icon-512-maskable.png"],
  ["icons/apple-touch-icon.png", "dist/icons/apple-touch-icon.png"],
] as const) {
  await Deno.copyFile(new URL(from, HERE), new URL(to, HERE));
}

// 3. Generate cache-manifest.js + stamp the buildId into dist/sw.js.
//    Runs AFTER staging so it hashes the final shell bytes.
await run(["run", "--allow-read", "--allow-write", "gen-revision.ts"]);

console.log("build: dist/ staged + cache-manifest + sw stamped");
