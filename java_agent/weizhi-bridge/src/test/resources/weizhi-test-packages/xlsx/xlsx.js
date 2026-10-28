/**
 * xlsx.js — Workbook model, read/write .xlsx (cells + formulas). P0: rewrite-on-save.
 *
 * import { Workbook, readXlsx, renderXlsx } from "weizhi:xlsx";
 */
var fs = require("fs");
var zip = require("zip");

var SS_NS = "http://schemas.openxmlformats.org/spreadsheetml/2006/main";
var REL_NS = "http://schemas.openxmlformats.org/package/2006/relationships";
var PKG_REL =
  "http://schemas.openxmlformats.org/officeDocument/2006/relationships";
var OFF_DOC =
  "http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument";
var WS_REL =
  "http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet";
var SST_REL =
  "http://schemas.openxmlformats.org/officeDocument/2006/relationships/sharedStrings";
var STY_REL =
  "http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles";

function xmlEscape(s) {
  return String(s)
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;");
}

function platformFiles() {
  if (typeof linux !== "undefined" && linux.platform === "linux" && linux.files) {
    return linux.files;
  }
  if (typeof mac !== "undefined" && mac.platform === "mac" && mac.files) {
    return mac.files;
  }
  if (typeof android !== "undefined" && android.platform === "android" && android.files) {
    return android.files;
  }
  return null;
}

function unpack(xlsxPath, workDir) {
  var files = platformFiles();
  if (files && files.zipExtract) {
    files.zipExtract(xlsxPath, workDir);
    return;
  }
  zip.extractSync(xlsxPath, workDir);
}

function packDir(sourceDir, outXlsx) {
  var files = platformFiles();
  if (files && files.zipCreate) {
    files.zipCreate(sourceDir, outXlsx);
    return;
  }
  zip.createSync(sourceDir, outXlsx);
}

function mkdirp(relDir) {
  var files = platformFiles();
  if (!files || !files.mkdir) {
    throw new Error("xlsx: platform files.mkdir required (install caps)");
  }
  var parts = relDir.split("/");
  var acc = "";
  for (var i = 0; i < parts.length; i++) {
    if (!parts[i]) {
      continue;
    }
    acc = acc ? acc + "/" + parts[i] : parts[i];
    files.mkdir(acc);
  }
}

function writeUtf8(relPath, text) {
  var parent = relPath.replace(/\/[^/]+$/, "");
  if (parent && parent !== relPath) {
    mkdirp(parent);
  }
  fs.writeFileSync(relPath, text);
}

function readUtf8(relPath) {
  return fs.readFileSync(relPath).toString();
}

function colToName(index) {
  var n = Number(index);
  if (!(n >= 0) || n !== Math.floor(n)) {
    throw new Error("bad argument: col index");
  }
  var s = "";
  n = n + 1;
  while (n > 0) {
    var rem = (n - 1) % 26;
    s = String.fromCharCode(65 + rem) + s;
    n = Math.floor((n - 1) / 26);
  }
  return s;
}

function parseCellRef(ref) {
  var m = String(ref).trim().toUpperCase().match(/^([A-Z]+)([1-9][0-9]*)$/);
  if (!m) {
    throw new Error("bad argument: cell ref " + ref);
  }
  var col = 0;
  for (var i = 0; i < m[1].length; i++) {
    col = col * 26 + (m[1].charCodeAt(i) - 64);
  }
  return { col: col - 1, row: parseInt(m[2], 10) - 1, a1: m[1] + m[2] };
}

function cellA1(col, row) {
  return colToName(col) + String(row + 1);
}

function normalizeFormula(f) {
  var s = String(f == null ? "" : f).trim();
  if (s.charAt(0) === "=") {
    s = s.slice(1);
  }
  return s;
}

/* ------------------------------------------------------------------ *
 * 轻量公式求值器（D 轮）：同 sheet 引用 + 常用函数子集。
 * 目的：setFormula 省略缓存值时自动求值写 <v>；evaluate() 供 Agent 自校验。
 * 不做跨表引用；不支持函数按 not-supported 降级（不写缓存，Excel 打开重算）。
 * ------------------------------------------------------------------ */

function EvError(message) {
  return { ok: false, error: message };
}

