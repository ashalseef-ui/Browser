// Volt Browser GeckoView Bridge - Content Media Scanner
(function () {
  if (window.__voltGeckoSnifferInstalled) return;
  window.__voltGeckoSnifferInstalled = true;

  let lastSentKey = "";

  function scanMedia() {
    try {
      const found = [];
      const seen = new Set();
      const pushUrl = (rawUrl, kindHint, titleHint, posterHint) => {
        if (!rawUrl || rawUrl.startsWith("blob:") || rawUrl.startsWith("data:")) return;
        let resolved = rawUrl;
        try {
          resolved = new URL(rawUrl, location.href).href;
        } catch (_) {}
        if (!/^https?:\/\//i.test(resolved) || seen.has(resolved)) return;
        seen.add(resolved);
        found.push({
          url: resolved,
          kind: kindHint || "video",
          title: titleHint || document.title || "",
          poster: posterHint || ""
        });
      };

      document.querySelectorAll("video").forEach((v) => {
        const poster = v.getAttribute("poster") || "";
        const title = v.getAttribute("title") || v.getAttribute("aria-label") || document.title;
        if (v.currentSrc) pushUrl(v.currentSrc, "video", title, poster);
        if (v.src) pushUrl(v.src, "video", title, poster);
        v.querySelectorAll("source").forEach((s) => {
          if (s.src) pushUrl(s.src, "video", title, poster);
        });
      });

      document.querySelectorAll("audio").forEach((a) => {
        const title = a.getAttribute("title") || document.title;
        if (a.currentSrc) pushUrl(a.currentSrc, "audio", title, "");
        if (a.src) pushUrl(a.src, "audio", title, "");
        a.querySelectorAll("source").forEach((s) => {
          if (s.src) pushUrl(s.src, "audio", title, "");
        });
      });

      document.querySelectorAll("a[href]").forEach((a) => {
        const href = a.href || "";
        if (/\.(mp4|webm|m3u8|mpd|mp3|m4a)(\?|#|$)/i.test(href)) {
          pushUrl(href, "auto", (a.textContent || "").trim() || document.title, "");
        }
      });

      if (found.length === 0) return;
      const key = found.map((x) => x.url).slice(0, 12).join("|");
      if (key === lastSentKey) return;
      lastSentKey = key;

      browser.runtime.sendMessage({
        type: "domMediaDetected",
        pageUrl: location.href,
        pageTitle: document.title || "",
        items: found.slice(0, 20)
      });
    } catch (_) {}
  }

  scanMedia();
  let debounceTimer = null;
  const observer = new MutationObserver(() => {
    clearTimeout(debounceTimer);
    debounceTimer = setTimeout(scanMedia, 900);
  });
  if (document.documentElement) {
    observer.observe(document.documentElement, { childList: true, subtree: true });
  }
  try {
    browser.runtime.onMessage.addListener((msg) => {
      if (msg && msg.type === "rescanMedia") {
        lastSentKey = "";
        scanMedia();
      }
    });
  } catch (_) {}
})();
