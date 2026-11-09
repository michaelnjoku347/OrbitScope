// Cubelight front end. Parameter indices match tracer/src/lib.rs.

const P = {
  WIDTH: 0, HEIGHT: 1, SYSTEM: 2, FORMAT: 3, BOUNCES: 4, SKY: 5, SEED: 6,
  YAW: 7, PITCH: 8, DIST: 9, FOV: 10, EXPOSURE: 11, VIGNETTE: 12,
  LIGHT_ANGLE: 13, LIGHT_ORBIT: 14, LIGHT_HEIGHT: 15, LIGHT_SIZE: 16, LIGHT_POWER: 17,
  LIGHT_R: 18, LIGHT_G: 19, LIGHT_B: 20,
  FLOOR_R: 21, FLOOR_G: 22, FLOOR_B: 23, FLOOR_ROUGH: 24, FLOOR_METAL: 25,
  CHECKER: 26, CHECKER_SCALE: 27, CUBE0: 28, STRIDE: 13,
};
const C = {
  X: 0, Z: 1, LIFT: 2, SIZE: 3, ROT: 4, METAL: 5, ROUGH: 6, TRANS: 7, EMIT: 8,
  R: 9, G: 10, B: 11, IOR: 12,
};

const SYSTEMS = [
  ["Direct", "One lamp, soft shadows as samples stack, and a cheap sky fill. Fastest to drag."],
  ["Whitted", "Classic ray tracing: mirror reflections, glass, and colored shadows."],
  ["Path", "Bounced light. The first frames are noisy; it settles as samples add up."],
  ["Normals", "Surface direction, mapped into color. Useful when a cube faces the wrong way."],
  ["Depth", "Distance from the camera, near to far."],
  ["Occlusion", "How boxed-in a point is. Contact shadows without the lamp."],
  ["Albedo", "The paint color before light."],
];

const FORMATS = [
  ["Gamma", "Exposure, then a plain sRGB curve."],
  ["Reinhard", "Highlights roll off gently."],
  ["ACES", "A film curve. This is the default print."],
  ["Punchy", "ACES with the contrast pushed."],
  ["Linear", "No gamma. A dim scientific view of the same light."],
  ["False color", "Luminance drawn as a heat map."],
];

const MATERIALS = [
  ["matte", "Matte"],
  ["metal", "Metal"],
  ["glass", "Glass"],
  ["lamp", "Lamp"],
];

const canvas = document.getElementById("view");
const ctx = canvas.getContext("2d", { willReadFrequently: true });
const statsEl = document.getElementById("stats");

const cam = { yaw: -0.66, pitch: 0.42, dist: 7.6 };
let api = null;
let paused = false;
let dragging = false;
let scheduled = false;
let orbit = false;
let compare = false;
let compareFormat = 0;
let wipe = 0.5;
let savedSystem = null;
let frameMs = 0;

const $ = (id) => document.getElementById(id);

function params() {
  return new Float32Array(api.memory.buffer, api.params_ptr(), api.param_len());
}

function srgbByte(c) {
  const x = c <= 0.0031308 ? c * 12.92 : 1.055 * Math.pow(Math.max(c, 0), 1 / 2.4) - 0.055;
  return Math.round(Math.min(1, Math.max(0, x)) * 255);
}

function hexToLinear(hex) {
  const n = parseInt(hex.slice(1), 16);
  const s = (v) => (v <= 0.04045 ? v / 12.92 : Math.pow((v + 0.055) / 1.055, 2.4));
  return [s(((n >> 16) & 255) / 255), s(((n >> 8) & 255) / 255), s((n & 255) / 255)];
}

function linearToHex(r, g, b) {
  const h = (v) => srgbByte(v).toString(16).padStart(2, "0");
  return `#${h(r)}${h(g)}${h(b)}`;
}

function wrapDeg(deg) {
  let d = deg % 360;
  if (d > 180) d -= 360;
  if (d < -180) d += 360;
  return Math.round(d);
}

function readNumber(id) {
  const el = $(id);
  const out = document.querySelector(`[data-for="${id}"]`);
  if (out) out.textContent = el.step && el.step.includes(".") ? Number(el.value).toFixed(el.step.split(".")[1].length) : el.value;
  return parseFloat(el.value);
}

