(function (global) {
  const Native = global.MobileForge && typeof global.MobileForge.version === "function"
    ? global.MobileForge
    : null;

  const pending = {};
  global.MF_onAi = function (id, payload) {
    const job = pending[id];
    if (job) {
      delete pending[id];
      job(payload);
    }
  };

  function parse(value) {
    if (value == null) return { ok: false, error: "Empty native response" };
    if (typeof value === "object") return value;
    try { return JSON.parse(value); } catch (e) { return { ok: false, error: String(value) }; }
  }

  function uid() {
    return "r" + Math.random().toString(36).slice(2) + Date.now().toString(36);
  }

  const WebFS = {
    data() {
      const raw = localStorage.getItem("mf.fs.v1");
      return raw ? JSON.parse(raw) : { projects: {}, providers: {}, secrets: {} };
    },
    save(db) { localStorage.setItem("mf.fs.v1", JSON.stringify(db)); },
    sanitize(name) { return String(name || "").trim().replace(/[^A-Za-z0-9_-]/g, "_").slice(0, 48); },
    ensure(name) {
      const db = this.data();
      const key = this.sanitize(name);
      if (!db.projects[key]) throw new Error("Project not found.");
      return { db, key, project: db.projects[key] };
    },
  };

  const demoFiles = {
    "project.json": '{\n  "name": "SkyRunner",\n  "engine": "MobileForge",\n  "version": "1.0.0",\n  "type": "3d",\n  "mainScene": "Main"\n}\n',
    "README.md": "# SkyRunner\n\nСоберите монеты, обойдите шипы и дойдите до голубых ворот.\nУправление: WASD / стик, пробел — прыжок.\n",
    "Scripts/Player.js": "function onStart(api){api.object.speed=8;api.log('SkyRunner online');}\nfunction onUpdate(api,dt){const s=api.object.speed||8;api.move(api.input.x*s*dt,0,-api.input.y*s*dt);if(api.input.jump)api.jump(8);if(api.object.z<-14){api.addScore(10);api.setPosition(0,1,10);}}\nfunction onCollisionEnter(api,other){if(other.type==='Coin'){api.addScore(5);api.destroy(other.name);}if(other.type==='Enemy'){api.addScore(-2);api.setPosition(0,1,10);}}\n",
    "Scripts/Player.cs": "public class Player {\n  public float speed = 8f;\n  void Update() {\n    Move(input.horizontal * speed, -input.vertical * speed);\n    if (input.jump) Jump(8f);\n  }\n}\n",
    "Scripts/Coin.js": "function onStart(api){api.object.solid=false;}\nfunction onUpdate(api,dt){api.object.ry=(api.object.ry||0)+dt*120;}\n",
    "Scripts/Hazard.js": "function onUpdate(api,dt){api.object.x=Math.sin(api.time.elapsed*(api.object.speed||2))*5;}\n",
    "Assets/readme.txt": "Drop text assets here. Meshes are generated primitives.\n",
    "Scenes/Main.scene.json": JSON.stringify({
      format: "mobileforge.scene.v1",
      name: "Main",
      dimension: "3D",
      objects: [
        { name: "MainCamera", type: "Camera", x: 0, y: 6, z: 12, rx: -22, color: "#9aa4b2", solid: false },
        { name: "Light", type: "Light", x: 4, y: 10, z: 2, color: "#fff4cc", solid: false },
        { name: "Arena", type: "Ground", x: 0, y: 0, z: 0, sx: 28, sy: 1, sz: 40, color: "#2a3144" },
        { name: "Player", type: "Player", x: 0, y: 1, z: 10, color: "#b69cff", script: "Scripts/Player.js", speed: 8, solid: true },
        { name: "Gate", type: "Mesh", x: 0, y: 1.5, z: -14, sx: 8, sy: 3, sz: 1, color: "#75e6da" },
        { name: "CoinA", type: "Coin", x: -3, y: 1.2, z: 4, color: "#f4c95d", script: "Scripts/Coin.js", solid: false },
        { name: "CoinB", type: "Coin", x: 3, y: 1.2, z: 0, color: "#f4c95d", script: "Scripts/Coin.js", solid: false },
        { name: "CoinC", type: "Coin", x: 0, y: 1.2, z: -6, color: "#f4c95d", script: "Scripts/Coin.js", solid: false },
        { name: "SpikeL", type: "Enemy", x: -5, y: 1, z: 2, color: "#ffb2c8", script: "Scripts/Hazard.js", speed: 2 },
        { name: "SpikeR", type: "Enemy", x: 5, y: 1, z: -4, color: "#ffb2c8", script: "Scripts/Hazard.js", speed: 2 }
      ]
    }, null, 2)
  };

  function starterFiles(name, type) {
    const dim = String(type).toLowerCase() === "2d" ? "2D" : "3D";
    const is3d = dim === "3D";
    const objects = is3d ? [
      { name: "MainCamera", type: "Camera", x: 0, y: 5, z: 10, rx: -18, color: "#9aa4b2", solid: false },
      { name: "Light", type: "Light", x: 3, y: 8, z: 2, color: "#fff4cc", solid: false },
      { name: "Ground", type: "Ground", x: 0, y: 0, z: 0, sx: 16, sy: 1, sz: 16, color: "#2a3144" },
      { name: "Player", type: "Player", x: 0, y: 1, z: 4, color: "#b69cff", script: "Scripts/Player.js", speed: 6, solid: true },
      { name: "Cube", type: "Mesh", x: 3, y: 1, z: -2, color: "#75e6da", solid: true }
    ] : [
      { name: "MainCamera", type: "Camera", x: 0, y: 0, z: 10, color: "#9aa4b2", solid: false },
      { name: "Ground", type: "Ground", x: 0, y: -4, z: 0, sx: 24, sy: 1, sz: 1, color: "#2a3144" },
      { name: "Player", type: "Player", x: -4, y: -2.5, z: 0, color: "#b69cff", script: "Scripts/Player.js", speed: 7, solid: true },
      { name: "Platform", type: "Sprite", x: 3, y: -1, z: 0, sx: 3, sy: 0.6, color: "#453b61" }
    ];
    return {
      "project.json": JSON.stringify({ name, engine: "MobileForge", version: "1.0.0", type: dim.toLowerCase(), mainScene: "Main" }, null, 2) + "\n",
      "README.md": `# ${name}\n\nMobileForge project.\n`,
      "Scripts/Player.js": "function onStart(api){api.log('Player ready');}\nfunction onUpdate(api,dt){const s=api.object.speed||6;api.move(api.input.x*s*dt,0,api.input.y*s*dt);if(api.input.jump)api.jump(7);}\nfunction onCollisionEnter(api,other){if(other.type==='Coin'){api.addScore(1);api.destroy(other.name);}}\n",
      "Scripts/Player.cs": "public class Player {\n  public float speed = 6f;\n  void Update() {\n    Move(input.horizontal * speed, input.vertical * speed);\n    if (input.jump) Jump(7f);\n  }\n}\n",
      "Assets/readme.txt": "Text assets live here.\n",
      "Scenes/Main.scene.json": JSON.stringify({ format: "mobileforge.scene.v1", name: "Main", dimension: dim, objects }, null, 2)
    };
  }

  const web = {
    version() { return { ok: true, name: "MobileForge", version: "1.0.0", native: false }; },
    projects() {
      const db = WebFS.data();
      return { ok: true, projects: Object.keys(db.projects).sort().map((name) => ({ name, path: "local/" + name, meta: JSON.parse(db.projects[name].files["project.json"] || "{\"name\":\"" + name + "\"}") })) };
    },
    createProject(rawName) { return this.createProjectTyped(rawName, "3d"); },
    createProjectTyped(rawName, type) {
      const name = WebFS.sanitize(rawName);
      if (!name) return { ok: false, error: "Enter a project name." };
      const db = WebFS.data();
      if (db.projects[name]) return { ok: false, error: "A project with this name already exists." };
      db.projects[name] = { files: starterFiles(name, type) };
      WebFS.save(db);
      return { ok: true, name };
    },
    deleteProject(name) {
      const db = WebFS.data();
      delete db.projects[WebFS.sanitize(name)];
      WebFS.save(db);
      return { ok: true };
    },
    renameProject(oldName, newName) {
      const db = WebFS.data();
      const from = WebFS.sanitize(oldName);
      const to = WebFS.sanitize(newName);
      if (!db.projects[from]) return { ok: false, error: "Project not found." };
      if (!to) return { ok: false, error: "Enter a project name." };
      if (db.projects[to]) return { ok: false, error: "A project with this name already exists." };
      db.projects[to] = db.projects[from];
      delete db.projects[from];
      if (db.projects[to].files["project.json"]) {
        try {
          const meta = JSON.parse(db.projects[to].files["project.json"]);
          meta.name = to;
          db.projects[to].files["project.json"] = JSON.stringify(meta, null, 2) + "\n";
        } catch (e) { /* keep */ }
      }
      WebFS.save(db);
      return { ok: true, name: to };
    },
    seedDemo() {
      const db = WebFS.data();
      if (!db.projects.SkyRunner) db.projects.SkyRunner = { files: Object.assign({}, demoFiles) };
      WebFS.save(db);
      return { ok: true, name: "SkyRunner" };
    },
    projectMeta(name) {
      try {
        const { project } = WebFS.ensure(name);
        return { ok: true, meta: JSON.parse(project.files["project.json"] || "{}") };
      } catch (e) { return { ok: false, error: e.message }; }
    },
    saveProjectMeta(name, json) {
      try {
        const { db, project } = WebFS.ensure(name);
        const meta = typeof json === "string" ? JSON.parse(json) : json;
        meta.name = WebFS.sanitize(name);
        project.files["project.json"] = JSON.stringify(meta, null, 2) + "\n";
        WebFS.save(db);
        return { ok: true };
      } catch (e) { return { ok: false, error: e.message }; }
    },
    listFiles(name) {
      try {
        const { project } = WebFS.ensure(name);
        const files = Object.keys(project.files).sort().map((path) => ({ path, name: path.split("/").pop(), size: project.files[path].length }));
        return { ok: true, files };
      } catch (e) { return { ok: false, error: e.message }; }
    },
    readFile(name, path) {
      try {
        const { project } = WebFS.ensure(name);
        if (project.files[path] == null) return { ok: false, error: "File not found." };
        return { ok: true, path, content: project.files[path] };
      } catch (e) { return { ok: false, error: e.message }; }
    },
    writeFile(name, path, content) {
      try {
        const { db, project } = WebFS.ensure(name);
        if (!path || path.startsWith("/") || path.includes("..")) return { ok: false, error: "Invalid path." };
        project.files[path] = String(content);
        WebFS.save(db);
        return { ok: true, path };
      } catch (e) { return { ok: false, error: e.message }; }
    },
    createFile(name, path, content) {
      try {
        const { project } = WebFS.ensure(name);
        if (project.files[path] != null) return { ok: false, error: "File already exists." };
        return this.writeFile(name, path, content || "");
      } catch (e) { return { ok: false, error: e.message }; }
    },
    deleteFile(name, path) {
      try {
        const { db, project } = WebFS.ensure(name);
        delete project.files[path];
        WebFS.save(db);
        return { ok: true };
      } catch (e) { return { ok: false, error: e.message }; }
    },
    renameFile(name, from, to) {
      try {
        const { db, project } = WebFS.ensure(name);
        if (project.files[from] == null) return { ok: false, error: "File not found." };
        if (project.files[to] != null) return { ok: false, error: "Target already exists." };
        project.files[to] = project.files[from];
        delete project.files[from];
        WebFS.save(db);
        return { ok: true, path: to };
      } catch (e) { return { ok: false, error: e.message }; }
    },
    scenes(name) {
      try {
        const { project } = WebFS.ensure(name);
        const scenes = Object.keys(project.files)
          .filter((p) => p.startsWith("Scenes/") && p.endsWith(".scene.json"))
          .map((p) => JSON.parse(project.files[p]));
        return { ok: true, scenes };
      } catch (e) { return { ok: false, error: e.message }; }
    },
    createScene(name, sceneName, dimension) {
      const safe = WebFS.sanitize(sceneName);
      const dim = String(dimension).toUpperCase() === "2D" ? "2D" : "3D";
      const scene = {
        format: "mobileforge.scene.v1",
        name: safe,
        dimension: dim,
        objects: [{ name: "MainCamera", type: "Camera", x: 0, y: dim === "3D" ? 5 : 0, z: 10, rx: dim === "3D" ? -18 : 0, color: "#9aa4b2", solid: false }]
      };
      const res = this.writeFile(name, "Scenes/" + safe + ".scene.json", JSON.stringify(scene, null, 2));
      if (!res.ok) return res;
      return { ok: true, scene };
    },
    saveScene(name, sceneJson) {
      const scene = typeof sceneJson === "string" ? JSON.parse(sceneJson) : sceneJson;
      const res = this.writeFile(name, "Scenes/" + WebFS.sanitize(scene.name) + ".scene.json", JSON.stringify(scene, null, 2));
      if (!res.ok) return res;
      return { ok: true, scene };
    },
    deleteScene(name, sceneName) {
      return this.deleteFile(name, "Scenes/" + WebFS.sanitize(sceneName) + ".scene.json");
    },
    exportProject(name) {
      try {
        const { project } = WebFS.ensure(name);
        return { ok: true, bundle: { format: "mobileforge.project.v1", name: WebFS.sanitize(name), files: project.files } };
      } catch (e) { return { ok: false, error: e.message }; }
    },
    importProject(bundleJson) {
      const bundle = typeof bundleJson === "string" ? JSON.parse(bundleJson) : bundleJson;
      let name = WebFS.sanitize(bundle.name || "Imported");
      const db = WebFS.data();
      if (db.projects[name]) name = name + "_" + Date.now().toString(36).slice(-4);
      db.projects[name] = { files: bundle.files || starterFiles(name, "3d") };
      WebFS.save(db);
      return { ok: true, name };
    },
    saveProvider(id, endpoint, apiKey, model) {
      const db = WebFS.data();
      db.providers[id] = { endpoint, model, hasKey: !!apiKey };
      if (apiKey) db.secrets[id] = apiKey;
      WebFS.save(db);
      return { ok: true };
    },
    providerConfig(id) {
      const db = WebFS.data();
      const p = db.providers[id] || {};
      return { ok: true, id, endpoint: p.endpoint || "", model: p.model || "", hasKey: !!(p.hasKey || db.secrets[id]) };
    },
    generate(providerId, model, prompt, endpoint) {
      return { ok: false, error: "В браузере нет нативного AI-моста. Соберите Android APK, чтобы вызывать Zen / OpenRouter / MCP." };
    },
    generateAsync() { return { ok: false, error: "Native AI is available only in the Android app." }; },
    checkMcp() { return { ok: false, error: "MCP доступен только на устройстве (127.0.0.1:8765)." }; },
    testProvider() { return { ok: false, error: "Проверка провайдера доступна в Android-сборке." }; }
  };

  function invoke(method, args) {
    if (Native && typeof Native[method] === "function") {
      try {
        return parse(Native[method].apply(Native, args));
      } catch (e) {
        return { ok: false, error: e.message || String(e) };
      }
    }
    if (typeof web[method] === "function") return web[method].apply(web, args);
    return { ok: false, error: "Unsupported method " + method };
  }

  const api = {
    native: !!Native,
    call(method, ...args) { return invoke(method, args); },
    generateAsync(providerId, model, prompt, endpoint) {
      if (Native && typeof Native.generateAsync === "function") {
        return new Promise((resolve) => {
          const id = uid();
          pending[id] = resolve;
          Native.generateAsync(id, providerId, model, prompt, endpoint || "");
          setTimeout(() => {
            if (pending[id]) {
              delete pending[id];
              resolve({ ok: false, error: "AI timeout (90s)" });
            }
          }, 95000);
        });
      }
      return Promise.resolve(invoke("generate", [providerId, model, prompt, endpoint || ""]));
    }
  };

  global.MFBridge = api;
})(window);