function tokenizeFormula(src) {
  var s = String(src).toUpperCase();
  var tokens = [];
  var i = 0;
  var len = s.length;
  while (i < len) {
    var ch = s.charAt(i);
    if (ch === " " || ch === "\t" || ch === "\n" || ch === "\r") {
      i++;
      continue;
    }
    // 字符串字面量（大小写敏感，需从原文取）
    if (ch === '"') {
      var raw = String(src);
      var j = i + 1;
      var out = "";
      while (j < raw.length) {
        if (raw.charAt(j) === '"') {
          if (raw.charAt(j + 1) === '"') {
            out += '"';
            j += 2;
            continue;
          }
          break;
        }
        out += raw.charAt(j);
        j++;
      }
      if (j >= raw.length) {
        throw new Error("unterminated string literal");
      }
      tokens.push({ k: "str", v: out });
      i = j + 1;
      continue;
    }
    // 数字
    if (/[0-9]/.test(ch) || (ch === "." && /[0-9]/.test(s.charAt(i + 1)))) {
      var m = s.slice(i).match(/^[0-9]*\.?[0-9]+(?:[eE][+-]?[0-9]+)?/);
      tokens.push({ k: "num", v: Number(m[0]) });
      i += m[0].length;
      continue;
    }
    // 引用 / 函数名
    var idm = s.slice(i).match(/^\$?[A-Z]{1,3}\$?[0-9]+(?![0-9]*(\(|[A-Z0-9_.]*\())/);
    var fm = s.slice(i).match(/^[A-Z][A-Z0-9._]*/);
    if (idm) {
      tokens.push({ k: "ref", v: idm[0].replace(/\$/g, "") });
      i += idm[0].length;
      continue;
    }
    if (fm && s.charAt(i + fm[0].length) === "(") {
      tokens.push({ k: "func", v: fm[0] });
      i += fm[0].length;
      continue;
    }
    if (fm) {
      throw new Error("unknown token: " + fm[0]);
    }
    // 运算符
    var two = s.slice(i, i + 2);
    if (two === "<=" || two === ">=" || two === "<>") {
      tokens.push({ k: "op", v: two });
      i += 2;
      continue;
    }
    if ("=<>+-*/^&%(),:".indexOf(ch) >= 0) {
      tokens.push({ k: "op", v: ch });
      i++;
      continue;
    }
    throw new Error("bad char in formula: " + ch);
  }
  return tokens;
}

function parseFormulaTokens(tokens) {
  var pos = 0;

  function peek() {
    return tokens[pos];
  }
  function next() {
    return tokens[pos++];
  }
  function expectOp(op) {
    var t = next();
    if (!t || t.k !== "op" || t.v !== op) {
      throw new Error("expected " + op);
    }
  }

  function parseExpr() {
    var left = parseConcat();
    while (true) {
      var t = peek();
      if (t && t.k === "op" && (t.v === "=" || t.v === "<>" || t.v === "<" || t.v === ">" || t.v === "<=" || t.v === ">=")) {
        next();
        var right = parseConcat();
        left = { k: "cmp", op: t.v, l: left, r: right };
        continue;
      }
      return left;
    }
  }

  function parseConcat() {
    var left = parseAddSub();
    while (true) {
      var t = peek();
      if (t && t.k === "op" && t.v === "&") {
        next();
        left = { k: "concat", l: left, r: parseAddSub() };
        continue;
      }
      return left;
    }
  }

  function parseAddSub() {
    var left = parseMulDiv();
    while (true) {
      var t = peek();
      if (t && t.k === "op" && (t.v === "+" || t.v === "-")) {
        next();
        left = { k: "bin", op: t.v, l: left, r: parseMulDiv() };
        continue;
      }
      return left;
    }
  }

  function parseMulDiv() {
    var left = parseUnary();
    while (true) {
      var t = peek();
      if (t && t.k === "op" && (t.v === "*" || t.v === "/")) {
        next();
        left = { k: "bin", op: t.v, l: left, r: parseUnary() };
        continue;
      }
      return left;
    }
  }

  function parseUnary() {
    var t = peek();
    if (t && t.k === "op" && (t.v === "-" || t.v === "+")) {
      next();
      return { k: "un", op: t.v, v: parseUnary() };
    }
    return parsePower();
  }

  function parsePower() {
    var base = parsePostfix();
    var t = peek();
    if (t && t.k === "op" && t.v === "^") {
      next();
      return { k: "bin", op: "^", l: base, r: parseUnary() };
    }
    return base;
  }

  function parsePostfix() {
    var v = parsePrimary();
    var t = peek();
    if (t && t.k === "op" && t.v === "%") {
      next();
      return { k: "pct", v: v };
    }
    return v;
  }

  function parsePrimary() {
    var t = next();
    if (!t) {
      throw new Error("unexpected end of formula");
    }
    if (t.k === "num") {
      return { k: "lit", t: "n", v: t.v };
    }
    if (t.k === "str") {
      return { k: "lit", t: "s", v: t.v };
    }
    if (t.k === "ref") {
      var nt = peek();
      if (nt && nt.k === "op" && nt.v === ":") {
        next();
        var t2 = next();
        if (!t2 || t2.k !== "ref") {
          throw new Error("bad range");
        }
        return { k: "range", a: t.v, b: t2.v };
      }
      return { k: "ref", v: t.v };
    }
    if (t.k === "func") {
      expectOp("(");
      var args = [];
      if (peek() && !(peek().k === "op" && peek().v === ")")) {
        args.push(parseExpr());
        while (peek() && peek().k === "op" && peek().v === ",") {
          next();
          args.push(parseExpr());
        }
      }
      expectOp(")");
      return { k: "call", name: t.v, args: args };
    }
    if (t.k === "op" && t.v === "(") {
      var v = parseExpr();
      expectOp(")");
      return v;
    }
    if (t.k === "op" && (t.v === "-" || t.v === "+")) {
      return { k: "un", op: t.v, v: parsePrimary() };
    }
    throw new Error("unexpected token: " + t.v);
  }

  var ast = parseExpr();
  if (pos !== tokens.length) {
    throw new Error("unexpected trailing tokens");
  }
  return ast;
}

var EV_FUNCTIONS = {
  SUM: 1, AVERAGE: 1, MIN: 1, MAX: 1, COUNT: 1, COUNTA: 1, PRODUCT: 1,
  IF: 1, IFERROR: 1, AND: 1, OR: 1, NOT: 1,
  ROUND: 1, ABS: 1, SQRT: 1, POWER: 1, MOD: 1, INT: 1,
  CONCAT: 1, CONCATENATE: 1, LEN: 1, LEFT: 1, RIGHT: 1, MID: 1,
  UPPER: 1, LOWER: 1, TRIM: 1,
};

function flattenArg(values) {
  var out = [];
  for (var i = 0; i < values.length; i++) {
    var v = values[i];
    if (Array.isArray(v)) {
      for (var j = 0; j < v.length; j++) {
        out.push(v[j]);
      }
    } else {
      out.push(v);
    }
  }
  return out;
}

function evNumber(v) {
  if (v && v.t === "n") {
    return v.v;
  }
  if (v && v.t === "s" && v.v !== "" && !isNaN(Number(v.v))) {
    return Number(v.v);
  }
  if (v && v.t === "b") {
    return v.v ? 1 : 0;
  }
  if (!v || v.t === "blank") {
    return 0;
  }
  return null;
}

function evString(v) {
  if (!v || v.t === "blank") {
    return "";
  }
  if (v.t === "s") {
    return v.v;
  }
  if (v.t === "n") {
    return String(v.v);
  }
  if (v.t === "b") {
    return v.v ? "TRUE" : "FALSE";
  }
  return "";
}

function evBool(v) {
  if (v && v.t === "b") {
    return v.v;
  }
  if (v && v.t === "n") {
    return v.v !== 0;
  }
  if (v && v.t === "s") {
    var up = String(v.v).toUpperCase();
    if (up === "TRUE") return true;
    if (up === "FALSE") return false;
  }
  return false;
}

function numVal(t, n) {
  return { t: t, v: n };
}

function callFunction(name, argValues) {
  var flat = flattenArg(argValues);
  var nums = [];
  for (var i = 0; i < flat.length; i++) {
    var n = evNumber(flat[i]);
    if (n != null) {
      nums.push(n);
    }
  }
  switch (name) {
    case "SUM":
      return numVal("n", nums.reduce(function (a, b) { return a + b; }, 0));
    case "AVERAGE":
      if (!nums.length) {
        throw new Error("AVERAGE of empty range");
      }
      return numVal("n", nums.reduce(function (a, b) { return a + b; }, 0) / nums.length);
    case "MIN":
      if (!nums.length) return numVal("n", 0);
      return numVal("n", Math.min.apply(null, nums));
    case "MAX":
      if (!nums.length) return numVal("n", 0);
      return numVal("n", Math.max.apply(null, nums));
    case "COUNT":
      return numVal("n", nums.length);
    case "COUNTA": {
      var c = 0;
      for (var j = 0; j < flat.length; j++) {
        if (flat[j] && flat[j].t !== "blank") c++;
      }
      return numVal("n", c);
    }
    case "PRODUCT":
      return numVal("n", nums.reduce(function (a, b) { return a * b; }, 1));
    case "IF":
      if (argValues.length < 2) throw new Error("IF needs 2-3 args");
      return evBool(argValues[0])
        ? (argValues[1] || { t: "b", v: true })
        : (argValues[2] != null ? argValues[2] : { t: "b", v: false });
    case "IFERROR": {
      if (argValues.length < 2) throw new Error("IFERROR needs 2 args");
      return argValues[1];
    }
    case "AND": {
      for (var a = 0; a < flat.length; a++) {
        if (flat[a] && flat[a].t !== "blank" && !evBool(flat[a])) return { t: "b", v: false };
      }
      return { t: "b", v: true };
    }
    case "OR": {
      var any = false;
      for (var o = 0; o < flat.length; o++) {
        if (flat[o] && evBool(flat[o])) any = true;
      }
      return { t: "b", v: any };
    }
    case "NOT":
      return { t: "b", v: !evBool(flat[0] || { t: "blank" }) };
    case "ROUND": {
      var x = evNumber(argValues[0]) || 0;
      var d = argValues[1] != null ? Math.round(evNumber(argValues[1]) || 0) : 0;
      var p = Math.pow(10, d);
      return numVal("n", Math.round(x * p) / p);
    }
    case "ABS":
      return numVal("n", Math.abs(evNumber(argValues[0]) || 0));
    case "SQRT": {
      var q = evNumber(argValues[0]) || 0;
      if (q < 0) throw new Error("SQRT of negative");
      return numVal("n", Math.sqrt(q));
    }
    case "POWER":
      return numVal("n", Math.pow(evNumber(argValues[0]) || 0, evNumber(argValues[1]) || 0));
    case "MOD": {
      var m = evNumber(argValues[1]) || 0;
      if (m === 0) throw new Error("MOD by zero");
      return numVal("n", (evNumber(argValues[0]) || 0) % m);
    }
    case "INT":
      return numVal("n", Math.floor(evNumber(argValues[0]) || 0));
    case "CONCAT":
    case "CONCATENATE": {
      var s = "";
      for (var c2 = 0; c2 < flat.length; c2++) {
        s += evString(flat[c2]);
      }
      return { t: "s", v: s };
    }
    case "LEN":
      return numVal("n", evString(argValues[0]).length);
    case "LEFT":
      return { t: "s", v: evString(argValues[0]).slice(0, Math.round(evNumber(argValues[1]) || 0)) };
    case "RIGHT": {
      var r = Math.round(evNumber(argValues[1]) || 0);
      var rs = evString(argValues[0]);
      return { t: "s", v: r > 0 ? rs.slice(-r) : "" };
    }
    case "MID": {
      var ms = evString(argValues[0]);
      var start = Math.round(evNumber(argValues[1]) || 1);
      var count = Math.round(evNumber(argValues[2]) || 0);
      return { t: "s", v: ms.substr(Math.max(0, start - 1), count) };
    }
    case "UPPER":
      return { t: "s", v: evString(argValues[0]).toUpperCase() };
    case "LOWER":
      return { t: "s", v: evString(argValues[0]).toLowerCase() };
    case "TRIM":
      return { t: "s", v: evString(argValues[0]).trim() };
    default:
      throw new Error("unsupported function: " + name);
  }
}

function cmpValues(op, l, r) {
  var ln = evNumber(l);
  var rn = evNumber(r);
  var bothNum = ln != null && rn != null && (l.t !== "s" || r.t !== "s");
  var res;
  if (bothNum) {
    switch (op) {
      case "=": res = ln === rn; break;
      case "<>": res = ln !== rn; break;
      case "<": res = ln < rn; break;
      case ">": res = ln > rn; break;
      case "<=": res = ln <= rn; break;
      default: res = ln >= rn; break;
    }
    return { t: "b", v: res };
  }
  var ls = evString(l);
  var rs = evString(r);
  switch (op) {
    case "=": return { t: "b", v: ls === rs };
    case "<>": return { t: "b", v: ls !== rs };
    case "<": return { t: "b", v: ls < rs };
    case ">": return { t: "b", v: ls > rs };
    case "<=": return { t: "b", v: ls <= rs };
    default: return { t: "b", v: ls >= rs };
  }
}

function evalAst(node, sheet, state) {
  switch (node.k) {
    case "lit":
      return { t: node.t, v: node.v };
    case "ref":
      return evalCellRef(sheet, node.v, state);
    case "range":
      return evalRangeRef(sheet, node.a, node.b, state);
    case "un": {
      var v = evalAst(node.v, sheet, state);
      var n = evNumber(v);
      if (n == null) throw new Error("unary on non-number");
      return { t: "n", v: node.op === "-" ? -n : n };
    }
    case "pct":
      return { t: "n", v: (evNumber(evalAst(node.v, sheet, state)) || 0) / 100 };
    case "bin": {
      var a = evNumber(evalAst(node.l, sheet, state));
      var b = evNumber(evalAst(node.r, sheet, state));
      if (a == null || b == null) throw new Error("arithmetic on non-number");
      switch (node.op) {
        case "+": return { t: "n", v: a + b };
        case "-": return { t: "n", v: a - b };
        case "*": return { t: "n", v: a * b };
        case "/":
          if (b === 0) throw new Error("divide by zero");
          return { t: "n", v: a / b };
        default:
          return { t: "n", v: Math.pow(a, b) };
      }
    }
    case "concat":
      return { t: "s", v: evString(evalAst(node.l, sheet, state)) + evString(evalAst(node.r, sheet, state)) };
    case "cmp":
      return cmpValues(node.op, evalAst(node.l, sheet, state), evalAst(node.r, sheet, state));
    case "call": {
      var argValues = [];
      for (var i = 0; i < node.args.length; i++) {
        argValues.push(evalAst(node.args[i], sheet, state));
      }
      return callFunction(node.name, argValues);
    }
    default:
      throw new Error("bad ast node");
  }
}

function evalCellRef(sheet, ref, state) {
  var a1 = parseCellRef(ref).a1;
  if (state.stack[a1]) {
    throw new Error("circular reference: " + a1);
  }
  if (Object.prototype.hasOwnProperty.call(state.memo, a1)) {
    return state.memo[a1];
  }
  var c = sheet.cells[a1];
  if (!c) {
    return { t: "blank" };
  }
  if (c.type === "formula") {
    if (c._evaluating) {
      throw new Error("circular reference: " + a1);
    }
    c._evaluating = true;
    state.stack[a1] = true;
    try {
      // stack 已由本函数维护，不再向 evalFormulaAst 传 selfA1（避免重复检测）。
      var result = evalFormulaAst(c.formula, sheet, state);
      state.memo[a1] = result;
      return result;
    } finally {
      delete state.stack[a1];
      c._evaluating = false;
    }
  }
  if (c.type === "number") {
    return { t: "n", v: c.value };
  }
  if (c.type === "boolean") {
    return { t: "b", v: c.value };
  }
  if (c.type === "string" || c.type === "date") {
    return { t: "s", v: c.value != null ? c.value : "" };
  }
  return { t: "blank" };
}

function evalRangeRef(sheet, refA, refB, state) {
  var a = parseCellRef(refA);
  var b = parseCellRef(refB);
  var r0 = Math.min(a.row, b.row);
  var r1 = Math.max(a.row, b.row);
  var c0 = Math.min(a.col, b.col);
  var c1 = Math.max(a.col, b.col);
  if ((r1 - r0 + 1) * (c1 - c0 + 1) > 100000) {
    throw new Error("range too large");
  }
  var out = [];
  for (var r = r0; r <= r1; r++) {
    for (var c = c0; c <= c1; c++) {
      out.push(evalCellRef(sheet, cellA1(c, r), state));
    }
  }
  return out;
}

function evalFormulaAst(formula, sheet, state) {
  if (!state.astCache) {
    state.astCache = Object.create(null);
  }
  var ast;
  if (Object.prototype.hasOwnProperty.call(state.astCache, formula)) {
    ast = state.astCache[formula];
  } else {
    ast = parseFormulaTokens(tokenizeFormula(formula));
    state.astCache[formula] = ast;
  }
  return evalAst(ast, sheet, state);
}

/** 求值一条公式（同 sheet）。失败返回 {ok:false, error}。 */
export function evaluateFormula(formula, sheet) {
  var f = normalizeFormula(formula);
  if (!f) {
    return EvError("empty formula");
  }
  var stripped = f.replace(/"(?:[^"]|"")*"/g, '""');
  if (/['!]/.test(stripped)) {
    return EvError("cross-sheet reference not supported by evaluator");
  }
  var state = { memo: Object.create(null), stack: Object.create(null), astCache: Object.create(null) };
  try {
    var v = evalFormulaAst(f, sheet, state, null);
    if (v.t === "n") {
      return { ok: true, value: v.v, valueType: "number" };
    }
    if (v.t === "s") {
      return { ok: true, value: v.v, valueType: "string" };
    }
    if (v.t === "b") {
      return { ok: true, value: v.v, valueType: "boolean" };
    }
    return { ok: true, value: 0, valueType: "number" };
  } catch (e) {
    return EvError(String(e.message || e));
  }
}

function guessType(v) {
  if (v == null || v === "") {
    return "blank";
  }
  if (typeof v === "number" && isFinite(v)) {
    return "number";
  }
  if (typeof v === "boolean") {
    return "boolean";
  }
  var s = String(v);
  if (/^-?\d+(\.\d+)?([eE][+-]?\d+)?$/.test(s)) {
    return "number";
  }
  return "string";
}

function Sheet(name, cells) {
  this.name = String(name || "Sheet1");
  this.cells = cells || Object.create(null);
  /** 合并单元格区域（"A1:B2"），读写均保留。 */
  this.mergeCells = [];
  /** 原生图表（addChart 写入；读已有文件不解析图表）。 */
  this.charts = [];
  /** 保真改写（1.2.0）：readXlsx 时记录原始 sheet XML 的行/单元格结构。 */
  this._origRows = null;
  this._origSheetPart = null;
  this._dirtyRefs = null;
  this._mergesDirty = false;
  this._patched = false;
  /** 透视表结构（读回；数据值本身在普通单元格里）。 */
  this.pivotTables = [];
}

Sheet.prototype._markDirty = function (a1) {
  if (!this._dirtyRefs) {
    this._dirtyRefs = Object.create(null);
  }
  this._dirtyRefs[a1] = true;
};

/** 该 sheet 自 readXlsx 后是否有结构/内容改动（决定 patch 与否）。 */
Sheet.prototype._isDirty = function () {
  if (this._mergesDirty || this._patched) {
    return true;
  }
  return this._dirtyRefs != null && Object.keys(this._dirtyRefs).length > 0;
};

Sheet.prototype.get = function (ref) {
  var a1 = parseCellRef(ref).a1;
  var c = this.cells[a1];
  if (!c) {
    return { ref: a1, type: "blank", value: null, formula: null };
  }
  return {
    ref: a1,
    type: c.type || "blank",
    value: c.value != null ? c.value : null,
    formula: c.formula != null ? c.formula : null,
  };
};

/** 读回的日期格在求值器里按字符串参与（数字聚合会跳过，避免把 45000 当数值）。 */
Sheet.prototype._evalValue = function (a1) {
  var c = this.cells[a1];
  if (!c) return { t: "blank" };
  if (c.type === "formula") return null;
  if (c.type === "number") return { t: "n", v: c.value };
  if (c.type === "boolean") return { t: "b", v: c.value };
  return { t: "s", v: c.value != null ? c.value : "" };
};

Sheet.prototype.set = function (ref, value) {
  var a1 = parseCellRef(ref).a1;
  if (value == null || value === "") {
    delete this.cells[a1];
    this._markDirty(a1);
    return this;
  }
  var type = guessType(value);
  var cell = { type: type };
  if (type === "number") {
    cell.value = typeof value === "number" ? value : Number(value);
  } else if (type === "boolean") {
    cell.value = !!value;
  } else {
    cell.type = "string";
    cell.value = String(value);
  }
  this.cells[a1] = cell;
  this._markDirty(a1);
  return this;
};

Sheet.prototype.setFormula = function (ref, formula, cachedValue) {
  var a1 = parseCellRef(ref).a1;
  var f = normalizeFormula(formula);
  if (!f) {
    throw new Error("bad argument: setFormula: formula required");
  }
  var cell = { type: "formula", formula: f };
  // 先落位（自引用/循环检测需要看到自身），再决定缓存值。
  this.cells[a1] = cell;
  if (cachedValue != null && cachedValue !== "") {
    var t = guessType(cachedValue);
    if (t === "number") {
      cell.value = typeof cachedValue === "number" ? cachedValue : Number(cachedValue);
      cell.valueType = "number";
    } else if (t === "boolean") {
      cell.value = !!cachedValue;
      cell.valueType = "boolean";
    } else {
      cell.value = String(cachedValue);
      cell.valueType = "string";
    }
  } else {
    // 自动求值缓存（1.3.0）：成功则写 <v>，失败静默降级（Excel 打开重算）。
    var ev = evaluateFormula(f, this);
    if (ev.ok) {
      cell.value = ev.value;
      cell.valueType = ev.valueType;
    }
  }
  this._markDirty(a1);
  return this;
};

/**
 * 求值指定格：公式格重算（含依赖链与循环检测），字面格返回现值。
 * 返回 {ok, value, valueType} 或 {ok:false, error}。
 */
Sheet.prototype.evaluate = function (ref) {
  var a1 = parseCellRef(ref).a1;
  var c = this.cells[a1];
  if (c && c.type === "formula") {
    return evaluateFormula(c.formula, this);
  }
  var g = this.get(a1);
  if (g.type === "blank" || g.value == null) {
    return { ok: true, value: null, valueType: "blank" };
  }
  if (g.type === "boolean") {
    return { ok: true, value: g.value, valueType: "boolean" };
  }
  if (g.type === "number") {
    return { ok: true, value: g.value, valueType: "number" };
  }
  return { ok: true, value: g.value, valueType: "string" };
};

Sheet.prototype.clear = function (ref) {
  var a1 = parseCellRef(ref).a1;
  delete this.cells[a1];
  this._markDirty(a1);
  return this;
};

Sheet.prototype.dimensions = function () {
  var keys = Object.keys(this.cells);
  if (keys.length === 0) {
    return { rows: 0, cols: 0, minRow: 0, maxRow: -1, minCol: 0, maxCol: -1 };
  }
  var minR = 1e9;
  var maxR = -1;
  var minC = 1e9;
  var maxC = -1;
  for (var i = 0; i < keys.length; i++) {
    var p = parseCellRef(keys[i]);
    if (p.row < minR) minR = p.row;
    if (p.row > maxR) maxR = p.row;
    if (p.col < minC) minC = p.col;
    if (p.col > maxC) maxC = p.col;
  }
  return {
    rows: maxR - minR + 1,
    cols: maxC - minC + 1,
    minRow: minR,
    maxRow: maxR,
    minCol: minC,
    maxCol: maxC,
  };
};

Sheet.prototype.getRange = function (rangeA1) {
  var parts = String(rangeA1).split(":");
  var a = parseCellRef(parts[0]);
  var b = parts.length > 1 ? parseCellRef(parts[1]) : a;
  var r0 = Math.min(a.row, b.row);
  var r1 = Math.max(a.row, b.row);
  var c0 = Math.min(a.col, b.col);
  var c1 = Math.max(a.col, b.col);
  var rows = [];
  for (var r = r0; r <= r1; r++) {
    var row = [];
    for (var c = c0; c <= c1; c++) {
      var cell = this.get(cellA1(c, r));
      row.push(cell.formula ? "=" + cell.formula : cell.value);
    }
    rows.push(row);
  }
  return rows;
};

Sheet.prototype.setRange = function (startRef, rows) {
  var start = parseCellRef(startRef);
  if (!Array.isArray(rows)) {
    throw new Error("bad argument: setRange: rows array required");
  }
  for (var r = 0; r < rows.length; r++) {
    var row = rows[r];
    if (!Array.isArray(row)) {
      continue;
    }
    for (var c = 0; c < row.length; c++) {
      var v = row[c];
      var a1 = cellA1(start.col + c, start.row + r);
      if (typeof v === "string" && v.charAt(0) === "=") {
        this.setFormula(a1, v);
      } else {
        this.set(a1, v);
      }
    }
  }
  return this;
};

Sheet.prototype.head = function (n) {
  var limit = n == null ? 20 : Math.max(0, Number(n) | 0);
  var dim = this.dimensions();
  if (dim.maxRow < 0) {
    return [];
  }
  var end = Math.min(dim.maxRow, dim.minRow + limit - 1);
  return this.getRange(
    cellA1(dim.minCol, dim.minRow) + ":" + cellA1(dim.maxCol, end)
  );
};

Sheet.prototype.headers = function () {
  var dim = this.dimensions();
  if (dim.maxRow < 0) {
    return [];
  }
  var row = this.getRange(
    cellA1(dim.minCol, dim.minRow) + ":" + cellA1(dim.maxCol, dim.minRow)
  )[0];
  return row || [];
};

Sheet.prototype.summary = function () {
  var dim = this.dimensions();
  var headers = this.headers();
  var sample = this.head(5);
  var keys = Object.keys(this.cells);
  var formulaCount = 0;
  var typeCounts = { string: 0, number: 0, boolean: 0, formula: 0, blank: 0 };
  for (var i = 0; i < keys.length; i++) {
    var c = this.cells[keys[i]];
    var t = c.type || "blank";
    if (t === "formula") {
      formulaCount++;
    }
    typeCounts[t] = (typeCounts[t] || 0) + 1;
  }
  return {
    name: this.name,
    rows: dim.maxRow < 0 ? 0 : dim.maxRow + 1,
    cols: dim.maxCol < 0 ? 0 : dim.maxCol + 1,
    nonEmpty: keys.length,
    formulas: formulaCount,
    headers: headers,
    typeCounts: typeCounts,
    sample: sample,
  };
};

Sheet.prototype.grep = function (needle, options) {
  options = options || {};
  var q = String(needle == null ? "" : needle);
  if (!q) {
    return { matches: [] };
  }
  var caseSensitive = !!options.caseSensitive;
  var hay = caseSensitive ? q : q.toLowerCase();
  var matches = [];
  var keys = Object.keys(this.cells).sort(compareA1);
  for (var i = 0; i < keys.length; i++) {
    var cell = this.get(keys[i]);
    var text =
      (cell.formula ? "=" + cell.formula : "") +
      " " +
      (cell.value == null ? "" : String(cell.value));
    var probe = caseSensitive ? text : text.toLowerCase();
    if (probe.indexOf(hay) >= 0) {
      matches.push({
        ref: cell.ref,
        type: cell.type,
        value: cell.value,
        formula: cell.formula,
      });
    }
  }
  return { matches: matches };
};

Sheet.prototype.textView = function (options) {
  options = options || {};
  var maxRows = options.maxRows == null ? 40 : Number(options.maxRows) | 0;
  var dim = this.dimensions();
  if (dim.maxRow < 0) {
    return "[" + this.name + "] (empty)";
  }
  var end = Math.min(dim.maxRow, dim.minRow + maxRows - 1);
  var lines = ["[" + this.name + "]"];
  for (var r = dim.minRow; r <= end; r++) {
    var parts = [];
    for (var c = dim.minCol; c <= dim.maxCol; c++) {
      var cell = this.get(cellA1(c, r));
      var v = cell.formula
        ? "=" + cell.formula
        : cell.value == null
          ? ""
          : String(cell.value);
      parts.push(v);
    }
    lines.push(String(r + 1) + "\t" + parts.join("\t"));
  }
  if (dim.maxRow > end) {
    lines.push("… +" + (dim.maxRow - end) + " more rows");
  }
  return lines.join("\n");
};

Sheet.prototype.toCsv = function () {
  var dim = this.dimensions();
  if (dim.maxRow < 0) {
    return "";
  }
  var rows = this.getRange(
    cellA1(dim.minCol, dim.minRow) + ":" + cellA1(dim.maxCol, dim.maxRow)
  );
  var lines = [];
  for (var i = 0; i < rows.length; i++) {
    lines.push(
      rows[i]
        .map(function (v) {
          if (v == null) {
            return "";
          }
          var s = String(v);
          if (/[",\n\r]/.test(s)) {
            return '"' + s.replace(/"/g, '""') + '"';
          }
          return s;
        })
        .join(",")
    );
  }
  return lines.join("\n");
};

Sheet.prototype.merge = function (rangeA1) {
  var ref = String(rangeA1).toUpperCase();
  var parts = ref.split(":");
  parseCellRef(parts[0]);
  if (parts.length > 1) {
    parseCellRef(parts[1]);
  }
  if (this.mergeCells.indexOf(ref) < 0) {
    this.mergeCells.push(ref);
  }
  this._mergesDirty = true;
  return this;
};

Sheet.prototype.unmerge = function (rangeA1) {
  var ref = String(rangeA1).toUpperCase();
  var i = this.mergeCells.indexOf(ref);
  if (i >= 0) {
    this.mergeCells.splice(i, 1);
  }
  this._mergesDirty = true;
  return this;
};

Sheet.prototype.getMerges = function () {
  return this.mergeCells.slice();
};

var CHART_TYPES = ["bar", "line", "pie"];

/**
 * 添加原生 Excel 图表（bar / line / pie），save 时写出 chart + drawing parts。
 * series: [{ name?, nameRef?, categories?, values }]
 */
Sheet.prototype.addChart = function (chart) {
  var c = chart || {};
  var type = String(c.type || "bar").toLowerCase();
  if (CHART_TYPES.indexOf(type) < 0) {
    throw new Error("bad argument: addChart: type must be bar | line | pie");
  }
  if (!Array.isArray(c.series) || c.series.length === 0) {
    throw new Error("bad argument: addChart: series array required");
  }
  for (var i = 0; i < c.series.length; i++) {
    var s = c.series[i];
    if (!s || !s.values) {
      throw new Error("bad argument: addChart: series[" + i + "].values range required");
    }
  }
  var anchor = parseCellRef(c.anchor || "E2");
  var to = c.to
    ? parseCellRef(c.to)
    : {
        col: anchor.col + 8,
        row: anchor.row + 15,
      };
  var entry = {
    type: type,
    title: c.title != null ? String(c.title) : "",
    legend: c.legend !== false,
    anchor: anchor,
    to: to,
    series: c.series.map(function (s) {
      return {
        name: s.name != null ? String(s.name) : "",
        nameRef: s.nameRef != null ? String(s.nameRef) : "",
        categories: s.categories != null ? String(s.categories) : "",
        values: String(s.values),
      };
    }),
  };
  entry.source = "add";
  this.charts.push(entry);
  return entry;
};

/** 读回已有图表（weizhi:xlsx 不重建原图表；引用信息供模型理解数据来源）。 */
Sheet.prototype.getCharts = function () {
  return this.charts.map(function (c) {
    return {
      type: c.type,
      title: c.title || "",
      source: c.source || "add",
      series: (c.series || []).map(function (s) {
        return {
          name: s.name || "",
          nameRef: s.nameRef || "",
          categories: s.categories || "",
          values: s.values || "",
        };
      }),
    };
  });
};

/** 读回的透视表结构：fields / rowFields / colFields / pageFields / dataFields。 */
Sheet.prototype.getPivotTables = function () {
  return this.pivotTables.map(function (p) {
    return {
      name: p.name,
      dataCaption: p.dataCaption,
      fields: p.fields.slice(),
      rowFields: p.rowFields.slice(),
      colFields: p.colFields.slice(),
      pageFields: p.pageFields.slice(),
      dataFields: p.dataFields.map(function (d) {
        return { name: d.name, field: d.field, subtotal: d.subtotal };
      }),
    };
  });
};

function mergeSpan(ref) {
  var parts = String(ref).split(":");
  var a = parseCellRef(parts[0]);
  var b = parts.length > 1 ? parseCellRef(parts[1]) : a;
  return {
    c0: Math.min(a.col, b.col),
    c1: Math.max(a.col, b.col),
    r0: Math.min(a.row, b.row),
    r1: Math.max(a.row, b.row),
  };
}

/** 表头行数探测：最早的纵向合并（单列跨多行）起点即数据区第一行；无则 1。 */
function detectHeaderRows(sheet) {
  var best = null;
  for (var i = 0; i < sheet.mergeCells.length; i++) {
    var span = mergeSpan(sheet.mergeCells[i]);
    if (span.c0 === span.c1 && span.r1 > span.r0) {
      if (best === null || span.r0 < best) {
        best = span.r0;
      }
    }
  }
  return best == null ? 1 : best;
}

function headerLabelFor(sheet, col, row) {
  var direct = sheet.cells[cellA1(col, row)];
  if (direct) {
    return direct.value != null ? String(direct.value) : "";
  }
  for (var i = 0; i < sheet.mergeCells.length; i++) {
    var span = mergeSpan(sheet.mergeCells[i]);
    if (col >= span.c0 && col <= span.c1 && row >= span.r0 && row <= span.r1) {
      var anchor = sheet.cells[cellA1(span.c0, span.r0)];
      return anchor && anchor.value != null ? String(anchor.value) : "";
    }
  }
  return null;
}

/**
 * 多级表头合成视图（中国式报表）：合并表头展开成每列的完整标签。
 *
 * 返回 [{ col:"A", label:"上半年 > 一季度 > 销售额", path:[...], dataRef:"A3", headerRows:2 }]。
 * headerRows 缺省按纵向合并自动探测；也可显式传 options.headerRows。
 */
Sheet.prototype.effectiveHeaders = function (options) {
  options = options || {};
  var dim = this.dimensions();
  if (dim.maxCol < 0 || dim.maxRow < 0) {
    return [];
  }
  var headerRows = options.headerRows == null
    ? detectHeaderRows(this)
    : Math.max(1, Number(options.headerRows) | 0);
  if (headerRows > dim.maxRow + 1) {
    headerRows = dim.maxRow + 1;
  }
  var out = [];
  for (var c = dim.minCol; c <= dim.maxCol; c++) {
    var path = [];
    for (var r = dim.minRow; r < dim.minRow + headerRows; r++) {
      var label = headerLabelFor(this, c, r);
      if (label != null && label !== "" && path[path.length - 1] !== label) {
        path.push(label);
      }
    }
    out.push({
      col: colToName(c),
      label: path.join(" > "),
      path: path,
      dataRef: cellA1(c, dim.minRow + headerRows),
      headerRows: headerRows,
    });
  }
  return out;
};

function compareA1(a, b) {
  var pa = parseCellRef(a);
  var pb = parseCellRef(b);
  if (pa.row !== pb.row) {
    return pa.row - pb.row;
  }
  return pa.col - pb.col;
}

function Workbook(options) {
  options = options || {};
  this.title = options.title != null ? String(options.title) : "";
  this.sheets = [];
  this._sourcePath = options._sourcePath || null;
}

Workbook.create = function (options) {
  var wb = new Workbook(options);
  if (!options || !options.empty) {
    wb.addSheet("Sheet1");
  }
  return wb;
};

Workbook.prototype.sheetNames = function () {
  return this.sheets.map(function (s) {
    return s.name;
  });
};

Workbook.prototype.sheet = function (nameOrIndex) {
  if (typeof nameOrIndex === "number") {
    var s = this.sheets[nameOrIndex];
    if (!s) {
      throw new Error("xlsx: sheet index out of range: " + nameOrIndex);
    }
    return s;
  }
  var name = String(nameOrIndex);
  for (var i = 0; i < this.sheets.length; i++) {
    if (this.sheets[i].name === name) {
      return this.sheets[i];
    }
  }
  throw new Error("xlsx: sheet not found: " + name);
};

Workbook.prototype.addSheet = function (name) {
  var n = String(name || "Sheet" + (this.sheets.length + 1));
  for (var i = 0; i < this.sheets.length; i++) {
    if (this.sheets[i].name === n) {
      throw new Error("xlsx: duplicate sheet name: " + n);
    }
  }
  var sh = new Sheet(n);
  this.sheets.push(sh);
  return sh;
};

Workbook.prototype.summary = function () {
  return {
    title: this.title || null,
    sheets: this.sheets.map(function (s) {
      return s.summary();
    }),
  };
};

Workbook.prototype.save = function (outputPath) {
  return renderXlsx(this, outputPath);
};

function buildSharedStrings(wb) {
  var list = [];
  var index = Object.create(null);
  function add(s) {
    var key = String(s);
    if (Object.prototype.hasOwnProperty.call(index, key)) {
      return index[key];
    }
    var i = list.length;
    list.push(key);
    index[key] = i;
    return i;
  }
  for (var si = 0; si < wb.sheets.length; si++) {
    var cells = wb.sheets[si].cells;
    var keys = Object.keys(cells);
    for (var i = 0; i < keys.length; i++) {
      var c = cells[keys[i]];
      if (c.type === "string" || (c.type === "formula" && c.valueType === "string")) {
        if (c.value != null) {
          add(c.value);
        }
      }
    }
  }
  return { list: list, index: index, add: add };
}

function sheetXml(sheet, sst, drawingRelId) {
  var keys = Object.keys(sheet.cells).sort(compareA1);
  var byRow = Object.create(null);
  var maxRow = 0;
  var maxCol = 0;
  for (var i = 0; i < keys.length; i++) {
    var p = parseCellRef(keys[i]);
    if (!byRow[p.row]) {
      byRow[p.row] = [];
    }
    byRow[p.row].push(p);
    if (p.row + 1 > maxRow) maxRow = p.row + 1;
    if (p.col + 1 > maxCol) maxCol = p.col + 1;
  }
  var dim =
    maxRow === 0
      ? "A1"
      : "A1:" + cellA1(Math.max(0, maxCol - 1), Math.max(0, maxRow - 1));
  var parts = [];
  parts.push('<?xml version="1.0" encoding="UTF-8" standalone="yes"?>');
  parts.push(
    '<worksheet xmlns="' +
      SS_NS +
      '" xmlns:r="' +
      PKG_REL +
      '">'
  );
  parts.push("<dimension ref=\"" + dim + "\"/>");
  parts.push("<sheetData>");
  var rowKeys = Object.keys(byRow)
    .map(Number)
    .sort(function (a, b) {
      return a - b;
    });
  for (var ri = 0; ri < rowKeys.length; ri++) {
    var row = rowKeys[ri];
    parts.push('<row r="' + (row + 1) + '">');
    var cols = byRow[row].sort(function (a, b) {
      return a.col - b.col;
    });
    for (var ci = 0; ci < cols.length; ci++) {
      var ref = cols[ci];
      var cell = sheet.cells[ref.a1];
      parts.push(cellXml(ref.a1, cell, sst));
    }
    parts.push("</row>");
  }
  parts.push("</sheetData>");
  if (sheet.mergeCells.length > 0) {
    parts.push('<mergeCells count="' + sheet.mergeCells.length + '">');
    for (var mi = 0; mi < sheet.mergeCells.length; mi++) {
      parts.push('<mergeCell ref="' + sheet.mergeCells[mi] + '"/>');
    }
    parts.push("</mergeCells>");
  }
  if (drawingRelId) {
    parts.push('<drawing r:id="' + drawingRelId + '"/>');
  }
  parts.push("</worksheet>");
  return parts.join("");
}

function cellXml(a1, cell, sst) {
  if (cell.type === "formula") {
    var out = '<c r="' + a1 + '"';
    if (cell.valueType === "string") {
      out += ' t="str"';
    } else if (cell.valueType === "boolean") {
      out += ' t="b"';
    }
    out += "><f>" + xmlEscape(cell.formula) + "</f>";
    if (cell.value != null) {
      if (cell.valueType === "boolean") {
        out += "<v>" + (cell.value ? "1" : "0") + "</v>";
      } else {
        out += "<v>" + xmlEscape(String(cell.value)) + "</v>";
      }
    }
    out += "</c>";
    return out;
  }
  if (cell.type === "number") {
    return '<c r="' + a1 + '"><v>' + xmlEscape(String(cell.value)) + "</v></c>";
  }
  if (cell.type === "boolean") {
    return (
      '<c r="' + a1 + '" t="b"><v>' + (cell.value ? "1" : "0") + "</v></c>"
    );
  }
  if (cell.type === "string") {
    var idx = sst.index[String(cell.value)];
    if (idx == null) {
      idx = sst.add(cell.value);
    }
    return '<c r="' + a1 + '" t="s"><v>' + idx + "</v></c>";
  }
  return '<c r="' + a1 + '"/>';
}

function sharedStringsXml(list) {
  var parts = [];
  parts.push('<?xml version="1.0" encoding="UTF-8" standalone="yes"?>');
  parts.push(
    '<sst xmlns="' +
      SS_NS +
      '" count="' +
      list.length +
      '" uniqueCount="' +
      list.length +
      '">'
  );
  for (var i = 0; i < list.length; i++) {
    parts.push("<si><t>" + xmlEscape(list[i]) + "</t></si>");
  }
  parts.push("</sst>");
  return parts.join("");
}

function stylesXml() {
  return (
    '<?xml version="1.0" encoding="UTF-8" standalone="yes"?>' +
    '<styleSheet xmlns="' +
    SS_NS +
    '">' +
    "<fonts count=\"1\"><font><sz val=\"11\"/><color theme=\"1\"/><name val=\"Calibri\"/><family val=\"2\"/></font></fonts>" +
    "<fills count=\"2\"><fill><patternFill patternType=\"none\"/></fill><fill><patternFill patternType=\"gray125\"/></fill></fills>" +
    "<borders count=\"1\"><border><left/><right/><top/><bottom/><diagonal/></border></borders>" +
    "<cellStyleXfs count=\"1\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\"/></cellStyleXfs>" +
    "<cellXfs count=\"1\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\"/></cellXfs>" +
    "<cellStyles count=\"1\"><cellStyle name=\"Normal\" xfId=\"0\" builtinId=\"0\"/></cellStyles>" +
    "</styleSheet>"
  );
}

function quoteSheetName(name) {
  var n = String(name);
  if (/^[A-Za-z_][A-Za-z0-9_]*$/.test(n)) {
    return n;
  }
  return "'" + n.replace(/'/g, "''") + "'";
}

function absCell(ref) {
  var p = parseCellRef(ref);
  return "$" + colToName(p.col) + "$" + String(p.row + 1);
}

function absRange(range) {
  var parts = String(range).split(":");
  var a = absCell(parts[0]);
  var b = parts.length > 1 ? absCell(parts[1]) : a;
  return a + ":" + b;
}

function refWithSheet(sheetName, range) {
  return quoteSheetName(sheetName) + "!" + absRange(range);
}

/** 展平一个区域，返回 [{ idx, num, text }]；只进缓存，不影响 Excel 重算。 */
function flatRange(sheet, range) {
  var rows = sheet.getRange(range);
  var out = [];
  var idx = 0;
  for (var i = 0; i < rows.length; i++) {
    for (var j = 0; j < rows[i].length; j++) {
      var v = rows[i][j];
      if (v == null || v === "") {
        out.push({ idx: idx, num: null, text: "" });
      } else if (typeof v === "number" && isFinite(v)) {
        out.push({ idx: idx, num: v, text: String(v) });
      } else if (typeof v === "boolean") {
        out.push({ idx: idx, num: null, text: v ? "TRUE" : "FALSE" });
      } else {
        var n = Number(v);
        out.push({
          idx: idx,
          num: isFinite(n) && String(v).trim() !== "" ? n : null,
          text: String(v),
        });
      }
      idx++;
    }
  }
  return out;
}

function strRefCacheXml(ref, pts) {
  var parts = [];
  parts.push("<c:strRef><c:f>" + xmlEscape(ref) + "</c:f>");
  parts.push('<c:strCache><c:ptCount val="' + pts.length + '"/>');
  for (var i = 0; i < pts.length; i++) {
    parts.push(
      '<c:pt idx="' + pts[i].idx + '"><c:v>' + xmlEscape(pts[i].text) + "</c:v></c:pt>"
    );
  }
  parts.push("</c:strCache></c:strRef>");
  return parts.join("");
}

function numRefCacheXml(ref, pts) {
  var parts = [];
  parts.push("<c:numRef><c:f>" + xmlEscape(ref) + "</c:f>");
  parts.push('<c:numCache><c:formatCode>General</c:formatCode>');
  parts.push('<c:ptCount val="' + pts.length + '"/>');
  for (var i = 0; i < pts.length; i++) {
    if (pts[i].num == null) {
      continue;
    }
    parts.push(
      '<c:pt idx="' + pts[i].idx + '"><c:v>' + xmlEscape(String(pts[i].num)) + "</c:v></c:pt>"
    );
  }
  parts.push("</c:numCache></c:numRef>");
  return parts.join("");
}

function chartSerXml(chart, sheet, index) {
  var s = chart.series[index];
  var parts = [];
  parts.push('<c:ser><c:idx val="' + index + '"/><c:order val="' + index + '"/>');
  if (s.nameRef) {
    var namePts = flatRange(sheet, s.nameRef);
    var nameText = namePts.length ? namePts[0].text : s.name;
    parts.push(
      "<c:tx>" + strRefCacheXml(refWithSheet(sheet.name, s.nameRef), [
        { idx: 0, text: nameText || s.name },
      ]) + "</c:tx>"
    );
  } else if (s.name) {
    parts.push("<c:tx><c:v>" + xmlEscape(s.name) + "</c:v></c:tx>");
  }
  if (s.categories) {
    parts.push(
      "<c:cat>" + strRefCacheXml(refWithSheet(sheet.name, s.categories), flatRange(sheet, s.categories)) + "</c:cat>"
    );
  }
  parts.push(
    "<c:val>" + numRefCacheXml(refWithSheet(sheet.name, s.values), flatRange(sheet, s.values)) + "</c:val>"
  );
  if (chart.type === "line") {
    parts.push('<c:smooth val="0"/>');
  }
  parts.push("</c:ser>");
  return parts.join("");
}

var CAT_AX_ID = "111111111";
var VAL_AX_ID = "222222222";

function axesXml() {
  return (
    '<c:catAx><c:axId val="' + CAT_AX_ID + '"/>' +
    '<c:scaling><c:orientation val="minMax"/></c:scaling>' +
    '<c:delete val="0"/><c:axPos val="b"/>' +
    '<c:tickLblPos val="nextTo"/>' +
    '<c:crossAx val="' + VAL_AX_ID + '"/></c:catAx>' +
    '<c:valAx><c:axId val="' + VAL_AX_ID + '"/>' +
    '<c:scaling><c:orientation val="minMax"/></c:scaling>' +
    '<c:delete val="0"/><c:axPos val="l"/>' +
    '<c:tickLblPos val="nextTo"/>' +
    '<c:crossAx val="' + CAT_AX_ID + '"/></c:valAx>'
  );
}

function chartPlotXml(chart, sheet) {
  var parts = [];
  var i;
  if (chart.type === "bar") {
    parts.push('<c:barChart><c:barDir val="col"/><c:grouping val="clustered"/><c:varyColors val="0"/>');
    for (i = 0; i < chart.series.length; i++) {
      parts.push(chartSerXml(chart, sheet, i));
    }
    parts.push('<c:gapWidth val="150"/>');
    parts.push('<c:axId val="' + CAT_AX_ID + '"/><c:axId val="' + VAL_AX_ID + '"/>');
    parts.push("</c:barChart>");
    parts.push(axesXml());
  } else if (chart.type === "line") {
    parts.push('<c:lineChart><c:grouping val="standard"/><c:varyColors val="0"/>');
    for (i = 0; i < chart.series.length; i++) {
      parts.push(chartSerXml(chart, sheet, i));
    }
    parts.push('<c:marker val="1"/>');
    parts.push('<c:axId val="' + CAT_AX_ID + '"/><c:axId val="' + VAL_AX_ID + '"/>');
    parts.push("</c:lineChart>");
    parts.push(axesXml());
  } else {
    parts.push('<c:pieChart><c:varyColors val="1"/>');
    for (i = 0; i < chart.series.length; i++) {
      parts.push(chartSerXml(chart, sheet, i));
    }
    parts.push('<c:firstSliceAng val="0"/></c:pieChart>');
  }
  return parts.join("");
}

function chartXml(chart, sheet) {
  var parts = [];
  parts.push('<?xml version="1.0" encoding="UTF-8" standalone="yes"?>');
  parts.push(
    '<c:chartSpace xmlns:c="http://schemas.openxmlformats.org/drawingml/2006/chart" ' +
      'xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main" ' +
      'xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">'
  );
  parts.push("<c:chart>");
  if (chart.title) {
    parts.push(
      "<c:title><c:tx><c:rich><a:bodyPr/><a:p><a:r><a:t>" +
        xmlEscape(chart.title) +
        "</a:t></a:r></a:p></c:rich></c:tx><c:overlay val=\"0\"/></c:title>"
    );
    parts.push('<c:autoTitleDeleted val="0"/>');
  } else {
    parts.push('<c:autoTitleDeleted val="1"/>');
  }
  parts.push("<c:plotArea><c:layout/>");
  parts.push(chartPlotXml(chart, sheet));
  parts.push("</c:plotArea>");
  if (chart.legend) {
    parts.push('<c:legend><c:legendPos val="r"/></c:legend>');
  }
  parts.push('<c:plotVisOnly val="1"/><c:dispBlanksAs val="gap"/>');
  parts.push("</c:chart></c:chartSpace>");
  return parts.join("");
}

function anchorCellXml(tag, p) {
  return (
    "<xdr:" + tag + "><xdr:col>" + p.col + "</xdr:col><xdr:colOff>0</xdr:colOff>" +
    "<xdr:row>" + p.row + "</xdr:row><xdr:rowOff>0</xdr:rowOff></xdr:" + tag + ">"
  );
}

function drawingXml(sheet) {
  var parts = [];
  parts.push('<?xml version="1.0" encoding="UTF-8" standalone="yes"?>');
  parts.push(
    '<xdr:wsDr xmlns:xdr="http://schemas.openxmlformats.org/drawingml/2006/spreadsheetDrawing" ' +
      'xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main">'
  );
  for (var i = 0; i < sheet.charts.length; i++) {
    var chart = sheet.charts[i];
    parts.push("<xdr:twoCellAnchor>");
    parts.push(anchorCellXml("from", chart.anchor));
    parts.push(anchorCellXml("to", chart.to));
    parts.push('<xdr:graphicFrame macro="">');
    parts.push(
      '<xdr:nvGraphicFramePr><xdr:cNvPr id="' +
        (i + 2) + '" name="Chart ' + (i + 1) + '"/><xdr:cNvGraphicFramePr/></xdr:nvGraphicFramePr>'
    );
    parts.push('<xdr:xfrm><a:off x="0" y="0"/><a:ext cx="0" cy="0"/></xdr:xfrm>');
    parts.push(
      '<a:graphic><a:graphicData uri="http://schemas.openxmlformats.org/drawingml/2006/chart">' +
        '<c:chart xmlns:c="http://schemas.openxmlformats.org/drawingml/2006/chart" ' +
        'xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships" ' +
        'r:id="rId' + (i + 1) + '"/></a:graphicData></a:graphic>'
    );
    parts.push("</xdr:graphicFrame><xdr:clientData/></xdr:twoCellAnchor>");
  }
  parts.push("</xdr:wsDr>");
  return parts.join("");
}

function drawingRelsXml(sheet, chartIds) {
  var parts = [];
  parts.push('<?xml version="1.0" encoding="UTF-8" standalone="yes"?>');
  parts.push('<Relationships xmlns="' + REL_NS + '">');
  for (var i = 0; i < sheet.charts.length; i++) {
    parts.push(
      '<Relationship Id="rId' + (i + 1) + '" ' +
        'Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/chart" ' +
        'Target="../charts/chart' + chartIds[i] + '.xml"/>'
    );
  }
  parts.push("</Relationships>");
  return parts.join("");
}

function sheetRelsXml(drawingId) {
  return (
    '<?xml version="1.0" encoding="UTF-8" standalone="yes"?>' +
    '<Relationships xmlns="' + REL_NS + '">' +
    '<Relationship Id="rId1" ' +
    'Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/drawing" ' +
    'Target="../drawings/drawing' + drawingId + '.xml"/>' +
    "</Relationships>"
  );
}

function writeXlsxTree(buildDir, wb) {
  var sst = buildSharedStrings(wb);
  // 为带图表的 sheet 分配 drawing / chart 编号（chart 全局自增）。
  var drawingIds = [];
  var chartIdOf = [];
  var nextDrawing = 0;
  var nextChart = 0;
  for (var si = 0; si < wb.sheets.length; si++) {
    var sheet = wb.sheets[si];
    if (sheet.charts.length > 0) {
      nextDrawing++;
      drawingIds[si] = nextDrawing;
      chartIdOf[si] = [];
      for (var ci = 0; ci < sheet.charts.length; ci++) {
        nextChart++;
        chartIdOf[si].push(nextChart);
      }
    }
  }
  writeUtf8(
    buildDir + "/[Content_Types].xml",
    contentTypesXml(wb.sheets, drawingIds, chartIdOf)
  );
  writeUtf8(buildDir + "/_rels/.rels", rootRelsXml());
  writeUtf8(buildDir + "/docProps/core.xml", coreXml(wb.title));
  writeUtf8(buildDir + "/docProps/app.xml", appXml(wb.sheets));
  writeUtf8(buildDir + "/xl/workbook.xml", workbookXml(wb.sheets));
  writeUtf8(buildDir + "/xl/_rels/workbook.xml.rels", workbookRelsXml(wb.sheets.length));
  writeUtf8(buildDir + "/xl/styles.xml", stylesXml());
  writeUtf8(buildDir + "/xl/sharedStrings.xml", sharedStringsXml(sst.list));
  for (var i = 0; i < wb.sheets.length; i++) {
    var sh = wb.sheets[i];
    var hasDrawing = drawingIds[i] != null;
    writeUtf8(
      buildDir + "/xl/worksheets/sheet" + (i + 1) + ".xml",
      sheetXml(sh, sst, hasDrawing ? "rId1" : null)
    );
    if (!hasDrawing) {
      continue;
    }
    var drawingId = drawingIds[i];
    writeUtf8(
      buildDir + "/xl/drawings/drawing" + drawingId + ".xml",
      drawingXml(sh)
    );
    writeUtf8(
      buildDir + "/xl/drawings/_rels/drawing" + drawingId + ".xml.rels",
      drawingRelsXml(sh, chartIdOf[i])
    );
    writeUtf8(
      buildDir + "/xl/worksheets/_rels/sheet" + (i + 1) + ".xml.rels",
      sheetRelsXml(drawingId)
    );
    for (var k = 0; k < sh.charts.length; k++) {
      writeUtf8(
        buildDir + "/xl/charts/chart" + chartIdOf[i][k] + ".xml",
        chartXml(sh.charts[k], sh)
      );
    }
  }
}

function contentTypesXml(sheets, drawingIds, chartIdOf) {
  var parts = [];
  parts.push('<?xml version="1.0" encoding="UTF-8" standalone="yes"?>');
  parts.push(
    '<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">'
  );
  parts.push(
    '<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>'
  );
  parts.push('<Default Extension="xml" ContentType="application/xml"/>');
  parts.push(
    '<Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>'
  );
  parts.push(
    '<Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>'
  );
  parts.push(
    '<Override PartName="/xl/sharedStrings.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sharedStrings+xml"/>'
  );
  parts.push(
    '<Override PartName="/docProps/core.xml" ContentType="application/vnd.openxmlformats-package.core-properties+xml"/>'
  );
  parts.push(
    '<Override PartName="/docProps/app.xml" ContentType="application/vnd.openxmlformats-officedocument.extended-properties+xml"/>'
  );
  for (var i = 1; i <= sheets.length; i++) {
    parts.push(
      '<Override PartName="/xl/worksheets/sheet' +
        i +
        '.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>'
    );
  }
  for (var d = 0; d < drawingIds.length; d++) {
    if (drawingIds[d] == null) {
      continue;
    }
    parts.push(
      '<Override PartName="/xl/drawings/drawing' +
        drawingIds[d] +
        '.xml" ContentType="application/vnd.openxmlformats-officedocument.drawing+xml"/>'
    );
    var ids = chartIdOf[d];
    for (var c = 0; c < ids.length; c++) {
      parts.push(
        '<Override PartName="/xl/charts/chart' +
          ids[c] +
          '.xml" ContentType="application/vnd.openxmlformats-officedocument.drawingml.chart+xml"/>'
      );
    }
  }
  parts.push("</Types>");
  return parts.join("");
}

function rootRelsXml() {
  return (
    '<?xml version="1.0" encoding="UTF-8" standalone="yes"?>' +
    '<Relationships xmlns="' +
    REL_NS +
    '">' +
    '<Relationship Id="rId1" Type="' +
    OFF_DOC +
    '" Target="xl/workbook.xml"/>' +
    '<Relationship Id="rId2" Type="http://schemas.openxmlformats.org/package/2006/relationships/metadata/core-properties" Target="docProps/core.xml"/>' +
    '<Relationship Id="rId3" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/extended-properties" Target="docProps/app.xml"/>' +
    "</Relationships>"
  );
}

function workbookRelsXml(sheetCount) {
  var parts = [];
  parts.push('<?xml version="1.0" encoding="UTF-8" standalone="yes"?>');
  parts.push('<Relationships xmlns="' + REL_NS + '">');
  var id = 1;
  for (var i = 1; i <= sheetCount; i++) {
    parts.push(
      '<Relationship Id="rId' +
        id +
        '" Type="' +
        WS_REL +
        '" Target="worksheets/sheet' +
        i +
        '.xml"/>'
    );
    id++;
  }
  parts.push(
    '<Relationship Id="rId' +
      id +
      '" Type="' +
      STY_REL +
      '" Target="styles.xml"/>'
  );
  id++;
  parts.push(
    '<Relationship Id="rId' +
      id +
      '" Type="' +
      SST_REL +
      '" Target="sharedStrings.xml"/>'
  );
  parts.push("</Relationships>");
  return parts.join("");
}

function workbookXml(sheets) {
  var parts = [];
  parts.push('<?xml version="1.0" encoding="UTF-8" standalone="yes"?>');
  parts.push(
    '<workbook xmlns="' +
      SS_NS +
      '" xmlns:r="' +
      PKG_REL +
      '"><sheets>'
  );
  for (var i = 0; i < sheets.length; i++) {
    parts.push(
      '<sheet name="' +
        xmlEscape(sheets[i].name) +
        '" sheetId="' +
        (i + 1) +
        '" r:id="rId' +
        (i + 1) +
        '"/>'
    );
  }
  parts.push("</sheets></workbook>");
  return parts.join("");
}

function coreXml(title) {
  var t = title ? xmlEscape(title) : "Workbook";
  return (
    '<?xml version="1.0" encoding="UTF-8" standalone="yes"?>' +
    '<cp:coreProperties xmlns:cp="http://schemas.openxmlformats.org/package/2006/metadata/core-properties" ' +
    'xmlns:dc="http://purl.org/dc/elements/1.1/" xmlns:dcterms="http://purl.org/dc/terms/" ' +
    'xmlns:dcmitype="http://purl.org/dc/dcmitype/" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">' +
    "<dc:title>" +
    t +
    "</dc:title><dc:creator>agent1</dc:creator></cp:coreProperties>"
  );
}

function appXml(sheets) {
  var titles = sheets
    .map(function (s) {
      return "<vt:lpstr>" + xmlEscape(s.name) + "</vt:lpstr>";
    })
    .join("");
  return (
    '<?xml version="1.0" encoding="UTF-8" standalone="yes"?>' +
    '<Properties xmlns="http://schemas.openxmlformats.org/officeDocument/2006/extended-properties" ' +
    'xmlns:vt="http://schemas.openxmlformats.org/officeDocument/2006/docPropsVTypes">' +
    "<Application>agent1</Application><HeadingPairs><vt:vector size=\"2\" baseType=\"variant\">" +
    "<vt:variant><vt:lpstr>Worksheets</vt:lpstr></vt:variant>" +
    "<vt:variant><vt:i4>" +
    sheets.length +
    "</vt:i4></vt:variant></vt:vector></HeadingPairs>" +
    "<TitlesOfParts><vt:vector size=\"" +
    sheets.length +
    "\" baseType=\"lpstr\">" +
    titles +
    "</vt:vector></TitlesOfParts></Properties>"
  );
}

function attr(xml, name) {
  var re = new RegExp(name + '="([^"]*)"');
  var m = xml.match(re);
  return m ? m[1] : "";
}

function parseSharedStrings(xml) {
  var list = [];
  if (!xml) {
    return list;
  }
  var siRe = /<si\b[^>]*>([\s\S]*?)<\/si>/g;
  var m;
  while ((m = siRe.exec(xml)) != null) {
    var inner = m[1];
    var texts = [];
    var tRe = /<t(?:\s[^>]*)?>([\s\S]*?)<\/t>/g;
    var tm;
    while ((tm = tRe.exec(inner)) != null) {
      texts.push(decodeXml(tm[1]));
    }
    list.push(texts.join(""));
  }
  return list;
}

function decodeXml(s) {
  return String(s)
    .replace(/&lt;/g, "<")
    .replace(/&gt;/g, ">")
    .replace(/&quot;/g, '"')
    .replace(/&apos;/g, "'")
    .replace(/&amp;/g, "&");
}

/* ------------------------------------------------------------------ *
 * 日期/时间识别（1.4.0）：xlsx 把日期存为数字序列值 + 样式表的数字格式。
 * 读取时按 style → numFmt 判定，把 45000.5 还原成 "2026-10-27 12:00:00"。
 * ------------------------------------------------------------------ */

var BUILTIN_DATE_FMT_IDS = (function () {
  var set = {};
  var ranges = [
    [14, 22],
    [27, 36],
    [45, 47],
    [50, 58],
  ];
  for (var r = 0; r < ranges.length; r++) {
    for (var i = ranges[r][0]; i <= ranges[r][1]; i++) {
      set[i] = true;
    }
  }
  return set;
})();

/** 自定义 formatCode 是否像日期/时间格式（排除引号文本、颜色段、转义后仍含 dmyhs 指示符）。 */
function formatCodeLooksLikeDate(code) {
  var s = String(code);
  s = s.replace(/\[[^\]]*\]/g, ""); // [Red]、[h] 等保留段名先去掉方括号内容里的颜色
  s = s.replace(/"[^"]*"/g, ""); // 字面量文本
  s = s.replace(/\\./g, ""); // 转义字符
  s = s.replace(/AM\/PM|A\/P/gi, "");
  return /[dmyhs]/i.test(s);
}

/** 解析 styles.xml → 每个 cellXf 索引是否日期格式（只认 cellXfs 段，排除 cellStyleXfs）。 */
function parseStyleDateFlags(stylesXml) {
  var flags = [];
  if (!stylesXml) {
    return flags;
  }
  var custom = {};
  var fmtRe = /<(?:\w+:)?numFmt\b[^>]*\/>/g;
  var m;
  while ((m = fmtRe.exec(stylesXml)) != null) {
    var id = parseInt(attr(m[0], "numFmtId"), 10);
    var code = attr(m[0], "formatCode");
    if (!isFinite(id) || !code) {
      continue;
    }
    custom[id] = formatCodeLooksLikeDate(code);
  }
  var xfsMatch = stylesXml.match(
    /<(?:\w+:)?cellXfs\b[^>]*>([\s\S]*?)<\/(?:\w+:)?cellXfs>/
  );
  if (!xfsMatch) {
    return flags;
  }
  var xfRe = /<(?:\w+:)?xf\b[^>]*\/>/g;
  var idx = 0;
  while ((m = xfRe.exec(xfsMatch[1])) != null) {
    var numFmtId = parseInt(attr(m[0], "numFmtId"), 10);
    var isDate =
      custom[numFmtId] != null ? custom[numFmtId] : !!BUILTIN_DATE_FMT_IDS[numFmtId];
    flags[idx] = isDate;
    idx++;
  }
  return flags;
}

/** Excel 日期序列值 → 可读字符串（UTC 分量，避免本地时区偏移）。 */
function serialToDateValue(serial) {
  var n = Number(serial);
  if (!isFinite(n)) {
    return null;
  }
  // Excel 1900 闰年 bug：序列 < 61 时比真实日历多一天。
  var fixed = n < 61 ? n + 1 : n;
  var ms = Math.round((fixed - 25569) * 86400000);
  if (ms < -62135596800000) {
    return null;
  }
  var d = new Date(ms);
  if (isNaN(d.getTime())) {
    return null;
  }
  function p2(x) {
    return x < 10 ? "0" + x : String(x);
  }
  var y = d.getUTCFullYear();
  var mo = d.getUTCMonth() + 1;
  var day = d.getUTCDate();
  var h = d.getUTCHours();
  var mi = d.getUTCMinutes();
  var s = d.getUTCSeconds();
  var datePart = y + "-" + p2(mo) + "-" + p2(day);
  var timePart = p2(h) + ":" + p2(mi) + ":" + p2(s);
  if (n >= 1) {
    if (h === 0 && mi === 0 && s === 0) {
      return datePart;
    }
    return datePart + " " + timePart;
  }
  return timePart;
}

function parseSheetCells(xml, shared, dateFlags, datesEnabled) {
  var cells = Object.create(null);
  var cRe = /<c\b([^>]*)>([\s\S]*?)<\/c>|<c\b([^>]*)\/>/g;
  var m;
  while ((m = cRe.exec(xml)) != null) {
    var attrs = m[1] != null ? m[1] : m[3];
    var body = m[2] != null ? m[2] : "";
    var r = attr(attrs, "r");
    if (!r) {
      continue;
    }
    var a1 = parseCellRef(r).a1;
    var t = attr(attrs, "t") || "";
    var sIdx = parseInt(attr(attrs, "s"), 10);
    var isDateCell =
      datesEnabled &&
      dateFlags &&
      isFinite(sIdx) &&
      dateFlags[sIdx] === true;
    var fMatch = body.match(/<f\b[^>]*>([\s\S]*?)<\/f>/);
    var vMatch = body.match(/<v\b[^>]*>([\s\S]*?)<\/v>/);
    var vRaw = vMatch ? decodeXml(vMatch[1]) : null;
    if (fMatch) {
      var cell = { type: "formula", formula: decodeXml(fMatch[1]) };
      if (vRaw != null) {
        if (t === "b") {
          cell.value = vRaw === "1" || vRaw === "true";
          cell.valueType = "boolean";
        } else if (t === "str" || t === "inlineStr") {
          cell.value = vRaw;
          cell.valueType = "string";
        } else if (t === "s") {
          var si = parseInt(vRaw, 10);
          cell.value = shared[si] != null ? shared[si] : vRaw;
          cell.valueType = "string";
        } else if (vRaw !== "" && !isNaN(Number(vRaw))) {
          if (isDateCell) {
            var dv = serialToDateValue(vRaw);
            if (dv != null) {
              cell.value = dv;
              cell.valueType = "date";
            } else {
              cell.value = Number(vRaw);
              cell.valueType = "number";
            }
          } else {
            cell.value = Number(vRaw);
            cell.valueType = "number";
          }
        } else if (vRaw != null) {
          cell.value = vRaw;
          cell.valueType = "string";
        }
      }
      cells[a1] = cell;
      continue;
    }
    if (t === "s") {
      var idx = parseInt(vRaw || "0", 10);
      cells[a1] = {
        type: "string",
        value: shared[idx] != null ? shared[idx] : "",
      };
    } else if (t === "b") {
      cells[a1] = { type: "boolean", value: vRaw === "1" || vRaw === "true" };
    } else if (t === "inlineStr") {
      var isMatch = body.match(/<t(?:\s[^>]*)?>([\s\S]*?)<\/t>/);
      cells[a1] = {
        type: "string",
        value: isMatch ? decodeXml(isMatch[1]) : "",
      };
    } else if (t === "str") {
      cells[a1] = { type: "string", value: vRaw != null ? vRaw : "" };
    } else if (vRaw != null && vRaw !== "" && !isNaN(Number(vRaw))) {
      if (isDateCell) {
        var dval = serialToDateValue(vRaw);
        if (dval != null) {
          cells[a1] = { type: "date", value: dval };
        } else {
          cells[a1] = { type: "number", value: Number(vRaw) };
        }
      } else {
        cells[a1] = { type: "number", value: Number(vRaw) };
      }
    } else if (vRaw != null && vRaw !== "") {
      cells[a1] = { type: "string", value: vRaw };
    }
  }
  return cells;
}

function parseWorkbookSheetNames(xml) {
  var names = [];
  var re = /<sheet\b[^>]*>/g;
  var m;
  while ((m = re.exec(xml)) != null) {
    var name = attr(m[0], "name") || "Sheet" + (names.length + 1);
    names.push(decodeXml(name));
  }
  return names.length ? names : ["Sheet1"];
}

function parseWorkbookSheetEntries(xml) {
  var out = [];
  var re = /<sheet\b[^>]*>/g;
  var m;
  while ((m = re.exec(xml)) != null) {
    var name = attr(m[0], "name") || "Sheet" + (out.length + 1);
    out.push({ name: decodeXml(name), rid: attr(m[0], "r:id") });
  }
  return out.length ? out : [{ name: "Sheet1", rid: "" }];
}

function parseWorkbookRels(relsXml) {
  var map = {};
  if (!relsXml) {
    return map;
  }
  var re = /<Relationship\b[^>]*>/g;
  var m;
  while ((m = re.exec(relsXml)) != null) {
    var id = attr(m[0], "Id");
    var target = attr(m[0], "Target");
    var type = attr(m[0], "Type") || "";
    if (id && target && type.indexOf("/worksheet") >= 0) {
      var norm = target.replace(/^\/+/, "");
      if (norm.indexOf("xl/") === 0) {
        norm = norm.slice(3);
      }
      map[id] = norm;
    }
  }
  return map;
}

/** 保真改写：记录原始 sheetData 的行结构（open 标签 + 每个 cell 的原 XML）。 */
function parseOrigRows(sheetXmlText) {
  var rows = [];
  if (!sheetXmlText || sheetXmlText.indexOf("<sheetData") < 0) {
    return rows;
  }
  var bodyMatch = sheetXmlText.match(/<sheetData>([\s\S]*?)<\/sheetData>/);
  var body = bodyMatch ? bodyMatch[1] : "";
  var rowRe = /<row\b[^>]*\/>|<row\b[^>]*>[\s\S]*?<\/row>/g;
  var m;
  while ((m = rowRe.exec(body)) != null) {
    var rowXml = m[0];
    var open = rowXml.match(/<row\b[^>]*>/)[0];
    var rowRef = attr(open, "r");
    var rowNum = rowRef ? parseInt(rowRef, 10) : 0;
    var selfClose = /\/>$/.test(rowXml.trim());
    var cells = {};
    if (!selfClose) {
      var cellRe = /<c\b[^>]*>[\s\S]*?<\/c>|<c\b[^>]*\/>/g;
      var cm;
      while ((cm = cellRe.exec(rowXml)) != null) {
        var ref = attr(cm[0], "r");
        if (ref) {
          cells[ref.toUpperCase()] = cm[0];
        }
      }
    }
    rows.push({ row: rowNum, open: open, cells: cells });
  }
  return rows;
}

/** patch 模式下生成新 cell XML：字符串走 inlineStr（不动 sharedStrings），继承原样式索引 s。 */
function cellPatchXml(a1, cell, origCellXml) {
  var sAttr = "";
  if (origCellXml) {
    var m = origCellXml.match(/\bs="(\d+)"/);
    if (m) {
      sAttr = ' s="' + m[1] + '"';
    }
  }
  if (cell.type === "formula") {
    var out = '<c r="' + a1 + '"' + sAttr;
    if (cell.valueType === "string") {
      out += ' t="str"';
    } else if (cell.valueType === "boolean") {
      out += ' t="b"';
    }
    out += "><f>" + xmlEscape(cell.formula) + "</f>";
    if (cell.value != null) {
      if (cell.valueType === "boolean") {
        out += "<v>" + (cell.value ? "1" : "0") + "</v>";
      } else {
        out += "<v>" + xmlEscape(String(cell.value)) + "</v>";
      }
    }
    return out + "</c>";
  }
  if (cell.type === "number") {
    return '<c r="' + a1 + '"' + sAttr + "><v>" + xmlEscape(String(cell.value)) + "</v></c>";
  }
  if (cell.type === "boolean") {
    return '<c r="' + a1 + '"' + sAttr + ' t="b"><v>' + (cell.value ? "1" : "0") + "</v></c>";
  }
  if (cell.type === "string") {
    return (
      '<c r="' + a1 + '"' + sAttr + ' t="inlineStr"><is><t>' +
      xmlEscape(cell.value) + "</t></is></c>"
    );
  }
  return '<c r="' + a1 + '"' + sAttr + "/>";
}

function refRowCol(a1) {
  var p = parseCellRef(a1);
  return p;
}

/**
 * 生成替换后的 sheetData 内容：未编辑的原始 cell 原样保留（含样式 s、公式缓存等），
 * 编辑/新增的 cell 用模型重写；行属性保留原始（ht/spans/s 等）。
 */
function buildPatchedSheetData(sheet) {
  var dirty = sheet._dirtyRefs || {};
  var origRows = sheet._origRows || [];
  var origByRow = Object.create(null);
  for (var i = 0; i < origRows.length; i++) {
    origByRow[origRows[i].row] = origRows[i];
  }
  var modelByRow = Object.create(null);
  var keys = Object.keys(sheet.cells);
  for (var k = 0; k < keys.length; k++) {
    var rc = refRowCol(keys[k]);
    if (!modelByRow[rc.row]) {
      modelByRow[rc.row] = [];
    }
    modelByRow[rc.row].push({ a1: keys[k], col: rc.col, cell: sheet.cells[keys[k]] });
  }
  var rowNumbers = Object.keys(origByRow);
  for (var mr in modelByRow) {
    if (rowNumbers.indexOf(mr) < 0) {
      rowNumbers.push(mr);
    }
  }
  rowNumbers = rowNumbers.map(Number).sort(function (a, b) { return a - b; });

  var parts = [];
  for (var r = 0; r < rowNumbers.length; r++) {
    var rowNum = rowNumbers[r];
    var orig = origByRow[rowNum];
    var open = orig ? orig.open : '<row r="' + rowNum + '">';
    var selfClose = /\/>$/.test(open);
    var modelCells = modelByRow[rowNum] || [];
    if (selfClose && modelCells.length === 0) {
      parts.push(open);
      continue;
    }
    if (selfClose) {
      open = open.replace(/\/>$/, ">") + "</row>";
    }
    var merged = [];
    if (orig) {
      var origRefs = Object.keys(orig.cells);
      for (var oc = 0; oc < origRefs.length; oc++) {
        var ref = origRefs[oc];
        if (dirty[ref]) {
          continue;
        }
        merged.push({ col: refRowCol(ref).col, xml: orig.cells[ref] });
      }
    }
    for (var mc = 0; mc < modelCells.length; mc++) {
      var entry = modelCells[mc];
      if (!dirty[entry.a1]) {
        continue;
      }
      merged.push({
        col: entry.col,
        xml: cellPatchXml(entry.a1, entry.cell, orig ? orig.cells[entry.a1] : null),
      });
    }
    if (merged.length === 0 && orig && !/\/>$/.test(orig.open)) {
      parts.push('<row r="' + rowNum + '">');
      parts.push("</row>");
      continue;
    }
    if (merged.length === 0 && !orig) {
      continue;
    }
    merged.sort(function (a, b) { return a.col - b.col; });
    var openNoClose = open.replace(/<\/row>$/, "");
    parts.push(openNoClose);
    for (var x = 0; x < merged.length; x++) {
      parts.push(merged[x].xml);
    }
    parts.push("</row>");
  }
  return parts.join("");
}

function replaceSheetDataSpan(xml, newSheetData) {
  var re = /<sheetData(?:\/>|>[\s\S]*?<\/sheetData>)/;
  if (!re.test(xml)) {
    throw new Error("xlsx: sheetData span not found (unsupported sheet xml)");
  }
  if (newSheetData === "") {
    return xml.replace(re, "<sheetData/>");
  }
  return xml.replace(re, "<sheetData>" + newSheetData + "</sheetData>");
}

function replaceMergeCellsSpan(xml, merges) {
  var re = /<mergeCells\b[^>]*(?:\/>|>[\s\S]*?<\/mergeCells>)/;
  if (merges.length === 0) {
    return xml.replace(re, "");
  }
  var block = '<mergeCells count="' + merges.length + '">';
  for (var i = 0; i < merges.length; i++) {
    block += '<mergeCell ref="' + merges[i] + '"/>';
  }
  block += "</mergeCells>";
  if (re.test(xml)) {
    return xml.replace(re, block);
  }
  // 原文件没有 mergeCells：插到 sheetData 之后
  var sd = /(<sheetData(?:\/>|>[\s\S]*?<\/sheetData>))/;
  return xml.replace(sd, "$1" + block);
}

function updateDimensionSpan(xml, sheet) {
  var refs = Object.keys(sheet.cells);
  var origRows = sheet._origRows || [];
  for (var i = 0; i < origRows.length; i++) {
    var origRefs = Object.keys(origRows[i].cells);
    for (var j = 0; j < origRefs.length; j++) {
      if (refs.indexOf(origRefs[j]) < 0) {
        refs.push(origRefs[j]);
      }
    }
  }
  if (refs.length === 0) {
    return xml;
  }
  var minR = 1e9, maxR = -1, minC = 1e9, maxC = -1;
  for (var k = 0; k < refs.length; k++) {
    var p = refRowCol(refs[k]);
    if (p.row < minR) minR = p.row;
    if (p.row > maxR) maxR = p.row;
    if (p.col < minC) minC = p.col;
    if (p.col > maxC) maxC = p.col;
  }
  var dim = "A1:" + cellA1(maxC, maxR);
  var re = /<dimension ref="[^"]*"\/>/;
  if (re.test(xml)) {
    return xml.replace(re, '<dimension ref="' + dim + '"/>');
  }
  return xml.replace(/<worksheet\b[^>]*>/, function (m) {
    return m + '<dimension ref="' + dim + '"/>';
  });
}

/**
 * 保真保存（1.2.0）：对 readXlsx 打开的工作簿，只替换被编辑 sheet 的
 * sheetData / mergeCells / dimension，其余 parts（样式、列宽、原图表、
 * 打印设置等）与未编辑单元格原 XML（含样式索引 s、公式缓存）逐字节保留。
 */
function patchSave(wb, outputPath) {
  var dir = wb._sourceDir;
  for (var i = 0; i < wb.sheets.length; i++) {
    var sheet = wb.sheets[i];
    if (sheet.charts.some(function (c) { return c.source === "add"; })) {
      throw new Error(
        "xlsx: addChart on a workbook loaded by readXlsx is not supported yet; " +
          "build a new workbook (buildXlsx) or edit via weizhi:xlsx-raw. " +
          "Existing charts are preserved and readable via getCharts()."
      );
    }
    if (!sheet._origSheetPart) {
      throw new Error(
        "xlsx: adding/removing/renaming sheets on a loaded workbook is not supported yet; " +
          "sheets are: " + (wb._origSheetNames || []).join(", ")
      );
    }
    if (!sheet._isDirty()) {
      continue;
    }
    var xmlPath = dir + "/" + sheet._origSheetPart;
    var xml = readUtf8(xmlPath);
    xml = replaceSheetDataSpan(xml, buildPatchedSheetData(sheet));
    xml = replaceMergeCellsSpan(xml, sheet.mergeCells);
    xml = updateDimensionSpan(xml, sheet);
    writeUtf8(xmlPath, xml);
    // 保存后原始结构以新状态为准，允许继续编辑后再 save。
    sheet._origRows = parseOrigRows(xml);
    sheet._dirtyRefs = null;
    sheet._mergesDirty = false;
  }
  packDir(dir, outputPath);
  var bytes = fs.readFileSync(outputPath).length;
  return { ok: true, path: outputPath, bytes: bytes };
}

export function renderXlsx(wb, outputPath) {
  if (!outputPath) {
    throw new Error("bad argument: renderXlsx: outputPath required");
  }
  if (wb && wb._sourceDir) {
    return patchSave(wb, outputPath);
  }
  if (!wb || !wb.sheets || wb.sheets.length === 0) {
    throw new Error("bad argument: renderXlsx: workbook has no sheets");
  }
  var buildDir = "tmp/xlsx-render-" + Date.now();
  writeXlsxTree(buildDir, wb);
  packDir(buildDir, outputPath);
  var bytes = fs.readFileSync(outputPath).length;
  return { ok: true, path: outputPath, bytes: bytes };
}

function parseMergeCells(xml) {
  var merges = [];
  if (!xml) {
    return merges;
  }
  var re = /<mergeCell ref="([^"]+)"\s*\/>/g;
  var m;
  while ((m = re.exec(xml)) != null) {
    merges.push(m[1].toUpperCase());
  }
  return merges;
}

function relsPathFor(part) {
  var slash = part.lastIndexOf("/");
  var dir = slash >= 0 ? part.slice(0, slash) : "";
  var name = slash >= 0 ? part.slice(slash + 1) : part;
  return (dir ? dir + "/" : "") + "_rels/" + name + ".rels";
}

function joinRel(baseDir, target) {
  var t = String(target).replace(/^\/+/, "");
  var parts = (baseDir ? baseDir + "/" : "") + t;
  var segs = parts.split("/");
  var out = [];
  for (var i = 0; i < segs.length; i++) {
    if (!segs[i] || segs[i] === ".") {
      continue;
    }
    if (segs[i] === "..") {
      out.pop();
      continue;
    }
    out.push(segs[i]);
  }
  return out.join("/");
}

/** part 的 rels 里找 type 含 suffix 的 Target，规范化为相对 workDir 路径；找不到返回 null。 */
function findRelTarget(workDir, part, suffix) {
  var relsXml = "";
  try {
    relsXml = readUtf8(workDir + "/" + relsPathFor(part));
  } catch (e) {
    return null;
  }
  var baseDir = part.indexOf("/") >= 0 ? part.replace(/\/[^/]+$/, "") : "";
  var re = /<(?:\w+:)?Relationship\b[^>]*>/g;
  var m;
  while ((m = re.exec(relsXml)) != null) {
    var type = attr(m[0], "Type") || "";
    if (type.indexOf(suffix) < 0) {
      continue;
    }
    var target = attr(m[0], "Target");
    if (target) {
      if (target.charAt(0) === "/") {
        return target.replace(/^\/+/, "");
      }
      return joinRel(baseDir, target);
    }
  }
  return null;
}

function extractF(block) {
  var m = block.match(/<(?:\w+:)?f>([\s\S]*?)<\/(?:\w+:)?f>/);
  return m ? decodeXml(m[1]) : "";
}

function innerBlock(xml, localName) {
  var m = xml.match(
    new RegExp("<(?:\\w+:)?" + localName + "\\b[^>]*>([\\s\\S]*?)</(?:\\w+:)?" + localName + ">")
  );
  return m ? m[1] : null;
}

function textOf(xml, localName) {
  var block = innerBlock(xml, localName);
  if (block == null) {
    return "";
  }
  var texts = [];
  var re = /<(?:\w+:)?t(?:\s[^>]*)?>([\s\S]*?)<\/(?:\w+:)?t>/g;
  var m;
  while ((m = re.exec(block)) != null) {
    texts.push(decodeXml(m[1]));
  }
  return texts.join("");
}

/** 解析一个 chartN.xml → {type, title, series[], source:"read"}；非 bar/line/pie 返回 null。 */
function parseChartXml(chartXml) {
  var typeMatch = chartXml.match(/<(?:\w+:)?(barChart|lineChart|pieChart)\b/);
  if (!typeMatch) {
    return null;
  }
  var type = typeMatch[1]
    .replace("Chart", "")
    .replace("bar", "bar")
    .replace("line", "line")
    .replace("pie", "pie");
  var entry = {
    type: type,
    title: textOf(chartXml, "title"),
    source: "read",
    series: [],
  };
  var serRe = /<(?:\w+:)?ser\b[\s\S]*?<\/(?:\w+:)?ser>/g;
  var m;
  while ((m = serRe.exec(chartXml)) != null) {
    var serXml = m[0];
    var s = { name: "", nameRef: "", categories: "", values: "" };
    var tx = innerBlock(serXml, "tx");
    if (tx != null) {
      var strRef = innerBlock(tx, "strRef");
      if (strRef != null) {
        s.nameRef = extractF(strRef);
      } else {
        var v = tx.match(/<(?:\w+:)?v>([\s\S]*?)<\/(?:\w+:)?v>/);
        s.name = v ? decodeXml(v[1]) : "";
      }
    }
    var cat = innerBlock(serXml, "cat");
    if (cat != null) {
      var catF = extractF(cat);
      if (catF) {
        s.categories = catF;
      }
    }
    var val = innerBlock(serXml, "val");
    if (val != null) {
      s.values = extractF(val);
    }
    entry.series.push(s);
  }
  return entry;
}

/** 读回某个 sheet 的原生图表：sheet rels → drawing → drawing rels → chartN.xml。 */
function parseSheetCharts(workDir, part) {
  var charts = [];
  var drawingPart = findRelTarget(workDir, part, "/drawing");
  if (!drawingPart) {
    return charts;
  }
  var drawingXml = "";
  try {
    drawingXml = readUtf8(workDir + "/" + drawingPart);
  } catch (e) {
    return charts;
  }
  var chartRe = /<(?:\w+:)?chart\b[^>]*\br:id="([^"]+)"/g;
  var m;
  while ((m = chartRe.exec(drawingXml)) != null) {
    var rid = m[1];
    var chartPart = chartTargetById(workDir, drawingPart, rid);
    if (!chartPart) {
      continue;
    }
    var chartXml = "";
    try {
      chartXml = readUtf8(workDir + "/" + chartPart);
    } catch (e2) {
      continue;
    }
    var entry = parseChartXml(chartXml);
    if (entry) {
      charts.push(entry);
    }
  }
  return charts;
}

