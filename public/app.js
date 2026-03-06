/* ======================================================
   TORII — Japanese Art & Crypto Marketplace
   Frontend Application Logic
====================================================== */

'use strict';

// -------------------------------------------------------
// DATA
// -------------------------------------------------------

const ARTISTS = [
  {
    id: 'dwtml',
    handle: '@dwtml',
    name: 'DWTML',
    fullName: 'Daiki Watanabe',
    style: 'Neo-sumi ink & generative calligraphy. Merges 800-year-old brushwork tradition with on-chain randomness.',
    avatar: '渡',
    avatarColor: '#1a1530',
    avatarBorder: 'rgba(208,160,80,0.4)',
    works: 84,
    volume: '312 ETH',
    tag: 'featured',
    tagLabel: 'Featured',
    verified: true,
  },
  {
    id: 'aq',
    handle: '@aq',
    name: 'AQ',
    fullName: 'Akira Quen',
    style: 'Digital ukiyo-e. Woodblock printing techniques translated into layered generative art on Ethereum.',
    avatar: '秋',
    avatarColor: '#1a1520',
    avatarBorder: 'rgba(148,67,231,0.4)',
    works: 61,
    volume: '198 ETH',
    tag: 'featured',
    tagLabel: 'Featured',
    verified: true,
  },
  {
    id: 'andres',
    handle: '@andres',
    name: 'Andres',
    fullName: 'Andrés Muñoz',
    style: 'Japan-meets-Latin surrealism. Photo-based large-format works that blur the line between ukiyo-e and García Márquez.',
    avatar: '和',
    avatarColor: '#151520',
    avatarBorder: 'rgba(200,52,42,0.4)',
    works: 47,
    volume: '144 ETH',
    tag: 'verified',
    tagLabel: 'Verified',
    verified: true,
  },
  {
    id: 'ack',
    handle: '@ack',
    name: 'ACK',
    fullName: 'Akiko Chiba-Kato',
    style: 'Wabi-sabi minimalism. Monochrome digital ceramics and conceptual sculptures minted as 3D assets.',
    avatar: '千',
    avatarColor: '#101520',
    avatarBorder: 'rgba(76,175,128,0.4)',
    works: 39,
    volume: '97 ETH',
    tag: 'verified',
    tagLabel: 'Verified',
    verified: true,
  },
];

const AUCTION_ARTWORKS = [
  {
    id: 'a1',
    title: 'Fuji at Dawn #001',
    artist: 'DWTML',
    artistId: 'dwtml',
    category: 'painting',
    kanji: '富士',
    kanjiSize: 72,
    bgColor: '#0d1520',
    bgColor2: '#1a1530',
    currentBid: 12.4,
    currentBidUSD: 40180,
    currency: 'ETH',
    bids: 24,
    endsIn: 3 * 3600 + 22 * 60 + 14,  // seconds
  },
  {
    id: 'a2',
    title: 'Ink Storm #VII',
    artist: 'AQ',
    artistId: 'aq',
    category: 'calligraphy',
    kanji: '嵐',
    kanjiSize: 96,
    bgColor: '#150d20',
    bgColor2: '#1a0d30',
    currentBid: 8.75,
    currentBidUSD: 28350,
    currency: 'ETH',
    bids: 17,
    endsIn: 8 * 3600 + 5 * 60 + 40,
  },
  {
    id: 'a3',
    title: 'Sakura Protocol',
    artist: 'Andres',
    artistId: 'andres',
    category: 'digital',
    kanji: '桜',
    kanjiSize: 80,
    bgColor: '#1a0d15',
    bgColor2: '#200d1a',
    currentBid: 5.2,
    currentBidUSD: 16848,
    currency: 'ETH',
    bids: 11,
    endsIn: 47 * 60 + 33,  // urgent
  },
  {
    id: 'a4',
    title: 'Void Ceramic #12',
    artist: 'ACK',
    artistId: 'ack',
    category: 'digital',
    kanji: '虚',
    kanjiSize: 88,
    bgColor: '#0d1515',
    bgColor2: '#0d1a20',
    currentBid: 3.6,
    currentBidUSD: 11664,
    currency: 'ETH',
    bids: 9,
    endsIn: 14 * 3600 + 30 * 60,
  },
  {
    id: 'a5',
    title: 'Night Market, Shinjuku',
    artist: 'Andres',
    artistId: 'andres',
    category: 'photography',
    kanji: '夜',
    kanjiSize: 90,
    bgColor: '#0d0d15',
    bgColor2: '#111125',
    currentBid: 2.1,
    currentBidUSD: 6804,
    currency: 'ETH',
    bids: 6,
    endsIn: 26 * 3600 + 15 * 60,
  },
  {
    id: 'a6',
    title: 'Dragon Gate — Origin',
    artist: 'DWTML',
    artistId: 'dwtml',
    category: 'calligraphy',
    kanji: '龍',
    kanjiSize: 88,
    bgColor: '#1a1005',
    bgColor2: '#20180a',
    currentBid: 18.9,
    currentBidUSD: 61218,
    currency: 'ETH',
    bids: 38,
    endsIn: 6 * 3600 + 44 * 60 + 10,
  },
];