function setRange(id, value, digits = 0) {
  const el = $(id);
  el.value = String(value);
  const out = document.querySelector(`[data-for="${id}"]`);
  if (out) out.textContent = digits ? Number(value).toFixed(digits) : String(Math.round(value));
}

function buildChoices(rootId, items, onPick) {
  const root = $(rootId);
  items.forEach((item, index) => {
    const button = document.createElement("button");
    button.type = "button";
    button.textContent = item[0];
    button.addEventListener("click", () => onPick(index));
    root.append(button);
  });
}

function markChoice(rootId, index) {
  [...$(rootId).children].forEach((button, i) => {
    button.setAttribute("aria-pressed", i === index ? "true" : "false");
  });
}

function systemName(id) {
  return SYSTEMS[id]?.[0] ?? "Custom";
}

function grab() {
  const w = api.frame_width();
  const h = api.frame_height();
  const src = new Uint8Array(api.memory.buffer, api.output_ptr(), w * h * 4);
  return { w, h, data: new Uint8ClampedArray(src) };
}

function paint(img) {
  if (canvas.width !== img.w || canvas.height !== img.h) {
    canvas.width = img.w;
    canvas.height = img.h;
  }
  ctx.putImageData(new ImageData(img.data, img.w, img.h), 0, 0);
}

function paintWipe(a, b, t) {
  const w = a.w;
  const h = a.h;
  const cut = Math.max(0, Math.min(w - 1, Math.floor(w * t)));
  const out = new Uint8ClampedArray(a.data);
  for (let y = 0; y < h; y++) {
    const start = (y * w + cut) * 4;
    const end = (y + 1) * w * 4;
    out.set(b.data.subarray(start, end), start);
    const line = (y * w + cut) * 4;
    out[line] = 243;
    out[line + 1] = 239;
    out[line + 2] = 230;
  }
  paint({ w, h, data: out });
}

function draw() {
  const pp = params();
  const main = pp[P.FORMAT];
  if (!compare) {
    api.present();
    paint(grab());
    return;
  }
  api.present();
  const left = grab();
  pp[P.FORMAT] = compareFormat;
  api.present();
  const right = grab();
  pp[P.FORMAT] = main;
  paintWipe(left, right, wipe);
}

function maxSpp() {
  return parseInt($("max-spp").value, 10);
}

function perFrame() {
  return parseInt($("per-frame").value, 10);
}

function updateHud(spp) {
  const pp = params();
  const sys = pp[P.SYSTEM] | 0;
  $("rebate-system").textContent = systemName(sys);
  $("rebate-res").textContent = `${api.frame_width()}×${api.frame_height()}`;
  $("rebate-spp").textContent = `${spp} spp`;
  const max = maxSpp();
  $("bar").style.width = `${Math.min(100, (spp / max) * 100)}%`;
  const heavy = frameMs > 160 ? " Drop the resolution if dragging feels sticky." : "";
  const state = paused ? "Paused." : spp >= max && !orbit ? "Settled." : "Sampling.";
  statsEl.textContent = `${systemName(sys)} · ${FORMATS[pp[P.FORMAT] | 0][0]} · ${spp}/${max} spp · ${frameMs.toFixed(1)} ms${heavy} ${state}`;
}

function kick() {
  if (scheduled || !api) return;
  scheduled = true;
  requestAnimationFrame(frame);
}

function frame() {
  scheduled = false;
  if (!api || dragging || paused) return;
  if (orbit) {
    cam.yaw += 0.012;
    params()[P.YAW] = cam.yaw;
    setRange("yaw", wrapDeg((cam.yaw * 180) / Math.PI));
  }
  const t0 = performance.now();
  const spp = api.trace(perFrame());
  frameMs = performance.now() - t0;
  draw();
  updateHud(spp);
  updateLegend();
  if (orbit || spp < maxSpp()) kick();
}

function changed() {
  if (!api) return;
  if (paused) {
    const t0 = performance.now();
    const spp = api.trace(perFrame());
    frameMs = performance.now() - t0;
    draw();
    updateHud(spp);
    updateLegend();
    return;
  }
  kick();
}

function postChanged() {
  if (!api) return;
  draw();
  updateHud(api.sample_count());
}

