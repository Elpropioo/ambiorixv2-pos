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
  CheckCheck,
  Calendar,
  ChevronLeft
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

export interface RecipeItem {
  ingredientId: string;
  ingredientName: string;
  quantity: number;
  unit: string;
}

export interface ComboItem {
  productId: string;
  productName: string;
  quantity: number;
}

export const UnitConverter = {
  getConversionFactor(fromUnit: string, toUnit: string): number | null {
    const from = fromUnit.trim().toLowerCase();
    const to = toUnit.trim().toLowerCase();
    if (from === to) return 1.0;
    if (from === 'g' && to === 'kg') return 0.001;
    if (from === 'kg' && to === 'g') return 1000.0;
    if (from === 'ml' && (to === 'l' || to === 'litro' || to === 'litros')) return 0.001;
    if ((from === 'l' || from === 'litro' || from === 'litros') && to === 'ml') return 1000.0;
    const isFromU = from === 'unidades' || from === 'un' || from === 'u' || from === 'unidad';
    const isToU = to === 'unidades' || to === 'un' || to === 'u' || to === 'unidad';
    if (isFromU && isToU) return 1.0;
    return null;
  },
  convert(qty: number, fromUnit: string, toUnit: string): number {
    const factor = this.getConversionFactor(fromUnit, toUnit);
    if (factor === null) throw new Error(`Unidades incompatibles: ${fromUnit} a ${toUnit}`);
    return qty * factor;
  }
};

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
  baseIngredientId?: string;
  imagePath?: string | null;
  stockQuantity: number;
  minStockAlert: number;
  unit: string;
  isActive: boolean;
  isCombo?: boolean;
  recipe?: RecipeItem[];
  comboItems?: ComboItem[];
  allowNegativeStock?: boolean;
  createdAt?: number;
  updatedAt?: number;
}

export interface CashierProfile {
  id: string;
  name: string;
  isActive: boolean;
}

export interface InventoryMovement {
  id: string;
  productId: string;
  productName: string;
  previousStock: number;
  change: number;
  resultingStock: number;
  type: string;
  reason: string;
  dateFormatted: string;
  userName: string;
}