const MARKET_ARTWORKS = [
  { id: 'm1', title: 'Moon Reflection', artist: 'AQ', kanji: '月', price: '1.8 ETH', bgColor: '#0d1020', bgColor2: '#101530' },
  { id: 'm2', title: 'Bamboo Study III', artist: 'DWTML', kanji: '竹', price: '0.95 ETH', bgColor: '#0d150d', bgColor2: '#10200d' },
  { id: 'm3', title: 'Wabi Vessel 04', artist: 'ACK', kanji: '器', price: '2.3 ETH', bgColor: '#150d0d', bgColor2: '#200d0d' },
  { id: 'm4', title: 'Rainstorm Over Edo', artist: 'Andres', kanji: '江', price: '3.1 ETH', bgColor: '#0d1015', bgColor2: '#0d1520' },
  { id: 'm5', title: 'Cherry Dawn', artist: 'AQ', kanji: '朝', price: '1.2 ETH', bgColor: '#150d15', bgColor2: '#1a0d1a' },
  { id: 'm6', title: 'Silent Stone', artist: 'ACK', kanji: '石', price: '0.7 ETH', bgColor: '#101010', bgColor2: '#181818' },
  { id: 'm7', title: 'Autumn Koi', artist: 'DWTML', kanji: '鯉', price: '1.55 ETH', bgColor: '#1a0f0a', bgColor2: '#20140a' },
  { id: 'm8', title: 'Tokyo Mist #3', artist: 'Andres', kanji: '霧', price: '2.8 ETH', bgColor: '#0a101a', bgColor2: '#0a1520' },
];

const DROPS = [
  {
    day: '15',
    month: 'Mar',
    title: 'Ink & Entropy — Genesis Collection',
    artist: 'DWTML',
    desc: '100 unique sumi-e pieces, each generated from on-chain entropy at mint.',
    edition: '100 editions',
    price: '0.5 ETH',
    status: 'live',
    statusLabel: 'Live Now',
  },
  {
    day: '22',
    month: 'Mar',
    title: 'Neo Ukiyo-e Series II',
    artist: 'AQ',
    desc: 'Second chapter of the blockbuster ukiyo-e collection. 50 pieces, whitelist priority.',
    edition: '50 editions',
    price: '1.2 ETH',
    status: 'wl',
    statusLabel: 'WL Open',
  },
  {
    day: '01',
    month: 'Apr',
    title: 'Latitudes — Japan × Latin America',
    artist: 'Andres',
    desc: 'A dual-culture exploration of natural landscapes. Large format photography, physical + digital.',
    edition: '30 editions',
    price: '2.0 ETH',
    status: 'soon',
    statusLabel: 'Coming Soon',
  },
  {
    day: '10',
    month: 'Apr',
    title: 'Void Objects',
    artist: 'ACK',
    desc: 'Interactive 3D ceramic sculptures. Holders can spin, rotate, and display in AR.',
    edition: '25 editions',
    price: '1.8 ETH',
    status: 'soon',
    statusLabel: 'Coming Soon',
  },
];

