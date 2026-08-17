const fs = require("fs");
const vm = require("vm");
const path = require("path");

const sandbox = { console, window: {}, module: { exports: {} } };
sandbox.global = sandbox;
sandbox.window = sandbox;
const root = path.join(__dirname, "..", "app/src/main/assets/js");
vm.runInNewContext(fs.readFileSync(path.join(root, "transpile.js"), "utf8"), sandbox);
const T = sandbox.MFTranspile;

const js = T.transpileCs(`
public class Player {
  public float speed = 6f;
  void Update() {
    Move(input.horizontal * speed, input.vertical * speed);
    if (input.jump) Jump(7f);
  }
  void OnCollisionEnter(other) {
    if (other.type == "Coin") { AddScore(1); Destroy(other.name); }
  }
}
`);
if (!js.includes("function onUpdate")) throw new Error("missing onUpdate");
if (!js.includes("api.move")) throw new Error("Move not mapped");
if (!js.includes("api.input.x")) throw new Error("input not mapped");
if (!js.includes("api.addScore")) throw new Error("AddScore not mapped");

const files = T.parseAiProposal("```Scripts/Foo.js\nfunction onStart(api){}\n```\n");
if (files[0].path !== "Scripts/Foo.js") throw new Error("path parse failed: " + files[0].path);

console.log("js-smoke ok");
