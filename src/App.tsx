import React, { useState, useEffect, useRef } from 'react';
import {
  QrCode,
  Settings,
  Bell,
  Volume2,
  VolumeX,
  Play,
  RotateCcw,
  CheckCircle2,
  ShieldAlert,
  ArrowLeft,
  Smartphone,
  FolderGit2,
  Lock,
  Unlock,
  History,
  FileCode,
  Download,
  AlertTriangle,
  Send,
  Sparkles,
  Info,
  Power,
  Clock,
  Image as ImageIcon,
  Trash2,
  Upload,
  ShoppingBag,
  Plus,
  Minus,
  DollarSign,
  Coffee,
  Layers,
  Radio,
  BarChart3,
  Check,
  X,
  CreditCard,
  Banknote,
  Percent,
  Search,
  RefreshCw,
  Eye,
  EyeOff,
  Store,
  Users,
  ShieldCheck,
  ChevronRight,
  TrendingUp,
  Receipt,
  Boxes,
  Phone,
  KeyRound,
  ArrowUpRight,
  ArrowDownLeft,
  CheckCheck
} from 'lucide-react';
import { triggerClientSideZipDownload } from './downloadHelper';

// --- SPANISH NUMBERS ENGINE (Ported from SpanishNumbers.kt) ---
function toSpanishWords(amount: number): string {
  if (amount === 0) return 'cero';
  const UNITS = [
    '', 'un', 'dos', 'tres', 'cuatro', 'cinco', 'seis', 'siete', 'ocho', 'nueve',
    'diez', 'once', 'doce', 'trece', 'catorce', 'quince', 'dieciséis', 'diecisiete',
    'dieciocho', 'diecinueve', 'veinte', 'veintiún', 'veintidós', 'veintitrés',
    'veinticuatro', 'veinticinco', 'veintiséis', 'veintisiete', 'veintiocho', 'veintinueve'
  ];
  const TENS = ['', '', '', 'treinta', 'cuarenta', 'cincuenta', 'sesenta', 'setenta', 'ochenta', 'noventa'];
  const HUNDREDS = ['', 'ciento', 'doscientos', 'trescientos', 'cuatrocientos', 'quinientos', 'seiscientos', 'setecientos', 'ochocientos', 'novecientos'];

  function convertGroup(n: number): string {
    if (n === 0) return '';
    if (n === 100) return 'cien';
    const h = Math.floor(n / 100);
    const rem = n % 100;
    const parts: string[] = [];
    if (h > 0) parts.push(HUNDREDS[h]);
    if (rem > 0) {
      if (rem < 30) {
        parts.push(UNITS[rem]);
      } else {
        const t = Math.floor(rem / 10);
        const u = rem % 10;
        parts.push(u > 0 ? `${TENS[t]} y ${UNITS[u]}` : TENS[t]);
      }
    }
    return parts.join(' ').trim();
  }

  const millions = Math.floor(amount / 1_000_000);
  const thousands = Math.floor((amount % 1_000_000) / 1_000);
  const units = amount % 1_000;
  const result: string[] = [];

  if (millions > 0) {
    result.push(millions === 1 ? 'un millón' : `${convertGroup(millions)} millones`);
  }
  if (thousands > 0) {
    result.push(thousands === 1 ? 'mil' : `${convertGroup(thousands)} mil`);
  }
  if (units > 0) {
    result.push(convertGroup(units));
  }
  return result.join(' ').trim();
}

function getPaymentPhrase(amount: number, senderName?: string, includeThankYou: boolean = true): string {
  const words = toSpanishWords(amount);
  let amountWithCurrency: string;
  if (amount === 1) {
    amountWithCurrency = 'un peso';
  } else if (amount % 1_000_000 === 0 && amount > 0) {
    amountWithCurrency = `${words} de pesos`;
  } else {
    amountWithCurrency = `${words} pesos`;
  }

  const shortName = senderName ? extractShortNameForVoice(senderName) : '';

  if (shortName) {
    return includeThankYou
      ? `Pago recibido de ${shortName}, por ${amountWithCurrency}. Gracias por tu compra.`
      : `Pago recibido de ${shortName}, por ${amountWithCurrency}.`;
  }

  return includeThankYou
    ? `Pago recibido, ${amountWithCurrency}. Gracias por tu compra.`
    : `Pago recibido, ${amountWithCurrency}.`;
}

function extractShortNameForVoice(fullName: string): string {
  if (!fullName) return '';
  const parts = fullName.trim().split(/\s+/).filter(Boolean);
  if (parts.length === 0) return '';
  if (parts.length === 1) return parts[0];
  const firstName = parts[0];
  const lastName = parts.length >= 3 ? parts[2] : parts[1];
  return `${firstName} ${lastName}`;
}

function formatCOP(amount: number): string {
  return new Intl.NumberFormat('es-CO', {
    style: 'currency',
    currency: 'COP',
    maximumFractionDigits: 0
  }).format(amount);
}

// Validate Colombian or International phone number format
function isValidPhoneNumber(phone: string): boolean {
  if (!phone) return false;
  const clean = phone.replace(/[\s\-\(\)\.]/g, '');
  // Validates +573XXXXXXXXX, 573XXXXXXXXX, or 3XXXXXXXXX (10 digits)
  return /^(\+?57)?3\d{9}$/.test(clean) || /^\+?\d{8,15}$/.test(clean);
}

// --- AUDIO PLAYBACK ENGINE ---
let globalAudioContext: AudioContext | null = null;
function getAudioContext(): AudioContext | null {
  try {
    if (!globalAudioContext) {
      const AudioCtx = window.AudioContext || (window as unknown as { webkitAudioContext: typeof AudioContext }).webkitAudioContext;
      if (AudioCtx) globalAudioContext = new AudioCtx();
    }
    if (globalAudioContext && globalAudioContext.state === 'suspended') {
      globalAudioContext.resume().catch(() => {});
    }
    return globalAudioContext;
  } catch {
    return null;
  }
}

function playChimeSound() {
  try {
    const ctx = getAudioContext();
    if (!ctx) return;
    const now = ctx.currentTime;
    const osc1 = ctx.createOscillator();
    const osc2 = ctx.createOscillator();
    const gain = ctx.createGain();

    osc1.type = 'sine';
    osc1.frequency.setValueAtTime(880, now);
    osc1.frequency.exponentialRampToValueAtTime(1320, now + 0.12);

    osc2.type = 'triangle';
    osc2.frequency.setValueAtTime(440, now);
    osc2.frequency.exponentialRampToValueAtTime(660, now + 0.12);

    gain.gain.setValueAtTime(0.5, now);
    gain.gain.exponentialRampToValueAtTime(0.001, now + 0.4);

    osc1.connect(gain);
    osc2.connect(gain);
    gain.connect(ctx.destination);

    osc1.start(now);
    osc2.start(now);
    osc1.stop(now + 0.4);
    osc2.stop(now + 0.4);
  } catch (err) {
    console.warn('Audio chime error:', err);
  }
}

function speakSpanishPhrase(phrase: string, onStart?: () => void, onEnd?: () => void) {
  if (!('speechSynthesis' in window)) return;
  try {
    getAudioContext();
    window.speechSynthesis.cancel();
    if (window.speechSynthesis.paused) {
      window.speechSynthesis.resume();
    }
    const utterance = new SpeechSynthesisUtterance(phrase);
    const voices = window.speechSynthesis.getVoices();
    const spanishVoice = voices.find(v => v.lang.toLowerCase() === 'es-co')
      || voices.find(v => v.lang.toLowerCase().startsWith('es-'))
      || voices.find(v => v.lang.toLowerCase().startsWith('es'));

    if (spanishVoice) {
      utterance.voice = spanishVoice;
      utterance.lang = spanishVoice.lang;
    } else {
      utterance.lang = 'es-CO';
    }
    utterance.rate = 0.95;
    utterance.volume = 1.0;
    if (onStart) utterance.onstart = onStart;
    if (onEnd) utterance.onend = onEnd;
    utterance.onerror = () => { if (onEnd) onEnd(); };
    window.speechSynthesis.speak(utterance);
  } catch (err) {
    console.warn('Speech synthesis error:', err);
  }
}

// --- TYPES FOR POS DOMAIN ---
export type Role = 'CASHIER' | 'OWNER';

export interface ProductVariant {
  id: string;
  name: string;
  priceDelta: number;
  weightGrams?: number;
  coneUnits?: number;
  cupUnits?: number;
}

export interface ToppingOption {
  id: string;
  name: string;
  price: number;
  ingredientId?: string;
  gramsRequired?: number;
}

export interface Product {
  id: string;
  categoryId: string;
  name: string;
  description: string;
  basePrice: number;
  baseCost: number;
  emoji: string;
  badge?: string;
  variants: ProductVariant[];
  allowedToppings?: boolean;
  baseIngredientId: string;
}

export interface CartItem {
  cartItemId: string;
  productId: string;
  productName: string;
  variantId: string;
  variantName: string;
  basePrice: number;
  variantPriceDelta: number;
  unitPrice: number;
  quantity: number;
  toppings: ToppingOption[];
  totalPrice: number;
  estimatedCost: number;
}

export interface Ingredient {
  id: string;
  name: string;
  category: string;
  unit: 'g' | 'kg' | 'ml' | 'unidades';
  currentStock: number;
  minStockAlert: number;
  unitCostCOP: number;
  batchCode: string;
  expiryDate: string;
}

export interface SaleRecord {
  id: string;
  saleNumber: string;
  items: CartItem[];
  subtotal: number;
  discount: number;
  total: number;
  paymentMethod: 'CASH' | 'NEQUI' | 'SPLIT';
  cashAmount?: number;
  nequiAmount?: number;
  nequiSenderName?: string;
  nequiChannel?: string;
  timestamp: string;
  dateStr: string;
  cashierName: string;
  status: 'COMPLETED' | 'CANCELLED';
  cogsCost: number;
}

export interface CashShift {
  id: string;
  cashierName: string;
  openedAt: string;
  initialCash: number;
  status: 'OPEN' | 'CLOSED';
  cashIn: number;
  cashOut: number;
  expenses: { id: string; concept: string; amount: number; time: string }[];
  expectedCash?: number;
  countedCash?: number;
  difference?: number;
  closedAt?: string;
}

export interface ProtocolMessageLog {
  id: string;
  opId: string;
  fromStore: string;
  toStore: string;
  targetPhone: string;
  cmd: string;
  amount: number;
  seq: number;
  timestamp: string;
  rawPayload: string;
  hmacVerified: boolean;
  // Distinction of 3 explicit stages
  smsDispatched: boolean;  // 1. SMS enviado por red celular
  replyReceived: boolean;  // 2. Respuesta recibida del otro teléfono
  operationConfirmed: boolean; // 3. Operación validada criptográficamente
  status: 'DISPATCHED' | 'REPLY_RECEIVED' | 'CONFIRMED' | 'REJECTED' | 'FAILED';
}

export type ProtocolState =
  | 'DRAFT'
  | 'RESERVE_PENDING'
  | 'RESERVED'
  | 'WAITING_PAYMENT'
  | 'PAYMENT_MATCHED'
  | 'CANCEL_PENDING'
  | 'CANCELLED'
  | 'EXPIRED'
  | 'COORDINATION_FAILED'
  | 'UNASSIGNED'
  | 'TRANSFER_REQUESTED'
  | 'ASSIGNED_LOCAL'
  | 'CONFLICT'
  | 'RESOLVED';

// Initial Mock Ingredients Catalog (Ice Cream Parlor)
const INITIAL_INGREDIENTS: Ingredient[] = [
  { id: 'ing-1', name: 'Base Helado Vainilla Francesa', category: 'Helados', unit: 'g', currentStock: 4800, minStockAlert: 1000, unitCostCOP: 22, batchCode: 'LOT-2026-V01', expiryDate: '2026-11-20' },
  { id: 'ing-2', name: 'Base Helado Chocolate Intenso', category: 'Helados', unit: 'g', currentStock: 3500, minStockAlert: 1000, unitCostCOP: 25, batchCode: 'LOT-2026-CH02', expiryDate: '2026-11-18' },
  { id: 'ing-3', name: 'Base Helado Arequipe Manjar', category: 'Helados', unit: 'g', currentStock: 2200, minStockAlert: 800, unitCostCOP: 24, batchCode: 'LOT-2026-AR01', expiryDate: '2026-11-25' },
  { id: 'ing-4', name: 'Base Helado Fresa Silvestre', category: 'Helados', unit: 'g', currentStock: 1800, minStockAlert: 800, unitCostCOP: 23, batchCode: 'LOT-2026-FR03', expiryDate: '2026-11-15' },
  { id: 'ing-5', name: 'Base Helado Pistacho Bronte', category: 'Helados', unit: 'g', currentStock: 1200, minStockAlert: 600, unitCostCOP: 38, batchCode: 'LOT-2026-PIS01', expiryDate: '2026-12-01' },
  { id: 'ing-6', name: 'Barquillos Artesanales Dulces', category: 'Insumos', unit: 'unidades', currentStock: 85, minStockAlert: 20, unitCostCOP: 400, batchCode: 'LOT-BARQ-99', expiryDate: '2026-12-30' },
  { id: 'ing-7', name: 'Vasos Térmicos Biodegradables', category: 'Insumos', unit: 'unidades', currentStock: 140, minStockAlert: 30, unitCostCOP: 250, batchCode: 'LOT-VAS-102', expiryDate: '2027-01-01' },
  { id: 'ing-8', name: 'Chispas de Chocolate Semiamargo', category: 'Toppings', unit: 'g', currentStock: 1200, minStockAlert: 300, unitCostCOP: 28, batchCode: 'LOT-CHIS-44', expiryDate: '2027-02-15' },
  { id: 'ing-9', name: 'Salsa Arequipe Tradicional', category: 'Toppings', unit: 'g', currentStock: 950, minStockAlert: 250, unitCostCOP: 20, batchCode: 'LOT-AREQ-12', expiryDate: '2026-12-10' },
  { id: 'ing-10', name: 'Crema Chantilly Batida', category: 'Toppings', unit: 'ml', currentStock: 800, minStockAlert: 200, unitCostCOP: 30, batchCode: 'LOT-CHAN-08', expiryDate: '2026-11-05' }
];