function chartTargetById(workDir, drawingPart, rid) {
  var relsXml = "";
  try {
    relsXml = readUtf8(workDir + "/" + relsPathFor(drawingPart));
  } catch (e) {
    return null;
  }
  var baseDir = drawingPart.indexOf("/") >= 0 ? drawingPart.replace(/\/[^/]+$/, "") : "";
  var re = /<(?:\w+:)?Relationship\b[^>]*>/g;
  var m;
  while ((m = re.exec(relsXml)) != null) {
    if ((attr(m[0], "Id") || "") !== rid) {
      continue;
    }
    if ((attr(m[0], "Type") || "").indexOf("/chart") < 0) {
      continue;
    }
    var target = attr(m[0], "Target");
    if (!target) {
      return null;
    }
    if (target.charAt(0) === "/") {
      return target.replace(/^\/+/, "");
    }
    return joinRel(baseDir, target);
  }
  return null;
}

function fieldNamesFromRefs(blockXml, cacheNames) {
  var names = [];
  if (!blockXml) {
    return names;
  }
  var re = /<(?:\w+:)?field\b[^>]*\bx="(-?\d+)"[^>]*\/>/g;
  var m;
  while ((m = re.exec(blockXml)) != null) {
    var x = parseInt(m[1], 10);
    if (x >= 2147483647 || x < 0) {
      names.push("<值>");
    } else {
      names.push(cacheNames[x] != null ? cacheNames[x] : "field#" + x);
    }
  }
  return names;
}

