// sql-bridge.js — Thin JS wrapper around sql.js for Kotlin/WASM interop.
// Loaded from index.html before the app starts. Exposes window.SqlBridge.

window.SqlBridge = (function () {
  const databases = new Map(); // id -> SQL.Database instance
  let nextId = 1;
  let initPromise = null;

  function ensureLoaded() {
    if (!initPromise) {
      initPromise = initSqlJs({
        locateFile: f => 'https://cdnjs.cloudflare.com/ajax/libs/sql.js/1.10.3/dist/' + f,
      });
    }
    return initPromise;
  }

  function createDatabase() {
    const SQL = await ensureLoaded();
    const db = new SQL.Database();
    const id = nextId++;
    databases.set(id, db);

    // Create Lymph schema tables
    db.exec(`CREATE TABLE IF NOT EXISTS weight_records (id TEXT PRIMARY KEY, timestamp INTEGER NOT NULL, weight_kg REAL NOT NULL, note TEXT)`);
    db.exec(`CREATE TABLE IF NOT EXISTS period_records (id TEXT PRIMARY KEY, timestamp INTEGER NOT NULL, duration_days INTEGER NOT NULL, flow_intensity INTEGER NOT NULL DEFAULT 2, symptoms TEXT)`);
    db.exec(`CREATE TABLE IF NOT EXISTS bowel_records (id TEXT PRIMARY KEY, timestamp INTEGER NOT NULL, bristol_type INTEGER NOT NULL, effort_level INTEGER NOT NULL DEFAULT 3, note TEXT)`);

    return id;
  }

  function closeDatabase(id) {
    const db = databases.get(id);
    if (db) { db.close(); databases.delete(id); }
  }

  function exec(dbId, sql) {
    databases.get(dbId).exec(sql);
  }

  // Execute a parameterized statement. Args are passed as a JS array of primitives.
  function executeWithArgs(dbId, sql, argsArray) {
    const db = databases.get(dbId);
    const stmt = db.prepare(sql);
    if (argsArray && argsArray.length > 0) {
      stmt.bind(Array.from(argsArray));
    }
    while (stmt.step()) {}
    stmt.free();
  }

  // Query returns results as: [[colName1, colName2, ...], [row1values...], [row2values...], ...]
  function query(dbId, sql) {
    const db = databases.get(dbId);
    const stmt = db.prepare(sql);
    const meta = stmt.getColumnNames();
    const rows = [];
    while (stmt.step()) {
      // mode 1 = JS array of values in column order
      rows.push(stmt.get(1));
    }
    stmt.free();
    return [meta, ...rows];
  }

  function queryWithArgs(dbId, sql, argsArray) {
    const db = databases.get(dbId);
    const stmt = db.prepare(sql);
    if (argsArray && argsArray.length > 0) {
      stmt.bind(Array.from(argsArray));
    }
    const meta = stmt.getColumnNames();
    const rows = [];
    while (stmt.step()) {
      rows.push(stmt.get(1));
    }
    stmt.free();
    return [meta, ...rows];
  }

  return {
    createDatabase: createDatabase,
    closeDatabase: closeDatabase,
    exec: exec,
    executeWithArgs: executeWithArgs,
    query: query,
    queryWithArgs: queryWithArgs,
  };
})();
