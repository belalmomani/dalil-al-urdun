-- دليل الأردن — مخطط Supabase
-- الدخول عبر Firebase Auth؛ هنا البيانات والوسائط فقط.
-- المبدأ: الزوار (مفتاح anon) يقرؤون المنشور فقط. كل كتابة تمر عبر الدالة admin-api
-- التي تتحقق من رمز Firebase وتستخدم service_role (الذي يتجاوز RLS). لا توجد سياسات كتابة لـ anon/authenticated.

-- ---------------------------------------------------------------- الجداول
create table if not exists public.regions (
  id         text primary key,
  name       text not null check (char_length(name) between 1 and 120),
  type       text not null default 'city' check (type in ('governorate','city','town','village','neighborhood')),
  parent_id  text,
  status     text not null default 'published' check (status in ('draft','published','archived')),
  updated_at timestamptz not null default now()
);

create table if not exists public.places (
  id            text primary key check (id ~ '^[a-z0-9][a-z0-9-]{0,62}$'),
  name          text not null check (char_length(name) between 1 and 200),
  category      text not null check (category in ('tourism','restaurants','government','shopping','education','news','jobs','apps','services')),
  status        text not null default 'draft' check (status in ('draft','review','published','archived')),
  region_id     text references public.regions(id) on delete set null,
  region_name   text,
  description   text not null default '' check (char_length(description) <= 5000),
  phone         text check (phone is null or char_length(phone) <= 40),
  address       text check (address is null or char_length(address) <= 300),
  lat           double precision check (lat is null or lat between -90 and 90),
  lng           double precision check (lng is null or lng between -180 and 180),
  website       text check (website is null or char_length(website) <= 300),
  price_level   smallint check (price_level is null or price_level in (1,2,3)),
  price_note    text,
  opening_hours text,
  kids_friendly boolean,
  age_groups    text[] not null default '{}',
  features      text[] not null default '{}',
  activities    text[] not null default '{}',
  alerts        text[] not null default '{}',
  source_name   text,
  source_url    text,
  verified_at   timestamptz,           -- null = غير موثّق
  featured      boolean not null default false,
  cover_url     text,
  cover_alt     text,
  created_at    timestamptz not null default now(),
  updated_at    timestamptz not null default now()
);
create index if not exists places_status_name_idx on public.places (status, name);
create index if not exists places_region_idx on public.places (region_id);

create table if not exists public.media (
  id           text primary key,
  place_id     text not null references public.places(id) on delete cascade,
  type         text not null check (type in ('image','video')),
  url          text not null,
  storage_path text not null,
  thumb_url    text,
  title        text,
  description  text,
  alt          text,
  sort_order   integer not null default 0,
  is_cover     boolean not null default false,
  duration_sec integer,
  source       text,
  license      text,
  content_type text,
  size_bytes   bigint,
  uploaded_by  text,
  uploaded_at  timestamptz not null default now()
);
create index if not exists media_place_idx on public.media (place_id, sort_order);

create table if not exists public.settings (
  key   text primary key,
  value jsonb not null
);
insert into public.settings (key, value) values ('public', '{"adsEnabled": true}'::jsonb)
  on conflict (key) do nothing;

create table if not exists public.reports (
  id           uuid primary key default gen_random_uuid(),
  place_id     text,
  reason       text not null,
  details      text,
  reporter_uid text not null,
  status       text not null default 'open' check (status in ('open','resolved')),
  created_at   timestamptz not null default now()
);
create index if not exists reports_status_idx on public.reports (status, created_at desc);

create table if not exists public.suggestions (
  id               uuid primary key default gen_random_uuid(),
  name             text not null,
  category         text,
  region_name      text,
  details          text,
  suggested_by_uid text not null,
  status           text not null default 'pending' check (status in ('pending','reviewed')),
  created_at       timestamptz not null default now()
);
create index if not exists suggestions_status_idx on public.suggestions (status, created_at desc);

create table if not exists public.favorites (
  uid        text primary key,
  place_ids  text[] not null default '{}',
  updated_at timestamptz not null default now()
);

-- المحررون/المديرون الإضافيون. المالك يُحدَّد بالبريد في إعداد الخادم (ADMIN_EMAIL) وليس هنا.
create table if not exists public.staff (
  email      text primary key check (email = lower(email)),
  role       text not null check (role in ('admin','editor')),
  added_by   text,
  created_at timestamptz not null default now()
);

create table if not exists public.audit (
  id          bigint generated always as identity primary key,
  action      text not null,
  target      text,
  actor_uid   text,
  actor_email text,
  extra       jsonb,
  at          timestamptz not null default now()
);
create index if not exists audit_at_idx on public.audit (at desc);

-- ---------------------------------------------------------------- الأمان (RLS)
alter table public.regions     enable row level security;
alter table public.places      enable row level security;
alter table public.media       enable row level security;
alter table public.settings    enable row level security;
alter table public.reports     enable row level security;
alter table public.suggestions enable row level security;
alter table public.favorites   enable row level security;
alter table public.staff       enable row level security;
alter table public.audit       enable row level security;

drop policy if exists "public read published regions" on public.regions;
create policy "public read published regions" on public.regions
  for select to anon, authenticated using (status = 'published');

drop policy if exists "public read published places" on public.places;
create policy "public read published places" on public.places
  for select to anon, authenticated using (status = 'published');

drop policy if exists "public read media of published places" on public.media;
create policy "public read media of published places" on public.media
  for select to anon, authenticated
  using (exists (select 1 from public.places p where p.id = media.place_id and p.status = 'published'));

drop policy if exists "public read public settings" on public.settings;
create policy "public read public settings" on public.settings
  for select to anon, authenticated using (key = 'public');

-- reports / suggestions / favorites / staff / audit: لا سياسات = ممنوع تمامًا على anon وauthenticated.
-- (service_role في الدالة يتجاوز RLS.) نسحب الصلاحيات صراحةً كطبقة إضافية.
revoke all on public.reports, public.suggestions, public.favorites, public.staff, public.audit from anon, authenticated;
revoke insert, update, delete, truncate on public.regions, public.places, public.media, public.settings from anon, authenticated;

-- ---------------------------------------------------------------- التخزين
-- حاوية عامة للقراءة (الصور تُعرض للزوار). الرفع فقط عبر روابط موقّعة تصدرها الدالة بعد التحقق من الصلاحية.
-- ملاحظة: ملفات المسودات قابلة للقراءة لمن يعرف الرابط الكامل (بخلاف سجلاتها في الجداول).
insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
values ('media', 'media', true, 62914560,
        array['image/jpeg','image/png','image/webp','video/mp4','video/webm'])
on conflict (id) do update
  set public = excluded.public,
      file_size_limit = excluded.file_size_limit,
      allowed_mime_types = excluded.allowed_mime_types;