function pageFieldNamesFromRefs(blockXml, cacheNames) {
  var names = [];
  if (!blockXml) {
    return names;
  }
  var re = /<(?:\w+:)?pageField\b[^>]*\bfld="(-?\d+)"[^>]*\/>/g;
  var m;
  while ((m = re.exec(blockXml)) != null) {
    var f = parseInt(m[1], 10);
    if (f >= 0 && f < cacheNames.length) {
      names.push(cacheNames[f]);
    } else {
      names.push("field#" + f);
    }
  }
  return names;
}

function cacheFieldNames(cacheXml) {
  var names = [];
  var re = /<(?:\w+:)?cacheField\b[^>]*>/g;
  var m;
  while ((m = re.exec(cacheXml)) != null) {
    names.push(attr(m[0], "name") || "field#" + names.length);
  }
  return names;
}

function dataFieldsFromXml(blockXml, cacheNames) {
  var out = [];
  if (!blockXml) {
    return out;
  }
  var re = /<(?:\w+:)?dataField\b[^>]*\/>/g;
  var m;
  while ((m = re.exec(blockXml)) != null) {
    var fld = parseInt(attr(m[0], "fld"), 10);
    out.push({
      name: attr(m[0], "name") || "",
      field: cacheNames[fld] != null ? cacheNames[fld] : "field#" + fld,
      subtotal: attr(m[0], "subtotal") || "sum",
    });
  }
  return out;
}

