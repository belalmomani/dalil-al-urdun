-- أرشفة (وليس حذف) الأماكن المكررة مع إبقاء النسخة الأفضل. قابل للتراجع: انظر آخر الملف.
-- المُبقى: the-jordan-museum, royal-automobile-museum, dar-al-saraya-museum, just-university
begin;
update public.places set status='archived', updated_at=now()
where id in ('jordan-museum-amman','jordan-museum','royal-automobile-museum-amman','dar-saraya-museum-irbid','jordan-university-science-technology')
  and status <> 'archived';
commit;
-- للتراجع عن عنصر: update public.places set status='published' where id='<المعرّف>';
