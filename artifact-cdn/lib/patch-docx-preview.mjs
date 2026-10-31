/**
 * docx-preview 0.4.1 打包进 host-web 之前，改它自己的解析：
 * - 边框省略 w:sz 时按 Word 的缺省 4（0.5pt）画，不再生成 null 线宽
 * - tblBorders 的 insideH / insideV 补到还没有对应边的单元格上
 * - textDirection=lrTb 是横排（也是缺省方向），不再映成 vertical-lr
 */

const DEFAULT_BORDER = "0.50pt";

function replaceOnce(source, pattern, replacement, label) {
  const flags = pattern.flags.includes("g") ? pattern.flags : `${pattern.flags}g`;
  const global = new RegExp(pattern.source, flags);
  const matches = source.match(global);
  if (!matches || matches.length !== 1) {
    throw new Error(`docx-preview patch ${label}: expected 1 match, got ${matches ? matches.length : 0}`);
  }
  global.lastIndex = 0;
  return source.replace(global, replacement);
}

export function patchDocxPreviewBundle(source) {
  let out = replaceOnce(
    source,
    /vertical-lr/g,
    "horizontal-tb",
    "lrTb writing mode",
  );

  if (out.includes("LengthUsage.Border")) {
    out = replaceOnce(
      out,
      /var size = globalXmlParser\.lengthAttr\(c, "sz", LengthUsage\.Border\);/g,
      'var size = globalXmlParser.lengthAttr(c, "sz", LengthUsage.Border) ?? "0.50pt";',
      "border size default",
    );
    out = replaceOnce(
      out,
      /case "bottom":\n(\s*)output\["border-bottom"\] = values\.valueOfBorder\(c\);\n\1break;/g,
      (full, indent) =>
        `${full}\n${indent}case "insideH":\n${indent}    if (!output["border-top"]) output["border-top"] = values.valueOfBorder(c);\n${indent}    if (!output["border-bottom"]) output["border-bottom"] = values.valueOfBorder(c);\n${indent}    break;\n${indent}case "insideV":\n${indent}    if (!output["border-left"]) output["border-left"] = values.valueOfBorder(c);\n${indent}    if (!output["border-right"]) output["border-right"] = values.valueOfBorder(c);\n${indent}    break;`,
      "inside borders",
    );
  } else {
    out = replaceOnce(
      out,
      /return`\$\{([\w$]+)\.lengthAttr\(([\w$]+),"sz",([\w$]+)\)\} \$\{([\w$]+)\} \$\{("auto"==[\w$]+\?[\w$]+:[\w$]+)\}`/g,
      'return`${($1.lengthAttr($2,"sz",$3)??"0.50pt")} ${$4} ${$5}`',
      "border size default",
    );
    out = replaceOnce(
      out,
      /case"bottom":t\["border-bottom"\]=(\w+)\.valueOfBorder\(r\)\}\}\}/g,
      'case"bottom":t["border-bottom"]=$1.valueOfBorder(r);break;case"insideH":if(!t["border-top"])t["border-top"]=$1.valueOfBorder(r);if(!t["border-bottom"])t["border-bottom"]=$1.valueOfBorder(r);break;case"insideV":if(!t["border-left"])t["border-left"]=$1.valueOfBorder(r);if(!t["border-right"])t["border-right"]=$1.valueOfBorder(r)}}}',
      "inside borders",
    );
  }

  if (!out.includes("horizontal-tb") || !out.includes(DEFAULT_BORDER) || !out.includes("insideH")) {
    throw new Error("docx-preview patch did not apply");
  }
  return out;
}
