// قاعدة بيانات وتخزين في الذاكرة بواجهة تشبه supabase-js بالقدر الذي تستعمله الدالة.
export const DB = { tables: {}, files: new Set(), failStorageRemove: false };
export const reset = () => { DB.tables = {}; DB.files = new Set(); DB.failStorageRemove = false; };
const T = (n) => (DB.tables[n] ??= []);
const KEY = { favorites: 'uid', staff: 'email', settings: 'key' };
let seq = 0;

class Q {
  constructor(table) { this.t = table; this.op = 'select'; this.f = []; this.opts = {}; this.ord = null; this.rng = null; this.lim = null; this.payload = null; this.ret = false; this.one = null; }
  select(_c, o) { if (this.op === 'select') this.opts = o || {}; else this.ret = true; return this; }
  insert(p) { this.op = 'insert'; this.payload = p; return this; }
  upsert(p, o) { this.op = 'upsert'; this.payload = p; this.conflict = o?.onConflict || KEY[this.t] || 'id'; return this; }
  update(p) { this.op = 'update'; this.payload = p; return this; }
  delete() { this.op = 'delete'; return this; }
  eq(c, v) { this.f.push((r) => r[c] === v); return this; }
  gte(c, v) { this.f.push((r) => r[c] >= v); return this; }
  order(c, o) { this.ord = [c, o?.ascending === false ? -1 : 1]; return this; }
  limit(n) { this.lim = n; return this; }
  range(a, b) { this.rng = [a, b]; return this; }
  maybeSingle() { this.one = 'maybe'; return this; }
  single() { this.one = 'single'; return this; }
  then(res, rej) { return Promise.resolve(this.run()).then(res, rej); }
  run() {
    const rows = T(this.t);
    const match = (r) => this.f.every((fn) => fn(r));
    let out; let count = null;
    if (this.op === 'insert') {
      const arr = Array.isArray(this.payload) ? this.payload : [this.payload]; out = [];
      for (const r of arr) {
        const k = KEY[this.t] || 'id';
        if (r[k] !== undefined && rows.some((x) => x[k] === r[k])) return { data: null, error: { message: 'duplicate key' } };
        const defaults = ['reports', 'suggestions'].includes(this.t)
          ? { id: `00000000-0000-0000-0000-${String(++seq).padStart(12, '0')}`, status: this.t === 'reports' ? 'open' : 'pending', created_at: new Date().toISOString() } : {};
        const row = { ...defaults, ...r }; rows.push(row); out.push(row);
      }
    } else if (this.op === 'upsert') {
      const arr = Array.isArray(this.payload) ? this.payload : [this.payload]; out = [];
      for (const r of arr) {
        const i = rows.findIndex((x) => x[this.conflict] === r[this.conflict]);
        if (i >= 0) { Object.assign(rows[i], r); out.push(rows[i]); } else { rows.push({ ...r }); out.push(rows[rows.length - 1]); }
      }
    } else if (this.op === 'update') {
      out = rows.filter(match); out.forEach((r) => Object.assign(r, this.payload));
    } else if (this.op === 'delete') {
      out = rows.filter(match); DB.tables[this.t] = rows.filter((r) => !match(r));
      // حذف بالتتالي لوسائط المكان
      if (this.t === 'places') { const ids = out.map((p) => p.id); DB.tables.media = T('media').filter((m) => !ids.includes(m.place_id)); }
    } else {
      out = rows.filter(match);
      if (this.ord) out = out.slice().sort((a, b) => (a[this.ord[0]] > b[this.ord[0]] ? 1 : a[this.ord[0]] < b[this.ord[0]] ? -1 : 0) * this.ord[1]);
      count = out.length;
      if (this.rng) out = out.slice(this.rng[0], this.rng[1] + 1);
      if (this.lim != null) out = out.slice(0, this.lim);
      if (this.opts.head) return { data: null, count, error: null };
    }
    if (this.one) {
      if (out.length > 1 && this.one === 'single') return { data: null, error: { message: 'multiple rows' } };
      if (out.length === 0 && this.one === 'single') return { data: null, error: { message: 'no rows' } };
      return { data: out[0] ?? null, error: null, count };
    }
    return { data: this.op === 'select' || this.ret ? out.map((r) => ({ ...r })) : null, error: null, count };
  }
}

export function createClient() {
  return {
    from: (t) => new Q(t),
    storage: {
      from: () => ({
        async createSignedUploadUrl(path) { DB.files.add(`pending:${path}`); return { data: { token: 'tok-' + path, signedUrl: 'https://x/upload/' + path }, error: null }; },
        async list(dir, o) { const names = [...DB.files].filter((p) => !p.startsWith('pending:') && p.startsWith(dir + '/')).map((p) => ({ name: p.slice(dir.length + 1) })).filter((x) => !o?.search || x.name.includes(o.search)); return { data: names, error: null }; },
        async remove(paths) { if (DB.failStorageRemove) return { data: null, error: { message: 'boom' } }; paths.forEach((p) => DB.files.delete(p)); return { data: [], error: null }; },
      }),
    },
  };
}
