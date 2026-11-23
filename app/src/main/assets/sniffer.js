/* PotatoTV injected document script.
 * Runs in every frame (including cross-origin embeds) and reports:
 *  - HLS / DASH / progressive stream URLs as the page requests them
 *  - the site's own server, quality and episode buttons
 *  - a D-pad driven virtual cursor for remote-only devices
 *  - redirect prevention when inside blocked hosts
 *  - Brave-style in-site ad/sponsor/banner removal via CSS selectors
 * It never modifies the video itself; playback stays in ExoPlayer.
 */
(function () {
  'use strict';
  if (window.__potatoInjected) return;
  window.__potatoInjected = true;

  var FRAME_ID = Math.random().toString(36).slice(2, 7);
  var seenStreams = Object.create(null);
  var lastMenusJson = '';
  var elementCounter = 0;
  var cursorOn = false;
  var cursorX = Math.round(window.innerWidth / 2);
  var cursorY = Math.round(window.innerHeight / 2);
  var cursorEl = null;

  /* ---------------------------------------------------------------- bridge */
  function sendNative(payload) {
    try {
      if (window.PotatoBridge && window.PotatoBridge.onEvent) {
        window.PotatoBridge.onEvent(JSON.stringify(payload));
      }
    } catch (e) {}
  }

  function toTop(payload) {
    if (window === window.top) {
      sendNative(payload);
      return;
    }
    try {
      window.top.postMessage(Object.assign({ __potato: true, frame: FRAME_ID }, payload), '*');
    } catch (e) {}
  }

  /* ------------------------------------------------------------- streams */
  function absolute(url) {
    try {
      if (!url) return '';
      var text = String(url).trim();
      if (text.indexOf('blob:') === 0 || text.indexOf('data:') === 0) return '';
      return new URL(text, location.href).href;
    } catch (e) {
      return '';
    }
  }

  function classify(url) {
    if (!url) return '';
    var s = String(url).toLowerCase();
    if (s.indexOf('.m3u8') >= 0 || s.indexOf('format=m3u8') >= 0 ||
        s.indexOf('type=m3u8') >= 0 || s.indexOf('application/x-mpegurl') >= 0) return 'hls';
    if (s.indexOf('.mpd') >= 0 || s.indexOf('format=dash') >= 0) return 'dash';
    if (/\.(mp4|webm|m4v|mov|mkv)(\?|#|$)/.test(s)) return 'progressive';
    return '';
  }

  /* Recognize streams by first bytes when the URL has no tell-tale extension.
     HLS playlists start with #EXTM3U, DASH manifests start with <MPD. */
  function classifyByType(contentType, body) {
    var ct = String(contentType || '').toLowerCase();
    if (ct.indexOf('mpegurl') >= 0 || ct.indexOf('vnd.apple.mpegurl') >= 0) return 'hls';
    if (ct.indexOf('dash+xml') >= 0) return 'dash';
    if (ct.startsWith('video/') || ct.indexOf('octet-stream') >= 0) return 'progressive';
    if (typeof body === 'string') {
      var head = body.slice(0, 64);
      if (/^#EXTM3U/.test(head)) return 'hls';
      if (/<MPD|<mpd/.test(head)) return 'dash';
    }
    return '';
  }

  function emitStream(rawUrl, kind, source) {
    var url = absolute(rawUrl);
    if (!url) return;
    var type = kind || classify(url);
    if (!type) return;
    if (seenStreams[url]) return;
    seenStreams[url] = 1;
    toTop({ type: 'stream', url: url, kind: type, source: source || location.href });
  }

  function scanPerformance() {
    try {
      var entries = performance.getEntriesByType('resource') || [];
      for (var i = 0; i < entries.length; i++) {
        var entry = entries[i];
        var name = entry && entry.name;
        var kind = classify(name);
        if (kind) emitStream(name, kind, 'performance');
        /* Extension-less resources: look at content type */
        if (!kind && entry.transferSize > 0 && entry.encodedBodySize > 0) {
          var ct = entry && entry.contentType ? String(entry.contentType).toLowerCase() : '';
          if (ct.indexOf('mpegurl') >= 0 || ct.indexOf('dash+xml') >= 0 || ct.startsWith('video/')) {
            var t = classifyByType(ct, '');
            if (t) emitStream(name, t, 'performance-type');
          }
        }
      }
    } catch (e) {}
  }

  function scanMedia() {
    try {
      var media = document.querySelectorAll('video, audio, source');
      for (var i = 0; i < media.length; i++) {
        var el = media[i];
        emitStream(el.currentSrc, '', 'media');
        emitStream(el.src, '', 'media');
        emitStream(el.getAttribute('data-src'), '', 'media');
        emitStream(el.getAttribute('data-stream'), '', 'media');
        emitStream(el.getAttribute('data-hls'), '', 'media');
        emitStream(el.getAttribute('data-video'), '', 'media');
        var sources = el.querySelectorAll ? el.querySelectorAll('source') : [];
        for (var j = 0; j < sources.length; j++) {
          emitStream(sources[j].src, '', 'media');
          emitStream(sources[j].getAttribute('src'), '', 'media');
        }
      }
    } catch (e) {}
  }

  /* Hook network APIs before the page's own scripts run. */
  try {
    var originalFetch = window.fetch;
    if (originalFetch) {
      window.fetch = function (input) {
        try {
          var url = typeof input === 'string' ? input : (input && input.url);
          var kind = classify(url);
          if (kind) emitStream(url, kind, 'fetch');
        } catch (e) {}
        return originalFetch.apply(this, arguments);
      };
    }
  } catch (e) {}

  try {
    var originalOpen = XMLHttpRequest.prototype.open;
    XMLHttpRequest.prototype.open = function (method, url) {
      try {
        var kind = classify(url);
        if (kind) emitStream(url, kind, 'xhr');
      } catch (e) {}
      return originalOpen.apply(this, arguments);
    };
  } catch (e) {}

  try {
    var mediaDesc = Object.getOwnPropertyDescriptor(HTMLMediaElement.prototype, 'src');
    if (mediaDesc && mediaDesc.set && mediaDesc.get) {
      Object.defineProperty(HTMLMediaElement.prototype, 'src', {
        configurable: true,
        get: mediaDesc.get,
        set: function (value) {
          try {
            var kind = classify(value);
            if (kind) emitStream(value, kind, 'media-src');
          } catch (e) {}
          return mediaDesc.set.call(this, value);
        }
      });
    }
  } catch (e) {}

  try {
    if (window.MediaSource && window.MediaSource.addSourceBuffer) {
      var originalAdd = window.MediaSource.prototype.addSourceBuffer;
      window.MediaSource.prototype.addSourceBuffer = function (mimeType) {
        try {
          var t = classifyByType(mimeType, '');
          if (t && this && this.sourceBuffers) { /* best effort: attach last URL if known */ }
        } catch (e) {}
        return originalAdd.call(this, mimeType);
      };
    }
  } catch (e) {}

  setInterval(scanPerformance, 1400);
  setInterval(scanMedia, 2200);

  try {
    new MutationObserver(function () { scanMedia(); }).observe(document.documentElement, {
      childList: true,
      subtree: true,
      attributes: true,
      attributeFilter: ['src', 'data-src', 'data-stream', 'data-hls', 'data-video']
    });
  } catch (e) {}

  /* --------------------------------------------------------- redirect prevention */
  // Block all navigation requests to known ad/tracker hosts so redirects can't take you out of the video.
  var AD_HOSTS = (function () {
    // Lazy parse from a module-level loader so we don't deadlock on init.
    return null; // populated below via window.__potatoAdHosts
  })();

  var BLOCKED_PATH_HINTS = [
    '/adserver', '/ads/', '/advert', '/popunder', '/prebid', '/vast?', '/vpaid',
    '/banners/', '/tracking/', '/sponsor/', '/popup/', '/click/', 'gampad', 'doubleclick'
  ];

  function isAdHost(host) {
    if (!host) return false;
    host = host.toLowerCase();
    return isAdDomain(host);
  }

  // IsHostPattern pattern — avoids O(n^2) substring checks; direct suffix checks suffice for ~100 patterns.
  function isAdDomain(host) {
    var domains = window.__potatoAdHosts || [];
    for (var i = 0; i < domains.length; i++) {
      var d = domains[i];
      if (!d) continue;
      if (d === host) return true;
      var idx = host.indexOf('.' + d);
      if (idx >= 0) return true;
      // Also check subdomain chain: e.g. googleadservices.com matches great.googleadservices.com
      var depth = host.split('.').length - 1; // number of dots before d
      while (depth > 0) {
        idx = host.lastIndexOf('.' + d, host.length - depth - 1);
        if (idx >= 0) return true;
        depth--;
      }
      // Direct match against full host after last dot
      if (host.endsWith(d)) return true;
    }
    // Path hints
    var lower = host.toLowerCase();
    for (var j = 0; j < BLOCKED_PATH_HINTS.length; j++) {
      if (lower.indexOf(BLOCKED_PATH_HINTS[j]) >= 0) return true;
    }
    return false;
  }

  // Override navigation-related methods to intercept redirects to ad hosts.
  function blockRedirectsToAdHosts() {
    // Never allow script-opened windows/tabs, and never follow _blank links:
    // streaming sites use both for pop-under and redirect ads.
    try {
      window.open = function () {
        try { console.warn('[PotatoTV] blocked popup window'); } catch (e) {}
        return null;
      };
    } catch (e) {}
    try {
      document.addEventListener('click', function (e) {
        var a = e.target && e.target.closest ? e.target.closest('a') : null;
        if (a && a.getAttribute && a.getAttribute('target') === '_blank') {
          e.preventDefault();
          e.stopPropagation();
        }
      }, true);
    } catch (e) {}
    try {
      var refreshes = document.querySelectorAll('meta[http-equiv="refresh"]');
      for (var i = 0; i < refreshes.length; i++) refreshes[i].removeAttribute('http-equiv');
    } catch (e) {}

    var origOpen = XMLHttpRequest.prototype.open;
    try {
      XMLHttpRequest.prototype.open = function (method, url) {
        try {
          var target = typeof url === 'string' ? url : (url && url.url);
          if (target && isAdHost(String(target).replace(/\?.*/, '').split('#')[0])) {
            throw new Error("PotatoTV blocked XHR redirect to ad host: " + target);
          }
        } catch (e) {
          throw e;
        }
        return origOpen.apply(this, arguments);
      };
    } catch (e) {}

    var fetchOrig = window.fetch;
    if (typeof fetchOrig === 'function') {
      try {
        window.fetch = function (input) {
          try {
            var u = typeof input === 'string' ? input : (input && input.url);
            if (u) {
              var pathPart = u.split('?')[0].split('#')[0];
              if (isAdHost(pathPart)) {
                console.warn('[PotatoTV] blocked redirect fetch to ad host:', u);
                return Promise.reject(new TypeError('PotatoTV blocked fetch redirect to ad host'));
              }
            }
          } catch (e) {}
          return fetchOrig.apply(this, arguments);
        };
      } catch (e) {}
    }
  }

  /* ----------------------------------------------------- Brave-style site hiding */
  // Hides common ad containers based on CSS class/attribute patterns.
  var AD_SELECTORS = [
    '[class*="advert"]', '[class*="Advert"]', '[class*="ad-"]', '[class*="ad "]',
    '[id*="advert"]', '[id*="Advert"]', '[class*="ad-slot"]',
    '[class*="sponsored"]', '[class*="Sponsored"]', '[class*="sponsor-"]',
    '[class*="promoted"]', '[class*="Promoted"]',
    '[class*="tracker"]', '[class*="Tracker"]',
    '[class*="popup"]', '[class*="Popup"]',
    '[class*="banner"]', '[class*="Banner"]', '[class*="banner-ad"]',
    '[class*="cookie-banner"]', '[class*="cookieWarn"]',
    '[class*="remarketing"]', '[class*="Remarketing"]',
    '[class*="outbrain"]', '[class*="Outbrain"]',
    '[class="adbox"]', '[class="ad-box"]',
    '[class*="inread"]', '[class*="Inread"]',
    '[class*="mraid"]', '[class*="MRAID"]',
    '[class*="overlay"]', '[class*="Overlay"]',
    '[class*="social-share"]', '[class*="SocialShare"]',
    '[class*="newsletter"]', '[class*="Newsletter"]',
    '[class*="affiliate"]', '[class*="Affiliate"]',
    '[id*="ad-container"]', '[id*="adContainer"]',
    '[data-ad]', '[data-ads]',
    '[class*="display-ad"]', '[class*="DisplayAd"]',
    '[class*="webview"]',
    'iframe[src*="googleads"]', 'iframe[src*="googlesyndication"]',
    'iframe[src*="doubleclick"]', 'iframe[src*="adsystem"]',
    'iframe[src*="googletagmanager"]'
  ];

  var HIDDEN_SELECTORS = [];
  for (var si = 0; si < AD_SELECTORS.length; si++) {
    HIDDEN_SELECTORS.push(AD_SELECTORS[si]);
  }

  function hideAds() {
    for (var i = 0; i < HIDDEN_SELECTORS.length; i++) {
      try {
        var els = document.querySelectorAll(HIDDEN_SELECTORS[i]);
        for (var j = 0; j < els.length; j++) {
          els[j].style.display = 'none';
        }
      } catch (e) {
        // selector may not match anything; skip silently
      }
    }
    // Also try data-attribute based hiding
    var dataEls = document.querySelectorAll('[data-original-text][data-ad]');
    for (var k = 0; k < dataEls.length; k++) {
      dataEls[k].style.display = 'none';
    }
  }

  /* --------------------------------------------------------- server menus */
  var SERVER_RE = /(server|srv|mirror|source|quality|1080|720|480|360|2160|4k|\bhd\b|\bfhd\b|\bsd\b|auto|embed|player|subtitle|sub\b|dub\b|multi)/i;
  var EPISODE_RE = /(s\d{1,2}\s*[\s._-]?\s*e\d{1,3})|(season\s*\d+)|(episode\s*\d+)|(\be\d{1,3}\b)/i;

  function labelOf(el) {
    var text = (el.innerText || el.textContent || el.value || '').replace(/\s+/g, ' ').trim();
    if (!text && el.getAttribute) text = (el.getAttribute('title') || el.getAttribute('aria-label') || '').trim();
    return text.slice(0, 48);
  }

  function visible(el) {
    try {
      if (el.tagName === 'OPTION') {
        var select = el.closest('select');
        return !!select && select.offsetParent !== null;
      }
      var rect = el.getBoundingClientRect();
      return rect.width > 2 && rect.height > 2 && el.offsetParent !== null;
    } catch (e) {
      return false;
    }
  }

  function idFor(el) {
    if (!el.__potatoId) el.__potatoId = FRAME_ID + 'x' + (++elementCounter);
    return el.__potatoId;
  }

  function scanMenus() {
    var servers = [];
    var episodes = [];
    var nodes;
    try {
      nodes = document.querySelectorAll(
        'button, a, [role="button"], [role="tab"], li, option, ' +
        '[class*="server"], [class*="Server"], [class*="quality"], [class*="Quality"], ' +
        '[class*="episode"], [class*="Episode"], [class*="season"], [class*="Season"]'
      );
    } catch (e) {
      nodes = [];
    }
    var limit = Math.min(nodes.length, 900);
    for (var i = 0; i < limit; i++) {
      var el = nodes[i];
      if (!visible(el)) continue;
      var label = labelOf(el);
      if (!label || label.length > 44) continue;
      if (el.tagName === 'OPTION' && !label) continue;

      var entry = null;
      if (SERVER_RE.test(label)) {
        entry = { id: idFor(el), label: label, kind: 'server' };
        servers.push(entry);
      } else if (EPISODE_RE.test(label)) {
        entry = { id: idFor(el), label: label, kind: 'episode' };
        episodes.push(entry);
      }
    }

    var uniqueServers = [];
    var seenServer = Object.create(null);
    for (var s = 0; s < servers.length; s++) {
      if (!seenServer[servers[s].id]) { seenServer[servers[s].id] = 1; uniqueServers.push(servers[s]); }
    }
    var uniqueEpisodes = [];
    var seenEpisode = Object.create(null);
    for (var e = 0; e < episodes.length; e++) {
      if (!seenEpisode[episodes[e].id]) { seenEpisode[episodes[e].id] = 1; uniqueEpisodes.push(episodes[e]); }
    }

    var payload = {
      type: 'menus',
      frame: FRAME_ID,
      url: location.href,
      servers: uniqueServers.slice(0, 40),
      episodes: uniqueEpisodes.slice(0, 120)
    };
    var encoded = JSON.stringify(payload);
    if (encoded === lastMenusJson) return;
    lastMenusJson = encoded;
    toTop(payload);
  }

  function clickElement(el) {
    try {
      if (!el) return false;
      if (el.scrollIntoView) el.scrollIntoView({ block: 'center', inline: 'center' });
      if (el.tagName === 'OPTION') {
        var select = el.closest('select');
        if (select) {
          select.value = el.value;
          select.dispatchEvent(new Event('input', { bubbles: true }));
          select.dispatchEvent(new Event('change', { bubbles: true }));
          return true;
        }
      }
      el.dispatchEvent(new MouseEvent('mousedown', { bubbles: true, cancelable: true, view: window }));
      el.dispatchEvent(new MouseEvent('mouseup', { bubbles: true, cancelable: true, view: window }));
      el.dispatchEvent(new MouseEvent('click', { bubbles: true, cancelable: true, view: window }));
      if (typeof el.click === 'function') el.click();
      return true;
    } catch (e) {
      return false;
    }
  }

  function clickById(id) {
    try {
      var el = document.querySelector('[data-potato-id="' + id + '"]') ||
        Array.prototype.slice.call(document.querySelectorAll('*')).filter(function (node) {
          return node.__potatoId === id;
        })[0];
      if (el) return clickElement(el);
    } catch (e) {}
    return false;
  }

  function ensureAttributes() {
    try {
      var all = document.querySelectorAll('[class*="server"], [class*="quality"], [class*="episode"], [class*="season"]');
      for (var i = 0; i < Math.min(all.length, 900); i++) {
        var el = all[i];
        if (el.__potatoId && !el.getAttribute('data-potato-id')) {
          el.setAttribute('data-potato-id', el.__potatoId);
        }
      }
    } catch (e) {}
  }

  function scanAll() {
    scanMenus();
    setTimeout(ensureAttributes, 50);
  }

  setInterval(scanAll, 3500);
  setInterval(ensureAttributes, 1800);

  /* ---------------------------------------------------------- virtual cursor */
  function ensureCursorElement() {
    if (cursorEl) return cursorEl;
    cursorEl = document.createElement('div');
    cursorEl.id = '__potato_cursor';
    cursorEl.style.cssText = [
      'position:fixed', 'left:' + cursorX + 'px', 'top:' + cursorY + 'px',
      'width:30px', 'height:30px', 'margin:-15px 0 0 -15px', 'border-radius:50%',
      'border:2px solid #4FCDFF', 'background:rgba(79,205,255,0.16)',
      'box-shadow:0 0 16px rgba(79,205,255,0.9), inset 0 0 10px rgba(79,205,255,0.5)',
      'pointer-events:none', 'z-index:2147483647', 'display:none',
      'transition:left 60ms linear, top 60ms linear'
    ].join(';');
    document.documentElement.appendChild(cursorEl);
    return cursorEl;
  }

  function moveCursor(dx, dy) {
    ensureCursorElement();
    cursorX = Math.max(6, Math.min(window.innerWidth - 6, cursorX + dx));
    cursorY = Math.max(6, Math.min(window.innerHeight - 6, cursorY + dy));
    cursorEl.style.left = cursorX + 'px';
    cursorEl.style.top = cursorY + 'px';
    try {
      var target = document.elementFromPoint(cursorX, cursorY);
      if (target) {
        target.dispatchEvent(new MouseEvent('mousemove', {
          bubbles: true, cancelable: true, view: window, clientX: cursorX, clientY: cursorY
        }));
      }
    } catch (e) {}
  }

  function cursorClick() {
    try {
      var el = document.elementFromPoint(cursorX, cursorY);
      if (!el) return;
      el.dispatchEvent(new MouseEvent('mousemove', {
        bubbles: true, cancelable: true, view: window, clientX: cursorX, clientY: cursorY
      }));
      el.dispatchEvent(new MouseEvent('mousedown', {
        bubbles: true, cancelable: true, view: window, clientX: cursorX, clientY: cursorY
      }));
      el.dispatchEvent(new MouseEvent('mouseup', {
        bubbles: true, cancelable: true, view: window, clientX: cursorX, clientY: cursorY
      }));
      el.dispatchEvent(new MouseEvent('click', {
        bubbles: true, cancelable: true, view: window, clientX: cursorX, clientY: cursorY
      }));
      if (typeof el.click === 'function') el.click();
    } catch (e) {}
  }

  function setCursor(on) {
    cursorOn = !!on;
    ensureCursorElement().style.display = cursorOn ? 'block' : 'none';
    toTop({ type: 'cursor', on: cursorOn });
  }

  window.addEventListener('keydown', function (event) {
    if (!cursorOn) return;
    var step = event.shiftKey ? 120 : 45;
    var handled = true;
    switch (event.key) {
      case 'ArrowUp': moveCursor(0, -step); break;
      case 'ArrowDown': moveCursor(0, step); break;
      case 'ArrowLeft': moveCursor(-step, 0); break;
      case 'ArrowRight': moveCursor(step, 0); break;
      case 'Enter': cursorClick(); break;
      case 'Escape': case 'Backspace': setCursor(false); break;
      default: handled = false;
    }
    if (handled) {
      event.preventDefault();
      event.stopPropagation();
    }
  }, true);

  /* ------------------------------------------------------------- commands */
  function broadcast(cmd) {
    try { window.postMessage(cmd, '*'); } catch (e) {}
    try {
      var frames = document.querySelectorAll('iframe, frame');
      for (var i = 0; i < frames.length; i++) {
        try { frames[i].contentWindow.postMessage(cmd, '*'); } catch (e) {}
      }
    } catch (e) {}
  }

  window.addEventListener('message', function (event) {
    var data = event.data;
    if (!data || typeof data !== 'object') return;

    if (data.__potato && data.type === 'menus' && window === window.top) {
      sendNative(data); /* relay iframe menus to the app */
      return;
    }

    if (data.__potatoCmd) {
      var cmd = data.cmd;
      if (cmd === 'click') clickById(data.id);
      else if (cmd === 'collect') { scanAll(); setTimeout(scanAll, 700); }
      else if (cmd === 'cursor') setCursor(!!data.on);
      if (window !== window.top) {
        try {
          var kids = document.querySelectorAll('iframe, frame');
          for (var i = 0; i < kids.length; i++) {
            try { kids[i].contentWindow.postMessage(data, '*'); } catch (e) {}
          }
        } catch (e) {}
      }
    }
  }, false);

  window.__potatoCommand = function (cmd) { broadcast(cmd); };
  window.__potatoClickServer = function (id) { broadcast({ __potatoCmd: true, cmd: 'click', id: id }); };
  window.__potatoClickEpisode = function (id) { broadcast({ __potatoCmd: true, cmd: 'click', id: id }); };
  window.__potatoCollect = function () { broadcast({ __potatoCmd: true, cmd: 'collect' }); scanAll(); };
  window.__potatoSetCursor = function (on) { broadcast({ __potatoCmd: true, cmd: 'cursor', on: !!on }); setCursor(!!on); };

  /* --------------------------------------------------------------- startup */
  // Expose internals for the host app (PotatoBridge injection point).
  window.__potatoAdHosts = window.__potatoAdHosts && window.__potatoAdHosts.length
    ? window.__potatoAdHosts
    : [
        'doubleclick.net', 'googlesyndication.com', 'googleadservices.com',
        'adnxs.com', 'adservice.google.com', 'amazon-adsystem.com',
        'ads-twitter.com', 'connect.facebook.net', 'scorecardresearch.com',
        'quantserve.com', 'taboola.com', 'outbrain.com', 'popads.net',
        'exoclick.com', 'juicyads.com', 'propellerads.com', 'adf.ly',
        'pubmatic.com', 'rubiconproject.com', 'casalemedia.com', 'criteo.com'
      ];
  window.__potatoDoHideAds = hideAds;
  window.__potatoDoBlockRedirects = blockRedirectsToAdHosts;

  try {
    hideAds(); // apply immediately on load
    blockRedirectsToAdHosts();
    document.addEventListener('DOMContentLoaded', function () {
      scanMedia();
      scanPerformance();
      scanAll();
      if (window === window.top) {
        sendNative({ type: 'page', title: document.title || '', url: location.href });
      }
    });
    window.addEventListener('load', function () {
      scanMedia();
      scanPerformance();
      scanAll();
      if (window === window.top) {
        sendNative({ type: 'page', title: document.title || '', url: location.href });
      }
    });
  } catch (e) {}

  scanPerformance();
  scanMedia();
  scanAll();
})();
