-- منصات وتطبيقات حكومية وسياحية تم التحقق منها بتاريخ 2026-10-22 من مواقعها الرسمية. آمن للتكرار.
-- لا أرقام هواتف ولا أسعار ولا تقييمات. وُصف كل عنصر بما ورد في مصدره فقط.
begin;
insert into public.places (id,name,name_en,category,status,description,description_en,website,source_name,source_url,verified_at,alerts) values
('tawasal-portal','بوابة تواصل','Tawasal Portal','apps','published',
 'منصة حكومية موحّدة للتشاور العام، يستطيع من خلالها الأفراد والجهات المعنية التعليق على التشريعات والمشاريع الحكومية قبل إقرارها، والمشاركة في الاستطلاعات والاطلاع على البيانات المفتوحة.',
 'A unified government platform for public consultation where individuals and stakeholders can comment on legislation and government projects before they are adopted, take part in surveys and access open data.',
 'https://tawasal.gov.jo','بوابة تواصل (الموقع الرسمي)','https://tawasal.gov.jo',now(),array[]::text[]),
('petrapass-tickets','بوابة تذاكر البتراء (PetraPass)','PetraPass – Petra Tickets','apps','published',
 'الموقع الرسمي لشراء تذاكر دخول موقع البتراء مسبقًا بتاريخ محدد والدفع إلكترونيًا، ويتيح البحث عن تذكرة قائمة وتسجيل الدخول لإدارة الحجوزات. تشغّله سلطة إقليم البترا التنموي السياحي.',
 'The official website for buying Petra admission tickets in advance for a specific date and paying online. It also lets you look up an existing ticket and log in to manage bookings. Operated by the Petra Development and Tourism Region Authority.',
 'https://petrapass.pdtra.gov.jo','PetraPass (الموقع الرسمي)','https://petrapass.pdtra.gov.jo',now(),
 array['التذاكر غير قابلة للاسترداد أو الاستبدال أو التحويل، ولا يمكن التعديل بعد الحجز؛ تأكد من التاريخ قبل التأكيد (حسب الموقع الرسمي).']),
('moi-evisa-portal','بوابة التأشيرات والإقامة الإلكترونية – وزارة الداخلية','Ministry of Interior E-Visa and Residence Portal','apps','published',
 'النظام الإلكتروني لوزارة الداخلية لخدمات التأشيرات والإقامة (16 خدمة في أربع مجموعات: التأشيرات، الخدمة السنوية، تجديد الإقامة المؤقتة، وخدمات أخرى). تُقبل طلبات التأشيرة المقدَّمة من خارج المملكة فقط، ويتم التسجيل بحساب بريد إلكتروني ومتابعة الطلب والدفع إلكترونيًا.',
 'The Ministry of Interior electronic system for visa and residence services (16 services in four groups: visas, annual service, temporary residence renewal and other services). Visa applications are accepted only from outside the Kingdom; applicants register with an email account, track applications and pay electronically.',
 'https://eservices.moi.gov.jo/MOI_EVISA/faces/Pages/Runnables/login.jsf','وزارة الداخلية الأردنية – التقديم الإلكتروني للتأشيرات والإقامة','https://moi.gov.jo/En/Pages/E_Applications_for_Visa_and_Residence_Permits',now(),
 array['تحقق من الشروط والرسوم الحالية من موقع الوزارة قبل التقديم.']),
('bekhedmetkom-platform','منصة بخدمتكم (التواصل الحكومي التفاعلي)','Bekhedmetkom (Interactive e-Gov Platform)','apps','published',
 'قناة تتيح للمواطنين التواصل مع الحكومة بشأن الخدمات العامة: الاستفسار وتقديم الاقتراحات والثناء والشكاوى والإبلاغ عن المخالفات، وتغطي 106 دوائر خدمات حكومية، وللطلبات مدد رد محددة وتُحال المتأخرة تلقائيًا لمسؤولين أعلى. تتوفر عنها صفحة تطبيق على متجر Google Play.',
 'A channel that lets citizens communicate with the government about public services: ask questions, make suggestions, give compliments, file complaints or report violations. It covers 106 government service departments; requests have set response times and overdue ones are automatically referred to higher-level officials. A Google Play app is listed for it.',
 null,'UN ESCWA Open Government – Bekhedmetkom Platform','https://opengov.unescwa.org/node/1164',now(),
 array['رابط الموقع الرسمي لم يُتحقق منه بعد؛ ابحث عن «بخدمتكم» في متجر التطبيقات أو من بوابة الأردن jordan.gov.jo.'])
on conflict (id) do update set name=excluded.name, name_en=excluded.name_en, category=excluded.category, description=excluded.description, description_en=excluded.description_en, website=excluded.website, source_name=excluded.source_name, source_url=excluded.source_url, verified_at=excluded.verified_at, alerts=excluded.alerts, updated_at=now();
-- إكمال رابط الموقع الرسمي للعنصرين الموجودين إن كان فارغًا (تم التحقق من الصفحتين)
update public.places set website='https://sanad.gov.jo' where id='sanad-official-services' and website is null;
update public.places set website='https://jordan.gov.jo' where id='jordan-government-services-portal' and website is null;
commit;
