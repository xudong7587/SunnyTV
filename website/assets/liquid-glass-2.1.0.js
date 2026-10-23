const T = /* @__PURE__ */ new Set(["glass", "frosted", "aurora"]);
let I = 0;
function $(t = "auto") {
  if (typeof window > "u" || matchMedia("(forced-colors: active), (prefers-reduced-transparency: reduce)").matches || !CSS.supports("backdrop-filter", "blur(2px)") && !CSS.supports("-webkit-backdrop-filter", "blur(2px)")) return "solid";
  const r = /(?:Chrome|Chromium|Edg)\//.test(navigator.userAgent) && !/(?:CriOS|EdgiOS)\//.test(navigator.userAgent);
  return t === "refracted" || t !== "blur" && r ? "refracted" : "blur";
}
function j(t, r, e, s, d) {
  const a = t - e / 2, n = r - s / 2, l = Math.min(d, e / 2, s / 2), u = Math.abs(a) - (e / 2 - l), f = Math.abs(n) - (s / 2 - l), C = Math.hypot(Math.max(u, 0), Math.max(f, 0)) + Math.min(Math.max(u, f), 0) - l, w = Math.min(1, Math.max(0, -C / 18)), M = 1 - Math.sqrt(Math.max(0, 1 - (1 - w) ** 2)), g = Math.min(l * 1.5, e / 2, s / 2), y = Math.abs(a) - (e / 2 - g), o = Math.abs(n) - (s / 2 - g);
  let m = y >= 0 || o >= 0 ? Math.max(y, 0) : y >= o ? 1 : 0, v = y >= 0 || o >= 0 ? Math.max(o, 0) : y >= o ? 0 : 1;
  const A = Math.hypot(m, v) || 1;
  return m = m / A * Math.sign(a), v = v / A * Math.sign(n), [M * -14 * m, M * -14 * v];
}
function x(t, r) {
  const e = document.createElementNS("http://www.w3.org/2000/svg", t);
  for (const [s, d] of Object.entries(r)) e.setAttribute(s, d);
  return e;
}
function z(t, r = {}) {
  const e = { variant: t.getAttribute("data-sunny-glass"), renderer: t.getAttribute("data-glass-renderer"), filter: t.style.getPropertyValue("--sunny-glass-filter"), inner: t.style.getPropertyValue("--sunny-glass-inner-radius") }, s = r.variant ?? "glass", d = x("svg", { width: "0", height: "0", "aria-hidden": "true", focusable: "false" });
  d.style.cssText = "position:fixed;pointer-events:none;overflow:hidden";
  const a = `sunny-lens-${++I}`, n = x("filter", { id: a, filterUnits: "userSpaceOnUse", primitiveUnits: "userSpaceOnUse", "color-interpolation-filters": "sRGB" }), l = x("feGaussianBlur", { in: "SourceGraphic", stdDeviation: s === "frosted" ? "8" : "2.2", result: "diffused" }), u = x("feColorMatrix", { in: "diffused", type: "saturate", values: ".85", result: "soft" }), f = x("feImage", { result: "lens", preserveAspectRatio: "none" }), C = x("feDisplacementMap", { in: "soft", in2: "lens", scale: s === "frosted" ? "5.6" : "28", xChannelSelector: "R", yChannelSelector: "G" });
  n.append(l, u, f, C), d.append(n), document.body.append(d), t.dataset.sunnyGlass = s;
  let w = "", M = !1, g = 0;
  function y() {
    if (g = 0, M) return;
    const h = $(r.renderer);
    t.dataset.glassRenderer = h;
    const c = t.offsetWidth, b = t.offsetHeight;
    if (c < 1 || b < 1) return;
    const F = getComputedStyle(t), L = r.radius ?? (parseFloat(F.borderTopLeftRadius) || 0), k = Math.min(L, c / 2, b / 2), G = (parseFloat(F.paddingTop) || 0) + (parseFloat(F.borderTopWidth) || 0);
    if (t.style.setProperty("--sunny-glass-inner-radius", `${Math.max(0, k - G)}px`), h !== "refracted") {
      t.style.removeProperty("--sunny-glass-filter");
      return;
    }
    const O = [Math.round(c), Math.round(b), L].join(":");
    if (O !== w) {
      const p = document.createElement("canvas"), E = Math.min(1, 640 / Math.max(c, b));
      p.width = Math.max(1, Math.round(c * E)), p.height = Math.max(1, Math.round(b * E));
      const P = p.getContext("2d");
      if (!P) {
        t.dataset.glassRenderer = "blur";
        return;
      }
      const S = P.createImageData(p.width, p.height);
      for (let i = 0; i < p.height; i++) for (let R = 0; R < p.width; R++) {
        const [D, U] = j((R + 0.5) / E, (i + 0.5) / E, c, b, L), q = (i * p.width + R) * 4;
        S.data[q] = Math.round(255 * (0.5 + D / 28)), S.data[q + 1] = Math.round(255 * (0.5 + U / 28)), S.data[q + 2] = 128, S.data[q + 3] = 255;
      }
      P.putImageData(S, 0, 0);
      for (const i of [n, f])
        i.setAttribute("x", i === n ? "-40" : "0"), i.setAttribute("y", i === n ? "-40" : "0"), i.setAttribute("width", String(c + (i === n ? 80 : 0))), i.setAttribute("height", String(b + (i === n ? 80 : 0)));
      f.setAttribute("href", p.toDataURL()), w = O;
    }
    t.style.setProperty("--sunny-glass-filter", `url("#${a}")`);
  }
  function o() {
    !M && !g && (g = requestAnimationFrame(y));
  }
  const m = new ResizeObserver(o);
  m.observe(t);
  const v = new MutationObserver(o);
  v.observe(document.documentElement, { attributes: !0, attributeFilter: ["data-theme", "data-corner", "data-material"] });
  const A = [matchMedia("(forced-colors: active)"), matchMedia("(prefers-reduced-transparency: reduce)")];
  return A.forEach((h) => h.addEventListener("change", o)), t.addEventListener("toggle", o), o(), () => {
    M = !0, cancelAnimationFrame(g), m.disconnect(), v.disconnect(), d.remove(), A.forEach((h) => h.removeEventListener("change", o)), t.removeEventListener("toggle", o);
    for (const [h, c] of [["data-sunny-glass", e.variant], ["data-glass-renderer", e.renderer]])
      c == null ? t.removeAttribute(h) : t.setAttribute(h, c);
    e.filter ? t.style.setProperty("--sunny-glass-filter", e.filter) : t.style.removeProperty("--sunny-glass-filter"), e.inner ? t.style.setProperty("--sunny-glass-inner-radius", e.inner) : t.style.removeProperty("--sunny-glass-inner-radius");
  };
}
function B(t = document.documentElement) {
  const r = /* @__PURE__ */ new Map();
  let e = 0;
  const s = () => {
    e = 0;
    const a = t.dataset.material, n = a && T.has(a), l = new Set(n ? t.querySelectorAll(".topbar,.sunny-picker,.task-dialog,dialog.detail") : []);
    for (const [u, f] of r) (!l.has(u) || f.variant !== a) && (f.dispose(), r.delete(u));
    if (n)
      for (const u of l) r.has(u) || r.set(u, { variant: a, dispose: z(u, { variant: a }) });
  }, d = new MutationObserver(() => {
    e || (e = requestAnimationFrame(s));
  });
  return d.observe(t, { childList: !0, subtree: !0, attributes: !0, attributeFilter: ["data-material"] }), s(), () => {
    d.disconnect(), cancelAnimationFrame(e);
    for (const a of r.values()) a.dispose();
    r.clear();
  };
}
export {
  z as enhanceLiquidGlass,
  $ as glassRenderer,
  B as installLiquidGlass,
  j as lensDisplacement
};
