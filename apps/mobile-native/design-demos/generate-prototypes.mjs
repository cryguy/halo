import { readFile, writeFile } from 'node:fs/promises'
import { dirname, join, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const here = dirname(fileURLToPath(import.meta.url))
const repoRoot = resolve(here, '../../..')

const remoteArt = {
  matrixPoster: 'https://images.metahub.space/poster/medium/tt0133093/img',
  matrixBackground: 'https://images.metahub.space/background/medium/tt0133093/img',
  inceptionPoster: 'https://images.metahub.space/poster/medium/tt1375666/img',
  darkKnightPoster: 'https://images.metahub.space/poster/medium/tt0468569/img',
  godfatherPoster: 'https://images.metahub.space/poster/medium/tt0068646/img',
  breakingBadPoster: 'https://images.metahub.space/poster/medium/tt0903747/img',
  breakingBadBackground: 'https://images.metahub.space/background/medium/tt0903747/img',
  avatarPoster: 'https://images.metahub.space/poster/medium/tt0417299/img',
}

async function remoteDataUrl(url) {
  const response = await fetch(url)
  if (!response.ok) throw new Error(`Failed to fetch ${url}: ${response.status}`)
  const bytes = Buffer.from(await response.arrayBuffer())
  const mime = response.headers.get('content-type')?.split(';')[0] || 'image/jpeg'
  return `data:${mime};base64,${bytes.toString('base64')}`
}

async function localDataUrl(path, mime) {
  const bytes = await readFile(path)
  return `data:${mime};base64,${bytes.toString('base64')}`
}

const art = Object.fromEntries(
  await Promise.all(Object.entries(remoteArt).map(async ([key, url]) => [key, await remoteDataUrl(url)])),
)
art.haloMark = await localDataUrl(
  join(repoRoot, 'apps/mobile/assets/android-icon-foreground.png'),
  'image/png',
)
const selectedDirection = {
  key: 'strict',
  file: 'Halo Android Direction A - Strict Parity.html',
  title: 'Direction A: Strict parity',
  subtitle: 'The original Expo layout, transferred literally into a native Android frame.',
  rationale: 'Best fit for “copy the original.” Geometry, hierarchy, action roles, and glass chrome remain visibly unchanged.',
  reference: 'Reference: Halo origin/main at 41a15c8',
}

await writeFile(join(here, selectedDirection.file), renderDocument(selectedDirection), 'utf8')
process.stdout.write(`Generated ${selectedDirection.file}\n`)

function renderDocument(direction) {
  return `<!doctype html>
<html lang="en">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>${direction.title}</title>
  <!--
    Assumptions:
    - origin/main is the visual source of truth.
    - This board compares layout interpretation, not feature scope.
    - Home, Search, Detail, Library, Sources, and Settings expose the load-bearing system.
    - Login, Downloads, Player, and sheets remain required after direction approval.
    Reasoning:
    - Real fixture artwork is embedded because poster imagery carries Halo's identity.
    - Every phone is an independent state machine so the board is both an overview and a prototype.
    - Production Kotlin UI is intentionally untouched at this gate.
  -->
  <style>${baseCss()}${variantCss(direction.key)}</style>
</head>
<body class="variant-${direction.key}">
  <svg aria-hidden="true" class="svg-defs">${svgSymbols()}</svg>
  <main class="board">
    <header class="board-head">
      <div class="board-copy">
        <div class="eyebrow">HALO NATIVE REWRITE · DESIGN DIRECTION</div>
        <h1>${direction.title}</h1>
        <p class="board-subtitle">${direction.subtitle}</p>
        <p class="board-rationale">${direction.rationale}</p>
      </div>
      <div class="board-meta">
        <div class="halo-lockup"><img src="${art.haloMark}" alt="Halo mark"><span>halo</span></div>
        <div class="palette" aria-label="Halo palette">
          <span style="--swatch:#0a0c11"></span><span style="--swatch:#14161d"></span><span style="--swatch:#f4f6fb"></span><span style="--swatch:#0a84ff"></span><span style="--swatch:#5dd39e"></span>
        </div>
        <p>${direction.reference}</p>
      </div>
    </header>
    <section class="phone-grid" id="phone-grid"></section>
  </main>
  <script>
    const ART = ${JSON.stringify(art)};
    const DIRECTION = ${JSON.stringify(direction.key)};
    const PHONE_SPECS = [
      ['home', '01 · Discovery'],
      ['search', '02 · Intent'],
      ['detail', '03 · Evaluation'],
      ['library', '04 · Ownership'],
      ['sources', '05 · Source choice'],
      ['settings', '06 · Configuration'],
    ];

    const grid = document.getElementById('phone-grid');
    grid.innerHTML = PHONE_SPECS.map(([initial, label], index) => phone(initial, label, index)).join('');
    document.querySelectorAll('.phone').forEach((element) => activate(element, element.dataset.initial));

    function phone(initial, label, index) {
      return '<article class="phone" data-initial="' + initial + '">' +
        '<div class="phone-label"><span>' + label + '</span><b>' + screenTitle(initial) + '</b></div>' +
        '<div class="android-shell">' +
          '<div class="android-screen">' +
            '<div class="status-bar"><span>9:41</span><span class="status-icons">' + icon('signal') + icon('wifi') + '<span class="battery">85</span></span></div>' +
            '<div class="punch-hole"></div>' +
            '<div class="app-viewport">' + screens(initial, index) + navigation() + '<div class="toast" role="status"></div><div class="sheet-layer"></div></div>' +
            '<div class="system-nav"><span></span></div>' +
          '</div>' +
        '</div>' +
      '</article>';
    }

    function screens(initial, index) {
      const names = ['home', 'search', 'detail', 'library', 'downloads', 'sources', 'settings'];
      return names.map((name) => '<section class="app-screen screen-' + name + '" data-screen="' + name + '">' + renderScreen(name, index) + '</section>').join('');
    }

    function renderScreen(name) {
      return strictScreen(name);
    }

    function strictScreen(name) {
      if (name === 'home') return strictHome();
      if (name === 'search') return strictSearch();
      if (name === 'detail') return strictDetail();
      if (name === 'library') return strictLibrary();
      if (name === 'downloads') return downloadsScreen('strict');
      if (name === 'sources') return strictSources();
      if (name === 'settings') return strictSettings();
      return '';
    }


    function strictHome() {
      return '<div class="scroll home-scroll">' +
        '<header class="screen-head"><h2>Watch</h2>' + searchButton() + segmented() + '</header>' +
        heroCard('The Matrix', ART.matrixBackground, '1999 · Science Fiction', '8.7') +
        mediaRail('Continue Watching', [poster('breakingBadPoster', 'Breaking Bad', true), poster('inceptionPoster', 'Inception'), poster('avatarPoster', 'Avatar')]) +
        mediaRail('Popular · Movies', [poster('matrixPoster', 'The Matrix'), poster('darkKnightPoster', 'The Dark Knight'), poster('godfatherPoster', 'The Godfather')]) +
      '</div>';
    }

    function strictSearch() {
      return '<div class="search-top">' + searchInput('matrix') + '<button class="text-action" data-go="home">Cancel</button></div>' +
        '<div class="scroll search-results">' +
          mediaRail('Fixture Catalogs · Movies', [poster('matrixPoster', 'The Matrix', false, true), poster('inceptionPoster', 'Inception', false, true), poster('darkKnightPoster', 'The Dark Knight', false, true)]) +
          mediaRail('Fixture Catalogs · Series', [poster('breakingBadPoster', 'Breaking Bad', false, true), poster('avatarPoster', 'Avatar', false, true)]) +
        '</div>';
    }

    function strictDetail() {
      return '<div class="scroll detail-scroll">' + detailHero('Breaking Bad', ART.breakingBadBackground, '2008-2013 · 5 seasons', '9.5') +
        '<div class="detail-body"><div class="detail-actions"><button class="primary wide" data-go="sources">' + icon('play') + 'Sources</button><button class="icon-action" data-toast="Added to Library">' + icon('bookmark') + '</button></div>' +
        '<p class="synopsis">A chemistry teacher facing a terminal diagnosis turns to manufacturing with a former student, changing both of their lives.</p>' +
        seasonButton() + episodeList() + '</div></div>' + backButton('home');
    }

    function strictLibrary() {
      return '<div class="scroll tabbed"><header class="screen-head"><h2>Library</h2>' + segmented() + '</header>' +
        '<div class="poster-grid strict-grid">' + libraryPosters(false) + '</div></div>';
    }

    function strictSources() {
      return '<div class="modal-head">' + backButtonInline('detail') + '<h3>Sources</h3><span></span></div><div class="scroll source-scroll">' +
        '<div class="group-label">FIXTURE STREAMS</div><div class="source-card">' + sourceRows('compact') + '</div>' +
        '<div class="group-label">FIXTURE CLOUD</div><div class="source-card">' + sourceRow('Cloud copy', '1080p · H.264 · Cached', '1.8 GB', 'compact') + '</div>' +
      '</div>';
    }

    function strictSettings() {
      return '<div class="scroll tabbed settings-scroll"><header class="screen-head"><h2>Settings</h2></header>' +
        settingsAddons('cards') + settingsPlayback('cards') + settingsServer('cards') + signOut() + '</div>';
    }


    function downloadsScreen(mode) {
      return '<div class="scroll tabbed downloads-screen"><header class="screen-head"><h2>Downloads</h2><p class="muted">2 items · 3.6 GB on device</p></header>' +
        '<div class="download-show"><img src="' + ART.breakingBadPoster + '"><div><h3>Breaking Bad</h3><p>2 downloads · 3.6 GB</p></div></div>' +
        '<button class="download-row"><span><b>S01E01</b><small>Downloaded · 1.8 GB</small></span>' + icon('play') + '</button>' +
        '<button class="download-row"><span><b>S01E02</b><small>Downloaded · 1.8 GB</small></span>' + icon('play') + '</button></div>';
    }

    function heroCard(title, background, meta, rating) {
      return '<div class="hero-card" style="background-image:linear-gradient(180deg,rgba(10,12,17,.05),rgba(10,12,17,.92)),url(' + cssUrl(background) + ')" data-go="detail">' +
        '<div><h3>' + title + '</h3><p>' + meta + ' · <span>★ ' + rating + '</span></p><button class="primary" data-go="sources">' + icon('play') + 'Play</button></div></div>';
    }

    function detailHero(title, background, meta, rating) {
      return '<div class="detail-hero" style="background-image:linear-gradient(180deg,rgba(10,12,17,.08),rgba(10,12,17,.82) 78%,#0a0c11),url(' + cssUrl(background) + ')"><div><h2>' + title + '</h2><p>' + meta + ' · <span>★ ' + rating + '</span></p></div></div>';
    }

    function searchButton() {
      return '<button class="search-field" data-go="search">' + icon('search') + '<span>Search movies, series...</span></button>';
    }

    function searchInput(value) {
      return '<label class="search-field input-field">' + icon('search') + '<input value="' + value + '" aria-label="Search"><button data-clear-input>' + icon('close') + '</button></label>';
    }

    function segmented(mode = 'track') {
      return '<div class="segmented ' + mode + '"><button class="selected">All</button><button>Movies</button><button>Series</button></div>';
    }

    function mediaRail(title, items) {
      return '<section class="media-section"><h3>' + title + '</h3><div class="media-rail">' + items.join('') + '</div></section>';
    }

    function poster(key, title, progress = false, label = false) {
      return '<button class="poster-card" data-go="detail"><span class="poster-image"><img src="' + ART[key] + '" alt="' + title + ' poster">' +
        (progress ? '<i class="progress"><b style="width:62%"></b></i>' : '') + '</span>' + (label ? '<span class="poster-label">' + title + '</span>' : '') + '</button>';
    }

    function libraryPosters(labels) {
      return [
        poster('matrixPoster', 'The Matrix', false, labels),
        poster('breakingBadPoster', 'Breaking Bad', false, labels),
        poster('inceptionPoster', 'Inception', false, labels),
        poster('darkKnightPoster', 'The Dark Knight', false, labels),
        poster('godfatherPoster', 'The Godfather', false, labels),
        poster('avatarPoster', 'Avatar', false, labels),
      ].join('');
    }

    function episodeList() {
      return '<div class="episodes">' + episode('1', 'Pilot', '58 min', '35%') + episode('2', "Cat's in the Bag...", '48 min', '') + episode('3', "...And the Bag's in the River", '48 min', '') + '</div>';
    }

    function episode(number, title, duration, progress) {
      return '<button class="episode" data-go="sources"><span class="episode-thumb" style="background-image:url(' + cssUrl(ART.breakingBadBackground) + ')"><i>' + icon('play') + '</i></span><span class="episode-copy"><b>' + number + '. ' + title + '</b><small>' + duration + '</small>' +
        (progress ? '<i class="episode-progress"><em style="width:' + progress + '"></em></i>' : '') + '</span></button>';
    }

    function seasonButton() {
      return '<button class="season-button" data-sheet="season"><span>Season 1</span>' + icon('chevron-down') + '</button>';
    }

    function sourceRows(mode) {
      return sourceRow('Fixture · 4K HDR', '2160p · HEVC · Dolby Vision', '12.4 GB', mode) +
        sourceRow('Fixture · 1080p', '1080p · H.264 · AAC', '3.2 GB', mode) +
        sourceRow('Fixture · Torrent', 'Debrid required · 23 seeders', '8.7 GB', mode);
    }

    function sourceRow(name, detail, size, mode) {
      if (mode === 'tiles') {
        return '<article class="source-tile"><button class="source-main" data-toast="Ready to play ' + name + '"><span class="quality-chip">' + name.split('·').pop().trim() + '</span><b>' + name.split('·')[0].trim() + '</b><small>' + detail + '</small><em>' + size + '</em></button><button class="download-action" data-download>' + icon('download') + '</button></article>';
      }
      if (mode === 'editorial') {
        return '<article class="source-editorial"><button data-toast="Ready to play ' + name + '"><span class="source-number">0' + (name.includes('4K') ? '1' : name.includes('1080') ? '2' : '3') + '</span><span><b>' + name + '</b><small>' + detail + '</small><code>' + size + '</code></span>' + icon('chevron-right') + '</button><button class="download-action" data-download>' + icon('download') + '</button></article>';
      }
      return '<div class="source-row"><button class="source-main" data-toast="Ready to play ' + name + '"><span><b>' + name + '</b><small>' + detail + '</small><code>' + size + '</code></span></button><button class="download-action" data-download>' + icon('download') + '</button></div>';
    }

    function settingsAddons(mode) {
      return '<section class="settings-group ' + mode + '"><h3>Addons</h3><div class="settings-block">' +
        settingRow(icon('grid'), 'Fixture Catalogs', 'v1.0 · Movies and series') +
        settingRow(icon('play-box'), 'Fixture Streams', 'v1.0 · Playback sources') +
        settingRow(icon('cloud'), 'Fixture Cloud', 'v1.0 · Personal catalog') +
      '</div></section>';
    }

    function settingsPlayback(mode) {
      return '<section class="settings-group ' + mode + '"><h3>Playback</h3><div class="settings-block">' +
        settingValueRow('Default audio language', 'Auto') + settingValueRow('Default subtitles', 'Off') +
        '<button class="setting-row" data-toggle><span>Autoplay next episode</span><i class="switch on"><b></b></i></button>' +
      '</div></section>';
    }

    function settingsServer(mode) {
      return '<section class="settings-group ' + mode + '"><h3>Server</h3><div class="settings-block">' +
        settingValueRow('Server', '127.0.0.1:18790') + '<div class="setting-row"><span>Status</span><strong class="connected"><i></i>Connected</strong></div>' +
      '</div></section>';
    }

    function settingRow(leading, title, detail) {
      return '<button class="setting-row addon-setting" data-toast="' + title + ' settings"><i class="setting-leading">' + leading + '</i><span><b>' + title + '</b><small>' + detail + '</small></span>' + icon('chevron-right') + '</button>';
    }

    function settingValueRow(title, value) {
      return '<button class="setting-row" data-toast="' + title + '"><span>' + title + '</span><strong>' + value + ' ' + icon('chevron-right') + '</strong></button>';
    }

    function signOut() {
      return '<button class="sign-out" data-toast="Sign out requires confirmation">Sign Out</button>';
    }

    function resultRow(key, title, meta, rating) {
      return '<button class="result-row" data-go="detail"><img src="' + ART[key] + '"><span><b>' + title + '</b><small>' + meta + '</small><em>★ ' + rating + '</em></span>' + icon('chevron-right') + '</button>';
    }

    function navigation() {
      return '<nav class="app-tabs" aria-label="Primary"><button data-tab="home">' + icon('home') + '<span>Home</span></button><button data-tab="library">' + icon('bookmark') + '<span>Library</span></button><button data-tab="downloads">' + icon('download') + '<span>Downloads</span></button><button data-tab="settings">' + icon('settings') + '<span>Settings</span></button></nav>';
    }

    function backButton(target) {
      return '<button class="back-float" data-go="' + target + '">' + icon('chevron-left') + '</button>';
    }

    function backButtonInline(target) {
      return '<button class="back-inline" data-go="' + target + '">' + icon('chevron-left') + '</button>';
    }

    function screenTitle(name) {
      return ({home:'Home',search:'Search',detail:'Series detail',library:'Library',sources:'Sources',settings:'Settings'})[name] || name;
    }

    function activate(phone, target) {
      phone.querySelectorAll('[data-screen]').forEach((screen) => screen.classList.toggle('active', screen.dataset.screen === target));
      phone.dataset.current = target;
      const tabs = ['home', 'library', 'downloads', 'settings'];
      phone.querySelector('.app-tabs').classList.toggle('visible', tabs.includes(target));
      phone.querySelectorAll('[data-tab]').forEach((tab) => tab.classList.toggle('active', tab.dataset.tab === target));
      phone.querySelector('.sheet-layer').innerHTML = '';
    }

    function showToast(phone, message) {
      const toast = phone.querySelector('.toast');
      toast.textContent = message;
      toast.classList.add('show');
      clearTimeout(phone.toastTimer);
      phone.toastTimer = setTimeout(() => toast.classList.remove('show'), 1500);
    }

    function showSeasonSheet(phone) {
      const layer = phone.querySelector('.sheet-layer');
      layer.innerHTML = '<button class="sheet-scrim" data-close-sheet aria-label="Close season selector"></button><div class="bottom-sheet"><i class="grabber"></i><h3>Season</h3>' +
        ['Season 1','Season 2','Season 3','Season 4','Season 5','Specials'].map((label, index) => '<button data-season="' + label + '" class="' + (index === 0 ? 'selected' : '') + '"><span>' + label + '</span>' + (index === 0 ? icon('check') : '') + '</button>').join('') + '</div>';
    }

    grid.addEventListener('click', (event) => {
      const phone = event.target.closest('.phone');
      if (!phone) return;
      const clear = event.target.closest('[data-clear-input]');
      if (clear) {
        event.preventDefault();
        const input = clear.parentElement.querySelector('input');
        if (input) input.value = '';
        return;
      }
      const nav = event.target.closest('[data-go], [data-tab]');
      if (nav) {
        event.preventDefault();
        activate(phone, nav.dataset.go || nav.dataset.tab);
        return;
      }
      const sheet = event.target.closest('[data-sheet]');
      if (sheet) {
        event.preventDefault();
        showSeasonSheet(phone);
        return;
      }
      const closeSheet = event.target.closest('[data-close-sheet]');
      if (closeSheet) {
        phone.querySelector('.sheet-layer').innerHTML = '';
        return;
      }
      const season = event.target.closest('[data-season]');
      if (season) {
        phone.querySelector('.sheet-layer').innerHTML = '';
        const activeScreen = phone.querySelector('[data-screen].active');
        const label = activeScreen.querySelector('.season-button span');
        if (label) label.textContent = season.dataset.season;
        showToast(phone, season.dataset.season + ' selected');
        return;
      }
      const toggle = event.target.closest('[data-toggle]');
      if (toggle) {
        const control = toggle.querySelector('.switch');
        control.classList.toggle('on');
        showToast(phone, control.classList.contains('on') ? 'Autoplay on' : 'Autoplay off');
        return;
      }
      const download = event.target.closest('[data-download]');
      if (download) {
        download.classList.toggle('done');
        download.innerHTML = download.classList.contains('done') ? icon('check') : icon('download');
        showToast(phone, download.classList.contains('done') ? 'Download started' : 'Download removed');
        return;
      }
      const toastTarget = event.target.closest('[data-toast]');
      if (toastTarget) showToast(phone, toastTarget.dataset.toast);
    });

    window.__ready = true;

    function cssUrl(value) { return value; }
    function icon(name) { return '<svg class="icon" aria-hidden="true"><use href="#i-' + name + '"></use></svg>'; }
  </script>
</body>
</html>`
}

function svgSymbols() {
  return `
    <symbol id="i-home" viewBox="0 0 24 24"><path d="M3 10.8 12 3l9 7.8v9.7a.5.5 0 0 1-.5.5h-5.2v-6.4H8.7V21H3.5a.5.5 0 0 1-.5-.5z"/></symbol>
    <symbol id="i-bookmark" viewBox="0 0 24 24"><path d="M6 4.5A1.5 1.5 0 0 1 7.5 3h9A1.5 1.5 0 0 1 18 4.5V21l-6-3.7L6 21z"/></symbol>
    <symbol id="i-download" viewBox="0 0 24 24"><path d="M12 3v12m-5-5 5 5 5-5M5 20h14"/></symbol>
    <symbol id="i-settings" viewBox="0 0 24 24"><path d="M12 15.2a3.2 3.2 0 1 0 0-6.4 3.2 3.2 0 0 0 0 6.4Z"/><path d="m19.4 15 .1 2.4-2.1 1.2-2-1.3-1.5.8-.2 2.4H11l-1.2-2.1-1.7-.5-1.8 1.5-2.1-1.2.1-2.4-1.2-1.3-2.3.4v-2.4l2.2-1 .4-1.7-1.6-1.7L3 6.4l2.4.1 1.3-1.2L6.3 3h2.4l1 2.2 1.7.4L13.1 4l2.1 1.2-.1 2.4 1.2 1.3 2.3-.4 1.2 2.1-1.5 1.8.5 1.7Z"/></symbol>
    <symbol id="i-search" viewBox="0 0 24 24"><circle cx="10.8" cy="10.8" r="6.8"/><path d="m16 16 5 5"/></symbol>
    <symbol id="i-play" viewBox="0 0 24 24"><path d="m8 5 11 7-11 7z"/></symbol>
    <symbol id="i-play-box" viewBox="0 0 24 24"><rect x="3" y="4" width="18" height="16" rx="3"/><path d="m10 9 5 3-5 3z"/></symbol>
    <symbol id="i-cloud" viewBox="0 0 24 24"><path d="M7.2 18h10.3a4 4 0 0 0 .5-8 6.2 6.2 0 0 0-11.8 1.2A3.4 3.4 0 0 0 7.2 18Z"/></symbol>
    <symbol id="i-grid" viewBox="0 0 24 24"><rect x="4" y="4" width="6" height="6" rx="1"/><rect x="14" y="4" width="6" height="6" rx="1"/><rect x="4" y="14" width="6" height="6" rx="1"/><rect x="14" y="14" width="6" height="6" rx="1"/></symbol>
    <symbol id="i-chevron-left" viewBox="0 0 24 24"><path d="m15 5-7 7 7 7"/></symbol>
    <symbol id="i-chevron-right" viewBox="0 0 24 24"><path d="m9 5 7 7-7 7"/></symbol>
    <symbol id="i-chevron-down" viewBox="0 0 24 24"><path d="m5 9 7 7 7-7"/></symbol>
    <symbol id="i-close" viewBox="0 0 24 24"><path d="m6 6 12 12M18 6 6 18"/></symbol>
    <symbol id="i-check" viewBox="0 0 24 24"><path d="m5 12 4 4L19 6"/></symbol>
    <symbol id="i-signal" viewBox="0 0 24 24"><path d="M4 19v-3m5 3v-7m5 7V8m5 11V4"/></symbol>
    <symbol id="i-wifi" viewBox="0 0 24 24"><path d="M4 9a12 12 0 0 1 16 0M7 13a7.5 7.5 0 0 1 10 0m-7 4a3 3 0 0 1 4 0"/></symbol>`
}

function baseCss() {
  return `
    :root{--bg:#0a0c11;--surface:#14161d;--surface-high:#1c202a;--border:#252a35;--text:#f4f6fb;--dim:#8b93a5;--accent:#0a84ff;--success:#5dd39e;--danger:#ff6b6b;--gold:#ffd479;--glass:rgba(255,255,255,.07);--glass-border:rgba(255,255,255,.11)}
    *{box-sizing:border-box}html{background:#07080b}body{margin:0;color:var(--text);font-family:Roboto,Arial,sans-serif;background:radial-gradient(circle at 50% -15%,#17202c 0,#0c0e13 28%,#07080b 62%);min-width:1460px}
    button,input{font:inherit}button{color:inherit;border:0;background:none;padding:0;cursor:pointer}.svg-defs{position:absolute;width:0;height:0;overflow:hidden}.icon{width:20px;height:20px;fill:none;stroke:currentColor;stroke-width:1.8;stroke-linecap:round;stroke-linejoin:round;display:block}.icon use[href="#i-home"],.icon use[href="#i-bookmark"],.icon use[href="#i-play"]{fill:currentColor;stroke:currentColor}
    .board{width:1460px;margin:0 auto;padding:54px 56px 74px}.board-head{display:flex;justify-content:space-between;gap:64px;align-items:flex-end;padding:0 12px 38px;border-bottom:1px solid rgba(255,255,255,.09);margin-bottom:44px}.board-copy{max-width:850px}.eyebrow{font-size:12px;letter-spacing:1.7px;font-weight:700;color:var(--accent);margin-bottom:14px}.board h1{font-size:48px;line-height:1.05;letter-spacing:-1.8px;margin:0 0 12px}.board-subtitle{font-size:19px;line-height:1.45;margin:0;color:#dce2ee}.board-rationale{font-size:14px;line-height:1.55;color:var(--dim);max-width:760px;margin:10px 0 0}.board-meta{text-align:right;color:var(--dim);font-size:12px}.halo-lockup{display:flex;align-items:center;justify-content:flex-end;gap:9px;font-size:18px;font-weight:800;color:var(--text);margin-bottom:14px}.halo-lockup img{width:36px;height:36px;object-fit:contain}.palette{display:flex;justify-content:flex-end;gap:6px}.palette span{display:block;width:25px;height:25px;border-radius:50%;background:var(--swatch);border:1px solid rgba(255,255,255,.15)}
    .phone-grid{display:grid;grid-template-columns:repeat(3,1fr);column-gap:48px;row-gap:58px;align-items:start}.phone{width:386px;justify-self:center}.phone-label{display:flex;align-items:baseline;justify-content:space-between;margin:0 5px 10px;color:var(--dim);font-size:11px;letter-spacing:.6px;text-transform:uppercase}.phone-label b{color:#cfd5e0;font-size:12px;letter-spacing:0;text-transform:none}
    .android-shell{display:inline-block;padding:8px;background:#1a1a1a;border-radius:39px;box-shadow:0 0 0 2px #2a2a2a,0 24px 64px rgba(0,0,0,.52);position:relative}.android-screen{position:relative;width:370px;height:800px;border-radius:32px;overflow:hidden;background:var(--bg)}.status-bar{position:absolute;inset:0 0 auto;height:29px;display:flex;align-items:center;justify-content:space-between;padding:0 21px;font-size:11px;font-weight:600;z-index:80;pointer-events:none;color:#fff;text-shadow:0 1px 3px rgba(0,0,0,.65)}.status-icons{display:flex;align-items:center;gap:6px}.status-icons .icon{width:12px;height:12px;stroke-width:2}.battery{font-size:9px;border:1px solid currentColor;border-radius:3px;padding:1px 3px}.punch-hole{position:absolute;top:9px;left:50%;width:11px;height:11px;transform:translateX(-50%);background:#050505;border-radius:50%;z-index:90;box-shadow:0 0 0 1px rgba(255,255,255,.07)}.app-viewport{position:absolute;inset:29px 0 21px;background:var(--bg);overflow:hidden}.system-nav{position:absolute;inset:auto 0 0;height:21px;display:flex;align-items:center;justify-content:center;background:var(--bg);z-index:90}.system-nav span{width:88px;height:4px;background:rgba(255,255,255,.5);border-radius:999px}
    .app-screen{display:none;position:absolute;inset:0;background:var(--bg);overflow:hidden}.app-screen.active{display:block}.scroll{height:100%;overflow:auto;scrollbar-width:none}.scroll::-webkit-scrollbar{display:none}.tabbed,.home-scroll{padding-bottom:70px}.screen-head{padding:14px 16px 12px}.screen-head h2{font-size:30px;line-height:1;margin:0 0 12px;font-weight:800;letter-spacing:-.6px}.screen-head .muted{margin:-4px 0 12px}.muted{color:var(--dim);font-size:12px}
    .search-field{display:flex;align-items:center;gap:8px;width:100%;height:40px;border-radius:11px;padding:0 12px;background:rgba(255,255,255,.09);color:var(--dim);font-size:14px;text-align:left}.search-field .icon{width:17px;height:17px}.input-field input{flex:1;min-width:0;border:0;outline:0;background:transparent;color:var(--text);font-size:14px}.input-field button{color:var(--dim)}.segmented{display:flex;gap:7px;background:rgba(255,255,255,.06);padding:3px;border-radius:10px;margin-top:10px}.segmented button{flex:1;border-radius:7px;padding:6px 4px;color:var(--dim);font-size:12.5px;font-weight:700}.segmented button.selected{background:rgba(255,255,255,.16);color:#fff}
    .hero-card{height:190px;margin:0 16px 22px;border-radius:20px;background-size:cover;background-position:center;overflow:hidden;position:relative;display:flex;align-items:flex-end;padding:16px;cursor:pointer}.hero-card h3{font-size:24px;margin:0 0 4px}.hero-card p,.detail-hero p{margin:0;color:#ccd2dc;font-size:12px;font-weight:600}.hero-card p span,.detail-hero p span{color:var(--gold)}.primary{display:inline-flex;align-items:center;justify-content:center;gap:6px;margin-top:10px;border-radius:999px;background:#fff;color:#000;padding:8px 16px;font-size:13px;font-weight:800}.primary .icon{width:15px;height:15px}.primary.wide{flex:1;border-radius:12px;margin-top:0;padding:12px 14px;font-size:15px}.primary.circle{width:48px;height:48px;border-radius:50%;padding:0;margin:0}.media-section{margin:0 0 22px}.media-section h3{font-size:17px;margin:0 16px 10px;letter-spacing:-.2px}.media-rail{display:flex;gap:10px;overflow:hidden;padding:0 16px}.poster-card{width:104px;flex:0 0 104px;text-align:left}.poster-image{display:block;position:relative;width:100%;height:156px;border-radius:12px;overflow:hidden;background:var(--surface)}.poster-image img{width:100%;height:100%;object-fit:cover}.poster-label{display:block;margin-top:6px;color:var(--dim);font-size:11px;font-weight:600;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}.progress{position:absolute;inset:auto 0 0;height:4px;background:rgba(255,255,255,.22)}.progress b{display:block;height:100%;background:var(--accent)}
    .app-tabs{display:none;position:absolute;inset:auto 0 0;height:62px;z-index:60;background:rgba(12,14,19,.72);backdrop-filter:blur(18px);border-top:1px solid rgba(255,255,255,.11);grid-template-columns:repeat(4,1fr)}.app-tabs.visible{display:grid}.app-tabs button{display:flex;flex-direction:column;align-items:center;justify-content:center;gap:3px;color:#f4f6fb;font-size:10px;font-weight:600}.app-tabs .icon{width:21px;height:21px}.app-tabs button.active{color:var(--accent)}
    .search-top{display:flex;align-items:center;gap:13px;padding:14px 16px 12px}.search-top .search-field{flex:1}.text-action{color:var(--accent);font-size:13px;font-weight:700}.search-results{padding-bottom:20px}.detail-hero{height:330px;background-size:cover;background-position:center;display:flex;align-items:flex-end;padding:16px}.detail-hero h2{font-size:31px;margin:0 0 4px;letter-spacing:.2px}.detail-body{padding:10px 16px 28px}.detail-actions{display:flex;gap:9px}.icon-action,.tonal,.outline-action{display:flex;align-items:center;justify-content:center;gap:6px;border-radius:12px;border:1px solid var(--glass-border);background:var(--glass);padding:10px 13px;color:var(--accent);font-size:13px;font-weight:700}.icon-action{width:50px}.synopsis{color:#c3c9d6;font-size:13px;line-height:1.5;margin:14px 0}.season-button{display:flex;align-items:center;gap:6px;border-radius:8px;background:var(--surface-high);padding:9px 13px;margin:8px 0 10px;font-size:13px;font-weight:700}.season-button .icon{width:15px;height:15px}.episodes{margin:0 -16px}.episode{display:flex;align-items:center;gap:9px;width:100%;padding:7px 16px;text-align:left}.episode-thumb{width:104px;height:58px;flex:0 0 104px;border-radius:7px;background-size:cover;background-position:center;display:grid;place-items:center}.episode-thumb i{width:26px;height:26px;border-radius:50%;background:rgba(0,0,0,.55);display:grid;place-items:center}.episode-thumb .icon{width:13px;height:13px}.episode-copy{flex:1;min-width:0}.episode-copy b{display:block;font-size:12px;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}.episode-copy small{display:block;color:var(--dim);font-size:10px;margin-top:3px}.episode-progress{display:block;height:3px;background:rgba(255,255,255,.16);margin-top:5px;border-radius:3px;overflow:hidden}.episode-progress em{display:block;height:100%;background:var(--accent)}.back-float{position:absolute;top:12px;left:14px;width:34px;height:34px;border-radius:50%;background:rgba(0,0,0,.46);display:grid;place-items:center;z-index:20}.back-inline{width:34px;height:34px;display:grid;place-items:center;border-radius:50%;background:rgba(255,255,255,.07);flex:0 0 auto}.back-inline .icon,.back-float .icon{width:22px;height:22px}
    .poster-grid{display:grid}.strict-grid{grid-template-columns:repeat(3,1fr);gap:10px;padding:0 16px}.strict-grid .poster-card{width:auto}.strict-grid .poster-image{height:auto;aspect-ratio:2/3}.modal-head{height:58px;display:grid;grid-template-columns:44px 1fr 44px;align-items:center;padding:9px 14px;border-bottom:1px solid var(--border)}.modal-head h3{margin:0;text-align:center;font-size:17px}.source-scroll{padding:14px 16px}.group-label,.settings-group>h3{font-size:11px;letter-spacing:.6px;font-weight:700;text-transform:uppercase;color:var(--accent);margin:12px 4px 7px}.source-card,.settings-block{border-radius:16px;background:var(--glass);border:1px solid var(--glass-border);overflow:hidden}.source-row{display:flex;align-items:stretch;border-bottom:1px solid var(--glass-border)}.source-row:last-child{border-bottom:0}.source-main{flex:1;text-align:left;padding:11px 13px;min-width:0}.source-main b{display:block;font-size:13px}.source-main small{display:block;color:var(--dim);font-size:10.5px;margin-top:3px}.source-main code{display:block;color:var(--dim);font-size:9.5px;margin-top:4px}.download-action{width:50px;display:grid;place-items:center;color:var(--accent)}.download-action.done{color:var(--success)}.settings-scroll{padding:0 16px 84px}.settings-scroll .screen-head{padding-left:0;padding-right:0}.setting-row{min-height:49px;width:100%;display:flex;align-items:center;justify-content:space-between;gap:10px;padding:9px 13px;border-bottom:1px solid var(--glass-border);font-size:13px;text-align:left}.setting-row:last-child{border-bottom:0}.setting-row>span{flex:1}.setting-row strong{display:flex;align-items:center;gap:3px;color:var(--dim);font-size:11.5px;font-weight:500}.setting-row strong .icon{width:14px;height:14px}.addon-setting>span b,.addon-setting>span small{display:block}.addon-setting>span small{font-size:10.5px;color:var(--dim);margin-top:2px}.setting-leading{width:31px;height:31px;border-radius:9px;background:rgba(10,132,255,.14);color:var(--accent);display:grid;place-items:center}.setting-leading .icon{width:16px;height:16px}.addon-setting>.icon{width:14px;height:14px;color:var(--dim)}.switch{width:37px;height:21px;border-radius:999px;background:#424753;padding:2px;display:flex;justify-content:flex-start}.switch b{width:17px;height:17px;border-radius:50%;background:#fff}.switch.on{background:var(--accent);justify-content:flex-end}.connected{color:var(--success)!important}.connected i{width:6px;height:6px;border-radius:50%;background:var(--success)}.sign-out{display:block;margin:20px auto 0;color:var(--danger);font-size:13px;font-weight:700}.toast{position:absolute;left:50%;bottom:78px;transform:translate(-50%,12px);background:rgba(5,7,12,.92);border:1px solid rgba(255,255,255,.16);border-radius:999px;padding:8px 13px;font-size:11px;font-weight:700;opacity:0;pointer-events:none;z-index:95;white-space:nowrap;transition:.2s}.toast.show{opacity:1;transform:translate(-50%,0)}
    .sheet-layer:empty{display:none}.sheet-layer{position:absolute;inset:0;z-index:100}.sheet-scrim{position:absolute;inset:0;background:rgba(0,0,0,.55);width:100%}.bottom-sheet{position:absolute;inset:auto 0 0;background:rgba(20,22,30,.96);backdrop-filter:blur(20px);border-radius:20px 20px 0 0;padding:8px 16px 18px;border-top:1px solid rgba(255,255,255,.12)}.grabber{display:block;width:34px;height:4px;border-radius:3px;background:rgba(255,255,255,.28);margin:0 auto 12px}.bottom-sheet h3{font-size:17px;margin:0 0 7px}.bottom-sheet button{display:flex;width:100%;align-items:center;justify-content:space-between;padding:9px 2px;border-bottom:1px solid rgba(255,255,255,.08);font-size:13px}.bottom-sheet button.selected{color:var(--accent);font-weight:700}.bottom-sheet .icon{width:17px;height:17px}.downloads-screen{padding-bottom:74px}.download-show{display:flex;align-items:center;gap:11px;padding:10px 16px}.download-show img{width:46px;height:69px;object-fit:cover;border-radius:7px}.download-show h3{margin:0 0 3px;font-size:17px}.download-show p{margin:0;color:var(--dim);font-size:11px}.download-row{display:flex;align-items:center;justify-content:space-between;width:calc(100% - 73px);margin-left:73px;padding:11px 16px 11px 0;border-bottom:1px solid var(--border);text-align:left}.download-row b,.download-row small{display:block}.download-row b{font-size:13px}.download-row small{color:var(--dim);font-size:10px;margin-top:3px}.download-row .icon{color:var(--accent)}
  `
}

function variantCss() {
  return strictCss()
}

function strictCss() {
  return `
    .variant-strict .board-head{border-bottom-color:rgba(10,132,255,.24)}
    .variant-strict .android-screen{box-shadow:inset 0 0 0 1px rgba(255,255,255,.035)}
  `
}