// -------------------------------------------------------
// STATE
// -------------------------------------------------------

let walletConnected = false;
let selectedCurrency = 'ETH';
let activeBidItem = null;
let auctionTimers = {};
let currentFilter = 'all';
let allMarketArtworks = [...MARKET_ARTWORKS];
let loadedCount = 8;
let remindedDrops = new Set();

// -------------------------------------------------------
// INIT
// -------------------------------------------------------

document.addEventListener('DOMContentLoaded', () => {
  spawnPetals();
  renderAuctions();
  renderArtists();
  renderMarket();
  renderDrops();
  startPriceTicker();
  startAuctionTimers();
  setupNavScroll();
});

// -------------------------------------------------------
// CHERRY BLOSSOMS
// -------------------------------------------------------

function spawnPetals() {
  const container = document.getElementById('petals');
  if (!container) return;

  for (let i = 0; i < 18; i++) {
    const petal = document.createElement('div');
    petal.className = 'petal';
    petal.style.left = Math.random() * 100 + '%';
    petal.style.animationDuration = (8 + Math.random() * 12) + 's';
    petal.style.animationDelay = (Math.random() * 15) + 's';
    petal.style.width = (6 + Math.random() * 8) + 'px';
    petal.style.height = (8 + Math.random() * 10) + 'px';
    petal.style.opacity = 0.1 + Math.random() * 0.3;
    const hue = 330 + Math.random() * 30;
    petal.style.background = `hsla(${hue}, 60%, 75%, 0.2)`;
    petal.style.borderRadius = Math.random() > 0.5 ? '50% 0' : '0 50%';
    container.appendChild(petal);
  }
}

// -------------------------------------------------------
// RENDER AUCTIONS
// -------------------------------------------------------

function renderAuctions(filter = 'all') {
  const grid = document.getElementById('auction-grid');
  if (!grid) return;

  const items = filter === 'all'
    ? AUCTION_ARTWORKS
    : AUCTION_ARTWORKS.filter(a => a.category === filter);

  grid.innerHTML = items.map(art => `
    <div class="auction-card" data-id="${art.id}" data-category="${art.category}" onclick="openBidModal('${art.id}')">
      <div class="card-art">
        <div class="card-art-inner" style="background: linear-gradient(135deg, ${art.bgColor} 0%, ${art.bgColor2} 100%);">
          <span class="card-kanji" style="font-size:${art.kanjiSize}px;">${art.kanji}</span>
        </div>
        <div class="card-overlay">
          <button class="card-overlay-btn">Place Bid</button>
        </div>
        <div class="card-timer ${art.endsIn < 3600 ? 'urgent' : ''}" id="timer-${art.id}">
          ${formatTime(art.endsIn)}
        </div>
        <div class="art-badge live-badge">LIVE</div>
      </div>
      <div class="card-body">
        <div class="card-category">${art.category}</div>
        <div class="card-title">${art.title}</div>
        <div class="card-artist">by ${art.artist}</div>
        <div class="card-bid-row">
          <div class="card-bid-info">
            <div class="card-bid-label">Current Bid</div>
            <div class="card-bid-val">${art.currentBid} ${art.currency}</div>
            <div class="card-bid-usd">≈ $${art.currentBidUSD.toLocaleString()}</div>
          </div>
          <div class="card-bids-count">${art.bids} bids</div>
        </div>
      </div>
    </div>
  `).join('');
}