function bindRange(id, apply) {
  $(id).addEventListener("input", () => {
    apply(readNumber(id));
    changed();
  });
}

function bindPost(id, apply) {
  $(id).addEventListener("input", () => {
    apply(readNumber(id));
    postChanged();
  });
}

function inferMaterial(pp, i) {
  const b = P.CUBE0 + i * P.STRIDE;
  if (pp[b + C.EMIT] > 0.8) return "lamp";
  if (pp[b + C.TRANS] > 0.5) return "glass";
  if (pp[b + C.METAL] > 0.5) return "metal";
  return "matte";
}

function applyMaterial(i, kind) {
  const b = P.CUBE0 + i * P.STRIDE;
  const pp = params();
  if (kind === "matte") {
    pp[b + C.METAL] = 0;
    pp[b + C.ROUGH] = 0.75;
    pp[b + C.TRANS] = 0;
    pp[b + C.EMIT] = 0;
  } else if (kind === "metal") {
    pp[b + C.METAL] = 1;
    pp[b + C.ROUGH] = 0.12;
    pp[b + C.TRANS] = 0;
    pp[b + C.EMIT] = 0;
  } else if (kind === "glass") {
    pp[b + C.METAL] = 0;
    pp[b + C.ROUGH] = 0.02;
    pp[b + C.TRANS] = 1;
    pp[b + C.EMIT] = 0;
    if (pp[b + C.IOR] < 1.2) pp[b + C.IOR] = 1.5;
  } else if (kind === "lamp") {
    pp[b + C.METAL] = 0;
    pp[b + C.ROUGH] = 0.45;
    pp[b + C.TRANS] = 0;
    pp[b + C.EMIT] = 5;
  }
}

function cubeCard(i) {
  const names = ["Left cube", "Center cube", "Right cube"];
  const card = document.createElement("article");
  card.className = "cube";
  card.dataset.cube = String(i);
  card.innerHTML = `
    <h3>${names[i]}</h3>
    <label>Material
      <select data-field="material">${MATERIALS.map(([id, name]) => `<option value="${id}">${name}</option>`).join("")}</select>
    </label>
    <label>Color <input data-field="color" type="color"></label>
    <div class="grid2">
      <label>X <b data-for="c${i}-x">0</b><input id="c${i}-x" data-field="x" type="range" min="-3.4" max="3.4" step="0.01"></label>
      <label>Z <b data-for="c${i}-z">0</b><input id="c${i}-z" data-field="z" type="range" min="-3.2" max="3.2" step="0.01"></label>
      <label>Lift <b data-for="c${i}-lift">0</b><input id="c${i}-lift" data-field="lift" type="range" min="0" max="2" step="0.01"></label>
      <label>Size <b data-for="c${i}-size">1</b><input id="c${i}-size" data-field="size" type="range" min="0.3" max="2.2" step="0.01"></label>
      <label>Rotate <b data-for="c${i}-rot">0</b><input id="c${i}-rot" data-field="rot" type="range" min="-180" max="180" step="1"></label>
      <label>Rough <b data-for="c${i}-rough">0.5</b><input id="c${i}-rough" data-field="rough" type="range" min="0" max="1" step="0.01"></label>
      <label>Metal <b data-for="c${i}-metal">0</b><input id="c${i}-metal" data-field="metal" type="range" min="0" max="1" step="0.01"></label>
      <label>Glass <b data-for="c${i}-trans">0</b><input id="c${i}-trans" data-field="trans" type="range" min="0" max="1" step="0.01"></label>
      <label>Emit <b data-for="c${i}-emit">0</b><input id="c${i}-emit" data-field="emit" type="range" min="0" max="10" step="0.1"></label>
      <label>IOR <b data-for="c${i}-ior">1.5</b><input id="c${i}-ior" data-field="ior" type="range" min="1.05" max="2.4" step="0.01"></label>
    </div>`;
  card.querySelector('[data-field="material"]').addEventListener("change", (event) => {
    applyMaterial(i, event.target.value);
    syncControls();
    changed();
  });
  card.querySelector('[data-field="color"]').addEventListener("input", (event) => {
    const [r, g, b] = hexToLinear(event.target.value);
    const pp = params();
    const base = P.CUBE0 + i * P.STRIDE;
    pp[base + C.R] = r;
    pp[base + C.G] = g;
    pp[base + C.B] = b;
    card.style.setProperty("--swatch", event.target.value);
    changed();
  });
  const fields = {
    x: C.X, z: C.Z, lift: C.LIFT, size: C.SIZE, rot: C.ROT, rough: C.ROUGH,
    metal: C.METAL, trans: C.TRANS, emit: C.EMIT, ior: C.IOR,
  };
  for (const [name, field] of Object.entries(fields)) {
    const input = card.querySelector(`[data-field="${name}"]`);
    input.addEventListener("input", () => {
      const digits = (input.step || "1").includes(".") ? input.step.split(".")[1].length : 0;
      const out = card.querySelector(`[data-for="c${i}-${name}"]`);
      if (out) out.textContent = digits ? Number(input.value).toFixed(digits) : input.value;
      params()[P.CUBE0 + i * P.STRIDE + field] = parseFloat(input.value);
      card.querySelector('[data-field="material"]').value = inferMaterial(params(), i);
      changed();
    });
  }
  return card;
}

