(function (global) {
  function cloneScene(scene) {
    return JSON.parse(JSON.stringify(scene));
  }

  function defaults(obj) {
    return Object.assign({
      name: "Object", type: "Empty",
      x: 0, y: 0, z: 0, rx: 0, ry: 0, rz: 0,
      sx: 1, sy: 1, sz: 1,
      color: "#b69cff", asset: "", script: "",
      solid: true, speed: 0, vx: 0, vy: 0, vz: 0
    }, obj || {});
  }

  function hexToRgb(hex) {
    const h = String(hex || "#b69cff").replace("#", "");
    const n = parseInt(h.length === 3 ? h.split("").map((c) => c + c).join("") : h, 16);
    return { r: (n >> 16) & 255, g: (n >> 8) & 255, b: n & 255 };
  }

  function shade(hex, amt) {
    const c = hexToRgb(hex);
    const k = (v) => Math.max(0, Math.min(255, v + amt));
    return "rgb(" + k(c.r) + "," + k(c.g) + "," + k(c.b) + ")";
  }

  function compileScript(path, source) {
    const js = global.MFTranspile.scriptFromSource(path, source);
    const fns = { onStart: null, onUpdate: null, onCollisionEnter: null, onButtonClick: null, onSceneLoaded: null };
    if (!js.trim()) return fns;
    const wrapped = js + ";\nreturn {onStart:typeof onStart==='function'?onStart:null,onUpdate:typeof onUpdate==='function'?onUpdate:null,onCollisionEnter:typeof onCollisionEnter==='function'?onCollisionEnter:null,onButtonClick:typeof onButtonClick==='function'?onButtonClick:null,onSceneLoaded:typeof onSceneLoaded==='function'?onSceneLoaded:null};";
    try {
      return Function(wrapped)();
    } catch (e) {
      console.error("Script compile failed", path, e);
      return fns;
    }
  }

  function createRuntime(options) {
    const input = { x: 0, y: 0, jump: false, action: false };
    const keys = {};
    let scene = cloneScene(options.scene);
    scene.objects = (scene.objects || []).map(defaults);
    const scripts = options.scripts || {};
    const compiled = {};
    const log = [];
    let score = 0;
    let elapsed = 0;
    let playing = false;
    let last = 0;
    let raf = 0;
    let loadSceneHandler = options.onLoadScene || null;

    scene.objects.forEach((obj) => {
      if (obj.script && scripts[obj.script]) compiled[obj.name] = compileScript(obj.script, scripts[obj.script]);
    });

    function apiFor(obj) {
      return {
        object: obj,
        scene,
        input,
        time: { dt: 0, elapsed },
        score,
        log(msg) { log.unshift(String(msg)); if (log.length > 40) log.pop(); },
        find(name) { return scene.objects.find((o) => o.name === name) || null; },
        move(dx, dy, dz) {
          obj.x += Number(dx) || 0;
          obj.y += Number(dy) || 0;
          obj.z += Number(dz) || 0;
        },
        setPosition(x, y, z) { obj.x = x; obj.y = y; obj.z = z; },
        jump(force) {
          if (obj.y <= groundY(obj) + 0.05) obj.vy = Number(force) || 6;
        },
        addScore(n) { score += Number(n) || 0; },
        destroy(name) {
          scene.objects = scene.objects.filter((o) => o.name !== name);
        },
        spawn(data) {
          const spawned = defaults(data);
          scene.objects.push(spawned);
          return spawned;
        },
        loadScene(name) { if (loadSceneHandler) loadSceneHandler(name); }
      };
    }

    function groundY(obj) {
      if (String(scene.dimension).toUpperCase() === "2D") {
        const grounds = scene.objects.filter((o) => o.type === "Ground" || o.type === "Sprite");
        let y = -8;
        grounds.forEach((g) => {
          if (Math.abs(obj.x - g.x) < (Math.abs(g.sx) + Math.abs(obj.sx)) * 0.6) {
            y = Math.max(y, g.y + Math.abs(g.sy) * 0.5 + Math.abs(obj.sy) * 0.5);
          }
        });
        return y;
      }
      return 1;
    }

    function aabb(a, b) {
      return Math.abs(a.x - b.x) < (Math.abs(a.sx) + Math.abs(b.sx)) * 0.55 &&
        Math.abs(a.y - b.y) < (Math.abs(a.sy) + Math.abs(b.sy)) * 0.55 &&
        Math.abs(a.z - b.z) < (Math.abs(a.sz) + Math.abs(b.sz)) * 0.55;
    }

    function step(dt) {
      elapsed += dt;
      const gravity = String(scene.dimension).toUpperCase() === "2D" ? 18 : 16;
      scene.objects.forEach((obj) => {
        const pack = compiled[obj.name];
        if (pack && pack.onUpdate) {
          const api = apiFor(obj);
          api.time = { dt, elapsed };
          api.score = score;
          try { pack.onUpdate(api, dt); } catch (e) { log.unshift("Update error: " + e.message); }
        }
        if (obj.type === "Player" || obj.type === "Enemy") {
          obj.vy = (obj.vy || 0) - gravity * dt;
          obj.y += obj.vy * dt;
          const gy = groundY(obj);
          if (obj.y < gy) { obj.y = gy; obj.vy = 0; }
        }
      });
      const list = scene.objects.slice();
      for (let i = 0; i < list.length; i++) {
        for (let j = i + 1; j < list.length; j++) {
          const a = list[i], b = list[j];
          if (!aabb(a, b)) continue;
          const pa = compiled[a.name], pb = compiled[b.name];
          if (pa && pa.onCollisionEnter) try { pa.onCollisionEnter(apiFor(a), b); } catch (e) { log.unshift(e.message); }
          if (pb && pb.onCollisionEnter) try { pb.onCollisionEnter(apiFor(b), a); } catch (e) { log.unshift(e.message); }
        }
      }
    }

    function fire(eventName, extra) {
      scene.objects.forEach((obj) => {
        const pack = compiled[obj.name];
        if (!pack) return;
        const fn = pack[eventName];
        if (!fn) return;
        try { fn(apiFor(obj), extra); } catch (e) { log.unshift(eventName + " error: " + e.message); }
      });
    }

    function loop(ts) {
      if (!playing) return;
      if (!last) last = ts;
      const dt = Math.min(0.05, (ts - last) / 1000);
      last = ts;
      step(dt);
      if (options.onFrame) options.onFrame(scene, { score, log, elapsed, input });
      raf = requestAnimationFrame(loop);
    }

    return {
      input,
      keys,
      get scene() { return scene; },
      get score() { return score; },
      get log() { return log; },
      get playing() { return playing; },
      start() {
        playing = true;
        last = 0;
        fire("onSceneLoaded");
        fire("onStart");
        raf = requestAnimationFrame(loop);
      },
      stop() {
        playing = false;
        cancelAnimationFrame(raf);
      },
      click() { fire("onButtonClick"); },
      setInput(partial) { Object.assign(input, partial); },
      reset(nextScene, nextScripts) {
        this.stop();
        scene = cloneScene(nextScene);
        scene.objects = (scene.objects || []).map(defaults);
        score = 0;
        elapsed = 0;
        log.length = 0;
        Object.keys(compiled).forEach((k) => delete compiled[k]);
        const pack = nextScripts || scripts;
        scene.objects.forEach((obj) => {
          if (obj.script && pack[obj.script]) compiled[obj.name] = compileScript(obj.script, pack[obj.script]);
        });
      }
    };
  }

  function project3d(x, y, z, cam, w, h) {
    const cx = cam.x, cy = cam.y, cz = cam.z;
    const yaw = (cam.ry || 0) * Math.PI / 180;
    const pitch = (cam.rx || 0) * Math.PI / 180;
    let dx = x - cx, dy = y - cy, dz = z - cz;
    const cosY = Math.cos(yaw), sinY = Math.sin(yaw);
    let rx = dx * cosY + dz * sinY;
    let rz = -dx * sinY + dz * cosY;
    const cosP = Math.cos(pitch), sinP = Math.sin(pitch);
    const ry = dy * cosP - rz * sinP;
    rz = dy * sinP + rz * cosP;
    const fov = 420;
    const depth = Math.max(0.4, rz);
    return { x: w / 2 + rx * fov / depth, y: h / 2 - ry * fov / depth, z: rz, s: fov / depth };
  }

  function drawScene(ctx, scene, opts) {
    const w = ctx.canvas.width, h = ctx.canvas.height;
    ctx.clearRect(0, 0, w, h);
    const g = ctx.createLinearGradient(0, 0, 0, h);
    g.addColorStop(0, "#15202b");
    g.addColorStop(1, "#0b0d14");
    ctx.fillStyle = g;
    ctx.fillRect(0, 0, w, h);
    const dim = String(scene.dimension || "3D").toUpperCase();
    const camObj = (scene.objects || []).find((o) => o.type === "Camera") || { x: 0, y: 5, z: 12, rx: -18, ry: 0 };
    const cam = Object.assign({ x: 0, y: 5, z: 12, rx: -18, ry: 0 }, camObj);
    if (opts && opts.follow) {
      const player = (scene.objects || []).find((o) => o.type === "Player");
      if (player && dim === "3D") {
        cam.x = player.x;
        cam.y = player.y + 5;
        cam.z = player.z + 11;
        cam.rx = -22;
      } else if (player) {
        cam.x = player.x;
        cam.y = player.y;
      }
    }
    if (opts && opts.orbit) {
      cam.ry += opts.orbit.yaw || 0;
      cam.rx += opts.orbit.pitch || 0;
    }
    if (dim === "2D") draw2d(ctx, scene, cam, opts);
    else draw3d(ctx, scene, cam, opts);
  }

  function draw2d(ctx, scene, cam, opts) {
    const w = ctx.canvas.width, h = ctx.canvas.height;
    const scale = 36;
    ctx.strokeStyle = "#ffffff14";
    ctx.lineWidth = 1;
    for (let i = -20; i <= 20; i++) {
      const x = w / 2 + (i - cam.x) * scale;
      ctx.beginPath(); ctx.moveTo(x, 0); ctx.lineTo(x, h); ctx.stroke();
    }
    (scene.objects || []).forEach((obj) => {
      if (obj.type === "Camera" || obj.type === "Light") return;
      const x = w / 2 + (obj.x - cam.x) * scale;
      const y = h / 2 - (obj.y - (cam.y || 0)) * scale;
      const bw = Math.max(8, Math.abs(obj.sx || 1) * scale);
      const bh = Math.max(8, Math.abs(obj.sy || 1) * scale);
      ctx.fillStyle = obj.color || "#b69cff";
      ctx.strokeStyle = opts && opts.selected === obj.name ? "#fff" : shade(obj.color, -30);
      ctx.lineWidth = opts && opts.selected === obj.name ? 3 : 1;
      ctx.beginPath();
      if (obj.type === "Coin") ctx.arc(x, y, bw * 0.4, 0, Math.PI * 2);
      else { ctx.roundRect ? ctx.roundRect(x - bw / 2, y - bh / 2, bw, bh, 6) : ctx.rect(x - bw / 2, y - bh / 2, bw, bh); }
      ctx.fill();
      ctx.stroke();
      ctx.fillStyle = "#ffffffcc";
      ctx.font = "12px sans-serif";
      ctx.fillText(obj.name, x - bw / 2, y - bh / 2 - 6);
    });
  }

  function draw3d(ctx, scene, cam, opts) {
    const w = ctx.canvas.width, h = ctx.canvas.height;
    const faces = [];
    for (let gz = -20; gz <= 20; gz += 2) {
      const a = project3d(-16, 0, gz, cam, w, h);
      const b = project3d(16, 0, gz, cam, w, h);
      if (a.z > 0.5 && b.z > 0.5) {
        ctx.strokeStyle = "#ffffff12";
        ctx.beginPath(); ctx.moveTo(a.x, a.y); ctx.lineTo(b.x, b.y); ctx.stroke();
      }
    }
    (scene.objects || []).forEach((obj) => {
      if (obj.type === "Camera") return;
      const sx = Math.abs(obj.sx || 1), sy = Math.abs(obj.sy || 1), sz = Math.abs(obj.sz || 1);
      const hw = sx * (obj.type === "Ground" ? 0.5 : 0.5);
      const hh = obj.type === "Ground" ? 0.08 : sy * 0.5;
      const hd = sz * 0.5;
      const corners = [
        [-hw, -hh, -hd], [hw, -hh, -hd], [hw, hh, -hd], [-hw, hh, -hd],
        [-hw, -hh, hd], [hw, -hh, hd], [hw, hh, hd], [-hw, hh, hd]
      ].map(([x, y, z]) => project3d(obj.x + x, obj.y + y, obj.z + z, cam, w, h));
      const quads = [
        [0, 1, 2, 3], [4, 5, 6, 7], [0, 1, 5, 4], [2, 3, 7, 6], [1, 2, 6, 5], [0, 3, 7, 4]
      ];
      quads.forEach((idx, qi) => {
        const pts = idx.map((i) => corners[i]);
        if (pts.some((p) => p.z < 0.4)) return;
        const z = pts.reduce((s, p) => s + p.z, 0) / 4;
        faces.push({ z, pts, color: shade(obj.color || "#b69cff", (qi - 2) * 16), selected: opts && opts.selected === obj.name, name: obj.name, top: qi === 3 });
      });
    });
    faces.sort((a, b) => b.z - a.z);
    faces.forEach((face) => {
      ctx.beginPath();
      ctx.moveTo(face.pts[0].x, face.pts[0].y);
      face.pts.slice(1).forEach((p) => ctx.lineTo(p.x, p.y));
      ctx.closePath();
      ctx.fillStyle = face.color;
      ctx.fill();
      ctx.strokeStyle = face.selected ? "#ffffff" : "#00000055";
      ctx.lineWidth = face.selected ? 2 : 1;
      ctx.stroke();
    });
  }

  function fitCanvas(canvas) {
    const rect = canvas.getBoundingClientRect();
    const dpr = Math.min(2, window.devicePixelRatio || 1);
    canvas.width = Math.max(320, Math.floor(rect.width * dpr));
    canvas.height = Math.max(220, Math.floor(rect.height * dpr));
    return canvas.getContext("2d");
  }

  global.MFEngine = { createRuntime, drawScene, fitCanvas, defaults, cloneScene };
})(window);