function filterAuctions(category, btn) {
  currentFilter = category;
  document.querySelectorAll('.filter-btn').forEach(b => b.classList.remove('active'));
  btn.classList.add('active');
  renderAuctions(category);
}

// -------------------------------------------------------
// RENDER ARTISTS
// -------------------------------------------------------

function renderArtists() {
  const grid = document.getElementById('artists-grid');
  if (!grid) return;

  grid.innerHTML = ARTISTS.map(a => `
    <div class="artist-card" onclick="showToast('Opening ${a.name}\\'s profile…')">
      <div class="artist-tag ${a.tag === 'featured' ? 'tag-featured' : 'tag-verified'}">${a.tagLabel}</div>
      <div class="artist-avatar" style="background:${a.avatarColor}; border-color:${a.avatarBorder};">
        ${a.avatar}
      </div>
      <div class="artist-handle">${a.handle}</div>
      <div class="artist-name">${a.name}</div>
      <div class="artist-style">${a.style}</div>
      <div class="artist-stats">
        <div class="artist-stat">
          <span class="artist-stat-val">${a.works}</span>
          <span class="artist-stat-key">Works</span>
        </div>
        <div class="artist-stat">
          <span class="artist-stat-val">${a.volume}</span>
          <span class="artist-stat-key">Volume</span>
        </div>
      </div>
    </div>
  `).join('');
}

// -------------------------------------------------------
// RENDER MARKET
// -------------------------------------------------------

function renderMarket(artworks = allMarketArtworks) {
  const grid = document.getElementById('market-grid');
  if (!grid) return;

  grid.innerHTML = artworks.map(art => `
    <div class="market-card" onclick="showToast('Opening "${art.title}"…')">
      <div class="market-art" style="background: linear-gradient(135deg, ${art.bgColor}, ${art.bgColor2});">
        ${art.kanji}
      </div>
      <div class="market-body">
        <div class="market-title">${art.title}</div>
        <div class="market-artist">by ${art.artist}</div>
        <div class="market-price-row">
          <div class="market-price">${art.price}</div>
          <button class="market-buy-btn" onclick="event.stopPropagation(); buyNow('${art.id}')">Buy Now</button>
        </div>
      </div>
    </div>
  `).join('');
}

function searchArtworks(query) {
  const q = query.toLowerCase().trim();
  if (!q) {
    renderMarket(allMarketArtworks);
    return;
  }
  const filtered = allMarketArtworks.filter(a =>
    a.title.toLowerCase().includes(q) || a.artist.toLowerCase().includes(q)
  );
  renderMarket(filtered);
}

function sortArtworks(value) {
  let sorted = [...allMarketArtworks];
  if (value === 'price-low') {
    sorted.sort((a, b) => parseFloat(a.price) - parseFloat(b.price));
  } else if (value === 'price-high') {
    sorted.sort((a, b) => parseFloat(b.price) - parseFloat(a.price));
  } else if (value === 'popular') {
    sorted.reverse();
  }
  renderMarket(sorted);
}

function loadMore() {
  showToast('Loading more artworks…');
}

function buyNow(id) {
  if (!walletConnected) {
    openModal('wallet-modal');
    return;
  }
  showToast('Opening purchase flow…', 'success');
}

// -------------------------------------------------------
// RENDER DROPS
// -------------------------------------------------------

function renderDrops() {
  const list = document.getElementById('drops-list');
  if (!list) return;

  list.innerHTML = DROPS.map((drop, i) => `
    <div class="drop-item">
      <div class="drop-date">
        <span class="drop-day">${drop.day}</span>
        <span class="drop-month">${drop.month}</span>
      </div>
      <div class="drop-info">
        <div class="drop-status status-${drop.status}">${drop.statusLabel}</div>
        <div class="drop-title">${drop.title}</div>
        <div class="drop-artist">by ${drop.artist}</div>
        <div class="drop-desc">${drop.desc}</div>
      </div>
      <div class="drop-meta">
        <div class="drop-edition">${drop.edition}</div>
        <div class="drop-price">${drop.price}</div>
      </div>
      <div class="drop-cta">
        <button class="btn-remind" id="remind-${i}" onclick="toggleRemind(${i}, this)">
          ${drop.status === 'live' ? 'Mint Now' : 'Set Reminder'}
        </button>
      </div>
    </div>
  `).join('');
}

