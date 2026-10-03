// One-shot: rasterize favicon.svg into PWA PNG icons via headless Edge canvas.
// Mirrors taskman's frontend/e2e/gen_icons.ts (proven on this machine).
// Run from frontend/: deno task gen-icons
//
// Background rules:
//   - "any" icons keep the logo's own background (favicon.svg paints #111111).
//   - maskable and apple-touch icons ALSO paint a solid background first:
//     Android circle-crops maskable icons and iOS composites apple-touch
//     icons over black — both mangle transparency, so give them #111111.

const PUBLIC = new URL(".", import.meta.url);

// Serve favicon.svg over http (canvas on a file:// or null origin taints
// same-origin images and toDataURL throws SecurityError).
const server = Deno.serve({ port: 0 }, async (req) => {
  const path = new URL(req.url).pathname;
  if (path === "/favicon.svg") {
    const bytes = await Deno.readFile(new URL("favicon.svg", PUBLIC));
    return new Response(bytes, { headers: { "content-type": "image/svg+xml" } });
  }
  return new Response("404", { status: 404 });
});
const port = (server.addr as { port: number }).port;
const origin = `http://127.0.0.1:${port}`;

// Launch Edge headless (flags only — never pass a URL on the command line)
const stamp = Date.now();
const localAppData = Deno.env.get("LOCALAPPDATA") ??
  `C:\\Users\\${Deno.env.get("USERNAME")}\\AppData\\Local`;
const profileDir = `${localAppData}\\lymph-icon-gen-${stamp}`;
const cdpPort = 9500 + Math.floor(Math.random() * 400);
const proc = new Deno.Command(
  "C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe",
  {
    args: [
      `--remote-debugging-port=${cdpPort}`,
      `--user-data-dir=${profileDir}`,
      "--headless=new",
      "--no-first-run",
    ],
    stdout: "null",
    stderr: "null",
  },
).spawn();

try {
  let wsUrl = "";
  for (let i = 0; i < 75; i++) {
    await new Promise((r) => setTimeout(r, 400));
    try {
      const res = await fetch(`http://127.0.0.1:${cdpPort}/json/version`);
      if (res.ok) {
        const info = await res.json();
        wsUrl = info.webSocketDebuggerUrl;
        break;
      }
    } catch { /* keep polling */ }
  }
  if (!wsUrl) throw new Error("Edge debug port never came up");

  // Minimal CDP over raw WebSocket
interface CdpMessage {
  id?: number;
  result?: unknown;
  error?: { message: string };
}
  const sock = new WebSocket(wsUrl);
  await new Promise<void>((res, rej) => {
    sock.onopen = () => res();
    sock.onerror = (e) => rej(new Error(`WS open failed: ${String(e)}`));
  });
  let nextId = 1;
  const pending = new Map<number, (msg: CdpMessage) => void>();
  sock.onmessage = (e) => {
    const m = JSON.parse(e.data as string) as CdpMessage;
    if (typeof m.id === "number" && Number.isInteger(m.id)) {
      const res = pending.get(m.id);
      if (typeof res === "function") {
        pending.delete(m.id);
        res(m);
      }
    }
  };
  let activeSessionId: string | undefined;
  function send(method: string, params?: Record<string, unknown>) {
    const id = nextId++;
    return new Promise<Record<string, unknown>>((resolve, reject) => {
      pending.set(id, (m) => {
        if (m.error) reject(new Error(`${method}: ${m.error.message}`));
        else resolve(m.result as Record<string, unknown>);
      });
      const msg: Record<string, unknown> = { id, method };
      if (params) msg.params = params;
      if (activeSessionId !== undefined) msg.sessionId = activeSessionId;
      sock.send(JSON.stringify(msg));
    });
  }

  const { targetId } = await send("Target.createTarget", {
    url: "about:blank",
  }) as { targetId: string };
  const { sessionId } = await send("Target.attachToTarget", {
    targetId,
    flatten: true,
  }) as { sessionId: string };
  activeSessionId = sessionId;
  await send("Runtime.enable");

  // Navigate to the served origin first — evaluating on about:blank (null
  // origin) makes same-origin http images "cross-origin" and taints the
  // canvas, so toDataURL() throws SecurityError.
  await send("Page.navigate", { url: origin });
  await new Promise((r) => setTimeout(r, 1000));

  const expr = `(async () => {
    const img = new Image();
    img.src = ${JSON.stringify(`${origin}/favicon.svg`)};
    await new Promise((res, rej) => { img.onload = res; img.onerror = rej; });
    const BG = "#111111"; // LymphBlack — manifest theme_color/background_color
    function draw(size, mode) {
      const c = document.createElement("canvas");
      c.width = size; c.height = size;
      const x = c.getContext("2d");
      if (mode !== "transparent") x.fillStyle = BG, x.fillRect(0, 0, size, size);
      const inset = mode === "maskable" ? size * 0.10 : 0;
      const box = size - 2 * inset;
      const s = Math.min(box / img.width, box / img.height);
      const w = img.width * s, h = img.height * s;
      x.drawImage(img, (size - w) / 2, (size - h) / 2, w, h);
      return c.toDataURL("image/png");
    }
    return {
      "icon-192.png": draw(192, "transparent"),
      "icon-512.png": draw(512, "transparent"),
      "icon-512-maskable.png": draw(512, "maskable"),
      "apple-touch-icon.png": draw(180, "apple"),
    };
  })()`;

  const r = await send("Runtime.evaluate", {
    expression: expr,
    returnByValue: true,
    awaitPromise: true,
  }) as Record<string, unknown>;
  const icons = (r.result as { value: Record<string, string> }).value;
  if (!icons || Object.keys(icons).length === 0) {
    throw new Error("Canvas eval returned no icons");
  }
  const outDir = new URL("icons/", PUBLIC);
  await Deno.mkdir(outDir, { recursive: true });
  for (const [name, dataurl] of Object.entries(icons)) {
    const b64 = dataurl.split(",", 2)[1];
    const bytes = Uint8Array.from(atob(b64), (c) => c.charCodeAt(0));
    await Deno.writeFile(new URL(`icons/${name}`, PUBLIC), bytes);
    console.log(`${name}: ${bytes.length} bytes`);
  }
  sock.close();
} finally {
  proc.kill();
  try {
    await server.shutdown();
  } catch { /* already down */ }
}
console.log("icons written to frontend/icons/");
