-- English names, batch 3 (last 6). Safe to re-run.
begin;
update public.places set name_en=$t$Ma'an Vocational Training Institute$t$ where id='maan-vocational-training-institute' and name_en is null;
update public.places set name_en=$t$Civil Status and Passports Office – Jabal Amman$t$ where id='civil-status-jabal-amman' and name_en is null;
update public.places set name_en=$t$Jordan EcoPark$t$ where id='jordan-ecopark-irbid' and name_en is null;
update public.places set name_en=$t$Sanad Government Services Platform$t$ where id='sanad-official-services' and name_en is null;
update public.places set name_en=$t$Iraq al-Amir Site$t$ where id='iraq-al-amir' and name_en is null;
update public.places set name_en=$t$Mukawir Archaeological Site$t$ where id='mukawir-archaeological-site' and name_en is null;
commit;
