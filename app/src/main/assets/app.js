/* TV Home: behaviour for the TV Home Launcher design.
   Data comes from window.Native (the Android bridge): installed apps, inputs, network,
   device. In a desktop browser a mock stands in so the design can be previewed. */
(function () {
  'use strict';

  // ------------------------------------------------------------ constants

  var PAD = 30, TILE_W = 322, PITCH = TILE_W + PAD, SHELF_W = 1730, FADE = 120;
  var MAX_HOME = 5, COLS = 5, ROW_H = 172 + 30;
  var EASE = 'cubic-bezier(.22,.61,.24,1)';

  var WALLS = [
    { id: 'misty', n: 'Misty coast', veil: 0.2 },
    { id: 'cove', n: 'Quiet cove', veil: 0.12 },
    { id: 'forest', n: 'Forest mist', veil: 0.12 },
    { id: 'night', n: 'Desert stars', veil: 0.12 }
  ];

  // Design brand marks (brands.js) matched to the package names apps use on Android TV.
  var BR = {};
  (window.TVAPPS || []).forEach(function (a) { BR[a.id] = a; });
  var RULES = [
    ['youtube', /^com\.google\.android\.youtube(\.tv|\.googletv)?$/],
    ['netflix', /^com\.netflix\.(ninja|mediaclient)$/],
    ['spotify', /^com\.spotify\.(tv\.android|music)$/],
    ['dstv', /dstv/i],
    ['prime', /^com\.amazon\.(amazonvideo\.livingroom|avod\.thirdpartyclient)$/],
    ['disney', /^com\.disney\.disneyplus/],
    ['stremio', /^com\.stremio\./],
    ['hbomax', /^(com\.hbo\.|com\.wbd\.stream)/],
    ['tubi', /^com\.tubitv/],
    ['twitch', /^tv\.twitch\./],
    ['plex', /^com\.plexapp\./],
    ['vlc', /^org\.videolan\.vlc/],
    ['soundcloud', /^com\.soundcloud\./],
    ['deezer', /^deezer\.android\./],
    ['chrome', /^com\.(android|chrome)\.chrome/],
    ['vimeo', /^com\.vimeo\./],
    ['crunchyroll', /^com\.crunchyroll\./],
    ['tiktok', /^(com\.zhiliaoapp\.musically|com\.ss\.android\.ugc\.)/],
    ['steam', /^com\.valvesoftware\./],
    ['photos', /^com\.google\.android\.apps\.photos/]
  ];

  var ICON = {
    wifi: 'M2 9a15 15 0 0120 0 M5.5 12.5a10 10 0 0113 0 M9 16a5 5 0 016 0 M12 19.5h.01',
    wifiOff: 'M2 9a15 15 0 0120 0 M5.5 12.5a10 10 0 0113 0 M9 16a5 5 0 016 0 M12 19.5h.01 M3 3l18 18',
    ethernet: 'M4 8h16v9H4z M8 17v3 M12 17v3 M16 17v3 M9 8V5h6v3',
    lock: 'M5 11h14v9H5z M8 11V8a4 4 0 018 0v3',
    tick: 'M5 12.5l4.5 4.5L19 7.5',
    back: 'M15 5l-7 7 7 7',
    chev: 'M9 5l7 7-7 7',
    up: 'M6 15l6-6 6 6'
  };

  var N = window.Native || mockNative();

  // ------------------------------------------------------------- helpers

  function $(id) { return document.getElementById(id); }
  function q(sel) { return document.querySelector(sel); }
  function J(s, d) { try { var v = JSON.parse(s); return v == null ? d : v; } catch (e) { return d; } }
  function esc(s) {
    return String(s == null ? '' : s).replace(/[&<>"']/g, function (c) {
      return { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c];
    });
  }
  function svg(d, size, sw, extra) {
    return '<svg aria-hidden="true" width="' + size + '" height="' + size + '" viewBox="0 0 24 24" fill="none" stroke="#f4f4f1" stroke-width="' + sw +
      '" stroke-linecap="round" stroke-linejoin="round"' + (extra || '') + '><path d="' + d + '"></path></svg>';
  }
  function wp(id, kind) { return 'wallpapers/' + id + '-' + kind + '.jpg'; }
  function wallById(id) { for (var i = 0; i < WALLS.length; i++) if (WALLS[i].id === id) return WALLS[i]; return WALLS[0]; }
  function wallIndex(id) { for (var i = 0; i < WALLS.length; i++) if (WALLS[i].id === id) return i; return 0; }
  function brandFor(pkg) {
    for (var i = 0; i < RULES.length; i++) if (RULES[i][1].test(pkg)) return BR[RULES[i][0]] || null;
    return null;
  }

  // ---------------------------------------------------------------- state

  var S = {
    view: 'home', zone: 'dock', dockIdx: 0, gridIdx: 0, topIdx: 1,
    wifi: false, wifiRows: [], wifiIdx: 0,
    items: [], byId: {}, home: [], dock: [], grid: [],
    wall: 'misty', screen: null, lock: false, net: {}, dev: {}
  };

  // ----------------------------------------------------------------- data

  function loadItems() {
    var apps = J(N.apps(), []), ins = J(N.inputs(), []), items = [];
    apps.forEach(function (a) {
      if (!a || !a.pkg) return;
      items.push({ id: a.pkg, kind: 'app', pkg: a.pkg, cls: a.cls || '', n: a.n || a.pkg, tv: !!a.tv,
        banner: a.banner || null, icon: a.icon || null, brand: brandFor(a.pkg) });
    });
    ins.forEach(function (x) {
      items.push({ id: 'in:' + x.id, kind: 'input', inputId: x.id, n: x.n || 'Input', type: x.type });
    });
    S.items = items;
    S.byId = {};
    items.forEach(function (it) { S.byId[it.id] = it; });
  }

  function loadHome() {
    var saved = J(N.get('home'), null);
    var home = saved && saved.length ? saved.filter(function (id) { return S.byId[id]; }) : [];
    if (!home.length) home = defaultHome();
    S.home = home.slice(0, MAX_HOME);
  }

  function defaultHome() {
    var pick = [];
    ['youtube', 'netflix', 'spotify', 'dstv', 'prime'].forEach(function (b) {
      for (var i = 0; i < S.items.length; i++) {
        var it = S.items[i];
        if (it.brand && it.brand.id === b) { pick.push(it.id); break; }
      }
    });
    var skip = /settings|tvlauncher|launcher|vending|leanbacklauncher|tvrecommendations/i;
    [true, false].forEach(function (tvOnly) {
      S.items.forEach(function (it) {
        if (pick.length >= MAX_HOME || it.kind !== 'app' || pick.indexOf(it.id) >= 0 || skip.test(it.pkg)) return;
        if (tvOnly && !it.tv) return;
        pick.push(it.id);
      });
    });
    return pick.slice(0, MAX_HOME);
  }

  function saveHome() { N.set('home', JSON.stringify(S.home)); }

  // ------------------------------------------------------------ rendering

  /* A tile face: the design's brand mark when the app is one of its brands, otherwise the
     app's own Android TV banner, otherwise its icon. Inputs get their name (HDMI 1, AV). */
  function face(it, size) {
    var big = size === 'tile', b = it.brand;
    if (b && b.d) {
      var p = (b.vb || '0 0 24 24').split(' ').map(Number), r = p[2] / p[3], wide = r >= 1.5;
      var w = big ? (wide ? 156 : 90) : (wide ? 64 : 38), h = wide ? Math.round(w / r) : w;
      return { bg: b.bg, html: '<svg aria-hidden="true" width="' + w + '" height="' + h + '" viewBox="' + (b.vb || '0 0 24 24') +
        '" fill="' + (b.outline ? 'none' : b.fg) + '" stroke="' + (b.outline ? b.fg : 'none') +
        '" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round"><path d="' + b.d + '"></path></svg>' };
    }
    if (b && b.txt) return { bg: b.bg, html: '<span class="txt" style="color:' + b.fg + '">' + esc(b.txt) + '</span>' };
    if (it.kind === 'input') {
      return { bg: '#222b31', html: '<span class="txt' + (it.n.length > 7 ? ' sm' : '') + '" style="color:#f4f4f1">' + esc(it.n) + '</span>' +
        (big ? '<span class="sub">Input</span>' : '') };
    }
    if (it.banner) return { bg: '#0e1215', html: '<img class="banner" alt="" src="' + it.banner + '">' };
    // No TV banner: the app's icon, with its name so it is never ambiguous.
    if (it.icon) {
      return { bg: '#2b343b', cls: big ? 'iconic' : '', html: '<img class="icon" alt="" src="' + it.icon + '">' +
        (big ? '<span class="nmc">' + esc(it.n) + '</span>' : '') };
    }
    return { bg: '#2b343b', html: '<span class="nmonly">' + esc(it.n) + '</span>' };
  }

  function tileHTML(it, key, style) {
    var f = face(it, 'tile');
    return '<button class="tile' + (f.cls ? ' ' + f.cls : '') + '" tabindex="-1" data-k="' + key + '" aria-label="' + esc(it.n) + '" title="' + esc(it.n) +
      '" style="background:' + f.bg + ';' + (style || '') + '">' + f.html + '</button>';
  }

  function renderHome() {
    S.dock = S.home.map(function (id) { return S.byId[id]; }).filter(Boolean);
    S.grid = S.items.slice();
    $('track').innerHTML = S.dock.map(function (it, i) { return tileHTML(it, 'dock:' + i, 'left:' + (i * PITCH) + 'px'); }).join('');
    $('track').style.width = Math.max(0, S.dock.length * PITCH - PAD) + 'px';
    $('grid').innerHTML = S.grid.map(function (it, i) { return tileHTML(it, 'grid:' + i); }).join('');
    S.gridFirst = -1;
    S.dockIdx = Math.max(0, Math.min(S.dockIdx, S.dock.length - 1));
    S.gridIdx = Math.max(0, Math.min(S.gridIdx, S.grid.length - 1));
    if (!S.dock.length && S.zone === 'dock') S.zone = 'top';
    paint();
  }

  function buildWallpapers() {
    $('wp').innerHTML = WALLS.map(function (w) {
      return '<img class="photo" alt="" data-id="' + w.id + '" src="' + wp(w.id, 'photo') + '">';
    }).join('');
  }

  function applyWall() {
    var w = wallById(S.wall);
    [].forEach.call(document.querySelectorAll('#wp .photo'), function (img) {
      img.classList.toggle('cur', img.getAttribute('data-id') === w.id);
    });
    $('veil').style.backgroundImage = 'linear-gradient(rgba(6,10,14,.46),rgba(6,10,14,.46)),url("' + wp(w.id, 'soft') + '")';
    var tint = 'linear-gradient(rgba(8,14,18,' + w.veil + '),rgba(8,14,18,' + w.veil + '))';
    var g = $('dockGlass').style;
    g.backgroundImage = tint + ',url("' + wp(w.id, 'glass') + '")';
    g.backgroundPosition = '0 0,-65px -711px';
    var pop = $('wifiPop').style;
    pop.backgroundImage = 'linear-gradient(rgba(8,14,18,.5),rgba(8,14,18,.5)),url("' + wp(w.id, 'glass') + '")';
    pop.backgroundPosition = '0 0,-1258px -176px';
    var c = $('clockText').style;
    c.backgroundImage = 'linear-gradient(180deg,rgba(255,255,255,.96),rgba(255,255,255,.80) 42%,rgba(255,255,255,.88)),url("' + wp(w.id, 'frost') + '")';
    c.backgroundSize = 'auto,1920px 1080px';
    c.backgroundPosition = '0 0,center -280px';
    c.backgroundRepeat = 'repeat,no-repeat';
  }

  function tick() {
    var d = new Date(), h = d.getHours() % 12 || 12;
    $('clockText').textContent = $('clockSmall').textContent = h + ':' + ('0' + d.getMinutes()).slice(-2);
    clearTimeout(S.tt);
    S.tt = setTimeout(tick, 60000 - d.getSeconds() * 1000 - d.getMilliseconds() + 40);
  }

  /* Move the focus highlight and keep the shelf / grid scrolled to it. */
  function paint() {
    [].forEach.call(document.querySelectorAll('#home .on'), function (el) { el.classList.remove('on'); });
    var key = null;
    if (S.screen) key = null;
    else if (S.wifi) key = 'wifi:' + S.wifiIdx;
    else if (S.zone === 'dock') key = 'dock:' + S.dockIdx;
    else if (S.zone === 'top') key = 'top:' + S.topIdx;
    else if (S.zone === 'grid') key = 'grid:' + S.gridIdx;
    var el = key && q('#home [data-k="' + key + '"]');
    if (el) el.classList.add('on');
    $('btnWifi').classList.toggle('open', S.wifi);

    // Dock shelf: scroll once focus passes the fourth tile, fade only the edges that hide tiles.
    var n = S.dock.length, trackW = Math.max(0, n * PITCH - PAD), maxOff = Math.max(0, trackW - SHELF_W);
    var off = Math.min(maxOff, Math.max(0, S.dockIdx - 3) * PITCH);
    $('track').style.transform = 'translateX(' + (-off) + 'px)';

    if (S.zone === 'grid' && S.view === 'apps') {
      // Keep one row above the focus in view; nudge 8px so the row scrolled past is fully hidden.
      var first = Math.max(0, Math.floor(S.gridIdx / COLS) - 1);
      S.gridTop = first ? first * ROW_H + 8 : 0;
      if (S.gridFirst !== first) {
        S.gridFirst = first;
        var kids = $('grid').children;
        for (var gi = 0; gi < kids.length; gi++) kids[gi].style.visibility = gi < first * COLS ? 'hidden' : '';
      }
      $('grid').style.transform = 'translateY(' + (-S.gridTop) + 'px)';
    }
  }

  // ---------------------------------------------------------------- views

  function setView(v, force) {
    if (S.lock && !force) return false;
    S.lock = true;
    setTimeout(function () { S.lock = false; }, 660);
    S.view = v;
    $('home').classList.toggle('apps', v === 'apps');
    return true;
  }

  function goApps(col) {
    if (!S.grid.length || !setView('apps')) return;
    S.zone = 'grid';
    S.gridIdx = Math.min(col || 0, S.grid.length - 1);
    paint();
  }

  function goHome(force) {
    if (S.view !== 'home' && !setView('home', force)) return;
    S.zone = S.dock.length ? 'dock' : 'top';
    paint();
  }

  function launch(it) {
    if (!it) return;
    if (it.kind === 'input') N.openInput(it.inputId);
    else N.launch(it.pkg, it.cls || '');
  }

  // ---------------------------------------------------------------- Wi-Fi

  function setNetGlyph() {
    S.net = J(N.network(), {});
    var d = !S.net.connected ? ICON.wifiOff : S.net.type === 'ethernet' ? ICON.ethernet : ICON.wifi;
    $('wifiGlyph').setAttribute('d', d);
  }

  function buildWifi() {
    setNetGlyph();
    var net = S.net, list = J(N.wifiList(), null), rows = [];
    $('wifiStatus').textContent = !net.connected ? 'Not connected' : net.type === 'ethernet' ? 'Ethernet' : 'Connected';
    if (net.connected && net.type === 'ethernet') rows.push({ n: 'Ethernet', cur: true, eth: true });
    var cur = net.connected && net.type === 'wifi' ? (net.ssid || 'Connected network') : null;
    if (cur) rows.push({ n: cur, cur: true, lock: true });
    (list || []).forEach(function (x) {
      if (x.n !== cur && rows.length < 4) rows.push({ n: x.n, lock: !!x.lock });
    });
    rows.push({ n: 'Other network…', other: true });
    S.wifiRows = rows;
    S.wifiIdx = Math.min(S.wifiIdx, rows.length - 1);
    $('wifiList').innerHTML = rows.map(function (r, i) {
      return '<button class="net' + (r.cur ? ' cur' : '') + '" tabindex="-1" data-k="wifi:' + i + '">' +
        svg(r.eth ? ICON.ethernet : ICON.wifi, 32, 1.8) +
        '<span class="nm">' + esc(r.n) + '</span>' +
        '<span class="lock">' + svg(ICON.lock, 26, 1.8, ' style="opacity:' + (r.lock ? 0.7 : 0) + '"') + '</span>' +
        '<span class="tick">' + svg(ICON.tick, 30, 2.2, ' style="opacity:' + (r.cur ? 1 : 0) + '"') + '</span>' +
        '</button>';
    }).join('');
  }

  function openWifi() {
    if (N.hasLocation && !N.hasLocation()) N.askLocation();
    if (N.scanWifi) N.scanWifi();
    S.wifi = true;
    S.wifiIdx = 0;
    buildWifi();
    var pop = $('wifiPop');
    pop.classList.remove('closing');
    void pop.offsetWidth;
    pop.classList.add('open');
    paint();
  }

  function closeWifi() {
    if (!S.wifi) return;
    S.wifi = false;
    var pop = $('wifiPop');
    pop.classList.remove('open');
    pop.classList.add('closing');
    setTimeout(function () { pop.classList.remove('closing'); }, 220);
    paint();
  }

  function wifiKey(k) {
    if (k === 'back' || k === 'menu') return closeWifi();
    if (k === 'up' && S.wifiIdx > 0) S.wifiIdx--;
    else if (k === 'down' && S.wifiIdx < S.wifiRows.length - 1) S.wifiIdx++;
    else if (k === 'ok') {
      var r = S.wifiRows[S.wifiIdx];
      closeWifi();
      if (!r.cur) N.openSettings('wifi'); // joining a network needs its password: hand over to the TV's Wi-Fi settings
      return;
    }
    paint();
  }

  // ------------------------------------------------------- settings screens

  var SROWS = [
    { id: 'network', n: 'Network', d: ICON.wifi },
    { id: 'wallpaper', n: 'Wallpaper', d: 'M3 5h18v14H3z M3 16l5-5 4 4 3-3 6 6 M8.5 9.5h.01', screen: 'wallpaper' },
    { id: 'apps', n: 'Apps on Home', d: 'M4 4h6v6H4z M14 4h6v6h-6z M4 14h6v6H4z M14 14h6v6h-6z', screen: 'apps' },
    { id: 'homekey', n: 'Home button', d: 'M3 11l9-7 9 7 M5 10v10h14V10 M10 20v-6h4v6', home: true },
    { id: 'display', n: 'Display', d: 'M3 4h18v12H3z M8 20h8 M12 16v4', sys: 'display' },
    { id: 'sound', n: 'Sound', d: 'M11 5L6 9H3v6h3l5 4z M15.5 8.5a5 5 0 010 7 M18.5 5.5a9 9 0 010 13', sys: 'sound' },
    { id: 'accessibility', n: 'Accessibility', d: 'M12 4a1.5 1.5 0 100 3 1.5 1.5 0 000-3z M5 9h14 M12 9v5 M9 20l3-6 3 6', sys: 'accessibility' },
    { id: 'about', n: 'About', d: 'M12 21a9 9 0 100-18 9 9 0 000 18z M12 11v5 M12 8h.01', sys: 'about' }
  ];

  function settingsDesc(id) {
    var net = S.net, dev = S.dev;
    switch (id) {
      case 'network':
        if (!net.connected) return 'Not connected. Opens Wi-Fi and Ethernet settings.';
        return (net.type === 'ethernet' ? 'Connected over Ethernet.' : 'Connected to ' + (net.ssid || 'Wi-Fi') + '.') +
          (net.ip ? ' This TV is at ' + net.ip + '.' : '');
      case 'wallpaper': return 'Choose the photo behind Home.';
      case 'apps': return 'Pick the main apps that sit in the Home bar.';
      case 'homekey':
        var hs = J(N.homeStatus ? N.homeStatus() : '{}', {});
        if (hs['default']) return 'TV Home is your home screen. The Home button opens it.';
        return 'Press OK to make TV Home your home screen. If the TV offers no choice, this opens Button Mapper in Google Play: install it and map Home to TV Home.';
      case 'display':
        var ins = S.items.filter(function (it) { return it.kind === 'input'; }).map(function (it) { return it.n; });
        return 'Picture, brightness and HDMI sources.' + (ins.length ? ' This TV has ' + ins.join(', ') + '.' : '');
      case 'sound': return 'Output device and volume.';
      case 'accessibility': return 'Text size, captions and motion.';
      case 'about':
        var name = [dev.brand && dev.brand !== dev.model ? dev.brand : '', dev.model || ''].join(' ').trim();
        var bits = [];
        if (name) bits.push(name);
        if (dev.release) bits.push('Android ' + dev.release);
        if (dev.w && dev.h) bits.push(dev.w + ' × ' + dev.h);
        return (bits.length ? bits.join(', ') + '. ' : '') + 'Device and software details.';
    }
    return '';
  }

  function backHTML(label) {
    return '<div class="sc-back" data-k="back">' + svg(ICON.back, 32, 1.8) + esc(label) + '</div>';
  }
  function bgHTML() {
    return '<div class="sc-bg" id="scBg" style="background-image:url(\'' + wp(S.wall, 'glass') + '\');animation:scFade 420ms ' + EASE + ' both"></div><div class="sc-dim"></div>';
  }

  function openScreen(name, f) {
    if (S.wifi) closeWifi();
    S.screen = { name: name, f: f || 0, col: 'L', r: 0, b: 1, anim: true };
    if (name === 'wallpaper') S.screen.f = wallIndex(S.wall);
    paint();
    renderScreen();
  }

  function closeScreens() {
    S.screen = null;
    $('screens').innerHTML = '';
    paint();
  }

  function renderScreen() {
    var sc = S.screen, h = '';
    if (sc.name === 'settings') {
      h = '<section class="sc">' + bgHTML() + backHTML('Home') +
        '<h1 class="sc-title" style="top:160px">Settings</h1>' +
        '<div class="slist" style="position:absolute;left:140px;top:268px;width:900px">' +
        SROWS.map(function (r, i) {
          return '<div class="srow" data-k="s:' + i + '" style="animation:scSlide 460ms ' + EASE + ' ' + (160 + i * 45) + 'ms both">' +
            svg(r.d, 40, 1.6, ' style="flex:none"') + '<span class="nm">' + esc(r.n) + '</span>' +
            svg(ICON.chev, 32, 2, ' style="opacity:.7"') + '</div>';
        }).join('') + '</div>' +
        '<div class="sc-desc"><div class="dt" id="scDt"></div><div class="dd" id="scDd"></div></div></section>';
    } else if (sc.name === 'wallpaper') {
      h = '<section class="sc">' + bgHTML() + backHTML('Settings') +
        '<h1 class="sc-title" style="top:176px">Wallpaper</h1>' +
        '<div style="position:absolute;left:140px;top:324px;width:760px">' +
        WALLS.map(function (w, i) {
          return '<div class="wrow" data-k="w:' + i + '" style="animation:scSlide 460ms ' + EASE + ' ' + (160 + i * 45) + 'ms both"><span>' + esc(w.n) + '</span>' +
            svg(ICON.tick, 40, 2, ' class="wtick"') + '</div>';
        }).join('') + '</div>' +
        '<div style="position:absolute;left:1020px;top:240px;width:760px;animation:scRise 540ms ' + EASE + ' 300ms both">' +
        '<div class="wprev">' +
        WALLS.map(function (w) { return '<img alt="" data-id="' + w.id + '" src="' + wp(w.id, 'photo') + '">'; }).join('') +
        '<div style="position:absolute;left:0;top:0;width:760px;height:120px;background:linear-gradient(to bottom,rgba(4,10,12,.32),rgba(4,10,12,0))"></div>' +
        '<div style="position:absolute;left:0;top:222px;width:760px;height:206px;background:linear-gradient(to bottom,rgba(4,10,12,0),rgba(4,10,12,.5))"></div>' +
        '<div style="position:absolute;left:0;width:760px;text-align:center;top:30px;font-size:30px;line-height:36px;font-weight:500;letter-spacing:-0.8px;text-shadow:0 1px 3px rgba(0,0,0,.25)">' + esc($('clockText').textContent) + '</div>' +
        '<div aria-hidden="true" style="position:absolute;left:82px;top:268px;width:596px;height:92px;border-radius:19px;background:rgba(8,14,18,.28);box-shadow:inset 0 1px 0 rgba(255,255,255,.24)">' +
        [12, 132, 252, 372, 492].map(function (x, i) {
          return '<div style="position:absolute;left:' + x + 'px;top:16px;width:' + (i === 4 ? 92 : 104) + 'px;height:60px;border-radius:9px;background:rgba(255,255,255,' + (i === 0 ? '.3' : '.14') + ')"></div>';
        }).join('') +
        '</div></div>' +
        '<div id="wStatus" style="margin-top:28px;font-size:28px;line-height:36px;font-weight:500;color:rgba(255,255,255,.84)"></div>' +
        '</div></section>';
    } else if (sc.name === 'apps') {
      var anim = sc.anim;
      var on = S.home.map(function (id) { return S.byId[id]; }).filter(Boolean);
      var off = S.items.filter(function (it) { return S.home.indexOf(it.id) < 0; });
      sc.L = on;
      sc.R = off;
      var row = function (it, i, inner, key, extraDelay) {
        var f = face(it, 'thumb');
        return '<div class="arow" data-row="' + key + '"' + (anim ? ' style="animation:scSlide 460ms ' + EASE + ' ' + (extraDelay + Math.min(i, 8) * 45) + 'ms both"' : '') + '>' +
          '<div class="thumb" style="background:' + f.bg + '">' + f.html + '</div>' +
          '<div class="nm">' + esc(it.n) + '</div>' + inner + '</div>';
      };
      h = '<section class="sc">' + bgHTML() + backHTML('Settings') +
        '<h1 class="sc-title" style="top:156px">Apps on Home</h1>' +
        '<div style="position:absolute;left:140px;top:252px;font-size:30px;line-height:40px;font-weight:500;color:rgba(255,255,255,.84);' + (anim ? 'animation:scRise 500ms ' + EASE + ' 170ms both' : '') + '">' +
        on.length + ' of ' + MAX_HOME + ' on Home. The rest are in All apps.</div>' +
        '<div style="position:absolute;left:140px;top:312px;width:780px">' +
        '<div class="colhead">On Home</div>' +
        on.map(function (it, i) {
          return row(it, i,
            '<button class="sq' + (i === 0 ? ' dim' : '') + '" tabindex="-1" data-k="aL:' + i + ':0" aria-label="Move ' + esc(it.n) + ' up">' + svg(ICON.up, 30, 2) + '</button>' +
            '<button class="pill' + (on.length <= 1 ? ' dim' : '') + '" tabindex="-1" data-k="aL:' + i + ':1" aria-label="Remove ' + esc(it.n) + ' from Home">Remove</button>',
            'L' + i, 200);
        }).join('') + '</div>' +
        '<div style="position:absolute;left:1000px;top:312px;width:780px;height:700px;display:flex;flex-direction:column">' +
        '<div class="colhead">Not on Home</div>' +
        '<div class="alist" id="aList" style="position:relative;flex:1;min-height:0;-webkit-mask-image:linear-gradient(to bottom,#000 calc(100% - 80px),rgba(0,0,0,.15));mask-image:linear-gradient(to bottom,#000 calc(100% - 80px),rgba(0,0,0,.15))">' +
        off.map(function (it, i) {
          return row(it, i,
            '<button class="pill' + (on.length >= MAX_HOME ? ' dim' : '') + '" tabindex="-1" data-k="aR:' + i + '" aria-label="Add ' + esc(it.n) + ' to Home">Add</button>',
            'R' + i, 240);
        }).join('') + '<div style="height:90px"></div></div></div></section>';
      sc.anim = false;
    }
    $('screens').innerHTML = h;
    paintScreen();
  }

  function paintScreen() {
    var sc = S.screen;
    if (!sc) return;
    [].forEach.call(document.querySelectorAll('#screens .on'), function (el) { el.classList.remove('on'); });
    if (sc.name === 'settings') {
      var el = q('#screens [data-k="s:' + sc.f + '"]');
      if (el) el.classList.add('on');
      $('scDt').textContent = SROWS[sc.f].n;
      $('scDd').textContent = settingsDesc(SROWS[sc.f].id);
    } else if (sc.name === 'wallpaper') {
      var applied = wallIndex(S.wall);
      [].forEach.call(document.querySelectorAll('#screens .wrow'), function (r, i) {
        r.classList.toggle('on', i === sc.f);
        r.querySelector('.wtick').style.opacity = i === applied ? 1 : 0;
      });
      [].forEach.call(document.querySelectorAll('#screens .wprev img'), function (img, i) {
        img.classList.toggle('cur', i === sc.f);
      });
      $('wStatus').textContent = sc.f === applied ? 'Applied' : 'Preview. Press OK to apply';
      $('scBg').style.backgroundImage = "url('" + wp(S.wall, 'glass') + "')";
    } else if (sc.name === 'apps') {
      if (sc.col === 'R' && !sc.R.length) { sc.col = 'L'; sc.b = 1; }
      if (sc.col === 'L' && !sc.L.length) sc.col = 'R';
      var list = sc.col === 'L' ? sc.L : sc.R;
      sc.r = Math.max(0, Math.min(sc.r, list.length - 1));
      var key = sc.col === 'L' ? 'aL:' + sc.r + ':' + sc.b : 'aR:' + sc.r;
      var btn = q('#screens [data-k="' + key + '"]'), rowEl = q('#screens [data-row="' + sc.col + sc.r + '"]');
      if (btn) btn.classList.add('on');
      if (rowEl) rowEl.classList.add('on');
      if (sc.col === 'R' && rowEl) {
        var box = $('aList'), t = rowEl.offsetTop, hgt = box.clientHeight;
        if (t < box.scrollTop) box.scrollTop = t;
        else if (t + 80 > box.scrollTop + hgt - 80) box.scrollTop = t + 80 - hgt + 80;
      }
    }
  }

  function screenBack() {
    var n = S.screen.name;
    if (n === 'settings') closeScreens();
    else openScreen('settings', n === 'wallpaper' ? 1 : 2);
  }

  function screenKey(k) {
    var sc = S.screen;
    if (k === 'back') return screenBack();
    if (k === 'menu') return closeScreens();
    if (sc.name === 'settings') {
      if (k === 'up' && sc.f > 0) sc.f--;
      else if (k === 'down' && sc.f < SROWS.length - 1) sc.f++;
      else if (k === 'ok') {
        var r = SROWS[sc.f];
        if (r.screen) return openScreen(r.screen);
        if (r.home) { if (N.makeDefaultHome) N.makeDefaultHome(); return; }
        N.openSettings(r.sys || 'wifi');
        return;
      }
      return paintScreen();
    }
    if (sc.name === 'wallpaper') {
      if (k === 'up' && sc.f > 0) sc.f--;
      else if (k === 'down' && sc.f < WALLS.length - 1) sc.f++;
      else if (k === 'ok') {
        S.wall = WALLS[sc.f].id;
        N.set('wallpaper', S.wall);
        applyWall();
      }
      return paintScreen();
    }
    if (sc.name === 'apps') return appsKey(k);
  }

  function appsKey(k) {
    var sc = S.screen, L = sc.L, R = sc.R;
    if (k === 'up') sc.r = Math.max(0, sc.r - 1);
    else if (k === 'down') sc.r = Math.min((sc.col === 'L' ? L : R).length - 1, sc.r + 1);
    else if (k === 'left') {
      if (sc.col === 'R' && L.length) { sc.col = 'L'; sc.b = 1; sc.r = Math.min(sc.r, L.length - 1); }
      else if (sc.col === 'L' && sc.b === 1) sc.b = 0;
    } else if (k === 'right') {
      if (sc.col === 'L' && sc.b === 0) sc.b = 1;
      else if (sc.col === 'L' && R.length) { sc.col = 'R'; sc.r = Math.min(sc.r, R.length - 1); }
    } else if (k === 'ok') {
      var changed = false;
      if (sc.col === 'L' && sc.b === 0 && sc.r > 0) {
        var h = S.home.slice(), t = h[sc.r - 1];
        h[sc.r - 1] = h[sc.r]; h[sc.r] = t;
        S.home = h; sc.r--; changed = true;
      } else if (sc.col === 'L' && sc.b === 1 && L.length > 1) {
        S.home = S.home.filter(function (id) { return id !== L[sc.r].id; });
        changed = true;
      } else if (sc.col === 'R' && L.length < MAX_HOME && R[sc.r]) {
        S.home = S.home.concat([R[sc.r].id]);
        changed = true;
      }
      if (changed) {
        saveHome();
        renderHome();
        var keepScroll = $('aList') ? $('aList').scrollTop : 0;
        renderScreen();
        if ($('aList')) $('aList').scrollTop = keepScroll;
        return;
      }
    }
    paintScreen();
  }

  // ------------------------------------------------------------ input keys

  function onKey(k) {
    if (S.screen) return screenKey(k);
    if (S.wifi) return wifiKey(k);
    if (k === 'back') { if (S.view === 'apps') goHome(); return; }
    if (k === 'menu') return openScreen('apps');

    if (S.zone === 'dock') {
      var n = S.dock.length;
      if (k === 'left') { if (S.dockIdx > 0) S.dockIdx--; }
      else if (k === 'right') { if (S.dockIdx < n - 1) S.dockIdx++; }
      else if (k === 'up') S.zone = 'top';
      else if (k === 'down') return goApps(Math.min(S.dockIdx, COLS - 1));
      else if (k === 'ok') return launch(S.dock[S.dockIdx]);
    } else if (S.zone === 'top') {
      if (k === 'left') S.topIdx = 0;
      else if (k === 'right') S.topIdx = 1;
      else if (k === 'down') {
        if (S.view === 'apps') S.zone = 'grid';
        else if (S.dock.length) S.zone = 'dock';
        else return goApps(0);
      } else if (k === 'ok') return S.topIdx === 0 ? openWifi() : openScreen('settings');
    } else if (S.zone === 'grid') {
      var g = S.grid.length, i = S.gridIdx, c = i % COLS;
      if (k === 'left') { if (c > 0) i--; }
      else if (k === 'right') { if (c < COLS - 1 && i + 1 < g) i++; }
      else if (k === 'up') { if (i >= COLS) i -= COLS; else return goHome(); }
      else if (k === 'down') {
        if (i + COLS < g) i += COLS;
        else if (Math.floor(i / COLS) < Math.floor((g - 1) / COLS)) i = g - 1;
      } else if (k === 'ok') return launch(S.grid[i]);
      S.gridIdx = i;
    }
    paint();
  }

  var KEYS = { 37: 'left', 38: 'up', 39: 'right', 40: 'down', 13: 'ok', 32: 'ok', 27: 'back', 8: 'back', 77: 'menu', 93: 'menu' };
  document.addEventListener('keydown', function (e) {
    var k = KEYS[e.keyCode] || (e.key === 'Enter' ? 'ok' : null);
    if (!k) return;
    e.preventDefault();
    onKey(k);
  });

  // Pointer (air-mouse remotes, preview in a browser): hover moves focus, click selects.
  function pointTo(k) {
    var p = k.split(':'), i = Number(p[1]);
    switch (p[0]) {
      case 'dock': if (S.view !== 'home') return false; S.zone = 'dock'; S.dockIdx = i; break;
      case 'grid': if (S.view !== 'apps') return false; S.zone = 'grid'; S.gridIdx = i; break;
      case 'top': S.zone = 'top'; S.topIdx = i; break;
      case 'wifi': S.wifiIdx = i; break;
      case 's': case 'w': S.screen.f = i; break;
      case 'aL': S.screen.col = 'L'; S.screen.r = i; S.screen.b = Number(p[2]); break;
      case 'aR': S.screen.col = 'R'; S.screen.r = i; break;
      default: return false;
    }
    return true;
  }
  document.addEventListener('mouseover', function (e) {
    var t = e.target.closest && e.target.closest('[data-k]');
    if (!t) return;
    if (pointTo(t.getAttribute('data-k'))) { if (S.screen) paintScreen(); else paint(); }
  });
  document.addEventListener('click', function (e) {
    var t = e.target.closest && e.target.closest('[data-k]');
    if (!t) {
      if (S.wifi && !e.target.closest('#wifiPop')) closeWifi();
      return;
    }
    var k = t.getAttribute('data-k');
    if (k === 'back') return onKey('back');
    if (k === 'cue:down') return goApps(0);
    if (k === 'cue:up') return goHome();
    if (k.indexOf('top:') === 0 && S.wifi) { closeWifi(); if (k === 'top:0') return; }
    if (pointTo(k)) onKey('ok');
  });
  document.addEventListener('wheel', function (e) {
    if (S.screen || S.wifi) return;
    if (e.deltaY > 12 && S.view === 'home') goApps(0);
    else if (e.deltaY < -12 && S.view === 'apps' && !S.gridTop) goHome();
  }, { passive: true });

  // ----------------------------------------------------- native callbacks

  window.TV = {
    back: function () { onKey('back'); },
    menu: function () { onKey('menu'); },
    home: function () {
      if (S.wifi) closeWifi();
      if (S.screen) closeScreens();
      if (S.view === 'apps') setView('home', true);
      S.zone = S.dock.length ? 'dock' : 'top';
      paint();
    },
    resumed: function () { tick(); TV.netChanged(); maybeAskHome(); },
    appsChanged: function () {
      loadItems();
      loadHome();
      renderHome();
      if (S.screen && S.screen.name === 'apps') renderScreen();
    },
    netChanged: function () {
      setNetGlyph();
      if (S.wifi) buildWifi(), paint();
      if (S.screen && S.screen.name === 'settings') paintScreen();
    }
  };

  /* First run only: if TV Home isn't the Home button yet, open Settings on the Home button row. */
  function maybeAskHome() {
    if (!window.Native || !N.homeStatus || N.get('homeAsked')) return;
    var hs = J(N.homeStatus(), {});
    if (hs['default']) return;
    N.set('homeAsked', '1');
    for (var i = 0; i < SROWS.length; i++) if (SROWS[i].home) return openScreen('settings', i);
  }

  // ------------------------------------------------------------------ fit

  function fit() {
    var w = window.innerWidth, h = window.innerHeight, s = Math.min(w / 1920, h / 1080), st = $('stage').style;
    st.transform = 'scale(' + s + ')';
    st.left = Math.round((w - 1920 * s) / 2) + 'px';
    st.top = Math.round((h - 1080 * s) / 2) + 'px';
  }
  window.addEventListener('resize', fit);

  // ----------------------------------------------------------------- boot

  function boot() {
    fit();
    S.dev = J(N.device(), {});
    S.wall = wallById(N.get('wallpaper') || 'misty').id;
    buildWallpapers();
    applyWall();
    tick();
    loadItems();
    loadHome();
    renderHome();
    setNetGlyph();
    maybeAskHome();

    // Preview helpers: ?view=apps, ?screen=settings|wallpaper|apps, ?wifi=1
    var qs = location.search;
    if (!window.Native && qs) {
      var m = /screen=(\w+)/.exec(qs);
      if (/view=apps/.test(qs)) { setView('apps', true); S.zone = 'grid'; S.gridIdx = 6; paint(); }
      if (/wifi=1/.test(qs)) openWifi();
      if (m) openScreen(m[1]);
    }
  }

  // ------------------------------------------------------------------ mock

  function mockNative() {
    var store = {};
    var apps = [
      ['com.google.android.youtube.tv', 'YouTube'], ['com.netflix.ninja', 'Netflix'], ['com.spotify.tv.android', 'Spotify'],
      ['com.dstv.now.android', 'DStv'], ['com.amazon.amazonvideo.livingroom', 'Prime Video'], ['com.disney.disneyplus', 'Disney+'],
      ['com.tubitv', 'Tubi'], ['com.plexapp.android', 'Plex'], ['org.videolan.vlc', 'VLC'], ['tv.twitch.android.app', 'Twitch'],
      ['com.android.vending', 'Google Play Store'], ['com.android.tv.settings', 'Settings'], ['com.google.android.tv', 'Live Channels'],
      ['com.showmax.app', 'Showmax']
    ].map(function (a) { return { pkg: a[0], cls: a[0] + '.Main', n: a[1], tv: true }; });
    apps[10].icon = 'data:image/svg+xml,' + encodeURIComponent('<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 48 48"><path d="M8 4l24 20L8 44z" fill="#34a853"/><path d="M8 4l30 17-6 3z" fill="#4285f4"/><path d="M8 44l30-17-6-3z" fill="#ea4335"/><path d="M32 24l6-3 6 3-6 3z" fill="#fbbc04"/></svg>');
    return {
      apps: function () { return JSON.stringify(apps); },
      inputs: function () { return JSON.stringify([{ id: 'hdmi1', n: 'HDMI 1', type: 'hdmi' }, { id: 'hdmi2', n: 'HDMI 2', type: 'hdmi' }, { id: 'av', n: 'AV', type: 'av' }]); },
      launch: function (p) { console.log('launch', p); },
      openInput: function (i) { console.log('input', i); },
      openSettings: function (w) { console.log('settings', w); },
      network: function () { return JSON.stringify({ connected: true, type: 'wifi', ssid: 'ALHN-1007', ip: '192.168.1.23', level: 3, location: true }); },
      wifiList: function () { return JSON.stringify([{ n: 'ALHN-1007', lock: true }, { n: 'ALHN-1007-5G', lock: true }, { n: 'Guest', lock: false }]); },
      scanWifi: function () {},
      hasLocation: function () { return true; },
      askLocation: function () {},
      device: function () { return JSON.stringify({ brand: 'Sharp', model: 'SWTV-20AE-PRO', release: '11', sdk: 30, w: 1920, h: 1080 }); },
      homeStatus: function () { return JSON.stringify({ 'default': false, service: false }); },
      makeDefaultHome: function () { console.log('make default home'); },
      get: function (k) { return store[k] || null; },
      set: function (k, v) { store[k] = v; }
    };
  }

  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', boot);
  else boot();
})();