function toggleRemind(idx, btn) {
  if (remindedDrops.has(idx)) {
    remindedDrops.delete(idx);
    btn.textContent = 'Set Reminder';
    btn.classList.remove('reminded');
    showToast('Reminder removed');
  } else {
    remindedDrops.add(idx);
    btn.textContent = '✓ Reminded';
    btn.classList.add('reminded');
    showToast('Reminder set! We\'ll notify you by email.', 'success');
  }
}

// -------------------------------------------------------
// AUCTION TIMERS
// -------------------------------------------------------

function startAuctionTimers() {
  // Clone times so we can count down
  AUCTION_ARTWORKS.forEach(art => {
    auctionTimers[art.id] = art.endsIn;
  });

  setInterval(() => {
    AUCTION_ARTWORKS.forEach(art => {
      if (auctionTimers[art.id] > 0) {
        auctionTimers[art.id]--;
      }
      const el = document.getElementById('timer-' + art.id);
      if (el) {
        const t = auctionTimers[art.id];
        el.textContent = formatTime(t);
        if (t < 3600) el.classList.add('urgent');
        else el.classList.remove('urgent');
      }
    });

    // Update modal timer if open
    if (activeBidItem) {
      const timeEl = document.getElementById('modal-time');
      if (timeEl) {
        timeEl.textContent = formatTime(auctionTimers[activeBidItem.id]);
      }
    }
  }, 1000);
}

function formatTime(seconds) {
  if (seconds <= 0) return 'Ended';
  const h = Math.floor(seconds / 3600);
  const m = Math.floor((seconds % 3600) / 60);
  const s = seconds % 60;
  if (h > 0) return `${h}h ${pad(m)}m ${pad(s)}s`;
  return `${pad(m)}m ${pad(s)}s`;
}

function pad(n) { return String(n).padStart(2, '0'); }

// -------------------------------------------------------
// PRICE TICKER
// -------------------------------------------------------

function startPriceTicker() {
  const prices = { ETH: 3241, SOL: 187, BTC: 62400 };
  const ethEl = document.getElementById('eth-price');
  const solEl = document.getElementById('sol-price');

  setInterval(() => {
    // Simulate small price fluctuation
    prices.ETH += (Math.random() - 0.49) * 8;
    prices.SOL += (Math.random() - 0.49) * 0.8;

    if (ethEl) ethEl.textContent = '$' + Math.round(prices.ETH).toLocaleString();
    if (solEl) solEl.textContent = '$' + Math.round(prices.SOL).toLocaleString();
  }, 4000);
}

// -------------------------------------------------------
// BID MODAL
// -------------------------------------------------------

function openBidModal(id) {
  const art = AUCTION_ARTWORKS.find(a => a.id === id);
  if (!art) return;
  activeBidItem = art;

  document.getElementById('modal-art-title').textContent = art.title;
  document.getElementById('modal-art-artist').textContent = 'by ' + art.artist;
  document.getElementById('modal-current-bid').textContent = art.currentBid + ' ' + art.currency;
  document.getElementById('modal-next-bid').textContent = (art.currentBid + 0.5).toFixed(2) + ' ETH (min)';
  document.getElementById('modal-time').textContent = formatTime(auctionTimers[art.id] || art.endsIn);
  document.getElementById('modal-bid-count').textContent = art.bids + ' bids';
  document.getElementById('bid-amount').value = '';
  document.getElementById('bid-amount').placeholder = (art.currentBid + 0.5).toFixed(2);

  const preview = document.getElementById('modal-art-preview');
  if (preview) {
    preview.style.background = `linear-gradient(135deg, ${art.bgColor}, ${art.bgColor2})`;
    preview.textContent = art.kanji;
  }

  openModal('bid-modal');
}

