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
  }
  this.cells[a1] = cell;
  this._markDirty(a1);
  return this;
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
  this.charts.push(entry);
  return entry;
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

function parseSheetCells(xml, shared) {
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
          cell.value = Number(vRaw);
          cell.valueType = "number";
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
      cells[a1] = { type: "number", value: Number(vRaw) };
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
    if (sheet.charts.length > 0) {
      throw new Error(
        "xlsx: addChart on a workbook loaded by readXlsx is not supported yet; " +
          "build a new workbook (buildXlsx) or edit via weizhi:xlsx-raw"
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
    var cells = parseSheetCells(sheetXmlText, shared);
    var sheet = new Sheet(names[i], cells);
    sheet.mergeCells = parseMergeCells(sheetXmlText);
    if (sheetXmlText) {
      sheet._origRows = parseOrigRows(sheetXmlText);
      sheet._origSheetPart = part;
    }
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
  colToName: colToName,
  parseCellRef: parseCellRef,
  cellA1: cellA1,
};