/** 解析一个 pivotTableN.xml（+ 对应 cacheDefinition）→ 结构摘要。 */
function parsePivotTableXml(workDir, pivotPart) {
  var xml = "";
  try {
    xml = readUtf8(workDir + "/" + pivotPart);
  } catch (e) {
    return null;
  }
  var defMatch = xml.match(/<(?:\w+:)?pivotTableDefinition\b[^>]*>/);
  if (!defMatch) {
    return null;
  }
  var cachePart = chartTargetByIdType(workDir, pivotPart, "/pivotCacheDefinition");
  var cacheNames = [];
  if (cachePart) {
    try {
      cacheNames = cacheFieldNames(readUtf8(workDir + "/" + cachePart));
    } catch (e2) {
      cacheNames = [];
    }
  }
  return {
    name: attr(defMatch[0], "name") || "",
    dataCaption: attr(defMatch[0], "dataCaption") || "",
    fields: cacheNames.slice(),
    rowFields: fieldNamesFromRefs(innerBlock(xml, "rowFields"), cacheNames),
    colFields: fieldNamesFromRefs(innerBlock(xml, "colFields"), cacheNames),
    pageFields: pageFieldNamesFromRefs(innerBlock(xml, "pageFields"), cacheNames),
    dataFields: dataFieldsFromXml(innerBlock(xml, "dataFields"), cacheNames),
  };
}

