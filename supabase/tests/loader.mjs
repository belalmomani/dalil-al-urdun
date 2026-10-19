export async function resolve(specifier, context, next) {
  if (specifier.startsWith('npm:@supabase/supabase-js')) return { url: new URL('./stub-supabase.mjs', import.meta.url).href, shortCircuit: true };
  if (specifier.startsWith('npm:jose')) return { url: new URL('./stub-jose.mjs', import.meta.url).href, shortCircuit: true };
  return next(specifier, context);
}