function syncControls() {
  const pp = params();
  cam.yaw = pp[P.YAW];
  cam.pitch = pp[P.PITCH];
  cam.dist = pp[P.DIST];
  markChoice("systems", pp[P.SYSTEM] | 0);
  markChoice("formats", pp[P.FORMAT] | 0);
  $("system-blurb").textContent = SYSTEMS[pp[P.SYSTEM] | 0][1];
  $("format-blurb").textContent = FORMATS[pp[P.FORMAT] | 0][1];
  setRange("exposure", pp[P.EXPOSURE], 2);
  setRange("vignette", pp[P.VIGNETTE], 2);
  setRange("bounces", pp[P.BOUNCES]);
  setRange("seed", pp[P.SEED]);
  setRange("light-angle", pp[P.LIGHT_ANGLE]);
  setRange("light-orbit", pp[P.LIGHT_ORBIT], 2);
  setRange("light-height", pp[P.LIGHT_HEIGHT], 2);
  setRange("light-size", pp[P.LIGHT_SIZE], 2);
  setRange("light-power", pp[P.LIGHT_POWER]);
  $("light-color").value = linearToHex(pp[P.LIGHT_R], pp[P.LIGHT_G], pp[P.LIGHT_B]);
  $("sky").value = String(pp[P.SKY] | 0);
  $("floor-color").value = linearToHex(pp[P.FLOOR_R], pp[P.FLOOR_G], pp[P.FLOOR_B]);
  $("checker").checked = pp[P.CHECKER] > 0.5;
  setRange("checker-scale", pp[P.CHECKER_SCALE], 2);
  setRange("floor-rough", pp[P.FLOOR_ROUGH], 2);
  setRange("floor-metal", pp[P.FLOOR_METAL], 2);
  setRange("yaw", wrapDeg((cam.yaw * 180) / Math.PI));
  setRange("pitch", (cam.pitch * 180) / Math.PI);
  setRange("dist", cam.dist, 2);
  setRange("fov", pp[P.FOV]);
  const res = `${pp[P.WIDTH] | 0}x${pp[P.HEIGHT] | 0}`;
  if ([...$("resolution").options].some((opt) => opt.value === res)) $("resolution").value = res;

  document.querySelectorAll(".cube").forEach((card) => {
    const i = Number(card.dataset.cube);
    const b = P.CUBE0 + i * P.STRIDE;
    card.querySelector('[data-field="material"]').value = inferMaterial(pp, i);
    const color = linearToHex(pp[b + C.R], pp[b + C.G], pp[b + C.B]);
    card.querySelector('[data-field="color"]').value = color;
    card.style.setProperty("--swatch", color);
    const values = {
      x: [pp[b + C.X], 2], z: [pp[b + C.Z], 2], lift: [pp[b + C.LIFT], 2],
      size: [pp[b + C.SIZE], 2], rot: [pp[b + C.ROT], 0], rough: [pp[b + C.ROUGH], 2],
      metal: [pp[b + C.METAL], 2], trans: [pp[b + C.TRANS], 2], emit: [pp[b + C.EMIT], 1],
      ior: [pp[b + C.IOR], 2],
    };
    for (const [name, [value, digits]] of Object.entries(values)) {
      const input = card.querySelector(`[data-field="${name}"]`);
      input.value = String(value);
      const out = card.querySelector(`[data-for="c${i}-${name}"]`);
      if (out) out.textContent = digits ? Number(value).toFixed(digits) : String(Math.round(value));
    }
  });
  updateLegend();
}