function chartTargetByIdType(workDir, ownerPart, typeSuffix) {
  var relsXml = "";
  try {
    relsXml = readUtf8(workDir + "/" + relsPathFor(ownerPart));
  } catch (e) {
    return null;
  }
  var baseDir = ownerPart.indexOf("/") >= 0 ? ownerPart.replace(/\/[^/]+$/, "") : "";
  var re = /<(?:\w+:)?Relationship\b[^>]*>/g;
  var m;
  while ((m = re.exec(relsXml)) != null) {
    if ((attr(m[0], "Type") || "").indexOf(typeSuffix) < 0) {
      continue;
    }
    var target = attr(m[0], "Target");
    if (!target) {
      return null;
    }
    if (target.charAt(0) === "/") {
      return target.replace(/^\/+/, "");
    }
    return joinRel(baseDir, target);
  }
  return null;
}

/** 读回某个 sheet 承载的透视表结构（数据值本身在普通单元格里已可读）。 */
function parseSheetPivotTables(workDir, part) {
  var out = [];
  var relsXml = "";
  try {
    relsXml = readUtf8(workDir + "/" + relsPathFor(part));
  } catch (e) {
    return out;
  }
  var baseDir = part.indexOf("/") >= 0 ? part.replace(/\/[^/]+$/, "") : "";
  var re = /<(?:\w+:)?Relationship\b[^>]*>/g;
  var m;
  while ((m = re.exec(relsXml)) != null) {
    if ((attr(m[0], "Type") || "").indexOf("/pivotTable") < 0) {
      continue;
    }
    var target = attr(m[0], "Target");
    if (!target) {
      continue;
    }
    var pivotPart = target.charAt(0) === "/"
      ? target.replace(/^\/+/, "")
      : joinRel(baseDir, target);
    var entry = parsePivotTableXml(workDir, pivotPart);
    if (entry) {
      out.push(entry);
    }
  }
  return out;
}

