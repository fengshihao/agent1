package com.agent1.android.productivity.logic.business

/**
 * 墨览壳在 CDN 上，Markdown 里的相对图片会按壳地址去请求。
 * 渲染后把非 http(s)/data/blob 的 img 改写到 [PreviewWorkspaceResources] 虚拟源。
 */
object MolanPreviewImages {

    fun rewriteJs(markdownRelativePath: String): String {
        val base = jsString(PreviewWorkspaceResources.directoryUrl(markdownRelativePath))
        return """
            (function(base){
              function local(src){
                if(!src) return false;
                if(/^(https?:|data:|blob:|\/\/)/i.test(src)) return false;
                return true;
              }
              function rewrite(img){
                var src = img.getAttribute("src") || "";
                if(!local(src)) return;
                try { img.src = new URL(src, base).href; } catch (e) {}
              }
              function scan(root){
                if(!root || !root.querySelectorAll) return;
                var imgs = root.querySelectorAll("img");
                for (var i = 0; i < imgs.length; i++) rewrite(imgs[i]);
              }
              function roots(){
                return [document.getElementById("molanPreviewBody"), document.getElementById("vditor")];
              }
              function pass(){ roots().forEach(scan); }
              pass();
              if (window.__agent1ImgObs) return;
              var obs = new MutationObserver(pass);
              if (document.body) {
                obs.observe(document.body, {subtree:true, childList:true, attributes:true, attributeFilter:["src"]});
              }
              window.__agent1ImgObs = obs;
            })($base);
        """.trimIndent()
    }

    private fun jsString(value: String): String {
        val escaped = value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "")
            .replace("\r", "")
        return "\"$escaped\""
    }
}