function placeBid() {
  if (!walletConnected) {
    closeModal('bid-modal');
    openModal('wallet-modal');
    return;
  }

  const amount = parseFloat(document.getElementById('bid-amount').value);
  if (!activeBidItem) return;
  const min = activeBidItem.currentBid + 0.5;

  if (!amount || amount < min) {
    showToast(`Minimum bid is ${min.toFixed(2)} ${selectedCurrency}`, 'error');
    return;
  }

  // Simulate bid
  activeBidItem.currentBid = amount;
  activeBidItem.bids += 1;
  activeBidItem.currentBidUSD = Math.round(amount * 3241);

  closeModal('bid-modal');
  renderAuctions(currentFilter);
  showToast(`Bid of ${amount} ${selectedCurrency} placed! 🎌`, 'success');
}

function selectCurrency(currency, btn) {
  selectedCurrency = currency;
  document.querySelectorAll('.currency-btn').forEach(b => b.classList.remove('active'));
  btn.classList.add('active');
  document.getElementById('bid-currency-badge').textContent = currency;
}

// -------------------------------------------------------
// WALLET
// -------------------------------------------------------

function connectWallet() {
  if (walletConnected) {
    showToast('Wallet already connected');
    return;
  }
  openModal('wallet-modal');
}

function selectWallet(name) {
  closeModal('wallet-modal');

  // Simulate connection
  setTimeout(() => {
    walletConnected = true;
    const btn = document.getElementById('connect-btn');
    if (btn) {
      btn.innerHTML = '<span class="btn-icon">✓</span> 0x3f4…9a2b';
      btn.style.color = '#4caf80';
      btn.style.borderColor = 'rgba(76,175,128,0.4)';
    }
    showToast(`${name} connected successfully`, 'success');
  }, 600);
}

// -------------------------------------------------------
// NEWSLETTER
// -------------------------------------------------------

function subscribeNewsletter(e) {
  e.preventDefault();
  const input = e.target.querySelector('input');
  if (input && input.value) {
    showToast('Application submitted! We\'ll be in touch.', 'success');
    input.value = '';
  }
}

// -------------------------------------------------------
// MODAL HELPERS
// -------------------------------------------------------

function openModal(id) {
  const el = document.getElementById(id);
  if (el) {
    el.classList.add('open');
    document.body.style.overflow = 'hidden';
  }
}

function closeModal(id) {
  const el = document.getElementById(id);
  if (el) {
    el.classList.remove('open');
    document.body.style.overflow = '';
  }
}

// -------------------------------------------------------
// TOAST
// -------------------------------------------------------

let toastTimeout;

function showToast(message, type = '') {
  const toast = document.getElementById('toast');
  if (!toast) return;

  toast.textContent = message;
  toast.className = 'toast show' + (type ? ' ' + type : '');

  clearTimeout(toastTimeout);
  toastTimeout = setTimeout(() => {
    toast.classList.remove('show');
  }, 3000);
}

// -------------------------------------------------------
// NAV SCROLL EFFECT
// -------------------------------------------------------

function setupNavScroll() {
  const nav = document.getElementById('nav');
  if (!nav) return;

  window.addEventListener('scroll', () => {
    if (window.scrollY > 60) {
      nav.style.background = 'rgba(10,10,12,0.97)';
    } else {
      nav.style.background = 'rgba(10,10,12,0.85)';
    }
  }, { passive: true });
}

// -------------------------------------------------------
// KEYBOARD
// -------------------------------------------------------

document.addEventListener('keydown', (e) => {
  if (e.key === 'Escape') {
    closeModal('bid-modal');
    closeModal('wallet-modal');
  }
});