function updateLegend() {
  if (!api) return;
  const pp = params();
  const root = $("legend");
  const labels = ["Left", "Center", "Right"];
  root.replaceChildren(...[0, 1, 2].map((i) => {
    const b = P.CUBE0 + i * P.STRIDE;
    const li = document.createElement("li");
    const sw = document.createElement("span");
    sw.className = "swatch";
    sw.style.background = linearToHex(pp[b + C.R], pp[b + C.G], pp[b + C.B]);
    li.append(sw, document.createTextNode(`${labels[i]} · ${inferMaterial(pp, i)}`));
    return li;
  }));
}

function fillFormatSelect() {
  const select = $("compare-format");
  FORMATS.forEach((item, index) => {
    const opt = document.createElement("option");
    opt.value = String(index);
    opt.textContent = item[0];
    select.append(opt);
  });
  select.value = "0";
}

function downloadBlob(filename, blob) {
  const url = URL.createObjectURL(blob);
  const a = document.createElement("a");
  a.href = url;
  a.download = filename;
  a.click();
  URL.revokeObjectURL(url);
}

function currentImage() {
  const pp = params();
  const main = pp[P.FORMAT];
  api.present();
  const img = grab();
  pp[P.FORMAT] = main;
  return img;
}

function savePng() {
  const img = currentImage();
  const c = document.createElement("canvas");
  c.width = img.w;
  c.height = img.h;
  c.getContext("2d").putImageData(new ImageData(img.data, img.w, img.h), 0, 0);
  c.toBlob((blob) => {
    if (blob) downloadBlob(`cubelight-${systemName(params()[P.SYSTEM] | 0).toLowerCase()}.png`, blob);
    draw();
  });
}

function savePpm() {
  const img = currentImage();
  const header = `P6\n${img.w} ${img.h}\n255\n`;
  const body = new Uint8Array(img.w * img.h * 3);
  for (let i = 0, j = 0; i < img.data.length; i += 4, j += 3) {
    body[j] = img.data[i];
    body[j + 1] = img.data[i + 1];
    body[j + 2] = img.data[i + 2];
  }
  const bytes = new Uint8Array(header.length + body.length);
  bytes.set(new TextEncoder().encode(header), 0);
  bytes.set(body, header.length);
  downloadBlob("cubelight.ppm", new Blob([bytes], { type: "application/octet-stream" }));
  draw();
}

function setColorParam(rIndex, hex) {
  const [r, g, b] = hexToLinear(hex);
  const pp = params();
  pp[rIndex] = r;
  pp[rIndex + 1] = g;
  pp[rIndex + 2] = b;
}

function presetStudio() {
  api.reset_defaults();
}

function presetDusk() {
  api.reset_defaults();
  const pp = params();
  pp[P.SKY] = 1;
  pp[P.EXPOSURE] = 1.3;
  pp[P.LIGHT_ANGLE] = 24;
  pp[P.LIGHT_POWER] = 34;
  pp[P.LIGHT_HEIGHT] = 3.6;
  setColorParam(P.LIGHT_R, "#ff8a4a");
  const gold = hexToLinear("#e7b15a");
  const b = P.CUBE0 + P.STRIDE;
  pp[b + C.R] = gold[0];
  pp[b + C.G] = gold[1];
  pp[b + C.B] = gold[2];
  pp[b + C.ROUGH] = 0.16;
}

