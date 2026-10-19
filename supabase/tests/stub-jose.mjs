// رمز الاختبار: "uid|email|verified|project" — الدالة الحقيقية تستخدم jose والتوقيع الفعلي.
export const createRemoteJWKSet = () => () => {};
export async function jwtVerify(token, _jwks, opts) {
  const [uid, email, verified, project] = String(token).split('|');
  if (!uid || project === undefined) throw new Error('bad token');
  if (opts.audience !== project) throw new Error('aud mismatch');
  return { payload: { sub: uid, email, email_verified: verified === '1' } };
}
