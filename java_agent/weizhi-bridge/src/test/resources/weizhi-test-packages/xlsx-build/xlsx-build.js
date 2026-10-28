/**
 * xlsx-build.js — fluent Builder for new workbooks (authoring only).
 *
 * import { buildXlsx, buildWorkbook } from "weizhi:xlsx-build";
 */
import { Workbook, renderXlsx } from "weizhi:xlsx";

function SheetBuilder(sheet) {
  this._sheet = sheet;
}

SheetBuilder.prototype.headers = function (cols) {
  if (!Array.isArray(cols)) {
    throw new Error("bad argument: headers: array required");
  }
  this._sheet.setRange("A1", [cols]);
  return this;
};

SheetBuilder.prototype.rows = function () {
  var startRow = 2;
  var dim = this._sheet.dimensions();
  if (dim.maxRow >= 0) {
    startRow = dim.maxRow + 2;
  }
  var data = Array.prototype.slice.call(arguments);
  if (data.length === 1 && Array.isArray(data[0]) && Array.isArray(data[0][0])) {
    data = data[0];
  }
  this._sheet.setRange("A" + startRow, data);
  return this;
};

SheetBuilder.prototype.cells = function (startRef, rows) {
  this._sheet.setRange(startRef, rows);
  return this;
};

SheetBuilder.prototype.set = function (ref, value) {
  this._sheet.set(ref, value);
  return this;
};

SheetBuilder.prototype.formula = function (ref, formula, cachedValue) {
  this._sheet.setFormula(ref, formula, cachedValue);
  return this;
};

SheetBuilder.prototype.merge = function (rangeA1) {
  this._sheet.merge(rangeA1);
  return this;
};

SheetBuilder.prototype.chart = function (chart) {
  this._sheet.addChart(chart);
  return this;
};

SheetBuilder.prototype.sheet = function () {
  return this._sheet;
};

function WorkbookBuilder(wb) {
  this._wb = wb;
  this._cur = wb.sheets.length ? wb.sheets[0] : null;
  this._sb = this._cur ? new SheetBuilder(this._cur) : null;
}

WorkbookBuilder.prototype._ensureSheet = function () {
  if (!this._cur) {
    this._cur = this._wb.addSheet("Sheet1");
    this._sb = new SheetBuilder(this._cur);
  }
  return this._sb;
};

WorkbookBuilder.prototype.sheet = function (name) {
  var n = String(name || "Sheet1");
  // 若仅有空默认 Sheet1，改名复用，避免多出一张空表。
  if (
    this._wb.sheets.length === 1 &&
    this._wb.sheets[0].name === "Sheet1" &&
    Object.keys(this._wb.sheets[0].cells).length === 0 &&
    n !== "Sheet1"
  ) {
    this._wb.sheets[0].name = n;
    this._cur = this._wb.sheets[0];
    this._sb = new SheetBuilder(this._cur);
    return this;
  }
  var existing = null;
  try {
    existing = this._wb.sheet(n);
  } catch (e) {
    existing = null;
  }
  this._cur = existing || this._wb.addSheet(n);
  this._sb = new SheetBuilder(this._cur);
  return this;
};

WorkbookBuilder.prototype.headers = function (cols) {
  this._ensureSheet().headers(cols);
  return this;
};

WorkbookBuilder.prototype.rows = function () {
  this._ensureSheet().rows.apply(this._sb, arguments);
  return this;
};

WorkbookBuilder.prototype.cells = function (startRef, rows) {
  this._ensureSheet().cells(startRef, rows);
  return this;
};

WorkbookBuilder.prototype.set = function (ref, value) {
  this._ensureSheet().set(ref, value);
  return this;
};

WorkbookBuilder.prototype.formula = function (ref, formula, cachedValue) {
  this._ensureSheet().formula(ref, formula, cachedValue);
  return this;
};

WorkbookBuilder.prototype.merge = function (rangeA1) {
  this._ensureSheet().merge(rangeA1);
  return this;
};

WorkbookBuilder.prototype.chart = function (chart) {
  this._ensureSheet().chart(chart);
  return this;
};

WorkbookBuilder.prototype.title = function (text) {
  this._wb.title = String(text);
  return this;
};

WorkbookBuilder.prototype.workbook = function () {
  return this._wb;
};

export function buildWorkbook(options, write) {
  var opts = Object.assign({}, options || {}, { empty: true });
  var wb = Workbook.create(opts);
  var b = new WorkbookBuilder(wb);
  if (typeof write === "function") {
    write(b);
  }
  if (wb.sheets.length === 0) {
    wb.addSheet("Sheet1");
  }
  return b;
}

export function buildXlsx(outputPath, write, options) {
  if (!outputPath) {
    throw new Error("bad argument: buildXlsx: outputPath required");
  }
  var b = buildWorkbook(options || {}, write);
  return renderXlsx(b.workbook(), outputPath);
}

export { WorkbookBuilder, SheetBuilder };

export default {
  buildXlsx: buildXlsx,
  buildWorkbook: buildWorkbook,
  WorkbookBuilder: WorkbookBuilder,
  SheetBuilder: SheetBuilder,
};