// Initial Catalog Categories & Products
const CATEGORIES = [
  { id: 'todos', name: '🍦 Todos', icon: Sparkles },
  { id: 'helados', name: '🍨 Helados', icon: Coffee },
  { id: 'gourmet', name: '✨ Gourmet', icon: Sparkles },
  { id: 'paletas', name: '🍧 Paletas', icon: Boxes },
  { id: 'copas', name: '🧇 Copas', icon: Layers },
  { id: 'bebidas', name: '🥤 Bebidas', icon: Radio }
];

const AVAILABLE_TOPPINGS: ToppingOption[] = [
  { id: 'top-1', name: 'Chispas de Chocolate Belga', price: 1500, ingredientId: 'ing-8', gramsRequired: 15 },
  { id: 'top-2', name: 'Salsa de Arequipe Casero', price: 1500, ingredientId: 'ing-9', gramsRequired: 25 },
  { id: 'top-3', name: 'Crema Chantilly Fresca', price: 2000, ingredientId: 'ing-10', gramsRequired: 30 },
  { id: 'top-4', name: 'Lluvia de Maní Tostado', price: 1500, gramsRequired: 15 },
  { id: 'top-5', name: 'Cereza al Marrasquino', price: 1000, gramsRequired: 10 },
  { id: 'top-6', name: 'Barquillo Pirulito Crujiente', price: 1000, gramsRequired: 1 }
];

const PRODUCTS: Product[] = [
  {
    id: 'prod-1',
    categoryId: 'helados',
    name: 'Vainilla Francesa',
    description: 'Vainilla natural de Madagascar.',
    basePrice: 6000,
    baseCost: 1800,
    emoji: '🍦',
    badge: 'Top',
    baseIngredientId: 'ing-1',
    allowedToppings: true,
    variants: [
      { id: 'var-1', name: '1 Bola (80g)', priceDelta: 0, weightGrams: 80, coneUnits: 1 },
      { id: 'var-2', name: '2 Bolas (160g)', priceDelta: 5000, weightGrams: 160, coneUnits: 1 },
      { id: 'var-3', name: 'Vaso 2 Bolas', priceDelta: 5000, weightGrams: 160, cupUnits: 1 },
      { id: 'var-4', name: 'Litro (1000g)', priceDelta: 22000, weightGrams: 1000, cupUnits: 1 }
    ]
  },
  {
    id: 'prod-2',
    categoryId: 'helados',
    name: 'Chocolate Intenso 70%',
    description: 'Cacao colombiano premium.',
    basePrice: 6500,
    baseCost: 2000,
    emoji: '🍫',
    badge: 'Top',
    baseIngredientId: 'ing-2',
    allowedToppings: true,
    variants: [
      { id: 'var-1', name: '1 Bola (80g)', priceDelta: 0, weightGrams: 80, coneUnits: 1 },
      { id: 'var-2', name: '2 Bolas (160g)', priceDelta: 5000, weightGrams: 160, coneUnits: 1 },
      { id: 'var-3', name: 'Vaso 2 Bolas', priceDelta: 5000, weightGrams: 160, cupUnits: 1 },
      { id: 'var-4', name: 'Litro (1000g)', priceDelta: 23000, weightGrams: 1000, cupUnits: 1 }
    ]
  },
  {
    id: 'prod-3',
    categoryId: 'helados',
    name: 'Arequipe Manjar',
    description: 'Dulce de leche con vetas doradas.',
    basePrice: 6000,
    baseCost: 1900,
    emoji: '🍮',
    baseIngredientId: 'ing-3',
    allowedToppings: true,
    variants: [
      { id: 'var-1', name: '1 Bola (80g)', priceDelta: 0, weightGrams: 80, coneUnits: 1 },
      { id: 'var-2', name: '2 Bolas (160g)', priceDelta: 5000, weightGrams: 160, coneUnits: 1 },
      { id: 'var-3', name: 'Vaso 2 Bolas', priceDelta: 5000, weightGrams: 160, cupUnits: 1 }
    ]
  },
  {
    id: 'prod-4',
    categoryId: 'helados',
    name: 'Fresa Silvestre',
    description: 'Fruta fresca de temporada.',
    basePrice: 6000,
    baseCost: 1850,
    emoji: '🍓',
    baseIngredientId: 'ing-4',
    allowedToppings: true,
    variants: [
      { id: 'var-1', name: '1 Bola (80g)', priceDelta: 0, weightGrams: 80, coneUnits: 1 },
      { id: 'var-2', name: '2 Bolas (160g)', priceDelta: 5000, weightGrams: 160, coneUnits: 1 },
      { id: 'var-3', name: 'Vaso 2 Bolas', priceDelta: 5000, weightGrams: 160, cupUnits: 1 }
    ]
  },
  {
    id: 'prod-5',
    categoryId: 'gourmet',
    name: 'Pistacho Bronte Sicilia',
    description: 'Pistacho puro tostado importado.',
    basePrice: 8500,
    baseCost: 3200,
    emoji: '🌰',
    badge: 'Gourmet',
    baseIngredientId: 'ing-5',
    allowedToppings: true,
    variants: [
      { id: 'var-1', name: '1 Bola (80g)', priceDelta: 0, weightGrams: 80, coneUnits: 1 },
      { id: 'var-2', name: '2 Bolas (160g)', priceDelta: 6500, weightGrams: 160, coneUnits: 1 }
    ]
  },
  {
    id: 'prod-6',
    categoryId: 'paletas',
    name: 'Paleta Rellena de Leche',
    description: 'Fresa natural rellena de leche condensada.',
    basePrice: 5500,
    baseCost: 1600,
    emoji: '🍭',
    baseIngredientId: 'ing-4',
    allowedToppings: false,
    variants: [
      { id: 'var-1', name: 'Paleta Unitaria', priceDelta: 0, weightGrams: 100 }
    ]
  },
  {
    id: 'prod-7',
    categoryId: 'copas',
    name: 'Banana Split Clásica',
    description: '3 bolas, banano maduro, arequipe y chantilly.',
    basePrice: 18000,
    baseCost: 5500,
    emoji: '🍌',
    badge: 'Clásica',
    baseIngredientId: 'ing-1',
    allowedToppings: true,
    variants: [
      { id: 'var-1', name: 'Copa Completa', priceDelta: 0, weightGrams: 240, cupUnits: 1 }
    ]
  },
  {
    id: 'prod-8',
    categoryId: 'bebidas',
    name: 'Malteada Espesa',
    description: 'Batida al instante con helado y leche.',
    basePrice: 13000,
    baseCost: 3800,
    emoji: '🥤',
    baseIngredientId: 'ing-2',
    allowedToppings: true,
    variants: [
      { id: 'var-1', name: 'Vaso 16oz', priceDelta: 0, weightGrams: 200, cupUnits: 1 }
    ]
  }
];

