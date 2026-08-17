(function (global) {
  function transpileCs(source) {
    if (!source) return "";
    let code = String(source);
    code = code.replace(/\/\/.*$/gm, "");
    code = code.replace(/\/\*[\s\S]*?\*\//g, "");
    const methods = {};
    const methodRe = /(?:public|private|protected)?\s*(?:void|int|float|bool|string)\s+([A-Za-z_][A-Za-z0-9_]*)\s*\(([^)]*)\)\s*\{/g;
    let match;
    while ((match = methodRe.exec(source))) {
      const name = match[1];
      const start = match.index + match[0].length - 1;
      const end = matchingBrace(source, start);
      if (end < 0) continue;
      methods[name] = source.slice(start + 1, end);
    }
    const fields = [];
    const fieldRe = /(?:public|private|protected)?\s*(?:float|int|bool|string)\s+([A-Za-z_][A-Za-z0-9_]*)\s*=\s*([^;]+);/g;
    while ((match = fieldRe.exec(source))) {
      fields.push("api.object." + match[1] + " = " + jsLiteral(match[2]) + ";");
    }
    const startBody = convertBody(methods.Start || methods.OnStart || methods.OnSceneLoaded || "");
    const updateBody = convertBody(methods.Update || methods.OnUpdate || "");
    const collisionBody = convertBody(methods.OnCollisionEnter || "");
    const clickBody = convertBody(methods.OnButtonClick || "");
    return [
      "function onStart(api) {",
      fields.map((l) => "  " + l).join("\n"),
      startBody,
      "}",
      "function onUpdate(api, dt) {",
      updateBody,
      "}",
      "function onCollisionEnter(api, other) {",
      collisionBody,
      "}",
      "function onButtonClick(api) {",
      clickBody,
      "}"
    ].join("\n");
  }

  function matchingBrace(text, openIndex) {
    let depth = 0;
    for (let i = openIndex; i < text.length; i++) {
      if (text[i] === "{") depth++;
      else if (text[i] === "}") {
        depth--;
        if (depth === 0) return i;
      }
    }
    return -1;
  }

  function jsLiteral(raw) {
    const v = String(raw).trim().replace(/f$/i, "");
    if (v === "true" || v === "false") return v;
    if (/^-?\d+(\.\d+)?$/.test(v)) return v;
    return JSON.stringify(v.replace(/^"|"$/g, ""));
  }

  function convertBody(body) {
    return String(body || "")
      .replace(/\bfloat\b|\bint\b|\bbool\b|\bvar\b|\bnew\b/g, "let")
      .replace(/\bthis\./g, "api.object.")
      .replace(/\binput\.horizontal\b/g, "api.input.x")
      .replace(/\binput\.vertical\b/g, "api.input.y")
      .replace(/\binput\.jump\b/g, "api.input.jump")
      .replace(/\binput\.action\b/g, "api.input.action")
      .replace(/\bMove\s*\(/g, "api.move(")
      .replace(/\bJump\s*\(/g, "api.jump(")
      .replace(/\bAddScore\s*\(/g, "api.addScore(")
      .replace(/\bDestroy\s*\(/g, "api.destroy(")
      .replace(/\bSetPosition\s*\(/g, "api.setPosition(")
      .replace(/\bFind\s*\(/g, "api.find(")
      .replace(/\bLoadScene\s*\(/g, "api.loadScene(")
      .replace(/\bLog\s*\(/g, "api.log(")
      .replace(/(\w+)\s*==\s*"/g, '$1 === "')
      .replace(/(\w+)\s*!=\s*"/g, '$1 !== "')
      .split("\n")
      .map((line) => line.trim() ? "  " + line.trim() : "")
      .join("\n");
  }

  function scriptFromSource(path, source) {
    if (!source) return "";
    if (/\.cs$/i.test(path) || /public\s+class\s+/.test(source)) return transpileCs(source);
    return source;
  }

  function parseAiProposal(text) {
    const files = [];
    const fence = /```([^\n]*)\n([\s\S]*?)```/g;
    let match;
    while ((match = fence.exec(text))) {
      const header = match[1].trim();
      const body = match[2].replace(/\s+$/, "") + "\n";
      let path = "";
      const pathMatch = header.match(/(?:[\w./-]+\.(?:cs|js|kt|cpp|json|glsl|md|txt|lua|xml))/i);
      if (pathMatch) path = pathMatch[0];
      const first = body.split("\n")[0] || "";
      const fileLine = first.match(/^(?:\/\/|#|<!--)\s*([A-Za-z0-9_./-]+\.[A-Za-z0-9]+)/);
      if (!path && fileLine) path = fileLine[1];
      files.push({ path: normalizePath(path), content: body, language: header });
    }
    const fileMarks = /(?:FILE|PATH|Файл)\s*:\s*([A-Za-z0-9_./-]+\.[A-Za-z0-9]+)\s*\n([\s\S]*?)(?=(?:FILE|PATH|Файл)\s*:|$)/gi;
    while ((match = fileMarks.exec(text))) {
      files.push({ path: normalizePath(match[1]), content: match[2].trim() + "\n", language: "" });
    }
    if (!files.length && text.trim()) {
      files.push({ path: "", content: text.trim() + "\n", language: "" });
    }
    return files;
  }

  function normalizePath(path) {
    if (!path) return "";
    let p = path.replace(/\\/g, "/");
    if (!p.includes("/")) {
      if (/\.(cs|js|kt|cpp|lua)$/i.test(p)) p = "Scripts/" + p;
      else if (/\.scene\.json$/i.test(p)) p = "Scenes/" + p;
      else if (/\.(md|txt|json)$/i.test(p) && p !== "project.json") p = p;
    }
    return p.replace(/^\/+/, "");
  }

  function guessPath(language, eventName) {
    const ext = ({ Kotlin: "kt", "C++": "cpp", GLSL: "glsl", JSON: "json", JavaScript: "js", JS: "js" })[language] || "cs";
    const base = (eventName || "Generated").replace(/[^A-Za-z0-9_]/g, "") || "Generated";
    if (ext === "json") return "Scenes/" + base + ".scene.json";
    if (ext === "glsl") return "Assets/" + base + ".glsl";
    return "Scripts/" + base + "." + ext;
  }

  global.MFTranspile = { transpileCs, scriptFromSource, parseAiProposal, guessPath };
})(window);

if (typeof module !== "undefined") {
  module.exports = global.MFTranspile || window.MFTranspile;
}
