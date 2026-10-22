-- دعم اللغة الإنجليزية: أعمدة اختيارية (null = يعرض التطبيق النص العربي). آمن للتكرار.
alter table public.places  add column if not exists name_en text;
alter table public.places  add column if not exists description_en text;
alter table public.places  add column if not exists region_name_en text;
alter table public.regions add column if not exists name_en text;