export interface CartItem {
  cartItemId: string;
  productId: string;
  productName: string;
  variantId?: string;
  variantName?: string;
  basePrice: number;
  variantPriceDelta?: number;
  unitPrice: number;
  quantity: number;
  toppings?: ToppingOption[];
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

export interface CategoryItem {
  id: string;
  name: string;
  emoji: string;
  isActive: boolean;
  displayOrder: number;
}

const DEFAULT_CATEGORIES: CategoryItem[] = [
  { id: 'helados', name: 'Helados', emoji: '🍨', isActive: true, displayOrder: 0 },
  { id: 'gourmet', name: 'Gourmet', emoji: '✨', isActive: true, displayOrder: 1 },
  { id: 'paletas', name: 'Paletas', emoji: '🍧', isActive: true, displayOrder: 2 },
  { id: 'copas', name: 'Copas', emoji: '🧇', isActive: true, displayOrder: 3 },
  { id: 'bebidas', name: 'Bebidas', emoji: '🥤', isActive: true, displayOrder: 4 }
];

const AVAILABLE_TOPPINGS: ToppingOption[] = [
  { id: 'top-1', name: 'Chispas de Chocolate Belga', price: 1500, ingredientId: 'ing-8', gramsRequired: 15 },
  { id: 'top-2', name: 'Salsa de Arequipe Casero', price: 1500, ingredientId: 'ing-9', gramsRequired: 25 },
  { id: 'top-3', name: 'Crema Chantilly Fresca', price: 2000, ingredientId: 'ing-10', gramsRequired: 30 },
  { id: 'top-4', name: 'Lluvia de Maní Tostado', price: 1500, gramsRequired: 15 },
  { id: 'top-5', name: 'Cereza al Marrasquino', price: 1000, gramsRequired: 10 },
  { id: 'top-6', name: 'Barquillo Pirulito Crujiente', price: 1000, gramsRequired: 1 }
];

const DEFAULT_PRODUCTS: Product[] = [
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
    stockQuantity: 45,
    minStockAlert: 10,
    unit: 'unidades',
    isActive: true,
    imagePath: null,
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
    stockQuantity: 38,
    minStockAlert: 10,
    unit: 'unidades',
    isActive: true,
    imagePath: null,
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
    stockQuantity: 22,
    minStockAlert: 8,
    unit: 'unidades',
    isActive: true,
    imagePath: null,
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
    stockQuantity: 18,
    minStockAlert: 8,
    unit: 'unidades',
    isActive: true,
    imagePath: null,
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
    stockQuantity: 14,
    minStockAlert: 5,
    unit: 'unidades',
    isActive: true,
    imagePath: null,
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
    stockQuantity: 30,
    minStockAlert: 10,
    unit: 'unidades',
    isActive: true,
    imagePath: null,
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
    stockQuantity: 12,
    minStockAlert: 4,
    unit: 'unidades',
    isActive: true,
    imagePath: null,
    allowedToppings: true,
    recipe: [
      { ingredientId: 'ing-1', ingredientName: 'Base Helado Vainilla Francesa', quantity: 240, unit: 'g' },
      { ingredientId: 'ing-9', ingredientName: 'Salsa Arequipe Tradicional', quantity: 30, unit: 'g' },
      { ingredientId: 'ing-10', ingredientName: 'Crema Chantilly Batida', quantity: 40, unit: 'ml' }
    ],
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
    stockQuantity: 20,
    minStockAlert: 5,
    unit: 'unidades',
    isActive: true,
    imagePath: null,
    allowedToppings: true,
    variants: [
      { id: 'var-1', name: 'Vaso 16oz', priceDelta: 0, weightGrams: 200, cupUnits: 1 }
    ]
  },
  {
    id: 'prod-combo-1',
    categoryId: 'helados',
    name: 'Combo Pareja Dulce',
    description: '1 Banana Split Clásica + 1 Malteada Espesa a precio especial.',
    basePrice: 28000,
    baseCost: 9300,
    emoji: '🎁',
    badge: 'Combo',
    isCombo: true,
    comboItems: [
      { productId: 'prod-7', productName: 'Banana Split Clásica', quantity: 1 },
      { productId: 'prod-8', productName: 'Malteada Espesa', quantity: 1 }
    ],
    stockQuantity: 10,
    minStockAlert: 2,
    unit: 'unidades',
    isActive: true,
    imagePath: null,
    variants: [
      { id: 'var-1', name: 'Combo Completo', priceDelta: 0 }
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
      return localStorage.getItem('ambiorix_local_name') || 'Salon de Onces';
    } catch {
      return 'Salon de Onces';
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
  const [businessName, setBusinessName] = useState('Salon de Onces');
  const [phoneText, setPhoneText] = useState('310 987 6543');
  const [qr1Image, setQr1Image] = useState<string | null>(() => {
    try {
      return localStorage.getItem('ambiorix_qr1');
    } catch {
      return null;
    }
  });

  // Products Catalog (Offline Persistent)
  const [products, setProducts] = useState<Product[]>(() => {
    try {
      const saved = localStorage.getItem('ambiorix_products');
      if (saved) return JSON.parse(saved);
    } catch (e) {
      console.warn('Failed to load products from storage', e);
    }
    return DEFAULT_PRODUCTS;
  });

  // Categories Catalog (Offline Persistent)
  const [categories, setCategories] = useState<CategoryItem[]>(() => {
    try {
      const saved = localStorage.getItem('ambiorix_categories');
      if (saved) return JSON.parse(saved);
    } catch (e) {
      console.warn('Failed to load categories from storage', e);
    }
    return DEFAULT_CATEGORIES;
  });

  // Cashiers Profiles & Active Cashier (Offline Persistent)
  const [cashiers, setCashiers] = useState<CashierProfile[]>(() => {
    try {
      const saved = localStorage.getItem('ambiorix_cashiers');
      if (saved) return JSON.parse(saved);
    } catch (e) {
      console.warn('Failed to load cashiers', e);
    }
    return [
      { id: 'c-1', name: 'Laura Gómez', isActive: true },
      { id: 'c-2', name: 'Camila Restrepo', isActive: true },
      { id: 'c-3', name: 'Valentina Ortiz', isActive: true }
    ];
  });

  const [activeCashier, setActiveCashier] = useState<CashierProfile>(() => {
    try {
      const saved = localStorage.getItem('ambiorix_active_cashier');
      if (saved) return JSON.parse(saved);
    } catch (e) {
      console.warn('Failed to load active cashier', e);
    }
    return { id: 'c-1', name: 'Laura Gómez', isActive: true };
  });

  // Inventory Audit Movements Log (Offline Persistent)
  const [inventoryMovements, setInventoryMovements] = useState<InventoryMovement[]>(() => {
    try {
      const saved = localStorage.getItem('ambiorix_movements');
      if (saved) return JSON.parse(saved);
    } catch (e) {
      console.warn('Failed to load movements', e);
    }
    return [
      {
        id: 'mov-init-1',
        productId: 'prod-1',
        productName: 'Vainilla Francesa',
        previousStock: 0,
        change: 45,
        resultingStock: 45,
        type: 'PURCHASE',
        reason: 'Inventario inicial del sistema',
        dateFormatted: 'Hoy 08:30 AM',
        userName: 'Laura Gómez'
      }
    ];
  });

  // Admin Modals
  const [showCashierModal, setShowCashierModal] = useState(false);
  const [showProductAdminModal, setShowProductAdminModal] = useState(false);
  const [showCategoryAdminModal, setShowCategoryAdminModal] = useState(false);
  const [showStockAdminModal, setShowStockAdminModal] = useState(false);

  // Sync to LocalStorage
  useEffect(() => {
    try {
      localStorage.setItem('ambiorix_products', JSON.stringify(products));
    } catch (e) {
      console.warn('Storage save products error', e);
    }
  }, [products]);

  useEffect(() => {
    try {
      localStorage.setItem('ambiorix_categories', JSON.stringify(categories));
    } catch (e) {
      console.warn('Storage save categories error', e);
    }
  }, [categories]);

  useEffect(() => {
    try {
      localStorage.setItem('ambiorix_cashiers', JSON.stringify(cashiers));
      localStorage.setItem('ambiorix_active_cashier', JSON.stringify(activeCashier));
    } catch (e) {
      console.warn('Storage save cashier error', e);
    }
  }, [cashiers, activeCashier]);

  useEffect(() => {
    try {
      localStorage.setItem('ambiorix_movements', JSON.stringify(inventoryMovements.slice(0, 100)));
    } catch (e) {
      console.warn('Storage save movements error', e);
    }
  }, [inventoryMovements]);

  // Ice Cream POS Cart State
  const [selectedCategory, setSelectedCategory] = useState<string>('todos');
  const [searchQuery, setSearchQuery] = useState<string>('');
  const [cart, setCart] = useState<CartItem[]>([]);
  const [discountPercent, setDiscountPercent] = useState<number>(0);

  // Product Selection Modal for Variants & Toppings
  const [selectedProductForModal, setSelectedProductForModal] = useState<Product | null>(null);
  const [modalVariantId, setModalVariantId] = useState<string>('');
  const [modalSelectedToppings, setModalSelectedToppings] = useState<string[]>([]);

  // Inventory state (Offline Persistent)
  const [ingredients, setIngredients] = useState<Ingredient[]>(() => {
    try {
      const saved = localStorage.getItem('ambiorix_ingredients');
      if (saved) return JSON.parse(saved);
    } catch (e) {
      console.warn('Failed to load ingredients from storage', e);
    }
    return INITIAL_INGREDIENTS;
  });

  useEffect(() => {
    try {
      localStorage.setItem('ambiorix_ingredients', JSON.stringify(ingredients));
    } catch (e) {
      console.warn('Storage save ingredients error', e);
    }
  }, [ingredients]);

  // Sales History
  const [salesHistory, setSalesHistory] = useState<SaleRecord[]>([
    {
      id: 'sale-101',
      saleNumber: 'V-00101',
      items: [
        {
          cartItemId: 'ci-combo-1',
          productId: 'prod-combo-1',
          productName: 'Combo Pareja Dulce',
          basePrice: 28000,
          unitPrice: 28000,
          quantity: 1,
          totalPrice: 28000,
          estimatedCost: 8500
        }
      ],
      subtotal: 28000,
      discount: 0,
      total: 28000,
      paymentMethod: 'NEQUI',
      nequiAmount: 28000,
      nequiSenderName: 'Carlos Mendoza',
      nequiChannel: 'App Nequi',
      timestamp: '10:15 a. m.',
      dateStr: '2026-10-26',
      cashierName: 'Laura Gómez',
      status: 'COMPLETED',
      cogsCost: 8500
    },
    {
      id: 'sale-102',
      saleNumber: 'V-00102',
      items: [
        {
          cartItemId: 'ci-102-1',
          productId: 'prod-1',
          productName: 'Vainilla Francesa',
          basePrice: 6500,
          unitPrice: 6500,
          quantity: 1,
          totalPrice: 6500,
          estimatedCost: 1500
        },
        {
          cartItemId: 'ci-102-2',
          productId: 'prod-3',
          productName: 'Paleta Artesanal de Arequipe',
          basePrice: 8000,
          unitPrice: 8000,
          quantity: 1,
          totalPrice: 8000,
          estimatedCost: 2100
        }
      ],
      subtotal: 14500,
      discount: 0,
      total: 14500,
      paymentMethod: 'NEQUI',
      nequiAmount: 14500,
      nequiSenderName: 'Valentina Ríos',
      nequiChannel: 'Bre-B',
      timestamp: '11:40 a. m.',
      dateStr: '2026-10-26',
      cashierName: 'Laura Gómez',
      status: 'COMPLETED',
      cogsCost: 3600
    },
    {
      id: 'sale-103',
      saleNumber: 'V-00103',
      items: [
        {
          cartItemId: 'ci-103-1',
          productId: 'prod-2',
          productName: 'Copa Chocolate Suprema',
          basePrice: 11000,
          unitPrice: 11000,
          quantity: 2,
          totalPrice: 22000,
          estimatedCost: 5000
        }
      ],
      subtotal: 22000,
      discount: 0,
      total: 22000,
      paymentMethod: 'CASH',
      cashAmount: 22000,
      timestamp: '12:30 p. m.',
      dateStr: '2026-10-26',
      cashierName: 'Laura Gómez',
      status: 'COMPLETED',
      cogsCost: 5000
    },
    {
      id: 'sale-p1-1',
      saleNumber: 'V-00085',
      items: [
        {
          cartItemId: 'ci-p1-1',
          productId: 'prod-combo-1',
          productName: 'Combo Pareja Dulce',
          basePrice: 28000,
          unitPrice: 28000,
          quantity: 1,
          totalPrice: 28000,
          estimatedCost: 8500
        },
        {
          cartItemId: 'ci-p1-2',
          productId: 'prod-7',
          productName: 'Banana Split Clásica',
          basePrice: 17000,
          unitPrice: 17000,
          quantity: 1,
          totalPrice: 17000,
          estimatedCost: 4200
        }
      ],
      subtotal: 45000,
      discount: 0,
      total: 45000,
      paymentMethod: 'NEQUI',
      nequiAmount: 45000,
      timestamp: '03:10 p. m.',
      dateStr: '2026-10-05',
      cashierName: 'Camila Restrepo',
      status: 'COMPLETED',
      cogsCost: 12700
    }
  ]);

  // Financial Expenses
  const [financialExpenses, setFinancialExpenses] = useState<any[]>([
    {
      id: 'exp-today-1',
      amount: 18000,
      category: 'Insumos',
      description: 'Compra de 6 litros de leche entera fresca',
      paymentSource: 'Caja efectivo',
      cashierName: 'Laura Gómez',
      notes: 'Factura prov #441',
      dateStr: '2026-10-26',
      time: '10:45 AM',
      isLinkedToInventory: true,
      status: 'ACTIVE'
    },
    {
      id: 'exp-today-2',
      amount: 12000,
      category: 'Transporte',
      description: 'Domicilio express de frutas frescas',
      paymentSource: 'Caja efectivo',
      cashierName: 'Laura Gómez',
      notes: 'Envío moto',
      dateStr: '2026-10-26',
      time: '11:20 AM',
      isLinkedToInventory: false,
      status: 'ACTIVE'
    },
    {
      id: 'exp-p1-1',
      amount: 25000,
      category: 'Reposición inventario',
      description: '100 Barquillos dulces artesanales',
      paymentSource: 'Caja efectivo',
      cashierName: 'Camila Restrepo',
      notes: 'Reposición stock',
      dateStr: '2026-10-05',
      time: '02:00 PM',
      isLinkedToInventory: true,
      status: 'ACTIVE'
    },
    {
      id: 'exp-p2-1',
      amount: 35000,
      category: 'Servicios',
      description: 'Pago de servicio de internet banda ancha',
      paymentSource: 'Nequi',
      cashierName: 'Laura Gómez',
      notes: 'Recibo Claro',
      dateStr: '2026-10-12',
      time: '09:30 AM',
      isLinkedToInventory: false,
      status: 'ACTIVE'
    }
  ]);

  // Financial Payment Transactions (Decoupled from sales for precise non-duplication)
  const [financialPaymentTransactions, setFinancialPaymentTransactions] = useState<any[]>([
    {
      id: 'tx-today-1',
      method: 'NEQUI',
      amount: 28000,
      dateStr: '2026-10-26',
      time: '10:15 AM',
      status: 'CONFIRMED',
      saleId: 'sale-101',
      saleNumber: 'V-00101',
      cashierName: 'Laura Gómez',
      payerNameOrPhone: 'Carlos Mendoza (3124567890)',
      bankTransactionRef: 'M884920',
      smsEventId: 'SMS-20261026-001',
      originInstallation: 'Tienda Local',
      nequiChannel: 'App Nequi',
      entryType: 'SALE_PAYMENT'
    },
    {
      id: 'tx-today-2',
      method: 'NEQUI',
      amount: 14500,
      dateStr: '2026-10-26',
      time: '11:40 AM',
      status: 'CONFIRMED',
      saleId: 'sale-102',
      saleNumber: 'V-00102',
      cashierName: 'Laura Gómez',
      payerNameOrPhone: 'Valentina Ríos',
      bankTransactionRef: undefined, // Displays "No disponible en el SMS"
      smsEventId: 'SMS-20261026-002',
      originInstallation: 'Tienda Local',
      nequiChannel: 'Bre-B',
      entryType: 'SALE_PAYMENT'
    },
    {
      id: 'tx-today-3',
      method: 'CASH',
      amount: 22000,
      dateStr: '2026-10-26',
      time: '12:30 PM',
      status: 'CONFIRMED',
      saleId: 'sale-103',
      saleNumber: 'V-00103',
      cashierName: 'Laura Gómez',
      entryType: 'SALE_PAYMENT',
      notes: 'Pago con billete de $50.000'
    },
    {
      id: 'tx-today-4',
      method: 'CASH',
      amount: 50000,
      dateStr: '2026-10-26',
      time: '09:00 AM',
      status: 'CONFIRMED',
      cashierName: 'Laura Gómez',
      entryType: 'MANUAL_CASH_IN',
      notes: 'Fondo de sencillo adicional para vueltas'
    },
    {
      id: 'tx-p1-1',
      method: 'NEQUI',
      amount: 45000,
      dateStr: '2026-10-05',
      time: '03:10 PM',
      status: 'CONFIRMED',
      saleId: 'sale-p1-1',
      saleNumber: 'V-00085',
      cashierName: 'Camila Restrepo',
      payerNameOrPhone: 'Andrés Morales',
      bankTransactionRef: 'M765431',
      smsEventId: 'SMS-20261005-010',
      originInstallation: 'Tienda Local',
      entryType: 'SALE_PAYMENT'
    },
    {
      id: 'tx-p1-2',
      method: 'CASH',
      amount: 32000,
      dateStr: '2026-10-05',
      time: '04:25 PM',
      status: 'CONFIRMED',
      saleId: 'sale-p1-2',
      saleNumber: 'V-00086',
      cashierName: 'Camila Restrepo',
      entryType: 'SALE_PAYMENT'
    },
    {
      id: 'tx-p2-1',
      method: 'NEQUI',
      amount: 62000,
      dateStr: '2026-10-12',
      time: '02:20 PM',
      status: 'CONFIRMED',
      saleId: 'sale-p2-1',
      saleNumber: 'V-00092',
      cashierName: 'Laura Gómez',
      payerNameOrPhone: 'Mariana Duque',
      bankTransactionRef: undefined, // "No disponible en el SMS"
      smsEventId: 'SMS-20261012-005',
      originInstallation: 'Tienda Remota: Pizzería',
      entryType: 'SALE_PAYMENT'
    },
    {
      id: 'tx-p3-1',
      method: 'CASH',
      amount: 54000,
      dateStr: '2026-10-18',
      time: '05:15 PM',
      status: 'CONFIRMED',
      saleId: 'sale-p3-1',
      saleNumber: 'V-00098',
      cashierName: 'Valentina Ortiz',
      entryType: 'SALE_PAYMENT'
    }
  ]);

  // Calendar & Financial Module States
  const [calendarYear, setCalendarYear] = useState<number>(2026);
  const [calendarMonth, setCalendarMonth] = useState<number>(10); // 1..12
  const [selectedCalendarDate, setSelectedCalendarDate] = useState<string>('2026-10-26');
  const [financialTab, setFinancialTab] = useState<'nequi' | 'cash' | 'expenses' | 'products'>('nequi');
  const [inspectingNequiTx, setInspectingNequiTx] = useState<any | null>(null);
  const [inspectingProductItem, setInspectingProductItem] = useState<any | null>(null);
  const [showNewExpenseModal, setShowNewExpenseModal] = useState<boolean>(false);
  const [newExpenseAmount, setNewExpenseAmount] = useState<string>('');
  const [newExpenseCategory, setNewExpenseCategory] = useState<string>('Insumos');
  const [newExpenseDesc, setNewExpenseDesc] = useState<string>('');
  const [newExpenseSource, setNewExpenseSource] = useState<string>('Caja efectivo');
  const [newExpenseNotes, setNewExpenseNotes] = useState<string>('');
  const [newExpenseLinkedInventory, setNewExpenseLinkedInventory] = useState<boolean>(false);
  const [expenseToAnnul, setExpenseToAnnul] = useState<any | null>(null);
  const [annulReason, setAnnulReason] = useState<string>('');

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

  // Recipe & Combo Ingredient Deductions upon completed sale
  const deductIngredientsForSale = (items: CartItem[]): boolean => {
    // 1. Resolve requirements recursively
    const ingredientDeductions: Record<string, { name: string; qty: number; unit: string }> = {};
    const productDeductions: Record<string, { name: string; qty: number }> = {};

    const resolveProduct = (productId: string, multiplier: number, visited: Set<string>) => {
      if (visited.has(productId)) return; // circular reference prevention
      visited.add(productId);

      const prod = products.find(p => p.id === productId);
      if (!prod) return;

      if (prod.isCombo && prod.comboItems && prod.comboItems.length > 0) {
        prod.comboItems.forEach(ci => {
          resolveProduct(ci.productId, ci.quantity * multiplier, new Set(visited));
        });
      } else if (prod.recipe && prod.recipe.length > 0) {
        prod.recipe.forEach(rec => {
          const ing = ingredients.find(i => i.id === rec.ingredientId);
          if (ing) {
            let converted = rec.quantity;
            try {
              converted = UnitConverter.convert(rec.quantity, rec.unit, ing.unit);
            } catch {
              converted = rec.quantity;
            }
            const totalRequired = converted * multiplier;
            if (!ingredientDeductions[ing.id]) {
              ingredientDeductions[ing.id] = { name: ing.name, qty: 0, unit: ing.unit };
            }
            ingredientDeductions[ing.id].qty += totalRequired;
          }
        });
      } else {
        if (!productDeductions[prod.id]) {
          productDeductions[prod.id] = { name: prod.name, qty: 0 };
        }
        productDeductions[prod.id].qty += multiplier;
      }
    };

    items.forEach(it => {
      resolveProduct(it.productId, it.quantity, new Set());

      // Also process toppings
      (it.toppings || []).forEach(top => {
        if (top.ingredientId && top.gramsRequired) {
          const ing = ingredients.find(i => i.id === top.ingredientId);
          if (ing) {
            let converted = top.gramsRequired;
            try {
              converted = UnitConverter.convert(top.gramsRequired, 'g', ing.unit);
            } catch {
              converted = top.gramsRequired;
            }
            const total = converted * it.quantity;
            if (!ingredientDeductions[ing.id]) {
              ingredientDeductions[ing.id] = { name: ing.name, qty: 0, unit: ing.unit };
            }
            ingredientDeductions[ing.id].qty += total;
          }
        }
      });
    });

    // 2. Validate sufficient stock
    for (const [ingId, req] of Object.entries(ingredientDeductions)) {
      const ing = ingredients.find(i => i.id === ingId);
      if (ing && ing.currentStock < req.qty) {
        alert(`Stock insuficiente de '${req.name}': disponible ${ing.currentStock} ${ing.unit}, requerido ${req.qty} ${req.unit}`);
        return false;
      }
    }

    for (const [prodId, req] of Object.entries(productDeductions)) {
      const prod = products.find(p => p.id === prodId);
      if (prod && !prod.allowNegativeStock && prod.stockQuantity < req.qty) {
        alert(`Stock insuficiente del producto '${prod.name}': disponible ${prod.stockQuantity}, requerido ${req.qty}`);
        return false;
      }
    }

    // 3. Apply deductions and log movements
    const newMovements: InventoryMovement[] = [];
    const nowTime = new Date().toLocaleTimeString('es-CO', { hour: '2-digit', minute: '2-digit' });

    setIngredients(prevIngs => {
      return prevIngs.map(ing => {
        const req = ingredientDeductions[ing.id];
        if (req) {
          const newStock = Math.max(0, ing.currentStock - req.qty);
          newMovements.push({
            id: `mov-${Date.now()}-${Math.random().toString(36).substring(2, 6)}`,
            productId: ing.id,
            productName: ing.name,
            previousStock: ing.currentStock,
            change: -req.qty,
            resultingStock: newStock,
            type: 'SALE_RECIPE',
            reason: `Consumo receta venta`,
            dateFormatted: nowTime,
            userName: activeCashier.name
          });
          return { ...ing, currentStock: newStock };
        }
        return ing;
      });
    });

    setProducts(prevProds => {
      return prevProds.map(prod => {
        const req = productDeductions[prod.id];
        if (req) {
          const newStock = prod.stockQuantity - req.qty;
          newMovements.push({
            id: `mov-${Date.now()}-${Math.random().toString(36).substring(2, 6)}`,
            productId: prod.id,
            productName: prod.name,
            previousStock: prod.stockQuantity,
            change: -req.qty,
            resultingStock: newStock,
            type: 'SALE',
            reason: `Venta POS`,
            dateFormatted: nowTime,
            userName: activeCashier.name
          });
          return { ...prod, stockQuantity: newStock, updatedAt: Date.now() };
        }
        return prod;
      });
    });

    if (newMovements.length > 0) {
      setInventoryMovements(prev => [...newMovements, ...prev]);
    }
    return true;
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
      cashierName: activeCashier.name,
      status: 'COMPLETED',
      cogsCost: cartEstimatedCost
    };

    if (!deductIngredientsForSale(cart)) return;
    setSalesHistory(prev => [newSale, ...prev]);

    // Financial transaction record
    const cashTx = {
      id: `tx-${Date.now()}`,
      method: 'CASH',
      amount: cartTotal,
      dateStr: '2026-10-26',
      time: timeStr,
      status: 'CONFIRMED',
      saleId: newSale.id,
      saleNumber: orderNo,
      cashierName: activeCashier.name,
      entryType: 'SALE_PAYMENT',
      notes: 'Venta POS en efectivo'
    };
    setFinancialPaymentTransactions(prev => [cashTx, ...prev]);

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
      channel: 'Caja Salon de Onces',
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
        cashierName: activeCashier.name,
        status: 'COMPLETED',
        cogsCost: cartEstimatedCost
      };

      if (!deductIngredientsForSale(cart)) return;
      setSalesHistory(prev => [newSale, ...prev]);

      // Financial transaction record
      const nequiTx = {
        id: `tx-${Date.now()}`,
        method: 'NEQUI',
        amount: amt,
        dateStr: '2026-10-26',
        time: timeStr,
        status: 'CONFIRMED',
        saleId: newSale.id,
        saleNumber: orderNo,
        cashierName: activeCashier.name,
        payerNameOrPhone: sender,
        bankTransactionRef: `M${Math.floor(100000 + Math.random() * 900000)}`,
        smsEventId: `SMS-${Date.now()}`,
        originInstallation: 'Tienda Local',
        nequiChannel: 'Bre-B',
        entryType: 'SALE_PAYMENT'
      };
      setFinancialPaymentTransactions(prev => [nequiTx, ...prev]);

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
          <span className="font-bold text-slate-300">Ambiorix Pay Móvil</span>
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
            <img
              src="/assets/logo.png"
              alt="Logo Ambiorix Pay"
              className="w-8 h-8 rounded-full border border-amber-500/40 object-contain bg-slate-900 p-0.5 shadow-md shadow-amber-500/10"
            />
            <div>
              <div className="flex items-center space-x-1.5">
                <h1 className="font-black text-xs tracking-wide text-white">AMBIORIX PAY</h1>
                <span className="text-[9px] bg-amber-500/20 text-amber-300 font-extrabold px-1.5 py-0.2 rounded">
                  Salon de Onces
                </span>
              </div>
              <p className="text-[10px] text-slate-400 truncate max-w-[170px]">
                {peerStoreEnabled ? `Conectado a ${peerStoreName}` : 'Modo Tienda Individual'}
              </p>
            </div>
          </div>

          {/* Role Pill, Cashier Badge & Financial Calendar Action */}
          <div className="flex items-center space-x-1.5">
            {/* Active Cashier Badge */}
            <button
              onClick={() => setShowCashierModal(true)}
              className="px-2 py-1 bg-slate-900 border border-slate-800 text-slate-300 hover:text-white rounded-lg text-[10px] font-bold flex items-center space-x-1 cursor-pointer"
              title="Cambiar o editar nombre de la cajera"
            >
              <Users className="w-3 h-3 text-amber-400" />
              <span className="max-w-[70px] truncate">{activeCashier.name}</span>
            </button>

            {/* Direct Button to Open Financial History Calendar (Sound test removed as requested) */}
            <button
              onClick={() => {
                setMobileTab('settings');
                setSettingsSection('history');
              }}
              className="p-1.5 rounded-lg bg-slate-900 border border-slate-800 text-amber-400 hover:text-amber-300 cursor-pointer"
              title="Historial de Ventas, Pagos y Gastos con Calendario"
            >
              <Calendar className="w-3.5 h-3.5" />
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
            <div className="p-3.5 space-y-3 flex flex-col">
              
              {/* Owner Quick Actions Toolbar (Products, Categories, Inventory) */}
              {currentRole === 'OWNER' && (
                <div className="flex items-center gap-1.5 p-1 bg-slate-950/80 border border-amber-500/30 rounded-xl text-[10px]">
                  <button
                    onClick={() => setShowProductAdminModal(true)}
                    className="flex-1 py-1 px-2 rounded-lg bg-amber-500/10 text-amber-300 font-bold border border-amber-500/30 hover:bg-amber-500/20 flex items-center justify-center gap-1 cursor-pointer"
                  >
                    <Boxes className="w-3 h-3" />
                    <span>Productos</span>
                  </button>
                  <button
                    onClick={() => setShowCategoryAdminModal(true)}
                    className="flex-1 py-1 px-2 rounded-lg bg-amber-500/10 text-amber-300 font-bold border border-amber-500/30 hover:bg-amber-500/20 flex items-center justify-center gap-1 cursor-pointer"
                  >
                    <Layers className="w-3 h-3" />
                    <span>Categorías</span>
                  </button>
                  <button
                    onClick={() => setShowStockAdminModal(true)}
                    className="flex-1 py-1 px-2 rounded-lg bg-amber-500/10 text-amber-300 font-bold border border-amber-500/30 hover:bg-amber-500/20 flex items-center justify-center gap-1 cursor-pointer"
                  >
                    <RefreshCw className="w-3 h-3" />
                    <span>Inventario</span>
                  </button>
                </div>
              )}

              {/* Category selector pills */}
              <div className="flex items-center space-x-1.5 overflow-x-auto pb-1 scrollbar-none text-[11px]">
                {[{ id: 'todos', name: 'Todos', emoji: '🍦', isActive: true, displayOrder: -1 }, ...categories].filter(c => c.isActive).map(cat => (
                  <button
                    key={cat.id}
                    onClick={() => setSelectedCategory(cat.id)}
                    className={`px-3 py-1.5 rounded-xl font-bold whitespace-nowrap transition cursor-pointer flex items-center gap-1 ${
                      selectedCategory === cat.id
                        ? 'bg-amber-500 text-slate-950 font-black shadow-sm'
                        : 'bg-slate-900/90 text-slate-300 border border-slate-800'
                    }`}
                  >
                    <span>{cat.emoji}</span>
                    <span>{cat.name}</span>
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

              {/* Compact 2-Column Product Grid (Dynamic from State) */}
              <div className="grid grid-cols-2 gap-2.5">
                {products.filter(p => {
                  const matchCat = selectedCategory === 'todos' || p.categoryId === selectedCategory;
                  const matchQuery = p.name.toLowerCase().includes(searchQuery.toLowerCase()) || (p.description && p.description.toLowerCase().includes(searchQuery.toLowerCase()));
                  return p.isActive && matchCat && matchQuery;
                }).map(product => {
                  const isOutOfStock = product.stockQuantity <= 0;
                  const isLowStock = !isOutOfStock && product.stockQuantity <= product.minStockAlert;

                  return (
                    <div
                      key={product.id}
                      onClick={() => !isOutOfStock && openProductModal(product)}
                      className={`p-3 rounded-2xl bg-slate-900/80 border transition-all flex flex-col justify-between cursor-pointer select-none active:scale-[0.98] ${
                        isOutOfStock
                          ? 'opacity-40 border-slate-800 cursor-not-allowed'
                          : 'border-slate-800 hover:border-amber-500/50'
                      }`}
                    >
                      <div>
                        <div className="flex items-start justify-between">
                          {product.imagePath ? (
                            <img src={product.imagePath} alt={product.name} className="w-10 h-10 object-cover rounded-lg border border-slate-800" />
                          ) : (
                            <span className="text-2xl p-1 rounded-lg bg-slate-950 border border-slate-800">
                              {product.emoji}
                            </span>
                          )}

                          {isOutOfStock ? (
                            <span className="text-[9px] font-bold px-1.5 py-0.2 rounded bg-red-500/20 text-red-300">
                              Agotado
                            </span>
                          ) : isLowStock ? (
                            <span className="text-[9px] font-bold px-1.5 py-0.2 rounded bg-amber-500/20 text-amber-300">
                              {product.stockQuantity} disp.
                            </span>
                          ) : product.badge ? (
                            <span className="text-[9px] font-black px-1.5 py-0.2 rounded bg-amber-500/20 text-amber-300 border border-amber-500/30">
                              {product.badge}
                            </span>
                          ) : null}
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
                  <h2 className="text-sm font-black text-white">Inventario de Productos</h2>
                  <p className="text-[10px] text-slate-400">Existencias, mermas y auditoría</p>
                </div>
                <button
                  onClick={() => setShowStockAdminModal(true)}
                  className="px-2.5 py-1.5 bg-amber-500 text-slate-950 font-bold text-xs rounded-xl shadow cursor-pointer flex items-center gap-1"
                >
                  <RefreshCw className="w-3.5 h-3.5" />
                  <span>Ajustar Stock</span>
                </button>
              </div>

              {/* Products Stock List */}
              <div className="space-y-2">
                {products.map(prod => {
                  const isLow = prod.stockQuantity <= prod.minStockAlert && prod.stockQuantity > 0;
                  const isOut = prod.stockQuantity <= 0;

                  return (
                    <div
                      key={prod.id}
                      className="p-2.5 bg-slate-900/90 border border-slate-800 rounded-xl flex items-center justify-between text-xs"
                    >
                      <div className="flex items-center gap-2">
                        {prod.imagePath ? (
                          <img src={prod.imagePath} alt={prod.name} className="w-8 h-8 object-cover rounded-lg border border-slate-800" />
                        ) : (
                          <span className="text-lg">{prod.emoji}</span>
                        )}
                        <div>
                          <span className="font-bold text-white block text-[11px]">{prod.name}</span>
                          <span className="text-[9px] text-slate-400">
                            Precio: {formatCOP(prod.basePrice)} {currentRole === 'OWNER' && `• Costo: ${formatCOP(prod.baseCost)}`}
                          </span>
                        </div>
                      </div>

                      <div className="text-right">
                        <span className="font-black text-amber-400 block text-xs">
                          {prod.stockQuantity} {prod.unit}
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

              <div className="pt-2 border-t border-slate-800 flex items-center justify-between">
                <span className="text-xs font-bold text-slate-300">Insumos de Receta Base</span>
                <button
                  onClick={() => {
                    setIngredients(INITIAL_INGREDIENTS);
                    alert('Stock restaurado a valores iniciales');
                  }}
                  className="p-1 text-slate-400 hover:text-amber-400"
                  title="Restaurar insumos"
                >
                  <RefreshCw className="w-3.5 h-3.5" />
                </button>
              </div>

              <div className="space-y-1.5 opacity-80">
                {ingredients.slice(0, 5).map(ing => (
                  <div key={ing.id} className="p-2 bg-slate-950/60 border border-slate-900 rounded-lg flex items-center justify-between text-[11px]">
                    <span className="text-slate-300">{ing.name}</span>
                    <span className="font-mono text-amber-300/80">{ing.currentStock} {ing.unit}</span>
                  </div>
                ))}
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
              {/* ============================================================== */}
              {/* SUB-SECTION: HISTORIAL DE VENTAS, PAGOS Y GASTOS (CALENDARIO) */}
              {/* ============================================================== */}
              {settingsSection === 'history' && (() => {
                const monthNames = [
                  'Enero', 'Febrero', 'Marzo', 'Abril', 'Mayo', 'Junio',
                  'Julio', 'Agosto', 'Septiembre', 'Octubre', 'Noviembre', 'Diciembre'
                ];
                const daysInMonth = new Date(calendarYear, calendarMonth, 0).getDate();
                const firstDayDow = (new Date(calendarYear, calendarMonth - 1, 1).getDay() + 6) % 7; // 0=Mon, 6=Sun

                // Selected date financial calculations
                const selectedNequiTxs = financialPaymentTransactions.filter(
                  tx => tx.method === 'NEQUI' && tx.dateStr === selectedCalendarDate && tx.status === 'CONFIRMED'
                );
                const selectedCashTxs = financialPaymentTransactions.filter(
                  tx => tx.method === 'CASH' && tx.dateStr === selectedCalendarDate && tx.status === 'CONFIRMED'
                );
                const totalNequi = selectedNequiTxs.reduce((sum, tx) => sum + tx.amount, 0);
                const totalCash = selectedCashTxs.reduce((sum, tx) => sum + tx.amount, 0);
                const totalReceived = totalNequi + totalCash;

                const dayExpenses = financialExpenses.filter(e => e.dateStr === selectedCalendarDate);
                const activeExpenses = dayExpenses.filter(e => e.status === 'ACTIVE');
                const totalExpenses = activeExpenses.reduce((sum, e) => sum + e.amount, 0);
                const balanceNeto = totalReceived - totalExpenses;

                // Products sold grouping on selected date
                const matchingSales = salesHistory.filter(s => {
                  if (s.status !== 'COMPLETED') return false;
                  if (s.dateStr === selectedCalendarDate) return true;
                  if (selectedCalendarDate === '2026-10-26' && (s.dateStr === 'Hoy' || s.dateStr === '2026-10-26')) return true;
                  return false;
                });

                const productGroupMap: Record<string, {
                  productId: string;
                  productName: string;
                  totalQty: number;
                  unitPrice: number;
                  totalRevenue: number;
                  salesCount: number;
                  occurrences: { saleNumber: string; time: string; quantity: number; total: number; cashier: string }[];
                }> = {};

                matchingSales.forEach(sale => {
                  sale.items.forEach(item => {
                    if (!productGroupMap[item.productId]) {
                      productGroupMap[item.productId] = {
                        productId: item.productId,
                        productName: item.productName,
                        totalQty: 0,
                        unitPrice: item.unitPrice,
                        totalRevenue: 0,
                        salesCount: 0,
                        occurrences: []
                      };
                    }
                    productGroupMap[item.productId].totalQty += item.quantity;
                    productGroupMap[item.productId].totalRevenue += item.totalPrice;
                    productGroupMap[item.productId].salesCount += 1;
                    productGroupMap[item.productId].occurrences.push({
                      saleNumber: sale.saleNumber,
                      time: sale.timestamp,
                      quantity: item.quantity,
                      total: item.totalPrice,
                      cashier: sale.cashierName
                    });
                  });
                });

                const soldProductsList = Object.values(productGroupMap);

                return (
                  <div className="space-y-3 animate-in fade-in duration-150">
                    {/* Top Navigation */}
                    <div className="flex items-center justify-between">
                      <div className="flex items-center space-x-2">
                        <button
                          onClick={() => setSettingsSection('main')}
                          className="p-1.5 bg-slate-900 border border-slate-800 rounded-xl text-slate-300 hover:text-white"
                        >
                          <ArrowLeft className="w-4 h-4" />
                        </button>
                        <div>
                          <h2 className="text-sm font-black text-white">Historial de Ventas y Pagos</h2>
                          <p className="text-[10px] text-slate-400">Auditoría con calendario mensual interactivo</p>
                        </div>
                      </div>

                      <button
                        onClick={() => {
                          setCalendarYear(2026);
                          setCalendarMonth(10);
                          setSelectedCalendarDate('2026-10-26');
                        }}
                        className="px-2 py-1 bg-amber-500/10 border border-amber-500/30 text-amber-300 font-bold text-[10px] rounded-lg hover:bg-amber-500/20"
                      >
                        Hoy
                      </button>
                    </div>

                    {/* Month Navigator Header */}
                    <div className="p-3 bg-slate-900/90 border border-slate-800 rounded-2xl flex items-center justify-between">
                      <button
                        onClick={() => {
                          if (calendarMonth === 1) {
                            setCalendarYear(y => y - 1);
                            setCalendarMonth(12);
                          } else {
                            setCalendarMonth(m => m - 1);
                          }
                        }}
                        className="p-1.5 rounded-lg bg-slate-800 hover:bg-slate-700 text-slate-300"
                        title="Mes anterior"
                      >
                        <ChevronLeft className="w-4 h-4" />
                      </button>

                      <div className="text-center">
                        <span className="text-xs font-black text-white uppercase tracking-wider block">
                          {monthNames[calendarMonth - 1]} {calendarYear}
                        </span>
                        <span className="text-[9px] text-slate-400">Toca un día para inspeccionar</span>
                      </div>

                      <button
                        onClick={() => {
                          if (calendarMonth === 12) {
                            setCalendarYear(y => y + 1);
                            setCalendarMonth(1);
                          } else {
                            setCalendarMonth(m => m + 1);
                          }
                        }}
                        className="p-1.5 rounded-lg bg-slate-800 hover:bg-slate-700 text-slate-300"
                        title="Mes siguiente"
                      >
                        <ChevronRight className="w-4 h-4" />
                      </button>
                    </div>

                    {/* Monthly Calendar Grid */}
                    <div className="p-3 bg-slate-950/70 border border-slate-800 rounded-2xl">
                      {/* Day of Week Labels */}
                      <div className="grid grid-cols-7 gap-1 text-center text-[10px] font-bold text-slate-500 mb-1">
                        <div>Lun</div>
                        <div>Mar</div>
                        <div>Mié</div>
                        <div>Jue</div>
                        <div>Vie</div>
                        <div>Sáb</div>
                        <div>Dom</div>
                      </div>

                      {/* Day Grid */}
                      <div className="grid grid-cols-7 gap-1">
                        {/* Empty padding days */}
                        {Array.from({ length: firstDayDow }).map((_, idx) => (
                          <div key={`empty-${idx}`} className="h-9 rounded-lg opacity-10" />
                        ))}

                        {/* Month Days */}
                        {Array.from({ length: daysInMonth }).map((_, idx) => {
                          const dayNum = idx + 1;
                          const dateKey = `${calendarYear}-${String(calendarMonth).padStart(2, '0')}-${String(dayNum).padStart(2, '0')}`;
                          const isSelected = selectedCalendarDate === dateKey;
                          const isToday = dateKey === '2026-10-26';

                          // Has financial transactions?
                          const hasIncome = financialPaymentTransactions.some(
                            tx => tx.dateStr === dateKey && tx.status === 'CONFIRMED'
                          );
                          const hasExpenses = financialExpenses.some(
                            e => e.dateStr === dateKey && e.status === 'ACTIVE'
                          );

                          return (
                            <button
                              key={`day-${dayNum}`}
                              onClick={() => setSelectedCalendarDate(dateKey)}
                              className={`h-9 rounded-xl flex flex-col items-center justify-center transition relative ${
                                isSelected
                                  ? 'bg-amber-500 text-slate-950 font-black shadow-md shadow-amber-500/20 ring-2 ring-amber-400'
                                  : isToday
                                  ? 'bg-slate-800/90 text-amber-300 font-bold border border-amber-500/30'
                                  : 'bg-slate-900/50 text-slate-300 hover:bg-slate-800 border border-slate-800/40'
                              }`}
                            >
                              <span className="text-[11px]">{dayNum}</span>

                              {/* Activity dots */}
                              <div className="flex items-center space-x-0.5 mt-0.5">
                                {hasIncome && (
                                  <span
                                    className={`w-1.5 h-1.5 rounded-full ${
                                      isSelected ? 'bg-slate-950' : 'bg-emerald-400'
                                    }`}
                                    title="Ventas registradas"
                                  />
                                )}
                                {hasExpenses && (
                                  <span
                                    className={`w-1.5 h-1.5 rounded-full ${
                                      isSelected ? 'bg-rose-900' : 'bg-rose-400'
                                    }`}
                                    title="Gastos registrados"
                                  />
                                )}
                              </div>
                            </button>
                          );
                        })}
                      </div>

                      {/* Legend */}
                      <div className="flex items-center justify-center space-x-4 mt-2.5 pt-2 border-t border-slate-800/60 text-[9px] text-slate-400">
                        <div className="flex items-center space-x-1">
                          <span className="w-1.5 h-1.5 rounded-full bg-emerald-400" />
                          <span>Ingresos (Nequi / Efectivo)</span>
                        </div>
                        <div className="flex items-center space-x-1">
                          <span className="w-1.5 h-1.5 rounded-full bg-rose-400" />
                          <span>Gastos del día</span>
                        </div>
                      </div>
                    </div>

                    {/* Selected Day Header & 3 Balance Cards */}
                    <div className="p-3 bg-gradient-to-br from-[#0F172A] to-[#1E293B] border border-amber-500/40 rounded-2xl shadow-md space-y-2.5">
                      <div className="flex items-center justify-between">
                        <div>
                          <span className="text-[10px] text-amber-300 uppercase font-black tracking-wider block">
                            Día Seleccionado
                          </span>
                          <h3 className="text-xs font-bold text-white">
                            {selectedCalendarDate === '2026-10-26' ? 'Hoy, 26 de Octubre de 2026' : selectedCalendarDate}
                          </h3>
                        </div>
                        <div className="px-2 py-0.5 rounded-full bg-slate-900 border border-slate-700 text-[10px] text-slate-300 font-mono">
                          {selectedNequiTxs.length + selectedCashTxs.length} pagos • {activeExpenses.length} gastos
                        </div>
                      </div>

                      {/* 3 KPI Balance Cards */}
                      <div className="grid grid-cols-3 gap-1.5 text-center">
                        {/* Total Recibido */}
                        <div className="p-2 rounded-xl bg-slate-900/90 border border-emerald-500/30">
                          <span className="text-[9px] text-emerald-300 block font-bold">Total Recibido</span>
                          <span className="text-xs font-black text-emerald-400 block mt-0.5">
                            {formatCOP(totalReceived)}
                          </span>
                          <span className="text-[8px] text-slate-400 block truncate mt-0.5">
                            Nequi + Efectivo
                          </span>
                        </div>

                        {/* Total Gastos */}
                        <div className="p-2 rounded-xl bg-slate-900/90 border border-rose-500/30">
                          <span className="text-[9px] text-rose-300 block font-bold">Total Gastos</span>
                          <span className="text-xs font-black text-rose-400 block mt-0.5">
                            {formatCOP(totalExpenses)}
                          </span>
                          <span className="text-[8px] text-slate-400 block truncate mt-0.5">
                            {activeExpenses.length} gastos
                          </span>
                        </div>

                        {/* Balance Neto */}
                        <div
                          className={`p-2 rounded-xl bg-slate-900/90 border ${
                            balanceNeto >= 0 ? 'border-amber-500/40' : 'border-rose-500/50'
                          }`}
                        >
                          <span className="text-[9px] text-amber-300 block font-bold">Balance Neto</span>
                          <span
                            className={`text-xs font-black block mt-0.5 ${
                              balanceNeto >= 0 ? 'text-amber-400' : 'text-rose-400'
                            }`}
                          >
                            {formatCOP(balanceNeto)}
                          </span>
                          <span className="text-[8px] text-slate-400 block truncate mt-0.5">
                            Recibido - Gastos
                          </span>
                        </div>
                      </div>
                    </div>

                    {/* 4 Tabs Bar: Nequi, Efectivo, Gastos, Vendido */}
                    <div className="grid grid-cols-4 gap-1 p-1 bg-slate-900 border border-slate-800 rounded-xl text-[10px] font-bold">
                      <button
                        onClick={() => setFinancialTab('nequi')}
                        className={`py-1.5 rounded-lg flex flex-col items-center justify-center transition ${
                          financialTab === 'nequi'
                            ? 'bg-amber-500 text-slate-950 font-black'
                            : 'text-slate-400 hover:text-white'
                        }`}
                      >
                        <span>Nequi</span>
                        <span className="text-[8px] opacity-80">({selectedNequiTxs.length})</span>
                      </button>

                      <button
                        onClick={() => setFinancialTab('cash')}
                        className={`py-1.5 rounded-lg flex flex-col items-center justify-center transition ${
                          financialTab === 'cash'
                            ? 'bg-amber-500 text-slate-950 font-black'
                            : 'text-slate-400 hover:text-white'
                        }`}
                      >
                        <span>Efectivo</span>
                        <span className="text-[8px] opacity-80">({selectedCashTxs.length})</span>
                      </button>

                      <button
                        onClick={() => setFinancialTab('expenses')}
                        className={`py-1.5 rounded-lg flex flex-col items-center justify-center transition ${
                          financialTab === 'expenses'
                            ? 'bg-amber-500 text-slate-950 font-black'
                            : 'text-slate-400 hover:text-white'
                        }`}
                      >
                        <span>Gastos</span>
                        <span className="text-[8px] opacity-80">({activeExpenses.length})</span>
                      </button>

                      <button
                        onClick={() => setFinancialTab('products')}
                        className={`py-1.5 rounded-lg flex flex-col items-center justify-center transition ${
                          financialTab === 'products'
                            ? 'bg-amber-500 text-slate-950 font-black'
                            : 'text-slate-400 hover:text-white'
                        }`}
                      >
                        <span>Vendido</span>
                        <span className="text-[8px] opacity-80">({soldProductsList.length})</span>
                      </button>
                    </div>

                    {/* Tab 1: NEQUI */}
                    {financialTab === 'nequi' && (
                      <div className="space-y-2">
                        <div className="p-2.5 bg-slate-900/80 border border-slate-800 rounded-xl flex items-center justify-between text-xs">
                          <span className="text-slate-300 font-bold">Total Nequi del día:</span>
                          <span className="font-black text-emerald-400 text-sm">{formatCOP(totalNequi)}</span>
                        </div>

                        {selectedNequiTxs.length === 0 ? (
                          <div className="p-6 text-center text-slate-500 text-xs bg-slate-900/30 rounded-xl border border-slate-900">
                            No hay pagos Nequi registrados en esta fecha.
                          </div>
                        ) : (
                          selectedNequiTxs.map(tx => (
                            <div
                              key={tx.id}
                              onClick={() => setInspectingNequiTx(tx)}
                              className="p-3 bg-slate-900/90 border border-slate-800 hover:border-amber-500/40 rounded-xl cursor-pointer transition space-y-1.5"
                            >
                              <div className="flex items-center justify-between">
                                <div className="flex items-center space-x-1.5">
                                  <span className="px-1.5 py-0.5 rounded bg-purple-500/20 text-purple-300 font-bold text-[9px]">
                                    {tx.nequiChannel || 'Nequi'}
                                  </span>
                                  <span className="text-xs font-bold text-white">{tx.payerNameOrPhone}</span>
                                </div>
                                <span className="font-black text-emerald-400 text-xs">{formatCOP(tx.amount)}</span>
                              </div>

                              <div className="flex items-center justify-between text-[10px] text-slate-400">
                                <span>Hora: {tx.time}</span>
                                {tx.bankTransactionRef ? (
                                  <span className="text-amber-400 font-mono">Ref: {tx.bankTransactionRef}</span>
                                ) : (
                                  <span className="text-amber-400/90 italic font-semibold">
                                    No disponible en el SMS
                                  </span>
                                )}
                              </div>

                              <div className="flex items-center justify-between text-[9px] text-slate-500 pt-1 border-t border-slate-800/60">
                                <span>SMS ID: {tx.smsEventId || 'N/A'}</span>
                                <span className="text-cyan-400 font-semibold">{tx.originInstallation}</span>
                              </div>
                            </div>
                          ))
                        )}
                      </div>
                    )}

                    {/* Tab 2: EFECTIVO */}
                    {financialTab === 'cash' && (
                      <div className="space-y-2">
                        <div className="p-2.5 bg-slate-900/80 border border-slate-800 rounded-xl flex items-center justify-between text-xs">
                          <span className="text-slate-300 font-bold">Total Efectivo del día:</span>
                          <span className="font-black text-amber-400 text-sm">{formatCOP(totalCash)}</span>
                        </div>

                        {selectedCashTxs.length === 0 ? (
                          <div className="p-6 text-center text-slate-500 text-xs bg-slate-900/30 rounded-xl border border-slate-900">
                            No hay movimientos en efectivo en esta fecha.
                          </div>
                        ) : (
                          selectedCashTxs.map(tx => (
                            <div
                              key={tx.id}
                              className="p-3 bg-slate-900/90 border border-slate-800 rounded-xl space-y-1"
                            >
                              <div className="flex items-center justify-between">
                                <div className="flex items-center space-x-1.5">
                                  <span className="px-1.5 py-0.5 rounded bg-emerald-500/20 text-emerald-400 font-bold text-[9px]">
                                    {tx.entryType === 'MANUAL_CASH_IN' ? 'Ingreso Fondo' : 'Venta POS'}
                                  </span>
                                  {tx.saleNumber && (
                                    <span className="text-[11px] font-bold text-white font-mono">{tx.saleNumber}</span>
                                  )}
                                </div>
                                <span className="font-black text-amber-400 text-xs">{formatCOP(tx.amount)}</span>
                              </div>

                              <div className="flex items-center justify-between text-[10px] text-slate-400">
                                <span>Cajera: {tx.cashierName}</span>
                                <span>Hora: {tx.time}</span>
                              </div>

                              {tx.notes && (
                                <p className="text-[10px] text-slate-400 italic pt-1 border-t border-slate-800/60">
                                  Nota: {tx.notes}
                                </p>
                              )}
                            </div>
                          ))
                        )}
                      </div>
                    )}

                    {/* Tab 3: GASTOS */}
                    {financialTab === 'expenses' && (
                      <div className="space-y-2">
                        <div className="flex items-center justify-between">
                          <div className="p-2.5 bg-slate-900/80 border border-slate-800 rounded-xl flex-1 flex items-center justify-between text-xs mr-2">
                            <span className="text-slate-300 font-bold">Total Gastos:</span>
                            <span className="font-black text-rose-400 text-sm">{formatCOP(totalExpenses)}</span>
                          </div>
                          <button
                            onClick={() => {
                              setNewExpenseAmount('');
                              setNewExpenseDesc('');
                              setNewExpenseNotes('');
                              setNewExpenseCategory('Insumos');
                              setNewExpenseSource('Caja efectivo');
                              setNewExpenseLinkedInventory(false);
                              setShowNewExpenseModal(true);
                            }}
                            className="px-3 py-2.5 bg-amber-500 hover:bg-amber-400 text-slate-950 font-black text-xs rounded-xl flex items-center space-x-1 shadow-md shadow-amber-500/20 cursor-pointer"
                          >
                            <Plus className="w-4 h-4" />
                            <span>Gasto</span>
                          </button>
                        </div>

                        {dayExpenses.length === 0 ? (
                          <div className="p-6 text-center text-slate-500 text-xs bg-slate-900/30 rounded-xl border border-slate-900">
                            No hay gastos registrados en esta fecha.
                          </div>
                        ) : (
                          dayExpenses.map(exp => (
                            <div
                              key={exp.id}
                              className={`p-3 bg-slate-900/90 border rounded-xl space-y-1.5 ${
                                exp.status === 'ANULLED'
                                  ? 'border-slate-800/40 opacity-60'
                                  : 'border-slate-800'
                              }`}
                            >
                              <div className="flex items-center justify-between">
                                <div className="flex items-center space-x-1.5">
                                  <span className="px-1.5 py-0.5 rounded bg-rose-500/20 text-rose-300 font-bold text-[9px]">
                                    {exp.category}
                                  </span>
                                  {exp.status === 'ANULLED' ? (
                                    <span className="px-1.5 py-0.5 rounded bg-slate-800 text-slate-400 font-bold text-[9px] line-through">
                                      ANULADO
                                    </span>
                                  ) : (
                                    <span className="text-[10px] text-slate-400">Origen: {exp.paymentSource}</span>
                                  )}
                                </div>
                                <span
                                  className={`font-black text-xs ${
                                    exp.status === 'ANULLED' ? 'text-slate-500 line-through' : 'text-rose-400'
                                  }`}
                                >
                                  {formatCOP(exp.amount)}
                                </span>
                              </div>

                              <p className="text-xs text-white font-medium">{exp.description}</p>

                              <div className="flex items-center justify-between text-[10px] text-slate-400">
                                <span>Por: {exp.cashierName}</span>
                                <span>Hora: {exp.time}</span>
                              </div>

                              {exp.isLinkedToInventory && (
                                <span className="inline-block px-1.5 py-0.2 rounded bg-cyan-500/10 text-cyan-300 text-[8px] font-semibold">
                                  ✓ Vinculado a inventario
                                </span>
                              )}

                              {exp.status === 'ANULLED' && exp.annulReason && (
                                <p className="text-[9px] text-rose-400 italic pt-1 border-t border-slate-800/60">
                                  Motivo de anulación: {exp.annulReason}
                                </p>
                              )}

                              {exp.status === 'ACTIVE' && (
                                <div className="pt-1.5 border-t border-slate-800/60 flex justify-end">
                                  <button
                                    onClick={() => {
                                      setExpenseToAnnul(exp);
                                      setAnnulReason('');
                                    }}
                                    className="px-2 py-0.5 text-[9px] font-bold text-rose-400 hover:text-rose-300 bg-rose-500/10 hover:bg-rose-500/20 rounded-md border border-rose-500/30"
                                  >
                                    Anular Gasto
                                  </button>
                                </div>
                              )}
                            </div>
                          ))
                        )}
                      </div>
                    )}

                    {/* Tab 4: LO QUE SE VENDIÓ ESE DÍA */}
                    {financialTab === 'products' && (
                      <div className="space-y-2">
                        <div className="p-2.5 bg-slate-900/80 border border-slate-800 rounded-xl flex items-center justify-between text-xs">
                          <span className="text-slate-300 font-bold">Productos Vendidos:</span>
                          <span className="font-bold text-amber-400 text-xs">
                            {soldProductsList.reduce((acc, p) => acc + p.totalQty, 0)} unidades en {matchingSales.length} ventas
                          </span>
                        </div>

                        {soldProductsList.length === 0 ? (
                          <div className="p-6 text-center text-slate-500 text-xs bg-slate-900/30 rounded-xl border border-slate-900">
                            No hay productos registrados en las ventas de esta fecha.
                          </div>
                        ) : (
                          soldProductsList.map(prodSummary => (
                            <div
                              key={prodSummary.productId}
                              onClick={() => setInspectingProductItem(prodSummary)}
                              className="p-3 bg-slate-900/90 border border-slate-800 hover:border-amber-500/40 rounded-xl cursor-pointer transition flex items-center justify-between"
                            >
                              <div>
                                <span className="font-bold text-white text-xs block">{prodSummary.productName}</span>
                                <span className="text-[10px] text-slate-400 mt-0.5 block">
                                  {prodSummary.totalQty} und. vendidas • Precio base: {formatCOP(prodSummary.unitPrice)}
                                </span>
                                <span className="text-[9px] text-amber-400/90 font-medium mt-0.5 block">
                                  Aparece en {prodSummary.salesCount} recibo(s) (Toca para ver detalle)
                                </span>
                              </div>
                              <div className="text-right">
                                <span className="font-black text-emerald-400 text-xs block">
                                  {formatCOP(prodSummary.totalRevenue)}
                                </span>
                                <span className="text-[9px] text-slate-500 block">Subtotal</span>
                              </div>
                            </div>
                          ))
                        )}
                      </div>
                    )}

                    {/* ============================================================== */}
                    {/* MODAL: AUDITORÍA DE PAGO NEQUI (CON "No disponible en el SMS") */}
                    {/* ============================================================== */}
                    {inspectingNequiTx && (
                      <div className="fixed inset-0 z-50 bg-black/80 backdrop-blur-sm flex items-center justify-center p-4">
                        <div className="w-full max-w-sm bg-slate-950 border border-amber-500/40 rounded-2xl p-4 space-y-3 shadow-2xl">
                          <div className="flex items-center justify-between border-b border-slate-800 pb-2">
                            <div className="flex items-center space-x-2">
                              <div className="w-7 h-7 rounded-lg bg-purple-500/20 text-purple-300 flex items-center justify-center font-bold text-xs">
                                N
                              </div>
                              <span className="font-black text-sm text-white">Auditoría Pago Nequi</span>
                            </div>
                            <button
                              onClick={() => setInspectingNequiTx(null)}
                              className="p-1 rounded-lg bg-slate-900 text-slate-400 hover:text-white"
                            >
                              <X className="w-4 h-4" />
                            </button>
                          </div>

                          <div className="p-3 rounded-xl bg-slate-900/90 border border-slate-800 text-center">
                            <span className="text-[10px] text-slate-400 block">Monto Acreditado</span>
                            <span className="text-xl font-black text-emerald-400 block mt-0.5">
                              {formatCOP(inspectingNequiTx.amount)}
                            </span>
                            <span className="text-[9px] text-purple-300 font-bold block mt-0.5">
                              {inspectingNequiTx.nequiChannel || 'App Nequi'}
                            </span>
                          </div>

                          <div className="space-y-2 text-xs">
                            <div className="p-2.5 rounded-xl bg-slate-900 border border-slate-800 space-y-1">
                              <span className="text-[10px] text-slate-400 block">Referencia Bancaria Nequi:</span>
                              {inspectingNequiTx.bankTransactionRef ? (
                                <span className="font-mono font-bold text-amber-300 text-sm">
                                  {inspectingNequiTx.bankTransactionRef}
                                </span>
                              ) : (
                                <div className="px-2 py-1 rounded bg-amber-500/10 border border-amber-500/30 text-amber-300 font-bold italic text-xs">
                                  No disponible en el SMS
                                </div>
                              )}
                              <p className="text-[9px] text-slate-500 mt-1">
                                Diferenciada estrictamente del identificador de evento SMS.
                              </p>
                            </div>

                            <div className="p-2.5 rounded-xl bg-slate-900 border border-slate-800 space-y-1">
                              <span className="text-[10px] text-slate-400 block">Remitente:</span>
                              <span className="font-bold text-white text-xs">{inspectingNequiTx.payerNameOrPhone}</span>
                            </div>

                            <div className="grid grid-cols-2 gap-2">
                              <div className="p-2 rounded-xl bg-slate-900 border border-slate-800">
                                <span className="text-[9px] text-slate-400 block">ID Evento SMS</span>
                                <span className="font-mono text-[10px] text-slate-200 block truncate">
                                  {inspectingNequiTx.smsEventId || 'SMS-DIRECT-01'}
                                </span>
                              </div>
                              <div className="p-2 rounded-xl bg-slate-900 border border-slate-800">
                                <span className="text-[9px] text-slate-400 block">Hora y Fecha</span>
                                <span className="text-[10px] text-slate-200 block truncate">
                                  {inspectingNequiTx.time} ({inspectingNequiTx.dateStr})
                                </span>
                              </div>
                            </div>

                            <div className="p-2.5 rounded-xl bg-slate-900 border border-slate-800 space-y-1">
                              <span className="text-[10px] text-slate-400 block">Instalación / Origen:</span>
                              <span className="text-xs font-bold text-cyan-400">{inspectingNequiTx.originInstallation}</span>
                            </div>

                            {inspectingNequiTx.saleNumber && (
                              <div className="p-2.5 rounded-xl bg-slate-900 border border-slate-800 flex justify-between items-center">
                                <span className="text-[10px] text-slate-400">Recibo de Venta Asociado:</span>
                                <span className="text-xs font-black text-white font-mono">{inspectingNequiTx.saleNumber}</span>
                              </div>
                            )}
                          </div>

                          <button
                            onClick={() => setInspectingNequiTx(null)}
                            className="w-full py-2 bg-slate-800 hover:bg-slate-700 text-white font-bold text-xs rounded-xl"
                          >
                            Cerrar Auditoría
                          </button>
                        </div>
                      </div>
                    )}

                    {/* ============================================================== */}
                    {/* MODAL: DETALLE DE OCURRENCIAS DE PRODUCTO VENDIDO              */}
                    {/* ============================================================== */}
                    {inspectingProductItem && (
                      <div className="fixed inset-0 z-50 bg-black/80 backdrop-blur-sm flex items-center justify-center p-4">
                        <div className="w-full max-w-sm bg-slate-950 border border-amber-500/40 rounded-2xl p-4 space-y-3 shadow-2xl">
                          <div className="flex items-center justify-between border-b border-slate-800 pb-2">
                            <div>
                              <h3 className="font-black text-sm text-white">{inspectingProductItem.productName}</h3>
                              <p className="text-[10px] text-slate-400">
                                {inspectingProductItem.totalQty} unidades vendidas el {selectedCalendarDate}
                              </p>
                            </div>
                            <button
                              onClick={() => setInspectingProductItem(null)}
                              className="p-1 rounded-lg bg-slate-900 text-slate-400 hover:text-white"
                            >
                              <X className="w-4 h-4" />
                            </button>
                          </div>

                          <div className="p-2.5 rounded-xl bg-slate-900 border border-slate-800 flex justify-between items-center text-xs">
                            <span className="text-slate-300">Total Generado:</span>
                            <span className="font-black text-emerald-400 text-sm">
                              {formatCOP(inspectingProductItem.totalRevenue)}
                            </span>
                          </div>

                          <div className="space-y-1.5 max-h-60 overflow-y-auto">
                            <span className="text-[10px] font-bold text-slate-400 uppercase tracking-wider block">
                              Recibos de venta con este producto:
                            </span>
                            {inspectingProductItem.occurrences.map((occ: any, i: number) => (
                              <div
                                key={i}
                                className="p-2.5 bg-slate-900/90 border border-slate-800 rounded-xl flex items-center justify-between text-xs"
                              >
                                <div>
                                  <div className="flex items-center space-x-1.5">
                                    <span className="font-mono font-bold text-white text-[11px]">{occ.saleNumber}</span>
                                    <span className="text-[9px] text-slate-400">({occ.time})</span>
                                  </div>
                                  <span className="text-[9px] text-slate-400 block mt-0.5">
                                    Cajera: {occ.cashier} • {occ.quantity} und.
                                  </span>
                                </div>
                                <span className="font-bold text-amber-400 text-xs">{formatCOP(occ.total)}</span>
                              </div>
                            ))}
                          </div>

                          <button
                            onClick={() => setInspectingProductItem(null)}
                            className="w-full py-2 bg-slate-800 hover:bg-slate-700 text-white font-bold text-xs rounded-xl"
                          >
                            Cerrar
                          </button>
                        </div>
                      </div>
                    )}

                    {/* ============================================================== */}
                    {/* MODAL: REGISTRAR GASTO DEL DÍA                                */}
                    {/* ============================================================== */}
                    {showNewExpenseModal && (
                      <div className="fixed inset-0 z-50 bg-black/80 backdrop-blur-sm flex items-center justify-center p-4">
                        <div className="w-full max-w-sm bg-slate-950 border border-amber-500/40 rounded-2xl p-4 space-y-3 shadow-2xl">
                          <div className="flex items-center justify-between border-b border-slate-800 pb-2">
                            <span className="font-black text-sm text-white">Registrar Gasto del Día</span>
                            <button
                              onClick={() => setShowNewExpenseModal(false)}
                              className="p-1 rounded-lg bg-slate-900 text-slate-400 hover:text-white"
                            >
                              <X className="w-4 h-4" />
                            </button>
                          </div>

                          <div className="space-y-2 text-xs">
                            <div>
                              <label className="text-[10px] text-slate-400 block mb-1">Monto en COP *</label>
                              <input
                                type="number"
                                placeholder="Ej. 25000"
                                value={newExpenseAmount}
                                onChange={e => setNewExpenseAmount(e.target.value)}
                                className="w-full p-2.5 rounded-xl bg-slate-900 border border-slate-800 text-white font-bold text-sm focus:border-amber-500 outline-none"
                              />
                            </div>

                            <div>
                              <label className="text-[10px] text-slate-400 block mb-1">Categoría de Gasto *</label>
                              <select
                                value={newExpenseCategory}
                                onChange={e => setNewExpenseCategory(e.target.value)}
                                className="w-full p-2 rounded-xl bg-slate-900 border border-slate-800 text-white text-xs outline-none focus:border-amber-500"
                              >
                                <option value="Insumos">Insumos (Leche, Frutas, Azúcar)</option>
                                <option value="Reposición inventario">Reposición inventario (Vasos, Barquillos)</option>
                                <option value="Transporte">Transporte / Domicilios</option>
                                <option value="Servicios">Servicios (Luz, Agua, Internet)</option>
                                <option value="Mantenimiento">Mantenimiento de Máquinas</option>
                                <option value="Otros">Otros</option>
                              </select>
                            </div>

                            <div>
                              <label className="text-[10px] text-slate-400 block mb-1">Descripción del Gasto *</label>
                              <input
                                type="text"
                                placeholder="Ej. Compra de 6 bolsas de leche entera"
                                value={newExpenseDesc}
                                onChange={e => setNewExpenseDesc(e.target.value)}
                                className="w-full p-2.5 rounded-xl bg-slate-900 border border-slate-800 text-white text-xs focus:border-amber-500 outline-none"
                              />
                            </div>

                            <div className="grid grid-cols-2 gap-2">
                              <div>
                                <label className="text-[10px] text-slate-400 block mb-1">Origen de Pago</label>
                                <select
                                  value={newExpenseSource}
                                  onChange={e => setNewExpenseSource(e.target.value)}
                                  className="w-full p-2 rounded-xl bg-slate-900 border border-slate-800 text-white text-xs outline-none focus:border-amber-500"
                                >
                                  <option value="Caja efectivo">Caja efectivo</option>
                                  <option value="Nequi">Nequi</option>
                                  <option value="Otro">Otro</option>
                                </select>
                              </div>

                              <div>
                                <label className="text-[10px] text-slate-400 block mb-1">Cajera Responsable</label>
                                <input
                                  type="text"
                                  value={activeCashier.name}
                                  disabled
                                  className="w-full p-2 rounded-xl bg-slate-900 border border-slate-800 text-slate-400 text-xs"
                                />
                              </div>
                            </div>

                            <div>
                              <label className="text-[10px] text-slate-400 block mb-1">Notas / Factura (Opcional)</label>
                              <input
                                type="text"
                                placeholder="Ej. Factura proveedora #412"
                                value={newExpenseNotes}
                                onChange={e => setNewExpenseNotes(e.target.value)}
                                className="w-full p-2 rounded-xl bg-slate-900 border border-slate-800 text-white text-xs focus:border-amber-500 outline-none"
                              />
                            </div>

                            <div className="p-2.5 rounded-xl bg-slate-900 border border-slate-800 flex items-center space-x-2">
                              <input
                                type="checkbox"
                                id="chk-linked-inv"
                                checked={newExpenseLinkedInventory}
                                onChange={e => setNewExpenseLinkedInventory(e.target.checked)}
                                className="w-4 h-4 accent-amber-500 rounded"
                              />
                              <label htmlFor="chk-linked-inv" className="text-[11px] text-slate-300 leading-tight">
                                Este gasto corresponde a reposición de inventario físico (no duplicar en costos)
                              </label>
                            </div>
                          </div>

                          <div className="flex items-center space-x-2 pt-2">
                            <button
                              onClick={() => setShowNewExpenseModal(false)}
                              className="flex-1 py-2 bg-slate-800 hover:bg-slate-700 text-white font-bold text-xs rounded-xl"
                            >
                              Cancelar
                            </button>
                            <button
                              onClick={() => {
                                const parsedAmt = parseFloat(newExpenseAmount);
                                if (isNaN(parsedAmt) || parsedAmt <= 0) {
                                  alert('Por favor ingresa un monto válido mayor a 0');
                                  return;
                                }
                                if (!newExpenseDesc.trim()) {
                                  alert('La descripción del gasto es obligatoria');
                                  return;
                                }

                                const now = new Date();
                                const timeStr = now.toLocaleTimeString('es-CO', { hour: '2-digit', minute: '2-digit' });

                                const newExp = {
                                  id: `exp-${Date.now()}`,
                                  amount: parsedAmt,
                                  category: newExpenseCategory,
                                  description: newExpenseDesc.trim(),
                                  paymentSource: newExpenseSource,
                                  cashierName: activeCashier.name,
                                  notes: newExpenseNotes.trim(),
                                  dateStr: selectedCalendarDate,
                                  time: timeStr,
                                  isLinkedToInventory: newExpenseLinkedInventory,
                                  status: 'ACTIVE'
                                };

                                setFinancialExpenses(prev => [newExp, ...prev]);

                                // If from Cash, deduct from Cash Shift
                                if (newExpenseSource === 'Caja efectivo') {
                                  setCashShift(prev => ({
                                    ...prev,
                                    cashOut: prev.cashOut + parsedAmt,
                                    expenses: [
                                      ...prev.expenses,
                                      {
                                        id: `exp-shift-${Date.now()}`,
                                        concept: newExpenseDesc.trim(),
                                        amount: parsedAmt,
                                        time: timeStr
                                      }
                                    ]
                                  }));
                                }

                                setShowNewExpenseModal(false);
                              }}
                              className="flex-1 py-2 bg-amber-500 hover:bg-amber-400 text-slate-950 font-black text-xs rounded-xl shadow-md shadow-amber-500/20"
                            >
                              Guardar Gasto
                            </button>
                          </div>
                        </div>
                      </div>
                    )}

                    {/* ============================================================== */}
                    {/* MODAL: ANULAR GASTO (CON MOTIVO OBLIGATORIO)                   */}
                    {/* ============================================================== */}
                    {expenseToAnnul && (
                      <div className="fixed inset-0 z-50 bg-black/80 backdrop-blur-sm flex items-center justify-center p-4">
                        <div className="w-full max-w-sm bg-slate-950 border border-rose-500/40 rounded-2xl p-4 space-y-3 shadow-2xl">
                          <div className="flex items-center justify-between border-b border-slate-800 pb-2">
                            <span className="font-black text-sm text-white">Anular Gasto</span>
                            <button
                              onClick={() => setExpenseToAnnul(null)}
                              className="p-1 rounded-lg bg-slate-900 text-slate-400 hover:text-white"
                            >
                              <X className="w-4 h-4" />
                            </button>
                          </div>

                          <div className="p-3 rounded-xl bg-slate-900 border border-slate-800 space-y-1 text-xs">
                            <span className="text-slate-400 text-[10px] block">Gasto a anular:</span>
                            <p className="text-white font-bold">{expenseToAnnul.description}</p>
                            <span className="text-rose-400 font-black text-sm block">
                              {formatCOP(expenseToAnnul.amount)}
                            </span>
                          </div>

                          <div>
                            <label className="text-[10px] text-slate-400 block mb-1">
                              Motivo de anulación (Obligatorio) *
                            </label>
                            <input
                              type="text"
                              placeholder="Ej. Registro duplicado por error"
                              value={annulReason}
                              onChange={e => setAnnulReason(e.target.value)}
                              className="w-full p-2.5 rounded-xl bg-slate-900 border border-slate-800 text-white text-xs focus:border-rose-500 outline-none"
                            />
                          </div>

                          <div className="flex items-center space-x-2 pt-2">
                            <button
                              onClick={() => setExpenseToAnnul(null)}
                              className="flex-1 py-2 bg-slate-800 hover:bg-slate-700 text-white font-bold text-xs rounded-xl"
                            >
                              Volver
                            </button>
                            <button
                              onClick={() => {
                                if (!annulReason.trim()) {
                                  alert('Debes indicar el motivo de la anulación.');
                                  return;
                                }
                                setFinancialExpenses(prev =>
                                  prev.map(e =>
                                    e.id === expenseToAnnul.id
                                      ? {
                                          ...e,
                                          status: 'ANULLED',
                                          annulReason: annulReason.trim(),
                                          annulledAt: new Date().toISOString()
                                        }
                                      : e
                                  )
                                );
                                setExpenseToAnnul(null);
                              }}
                              className="flex-1 py-2 bg-rose-600 hover:bg-rose-500 text-white font-black text-xs rounded-xl"
                            >
                              Confirmar Anulación
                            </button>
                          </div>
                        </div>
                      </div>
                    )}
                  </div>
                );
              })()}

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

      {/* ============================================================== */}
      {/* MODAL 1: EDIT CASHIER NAME & MANAGE PROFILES                   */}
      {/* ============================================================== */}
      {showCashierModal && (
        <CashierManagementModal
          activeCashier={activeCashier}
          cashiers={cashiers}
          onSaveActiveName={(newName) => {
            const trimmed = newName.trim();
            if (!trimmed) return 'El nombre de la cajera no puede estar vacío.';
            const updated = { ...activeCashier, name: trimmed };
            setActiveCashier(updated);
            setCashiers(prev => prev.map(c => c.id === activeCashier.id ? updated : c));
            setCashShift(prev => ({ ...prev, cashierName: trimmed }));
            setShowCashierModal(false);
            return null;
          }}
          onSelectProfile={(profile) => {
            setActiveCashier(profile);
            setCashShift(prev => ({ ...prev, cashierName: profile.name }));
            setShowCashierModal(false);
          }}
          onCreateProfile={(name) => {
            const trimmed = name.trim();
            if (!trimmed) return 'El nombre de la cajera no puede estar vacío.';
            const newProfile: CashierProfile = { id: `cashier-${Date.now()}`, name: trimmed, isActive: true };
            setCashiers(prev => [...prev, newProfile]);
            setActiveCashier(newProfile);
            setCashShift(prev => ({ ...prev, cashierName: trimmed }));
            setShowCashierModal(false);
            return null;
          }}
          onClose={() => setShowCashierModal(false)}
        />
      )}

      {/* ============================================================== */}
      {/* MODAL 2: PRODUCT MANAGEMENT (CRUD & PHOTO PICKER)              */}
      {/* ============================================================== */}
      {showProductAdminModal && (
        <ProductManagementModal
          products={products}
          categories={categories}
          ingredients={ingredients}
          isOwner={currentRole === 'OWNER'}
          onSaveProduct={(productData) => {
            if (productData.id) {
              setProducts(prev => prev.map(p => p.id === productData.id ? { ...p, ...productData, updatedAt: Date.now() } : p));
            } else {
              const newProd: Product = {
                ...productData,
                id: `prod-${Date.now()}`,
                variants: [
                  { id: 'var-1', name: 'Unidad', priceDelta: 0, weightGrams: 100 }
                ],
                allowedToppings: true,
                createdAt: Date.now(),
                updatedAt: Date.now()
              } as Product;
              setProducts(prev => [...prev, newProd]);
            }
          }}
          onDeleteProduct={(productId) => {
            setProducts(prev => prev.filter(p => p.id !== productId));
          }}
          onAddIngredient={(newIng) => {
            setIngredients(prev => [...prev, newIng]);
          }}
          onClose={() => setShowProductAdminModal(false)}
        />
      )}

      {/* ============================================================== */}
      {/* MODAL 3: CATEGORY MANAGEMENT (SAFE REASSIGN & DELETE)          */}
      {/* ============================================================== */}
      {showCategoryAdminModal && (
        <CategoryManagementModal
          categories={categories}
          products={products}
          onCreateCategory={(name, emoji) => {
            const trimmed = name.trim();
            if (!trimmed) return 'El nombre de la categoría no puede estar vacío.';
            const newCat: CategoryItem = {
              id: `cat-${Date.now()}`,
              name: trimmed,
              emoji: emoji || '🍦',
              isActive: true,
              displayOrder: categories.length
            };
            setCategories(prev => [...prev, newCat]);
            return null;
          }}
          onUpdateCategory={(id, name, emoji) => {
            const trimmed = name.trim();
            if (!trimmed) return 'El nombre de la categoría no puede estar vacío.';
            setCategories(prev => prev.map(c => c.id === id ? { ...c, name: trimmed, emoji } : c));
            return null;
          }}
          onDeleteCategory={(catId, reassignToCatId) => {
            if (categories.length <= 1) return 'No se puede eliminar la única categoría existente.';
            if (catId === reassignToCatId) return 'La categoría de reasignación debe ser diferente.';

            // Reassign products safely without dropping them
            setProducts(prev => prev.map(p => p.categoryId === catId ? { ...p, categoryId: reassignToCatId, updatedAt: Date.now() } : p));
            setCategories(prev => prev.filter(c => c.id !== catId));
            return null;
          }}
          onClose={() => setShowCategoryAdminModal(false)}
        />
      )}

      {/* ============================================================== */}
      {/* MODAL 4: INVENTORY ADJUSTMENT & AUDIT MOVEMENTS                */}
      {/* ============================================================== */}
      {showStockAdminModal && (
        <StockAuditModal
          products={products}
          movements={inventoryMovements}
          auditorName={activeCashier.name}
          onAdjustStock={(productId, newStock, reason, type) => {
            const trimmedReason = reason.trim();
            if (!trimmedReason) return 'El motivo del ajuste es obligatorio.';
            if (newStock < 0) return 'El inventario resultante no puede ser negativo.';

            const targetProd = products.find(p => p.id === productId);
            if (!targetProd) return 'Producto no encontrado.';

            const prevStock = targetProd.stockQuantity;
            const change = newStock - prevStock;

            setProducts(prev => prev.map(p => p.id === productId ? { ...p, stockQuantity: newStock, updatedAt: Date.now() } : p));

            const mov: InventoryMovement = {
              id: `mov-${Date.now()}-${Math.random().toString(36).substring(2, 6)}`,
              productId: targetProd.id,
              productName: targetProd.name,
              previousStock: prevStock,
              change,
              resultingStock: newStock,
              type,
              reason: trimmedReason,
              dateFormatted: new Date().toLocaleTimeString('es-CO', { hour: '2-digit', minute: '2-digit' }),
              userName: activeCashier.name
            };

            setInventoryMovements(prev => [mov, ...prev]);
            return null;
          }}
          onClose={() => setShowStockAdminModal(false)}
        />
      )}
    </div>
  );
}

// =============================================================================
// SUB-COMPONENTS: ADMINISTRATIVE MODALS
// =============================================================================

function CashierManagementModal({
  activeCashier,
  cashiers,
  onSaveActiveName,
  onSelectProfile,
  onCreateProfile,
  onClose
}: {
  activeCashier: CashierProfile;
  cashiers: CashierProfile[];
  onSaveActiveName: (name: string) => string | null;
  onSelectProfile: (profile: CashierProfile) => void;
  onCreateProfile: (name: string) => string | null;
  onClose: () => void;
}) {
  const [nameInput, setNameInput] = useState(activeCashier.name);
  const [isCreating, setIsCreating] = useState(false);
  const [newProfileName, setNewProfileName] = useState('');
  const [errorMsg, setErrorMsg] = useState<string | null>(null);

  return (
    <div className="fixed inset-0 z-50 bg-black/85 backdrop-blur-sm flex items-end sm:items-center justify-center p-0 sm:p-4">
      <div className="w-full max-w-md bg-[#0B1224] border border-slate-800 rounded-t-[32px] sm:rounded-3xl p-5 shadow-2xl flex flex-col space-y-4 animate-in slide-in-from-bottom duration-200">
        <div className="flex items-center justify-between">
          <div className="flex items-center space-x-2">
            <Users className="w-5 h-5 text-amber-400" />
            <h3 className="font-black text-sm text-white">
              {isCreating ? 'Agregar Nueva Cajera' : 'Administrar Cajeras'}
            </h3>
          </div>
          <button onClick={onClose} className="p-1 hover:bg-slate-800 text-slate-400 rounded-lg">
            <X className="w-5 h-5" />
          </button>
        </div>

        {errorMsg && (
          <div className="p-2 bg-red-500/20 border border-red-500/40 rounded-xl text-red-300 text-xs font-semibold">
            {errorMsg}
          </div>
        )}

        {!isCreating ? (
          <div className="space-y-4">
            <div>
              <label className="text-[11px] text-slate-400 block mb-1">Nombre de la cajera activa en turno:</label>
              <div className="flex gap-2">
                <input
                  type="text"
                  value={nameInput}
                  onChange={e => {
                    setNameInput(e.target.value);
                    setErrorMsg(null);
                  }}
                  className="flex-1 px-3 py-2 bg-slate-950 border border-slate-800 rounded-xl text-xs text-white focus:outline-none focus:border-amber-400"
                />
                <button
                  onClick={() => {
                    const err = onSaveActiveName(nameInput);
                    if (err) setErrorMsg(err);
                  }}
                  className="px-4 py-2 bg-amber-500 text-slate-950 font-bold text-xs rounded-xl shadow cursor-pointer"
                >
                  Guardar
                </button>
              </div>
              <p className="text-[10px] text-slate-500 mt-1">Este nombre se imprime en recibos y auditorías de caja.</p>
            </div>

            <div>
              <span className="text-[11px] font-bold text-amber-400 block mb-2 uppercase tracking-wide">
                Perfiles de cajeras registrados:
              </span>
              <div className="space-y-1.5 max-h-48 overflow-y-auto">
                {cashiers.map(c => {
                  const isActive = c.id === activeCashier.id;
                  return (
                    <div
                      key={c.id}
                      onClick={() => onSelectProfile(c)}
                      className={`p-2.5 rounded-xl border flex items-center justify-between text-xs cursor-pointer transition ${
                        isActive
                          ? 'bg-amber-500/15 border-amber-500/50 text-white'
                          : 'bg-slate-950/70 border-slate-800 text-slate-300 hover:border-slate-700'
                      }`}
                    >
                      <div className="flex items-center gap-2">
                        <span className="w-2 h-2 rounded-full bg-emerald-400" />
                        <span className="font-semibold">{c.name}</span>
                      </div>
                      {isActive && (
                        <span className="text-[10px] font-bold text-amber-400 bg-amber-500/20 px-2 py-0.5 rounded-md">
                          En Turno
                        </span>
                      )}
                    </div>
                  );
                })}
              </div>
            </div>

            <button
              onClick={() => {
                setIsCreating(true);
                setNewProfileName('');
                setErrorMsg(null);
              }}
              className="w-full py-2.5 bg-slate-900 border border-slate-800 text-slate-300 hover:text-white font-bold text-xs rounded-xl flex items-center justify-center gap-1.5 cursor-pointer"
            >
              <Plus className="w-3.5 h-3.5 text-amber-400" />
              <span>+ Agregar Nuevo Perfil</span>
            </button>
          </div>
        ) : (
          <div className="space-y-3">
            <div>
              <label className="text-[11px] text-slate-400 block mb-1">Nombre completo de la cajera:</label>
              <input
                type="text"
                placeholder="Ej. Valentina Ortiz"
                value={newProfileName}
                onChange={e => {
                  setNewProfileName(e.target.value);
                  setErrorMsg(null);
                }}
                className="w-full px-3 py-2 bg-slate-950 border border-slate-800 rounded-xl text-xs text-white focus:outline-none focus:border-amber-400"
              />
            </div>

            <div className="flex gap-2 pt-2">
              <button
                onClick={() => setIsCreating(false)}
                className="flex-1 py-2.5 bg-slate-900 border border-slate-800 text-slate-400 font-bold text-xs rounded-xl"
              >
                Volver
              </button>
              <button
                onClick={() => {
                  const err = onCreateProfile(newProfileName);
                  if (err) setErrorMsg(err);
                }}
                className="flex-1 py-2.5 bg-amber-500 text-slate-950 font-black text-xs rounded-xl shadow cursor-pointer"
              >
                Crear Perfil
              </button>
            </div>
          </div>
        )}
      </div>
    </div>
  );
}

function ProductManagementModal({
  products,
  categories,
  ingredients,
  isOwner,
  onSaveProduct,
  onDeleteProduct,
  onAddIngredient,
  onClose
}: {
  products: Product[];
  categories: CategoryItem[];
  ingredients: Ingredient[];
  isOwner: boolean;
  onSaveProduct: (data: Partial<Product>) => void;
  onDeleteProduct: (id: string) => void;
  onAddIngredient?: (ingredient: Ingredient) => void;
  onClose: () => void;
}) {
  const [editingProduct, setEditingProduct] = useState<Product | null>(null);
  const [isCreating, setIsCreating] = useState(false);
  const [search, setSearch] = useState('');
  const [catFilter, setCatFilter] = useState('todos');

  // Form states
  const [name, setName] = useState('');
  const [basePrice, setBasePrice] = useState('');
  const [baseCost, setBaseCost] = useState('');
  const [categoryId, setCategoryId] = useState(categories[0]?.id || 'helados');
  const [description, setDescription] = useState('');
  const [stockQuantity, setStockQuantity] = useState('10');
  const [unit, setUnit] = useState('unidades');
  const [minStockAlert, setMinStockAlert] = useState('5');
  const [isActive, setIsActive] = useState(true);
  const [emoji, setEmoji] = useState('🍨');
  const [imagePath, setImagePath] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  // Recipe & Combo states
  const [isCombo, setIsCombo] = useState(false);
  const [recipe, setRecipe] = useState<RecipeItem[]>([]);
  const [comboItems, setComboItems] = useState<ComboItem[]>([]);
  const [allowNegativeStock, setAllowNegativeStock] = useState(false);

  // Helpers
  const [addIngId, setAddIngId] = useState(ingredients[0]?.id || '');
  const [addIngQty, setAddIngQty] = useState('100');
  const [addIngUnit, setAddIngUnit] = useState('g');
  const [addComboChildId, setAddComboChildId] = useState('');

  // Cuadrito para agregar nuevo insumo al local
  const [newIngName, setNewIngName] = useState('');
  const [newIngUnit, setNewIngUnit] = useState<'g' | 'kg' | 'ml' | 'unidades'>('g');
  const [newIngCost, setNewIngCost] = useState('');
  const [newIngStock, setNewIngStock] = useState('1000');
  const [newIngCategory, setNewIngCategory] = useState('Insumos');
  const [newIngSuccessMsg, setNewIngSuccessMsg] = useState<string | null>(null);

  const handleAddNewIngredient = () => {
    if (!newIngName.trim()) return;
    const created: Ingredient = {
      id: `ing-${Date.now()}`,
      name: newIngName.trim(),
      category: newIngCategory,
      unit: newIngUnit,
      currentStock: parseFloat(newIngStock) || 0,
      minStockAlert: 50,
      unitCostCOP: parseFloat(newIngCost) || 0,
      batchCode: `LOTE-${new Date().getFullYear()}`,
      expiryDate: ''
    };

    if (onAddIngredient) {
      onAddIngredient(created);
    }
    setAddIngId(created.id);
    setAddIngUnit(created.unit);
    setNewIngName('');
    setNewIngCost('');
    setNewIngSuccessMsg(`¡Insumo "${created.name}" registrado con éxito!`);
    setTimeout(() => setNewIngSuccessMsg(null), 3000);
  };

  const calculateRecipeCost = (rec: RecipeItem[]): number => {
    return rec.reduce((acc, r) => {
      const ing = ingredients.find(i => i.id === r.ingredientId);
      if (!ing) return acc;
      try {
        const converted = UnitConverter.convert(r.quantity, r.unit, ing.unit);
        return acc + (converted * ing.unitCostCOP);
      } catch {
        return acc + (r.quantity * ing.unitCostCOP);
      }
    }, 0);
  };

  const startEdit = (p: Product) => {
    setEditingProduct(p);
    setIsCreating(false);
    setName(p.name);
    setBasePrice(p.basePrice.toString());
    setBaseCost(p.baseCost !== undefined ? p.baseCost.toString() : '');
    setCategoryId(p.categoryId);
    setDescription(p.description || '');
    setStockQuantity(p.stockQuantity.toString());
    setUnit(p.unit || 'unidades');
    setMinStockAlert(p.minStockAlert.toString());
    setIsActive(p.isActive);
    setEmoji(p.emoji);
    setImagePath(p.imagePath || null);
    setIsCombo(p.isCombo || false);
    setRecipe(p.recipe || []);
    setComboItems(p.comboItems || []);
    setAllowNegativeStock(p.allowNegativeStock || false);
    setError(null);
  };

  const startCreate = () => {
    setEditingProduct(null);
    setIsCreating(true);
    setName('');
    setBasePrice('');
    setBaseCost('');
    setCategoryId(categories[0]?.id || 'helados');
    setDescription('');
    setStockQuantity('15');
    setUnit('unidades');
    setMinStockAlert('5');
    setIsActive(true);
    setEmoji('🍨');
    setImagePath(null);
    setIsCombo(false);
    setRecipe([]);
    setComboItems([]);
    setAllowNegativeStock(false);
    setError(null);
  };

  const handleImageFileChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (file) {
      const reader = new FileReader();
      reader.onload = () => {
        setImagePath(reader.result as string);
      };
      reader.readAsDataURL(file);
    }
  };

  const handleSave = () => {
    if (!name.trim()) {
      setError('El nombre del producto es obligatorio.');
      return;
    }
    const priceNum = parseInt(basePrice, 10);
    if (isNaN(priceNum) || priceNum < 0) {
      setError('Ingresa un precio válido.');
      return;
    }
    const parsedCost = parseInt(baseCost, 10);
    const costNum = !isNaN(parsedCost) ? parsedCost : 0;
    const stockNum = parseFloat(stockQuantity) || 0;
    const minAlertNum = parseFloat(minStockAlert) || 5;

    // Circular check for combo
    if (isCombo && editingProduct) {
      if (comboItems.some(ci => ci.productId === editingProduct.id)) {
        setError('Referencia circular: un combo no puede contenerse a sí mismo.');
        return;
      }
    }

    onSaveProduct({
      id: editingProduct?.id,
      name: name.trim(),
      basePrice: priceNum,
      baseCost: costNum,
      categoryId,
      description: description.trim(),
      stockQuantity: stockNum,
      unit,
      minStockAlert: minAlertNum,
      isActive,
      emoji,
      imagePath,
      isCombo,
      recipe,
      comboItems,
      allowNegativeStock
    });

    setEditingProduct(null);
    setIsCreating(false);
  };

  const filtered = products.filter(p => {
    const matchCat = catFilter === 'todos' || p.categoryId === catFilter;
    const matchQuery = p.name.toLowerCase().includes(search.toLowerCase());
    return matchCat && matchQuery;
  });

  return (
    <div className="fixed inset-0 z-50 bg-black/85 backdrop-blur-sm flex items-end sm:items-center justify-center p-0 sm:p-4">
      <div className="w-full max-w-md bg-[#0B1224] border border-slate-800 rounded-t-[32px] sm:rounded-3xl p-5 shadow-2xl flex flex-col max-h-[90vh] animate-in slide-in-from-bottom duration-200">
        <div className="flex items-center justify-between pb-3 border-b border-slate-800">
          <div className="flex items-center space-x-2">
            <Boxes className="w-5 h-5 text-amber-400" />
            <h3 className="font-black text-sm text-white">
              {isCreating ? 'Nuevo Producto' : editingProduct ? 'Editar Producto' : 'Administrar Productos'}
            </h3>
          </div>
          <button onClick={onClose} className="p-1 hover:bg-slate-800 text-slate-400 rounded-lg">
            <X className="w-5 h-5" />
          </button>
        </div>

        {error && (
          <div className="my-2 p-2 bg-red-500/20 border border-red-500/40 rounded-xl text-red-300 text-xs font-semibold">
            {error}
          </div>
        )}

        {isCreating || editingProduct ? (
          <div className="flex-1 overflow-y-auto py-3 space-y-3 pr-1 text-xs">
            <div>
              <label className="text-[11px] text-slate-400 block mb-1">Nombre del Producto:</label>
              <input
                type="text"
                placeholder="Ej. Helado de Maracuyá"
                value={name}
                onChange={e => setName(e.target.value)}
                className="w-full px-3 py-2 bg-slate-950 border border-slate-800 rounded-xl text-white focus:outline-none focus:border-amber-400"
              />
            </div>

            <div className="grid grid-cols-2 gap-2">
              <div>
                <label className="text-[11px] text-slate-400 block mb-1">Precio Venta COP *:</label>
                <input
                  type="number"
                  placeholder="Ej. 6500"
                  value={basePrice}
                  onChange={e => setBasePrice(e.target.value)}
                  className="w-full px-3 py-2 bg-slate-950 border border-slate-800 rounded-xl font-bold text-amber-400 focus:outline-none focus:border-amber-400"
                />
              </div>

              <div>
                <label className="text-[11px] text-slate-400 block mb-1">Categoría:</label>
                <select
                  value={categoryId}
                  onChange={e => setCategoryId(e.target.value)}
                  className="w-full px-3 py-2 bg-slate-950 border border-slate-800 rounded-xl text-white focus:outline-none focus:border-amber-400"
                >
                  {categories.map(c => (
                    <option key={c.id} value={c.id}>{c.emoji} {c.name}</option>
                  ))}
                </select>
              </div>
            </div>

            {/* Photo Picker */}
            <div>
              <label className="text-[11px] text-slate-400 block mb-1">Fotografía del Producto:</label>
              <div className="flex items-center gap-3">
                <div className="w-12 h-12 rounded-xl bg-slate-950 border border-slate-800 flex items-center justify-center overflow-hidden shrink-0">
                  {imagePath ? (
                    <img src={imagePath} alt="Preview" className="w-full h-full object-cover" />
                  ) : (
                    <span className="text-2xl">{emoji}</span>
                  )}
                </div>
                <div className="flex-1 space-y-1">
                  <label className="px-3 py-1.5 bg-slate-900 border border-slate-800 text-slate-200 hover:text-white rounded-lg text-xs font-semibold cursor-pointer inline-flex items-center gap-1.5">
                    <Upload className="w-3.5 h-3.5 text-amber-400" />
                    <span>Subir Foto Galería</span>
                    <input type="file" accept="image/*" onChange={handleImageFileChange} className="hidden" />
                  </label>
                  {imagePath && (
                    <button
                      type="button"
                      onClick={() => setImagePath(null)}
                      className="text-[10px] text-red-400 hover:text-red-300 ml-2"
                    >
                      Quitar foto
                    </button>
                  )}
                </div>
              </div>
            </div>

            <div>
              <label className="text-[11px] text-slate-400 block mb-1">Descripción breve:</label>
              <input
                type="text"
                placeholder="Ej. Sabor refrescante con frutas naturales"
                value={description}
                onChange={e => setDescription(e.target.value)}
                className="w-full px-3 py-2 bg-slate-950 border border-slate-800 rounded-xl text-white focus:outline-none focus:border-amber-400"
              />
            </div>

            {/* Tipo de Producto: Estándar con Receta vs Combo */}
            <div>
              <label className="text-[11px] text-slate-400 block mb-1">Tipo de producto:</label>
              <div className="grid grid-cols-2 gap-2">
                <button
                  type="button"
                  onClick={() => setIsCombo(false)}
                  className={`py-2 px-3 rounded-xl border text-xs font-bold transition cursor-pointer ${
                    !isCombo ? 'bg-amber-500 text-slate-950 border-amber-400' : 'bg-slate-950 text-slate-300 border-slate-800'
                  }`}
                >
                  🍦 Individual / Receta
                </button>
                <button
                  type="button"
                  onClick={() => setIsCombo(true)}
                  className={`py-2 px-3 rounded-xl border text-xs font-bold transition cursor-pointer ${
                    isCombo ? 'bg-amber-500 text-slate-950 border-amber-400' : 'bg-slate-950 text-slate-300 border-slate-800'
                  }`}
                >
                  🎁 Combo Compuesto
                </button>
              </div>
            </div>

            {isCombo ? (
              /* Sección Combo */
              <div className="p-3 bg-slate-950 border border-slate-800 rounded-xl space-y-2">
                <div className="flex items-center justify-between">
                  <span className="font-bold text-amber-400 text-xs">Productos en este Combo:</span>
                  <span className="text-[10px] text-slate-400">{comboItems.length} componente(s)</span>
                </div>

                {comboItems.length === 0 ? (
                  <p className="text-[11px] text-slate-500">Agrega productos que se descontarán automáticamente al vender este combo.</p>
                ) : (
                  <div className="space-y-1.5">
                    {comboItems.map((ci, idx) => (
                      <div key={idx} className="flex items-center justify-between p-2 bg-slate-900 rounded-lg text-xs">
                        <span>• {ci.quantity}x {ci.productName}</span>
                        <button
                          type="button"
                          onClick={() => setComboItems(prev => prev.filter((_, i) => i !== idx))}
                          className="text-red-400 hover:text-red-300 p-0.5"
                        >
                          <X className="w-3.5 h-3.5" />
                        </button>
                      </div>
                    ))}
                  </div>
                )}

                <div className="pt-2 border-t border-slate-800 flex gap-2">
                  <select
                    value={addComboChildId}
                    onChange={e => setAddComboChildId(e.target.value)}
                    className="flex-1 px-2 py-1.5 bg-slate-900 border border-slate-800 rounded-lg text-xs text-white"
                  >
                    <option value="">Seleccionar producto...</option>
                    {products.filter(p => p.id !== editingProduct?.id && !p.isCombo).map(p => (
                      <option key={p.id} value={p.id}>{p.name} ({formatCOP(p.basePrice)})</option>
                    ))}
                  </select>
                  <button
                    type="button"
                    onClick={() => {
                      const found = products.find(p => p.id === addComboChildId);
                      if (found) {
                        setComboItems(prev => [...prev, { productId: found.id, productName: found.name, quantity: 1 }]);
                        setAddComboChildId('');
                      }
                    }}
                    disabled={!addComboChildId}
                    className="px-3 py-1.5 bg-amber-500 text-slate-950 font-bold rounded-lg text-xs disabled:opacity-50 cursor-pointer"
                  >
                    + Añadir
                  </button>
                </div>
              </div>
            ) : (
              /* Sección Receta e Insumos */
              <div className="p-3 bg-slate-950 border border-slate-800 rounded-xl space-y-2">
                <div className="flex items-center justify-between">
                  <span className="font-bold text-amber-400 text-xs">Composición / Receta de Insumos:</span>
                  <span className="text-[10px] text-slate-400">Descuento de stock en ventas</span>
                </div>

                {recipe.length === 0 ? (
                  <p className="text-[11px] text-slate-500">Sin receta: se descuenta del inventario directo del producto.</p>
                ) : (
                  <div className="space-y-1.5">
                    {recipe.map((r, idx) => (
                      <div key={idx} className="flex items-center justify-between p-2 bg-slate-900 rounded-lg text-xs">
                        <span>• {r.ingredientName}: <strong className="text-white">{r.quantity} {r.unit}</strong></span>
                        <button
                          type="button"
                          onClick={() => setRecipe(prev => prev.filter((_, i) => i !== idx))}
                          className="text-red-400 hover:text-red-300 p-0.5"
                        >
                          <X className="w-3.5 h-3.5" />
                        </button>
                      </div>
                    ))}
                  </div>
                )}

                <div className="pt-2 border-t border-slate-800 space-y-2">
                  <div className="grid grid-cols-3 gap-1.5">
                    <select
                      value={addIngId}
                      onChange={e => {
                        setAddIngId(e.target.value);
                        const ing = ingredients.find(i => i.id === e.target.value);
                        if (ing) setAddIngUnit(ing.unit);
                      }}
                      className="col-span-3 px-2 py-1.5 bg-slate-900 border border-slate-800 rounded-lg text-xs text-white"
                    >
                      {ingredients.map(i => (
                        <option key={i.id} value={i.id}>{i.name} ({i.unit})</option>
                      ))}
                    </select>
                    <input
                      type="number"
                      placeholder="Cantidad"
                      value={addIngQty}
                      onChange={e => setAddIngQty(e.target.value)}
                      className="col-span-2 px-2 py-1.5 bg-slate-900 border border-slate-800 rounded-lg text-xs text-white"
                    />
                    <select
                      value={addIngUnit}
                      onChange={e => setAddIngUnit(e.target.value)}
                      className="px-2 py-1.5 bg-slate-900 border border-slate-800 rounded-lg text-xs text-white"
                    >
                      <option value="g">g</option>
                      <option value="kg">kg</option>
                      <option value="ml">ml</option>
                      <option value="L">L</option>
                      <option value="unidades">unidades</option>
                    </select>
                  </div>
                  <button
                    type="button"
                    onClick={() => {
                      const found = ingredients.find(i => i.id === addIngId);
                      const q = parseFloat(addIngQty) || 0;
                      if (found && q > 0) {
                        setRecipe(prev => [...prev, { ingredientId: found.id, ingredientName: found.name, quantity: q, unit: addIngUnit }]);
                      }
                    }}
                    className="w-full py-1.5 bg-slate-900 hover:bg-slate-800 border border-slate-800 text-amber-400 font-bold rounded-lg text-xs cursor-pointer"
                  >
                    + Agregar Insumo a la Receta
                  </button>
                </div>
              </div>
            )}

            {/* Costo del Producto (Definición manual por el usuario) */}
            <div>
              <label className="text-[11px] text-slate-300 font-bold block mb-1">
                Costo del Producto (COP) *:
              </label>
              <input
                type="number"
                placeholder="Ej. 2500"
                value={baseCost}
                onChange={e => setBaseCost(e.target.value)}
                className="w-full px-3 py-2 bg-slate-950 border border-slate-800 rounded-xl font-bold text-cyan-400 focus:outline-none focus:border-amber-400"
              />
              <p className="text-[10px] text-slate-400 mt-0.5">
                Ingresa tú mismo el costo de este producto.
              </p>
            </div>

            {/* Cuadrito abajo: Agregar nuevo insumo al local */}
            <div className="p-3 bg-slate-900/90 border border-amber-500/40 rounded-2xl space-y-2.5">
              <div className="flex items-center justify-between">
                <div className="flex items-center space-x-1.5">
                  <Plus className="w-3.5 h-3.5 text-amber-400" />
                  <span className="font-black text-xs text-amber-300">Agregar Nuevo Insumo</span>
                </div>
                <span className="text-[10px] text-slate-400">Inventario del local</span>
              </div>

              {newIngSuccessMsg && (
                <div className="p-2 bg-emerald-500/20 border border-emerald-500/40 rounded-xl text-emerald-300 text-[11px] font-bold">
                  {newIngSuccessMsg}
                </div>
              )}

              <div className="space-y-2">
                <div>
                  <label className="text-[10px] text-slate-400 block mb-1">Nombre del nuevo insumo *</label>
                  <input
                    type="text"
                    placeholder="Ej. Crema chantilly, Sirope de fresa, Queso, etc."
                    value={newIngName}
                    onChange={e => setNewIngName(e.target.value)}
                    className="w-full px-2.5 py-1.5 bg-slate-950 border border-slate-800 rounded-xl text-xs text-white focus:outline-none focus:border-amber-400"
                  />
                </div>

                <div className="grid grid-cols-2 gap-2">
                  <div>
                    <label className="text-[10px] text-slate-400 block mb-1">Unidad de medida</label>
                    <select
                      value={newIngUnit}
                      onChange={e => setNewIngUnit(e.target.value as any)}
                      className="w-full px-2 py-1.5 bg-slate-950 border border-slate-800 rounded-xl text-xs text-white"
                    >
                      <option value="g">Gramos (g)</option>
                      <option value="kg">Kilogramos (kg)</option>
                      <option value="ml">Mililitros (ml)</option>
                      <option value="unidades">Unidades</option>
                    </select>
                  </div>

                  <div>
                    <label className="text-[10px] text-slate-400 block mb-1">Costo unitario (COP)</label>
                    <input
                      type="number"
                      placeholder="Ej. 25 por g/und"
                      value={newIngCost}
                      onChange={e => setNewIngCost(e.target.value)}
                      className="w-full px-2.5 py-1.5 bg-slate-950 border border-slate-800 rounded-xl text-xs text-white focus:outline-none focus:border-amber-400"
                    />
                  </div>
                </div>

                <div className="grid grid-cols-2 gap-2">
                  <div>
                    <label className="text-[10px] text-slate-400 block mb-1">Stock inicial</label>
                    <input
                      type="number"
                      placeholder="Ej. 1000"
                      value={newIngStock}
                      onChange={e => setNewIngStock(e.target.value)}
                      className="w-full px-2.5 py-1.5 bg-slate-950 border border-slate-800 rounded-xl text-xs text-white"
                    />
                  </div>

                  <div>
                    <label className="text-[10px] text-slate-400 block mb-1">Categoría</label>
                    <select
                      value={newIngCategory}
                      onChange={e => setNewIngCategory(e.target.value)}
                      className="w-full px-2.5 py-1.5 bg-slate-950 border border-slate-800 rounded-xl text-xs text-white"
                    >
                      <option value="Insumos">Insumos</option>
                      <option value="Helados">Helados</option>
                      <option value="Toppings">Toppings</option>
                      <option value="Lácteos">Lácteos</option>
                      <option value="Otros">Otros</option>
                    </select>
                  </div>
                </div>

                <button
                  type="button"
                  disabled={!newIngName.trim()}
                  onClick={handleAddNewIngredient}
                  className="w-full py-2 bg-gradient-to-r from-amber-500 to-amber-400 hover:from-amber-400 text-slate-950 font-black text-xs rounded-xl shadow cursor-pointer disabled:opacity-40"
                >
                  + Guardar Nuevo Insumo
                </button>
              </div>
            </div>

            <div className="grid grid-cols-2 gap-2">
              <div>
                <label className="text-[11px] text-slate-400 block mb-1">Existencias Iniciales:</label>
                <input
                  type="number"
                  value={stockQuantity}
                  onChange={e => setStockQuantity(e.target.value)}
                  className="w-full px-3 py-2 bg-slate-950 border border-slate-800 rounded-xl text-white focus:outline-none focus:border-amber-400"
                />
              </div>

              <div>
                <label className="text-[11px] text-slate-400 block mb-1">Unidad de medida:</label>
                <select
                  value={unit}
                  onChange={e => setUnit(e.target.value)}
                  className="w-full px-3 py-2 bg-slate-950 border border-slate-800 rounded-xl text-white focus:outline-none focus:border-amber-400"
                >
                  <option value="unidades">Unidades</option>
                  <option value="g">Gramos (g)</option>
                  <option value="kg">Kilogramos (kg)</option>
                  <option value="ml">Mililitros (ml)</option>
                  <option value="L">Litros (L)</option>
                </select>
              </div>
            </div>

            <div className="flex items-center justify-between p-2.5 bg-slate-950/70 border border-slate-800 rounded-xl">
              <div>
                <span className="text-white font-semibold block text-xs">Permitir existencias negativas</span>
                <span className="text-[10px] text-slate-400">Ventas bajo demanda aun sin stock previo</span>
              </div>
              <input
                type="checkbox"
                checked={allowNegativeStock}
                onChange={e => setAllowNegativeStock(e.target.checked)}
                className="w-4 h-4 accent-amber-500 cursor-pointer"
              />
            </div>

            <div className="flex items-center justify-between p-2.5 bg-slate-950/70 border border-slate-800 rounded-xl">
              <span className="text-white font-semibold">Producto Activo para Ventas</span>
              <input
                type="checkbox"
                checked={isActive}
                onChange={e => setIsActive(e.target.checked)}
                className="w-4 h-4 accent-amber-500 cursor-pointer"
              />
            </div>

            <div className="flex gap-2 pt-2">
              <button
                onClick={() => {
                  setEditingProduct(null);
                  setIsCreating(false);
                }}
                className="flex-1 py-2.5 bg-slate-900 border border-slate-800 text-slate-400 font-bold rounded-xl"
              >
                Cancelar
              </button>
              <button
                onClick={handleSave}
                className="flex-1 py-2.5 bg-amber-500 text-slate-950 font-black rounded-xl shadow cursor-pointer"
              >
                Guardar Producto
              </button>
            </div>
          </div>
        ) : (
          <div className="flex-1 overflow-y-auto py-3 space-y-3">
            <button
              onClick={startCreate}
              className="w-full py-2.5 bg-amber-500 text-slate-950 font-bold text-xs rounded-xl shadow flex items-center justify-center gap-1 cursor-pointer"
            >
              <Plus className="w-4 h-4" />
              <span>Crear Nuevo Producto</span>
            </button>

            <div className="flex gap-2">
              <div className="relative flex-1">
                <Search className="w-3.5 h-3.5 text-slate-400 absolute left-2.5 top-1/2 -translate-y-1/2" />
                <input
                  type="text"
                  placeholder="Buscar en catálogo..."
                  value={search}
                  onChange={e => setSearch(e.target.value)}
                  className="w-full pl-7 pr-3 py-1.5 bg-slate-950 border border-slate-800 rounded-xl text-xs text-white placeholder-slate-500 focus:outline-none"
                />
              </div>

              <select
                value={catFilter}
                onChange={e => setCatFilter(e.target.value)}
                className="px-2 py-1.5 bg-slate-950 border border-slate-800 rounded-xl text-xs text-slate-300"
              >
                <option value="todos">Todas las categorías</option>
                {categories.map(c => (
                  <option key={c.id} value={c.id}>{c.name}</option>
                ))}
              </select>
            </div>

            <div className="space-y-2 max-h-72 overflow-y-auto pr-1">
              {filtered.map(p => (
                <div
                  key={p.id}
                  className={`p-2.5 bg-slate-950/70 border rounded-xl flex items-center justify-between text-xs transition ${
                    p.isActive ? 'border-slate-800' : 'border-slate-800/40 opacity-50'
                  }`}
                >
                  <div className="flex items-center gap-2.5">
                    {p.imagePath ? (
                      <img src={p.imagePath} alt={p.name} className="w-9 h-9 object-cover rounded-lg border border-slate-800" />
                    ) : (
                      <span className="text-xl p-1 bg-slate-900 rounded-lg">{p.emoji}</span>
                    )}
                    <div>
                      <span className="font-bold text-white block text-xs">{p.name}</span>
                      <span className="text-[10px] text-amber-400 font-bold">
                        {formatCOP(p.basePrice)} • {p.stockQuantity} {p.unit}
                      </span>
                    </div>
                  </div>

                  <div className="flex items-center gap-1.5">
                    <button
                      onClick={() => startEdit(p)}
                      className="px-2.5 py-1 bg-slate-900 border border-slate-800 text-slate-200 hover:text-white rounded-lg text-[11px] font-bold"
                    >
                      Editar
                    </button>
                    <button
                      onClick={() => {
                        if (confirm(`¿Eliminar ${p.name}?`)) onDeleteProduct(p.id);
                      }}
                      className="p-1 text-red-400 hover:text-red-300"
                    >
                      <Trash2 className="w-3.5 h-3.5" />
                    </button>
                  </div>
                </div>
              ))}
            </div>
          </div>
        )}
      </div>
    </div>
  );
}

function CategoryManagementModal({
  categories,
  products,
  onCreateCategory,
  onUpdateCategory,
  onDeleteCategory,
  onClose
}: {
  categories: CategoryItem[];
  products: Product[];
  onCreateCategory: (name: string, emoji: string) => string | null;
  onUpdateCategory: (id: string, name: string, emoji: string) => string | null;
  onDeleteCategory: (id: string, reassignToId: string) => string | null;
  onClose: () => void;
}) {
  const [newCatName, setNewCatName] = useState('');
  const [newCatEmoji, setNewCatEmoji] = useState('🍦');
  const [catToDelete, setCatToDelete] = useState<CategoryItem | null>(null);
  const [reassignTargetId, setReassignTargetId] = useState<string>('');
  const [errorMsg, setErrorMsg] = useState<string | null>(null);

  const getProductCount = (catId: string) => products.filter(p => p.categoryId === catId).length;

  return (
    <div className="fixed inset-0 z-50 bg-black/85 backdrop-blur-sm flex items-end sm:items-center justify-center p-0 sm:p-4">
      <div className="w-full max-w-md bg-[#0B1224] border border-slate-800 rounded-t-[32px] sm:rounded-3xl p-5 shadow-2xl flex flex-col space-y-3.5 animate-in slide-in-from-bottom duration-200">
        <div className="flex items-center justify-between">
          <div className="flex items-center space-x-2">
            <Layers className="w-5 h-5 text-amber-400" />
            <h3 className="font-black text-sm text-white">
              {catToDelete ? 'Reasignar Productos & Eliminar' : 'Administrar Categorías'}
            </h3>
          </div>
          <button onClick={onClose} className="p-1 hover:bg-slate-800 text-slate-400 rounded-lg">
            <X className="w-5 h-5" />
          </button>
        </div>

        {errorMsg && (
          <div className="p-2 bg-red-500/20 border border-red-500/40 rounded-xl text-red-300 text-xs font-semibold">
            {errorMsg}
          </div>
        )}

        {catToDelete ? (
          <div className="space-y-3 text-xs">
            <p className="text-slate-300">
              La categoría <strong className="text-amber-400">{catToDelete.name}</strong> contiene <strong className="text-white">{getProductCount(catToDelete.id)} producto(s)</strong>.
              Para no perderlos, selecciona a qué categoría reasignarlos obligatoriamente:
            </p>

            <div className="space-y-1.5">
              {categories.filter(c => c.id !== catToDelete.id).map(target => {
                const isSelected = reassignTargetId === target.id;
                return (
                  <div
                    key={target.id}
                    onClick={() => setReassignTargetId(target.id)}
                    className={`p-2.5 rounded-xl border flex items-center justify-between cursor-pointer ${
                      isSelected
                        ? 'bg-amber-500/20 border-amber-500 text-white font-bold'
                        : 'bg-slate-950 border-slate-800 text-slate-300'
                    }`}
                  >
                    <span>{target.emoji} {target.name}</span>
                    {isSelected && <Check className="w-4 h-4 text-amber-400" />}
                  </div>
                );
              })}
            </div>

            <div className="flex gap-2 pt-2">
              <button
                onClick={() => setCatToDelete(null)}
                className="flex-1 py-2.5 bg-slate-900 border border-slate-800 text-slate-400 font-bold rounded-xl"
              >
                Cancelar
              </button>
              <button
                disabled={!reassignTargetId}
                onClick={() => {
                  const err = onDeleteCategory(catToDelete.id, reassignTargetId);
                  if (err) setErrorMsg(err);
                  else setCatToDelete(null);
                }}
                className="flex-1 py-2.5 bg-red-600 hover:bg-red-500 text-white font-black rounded-xl shadow disabled:opacity-50 cursor-pointer"
              >
                Reasignar y Eliminar
              </button>
            </div>
          </div>
        ) : (
          <div className="space-y-3">
            {/* Create form */}
            <div className="flex gap-2">
              <input
                type="text"
                placeholder="Nueva categoría..."
                value={newCatName}
                onChange={e => {
                  setNewCatName(e.target.value);
                  setErrorMsg(null);
                }}
                className="flex-1 px-3 py-2 bg-slate-950 border border-slate-800 rounded-xl text-xs text-white focus:outline-none focus:border-amber-400"
              />
              <button
                onClick={() => {
                  const err = onCreateCategory(newCatName, newCatEmoji);
                  if (err) setErrorMsg(err);
                  else setNewCatName('');
                }}
                className="px-4 py-2 bg-amber-500 text-slate-950 font-bold text-xs rounded-xl shadow cursor-pointer"
              >
                Crear
              </button>
            </div>

            {/* List */}
            <div className="space-y-2 max-h-60 overflow-y-auto pr-1">
              {categories.map(c => {
                const count = getProductCount(c.id);
                return (
                  <div
                    key={c.id}
                    className="p-2.5 bg-slate-950 border border-slate-800 rounded-xl flex items-center justify-between text-xs"
                  >
                    <div>
                      <span className="font-bold text-white block text-xs">{c.emoji} {c.name}</span>
                      <span className="text-[10px] text-slate-500">{count} producto(s) asignado(s)</span>
                    </div>

                    {categories.length > 1 && (
                      <button
                        onClick={() => {
                          const fallback = categories.find(cat => cat.id !== c.id);
                          setReassignTargetId(fallback?.id || '');
                          setCatToDelete(c);
                          setErrorMsg(null);
                        }}
                        className="p-1 text-red-400 hover:text-red-300"
                        title="Eliminar categoría con reasignación"
                      >
                        <Trash2 className="w-3.5 h-3.5" />
                      </button>
                    )}
                  </div>
                );
              })}
            </div>
          </div>
        )}
      </div>
    </div>
  );
}

function StockAuditModal({
  products,
  movements,
  auditorName,
  onAdjustStock,
  onClose
}: {
  products: Product[];
  movements: InventoryMovement[];
  auditorName: string;
  onAdjustStock: (productId: string, newStock: number, reason: string, type: string) => string | null;
  onClose: () => void;
}) {
  const [subTab, setSubTab] = useState<'adjust' | 'logs'>('adjust');
  const [selectedProductId, setSelectedProductId] = useState<string>(products[0]?.id || '');
  const [mode, setMode] = useState<'MANUAL_ADJUSTMENT' | 'PURCHASE' | 'WASTE_MERMA'>('PURCHASE');
  const [amountInput, setAmountInput] = useState('');
  const [reasonInput, setReasonInput] = useState('');
  const [errorMsg, setErrorMsg] = useState<string | null>(null);

  const selectedProd = products.find(p => p.id === selectedProductId) || products[0];

  const handleApply = () => {
    if (!selectedProd) return;
    const qty = parseFloat(amountInput);
    if (isNaN(qty) || qty <= 0 && mode !== 'MANUAL_ADJUSTMENT') {
      setErrorMsg('Ingresa una cantidad numérica válida.');
      return;
    }
    if (!reasonInput.trim()) {
      setErrorMsg('El motivo del movimiento es obligatorio para la auditoría.');
      return;
    }

    let resultingStock = selectedProd.stockQuantity;
    if (mode === 'PURCHASE') {
      resultingStock = selectedProd.stockQuantity + qty;
    } else if (mode === 'WASTE_MERMA') {
      resultingStock = Math.max(0, selectedProd.stockQuantity - qty);
    } else {
      resultingStock = qty;
    }

    const err = onAdjustStock(selectedProd.id, resultingStock, reasonInput, mode);
    if (err) {
      setErrorMsg(err);
    } else {
      setAmountInput('');
      setReasonInput('');
      setSubTab('logs');
    }
  };

  return (
    <div className="fixed inset-0 z-50 bg-black/85 backdrop-blur-sm flex items-end sm:items-center justify-center p-0 sm:p-4">
      <div className="w-full max-w-md bg-[#0B1224] border border-slate-800 rounded-t-[32px] sm:rounded-3xl p-5 shadow-2xl flex flex-col max-h-[90vh] animate-in slide-in-from-bottom duration-200">
        <div className="flex items-center justify-between pb-3 border-b border-slate-800">
          <div className="flex items-center space-x-2">
            <RefreshCw className="w-5 h-5 text-amber-400" />
            <h3 className="font-black text-sm text-white">Inventario y Auditoría</h3>
          </div>
          <button onClick={onClose} className="p-1 hover:bg-slate-800 text-slate-400 rounded-lg">
            <X className="w-5 h-5" />
          </button>
        </div>

        {/* Subtabs */}
        <div className="grid grid-cols-2 gap-1 bg-slate-950 p-1 rounded-xl border border-slate-800 my-3">
          <button
            onClick={() => setSubTab('adjust')}
            className={`py-1.5 rounded-lg text-xs font-bold transition ${
              subTab === 'adjust' ? 'bg-amber-500 text-slate-950 font-black shadow' : 'text-slate-400'
            }`}
          >
            Ajustar Stock
          </button>
          <button
            onClick={() => setSubTab('logs')}
            className={`py-1.5 rounded-lg text-xs font-bold transition ${
              subTab === 'logs' ? 'bg-amber-500 text-slate-950 font-black shadow' : 'text-slate-400'
            }`}
          >
            Auditoría ({movements.length})
          </button>
        </div>

        {errorMsg && (
          <div className="mb-2 p-2 bg-red-500/20 border border-red-500/40 rounded-xl text-red-300 text-xs font-semibold">
            {errorMsg}
          </div>
        )}

        {subTab === 'adjust' ? (
          <div className="flex-1 overflow-y-auto space-y-3 text-xs pr-1">
            <div>
              <label className="text-[11px] text-slate-400 block mb-1">Producto a modificar:</label>
              <select
                value={selectedProductId}
                onChange={e => setSelectedProductId(e.target.value)}
                className="w-full px-3 py-2 bg-slate-950 border border-slate-800 rounded-xl text-white font-semibold focus:outline-none focus:border-amber-400"
              >
                {products.map(p => (
                  <option key={p.id} value={p.id}>
                    {p.emoji} {p.name} (Stock: {p.stockQuantity} {p.unit})
                  </option>
                ))}
              </select>
            </div>

            <div>
              <label className="text-[11px] text-slate-400 block mb-1">Tipo de Operación:</label>
              <div className="grid grid-cols-3 gap-1.5">
                <button
                  type="button"
                  onClick={() => setMode('PURCHASE')}
                  className={`py-1.5 px-2 rounded-xl border font-bold text-[11px] transition ${
                    mode === 'PURCHASE'
                      ? 'bg-emerald-500/20 border-emerald-500 text-emerald-300'
                      : 'bg-slate-950 border-slate-800 text-slate-400'
                  }`}
                >
                  + Entrada
                </button>
                <button
                  type="button"
                  onClick={() => setMode('WASTE_MERMA')}
                  className={`py-1.5 px-2 rounded-xl border font-bold text-[11px] transition ${
                    mode === 'WASTE_MERMA'
                      ? 'bg-red-500/20 border-red-500 text-red-300'
                      : 'bg-slate-950 border-slate-800 text-slate-400'
                  }`}
                >
                  - Merma
                </button>
                <button
                  type="button"
                  onClick={() => setMode('MANUAL_ADJUSTMENT')}
                  className={`py-1.5 px-2 rounded-xl border font-bold text-[11px] transition ${
                    mode === 'MANUAL_ADJUSTMENT'
                      ? 'bg-amber-500/20 border-amber-500 text-amber-300'
                      : 'bg-slate-950 border-slate-800 text-slate-400'
                  }`}
                >
                  Fijar Total
                </button>
              </div>
            </div>

            <div>
              <label className="text-[11px] text-slate-400 block mb-1">
                {mode === 'MANUAL_ADJUSTMENT' ? 'Nuevo Total Resultante:' : 'Cantidad a Modificar:'}
              </label>
              <input
                type="number"
                placeholder={mode === 'MANUAL_ADJUSTMENT' ? 'Ej. 50' : 'Ej. 10'}
                value={amountInput}
                onChange={e => {
                  setAmountInput(e.target.value);
                  setErrorMsg(null);
                }}
                className="w-full px-3 py-2 bg-slate-950 border border-slate-800 rounded-xl font-bold text-amber-400 focus:outline-none focus:border-amber-400"
              />
            </div>

            <div>
              <label className="text-[11px] text-slate-400 block mb-1">Motivo Obligatorio de Auditoría:</label>
              <input
                type="text"
                placeholder="Ej. Factura compra #540, daño en refrigerador"
                value={reasonInput}
                onChange={e => {
                  setReasonInput(e.target.value);
                  setErrorMsg(null);
                }}
                className="w-full px-3 py-2 bg-slate-950 border border-slate-800 rounded-xl text-white focus:outline-none focus:border-amber-400"
              />
            </div>

            <p className="text-[10px] text-slate-500">
              Auditor responsable: <strong className="text-slate-300">{auditorName}</strong>
            </p>

            <button
              onClick={handleApply}
              className="w-full py-2.5 bg-amber-500 text-slate-950 font-black text-xs rounded-xl shadow cursor-pointer mt-2"
            >
              Registrar Movimiento
            </button>
          </div>
        ) : (
          <div className="flex-1 overflow-y-auto space-y-2 pr-1">
            {movements.length === 0 ? (
              <p className="text-slate-500 text-center py-6 text-xs">Sin movimientos auditados.</p>
            ) : (
              movements.map(m => (
                <div key={m.id} className="p-2.5 bg-slate-950 border border-slate-800 rounded-xl text-xs space-y-1">
                  <div className="flex items-center justify-between">
                    <span className="font-bold text-white text-xs">{m.productName}</span>
                    <span className={`font-black text-xs ${m.change >= 0 ? 'text-emerald-400' : 'text-red-400'}`}>
                      {m.change >= 0 ? `+${m.change}` : m.change} (Stock: {m.resultingStock})
                    </span>
                  </div>
                  <div className="flex items-center justify-between text-[10px] text-slate-400">
                    <span className="font-mono bg-slate-900 px-1.5 py-0.5 rounded text-amber-300">{m.type}</span>
                    <span>{m.dateFormatted}</span>
                  </div>
                  <p className="text-[10px] text-slate-300 italic">«{m.reason}» • por {m.userName}</p>
                </div>
              ))
            )}
          </div>
        )}
      </div>
    </div>
  );
}