function presetNeon() {
  api.reset_defaults();
  const pp = params();
  pp[P.SKY] = 2;
  pp[P.EXPOSURE] = 1.35;
  pp[P.LIGHT_POWER] = 18;
  pp[P.LIGHT_SIZE] = 0.28;
  pp[P.LIGHT_HEIGHT] = 3.4;
  pp[P.LIGHT_ORBIT] = 3.2;
  setColorParam(P.LIGHT_R, "#7af0ff");
  pp[P.FLOOR_METAL] = 0.55;
  pp[P.FLOOR_ROUGH] = 0.22;
  setColorParam(P.FLOOR_R, "#2c2a33");
  const colors = ["#ff3d8a", "#b8fff6", "#ffe56a"];
  colors.forEach((hex, i) => {
    const [r, g, b] = hexToLinear(hex);
    const base = P.CUBE0 + i * P.STRIDE;
    pp[base + C.R] = r;
    pp[base + C.G] = g;
    pp[base + C.B] = b;
  });
  const metal = P.CUBE0 + P.STRIDE;
  pp[metal + C.EMIT] = 0.7;
  applyMaterial(2, "lamp");
  const lamp = P.CUBE0 + 2 * P.STRIDE;
  const yellow = hexToLinear("#ffe56a");
  pp[lamp + C.R] = yellow[0];
  pp[lamp + C.G] = yellow[1];
  pp[lamp + C.B] = yellow[2];
  pp[lamp + C.EMIT] = 7;
}

function presetClay() {
  api.reset_defaults();
  const pp = params();
  pp[P.SYSTEM] = 0;
  pp[P.SKY] = 0;
  pp[P.CHECKER] = 0;
  pp[P.LIGHT_SIZE] = 0.85;
  pp[P.LIGHT_POWER] = 40;
  pp[P.FLOOR_ROUGH] = 0.8;
  setColorParam(P.FLOOR_R, "#e7e0d4");
  setColorParam(P.LIGHT_R, "#fff4e4");
  const colors = ["#d4654a", "#e6d2a8", "#7f8f62"];
  colors.forEach((hex, i) => {
    applyMaterial(i, "matte");
    const [r, g, b] = hexToLinear(hex);
    const base = P.CUBE0 + i * P.STRIDE;
    pp[base + C.R] = r;
    pp[base + C.G] = g;
    pp[base + C.B] = b;
  });
}

function shuffle() {
  const pp = params();
  const kinds = ["matte", "metal", "glass", "lamp"];
  for (let i = 0; i < 3; i++) {
    const base = P.CUBE0 + i * P.STRIDE;
    const hue = Math.random();
    const rgb = hslToRgb(hue, 0.55 + Math.random() * 0.35, 0.55);
    const lin = hexToLinear(rgb);
    pp[base + C.R] = lin[0];
    pp[base + C.G] = lin[1];
    pp[base + C.B] = lin[2];
    pp[base + C.ROT] = Math.round(Math.random() * 180 - 90);
    pp[base + C.LIFT] = Math.random() < 0.3 ? Math.random() * 0.6 : 0;
    applyMaterial(i, kinds[Math.floor(Math.random() * kinds.length)]);
    if (inferMaterial(pp, i) === "lamp") {
      pp[base + C.R] = lin[0];
      pp[base + C.G] = lin[1];
      pp[base + C.B] = lin[2];
    }
  }
  pp[P.LIGHT_ANGLE] = Math.round(Math.random() * 360 - 180);
}

function hslToRgb(h, s, l) {
  const a = s * Math.min(l, 1 - l);
  const f = (n) => {
    const k = (n + h * 12) % 12;
    const c = l - a * Math.max(-1, Math.min(k - 3, Math.min(9 - k, 1)));
    return Math.round(c * 255).toString(16).padStart(2, "0");
  };
  return `#${f(0)}${f(8)}${f(4)}`;
}