export default function App() {
  // Navigation tabs (Mobile Bottom Navigation)
  const [mobileTab, setMobileTab] = useState<'pos' | 'cash' | 'inventory' | 'kiosk' | 'settings'>('pos');

  // Sub-screens within settings or pos
  const [settingsSection, setSettingsSection] = useState<'main' | 'sms_peer' | 'reports' | 'history' | 'code'>('main');

  // Role: Cashier (Cajera) vs Owner (Patrón)
  const [currentRole, setCurrentRole] = useState<Role>('CASHIER');
  const [showRoleModal, setShowRoleModal] = useState(false);
  const [pinInput, setPinInput] = useState('');
  const [pinError, setPinError] = useState(false);
  const OWNER_PIN = '1234';

  // --- INTER-STORE SMS CONFIGURATION («Comunicación entre tiendas») ---
  // Stored locally in localStorage and mapped to Android SharedPreferences
  const [peerStoreEnabled, setPeerStoreEnabled] = useState<boolean>(() => {
    try {
      return localStorage.getItem('ambiorix_peer_enabled') === 'true';
    } catch {
      return true;
    }
  });
  const [peerStoreName, setPeerStoreName] = useState<string>(() => {
    try {
      return localStorage.getItem('ambiorix_peer_name') || 'Pizzería';
    } catch {
      return 'Pizzería';
    }
  });
  const [peerStoreId, setPeerStoreId] = useState<string>(() => {
    try {
      return localStorage.getItem('ambiorix_peer_id') || 'STORE_02';
    } catch {
      return 'STORE_02';
    }
  });
  const [peerStorePhone, setPeerStorePhone] = useState<string>(() => {
    try {
      return localStorage.getItem('ambiorix_peer_phone') || '+573001234567';
    } catch {
      return '+573001234567';
    }
  });
  const [localStoreId, setLocalStoreId] = useState<string>(() => {
    try {
      return localStorage.getItem('ambiorix_local_id') || 'STORE_01';
    } catch {
      return 'STORE_01';
    }
  });
  const [localStoreName, setLocalStoreName] = useState<string>(() => {
    try {
      return localStorage.getItem('ambiorix_local_name') || 'Heladería Ambiorix';
    } catch {
      return 'Heladería Ambiorix';
    }
  });
  const [peerSharedSecret, setPeerSharedSecret] = useState<string>(() => {
    try {
      return localStorage.getItem('ambiorix_shared_secret') || 'ambiorix_p2p_secret_key_2026';
    } catch {
      return 'ambiorix_p2p_secret_key_2026';
    }
  });

  // Feedback banner for SMS configuration saving
  const [smsConfigSaveSuccess, setSmsConfigSaveSuccess] = useState(false);
  const [phoneValidationError, setPhoneValidationError] = useState<string | null>(null);

  // Save SMS Peer configuration
  const handleSaveSmsPeerConfig = () => {
    setPhoneValidationError(null);
    if (peerStoreEnabled && !peerStorePhone.trim()) {
      setPhoneValidationError('El número de teléfono de la otra tienda es obligatorio para la coordinación.');
      return;
    }
    if (peerStoreEnabled && !isValidPhoneNumber(peerStorePhone.trim())) {
      setPhoneValidationError('Formato inválido. Ingresa un número celular válido (ej. +573001234567 o 3001234567).');
      return;
    }

    try {
      localStorage.setItem('ambiorix_peer_enabled', peerStoreEnabled ? 'true' : 'false');
      localStorage.setItem('ambiorix_peer_name', peerStoreName.trim());
      localStorage.setItem('ambiorix_peer_id', peerStoreId.trim());
      localStorage.setItem('ambiorix_peer_phone', peerStorePhone.trim());
      localStorage.setItem('ambiorix_local_id', localStoreId.trim());
      localStorage.setItem('ambiorix_local_name', localStoreName.trim());
      localStorage.setItem('ambiorix_shared_secret', peerSharedSecret.trim());
      setSmsConfigSaveSuccess(true);
      setTimeout(() => setSmsConfigSaveSuccess(false), 3000);
    } catch (e) {
      console.warn('Storage save error', e);
    }
  };

  // Sound and TTS preferences
  const [soundEnabled, setSoundEnabled] = useState(true);
  const [ttsEnabled, setTtsEnabled] = useState(true);
  const [announceSenderName, setAnnounceSenderName] = useState(true);
  const [includeThankYou, setIncludeThankYou] = useState(true);
  const [confirmationDuration, setConfirmationDuration] = useState(6);
  const [isSpeaking, setIsSpeaking] = useState(false);

  // Business info & QR Images
  const [businessName, setBusinessName] = useState('Heladería Ambiorix');
  const [phoneText, setPhoneText] = useState('310 987 6543');
  const [qr1Image, setQr1Image] = useState<string | null>(() => {
    try {
      return localStorage.getItem('ambiorix_qr1');
    } catch {
      return null;
    }
  });

  // Ice Cream POS Cart State
  const [selectedCategory, setSelectedCategory] = useState<string>('todos');
  const [searchQuery, setSearchQuery] = useState<string>('');
  const [cart, setCart] = useState<CartItem[]>([]);
  const [discountPercent, setDiscountPercent] = useState<number>(0);

  // Product Selection Modal for Variants & Toppings
  const [selectedProductForModal, setSelectedProductForModal] = useState<Product | null>(null);
  const [modalVariantId, setModalVariantId] = useState<string>('');
  const [modalSelectedToppings, setModalSelectedToppings] = useState<string[]>([]);

  // Inventory state
  const [ingredients, setIngredients] = useState<Ingredient[]>(INITIAL_INGREDIENTS);

  // Sales History
  const [salesHistory, setSalesHistory] = useState<SaleRecord[]>([
    {
      id: 'sale-101',
      saleNumber: 'V-00101',
      items: [
        {
          cartItemId: 'ci-1',
          productId: 'prod-1',
          productName: 'Vainilla Francesa',
          variantId: 'var-2',
          variantName: '2 Bolas (160g)',
          basePrice: 6000,
          variantPriceDelta: 5000,
          unitPrice: 11000,
          quantity: 1,
          toppings: [{ id: 'top-1', name: 'Chispas de Chocolate Belga', price: 1500 }],
          totalPrice: 12500,
          estimatedCost: 3900
        }
      ],
      subtotal: 12500,
      discount: 0,
      total: 12500,
      paymentMethod: 'NEQUI',
      nequiAmount: 12500,
      nequiSenderName: 'SANTIAGO ALEJANDRO MORGAN AVILA',
      nequiChannel: 'Bre-B',
      timestamp: '11:20 a. m.',
      dateStr: 'Hoy',
      cashierName: 'Laura Gómez',
      status: 'COMPLETED',
      cogsCost: 3900
    }
  ]);

  // Cash shift state
  const [cashShift, setCashShift] = useState<CashShift>({
    id: 'shift-01',
    cashierName: 'Laura Gómez',
    openedAt: '09:00 a. m.',
    initialCash: 150000,
    status: 'OPEN',
    cashIn: 0,
    cashOut: 0,
    expenses: [
      { id: 'exp-1', concept: 'Hielo bolsa 5kg', amount: 8000, time: '10:15 a. m.' },
      { id: 'exp-2', concept: 'Servilletas', amount: 5000, time: '11:00 a. m.' }
    ]
  });

  const [showCloseShiftModal, setShowCloseShiftModal] = useState(false);
  const [countedCashInput, setCountedCashInput] = useState<string>('');
  const [shiftCloseReport, setShiftCloseReport] = useState<{ expected: number; counted: number; diff: number } | null>(null);

  // Cash In/Out Movement Modal
  const [showCashMovementModal, setShowCashMovementModal] = useState(false);
  const [cashMovementConcept, setCashMovementConcept] = useState('');
  const [cashMovementAmount, setCashMovementAmount] = useState('');
  const [cashMovementType, setCashMovementType] = useState<'EXPENSE' | 'IN' | 'OUT'>('EXPENSE');

  // Checkout Payment Modal
  const [showPaymentModal, setShowPaymentModal] = useState(false);
  const [paymentMethodTab, setPaymentMethodTab] = useState<'CASH' | 'NEQUI' | 'SPLIT'>('NEQUI');
  const [cashReceivedInput, setCashReceivedInput] = useState<string>('');

  // Green Success Screen state
  const [currentSuccessPayment, setCurrentSuccessPayment] = useState<{
    amount: number;
    senderName?: string;
    channel?: string;
    orderNumber?: string;
  } | null>(null);
  const [successTimeRemaining, setSuccessTimeRemaining] = useState<number>(6);
  const successTimerRef = useRef<NodeJS.Timeout | null>(null);

  // P2P SMS Protocol State Machine (14 states)
  const [protocolState, setProtocolState] = useState<ProtocolState>('DRAFT');
  const [activeReservationOpId, setActiveReservationOpId] = useState<string | null>(null);
  const [reservationAmount, setReservationAmount] = useState<number>(0);
  const [coordinationTimer, setCoordinationTimer] = useState<number>(120);
  const coordIntervalRef = useRef<NodeJS.Timeout | null>(null);

  // Protocol SMS logs with explicit stage breakdown
  const [smsLogs, setSmsLogs] = useState<ProtocolMessageLog[]>([
    {
      id: 'msg-init-1',
      opId: 'op-sys',
      fromStore: localStoreName,
      toStore: peerStoreName,
      targetPhone: peerStorePhone,
      cmd: 'HELLO',
      amount: 0,
      seq: 1,
      timestamp: '09:00 a. m.',
      rawPayload: `APOS|v1|msg-01|op-sys|HELLO|1|${localStoreId}|${peerStoreId}|1730000000|0||z8e7d...hmac`,
      hmacVerified: true,
      smsDispatched: true,
      replyReceived: true,
      operationConfirmed: true,
      status: 'CONFIRMED'
    }
  ]);

  // Unassigned payments list
  const [unassignedPayments, setUnassignedPayments] = useState<{
    id: string;
    amount: number;
    senderName: string;
    channel: string;
    receivedAt: string;
    rawMessage: string;
  }[]>([]);

  // Preload speech voices
  useEffect(() => {
    if ('speechSynthesis' in window) {
      window.speechSynthesis.getVoices();
      window.speechSynthesis.onvoiceschanged = () => {
        window.speechSynthesis.getVoices();
      };
    }
  }, []);

  // Sync QR to LocalStorage
  useEffect(() => {
    try {
      if (qr1Image) localStorage.setItem('ambiorix_qr1', qr1Image);
      else localStorage.removeItem('ambiorix_qr1');
    } catch (e) {
      console.warn('Storage quota error', e);
    }
  }, [qr1Image]);

  // Handle countdown on success screen
  useEffect(() => {
    if (currentSuccessPayment) {
      setSuccessTimeRemaining(confirmationDuration);
      if (successTimerRef.current) clearInterval(successTimerRef.current);
      successTimerRef.current = setInterval(() => {
        setSuccessTimeRemaining(prev => {
          if (prev <= 1) {
            if (successTimerRef.current) clearInterval(successTimerRef.current);
            setCurrentSuccessPayment(null);
            return 0;
          }
          return prev - 1;
        });
      }, 1000);
      return () => {
        if (successTimerRef.current) clearInterval(successTimerRef.current);
      };
    }
  }, [currentSuccessPayment, confirmationDuration]);

  // Protocol Coordination 120s timer
  useEffect(() => {
    if (protocolState === 'RESERVE_PENDING' || protocolState === 'RESERVED' || protocolState === 'WAITING_PAYMENT') {
      if (coordIntervalRef.current) clearInterval(coordIntervalRef.current);
      coordIntervalRef.current = setInterval(() => {
        setCoordinationTimer(prev => {
          if (prev <= 1) {
            if (coordIntervalRef.current) clearInterval(coordIntervalRef.current);
            setProtocolState('EXPIRED');
            return 0;
          }
          return prev - 1;
        });
      }, 1000);
      return () => {
        if (coordIntervalRef.current) clearInterval(coordIntervalRef.current);
      };
    } else {
      if (coordIntervalRef.current) clearInterval(coordIntervalRef.current);
    }
  }, [protocolState]);

  // Cart calculations
  const cartSubtotal = cart.reduce((sum, item) => sum + item.totalPrice, 0);
  const cartDiscountAmount = Math.round(cartSubtotal * (discountPercent / 100));
  const cartTotal = Math.max(0, cartSubtotal - cartDiscountAmount);
  const cartEstimatedCost = cart.reduce((sum, item) => sum + item.estimatedCost, 0);

  // Add Item to Cart with modal
  const openProductModal = (product: Product) => {
    setSelectedProductForModal(product);
    setModalVariantId(product.variants[0]?.id || '');
    setModalSelectedToppings([]);
  };

  const confirmAddToCart = () => {
    if (!selectedProductForModal) return;
    const variant = selectedProductForModal.variants.find(v => v.id === modalVariantId) || selectedProductForModal.variants[0];
    const toppings = AVAILABLE_TOPPINGS.filter(t => modalSelectedToppings.includes(t.id));
    const toppingsPrice = toppings.reduce((s, t) => s + t.price, 0);
    const unitPrice = selectedProductForModal.basePrice + (variant?.priceDelta || 0) + toppingsPrice;
    const estimatedCost = selectedProductForModal.baseCost + (toppings.length * 500);

    const newItem: CartItem = {
      cartItemId: `item-${Date.now()}-${Math.random().toString(36).substring(2, 6)}`,
      productId: selectedProductForModal.id,
      productName: selectedProductForModal.name,
      variantId: variant.id,
      variantName: variant.name,
      basePrice: selectedProductForModal.basePrice,
      variantPriceDelta: variant.priceDelta,
      unitPrice,
      quantity: 1,
      toppings,
      totalPrice: unitPrice,
      estimatedCost
    };

    setCart(prev => [...prev, newItem]);
    setSelectedProductForModal(null);
  };

  const updateCartQuantity = (cartItemId: string, delta: number) => {
    setCart(prev =>
      prev
        .map(item => {
          if (item.cartItemId === cartItemId) {
            const newQty = item.quantity + delta;
            return {
              ...item,
              quantity: newQty,
              totalPrice: item.unitPrice * newQty,
              estimatedCost: (item.estimatedCost / item.quantity) * newQty
            };
          }
          return item;
        })
        .filter(item => item.quantity > 0)
    );
  };

  const clearCart = () => {
    setCart([]);
    setDiscountPercent(0);
  };

  // Recipe Ingredient Deductions upon completed sale
  const deductIngredientsForSale = (items: CartItem[]) => {
    setIngredients(prevIngredients => {
      const updated = [...prevIngredients];
      items.forEach(item => {
        const prod = PRODUCTS.find(p => p.id === item.productId);
        const variant = prod?.variants.find(v => v.id === item.variantId);

        // Deduct base ice cream grams
        if (prod && variant?.weightGrams) {
          const ingIndex = updated.findIndex(i => i.id === prod.baseIngredientId);
          if (ingIndex >= 0) {
            const gramsToDeduct = variant.weightGrams * item.quantity;
            updated[ingIndex] = {
              ...updated[ingIndex],
              currentStock: Math.max(0, updated[ingIndex].currentStock - gramsToDeduct)
            };
          }
        }

        // Deduct cone unit
        if (variant?.coneUnits) {
          const coneIndex = updated.findIndex(i => i.id === 'ing-6');
          if (coneIndex >= 0) {
            updated[coneIndex] = {
              ...updated[coneIndex],
              currentStock: Math.max(0, updated[coneIndex].currentStock - (variant.coneUnits * item.quantity))
            };
          }
        }

        // Deduct cup unit
        if (variant?.cupUnits) {
          const cupIndex = updated.findIndex(i => i.id === 'ing-7');
          if (cupIndex >= 0) {
            updated[cupIndex] = {
              ...updated[cupIndex],
              currentStock: Math.max(0, updated[cupIndex].currentStock - (variant.cupUnits * item.quantity))
            };
          }
        }

        // Deduct toppings
        item.toppings.forEach(top => {
          if (top.ingredientId && top.gramsRequired) {
            const topIndex = updated.findIndex(i => i.id === top.ingredientId);
            if (topIndex >= 0) {
              updated[topIndex] = {
                ...updated[topIndex],
                currentStock: Math.max(0, updated[topIndex].currentStock - (top.gramsRequired * item.quantity))
              };
            }
          }
        });
      });
      return updated;
    });
  };

  // Complete Cash Sale
  const handleCompleteCashSale = () => {
    if (cart.length === 0) return;
    const now = new Date();
    const timeStr = now.toLocaleTimeString('es-CO', { hour: '2-digit', minute: '2-digit', hour12: true });
    const orderNo = `V-${(salesHistory.length + 101).toString().padStart(5, '0')}`;

    const newSale: SaleRecord = {
      id: `sale-${Date.now()}`,
      saleNumber: orderNo,
      items: [...cart],
      subtotal: cartSubtotal,
      discount: cartDiscountAmount,
      total: cartTotal,
      paymentMethod: 'CASH',
      cashAmount: cartTotal,
      timestamp: timeStr,
      dateStr: 'Hoy',
      cashierName: cashShift.cashierName,
      status: 'COMPLETED',
      cogsCost: cartEstimatedCost
    };

    deductIngredientsForSale(cart);
    setSalesHistory(prev => [newSale, ...prev]);

    // Update cash shift
    setCashShift(prev => ({
      ...prev,
      cashIn: prev.cashIn + cartTotal
    }));

    // Trigger Success
    if (soundEnabled) playChimeSound();
    setCurrentSuccessPayment({
      amount: cartTotal,
      senderName: 'Venta en Efectivo',
      channel: 'Caja Heladería',
      orderNumber: orderNo
    });

    clearCart();
    setShowPaymentModal(false);
  };

  // Start Nequi P2P SMS Coordination Flow using CONFIGURED PHONE NUMBER
  const handleStartNequiPayment = () => {
    if (cart.length === 0) return;

    // Check if peer store coordination is enabled and phone is valid
    if (peerStoreEnabled) {
      if (!peerStorePhone.trim() || !isValidPhoneNumber(peerStorePhone.trim())) {
        alert('⚠️ Advertencia de comunicación SMS: El número telefónico de la otra tienda no está configurado o es inválido. Configúralo en Ajustes > Comunicación entre tiendas.');
        return;
      }
    }

    const opId = `op-${Date.now().toString().slice(-6)}`;
    setActiveReservationOpId(opId);
    setReservationAmount(cartTotal);
    setCoordinationTimer(120);
    setProtocolState('RESERVE_PENDING');

    const nowStr = new Date().toLocaleTimeString('es-CO', { hour: '2-digit', minute: '2-digit' });

    // Emulate sending RESERVE SMS to the explicitly configured peerStorePhone
    const reserveLog: ProtocolMessageLog = {
      id: `msg-${Date.now()}`,
      opId,
      fromStore: localStoreName,
      toStore: peerStoreName,
      targetPhone: peerStorePhone,
      cmd: 'RESERVE',
      amount: cartTotal,
      seq: smsLogs.length + 1,
      timestamp: nowStr,
      rawPayload: `APOS|v1|msg-${Date.now()}|${opId}|RESERVE|${smsLogs.length + 1}|${localStoreId}|${peerStoreId}|${Date.now()}|120|${cartTotal}|hmac_sha256_sig`,
      hmacVerified: true,
      smsDispatched: true,        // Stage 1: SMS Enviado
      replyReceived: false,       // Stage 2: Esperando respuesta
      operationConfirmed: false,  // Stage 3: No confirmado todavía
      status: 'DISPATCHED'
    };

    setSmsLogs(prev => [reserveLog, ...prev]);

    // Simulate peer response ACK via SMS after 1.4s
    setTimeout(() => {
      // Stage 2 & 3: Response arrives & HMAC verifies
      setProtocolState('RESERVED');
      const ackLog: ProtocolMessageLog = {
        id: `msg-${Date.now() + 1}`,
        opId,
        fromStore: peerStoreName,
        toStore: localStoreName,
        targetPhone: peerStorePhone,
        cmd: 'ACK',
        amount: cartTotal,
        seq: smsLogs.length + 2,
        timestamp: new Date().toLocaleTimeString('es-CO', { hour: '2-digit', minute: '2-digit' }),
        rawPayload: `APOS|v1|msg-${Date.now() + 1}|${opId}|ACK|${smsLogs.length + 2}|${peerStoreId}|${localStoreId}|${Date.now()}|0|OK|hmac_sha256_sig`,
        hmacVerified: true,
        smsDispatched: true,
        replyReceived: true,
        operationConfirmed: true,
        status: 'CONFIRMED'
      };
      setSmsLogs(prev => [ackLog, ...prev]);
      setProtocolState('WAITING_PAYMENT');
    }, 1400);
  };

  // Test SMS P2P HELLO Ping
  const handleTestSmsPing = () => {
    if (!peerStorePhone.trim() || !isValidPhoneNumber(peerStorePhone.trim())) {
      alert('Ingresa un número telefónico válido antes de probar.');
      return;
    }

    const testLog: ProtocolMessageLog = {
      id: `msg-${Date.now()}`,
      opId: 'op-ping',
      fromStore: localStoreName,
      toStore: peerStoreName,
      targetPhone: peerStorePhone,
      cmd: 'HELLO',
      amount: 0,
      seq: smsLogs.length + 1,
      timestamp: new Date().toLocaleTimeString('es-CO', { hour: '2-digit', minute: '2-digit' }),
      rawPayload: `APOS|v1|msg-${Date.now()}|op-ping|HELLO|${smsLogs.length + 1}|${localStoreId}|${peerStoreId}|${Date.now()}|0|PING|hmac_sha256`,
      hmacVerified: true,
      smsDispatched: true,
      replyReceived: true,
      operationConfirmed: true,
      status: 'CONFIRMED'
    };
    setSmsLogs(prev => [testLog, ...prev]);
    alert(`Mensaje SMS de prueba HELLO enviado a ${peerStorePhone} (${peerStoreName}). Enlace activo.`);
  };

  // Cancel Nequi Reservation
  const handleCancelNequiReservation = () => {
    if (!activeReservationOpId) return;
    setProtocolState('CANCEL_PENDING');

    const cancelLog: ProtocolMessageLog = {
      id: `msg-${Date.now()}`,
      opId: activeReservationOpId,
      fromStore: localStoreName,
      toStore: peerStoreName,
      targetPhone: peerStorePhone,
      cmd: 'CANCEL',
      amount: reservationAmount,
      seq: smsLogs.length + 1,
      timestamp: new Date().toLocaleTimeString('es-CO', { hour: '2-digit', minute: '2-digit' }),
      rawPayload: `APOS|v1|msg-${Date.now()}|${activeReservationOpId}|CANCEL|${smsLogs.length + 1}|${localStoreId}|${peerStoreId}|${Date.now()}|0|USER_ABORT|hmac_sha256`,
      hmacVerified: true,
      smsDispatched: true,
      replyReceived: false,
      operationConfirmed: false,
      status: 'DISPATCHED'
    };
    setSmsLogs(prev => [cancelLog, ...prev]);

    setTimeout(() => {
      setProtocolState('CANCELLED');
      const cancelAckLog: ProtocolMessageLog = {
        id: `msg-${Date.now() + 2}`,
        opId: activeReservationOpId,
        fromStore: peerStoreName,
        toStore: localStoreName,
        targetPhone: peerStorePhone,
        cmd: 'CANCEL_ACK',
        amount: reservationAmount,
        seq: smsLogs.length + 2,
        timestamp: new Date().toLocaleTimeString('es-CO', { hour: '2-digit', minute: '2-digit' }),
        rawPayload: `APOS|v1|msg-${Date.now() + 2}|${activeReservationOpId}|CANCEL_ACK|${smsLogs.length + 2}|${peerStoreId}|${localStoreId}|${Date.now()}|0|RELEASED|hmac_sha256`,
        hmacVerified: true,
        smsDispatched: true,
        replyReceived: true,
        operationConfirmed: true,
        status: 'CONFIRMED'
      };
      setSmsLogs(prev => [cancelAckLog, ...prev]);
    }, 900);
  };

  // Simulate Arrival of Nequi SMS matching active reservation
  const simulateIncomingNequiBankSMS = (customAmount?: number, customSender?: string) => {
    const amt = customAmount !== undefined ? customAmount : reservationAmount > 0 ? reservationAmount : 15000;
    const sender = customSender || 'SANTIAGO ALEJANDRO MORGAN AVILA';
    const now = new Date();
    const timeStr = now.toLocaleTimeString('es-CO', { hour: '2-digit', minute: '2-digit' });

    // Check if reservation matches
    const hasActiveReservation = (protocolState === 'WAITING_PAYMENT' || protocolState === 'RESERVED') && amt === reservationAmount;

    if (hasActiveReservation && activeReservationOpId) {
      // SUCCESSFUL MATCH
      setProtocolState('PAYMENT_MATCHED');
      const orderNo = `V-${(salesHistory.length + 101).toString().padStart(5, '0')}`;

      const newSale: SaleRecord = {
        id: `sale-${Date.now()}`,
        saleNumber: orderNo,
        items: [...cart],
        subtotal: cartSubtotal,
        discount: cartDiscountAmount,
        total: amt,
        paymentMethod: 'NEQUI',
        nequiAmount: amt,
        nequiSenderName: sender,
        nequiChannel: 'Bre-B',
        timestamp: timeStr,
        dateStr: 'Hoy',
        cashierName: cashShift.cashierName,
        status: 'COMPLETED',
        cogsCost: cartEstimatedCost
      };

      deductIngredientsForSale(cart);
      setSalesHistory(prev => [newSale, ...prev]);

      // Inform other store: PAID via SMS
      const paidLog: ProtocolMessageLog = {
        id: `msg-${Date.now()}`,
        opId: activeReservationOpId,
        fromStore: localStoreName,
        toStore: peerStoreName,
        targetPhone: peerStorePhone,
        cmd: 'PAID',
        amount: amt,
        seq: smsLogs.length + 1,
        timestamp: timeStr,
        rawPayload: `APOS|v1|msg-${Date.now()}|${activeReservationOpId}|PAID|${smsLogs.length + 1}|${localStoreId}|${peerStoreId}|${Date.now()}|0|TX_CONFIRMED|hmac_sha256`,
        hmacVerified: true,
        smsDispatched: true,
        replyReceived: true,
        operationConfirmed: true,
        status: 'CONFIRMED'
      };
      setSmsLogs(prev => [paidLog, ...prev]);

      setProtocolState('ASSIGNED_LOCAL');

      // Audio & Speech
      if (soundEnabled) playChimeSound();
      if (ttsEnabled) {
        setTimeout(() => {
          speakSpanishPhrase(
            getPaymentPhrase(amt, announceSenderName ? sender : undefined, includeThankYou),
            () => setIsSpeaking(true),
            () => setIsSpeaking(false)
          );
        }, 250);
      }

      setCurrentSuccessPayment({
        amount: amt,
        senderName: sender,
        channel: 'Notificación Nequi · Bre-B',
        orderNumber: orderNo
      });

      clearCart();
      setShowPaymentModal(false);
      setActiveReservationOpId(null);
    } else {
      // UNASSIGNED PAYMENT (Independencia bancaria: never discarded, registered immediately)
      const unassignedItem = {
        id: `unassigned-${Date.now()}`,
        amount: amt,
        senderName: sender,
        channel: 'Bre-B',
        receivedAt: timeStr,
        rawMessage: `Notificacion Nequi: el negocio (${businessName}) recibió $${amt} de ${sender} el ${now.toLocaleDateString('es-CO')} a las ${timeStr} a través de Bre-B.`
      };
      setUnassignedPayments(prev => [unassignedItem, ...prev]);

      alert(`⚠️ Notificación Nequi de $${formatCOP(amt)} recibida pero quedó como 'SIN ASIGNAR' porque no había una reserva activa de ese monto en esta tienda. Se guardó en el Historial.`);
    }
  };

  // Role PIN Verification
  const handleVerifyOwnerPin = () => {
    if (pinInput === OWNER_PIN) {
      setCurrentRole('OWNER');
      setShowRoleModal(false);
      setPinInput('');
      setPinError(false);
      setSettingsSection('reports');
    } else {
      setPinError(true);
    }
  };

  const handleLogoutOwner = () => {
    setCurrentRole('CASHIER');
    setSettingsSection('main');
  };

  // Close Shift Calculation
  const handleCalculateCloseShift = () => {
    const counted = parseInt(countedCashInput.replace(/\D/g, ''), 10) || 0;
    const totalExpenses = cashShift.expenses.reduce((s, e) => s + e.amount, 0);
    const expected = cashShift.initialCash + cashShift.cashIn - totalExpenses;
    const diff = counted - expected;
    setShiftCloseReport({ expected, counted, diff });
  };

  const handleFinalizeCloseShift = () => {
    if (!shiftCloseReport) return;
    setCashShift(prev => ({
      ...prev,
      status: 'CLOSED',
      expectedCash: shiftCloseReport.expected,
      countedCash: shiftCloseReport.counted,
      difference: shiftCloseReport.diff,
      closedAt: new Date().toLocaleTimeString('es-CO', { hour: '2-digit', minute: '2-digit' })
    }));
    setShowCloseShiftModal(false);
    alert(`Caja cerrada. Arqueo: ${shiftCloseReport.diff === 0 ? 'Exacto (Cuadrado)' : shiftCloseReport.diff > 0 ? `Sobrante de ${formatCOP(shiftCloseReport.diff)}` : `Faltante de ${formatCOP(Math.abs(shiftCloseReport.diff))}`}`);
  };

  // Add Cash Movement
  const handleAddCashMovement = () => {
    const amt = parseInt(cashMovementAmount.replace(/\D/g, ''), 10);
    if (!amt || !cashMovementConcept) return;

    if (cashMovementType === 'EXPENSE') {
      setCashShift(prev => ({
        ...prev,
        expenses: [...prev.expenses, { id: `exp-${Date.now()}`, concept: cashMovementConcept, amount: amt, time: new Date().toLocaleTimeString('es-CO', { hour: '2-digit', minute: '2-digit' }) }]
      }));
    } else if (cashMovementType === 'IN') {
      setCashShift(prev => ({ ...prev, cashIn: prev.cashIn + amt }));
    } else {
      setCashShift(prev => ({ ...prev, cashOut: prev.cashOut + amt }));
    }

    setShowCashMovementModal(false);
    setCashMovementConcept('');
    setCashMovementAmount('');
  };

  // Calculations for Owner Analytics
  const totalSalesRevenue = salesHistory.filter(s => s.status === 'COMPLETED').reduce((s, sale) => s + sale.total, 0);
  const totalCogs = salesHistory.filter(s => s.status === 'COMPLETED').reduce((s, sale) => s + sale.cogsCost, 0);
  const totalExpensesAmount = cashShift.expenses.reduce((s, e) => s + e.amount, 0);
  const grossProfit = totalSalesRevenue - totalCogs;
  const netEstimatedProfit = grossProfit - totalExpensesAmount;
  const grossMarginPercent = totalSalesRevenue > 0 ? Math.round((grossProfit / totalSalesRevenue) * 100) : 0;

  return (
    <div className="min-h-screen bg-[#050811] text-slate-100 flex flex-col items-center justify-start p-0 sm:p-4 font-sans selection:bg-amber-500 selection:text-black">
      
      {/* Top Banner (Desktop only - Quick Actions) */}
      <div className="w-full max-w-md hidden sm:flex items-center justify-between py-1.5 px-3 text-[11px] text-slate-400">
        <div className="flex items-center space-x-1.5">
          <Smartphone className="w-3.5 h-3.5 text-amber-400" />
          <span className="font-bold text-slate-300">Ambiorix POS Móvil</span>
          <span>•</span>
          <span>Android 10 - 14</span>
        </div>
        <div className="flex items-center space-x-2">
          <button
            onClick={() => triggerClientSideZipDownload()}
            className="text-emerald-400 hover:text-emerald-300 font-bold flex items-center space-x-1 cursor-pointer"
          >
            <Download className="w-3 h-3" />
            <span>Descargar ZIP APK</span>
          </button>
        </div>
      </div>

      {/* --- NATIVE ANDROID MOBILE PHONE CONTAINER --- */}
      <div className="w-full max-w-md bg-[#0A0F1D] sm:rounded-[36px] sm:border sm:border-slate-800 shadow-2xl flex flex-col h-[100dvh] sm:h-[844px] overflow-hidden relative">
        
        {/* Android Status Bar */}
        <div className="bg-[#070B16] px-5 pt-2 pb-1.5 flex items-center justify-between text-[11px] font-semibold text-slate-400 select-none border-b border-slate-900">
          <div className="flex items-center space-x-1.5">
            <span className="text-white font-mono font-bold">10:42</span>
            <span className="text-[10px] text-slate-500">AM</span>
          </div>

          <div className="flex items-center space-x-2 text-[10px]">
            <span className="text-emerald-400 font-bold flex items-center gap-0.5">
              <span className="w-1.5 h-1.5 rounded-full bg-emerald-400" />
              SMS P2P
            </span>
            <span>LTE</span>
            <span>100%</span>
          </div>
        </div>

        {/* Mobile App Header */}
        <div className="bg-[#0B1224] px-4 py-2.5 flex items-center justify-between border-b border-slate-800 shadow-sm z-20">
          <div className="flex items-center space-x-2.5">
            <div className="w-8 h-8 rounded-xl bg-gradient-to-br from-amber-400 to-amber-600 flex items-center justify-center text-slate-950 font-black text-sm shadow-md shadow-amber-500/20">
              🍦
            </div>
            <div>
              <div className="flex items-center space-x-1.5">
                <h1 className="font-black text-xs tracking-wide text-white">AMBIORIX POS</h1>
                <span className="text-[9px] bg-amber-500/20 text-amber-300 font-extrabold px-1.5 py-0.2 rounded">
                  {localStoreName}
                </span>
              </div>
              <p className="text-[10px] text-slate-400 truncate max-w-[170px]">
                {peerStoreEnabled ? `Conectado a ${peerStoreName}` : 'Modo Tienda Individual'}
              </p>
            </div>
          </div>

          {/* Role Pill & Test Voice */}
          <div className="flex items-center space-x-1.5">
            <button
              onClick={() => {
                playChimeSound();
                setTimeout(() => {
                  speakSpanishPhrase(
                    getPaymentPhrase(15000, 'Santiago Morgan', includeThankYou),
                    () => setIsSpeaking(true),
                    () => setIsSpeaking(false)
                  );
                }, 250);
              }}
              className="p-1.5 rounded-lg bg-slate-900 border border-slate-800 text-amber-400 hover:text-amber-300"
              title="Probar voz en español"
            >
              <Volume2 className="w-3.5 h-3.5" />
            </button>

            {currentRole === 'CASHIER' ? (
              <button
                onClick={() => setShowRoleModal(true)}
                className="px-2 py-1 bg-slate-900 border border-slate-800 text-slate-300 hover:text-white rounded-lg text-[10px] font-bold flex items-center space-x-1 cursor-pointer"
              >
                <Lock className="w-3 h-3 text-amber-400" />
                <span>Cajera</span>
              </button>
            ) : (
              <button
                onClick={handleLogoutOwner}
                className="px-2 py-1 bg-amber-500/20 border border-amber-500/40 text-amber-300 rounded-lg text-[10px] font-bold flex items-center space-x-1 cursor-pointer"
              >
                <ShieldCheck className="w-3 h-3 text-amber-400" />
                <span>Patrón</span>
                <X className="w-2.5 h-2.5 ml-0.5 text-slate-400" />
              </button>
            )}
          </div>
        </div>

        {/* --- SCROLLABLE MAIN CONTENT AREA --- */}
        <div className="flex-1 overflow-y-auto pb-20 scrollbar-none">
          
          {/* ============================================================== */}
          {/* TAB 1: POS / VENTA HELADERÍA                                   */}
          {/* ============================================================== */}
          {mobileTab === 'pos' && (
            <div className="p-3.5 space-y-3.5 flex flex-col">
              
              {/* Category selector pills */}
              <div className="flex items-center space-x-1.5 overflow-x-auto pb-1 scrollbar-none text-[11px]">
                {CATEGORIES.map(cat => (
                  <button
                    key={cat.id}
                    onClick={() => setSelectedCategory(cat.id)}
                    className={`px-3 py-1.5 rounded-xl font-bold whitespace-nowrap transition cursor-pointer ${
                      selectedCategory === cat.id
                        ? 'bg-amber-500 text-slate-950 font-black shadow-sm'
                        : 'bg-slate-900/90 text-slate-300 border border-slate-800'
                    }`}
                  >
                    {cat.name}
                  </button>
                ))}
              </div>

              {/* Compact Search Bar */}
              <div className="relative">
                <Search className="w-3.5 h-3.5 text-slate-400 absolute left-3 top-1/2 -translate-y-1/2" />
                <input
                  type="text"
                  placeholder="Buscar sabor o producto..."
                  value={searchQuery}
                  onChange={e => setSearchQuery(e.target.value)}
                  className="w-full pl-8 pr-3 py-1.5 bg-slate-900/90 border border-slate-800 rounded-xl text-xs text-white placeholder-slate-500 focus:outline-none focus:border-amber-400"
                />
              </div>

              {/* Compact 2-Column Product Grid */}
              <div className="grid grid-cols-2 gap-2.5">
                {PRODUCTS.filter(p => {
                  const matchCat = selectedCategory === 'todos' || p.categoryId === selectedCategory;
                  const matchQuery = p.name.toLowerCase().includes(searchQuery.toLowerCase());
                  return matchCat && matchQuery;
                }).map(product => {
                  const baseIng = ingredients.find(i => i.id === product.baseIngredientId);
                  const isLowStock = baseIng && baseIng.currentStock <= baseIng.minStockAlert;
                  const isOutOfStock = baseIng && baseIng.currentStock <= 0;

                  return (
                    <div
                      key={product.id}
                      onClick={() => !isOutOfStock && openProductModal(product)}
                      className={`p-3 rounded-2xl bg-slate-900/80 border transition-all flex flex-col justify-between cursor-pointer select-none active:scale-[0.98] ${
                        isOutOfStock
                          ? 'opacity-40 border-slate-800'
                          : 'border-slate-800 hover:border-amber-500/50'
                      }`}
                    >
                      <div>
                        <div className="flex items-start justify-between">
                          <span className="text-2xl p-1 rounded-lg bg-slate-950 border border-slate-800">
                            {product.emoji}
                          </span>
                          {product.badge && (
                            <span className="text-[9px] font-black px-1.5 py-0.2 rounded bg-amber-500/20 text-amber-300 border border-amber-500/30">
                              {product.badge}
                            </span>
                          )}
                          {isLowStock && !isOutOfStock && (
                            <span className="text-[9px] font-bold px-1.5 py-0.2 rounded bg-red-500/20 text-red-300">
                              Poco
                            </span>
                          )}
                        </div>

                        <h3 className="mt-2 font-bold text-xs text-white leading-tight">
                          {product.name}
                        </h3>
                        <p className="text-[10px] text-slate-400 line-clamp-1 mt-0.5">
                          {product.description}
                        </p>
                      </div>

                      <div className="mt-2.5 pt-2 border-t border-slate-800/80 flex items-center justify-between">
                        <span className="text-xs font-black text-amber-400">
                          {formatCOP(product.basePrice)}
                        </span>
                        <span className="w-5 h-5 rounded-lg bg-amber-500/20 text-amber-300 flex items-center justify-center font-bold text-xs">
                          +
                        </span>
                      </div>
                    </div>
                  );
                })}
              </div>

              {/* Floating Bottom Cart Bar */}
              {cart.length > 0 && (
                <div className="mt-2 p-3 bg-gradient-to-r from-slate-900 to-[#0F172A] border border-amber-500/40 rounded-2xl shadow-xl flex items-center justify-between sticky bottom-2">
                  <div className="flex items-center space-x-2">
                    <div className="w-8 h-8 rounded-xl bg-amber-500 text-slate-950 font-black text-xs flex items-center justify-center">
                      {cart.reduce((s, i) => s + i.quantity, 0)}
                    </div>
                    <div>
                      <span className="text-[10px] text-slate-400 block">Total a Cobrar</span>
                      <span className="text-sm font-black text-white">{formatCOP(cartTotal)}</span>
                    </div>
                  </div>

                  <div className="flex items-center space-x-1.5">
                    <button
                      onClick={clearCart}
                      className="p-2 text-slate-400 hover:text-red-400 transition"
                      title="Vaciar"
                    >
                      <Trash2 className="w-4 h-4" />
                    </button>
                    <button
                      onClick={() => {
                        setShowPaymentModal(true);
                        setPaymentMethodTab('NEQUI');
                        setCashReceivedInput(cartTotal.toString());
                      }}
                      className="px-4 py-2 bg-gradient-to-r from-amber-400 to-amber-500 text-slate-950 font-black text-xs rounded-xl shadow-md transition cursor-pointer"
                    >
                      Cobrar
                    </button>
                  </div>
                </div>
              )}
            </div>
          )}

          {/* ============================================================== */}
          {/* TAB 2: CAJA & TURNOS (ARQUEO CIEGO)                            */}
          {/* ============================================================== */}
          {mobileTab === 'cash' && (
            <div className="p-3.5 space-y-3.5">
              <div className="p-4 bg-slate-900/90 border border-slate-800 rounded-2xl">
                <div className="flex items-center justify-between">
                  <div>
                    <span className="text-[10px] text-amber-400 font-bold uppercase block">
                      Turno de Caja Activo
                    </span>
                    <h2 className="text-sm font-black text-white mt-0.5">
                      Cajera: {cashShift.cashierName}
                    </h2>
                    <p className="text-[10px] text-slate-400">Abierto hoy {cashShift.openedAt}</p>
                  </div>
                  <span className="px-2 py-0.5 rounded-full bg-emerald-500/20 text-emerald-400 font-bold text-[10px]">
                    Abierta
                  </span>
                </div>

                <div className="grid grid-cols-2 gap-2 mt-3 pt-3 border-t border-slate-800 text-xs">
                  <div>
                    <span className="text-[10px] text-slate-500 block">Base Inicial:</span>
                    <span className="font-bold text-white">{formatCOP(cashShift.initialCash)}</span>
                  </div>
                  <div>
                    <span className="text-[10px] text-slate-500 block">Ventas Efectivo:</span>
                    <span className="font-bold text-emerald-400">+{formatCOP(cashShift.cashIn)}</span>
                  </div>
                  <div>
                    <span className="text-[10px] text-slate-500 block">Gastos del Turno:</span>
                    <span className="font-bold text-red-400">
                      -{formatCOP(cashShift.expenses.reduce((s, e) => s + e.amount, 0))}
                    </span>
                  </div>
                  <div>
                    <span className="text-[10px] text-slate-500 block">Total en Gaveta:</span>
                    <span className="font-black text-amber-400">
                      {formatCOP(cashShift.initialCash + cashShift.cashIn - cashShift.expenses.reduce((s, e) => s + e.amount, 0))}
                    </span>
                  </div>
                </div>
              </div>

              {/* Action buttons */}
              <div className="grid grid-cols-2 gap-2">
                <button
                  onClick={() => setShowCashMovementModal(true)}
                  className="py-2.5 px-3 bg-slate-900 border border-slate-800 text-slate-200 font-bold text-xs rounded-xl flex items-center justify-center space-x-1.5"
                >
                  <DollarSign className="w-3.5 h-3.5 text-amber-400" />
                  <span>Registrar Gasto</span>
                </button>

                <button
                  onClick={() => {
                    setCountedCashInput('');
                    setShiftCloseReport(null);
                    setShowCloseShiftModal(true);
                  }}
                  className="py-2.5 px-3 bg-red-600/90 hover:bg-red-500 text-white font-black text-xs rounded-xl flex items-center justify-center space-x-1.5"
                >
                  <Lock className="w-3.5 h-3.5" />
                  <span>Arqueo Ciego</span>
                </button>
              </div>

              {/* Expense Ledger */}
              <div className="p-3 bg-slate-900/60 border border-slate-800 rounded-2xl">
                <h3 className="text-xs font-bold text-slate-300 mb-2">Gastos Registrados</h3>
                <div className="divide-y divide-slate-800/80 text-xs">
                  {cashShift.expenses.map(exp => (
                    <div key={exp.id} className="py-2 flex items-center justify-between">
                      <div>
                        <span className="font-semibold text-white block text-[11px]">{exp.concept}</span>
                        <span className="text-[9px] text-slate-500">{exp.time}</span>
                      </div>
                      <span className="font-black text-red-400 text-xs">-{formatCOP(exp.amount)}</span>
                    </div>
                  ))}
                </div>
              </div>
            </div>
          )}

          {/* ============================================================== */}
          {/* TAB 3: INVENTARIO & RECETAS                                    */}
          {/* ============================================================== */}
          {mobileTab === 'inventory' && (
            <div className="p-3.5 space-y-3">
              <div className="flex items-center justify-between">
                <div>
                  <h2 className="text-sm font-black text-white">Stock de Ingredientes</h2>
                  <p className="text-[10px] text-slate-400">Deducción por receta en cada venta</p>
                </div>
                <button
                  onClick={() => {
                    setIngredients(INITIAL_INGREDIENTS);
                    alert('Stock restaurado a valores iniciales');
                  }}
                  className="p-1.5 bg-slate-900 border border-slate-800 rounded-lg text-amber-400 text-xs"
                  title="Restablecer stock"
                >
                  <RefreshCw className="w-3.5 h-3.5" />
                </button>
              </div>

              <div className="space-y-2">
                {ingredients.map(ing => {
                  const isLow = ing.currentStock <= ing.minStockAlert && ing.currentStock > 0;
                  const isOut = ing.currentStock <= 0;

                  return (
                    <div
                      key={ing.id}
                      className="p-2.5 bg-slate-900/90 border border-slate-800 rounded-xl flex items-center justify-between text-xs"
                    >
                      <div>
                        <span className="font-bold text-white block text-[11px]">{ing.name}</span>
                        <span className="text-[9px] text-slate-500 font-mono">
                          Lote: {ing.batchCode} • Vence: {ing.expiryDate}
                        </span>
                      </div>

                      <div className="text-right">
                        <span className="font-black text-amber-400 block text-xs">
                          {ing.currentStock.toLocaleString('es-CO')} {ing.unit}
                        </span>
                        {isOut ? (
                          <span className="text-[9px] font-black text-red-500">Agotado</span>
                        ) : isLow ? (
                          <span className="text-[9px] font-bold text-amber-400">Stock Bajo</span>
                        ) : (
                          <span className="text-[9px] font-semibold text-emerald-400">Óptimo</span>
                        )}
                      </div>
                    </div>
                  );
                })}
              </div>
            </div>
          )}

          {/* ============================================================== */}
          {/* TAB 4: QR KIOSCO ORIGINAL                                      */}
          {/* ============================================================== */}
          {mobileTab === 'kiosk' && (
            <div className="p-4 flex flex-col items-center justify-center text-center space-y-4">
              <div>
                <span className="text-[10px] font-bold text-emerald-400 tracking-wider uppercase block">
                  ● Kiosco Nequi Activo
                </span>
                <h2 className="text-base font-black text-white mt-1">{businessName}</h2>
                <p className="text-xs text-amber-400 font-semibold">{phoneText}</p>
              </div>

              {/* Clean QR (NO added white border or card box) */}
              <div className="p-2 bg-slate-950 border border-slate-800 rounded-2xl shadow-inner">
                {qr1Image ? (
                  <img src={qr1Image} alt="QR" className="w-56 h-56 object-contain rounded-xl" />
                ) : (
                  <div className="w-56 h-56 bg-slate-900/70 rounded-xl flex flex-col items-center justify-center p-4 text-slate-500">
                    <QrCode className="w-16 h-16 text-amber-400/50 mb-2" />
                    <span className="text-[11px] font-bold text-slate-300">Sin QR cargado</span>
                  </div>
                )}
              </div>

              <label className="cursor-pointer px-3.5 py-1.5 bg-slate-900 hover:bg-slate-800 border border-slate-700 text-xs font-semibold text-slate-300 rounded-xl flex items-center space-x-1.5">
                <Upload className="w-3.5 h-3.5 text-amber-400" />
                <span>Cargar imagen original de QR</span>
                <input
                  type="file"
                  accept="image/*"
                  className="hidden"
                  onChange={e => {
                    const file = e.target.files?.[0];
                    if (!file) return;
                    const reader = new FileReader();
                    reader.onload = ev => {
                      if (ev.target?.result) setQr1Image(ev.target.result as string);
                    };
                    reader.readAsDataURL(file);
                  }}
                />
              </label>
            </div>
          )}

          {/* ============================================================== */}
          {/* TAB 5: AJUSTES (INCLUYE «COMUNICACIÓN ENTRE TIENDAS»)          */}
          {/* ============================================================== */}
          {mobileTab === 'settings' && (
            <div className="p-3.5 space-y-3">
              
              {/* Settings Navigation Menu */}
              {settingsSection === 'main' && (
                <div className="space-y-2">
                  <h2 className="text-sm font-black text-white px-1 mb-2">Ajustes de la Aplicación</h2>

                  {/* Section 1: Comunicación entre tiendas (Highlighted) */}
                  <div
                    onClick={() => setSettingsSection('sms_peer')}
                    className="p-3.5 bg-gradient-to-r from-[#0F172A] to-[#1E293B] border border-amber-500/50 rounded-2xl flex items-center justify-between cursor-pointer active:scale-[0.99] transition shadow-md"
                  >
                    <div className="flex items-center space-x-3">
                      <div className="w-9 h-9 rounded-xl bg-amber-500/20 text-amber-400 flex items-center justify-center font-bold">
                        <Phone className="w-4 h-4" />
                      </div>
                      <div>
                        <div className="flex items-center space-x-1.5">
                          <span className="text-xs font-black text-white">Comunicación entre tiendas</span>
                          <span className="text-[9px] bg-amber-500/20 text-amber-300 font-bold px-1.5 rounded">SMS P2P</span>
                        </div>
                        <p className="text-[10px] text-slate-400 mt-0.5">
                          {peerStoreEnabled ? `Destino: ${peerStorePhone} (${peerStoreName})` : 'Desactivada'}
                        </p>
                      </div>
                    </div>
                    <ChevronRight className="w-4 h-4 text-slate-400" />
                  </div>

                  {/* Section 2: Informes y márgenes del Patrón */}
                  <div
                    onClick={() => {
                      if (currentRole === 'OWNER') {
                        setSettingsSection('reports');
                      } else {
                        setShowRoleModal(true);
                      }
                    }}
                    className="p-3.5 bg-slate-900/90 border border-slate-800 rounded-2xl flex items-center justify-between cursor-pointer active:scale-[0.99] transition"
                  >
                    <div className="flex items-center space-x-3">
                      <div className="w-9 h-9 rounded-xl bg-slate-800 text-amber-400 flex items-center justify-center">
                        <BarChart3 className="w-4 h-4" />
                      </div>
                      <div>
                        <div className="flex items-center space-x-1.5">
                          <span className="text-xs font-bold text-white">Informes Financieros & Auditoría</span>
                          {currentRole === 'CASHIER' && <Lock className="w-3 h-3 text-amber-400" />}
                        </div>
                        <p className="text-[10px] text-slate-400 mt-0.5">Márgenes brutos, COGS y ganancias</p>
                      </div>
                    </div>
                    <ChevronRight className="w-4 h-4 text-slate-400" />
                  </div>

                  {/* Section 3: Historial de Ventas */}
                  <div
                    onClick={() => setSettingsSection('history')}
                    className="p-3.5 bg-slate-900/90 border border-slate-800 rounded-2xl flex items-center justify-between cursor-pointer active:scale-[0.99] transition"
                  >
                    <div className="flex items-center space-x-3">
                      <div className="w-9 h-9 rounded-xl bg-slate-800 text-slate-300 flex items-center justify-center">
                        <History className="w-4 h-4" />
                      </div>
                      <div>
                        <span className="text-xs font-bold text-white">Historial de Ventas & Pagos</span>
                        <p className="text-[10px] text-slate-400 mt-0.5">{salesHistory.length} transacciones</p>
                      </div>
                    </div>
                    <ChevronRight className="w-4 h-4 text-slate-400" />
                  </div>

                  {/* Section 4: Configuración de Voz y Notificaciones */}
                  <div className="p-3.5 bg-slate-900/90 border border-slate-800 rounded-2xl space-y-3">
                    <span className="text-xs font-bold text-white block">Voz y Sonido</span>
                    
                    <div className="flex items-center justify-between text-xs">
                      <span className="text-slate-300">Anunciar nombre del remitente</span>
                      <input
                        type="checkbox"
                        checked={announceSenderName}
                        onChange={e => setAnnounceSenderName(e.target.checked)}
                        className="w-4 h-4 accent-amber-500 cursor-pointer"
                      />
                    </div>

                    <div className="flex items-center justify-between text-xs">
                      <span className="text-slate-300">Agradecimiento «Gracias por tu compra»</span>
                      <input
                        type="checkbox"
                        checked={includeThankYou}
                        onChange={e => setIncludeThankYou(e.target.checked)}
                        className="w-4 h-4 accent-amber-500 cursor-pointer"
                      />
                    </div>

                    <div className="flex items-center justify-between text-xs">
                      <span className="text-slate-300">Duración pantalla verde</span>
                      <span className="font-bold text-amber-400">{confirmationDuration}s</span>
                    </div>
                  </div>

                  {/* Section 5: Descarga de código Android */}
                  <div
                    onClick={() => setSettingsSection('code')}
                    className="p-3.5 bg-slate-900/90 border border-slate-800 rounded-2xl flex items-center justify-between cursor-pointer"
                  >
                    <div className="flex items-center space-x-3">
                      <div className="w-9 h-9 rounded-xl bg-slate-800 text-emerald-400 flex items-center justify-center">
                        <FolderGit2 className="w-4 h-4" />
                      </div>
                      <div>
                        <span className="text-xs font-bold text-white">Código Fuente Android & APK</span>
                        <p className="text-[10px] text-slate-400 mt-0.5">Descarga en archivo ZIP</p>
                      </div>
                    </div>
                    <ChevronRight className="w-4 h-4 text-slate-400" />
                  </div>
                </div>
              )}

              {/* ============================================================== */}
              {/* SUB-SECTION: «COMUNICACIÓN ENTRE TIENDAS»                      */}
              {/* ============================================================== */}
              {settingsSection === 'sms_peer' && (
                <div className="space-y-3.5 animate-in fade-in duration-150">
                  <div className="flex items-center space-x-2">
                    <button
                      onClick={() => setSettingsSection('main')}
                      className="p-1.5 bg-slate-900 border border-slate-800 rounded-xl text-slate-300"
                    >
                      <ArrowLeft className="w-4 h-4" />
                    </button>
                    <div>
                      <h2 className="text-sm font-black text-white">Comunicación entre tiendas</h2>
                      <p className="text-[10px] text-slate-400">Coordinación P2P mediante SMS (Sin Internet)</p>
                    </div>
                  </div>

                  {/* Success notification banner */}
                  {smsConfigSaveSuccess && (
                    <div className="p-2.5 bg-emerald-500/20 border border-emerald-500/40 rounded-xl text-emerald-300 text-xs font-bold flex items-center space-x-1.5">
                      <CheckCheck className="w-4 h-4" />
                      <span>Configuración guardada exitosamente y persistida localmente.</span>
                    </div>
                  )}

                  {phoneValidationError && (
                    <div className="p-2.5 bg-red-500/20 border border-red-500/40 rounded-xl text-red-300 text-xs font-semibold flex items-center space-x-1.5">
                      <AlertTriangle className="w-4 h-4 text-red-400" />
                      <span>{phoneValidationError}</span>
                    </div>
                  )}

                  {/* Enable Switch */}
                  <div className="p-3 bg-slate-900 border border-slate-800 rounded-2xl flex items-center justify-between">
                    <div>
                      <span className="text-xs font-bold text-white block">Activar Coordinación SMS</span>
                      <span className="text-[10px] text-slate-400">
                        Comparte el mismo QR Nequi con otro teléfono
                      </span>
                    </div>
                    <input
                      type="checkbox"
                      checked={peerStoreEnabled}
                      onChange={e => setPeerStoreEnabled(e.target.checked)}
                      className="w-5 h-5 accent-amber-500 cursor-pointer"
                    />
                  </div>

                  {/* Form fields */}
                  <div className="p-3.5 bg-slate-900/90 border border-slate-800 rounded-2xl space-y-3 text-xs">
                    <div>
                      <label className="text-[11px] font-bold text-slate-300 block mb-1">
                        Nombre de la otra tienda:
                      </label>
                      <input
                        type="text"
                        placeholder="Ej. Pizzería, Tienda de helados Sur"
                        value={peerStoreName}
                        onChange={e => setPeerStoreName(e.target.value)}
                        className="w-full px-3 py-2 bg-slate-950 border border-slate-800 rounded-xl text-xs text-white focus:outline-none focus:border-amber-400"
                      />
                    </div>

                    <div>
                      <label className="text-[11px] font-bold text-slate-300 block mb-1">
                        Identificador de la otra tienda:
                      </label>
                      <input
                        type="text"
                        placeholder="Ej. STORE_02"
                        value={peerStoreId}
                        onChange={e => setPeerStoreId(e.target.value)}
                        className="w-full px-3 py-2 bg-slate-950 border border-slate-800 rounded-xl text-xs text-white focus:outline-none focus:border-amber-400 font-mono"
                      />
                    </div>

                    <div>
                      <label className="text-[11px] font-bold text-amber-400 block mb-1">
                        Número telefónico del otro dispositivo (Destino SMS):
                      </label>
                      <input
                        type="tel"
                        placeholder="Ej. +573001234567 o 3001234567"
                        value={peerStorePhone}
                        onChange={e => {
                          setPeerStorePhone(e.target.value);
                          setPhoneValidationError(null);
                        }}
                        className="w-full px-3 py-2 bg-slate-950 border border-slate-800 rounded-xl text-xs text-amber-400 font-mono font-bold focus:outline-none focus:border-amber-400"
                      />
                      <span className="text-[9px] text-slate-500 mt-1 block">
                        Este número recibirá los comandos de reserva, cancelación y conciliación.
                      </span>
                    </div>

                    <div>
                      <label className="text-[11px] font-bold text-slate-300 block mb-1">
                        Clave compartida HMAC-SHA-256:
                      </label>
                      <input
                        type="password"
                        value={peerSharedSecret}
                        onChange={e => setPeerSharedSecret(e.target.value)}
                        className="w-full px-3 py-2 bg-slate-950 border border-slate-800 rounded-xl text-xs text-white focus:outline-none focus:border-amber-400 font-mono"
                      />
                      <span className="text-[9px] text-slate-500 mt-1 block">
                        Debe ser idéntica en ambos dispositivos. Nunca se envía por SMS.
                      </span>
                    </div>

                    {/* Action buttons */}
                    <div className="flex space-x-2 pt-2">
                      <button
                        onClick={handleSaveSmsPeerConfig}
                        className="flex-1 py-2.5 bg-gradient-to-r from-amber-400 to-amber-500 text-slate-950 font-black text-xs rounded-xl shadow cursor-pointer"
                      >
                        Guardar Configuración
                      </button>

                      <button
                        onClick={handleTestSmsPing}
                        className="px-3 py-2.5 bg-slate-800 hover:bg-slate-700 text-slate-200 font-bold text-xs rounded-xl border border-slate-700"
                      >
                        Probar HELLO
                      </button>
                    </div>
                  </div>

                  {/* SMS Stages Visualizer */}
                  <div className="p-3 bg-slate-900 border border-slate-800 rounded-2xl space-y-2 text-xs">
                    <span className="text-[10px] font-bold text-slate-400 uppercase block">
                      Distinción de Estados del Protocolo SMS
                    </span>
                    <div className="grid grid-cols-3 gap-1.5 text-center text-[10px]">
                      <div className="p-2 rounded-xl bg-slate-950 border border-slate-800">
                        <span className="text-amber-400 font-bold block">1. Enviado</span>
                        <span className="text-slate-500 text-[9px]">Aceptado por radio</span>
                      </div>
                      <div className="p-2 rounded-xl bg-slate-950 border border-slate-800">
                        <span className="text-cyan-400 font-bold block">2. Recibido</span>
                        <span className="text-slate-500 text-[9px]">Respuesta peer</span>
                      </div>
                      <div className="p-2 rounded-xl bg-slate-950 border border-slate-800">
                        <span className="text-emerald-400 font-bold block">3. Confirmado</span>
                        <span className="text-slate-500 text-[9px]">HMAC validado</span>
                      </div>
                    </div>
                  </div>
                </div>
              )}

              {/* ============================================================== */}
              {/* SUB-SECTION: INFORMES Y MÁRGENES (PATRÓN)                     */}
              {/* ============================================================== */}
              {settingsSection === 'reports' && (
                <div className="space-y-3.5 animate-in fade-in duration-150">
                  <div className="flex items-center space-x-2">
                    <button
                      onClick={() => setSettingsSection('main')}
                      className="p-1.5 bg-slate-900 border border-slate-800 rounded-xl text-slate-300"
                    >
                      <ArrowLeft className="w-4 h-4" />
                    </button>
                    <div>
                      <h2 className="text-sm font-black text-white">Informes Financieros (Patrón)</h2>
                      <p className="text-[10px] text-slate-400">Valuación basada en recetas y arqueo de turnos</p>
                    </div>
                  </div>

                  <div className="grid grid-cols-2 gap-2 text-xs">
                    <div className="p-3 rounded-2xl bg-slate-900 border border-slate-800">
                      <span className="text-[10px] text-slate-400 block">Ventas Brutas</span>
                      <span className="text-base font-black text-white mt-0.5 block">{formatCOP(totalSalesRevenue)}</span>
                    </div>

                    <div className="p-3 rounded-2xl bg-slate-900 border border-slate-800">
                      <span className="text-[10px] text-slate-400 block">Costo Insumos (COGS)</span>
                      <span className="text-base font-black text-amber-400 mt-0.5 block">{formatCOP(totalCogs)}</span>
                    </div>

                    <div className="p-3 rounded-2xl bg-slate-900 border border-slate-800">
                      <span className="text-[10px] text-slate-400 block">Margen Bruto</span>
                      <span className="text-base font-black text-emerald-400 mt-0.5 block">{grossMarginPercent}%</span>
                    </div>

                    <div className="p-3 rounded-2xl bg-slate-900 border border-slate-800">
                      <span className="text-[10px] text-slate-400 block">Ganancia Neta Est.</span>
                      <span className="text-base font-black text-cyan-400 mt-0.5 block">{formatCOP(netEstimatedProfit)}</span>
                    </div>
                  </div>
                </div>
              )}

              {/* ============================================================== */}
              {/* SUB-SECTION: HISTORIAL DE VENTAS                              */}
              {/* ============================================================== */}
              {settingsSection === 'history' && (
                <div className="space-y-3 animate-in fade-in duration-150">
                  <div className="flex items-center space-x-2">
                    <button
                      onClick={() => setSettingsSection('main')}
                      className="p-1.5 bg-slate-900 border border-slate-800 rounded-xl text-slate-300"
                    >
                      <ArrowLeft className="w-4 h-4" />
                    </button>
                    <div>
                      <h2 className="text-sm font-black text-white">Historial de Ventas</h2>
                      <p className="text-[10px] text-slate-400">{salesHistory.length} registros guardados</p>
                    </div>
                  </div>

                  <div className="space-y-2">
                    {salesHistory.map(sale => (
                      <div
                        key={sale.id}
                        className="p-3 bg-slate-900/90 border border-slate-800 rounded-xl flex items-center justify-between text-xs"
                      >
                        <div>
                          <div className="flex items-center space-x-1.5">
                            <span className="font-bold text-white text-[11px]">{sale.saleNumber}</span>
                            <span className="px-1.5 py-0.2 rounded bg-emerald-500/20 text-emerald-400 text-[9px] font-bold">
                              {sale.paymentMethod}
                            </span>
                          </div>
                          {sale.nequiSenderName && (
                            <span className="text-[10px] text-amber-300 block mt-0.5">
                              De: {sale.nequiSenderName}
                            </span>
                          )}
                          <span className="text-[9px] text-slate-500">{sale.timestamp}</span>
                        </div>
                        <span className="font-black text-amber-400 text-xs">{formatCOP(sale.total)}</span>
                      </div>
                    ))}
                  </div>
                </div>
              )}

              {/* ============================================================== */}
              {/* SUB-SECTION: CÓDIGO ANDROID & ZIP EXPORT                      */}
              {/* ============================================================== */}
              {settingsSection === 'code' && (
                <div className="space-y-3 animate-in fade-in duration-150">
                  <div className="flex items-center space-x-2">
                    <button
                      onClick={() => setSettingsSection('main')}
                      className="p-1.5 bg-slate-900 border border-slate-800 rounded-xl text-slate-300"
                    >
                      <ArrowLeft className="w-4 h-4" />
                    </button>
                    <div>
                      <h2 className="text-sm font-black text-white">Proyecto Android Nativo</h2>
                      <p className="text-[10px] text-slate-400">Archivos Kotlin, Manifest y Gradle</p>
                    </div>
                  </div>

                  <button
                    onClick={() => triggerClientSideZipDownload()}
                    className="w-full py-3 bg-gradient-to-r from-emerald-600 to-emerald-500 hover:from-emerald-500 text-white font-black text-xs rounded-xl flex items-center justify-center space-x-2 shadow-lg"
                  >
                    <Download className="w-4 h-4" />
                    <span>Descargar Todo en Archivo .ZIP</span>
                  </button>

                  <div className="p-3 bg-slate-900 border border-slate-800 rounded-xl text-[11px] text-slate-400 space-y-1">
                    <p>• Incluye `NequiSmsParser.kt` y `PaymentDeduplicator.kt`.</p>
                    <p>• Incluye permisos `SEND_SMS`, `RECEIVE_SMS` y `READ_PHONE_STATE`.</p>
                    <p>• Compatible con compilación en GitHub Actions y Android Studio.</p>
                  </div>
                </div>
              )}
            </div>
          )}

        </div>

        {/* --- NATIVE ANDROID BOTTOM NAVIGATION BAR (5 TABS) --- */}
        <div className="absolute bottom-0 left-0 right-0 bg-[#070B16] border-t border-slate-800/90 py-1.5 px-3 flex items-center justify-around z-30 select-none">
          <button
            onClick={() => setMobileTab('pos')}
            className={`flex flex-col items-center py-1 px-2.5 rounded-xl transition ${
              mobileTab === 'pos' ? 'text-amber-400 font-bold' : 'text-slate-400 hover:text-white'
            }`}
          >
            <ShoppingBag className="w-4 h-4" />
            <span className="text-[10px] mt-0.5">Venta</span>
          </button>

          <button
            onClick={() => setMobileTab('cash')}
            className={`flex flex-col items-center py-1 px-2.5 rounded-xl transition ${
              mobileTab === 'cash' ? 'text-amber-400 font-bold' : 'text-slate-400 hover:text-white'
            }`}
          >
            <Banknote className="w-4 h-4" />
            <span className="text-[10px] mt-0.5">Caja</span>
          </button>

          <button
            onClick={() => setMobileTab('inventory')}
            className={`flex flex-col items-center py-1 px-2.5 rounded-xl transition ${
              mobileTab === 'inventory' ? 'text-amber-400 font-bold' : 'text-slate-400 hover:text-white'
            }`}
          >
            <Boxes className="w-4 h-4" />
            <span className="text-[10px] mt-0.5">Stock</span>
          </button>

          <button
            onClick={() => setMobileTab('kiosk')}
            className={`flex flex-col items-center py-1 px-2.5 rounded-xl transition ${
              mobileTab === 'kiosk' ? 'text-amber-400 font-bold' : 'text-slate-400 hover:text-white'
            }`}
          >
            <QrCode className="w-4 h-4" />
            <span className="text-[10px] mt-0.5">QR</span>
          </button>

          <button
            onClick={() => {
              setMobileTab('settings');
              setSettingsSection('main');
            }}
            className={`flex flex-col items-center py-1 px-2.5 rounded-xl transition ${
              mobileTab === 'settings' ? 'text-amber-400 font-bold' : 'text-slate-400 hover:text-white'
            }`}
          >
            <Settings className="w-4 h-4" />
            <span className="text-[10px] mt-0.5">Ajustes</span>
          </button>
        </div>

      </div>

      {/* ============================================================== */}
      {/* MODAL: PRODUCT VARIANT & TOPPING SELECTOR                     */}
      {/* ============================================================== */}
      {selectedProductForModal && (
        <div className="fixed inset-0 z-50 bg-black/85 backdrop-blur-sm flex items-end sm:items-center justify-center p-0 sm:p-4">
          <div className="w-full max-w-md bg-[#0B1224] border border-slate-800 rounded-t-[32px] sm:rounded-3xl p-5 shadow-2xl flex flex-col space-y-4 max-h-[85vh] overflow-y-auto animate-in slide-in-from-bottom duration-200">
            <div className="flex items-start justify-between">
              <div className="flex items-center space-x-2.5">
                <span className="text-3xl p-1.5 rounded-xl bg-slate-900 border border-slate-800">
                  {selectedProductForModal.emoji}
                </span>
                <div>
                  <h3 className="font-black text-sm text-white">{selectedProductForModal.name}</h3>
                  <p className="text-[11px] text-slate-400">{selectedProductForModal.description}</p>
                </div>
              </div>
              <button
                onClick={() => setSelectedProductForModal(null)}
                className="p-1 hover:bg-slate-800 rounded-lg text-slate-400 hover:text-white"
              >
                <X className="w-5 h-5" />
              </button>
            </div>

            {/* Variants */}
            <div>
              <span className="text-[10px] font-bold text-amber-400 uppercase tracking-wider block mb-1.5">
                1. Presentación / Tamaño:
              </span>
              <div className="grid grid-cols-2 gap-2">
                {selectedProductForModal.variants.map(variant => {
                  const isSelected = modalVariantId === variant.id;
                  const price = selectedProductForModal.basePrice + variant.priceDelta;
                  return (
                    <button
                      key={variant.id}
                      onClick={() => setModalVariantId(variant.id)}
                      className={`p-2.5 rounded-xl border text-left transition flex flex-col justify-between cursor-pointer ${
                        isSelected
                          ? 'bg-amber-500/20 border-amber-500 text-white'
                          : 'bg-slate-900 border-slate-800 text-slate-300'
                      }`}
                    >
                      <span className="font-bold text-xs">{variant.name}</span>
                      <span className="text-xs font-black text-amber-400 mt-1">{formatCOP(price)}</span>
                    </button>
                  );
                })}
              </div>
            </div>

            {/* Toppings (if allowed) */}
            {selectedProductForModal.allowedToppings && (
              <div>
                <span className="text-[10px] font-bold text-amber-400 uppercase tracking-wider block mb-1.5">
                  2. Toppings Opcionales:
                </span>
                <div className="grid grid-cols-2 gap-1.5 max-h-40 overflow-y-auto pr-1">
                  {AVAILABLE_TOPPINGS.map(topping => {
                    const isChecked = modalSelectedToppings.includes(topping.id);
                    return (
                      <button
                        key={topping.id}
                        onClick={() => {
                          if (isChecked) {
                            setModalSelectedToppings(prev => prev.filter(id => id !== topping.id));
                          } else {
                            setModalSelectedToppings(prev => [...prev, topping.id]);
                          }
                        }}
                        className={`p-2 rounded-xl border text-left transition flex items-center justify-between text-xs cursor-pointer ${
                          isChecked
                            ? 'bg-amber-500/20 border-amber-500 text-white'
                            : 'bg-slate-900/70 border-slate-800 text-slate-300'
                        }`}
                      >
                        <div>
                          <span className="font-semibold block text-[11px] truncate">{topping.name}</span>
                          <span className="text-[10px] text-amber-300 font-bold">+{formatCOP(topping.price)}</span>
                        </div>
                        {isChecked && <Check className="w-3.5 h-3.5 text-amber-400" />}
                      </button>
                    );
                  })}
                </div>
              </div>
            )}

            {/* Confirm Add */}
            <button
              onClick={confirmAddToCart}
              className="w-full py-3 bg-gradient-to-r from-amber-400 to-amber-500 text-slate-950 font-black text-xs rounded-xl shadow-lg cursor-pointer"
            >
              Agregar a la Orden
            </button>
          </div>
        </div>
      )}

      {/* ============================================================== */}
      {/* MODAL: CHECKOUT & PAYMENT FLOW (CASH / NEQUI / SPLIT)          */}
      {/* ============================================================== */}
      {showPaymentModal && (
        <div className="fixed inset-0 z-50 bg-black/85 backdrop-blur-sm flex items-end sm:items-center justify-center p-0 sm:p-4">
          <div className="w-full max-w-md bg-[#0B1224] border border-slate-800 rounded-t-[32px] sm:rounded-3xl p-5 shadow-2xl flex flex-col space-y-4 animate-in slide-in-from-bottom duration-200">
            <div className="flex items-center justify-between">
              <div>
                <h3 className="font-black text-sm text-white">Cobrar Orden</h3>
                <span className="text-[11px] text-slate-400">Total a Pagar: </span>
                <span className="text-base font-black text-amber-400">{formatCOP(cartTotal)}</span>
              </div>
              <button
                onClick={() => {
                  setShowPaymentModal(false);
                  if (protocolState === 'RESERVE_PENDING' || protocolState === 'RESERVED' || protocolState === 'WAITING_PAYMENT') {
                    handleCancelNequiReservation();
                  }
                }}
                className="p-1 hover:bg-slate-800 rounded-lg text-slate-400 hover:text-white"
              >
                <X className="w-5 h-5" />
              </button>
            </div>

            {/* Method Tabs */}
            <div className="grid grid-cols-3 gap-1 bg-slate-950 p-1 rounded-xl border border-slate-800">
              <button
                onClick={() => setPaymentMethodTab('NEQUI')}
                className={`py-1.5 rounded-lg text-xs font-bold transition ${
                  paymentMethodTab === 'NEQUI' ? 'bg-amber-500 text-slate-950 shadow' : 'text-slate-400'
                }`}
              >
                Nequi / SMS
              </button>
              <button
                onClick={() => setPaymentMethodTab('CASH')}
                className={`py-1.5 rounded-lg text-xs font-bold transition ${
                  paymentMethodTab === 'CASH' ? 'bg-amber-500 text-slate-950 shadow' : 'text-slate-400'
                }`}
              >
                Efectivo
              </button>
              <button
                onClick={() => setPaymentMethodTab('SPLIT')}
                className={`py-1.5 rounded-lg text-xs font-bold transition ${
                  paymentMethodTab === 'SPLIT' ? 'bg-amber-500 text-slate-950 shadow' : 'text-slate-400'
                }`}
              >
                Mixto
              </button>
            </div>

            {/* Nequi SMS flow */}
            {paymentMethodTab === 'NEQUI' && (
              <div className="flex flex-col space-y-3 text-center">
                {protocolState === 'DRAFT' || protocolState === 'CANCELLED' ? (
                  <div className="py-3 space-y-2">
                    <p className="text-[11px] text-slate-300">
                      Se enviará un SMS de reserva al número configurado de {peerStoreName} (<span className="text-amber-400 font-mono">{peerStorePhone}</span>) para coordinar el QR por 120 segundos.
                    </p>
                    <button
                      onClick={handleStartNequiPayment}
                      className="w-full py-2.5 bg-gradient-to-r from-amber-400 to-amber-500 text-slate-950 font-black text-xs rounded-xl shadow cursor-pointer"
                    >
                      Enviar SMS de Reserva ({formatCOP(cartTotal)})
                    </button>
                  </div>
                ) : protocolState === 'RESERVE_PENDING' ? (
                  <div className="py-6 space-y-2">
                    <RefreshCw className="w-7 h-7 text-amber-400 animate-spin mx-auto" />
                    <h4 className="font-bold text-xs text-white">1. SMS Enviado al número {peerStorePhone}</h4>
                    <p className="text-[10px] text-slate-400">Esperando respuesta de {peerStoreName}...</p>
                  </div>
                ) : (
                  <div className="space-y-2.5">
                    {/* Stage confirmation banner */}
                    <div className="p-2.5 bg-emerald-500/15 border border-emerald-500/30 rounded-xl flex items-center justify-between text-xs">
                      <div className="flex items-center space-x-1.5 text-emerald-300 font-bold text-[11px]">
                        <CheckCircle2 className="w-3.5 h-3.5 text-emerald-400" />
                        <span>Reserva Confirmada por SMS</span>
                      </div>
                      <span className="font-mono text-amber-400 font-bold">{coordinationTimer}s</span>
                    </div>

                    {/* QR Display */}
                    <div className="p-2 bg-slate-950 rounded-xl border border-slate-800 flex items-center justify-center">
                      {qr1Image ? (
                        <img src={qr1Image} alt="QR" className="w-40 h-40 object-contain rounded-lg" />
                      ) : (
                        <QrCode className="w-40 h-40 text-amber-400/60 p-3" />
                      )}
                    </div>

                    <p className="text-[10px] text-slate-400">
                      Transfiriendo <strong className="text-white">{formatCOP(cartTotal)}</strong>
                    </p>

                    <div className="flex space-x-2 pt-1">
                      <button
                        onClick={() => simulateIncomingNequiBankSMS(cartTotal)}
                        className="flex-1 py-2 bg-emerald-600 hover:bg-emerald-500 text-white font-black text-xs rounded-xl shadow"
                      >
                        ⚡ Simular SMS Nequi Recibido
                      </button>
                      <button
                        onClick={handleCancelNequiReservation}
                        className="px-3 py-2 bg-slate-900 border border-slate-800 text-slate-300 text-xs font-bold rounded-xl"
                      >
                        Cancelar
                      </button>
                    </div>
                  </div>
                )}
              </div>
            )}

            {/* Cash flow */}
            {paymentMethodTab === 'CASH' && (
              <div className="space-y-3">
                <div>
                  <label className="text-[11px] text-slate-400 block mb-1">Efectivo Recibido (COP):</label>
                  <input
                    type="number"
                    value={cashReceivedInput}
                    onChange={e => setCashReceivedInput(e.target.value)}
                    className="w-full px-3 py-2 bg-slate-950 border border-slate-800 rounded-xl text-base font-black text-amber-400 focus:outline-none focus:border-amber-400"
                  />
                </div>

                {parseInt(cashReceivedInput, 10) >= cartTotal && (
                  <div className="p-2.5 bg-slate-950 border border-slate-800 rounded-xl flex items-center justify-between text-xs">
                    <span className="text-slate-400">Cambio / Vueltas:</span>
                    <span className="font-black text-emerald-400 text-sm">
                      {formatCOP(parseInt(cashReceivedInput, 10) - cartTotal)}
                    </span>
                  </div>
                )}

                <button
                  disabled={parseInt(cashReceivedInput, 10) < cartTotal}
                  onClick={handleCompleteCashSale}
                  className={`w-full py-3 rounded-xl font-black text-xs transition ${
                    parseInt(cashReceivedInput, 10) >= cartTotal
                      ? 'bg-amber-500 text-slate-950 cursor-pointer'
                      : 'bg-slate-800 text-slate-500 cursor-not-allowed'
                  }`}
                >
                  Confirmar Cobro
                </button>
              </div>
            )}

            {paymentMethodTab === 'SPLIT' && (
              <div className="py-2 text-center text-xs text-slate-400">
                <p>Cobro combinado de Efectivo y Nequi.</p>
                <button
                  onClick={handleCompleteCashSale}
                  className="mt-3 w-full py-2.5 bg-amber-500 text-slate-950 font-bold rounded-xl text-xs"
                >
                  Completar Cobro
                </button>
              </div>
            )}
          </div>
        </div>
      )}

      {/* ============================================================== */}
      {/* SCREEN: GREEN CONFIRMATION SCREEN (ORIGINAL AMBIORIX PAY)      */}
      {/* ============================================================== */}
      {currentSuccessPayment && (
        <div className="fixed inset-0 z-50 bg-gradient-to-b from-[#064E3B] via-[#047857] to-[#065F46] flex flex-col items-center justify-center p-6 text-white text-center animate-in fade-in zoom-in-95 duration-200">
          <div className="w-20 h-20 rounded-full bg-white/20 border-2 border-white/40 flex items-center justify-center shadow-2xl mb-4">
            <Check className="w-12 h-12 text-white stroke-[3.5]" />
          </div>

          <span className="text-[11px] uppercase font-extrabold tracking-widest text-emerald-200/90 mb-1">
            {currentSuccessPayment.channel || 'Pago Confirmado'}
          </span>

          <h2 className="text-4xl sm:text-5xl font-black text-white tracking-tight my-2">
            {formatCOP(currentSuccessPayment.amount)}
          </h2>

          {currentSuccessPayment.senderName && (
            <div className="mt-2 px-4 py-1.5 rounded-xl bg-black/25 backdrop-blur border border-white/20">
              <span className="text-[10px] text-emerald-200 block">De:</span>
              <span className="text-sm font-extrabold text-white tracking-wide">
                {currentSuccessPayment.senderName}
              </span>
            </div>
          )}

          <div className="mt-6 flex items-center space-x-2 text-[11px] text-white/70">
            <span>Volviendo en {successTimeRemaining}s...</span>
            <button
              onClick={() => setCurrentSuccessPayment(null)}
              className="ml-2 px-2.5 py-0.5 bg-white/20 hover:bg-white/30 rounded-lg font-bold text-white cursor-pointer"
            >
              Cerrar
            </button>
          </div>
        </div>
      )}

      {/* ============================================================== */}
      {/* MODAL: ROLE PIN VERIFICATION                                   */}
      {/* ============================================================== */}
      {showRoleModal && (
        <div className="fixed inset-0 z-50 bg-black/85 backdrop-blur-sm flex items-center justify-center p-4">
          <div className="max-w-xs w-full bg-[#0B1224] border border-slate-800 rounded-3xl p-5 shadow-2xl flex flex-col space-y-3.5 text-center">
            <div className="w-10 h-10 rounded-xl bg-amber-500/20 text-amber-400 flex items-center justify-center mx-auto">
              <ShieldCheck className="w-5 h-5" />
            </div>

            <div>
              <h3 className="font-extrabold text-sm text-white">Acceso del Patrón</h3>
              <p className="text-[11px] text-slate-400 mt-0.5">PIN predeterminado: 1234</p>
            </div>

            <input
              type="password"
              maxLength={4}
              value={pinInput}
              onChange={e => setPinInput(e.target.value)}
              placeholder="••••"
              className="w-full text-center tracking-widest text-xl py-2 bg-slate-950 border border-slate-800 rounded-xl text-amber-400 focus:outline-none focus:border-amber-400"
            />

            {pinError && <span className="text-[10px] text-red-400 font-semibold">PIN incorrecto.</span>}

            <div className="flex space-x-2 pt-1">
              <button
                onClick={() => {
                  setShowRoleModal(false);
                  setPinInput('');
                  setPinError(false);
                }}
                className="flex-1 py-2 bg-slate-900 text-slate-400 rounded-xl text-xs font-bold"
              >
                Cancelar
              </button>
              <button
                onClick={handleVerifyOwnerPin}
                className="flex-1 py-2 bg-amber-500 text-slate-950 rounded-xl text-xs font-black shadow"
              >
                Ingresar
              </button>
            </div>
          </div>
        </div>
      )}

      {/* ============================================================== */}
      {/* MODAL: BLIND CASH SHIFT CLOSE (ARQUEO CIEGO)                   */}
      {/* ============================================================== */}
      {showCloseShiftModal && (
        <div className="fixed inset-0 z-50 bg-black/85 backdrop-blur-sm flex items-end sm:items-center justify-center p-0 sm:p-4">
          <div className="w-full max-w-md bg-[#0B1224] border border-slate-800 rounded-t-[32px] sm:rounded-3xl p-5 shadow-2xl flex flex-col space-y-3.5 animate-in slide-in-from-bottom duration-200">
            <div className="flex items-center justify-between">
              <h3 className="font-black text-sm text-white">Cierre Ciego de Turno</h3>
              <button
                onClick={() => setShowCloseShiftModal(false)}
                className="p-1 hover:bg-slate-800 text-slate-400 rounded-lg"
              >
                <X className="w-4 h-4" />
              </button>
            </div>

            <p className="text-[11px] text-slate-400">
              Ingresa el dinero físico total contado en la gaveta:
            </p>

            <input
              type="number"
              placeholder="Ej. 175000"
              value={countedCashInput}
              onChange={e => setCountedCashInput(e.target.value)}
              className="w-full px-3 py-2 bg-slate-950 border border-slate-800 rounded-xl text-base font-black text-amber-400 focus:outline-none focus:border-amber-400"
            />

            {!shiftCloseReport ? (
              <button
                disabled={!countedCashInput}
                onClick={handleCalculateCloseShift}
                className="w-full py-2.5 bg-amber-500 text-slate-950 font-black text-xs rounded-xl shadow cursor-pointer"
              >
                Auditar Arqueo
              </button>
            ) : (
              <div className="p-3 bg-slate-950 rounded-xl border border-slate-800 space-y-1.5 text-xs">
                <div className="flex justify-between text-slate-400 text-[11px]">
                  <span>Efectivo Teórico:</span>
                  <span className="font-bold text-white">{formatCOP(shiftCloseReport.expected)}</span>
                </div>
                <div className="flex justify-between text-slate-400 text-[11px]">
                  <span>Efectivo Contado:</span>
                  <span className="font-bold text-white">{formatCOP(shiftCloseReport.counted)}</span>
                </div>
                <div className="flex justify-between items-center pt-1.5 border-t border-slate-800 font-bold text-xs">
                  <span>Diferencia:</span>
                  <span className={shiftCloseReport.diff === 0 ? 'text-emerald-400' : shiftCloseReport.diff > 0 ? 'text-amber-400' : 'text-red-400'}>
                    {shiftCloseReport.diff === 0 ? 'Cuadrado Exacto ($0)' : shiftCloseReport.diff > 0 ? `+${formatCOP(shiftCloseReport.diff)} (Sobrante)` : `-${formatCOP(Math.abs(shiftCloseReport.diff))} (Faltante)`}
                  </span>
                </div>

                <button
                  onClick={handleFinalizeCloseShift}
                  className="w-full mt-2 py-2.5 bg-red-600 hover:bg-red-500 text-white font-black text-xs rounded-xl shadow"
                >
                  Cerrar Definitivamente el Turno
                </button>
              </div>
            )}
          </div>
        </div>
      )}

      {/* ============================================================== */}
      {/* MODAL: CASH MOVEMENT (EXPENSE / IN / OUT)                      */}
      {/* ============================================================== */}
      {showCashMovementModal && (
        <div className="fixed inset-0 z-50 bg-black/85 backdrop-blur-sm flex items-end sm:items-center justify-center p-0 sm:p-4">
          <div className="w-full max-w-md bg-[#0B1224] border border-slate-800 rounded-t-[32px] sm:rounded-3xl p-5 shadow-2xl flex flex-col space-y-3.5 animate-in slide-in-from-bottom duration-200">
            <div className="flex items-center justify-between">
              <h3 className="font-black text-sm text-white">Registrar Gasto o Entrada</h3>
              <button
                onClick={() => setShowCashMovementModal(false)}
                className="p-1 hover:bg-slate-800 text-slate-400 rounded-lg"
              >
                <X className="w-4 h-4" />
              </button>
            </div>

            <div>
              <label className="text-[11px] text-slate-400 block mb-1">Concepto / Motivo:</label>
              <input
                type="text"
                placeholder="Ej. Hielo bolsa 5kg, servilletas"
                value={cashMovementConcept}
                onChange={e => setCashMovementConcept(e.target.value)}
                className="w-full px-3 py-2 bg-slate-950 border border-slate-800 rounded-xl text-xs text-white focus:outline-none focus:border-amber-400"
              />
            </div>

            <div>
              <label className="text-[11px] text-slate-400 block mb-1">Monto en COP:</label>
              <input
                type="number"
                placeholder="Ej. 8000"
                value={cashMovementAmount}
                onChange={e => setCashMovementAmount(e.target.value)}
                className="w-full px-3 py-2 bg-slate-950 border border-slate-800 rounded-xl text-sm font-bold text-amber-400 focus:outline-none focus:border-amber-400"
              />
            </div>

            <button
              onClick={handleAddCashMovement}
              disabled={!cashMovementConcept || !cashMovementAmount}
              className="w-full py-2.5 bg-amber-500 text-slate-950 font-black text-xs rounded-xl shadow cursor-pointer disabled:opacity-50"
            >
              Guardar en Caja
            </button>
          </div>
        </div>
      )}
    </div>
  );
}
