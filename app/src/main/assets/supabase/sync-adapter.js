// BADEGNAN OS — safe local-first sync adapter placeholder.
// No business data is modified here. A future authenticated sync layer can listen to
// badegnan:supabase-ready and explicitly synchronize validated snapshots.
window.BADEGNAN_SYNC = window.BADEGNAN_SYNC || {
  enabled: false,
  mode: 'local-first',
  status: 'local'
};
