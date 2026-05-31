/* ═══════════════════════════════════════════
   Portfolio Tracker — Alpine.js Store
   Handles: theme, drawer, modals, toasts, keyboard shortcuts
   ═══════════════════════════════════════════ */

document.addEventListener('alpine:init', () => {
  Alpine.data('app', () => ({
    // ── Theme ──
    theme: localStorage.getItem('theme_pref') || (window.matchMedia && window.matchMedia('(prefers-color-scheme:dark)').matches ? 'dark' : 'light'),
    toggleTheme() {
      this.theme = this.theme === 'dark' ? 'light' : 'dark';
      document.documentElement.setAttribute('data-theme', this.theme);
      localStorage.setItem('theme_pref', this.theme);
      // trigger chart/history refresh if those functions exist
      if (typeof renderPieChart === 'function') renderPieChart();
      if (typeof loadHistory === 'function') loadHistory();
    },

    // ── Drawer ──
    drawerOpen: false,
    toggleDrawer() { this.drawerOpen = !this.drawerOpen; },
    closeDrawer() { this.drawerOpen = false; },

    // ── Modal state ──
    modal: null, // 'position' | 'dca' | 'detail' | 'import' | null
    openModal(type, ticker) {
      this.modal = type;
      if (type === 'position' && !ticker) {
        // New position — handled by existing openAddModal()
        openAddModal();
        return;
      }
      if (type === 'position' && ticker) {
        openEditModal(ticker);
        return;
      }
      if (type === 'dca') {
        openDcaModal('BUY');
        return;
      }
      if (type === 'detail' && ticker) {
        openDetailModal(ticker);
        return;
      }
      if (type === 'import') {
        openImportModal();
        return;
      }
    },
    closeModal() { this.modal = null; closeModal(); closeDcaModal(); },

    // ── Toast system ──
    toasts: [],
    _toastId: 0,
    toast(msg, type) {
      // Use existing showToast
      showToast(msg, type || 'success');
    },

    // ── Navigation ──
    navigate(section) {
      this.closeDrawer();
      if (section === 'positions') document.getElementById('positionsSection')?.scrollIntoView({ behavior: 'smooth' });
      if (section === 'history') document.getElementById('dcaSection')?.scrollIntoView({ behavior: 'smooth' });
      if (section === 'watchlist') document.getElementById('watchlistSection')?.scrollIntoView({ behavior: 'smooth' });
    },

    // ── Lifecycle ──
    init() {
      // Keyboard shortcuts
      document.addEventListener('keydown', (e) => {
        if ((e.metaKey || e.ctrlKey) && e.key === 'k') { e.preventDefault(); this.toggleDrawer(); }
        if ((e.metaKey || e.ctrlKey) && e.key === 'n' && !e.shiftKey) { e.preventDefault(); openAddModal(); }
        if ((e.metaKey || e.ctrlKey) && e.key === 'n' && e.shiftKey) { e.preventDefault(); openDcaModal('BUY'); }
        if (e.key === 'Escape') {
          if (this.drawerOpen) { this.drawerOpen = false; return; }
          // Close all modals
          if (typeof closeModal === 'function') closeModal();
          if (typeof closeDcaModal === 'function') closeDcaModal();
          if (typeof closeDetailModal === 'function') closeDetailModal();
          if (typeof closePeriodHistory === 'function') closePeriodHistory();
          if (typeof closeImportModal === 'function') closeImportModal();
          if (typeof closeTgDetail === 'function') closeTgDetail();
          if (typeof closePortfolioDetail === 'function') closePortfolioDetail();
          if (typeof closeTelegramConfigModal === 'function') closeTelegramConfigModal();
          if (typeof closeWatchlistModal === 'function') closeWatchlistModal();
          if (typeof closeWlAlertModal === 'function') closeWlAlertModal();
        }
      });
    }
  }));
});
