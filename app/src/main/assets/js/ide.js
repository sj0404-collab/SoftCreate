(function () {
  const pages = [
    ["▣", "Проекты", "projects"],
    ["⌘", "Studio", "studio"],
    ["◇", "Сцены", "scenes"],
    ["▶", "Play", "play"],
    ["✦", "AI", "ai"],
    ["⚙", "Настройки", "settings"]
  ];

  const models = {
    zen: ["deepseek-v4-flash", "deepseek-v4-flash-free", "mimo-v2.5-free", "nemotron-3-ultra-free", "north-mini-code-free", "laguna-s-2.1-free"],
    openrouter: ["openrouter/free", "google/gemma-3-27b-it:free", "meta-llama/llama-3.3-70b-instruct:free", "qwen/qwen3-30b-a3b:free"],
    mcp: ["Termux local agent model"],
    custom: []
  };

  const state = {
    page: "projects",
    project: null,
    files: [],
    openPath: null,
    content: "",
    dirty: false,
    scenes: [],
    scene: null,
    selected: null,
    provider: "zen",
    proposal: null,
    native: false,
    orbit: { yaw: 0, pitch: 0 },
    runtime: null
  };

  const $ = (id) => document.getElementById(id);

  function toast(text) {
    const el = $("toast");
    el.textContent = text;
    el.style.display = "block";
    clearTimeout(toast._t);
    toast._t = setTimeout(() => { el.style.display = "none"; }, 2400);
  }

  function show(id) {
    if (state.dirty && state.page === "studio" && id !== "studio") {
      if (!confirm("Есть несохранённый файл. Уйти без сохранения?")) return;
    }
    if (id !== "play" && state.runtime) {
      state.runtime.stop();
    }
    state.page = id;
    document.querySelectorAll(".page").forEach((p) => p.classList.toggle("active", p.id === "page-" + id));
    document.querySelectorAll(".rail button, .mobileNav button").forEach((b, n) => b.classList.toggle("active", pages[n][2] === id));
    const sub = state.project ? (id.toUpperCase() + " • " + state.project) : id.toUpperCase();
    $("subtitle").textContent = sub;
    $("badge").textContent = state.dirty ? "● не сохранено" : (state.native ? "native" : "web");
    if (id === "projects") renderProjects();
    if (id === "studio") { refreshFiles(); drawViewport(); }
    if (id === "scenes") renderScenes();
    if (id === "play") startPlay();
    if (id === "settings") loadSettings();
    if (id === "ai") refreshModels();
  }

  function nav(container, rail) {
    pages.forEach(([icon, label, id]) => {
      const b = document.createElement("button");
      b.innerHTML = "<span>" + icon + "</span>" + (rail ? "" : label);
      b.title = label;
      b.onclick = () => show(id);
      container.appendChild(b);
    });
  }

  function requireProject() {
    if (!state.project) {
      toast("Сначала откройте проект");
      show("projects");
      return false;
    }
    return true;
  }

  async function renderProjects() {
    const res = MFBridge.call("projects");
    const list = $("projectList");
    list.innerHTML = "";
    const items = (res.projects || []);
    if (!items.length) {
      list.innerHTML = '<div class="card empty">Проектов пока нет. Создайте новый или откройте демо SkyRunner.</div>';
      return;
    }
    items.forEach((p) => {
      const card = document.createElement("div");
      card.className = "card project";
      const type = (p.meta && (p.meta.type || p.meta.engine)) || "project";
      card.innerHTML = '<div class="icon">◇</div><div><h3></h3><small></small><p></p><div class="row"></div></div>';
      card.querySelector("h3").textContent = p.name;
      card.querySelector("small").textContent = (p.name === state.project ? "ACTIVE • " : "") + String(type).toUpperCase();
      card.querySelector("small").style.color = "var(--cyan)";
      card.querySelector("p").textContent = "Scripts · Scenes · Assets";
      const row = card.querySelector(".row");
      const open = document.createElement("button");
      open.className = "primary";
      open.textContent = "Открыть";
      open.onclick = () => openProject(p.name);
      const exp = document.createElement("button");
      exp.textContent = "Экспорт";
      exp.onclick = () => exportProject(p.name);
      const del = document.createElement("button");
      del.className = "danger";
      del.textContent = "Удалить";
      del.onclick = () => {
        if (!confirm("Удалить проект " + p.name + "?")) return;
        const r = MFBridge.call("deleteProject", p.name);
        if (!r.ok) return toast(r.error);
        if (state.project === p.name) {
          state.project = null;
          state.scene = null;
          state.openPath = null;
        }
        renderProjects();
      };
      row.append(open, exp, del);
      list.appendChild(card);
    });
  }

  function openProject(name) {
    state.project = name;
    state.dirty = false;
    state.openPath = null;
    state.content = "";
    const scenes = MFBridge.call("scenes", name);
    state.scenes = scenes.scenes || [];
    state.scene = state.scenes[0] || null;
    state.selected = state.scene && state.scene.objects[0] ? state.scene.objects[0].name : null;
    $("activeProject").textContent = name;
    toast("Проект: " + name);
    show("studio");
  }

  function createProject() {
    const name = prompt("Имя проекта");
    if (!name) return;
    const type = confirm("OK — 3D, Отмена — 2D") ? "3d" : "2d";
    const res = MFBridge.call("createProjectTyped", name, type);
    if (!res.ok) return toast(res.error || "Не удалось создать");
    openProject(res.name);
  }

  function seedDemo() {
    const res = MFBridge.call("seedDemo");
    if (!res.ok) return toast(res.error);
    openProject(res.name);
  }

  function importProject() {
    $("importText").value = "";
    $("modal").hidden = false;
  }

  function confirmImport() {
    const raw = $("importText").value.trim();
    if (!raw) return toast("Вставьте JSON-бандл проекта");
    let bundle;
    try { bundle = JSON.parse(raw); } catch (e) { return toast("Некорректный JSON"); }
    const res = MFBridge.call("importProject", JSON.stringify(bundle));
    $("modal").hidden = true;
    if (!res.ok) return toast(res.error);
    openProject(res.name);
  }

  function exportProject(name) {
    const res = MFBridge.call("exportProject", name || state.project);
    if (!res.ok) return toast(res.error);
    const blob = new Blob([JSON.stringify(res.bundle, null, 2)], { type: "application/json" });
    const a = document.createElement("a");
    a.href = URL.createObjectURL(blob);
    a.download = (name || state.project) + ".mobileforge.json";
    a.click();
    toast("Экспорт готов");
  }

  function refreshFiles() {
    if (!state.project) {
      $("fileTree").innerHTML = '<div class="empty">Откройте проект</div>';
      return;
    }
    const res = MFBridge.call("listFiles", state.project);
    state.files = res.files || [];
    const tree = $("fileTree");
    tree.innerHTML = "";
    const groups = {};
    state.files.forEach((f) => {
      const dir = f.path.includes("/") ? f.path.split("/").slice(0, -1).join("/") : "/";
      (groups[dir] = groups[dir] || []).push(f);
    });
    Object.keys(groups).sort().forEach((dir) => {
      const title = document.createElement("div");
      title.innerHTML = "<b>" + (dir === "/" ? state.project : dir) + "</b>";
      tree.appendChild(title);
      groups[dir].forEach((f) => {
        const b = document.createElement("button");
        b.textContent = "◇ " + f.name;
        b.className = state.openPath === f.path ? "active" : "";
        b.onclick = () => openFile(f.path);
        tree.appendChild(b);
      });
    });
    $("editorTitle").textContent = (state.openPath || "Нет файла").toUpperCase();
    renderSceneListMini();
    drawViewport();
    fillInspector();
  }

  function openFile(path) {
    if (state.dirty && !confirm("Бросить несохранённые изменения?")) return;
    const res = MFBridge.call("readFile", state.project, path);
    if (!res.ok) return toast(res.error);
    state.openPath = path;
    state.content = res.content;
    state.dirty = false;
    $("editor").value = res.content;
    $("editorTitle").textContent = path.toUpperCase();
    if (path.endsWith(".scene.json")) {
      try {
        state.scene = JSON.parse(res.content);
        state.selected = state.scene.objects[0] && state.scene.objects[0].name;
      } catch (e) { /* ignore */ }
    }
    refreshFiles();
  }

  function saveFile() {
    if (!state.project || !state.openPath) return toast("Нет открытого файла");
    const content = $("editor").value;
    const res = MFBridge.call("writeFile", state.project, state.openPath, content);
    if (!res.ok) return toast(res.error);
    state.content = content;
    state.dirty = false;
    if (state.openPath.endsWith(".scene.json")) {
      try { state.scene = JSON.parse(content); } catch (e) { /* keep */ }
    }
    toast("Сохранено: " + state.openPath);
    $("badge").textContent = state.native ? "native" : "web";
  }

  function newFile() {
    if (!requireProject()) return;
    const path = prompt("Путь файла", "Scripts/New.js");
    if (!path) return;
    const res = MFBridge.call("createFile", state.project, path, "// " + path + "\n");
    if (!res.ok) return toast(res.error);
    openFile(path);
  }

  function deleteOpenFile() {
    if (!state.openPath) return;
    if (!confirm("Удалить " + state.openPath + "?")) return;
    const res = MFBridge.call("deleteFile", state.project, state.openPath);
    if (!res.ok) return toast(res.error);
    state.openPath = null;
    state.content = "";
    $("editor").value = "";
    refreshFiles();
  }

  function renderScenes() {
    if (!requireProject()) return;
    const res = MFBridge.call("scenes", state.project);
    state.scenes = res.scenes || [];
    const box = $("sceneCards");
    box.innerHTML = "";
    state.scenes.forEach((sc) => {
      const card = document.createElement("div");
      card.className = "card";
      card.innerHTML = "<h3></h3><p></p><div class='row'></div>";
      card.querySelector("h3").textContent = "◇ " + sc.name + ".scene.json";
      card.querySelector("p").textContent = sc.dimension + " • " + (sc.objects || []).map((o) => o.name).join(", ");
      const open = document.createElement("button");
      open.className = "primary";
      open.textContent = "Открыть в Studio";
      open.onclick = () => {
        state.scene = sc;
        state.selected = sc.objects[0] && sc.objects[0].name;
        openFile("Scenes/" + sc.name + ".scene.json");
        show("studio");
      };
      const play = document.createElement("button");
      play.textContent = "▶ Play";
      play.onclick = () => { state.scene = sc; show("play"); };
      const del = document.createElement("button");
      del.className = "danger";
      del.textContent = "Удалить";
      del.onclick = () => {
        if (!confirm("Удалить сцену " + sc.name + "?")) return;
        const r = MFBridge.call("deleteScene", state.project, sc.name);
        if (!r.ok) return toast(r.error);
        if (state.scene && state.scene.name === sc.name) state.scene = null;
        renderScenes();
      };
      card.querySelector(".row").append(open, play, del);
      box.appendChild(card);
    });
    const add = document.createElement("div");
    add.className = "card";
    add.innerHTML = "<h3>＋ Новая сцена</h3><p>2D canvas или 3D арена с камерой.</p>";
    const row = document.createElement("div");
    row.className = "row";
    const b2 = document.createElement("button");
    b2.textContent = "2D";
    b2.onclick = () => newScene("2D");
    const b3 = document.createElement("button");
    b3.className = "primary";
    b3.textContent = "3D";
    b3.onclick = () => newScene("3D");
    row.append(b2, b3);
    add.appendChild(row);
    box.appendChild(add);
  }

  function newScene(dim) {
    const name = prompt("Имя сцены", dim === "2D" ? "Menu" : "Arena");
    if (!name) return;
    const res = MFBridge.call("createScene", state.project, name, dim);
    if (!res.ok) return toast(res.error);
    state.scene = res.scene;
    toast("Сцена создана: " + res.scene.name);
    renderScenes();
  }

  function renderSceneListMini() {
    const box = $("sceneObjects");
    if (!box) return;
    box.innerHTML = "";
    if (!state.scene) {
      box.innerHTML = '<div class="empty">Нет сцены</div>';
      return;
    }
    (state.scene.objects || []).forEach((obj) => {
      const b = document.createElement("button");
      b.textContent = obj.name + " · " + obj.type;
      b.className = state.selected === obj.name ? "active" : "";
      b.onclick = () => { state.selected = obj.name; fillInspector(); drawViewport(); };
      box.appendChild(b);
    });
  }

  function selectedObject() {
    if (!state.scene) return null;
    return (state.scene.objects || []).find((o) => o.name === state.selected) || null;
  }

  function fillInspector() {
    const obj = selectedObject();
    const box = $("inspector");
    if (!obj) {
      box.innerHTML = "<b>INSPECTOR</b><div class='empty'>Выберите объект</div>";
      return;
    }
    const fields = [
      ["name", "text"], ["type", "select"], ["x", "number"], ["y", "number"], ["z", "number"],
      ["rx", "number"], ["ry", "number"], ["rz", "number"],
      ["sx", "number"], ["sy", "number"], ["sz", "number"],
      ["color", "color"], ["script", "text"], ["asset", "text"], ["speed", "number"]
    ];
    const types = ["Camera", "Player", "Mesh", "Sprite", "Ground", "Light", "Coin", "Enemy", "Empty", "Button"];
    box.innerHTML = "<b>INSPECTOR — " + obj.name + "</b>";
    fields.forEach(([key, kind]) => {
      const row = document.createElement("div");
      row.className = "field";
      const lab = document.createElement("span");
      lab.textContent = key;
      let input;
      if (kind === "select") {
        input = document.createElement("select");
        types.forEach((t) => {
          const o = document.createElement("option");
          o.value = t; o.textContent = t; if (obj.type === t) o.selected = true;
          input.appendChild(o);
        });
      } else {
        input = document.createElement("input");
        input.type = kind === "color" ? "color" : kind;
        input.step = "0.1";
        input.value = obj[key] == null ? "" : obj[key];
      }
      input.oninput = () => {
        obj[key] = kind === "number" ? Number(input.value) : input.value;
        if (key === "name") state.selected = obj.name;
        persistScene(false);
        drawViewport();
      };
      row.append(lab, input);
      box.appendChild(row);
    });
    const solid = document.createElement("button");
    solid.textContent = obj.solid ? "Solid: ON" : "Solid: OFF";
    solid.onclick = () => { obj.solid = !obj.solid; persistScene(false); fillInspector(); };
    box.appendChild(solid);
  }

  function persistScene(toastIt) {
    if (!state.project || !state.scene) return;
    const res = MFBridge.call("saveScene", state.project, JSON.stringify(state.scene));
    if (!res.ok) return toast(res.error);
    if (state.openPath === "Scenes/" + state.scene.name + ".scene.json") {
      $("editor").value = JSON.stringify(state.scene, null, 2);
      state.dirty = false;
    }
    if (toastIt) toast("Сцена сохранена");
  }

  function addObject(type) {
    if (!state.scene) return toast("Сначала создайте сцену");
    const name = type + (state.scene.objects.length + 1);
    const obj = MFEngine.defaults({
      name, type,
      x: type === "Ground" ? 0 : 2,
      y: type === "Camera" ? 5 : 1,
      z: type === "Camera" ? 10 : 0,
      color: type === "Coin" ? "#f4c95d" : type === "Enemy" ? "#ffb2c8" : "#b69cff",
      solid: type !== "Camera" && type !== "Light" && type !== "Coin"
    });
    state.scene.objects.push(obj);
    state.selected = name;
    persistScene(true);
    renderSceneListMini();
    fillInspector();
    drawViewport();
  }

  function deleteSelected() {
    if (!state.scene || !state.selected) return;
    state.scene.objects = state.scene.objects.filter((o) => o.name !== state.selected);
    state.selected = state.scene.objects[0] && state.scene.objects[0].name;
    persistScene(true);
    renderSceneListMini();
    fillInspector();
    drawViewport();
  }

  function drawViewport() {
    const canvas = $("viewport");
    if (!canvas || !canvas.getClientRects().length) return;
    const ctx = MFEngine.fitCanvas(canvas);
    if (!state.scene) {
      ctx.fillStyle = "#121922";
      ctx.fillRect(0, 0, canvas.width, canvas.height);
      return;
    }
    MFEngine.drawScene(ctx, state.scene, { selected: state.selected, orbit: state.orbit });
  }

  function collectScripts() {
    const scripts = {};
    if (!state.project || !state.scene) return scripts;
    (state.scene.objects || []).forEach((obj) => {
      if (!obj.script) return;
      const res = MFBridge.call("readFile", state.project, obj.script);
      if (res.ok) scripts[obj.script] = res.content;
    });
    return scripts;
  }

  function startPlay() {
    if (!requireProject()) return;
    if (!state.scene) {
      const res = MFBridge.call("scenes", state.project);
      state.scenes = res.scenes || [];
      state.scene = state.scenes[0];
    }
    if (!state.scene) return toast("Нет сцены для Play");
    if (state.runtime) state.runtime.stop();
    const canvas = $("playview");
    const hud = $("hud");
    state.runtime = MFEngine.createRuntime({
      scene: state.scene,
      scripts: collectScripts(),
      onFrame(scene, info) {
        const ctx = MFEngine.fitCanvas(canvas);
        MFEngine.drawScene(ctx, scene, { follow: true });
        hud.textContent = state.project + "  •  score " + info.score + (info.log[0] ? "  •  " + info.log[0] : "");
      }
    });
    bindPlayInput(state.runtime);
    state.runtime.start();
  }

  function bindPlayInput(runtime) {
    const setKey = (e, down) => {
      const k = e.key.toLowerCase();
      if (["arrowup", "arrowdown", "arrowleft", "arrowright", " ", "w", "a", "s", "d"].includes(k)) e.preventDefault();
      runtime.keys[k] = down;
      const x = (runtime.keys.d || runtime.keys.arrowright ? 1 : 0) - (runtime.keys.a || runtime.keys.arrowleft ? 1 : 0);
      const y = (runtime.keys.w || runtime.keys.arrowup ? 1 : 0) - (runtime.keys.s || runtime.keys.arrowdown ? 1 : 0);
      runtime.setInput({ x, y, jump: !!(runtime.keys[" "] || runtime.keys.shift), action: !!runtime.keys.enter });
    };
    window.onkeydown = (e) => setKey(e, true);
    window.onkeyup = (e) => setKey(e, false);
    const joy = $("joystick");
    const stick = $("stick");
    const apply = (clientX, clientY) => {
      const r = joy.getBoundingClientRect();
      const cx = r.left + r.width / 2, cy = r.top + r.height / 2;
      let dx = (clientX - cx) / 48, dy = (clientY - cy) / 48;
      const mag = Math.hypot(dx, dy) || 1;
      if (mag > 1) { dx /= mag; dy /= mag; }
      stick.style.left = (38 + dx * 28) + "px";
      stick.style.top = (38 + dy * 28) + "px";
      runtime.setInput({ x: dx, y: -dy });
    };
    const end = () => {
      stick.style.left = "38px";
      stick.style.top = "38px";
      runtime.setInput({ x: 0, y: 0 });
    };
    joy.ontouchstart = joy.onpointerdown = (e) => { e.preventDefault(); apply(e.clientX || e.touches[0].clientX, e.clientY || e.touches[0].clientY); };
    joy.ontouchmove = joy.onpointermove = (e) => { if (e.buttons || e.touches) apply(e.clientX || e.touches[0].clientX, e.clientY || e.touches[0].clientY); };
    joy.ontouchend = joy.onpointerup = end;
    $("btnJump").onclick = () => runtime.setInput({ jump: true });
    $("btnJump").onpointerup = () => runtime.setInput({ jump: false });
    $("btnAction").onclick = () => runtime.click();
  }

  function refreshModels() {
    const list = $("modelList");
    const preset = models[state.provider] || [];
    const custom = $("customModel").value.trim();
    const items = custom && state.provider === "custom" ? [custom] : preset.concat(custom ? [custom] : []);
    list.innerHTML = items.map((m) => "<option>" + m + "</option>").join("");
    if (!items.length) list.innerHTML = "<option>custom-model</option>";
  }

  function buildPrompt() {
    const task = $("aiTask").value.trim();
    const eventName = $("eventList").value;
    const lang = $("languageList").value;
    const fileCtx = state.openPath ? ("Active file: " + state.openPath + "\n```\n" + $("editor").value.slice(0, 4000) + "\n```\n") : "";
    const sceneCtx = state.scene ? ("Active scene " + state.scene.name + " " + state.scene.dimension + " objects: " + JSON.stringify(state.scene.objects).slice(0, 2500) + "\n") : "";
    return [
      "You are the MobileForge game-engine coding agent.",
      "Return complete file contents in fenced code blocks.",
      "Put the file path in the fence language tag, for example ```Scripts/Player.js",
      "Event hook: " + eventName,
      "Language: " + lang,
      "Project: " + (state.project || "none"),
      sceneCtx,
      fileCtx,
      "Task: " + task
    ].join("\n");
  }

  async function generateAi() {
    if (!$("aiTask").value.trim()) return toast("Опишите задачу");
    $("aiout").textContent = "Генерация…";
    $("generate").disabled = true;
    const prompt = buildPrompt();
    const model = $("modelList").value;
    const endpoint = $("customEndpoint").value.trim();
    const res = await MFBridge.generateAsync(state.provider, model, prompt, endpoint);
    $("generate").disabled = false;
    if (!res.ok) {
      $("aiout").textContent = "Ошибка: " + (res.error || "AI failed");
      return;
    }
    const files = MFTranspile.parseAiProposal(res.text);
    if (files.length === 1 && !files[0].path) {
      files[0].path = state.openPath || MFTranspile.guessPath($("languageList").value, $("eventList").value);
    }
    files.forEach((f) => {
      if (!f.path) f.path = MFTranspile.guessPath($("languageList").value, $("eventList").value);
    });
    state.proposal = { text: res.text, files };
    $("aiout").textContent = res.text;
    renderDiff();
    toast("Proposal готов — проверьте Review");
  }

  function renderDiff() {
    const box = $("diffBox");
    box.innerHTML = "";
    if (!state.proposal) return;
    state.proposal.files.forEach((file) => {
      const current = state.project ? MFBridge.call("readFile", state.project, file.path) : { ok: false };
      const col = document.createElement("div");
      col.innerHTML = "<h3></h3><div class='diff'><pre></pre><pre></pre></div>";
      col.querySelector("h3").textContent = file.path + (current.ok ? "" : " (new)");
      col.querySelectorAll("pre")[0].textContent = current.ok ? current.content : "(нет файла)";
      col.querySelectorAll("pre")[1].textContent = file.content;
      box.appendChild(col);
    });
  }

  function applyProposal() {
    if (!requireProject()) return;
    if (!state.proposal || !state.proposal.files.length) return toast("Сначала сгенерируйте proposal");
    state.proposal.files.forEach((file) => {
      const res = MFBridge.call("writeFile", state.project, file.path, file.content);
      if (!res.ok) toast(file.path + ": " + res.error);
    });
    toast("Применено файлов: " + state.proposal.files.length);
    refreshFiles();
    if (state.openPath) {
      const latest = state.proposal.files.find((f) => f.path === state.openPath);
      if (latest) $("editor").value = latest.content;
    }
  }

  function loadSettings() {
    ["zen", "openrouter", "mcp", "custom"].forEach((id) => {
      const cfg = MFBridge.call("providerConfig", id);
      if (id === "custom") $("customEndpoint").value = cfg.endpoint || $("customEndpoint").value;
      const dot = $("dot-" + id);
      if (dot) {
        dot.classList.toggle("off", !cfg.hasKey);
        dot.title = cfg.hasKey ? "ключ сохранён" : "ключа нет";
      }
      if (cfg.model && id === state.provider) {
        $("customModel").value = cfg.model;
      }
    });
    $("nativeFlag").textContent = state.native
      ? "Ключи пишутся в Android Keystore (AES-GCM)."
      : "Сейчас web-режим: ключи останутся в localStorage этой страницы. В APK используется Keystore.";
  }

  function saveSettings() {
    const map = [
      ["zen", "", $("zenKey").value],
      ["openrouter", "", $("orKey").value],
      ["mcp", "http://127.0.0.1:8765/mcp/call", $("mcpKey").value],
      ["custom", $("customEndpoint").value, $("customKey").value]
    ];
    map.forEach(([id, endpoint, key]) => {
      MFBridge.call("saveProvider", id, endpoint, key, $("customModel").value || "");
    });
    toast("Настройки сохранены");
    loadSettings();
  }

  function checkMcp() {
    const res = MFBridge.call("checkMcp");
    toast(res.ok ? ("MCP: " + (res.text || "ok")) : ("MCP: " + res.error));
    $("mcpOut").textContent = res.ok ? (res.text || "ok") : (res.error || "fail");
  }

  function testProvider() {
    const model = $("modelList").value;
    const res = MFBridge.call("testProvider", state.provider, model, $("customEndpoint").value);
    toast(res.ok ? "Провайдер ответил" : res.error);
    $("aiout").textContent = res.ok ? res.text : ("Тест: " + res.error);
  }

  function setupViewportDrag() {
    const canvas = $("viewport");
    let drag = null;
    canvas.onpointerdown = (e) => { drag = { x: e.clientX, y: e.clientY }; canvas.setPointerCapture(e.pointerId); };
    canvas.onpointermove = (e) => {
      if (!drag) return;
      state.orbit.yaw += (e.clientX - drag.x) * 0.4;
      state.orbit.pitch += (e.clientY - drag.y) * 0.2;
      drag = { x: e.clientX, y: e.clientY };
      drawViewport();
    };
    canvas.onpointerup = () => { drag = null; };
  }

  window.MFBack = function () {
    if (state.page === "play") { show("studio"); return true; }
    if (state.page !== "projects") { show("projects"); return true; }
    return false;
  };

  function init() {
    nav($("rail"), true);
    nav($("mobileNav"), false);
    const ver = MFBridge.call("version");
    state.native = !!(ver && ver.native);
    $("badge").textContent = state.native ? "native " + (ver.version || "") : "web preview";
    $("createProject").onclick = createProject;
    $("seedDemo").onclick = seedDemo;
    $("importProject").onclick = importProject;
    $("confirmImport").onclick = confirmImport;
    $("cancelImport").onclick = () => { $("modal").hidden = true; };
    $("saveFile").onclick = saveFile;
    $("newFile").onclick = newFile;
    $("deleteFile").onclick = deleteOpenFile;
    $("addMesh").onclick = () => addObject("Mesh");
    $("addPlayer").onclick = () => addObject("Player");
    $("addCoin").onclick = () => addObject("Coin");
    $("addLight").onclick = () => addObject("Light");
    $("delObj").onclick = deleteSelected;
    $("saveScene").onclick = () => persistScene(true);
    $("playScene").onclick = () => show("play");
    $("editor").oninput = () => { state.dirty = true; $("badge").textContent = "● не сохранено"; };
    document.querySelectorAll("#providers button").forEach((b) => {
      b.onclick = () => {
        state.provider = b.dataset.provider;
        document.querySelectorAll("#providers button").forEach((x) => x.classList.toggle("active", x === b));
        refreshModels();
        toast("Провайдер: " + b.textContent);
      };
    });
    $("generate").onclick = generateAi;
    $("review").onclick = () => { if (!state.proposal) return toast("Нет proposal"); renderDiff(); toast("Проверьте diff и нажмите Apply"); };
    $("apply").onclick = applyProposal;
    $("saveSettings").onclick = saveSettings;
    $("checkMcp").onclick = checkMcp;
    $("testProvider").onclick = testProvider;
    $("customModel").oninput = refreshModels;
    setupViewportDrag();
    window.addEventListener("resize", () => { drawViewport(); });
    refreshModels();
    show("projects");
  }

  document.addEventListener("DOMContentLoaded", init);
})();
