/* ═══════════════════════════════════════════
   Portfolio Tracker — Supabase Auth Module
   Initializes Supabase client and provides auth helpers.
   ═══════════════════════════════════════════ */

var SUPABASE_URL = 'https://agtkcnxmlbccbwmsuxdz.supabase.co';
var SUPABASE_KEY = 'sb_publishable_W_Nc9hzh_M-uMNMbTPeE5w_6IKDgPd3';
var CONTEXT_PATH = window.__CONTEXT_PATH__ || window.location.pathname.replace(/\/+$/, '').replace(/\/login\.html$/, '') || '/portfoliotracker';

// Internal Supabase client instance (underscore avoids shadowing window.supabase from CDN)
var _supabase = null;

/**
 * Initialize the Supabase client. Idempotent — safe to call multiple times.
 */
function initSupabase() {
  if (_supabase) return _supabase;
  if (!window.supabase || !window.supabase.createClient) {
    console.error('auth.js: Supabase SDK not loaded. Check CDN script tag.');
    return null;
  }
  _supabase = window.supabase.createClient(SUPABASE_URL, SUPABASE_KEY);
  _supabase.auth.onAuthStateChange(onAuthChange);
  return _supabase;
}

/**
 * Returns the current access token, refreshing the session if needed.
 * Returns null if no session exists.
 */
async function getAccessToken() {
  try {
    var client = initSupabase();
    if (!client) return null;
    var resp = await client.auth.getSession();
    var session = resp.data && resp.data.session;
    return session ? session.access_token : null;
  } catch (e) {
    console.error('auth.js: getAccessToken error', e);
    return null;
  }
}

/**
 * Returns true if the user has an active session.
 */
async function hasSession() {
  try {
    var client = initSupabase();
    if (!client) return false;
    var resp = await client.auth.getSession();
    return !!(resp.data && resp.data.session);
  } catch (e) {
    return false;
  }
}

/**
 * Guards a page: redirects to login.html if no session.
 * Call this at the start of protected pages.
 */
async function requireAuth() {
  if (!(await hasSession())) {
    var loginUrl = CONTEXT_PATH + '/login.html';
    window.location.replace(loginUrl);
    throw new Error('redirecting to login');
  }
}

/**
 * Signs out and redirects to login.html.
 */
async function signOut() {
  try {
    var client = initSupabase();
    if (client) await client.auth.signOut();
  } catch (e) {
    console.error('auth.js: signOut error', e);
  }
  window.location.replace(CONTEXT_PATH + '/login.html');
}

/**
 * Auth state change handler.
 */
function onAuthChange(event, session) {
  if (event === 'SIGNED_OUT') {
    window.location.replace(CONTEXT_PATH + '/login.html');
  }
}
