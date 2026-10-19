// يوجّه استيرادات npm: في دالة Deno إلى بدائل محلية للاختبار في Node.
import { register } from 'node:module';
register('./loader.mjs', import.meta.url);