function bindCameraDrag() {
  let lastX = 0;
  let lastY = 0;
  canvas.addEventListener("pointerdown", (event) => {
    dragging = true;
    lastX = event.clientX;
    lastY = event.clientY;
    canvas.setPointerCapture(event.pointerId);
    if ((params()[P.SYSTEM] | 0) === 2) {
      savedSystem = 2;
      params()[P.SYSTEM] = 0;
    }
  });
  canvas.addEventListener("pointermove", (event) => {
    if (!dragging) return;
    const dx = event.clientX - lastX;
    const dy = event.clientY - lastY;
    lastX = event.clientX;
    lastY = event.clientY;
    const pp = params();
    if (event.shiftKey) {
      pp[P.LIGHT_ANGLE] = Math.max(-180, Math.min(180, pp[P.LIGHT_ANGLE] + dx * 0.35));
      pp[P.LIGHT_HEIGHT] = Math.max(0.6, Math.min(10, pp[P.LIGHT_HEIGHT] - dy * 0.02));
      setRange("light-angle", pp[P.LIGHT_ANGLE]);
      setRange("light-height", pp[P.LIGHT_HEIGHT], 2);
    } else {
      cam.yaw += dx * 0.007;
      cam.pitch = Math.max(0.12, Math.min(1.25, cam.pitch + dy * 0.005));
      pp[P.YAW] = cam.yaw;
      pp[P.PITCH] = cam.pitch;
      setRange("yaw", wrapDeg((cam.yaw * 180) / Math.PI));
      setRange("pitch", (cam.pitch * 180) / Math.PI);
    }
    const t0 = performance.now();
    const spp = api.trace(1);
    frameMs = performance.now() - t0;
    draw();
    updateHud(spp);
  });
  const end = () => {
    if (!dragging) return;
    dragging = false;
    if (savedSystem !== null) {
      params()[P.SYSTEM] = savedSystem;
      savedSystem = null;
      markChoice("systems", params()[P.SYSTEM] | 0);
    }
    if (!paused) kick();
  };
  canvas.addEventListener("pointerup", end);
  canvas.addEventListener("pointercancel", end);
  canvas.addEventListener("dblclick", () => {
    cam.yaw = -0.66;
    cam.pitch = 0.42;
    cam.dist = 7.6;
    const pp = params();
    pp[P.YAW] = cam.yaw;
    pp[P.PITCH] = cam.pitch;
    pp[P.DIST] = cam.dist;
    syncControls();
    changed();
  });
  canvas.addEventListener("wheel", (event) => {
    event.preventDefault();
    cam.dist = Math.max(3, Math.min(16, cam.dist * Math.exp(event.deltaY * 0.0011)));
    params()[P.DIST] = cam.dist;
    setRange("dist", cam.dist, 2);
    changed();
  }, { passive: false });
}

