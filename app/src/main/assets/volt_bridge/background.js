// Volt Browser GeckoView Bridge - Background Script
// Coordinates Proxy / Kill Switch enforcement, Lite Data Saver filtering, and Media Hub relay.

let bridgeState = {
  killSwitch: false,
  liteMode: false,
  proxyType: "",
  proxyHost: "",
  proxyPort: 0,
  proxyUsername: "",
  proxyPassword: "",
  bypassedHosts: []
};

let nativePort = null;

function connectNativeBridge() {
  try {
    nativePort = browser.runtime.connectNative("voltBridge");
    nativePort.onMessage.addListener((msg) => {
      if (!msg || typeof msg !== "object") return;
      if (msg.type === "syncState" && msg.state) {
        bridgeState = Object.assign({}, bridgeState, msg.state);
      }
    });
    nativePort.onDisconnect.addListener(() => {
      nativePort = null;
      setTimeout(connectNativeBridge, 1500);
    });
  } catch (e) {
    // Native messaging unavailable in test context
  }
}

connectNativeBridge();

function extractHost(url) {
  try {
    return new URL(url).hostname.replace(/^www\./i, "").toLowerCase();
  } catch (e) {
    return "";
  }
}

// 1. Proxy & Kill Switch enforcement
if (browser.proxy && browser.proxy.onRequest) {
  browser.proxy.onRequest.addListener(
    (requestInfo) => {
      const host = extractHost(requestInfo.url || "");
      if (host === "127.0.0.1" || host === "localhost") {
        return { type: "direct" };
      }
      if (bridgeState.killSwitch) {
        // Drop outbound connections deterministically when Kill Switch is active
        return { type: "http", host: "127.0.0.1", port: 9 };
      }
      if (bridgeState.proxyHost && bridgeState.proxyPort > 0) {
        const pType = (bridgeState.proxyType || "http").toLowerCase();
        if (pType === "socks5" || pType === "socks") {
          return {
            type: "socks",
            host: bridgeState.proxyHost,
            port: bridgeState.proxyPort,
            proxyDNS: true,
            username: bridgeState.proxyUsername || undefined,
            password: bridgeState.proxyPassword || undefined
          };
        }
        return {
          type: pType === "https" ? "https" : "http",
          host: bridgeState.proxyHost,
          port: bridgeState.proxyPort
        };
      }
      return { type: "direct" };
    },
    { urls: ["<all_urls>"] }
  );
}

// 2. Proxy Auth handler for authenticated HTTP relays
if (browser.webRequest && browser.webRequest.onAuthRequired) {
  browser.webRequest.onAuthRequired.addListener(
    (details) => {
      if (
        details.isProxy &&
        bridgeState.proxyUsername &&
        bridgeState.proxyHost &&
        details.challenger &&
        details.challenger.host === bridgeState.proxyHost
      ) {
        return {
          authCredentials: {
            username: bridgeState.proxyUsername,
            password: bridgeState.proxyPassword || ""
          }
        };
      }
      return {};
    },
    { urls: ["<all_urls>"] },
    ["blocking"]
  );
}

// 3. Lite Data Saver blocking + media stream URL sniffing
if (browser.webRequest && browser.webRequest.onBeforeRequest) {
  browser.webRequest.onBeforeRequest.addListener(
    (details) => {
      const pageHost = extractHost(details.documentUrl || details.originUrl || details.url || "");
      const isBypassed = Array.isArray(bridgeState.bypassedHosts) && bridgeState.bypassedHosts.includes(pageHost);

      if (
        bridgeState.liteMode &&
        !isBypassed &&
        (details.type === "image" || details.type === "font" || details.type === "imageset")
      ) {
        return { cancel: true };
      }

      const lower = (details.url || "").toLowerCase();
      if (
        lower.includes(".m3u8") ||
        lower.includes(".mpd") ||
        lower.includes(".mp4") ||
        lower.includes(".webm")
      ) {
        if (nativePort) {
          try {
            nativePort.postMessage({
              type: "networkMediaDetected",
              url: details.url,
              pageUrl: details.documentUrl || details.originUrl || "",
              mimeHint: details.type || ""
            });
          } catch (_) {}
        }
      }
      return {};
    },
    { urls: ["<all_urls>"] },
    ["blocking"]
  );
}

// 4. Relay DOM-discovered media from content script to Android Media Hub
browser.runtime.onMessage.addListener((message, sender) => {
  if (!message || !nativePort) return;
  if (message.type === "domMediaDetected") {
    try {
      nativePort.postMessage({
        type: "domMediaDetected",
        pageUrl: message.pageUrl || (sender && sender.url) || "",
        pageTitle: message.pageTitle || "",
        items: message.items || []
      });
    } catch (_) {}
  }
});