export function readXlsx(inputPath, options) {
  options = options || {};
  if (!inputPath) {
    throw new Error("bad argument: readXlsx: inputPath required");
  }
  var workDir = options.workDir || "tmp/xlsx-read-" + Date.now();
  unpack(inputPath, workDir);
  var wbXml = readUtf8(workDir + "/xl/workbook.xml");
  var relsXml = "";
  try {
    relsXml = readUtf8(workDir + "/xl/_rels/workbook.xml.rels");
  } catch (eRels) {
    relsXml = "";
  }
  var relMap = parseWorkbookRels(relsXml);
  var entries = parseWorkbookSheetEntries(wbXml);
  var names = entries.map(function (en) {
    return en.name;
  });
  var shared = [];
  try {
    shared = parseSharedStrings(readUtf8(workDir + "/xl/sharedStrings.xml"));
  } catch (e) {
    shared = [];
  }
  var datesEnabled = options.dates !== false;
  var dateFlags = [];
  try {
    dateFlags = parseStyleDateFlags(readUtf8(workDir + "/xl/styles.xml"));
  } catch (eStyles) {
    dateFlags = [];
  }
  var wb = Workbook.create({ empty: true, title: "", _sourcePath: inputPath });
  for (var i = 0; i < entries.length; i++) {
    var target = relMap[entries[i].rid] || "worksheets/sheet" + (i + 1) + ".xml";
    var part = "xl/" + target.replace(/^xl\//, "");
    var sheetXmlText = "";
    try {
      sheetXmlText = readUtf8(workDir + "/" + part);
    } catch (e2) {
      sheetXmlText = "";
    }
    var cells = parseSheetCells(sheetXmlText, shared, dateFlags, datesEnabled);
    var sheet = new Sheet(names[i], cells);
    sheet.mergeCells = parseMergeCells(sheetXmlText);
    if (sheetXmlText) {
      sheet._origRows = parseOrigRows(sheetXmlText);
      sheet._origSheetPart = part;
    }
    sheet.charts = parseSheetCharts(workDir, part);
    sheet.pivotTables = parseSheetPivotTables(workDir, part);
    wb.sheets.push(sheet);
  }
  if (wb.sheets.length === 0) {
    wb.addSheet("Sheet1");
  }
  wb._sourceDir = workDir;
  wb._origSheetNames = names.slice();
  return wb;
}

export function csvToXlsx(options) {
  options = options || {};
  var outputPath = options.outputPath;
  if (!outputPath) {
    throw new Error("bad argument: csvToXlsx: outputPath required");
  }
  var text =
    options.csv != null
      ? String(options.csv)
      : fs.readFileSync(options.inputPath).toString();
  var rows = parseCsv(text);
  var wb = Workbook.create({ title: options.title || "" });
  var sh = wb.sheet(0);
  if (options.sheetName) {
    sh.name = String(options.sheetName);
  }
  sh.setRange("A1", rows);
  return renderXlsx(wb, outputPath);
}

function parseCsv(text) {
  var rows = [];
  var row = [];
  var cur = "";
  var inQ = false;
  var s = String(text).replace(/^\uFEFF/, "");
  for (var i = 0; i < s.length; i++) {
    var ch = s.charAt(i);
    if (inQ) {
      if (ch === '"') {
        if (s.charAt(i + 1) === '"') {
          cur += '"';
          i++;
        } else {
          inQ = false;
        }
      } else {
        cur += ch;
      }
      continue;
    }
    if (ch === '"') {
      inQ = true;
    } else if (ch === ",") {
      row.push(cur);
      cur = "";
    } else if (ch === "\n") {
      row.push(cur);
      rows.push(row);
      row = [];
      cur = "";
    } else if (ch === "\r") {
      /* skip */
    } else {
      cur += ch;
    }
  }
  if (cur.length || row.length) {
    row.push(cur);
    rows.push(row);
  }
  return rows;
}

export { Workbook, Sheet, colToName, parseCellRef, cellA1 };

export default {
  Workbook: Workbook,
  Sheet: Sheet,
  readXlsx: readXlsx,
  renderXlsx: renderXlsx,
  csvToXlsx: csvToXlsx,
  evaluateFormula: evaluateFormula,
  colToName: colToName,
  parseCellRef: parseCellRef,
  cellA1: cellA1,
};