function bindUi() {
  buildChoices("systems", SYSTEMS, (index) => {
    params()[P.SYSTEM] = index;
    $("system-blurb").textContent = SYSTEMS[index][1];
    markChoice("systems", index);
    changed();
  });
  buildChoices("formats", FORMATS, (index) => {
    params()[P.FORMAT] = index;
    $("format-blurb").textContent = FORMATS[index][1];
    markChoice("formats", index);
    postChanged();
  });
  fillFormatSelect();
  for (let i = 0; i < 3; i++) $("cubes").append(cubeCard(i));

  bindPost("exposure", (v) => { params()[P.EXPOSURE] = v; });
  bindPost("vignette", (v) => { params()[P.VIGNETTE] = v; });
  bindRange("bounces", (v) => { params()[P.BOUNCES] = v; });
  bindRange("seed", (v) => { params()[P.SEED] = v; });
  bindRange("light-angle", (v) => { params()[P.LIGHT_ANGLE] = v; });
  bindRange("light-orbit", (v) => { params()[P.LIGHT_ORBIT] = v; });
  bindRange("light-height", (v) => { params()[P.LIGHT_HEIGHT] = v; });
  bindRange("light-size", (v) => { params()[P.LIGHT_SIZE] = v; });
  bindRange("light-power", (v) => { params()[P.LIGHT_POWER] = v; });
  bindRange("checker-scale", (v) => { params()[P.CHECKER_SCALE] = v; });
  bindRange("floor-rough", (v) => { params()[P.FLOOR_ROUGH] = v; });
  bindRange("floor-metal", (v) => { params()[P.FLOOR_METAL] = v; });
  bindRange("fov", (v) => { params()[P.FOV] = v; });
  bindRange("yaw", (deg) => {
    cam.yaw = deg * Math.PI / 180;
    params()[P.YAW] = cam.yaw;
  });
  bindRange("pitch", (deg) => {
    cam.pitch = deg * Math.PI / 180;
    params()[P.PITCH] = cam.pitch;
  });
  bindRange("dist", (v) => {
    cam.dist = v;
    params()[P.DIST] = v;
  });

  $("light-color").addEventListener("input", (event) => {
    setColorParam(P.LIGHT_R, event.target.value);
    changed();
  });
  $("floor-color").addEventListener("input", (event) => {
    setColorParam(P.FLOOR_R, event.target.value);
    changed();
  });
  $("checker").addEventListener("change", () => {
    params()[P.CHECKER] = $("checker").checked ? 1 : 0;
    changed();
  });
  $("sky").addEventListener("change", () => {
    params()[P.SKY] = parseInt($("sky").value, 10);
    changed();
  });
  $("resolution").addEventListener("change", () => {
    const [w, h] = $("resolution").value.split("x").map(Number);
    const pp = params();
    pp[P.WIDTH] = w;
    pp[P.HEIGHT] = h;
    changed();
  });
  $("per-frame").addEventListener("input", () => {
    readNumber("per-frame");
    if (!paused) kick();
  });
  $("max-spp").addEventListener("input", () => {
    readNumber("max-spp");
    updateHud(api.sample_count());
    if (!paused) kick();
  });
  $("orbit").addEventListener("change", () => {
    orbit = $("orbit").checked;
    if (orbit && !paused) kick();
  });
  $("compare").addEventListener("change", () => {
    compare = $("compare").checked;
    $("compare-controls").hidden = !compare;
    draw();
  });
  $("compare-format").addEventListener("change", () => {
    compareFormat = parseInt($("compare-format").value, 10);
    draw();
  });
  $("wipe").addEventListener("input", () => {
    wipe = parseInt($("wipe").value, 10) / 100;
    draw();
  });
  $("pause").addEventListener("click", () => {
    paused = !paused;
    $("pause").textContent = paused ? "Resume" : "Pause";
    if (!paused) kick();
  });
  $("png").addEventListener("click", savePng);
  $("ppm").addEventListener("click", savePpm);
  $("shuffle").addEventListener("click", () => {
    shuffle();
    syncControls();
    changed();
  });
  $("reset-cam").addEventListener("click", () => {
    cam.yaw = -0.66;
    cam.pitch = 0.42;
    cam.dist = 7.6;
    const pp = params();
    pp[P.YAW] = cam.yaw;
    pp[P.PITCH] = cam.pitch;
    pp[P.DIST] = cam.dist;
    pp[P.FOV] = 36;
    syncControls();
    changed();
  });
  document.querySelectorAll("[data-preset]").forEach((button) => {
    button.addEventListener("click", () => {
      const name = button.dataset.preset;
      if (name === "studio") presetStudio();
      if (name === "dusk") presetDusk();
      if (name === "neon") presetNeon();
      if (name === "clay") presetClay();
      syncControls();
      changed();
    });
  });

  window.addEventListener("keydown", (event) => {
    if (event.target.matches("input, select, textarea")) return;
    if (event.key >= "1" && event.key <= "7") {
      const index = Number(event.key) - 1;
      params()[P.SYSTEM] = index;
      markChoice("systems", index);
      $("system-blurb").textContent = SYSTEMS[index][1];
      changed();
    } else if (event.key.toLowerCase() === "f") {
      const next = ((params()[P.FORMAT] | 0) + 1) % FORMATS.length;
      params()[P.FORMAT] = next;
      markChoice("formats", next);
      $("format-blurb").textContent = FORMATS[next][1];
      postChanged();
    } else if (event.key === " ") {
      event.preventDefault();
      $("pause").click();
    } else if (event.key === "[") {
      const v = Math.max(0.3, params()[P.EXPOSURE] - 0.05);
      params()[P.EXPOSURE] = v;
      setRange("exposure", v, 2);
      postChanged();
    } else if (event.key === "]") {
      const v = Math.min(3, params()[P.EXPOSURE] + 0.05);
      params()[P.EXPOSURE] = v;
      setRange("exposure", v, 2);
      postChanged();
    }
  });
  bindCameraDrag();
}

async function boot() {
  try {
    const url = new URL("cubelight.wasm", import.meta.url);
    const bytes = await fetch(url).then((res) => {
      if (!res.ok) throw new Error(`WASM fetch failed (${res.status})`);
      return res.arrayBuffer();
    });
    const { instance } = await WebAssembly.instantiate(bytes, {});
    api = instance.exports;
    api.init(960 * 540);
    bindUi();
    syncControls();
    changed();
  } catch (error) {
    statsEl.textContent = `The tracer did not start. ${error.message}. Run ./build.sh from the tracer folder, then refresh.`;
  }
}

boot();
