const express = require('express');
const path = require('path');

const app = express();
const port = process.env.PORT || 3000;

app.use(express.json());
app.use(express.static(path.join(__dirname, 'public')));

// ---- API: Artists ----
const ARTISTS = [
  { id: 'dwtml', handle: '@dwtml', name: 'DWTML', works: 84, volume: '312 ETH' },
  { id: 'aq', handle: '@aq', name: 'AQ', works: 61, volume: '198 ETH' },
  { id: 'andres', handle: '@andres', name: 'Andres', works: 47, volume: '144 ETH' },
  { id: 'ack', handle: '@ack', name: 'ACK', works: 39, volume: '97 ETH' },
];

// ---- API: Auctions ----
const AUCTIONS = [
  { id: 'a1', title: 'Fuji at Dawn #001', artist: 'DWTML', currentBid: 12.4, currency: 'ETH', bids: 24, category: 'painting' },
  { id: 'a2', title: 'Ink Storm #VII', artist: 'AQ', currentBid: 8.75, currency: 'ETH', bids: 17, category: 'calligraphy' },
  { id: 'a3', title: 'Sakura Protocol', artist: 'Andres', currentBid: 5.2, currency: 'ETH', bids: 11, category: 'digital' },
  { id: 'a4', title: 'Void Ceramic #12', artist: 'ACK', currentBid: 3.6, currency: 'ETH', bids: 9, category: 'digital' },
  { id: 'a5', title: 'Night Market, Shinjuku', artist: 'Andres', currentBid: 2.1, currency: 'ETH', bids: 6, category: 'photography' },
  { id: 'a6', title: 'Dragon Gate — Origin', artist: 'DWTML', currentBid: 18.9, currency: 'ETH', bids: 38, category: 'calligraphy' },
];

// ---- Routes ----

app.get('/', (req, res) => {
  res.sendFile(path.join(__dirname, 'public', 'index.html'));
});

app.get('/api/artists', (req, res) => {
  res.json({ artists: ARTISTS });
});

app.get('/api/artists/:id', (req, res) => {
  const artist = ARTISTS.find(a => a.id === req.params.id);
  if (!artist) return res.status(404).json({ error: 'Artist not found' });
  res.json(artist);
});

app.get('/api/auctions', (req, res) => {
  const { category } = req.query;
  const results = category
    ? AUCTIONS.filter(a => a.category === category)
    : AUCTIONS;
  res.json({ auctions: results });
});

app.get('/api/auctions/:id', (req, res) => {
  const auction = AUCTIONS.find(a => a.id === req.params.id);
  if (!auction) return res.status(404).json({ error: 'Auction not found' });
  res.json(auction);
});

app.post('/api/auctions/:id/bid', (req, res) => {
  const auction = AUCTIONS.find(a => a.id === req.params.id);
  if (!auction) return res.status(404).json({ error: 'Auction not found' });

  const { amount, currency, wallet } = req.body;
  if (!amount || amount <= auction.currentBid) {
    return res.status(400).json({ error: 'Bid must exceed current bid' });
  }

  auction.currentBid = amount;
  auction.bids += 1;

  res.json({ success: true, auction, message: 'Bid placed successfully' });
});

app.post('/api/newsletter', (req, res) => {
  const { email } = req.body;
  if (!email || !email.includes('@')) {
    return res.status(400).json({ error: 'Valid email required' });
  }
  res.json({ success: true, message: 'Application received. We\'ll be in touch.' });
});

app.get('/api/health', (req, res) => {
  res.json({ status: 'ok', service: 'TORII Market', version: '1.0.0' });
});

app.listen(port, () => {
  console.log(`TORII Market running at http://localhost:${port}`);
});
