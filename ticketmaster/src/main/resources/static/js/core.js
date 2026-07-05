/**
 * core.js — Shared utilities & global state
 * =============================================================
 * Load this FIRST (before every other app script).
 *
 * What lives here:
 *   - authHeaders / fetchWithAuth / guardAuth / logout — the auth plumbing every
 *     other file relies on to talk to the API and stay logged in.
 *   - Global mutable state (let ticketIdPendingResolve, currentTickets, etc.) —
 *     centralized here so it's easy to find "what state does this app track?"
 *     in one place instead of scattered across feature files.
 *   - Small, generic, reusable helpers used by 2+ feature files: escapeHtml,
 *     showToast, showMgmtMsg, renderSkeletonRows/Block, formatFileSize,
 *     formatFieldName, formatHistoryTimestamp, switchView.
 *   - Nav/theme chrome: toggleMobileNav, toggleDarkMode, toggleNavCollapse.
 *
 * If you're adding a new helper function used by more than one feature file,
 * it belongs here. If it's only used by one feature, keep it in that feature's
 * own file instead — don't grow this file into a second dumping ground.
 */

// --- 1. Auth & Security ---
function authHeaders() { return { 'Content-Type': 'application/json' }; }

function guardAuth() {
    if (!localStorage.getItem('jwt_username')) window.location.href = '/landing.html';
}

async function fetchWithAuth(url, options = {}) {
    // credentials: 'include' ensures cookies are sent on every request
    options.credentials = 'include';
    let response = await fetch(url, options);

    if (response.status === 401) {
        // Try silent token refresh — server reads cookie, sets new cookie
        const refreshResponse = await fetch('/api/auth/refresh', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            credentials: 'include'
        });
        if (refreshResponse.ok) {
            return await fetch(url, options);
        }
        logout();
    }
    return response;
}

function toggleMobileNav() {
    const nav     = document.querySelector('.md-nav-drawer');
    const overlay = document.getElementById('mobileOverlay');
    const isOpen  = nav.classList.contains('mobile-open');
    nav.classList.toggle('mobile-open', !isOpen);
    overlay.classList.toggle('visible', !isOpen);
}

function toggleDarkMode() {
    const isDark = document.body.getAttribute('data-theme') === 'dark';
    const icon   = document.getElementById('darkModeIcon');

    if (isDark) {
        document.body.removeAttribute('data-theme');
        localStorage.setItem('darkMode', 'false');
        if (icon) icon.innerText = 'dark_mode';
    } else {
        document.body.setAttribute('data-theme', 'dark');
        localStorage.setItem('darkMode', 'true');
        if (icon) icon.innerText = 'light_mode';
    }
}

function toggleNavCollapse() {
    const nav = document.querySelector('.md-nav-drawer');
    nav.classList.toggle('nav-collapsed');
    // Close sub-menu when collapsing
    if (nav.classList.contains('nav-collapsed')) {
        const subMenu = document.getElementById('consoleSubMenu');
        const chevron = document.getElementById('consoleSubChevron');
        if (subMenu) subMenu.style.display = 'none';
        if (chevron) chevron.style.transform = 'rotate(0deg)';
    }
    localStorage.setItem('navCollapsed', nav.classList.contains('nav-collapsed'));
}

async function logout() {
    try {
        // Server reads refresh token from cookie, revokes it, and clears both cookies
        await fetch('/api/auth/logout', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            credentials: 'include'
        });
    } catch (e) {
        console.error('Logout revocation failed:', e);
    }
    localStorage.clear();
    window.location.href = '/landing.html';
}

// --- 2. State & Init ---
let ticketIdPendingResolve = null;
let currentTickets = [];
let currentDetailTicketId = null;
let presenceHeartbeatInterval = null;
let consoleCurrentPage = 0;
let allUsers = [];
let agentWorkload = {};
let selectedTicketIds = new Set();
let consoleSearchQuery = '';
let quickFilterAssignedToMe = false;
let quickFilterRaisedByMe   = false;
let consoleLockedRaisedBy   = null
let consoleSortParam = 'id,desc';
let mainChartInstance = null;
let volumeChartInstance = null;
let activeVolumeDays = 30;
let activeChartTab = 'status';

function switchView(viewId, el) {
    document.querySelectorAll('.app-view').forEach(v => v.classList.remove('active-view'));
    document.querySelectorAll('.nav-item').forEach(n => n.classList.remove('active'));
    document.getElementById(viewId).classList.add('active-view');
    if (el) {
        el.classList.add('active');
        // Extract text safely without the icon text
        const label = el.querySelector('.nav-label');
        document.getElementById('currentViewTitle').innerText = label ? label.innerText.trim() : 'Workspace';
    }
    // Close mobile nav when navigating
    if (window.innerWidth <= 768) {
        const nav = document.querySelector('.md-nav-drawer');
        const overlay = document.getElementById('mobileOverlay');
        nav.classList.remove('mobile-open');
        overlay.classList.remove('visible');
    }
    if (viewId === 'viewConsole') {
        // Open sub-menu when entering console (not for USER role, not when collapsed)
        const role = localStorage.getItem('jwt_role');
        const isCollapsed = document.querySelector('.md-nav-drawer')?.classList.contains('nav-collapsed');
        const subMenu = document.getElementById('consoleSubMenu');
        const chevron = document.getElementById('consoleSubChevron');
        if (subMenu && role !== 'USER' && !isCollapsed) {
            subMenu.style.display = 'block';
            if (chevron) chevron.style.transform = 'rotate(180deg)';
        }
        loadConsolePage(0, '');
    } else {
        // Close sub-menu and reset quick filters when leaving console
        const subMenu = document.getElementById('consoleSubMenu');
        const chevron = document.getElementById('consoleSubChevron');
        if (subMenu) subMenu.style.display = 'none';
        if (chevron) chevron.style.transform = 'rotate(0deg)';
        if (!consoleLockedRaisedBy) {
            quickFilterAssignedToMe = false;
            quickFilterRaisedByMe   = false;
            document.getElementById('subFilterAssigned')?.classList.remove('active');
            document.getElementById('subFilterRaised')?.classList.remove('active');
        }
    }
    if (viewId === 'viewReports') {
        loadAgentPerformance();
        loadVolumeTrend(30);
    }
    if (viewId === 'viewSettings') {
        switchSettingsTab('tabUsers');
    }
}

// --- Loading skeletons (Tier 4) ---
// Renders `rows` skeleton <tr>s into a table body, one shimmer bar per column.
// widths lets callers vary bar length per column so it doesn't look like a uniform grid.
function renderSkeletonRows(tbody, colCount, rows = 5, widths = null) {
    if (!tbody) return;
    const w = widths || Array(colCount).fill('70%');
    let html = '';
    for (let r = 0; r < rows; r++) {
        html += '<tr class="skel-row">';
        for (let c = 0; c < colCount; c++) {
            html += `<td><span class="skel" style="width:${w[c % w.length]}; height:${c === 0 ? '14px' : '11px'};"></span></td>`;
        }
        html += '</tr>';
    }
    tbody.innerHTML = html;
}

// Same idea for non-table containers (card lists, detail panels)
function renderSkeletonBlock(container, lines = 3) {
    if (!container) return;
    let html = '<div style="display:flex; flex-direction:column; gap:10px;">';
    for (let i = 0; i < lines; i++) {
        const width = i === lines - 1 ? '40%' : `${85 - i * 10}%`;
        html += `<span class="skel" style="width:${width}; height:12px;"></span>`;
    }
    html += '</div>';
    container.innerHTML = html;
}

function escapeHtml(str) {
    if (str === null || str === undefined) return '';
    return String(str)
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;')
        .replace(/'/g, '&#39;');
}

function formatFileSize(bytes) {
    if (!bytes) return '0 B';
    if (bytes < 1024) return bytes + ' B';
    if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB';
    return (bytes / (1024 * 1024)).toFixed(1) + ' MB';
}

function showMgmtMsg(elId, text, isError) {
    const box = document.getElementById(elId);
    if (!box) return;
    box.style.display = 'block';
    box.style.background = isError ? '#FCE8E6' : '#E6F4EA';
    box.style.color = isError ? '#EA4335' : '#34A853';
    box.innerText = text;
    setTimeout(() => { box.style.display = 'none'; }, 4000);
}

function formatFieldName(field) {
    const map = {
        status: 'Status', priority: 'Priority', category: 'Category',
        assignedTo: 'Assigned To', title: 'Title', description: 'Description'
    };
    return map[field] || field;
}

function formatHistoryTimestamp(ts) {
    if (!ts) return '';
    const d = new Date(ts);
    return d.toLocaleString('en-IN', { dateStyle: 'medium', timeStyle: 'short' });
}

// ===== J4: KEYBOARD SHORTCUTS =====

// Sequence tracker for two-key shortcuts like G→D, G→C
function showToast(message, type = 'default', duration = 3500) {
    const container = document.getElementById('toastContainer');
    if (!container) return;

    const icons = {
        success: 'check_circle',
        error:   'error',
        warning: 'warning',
        default: 'info'
    };

    const toast = document.createElement('div');
    toast.className = `toast toast-${type}`;
    toast.innerHTML = `
        <span class="material-symbols-rounded" style="font-size:18px; flex-shrink:0;">${icons[type] || 'info'}</span>
        <span style="flex:1;">${escapeHtml(message)}</span>
        <button onclick="this.parentElement.remove()"
                style="border:none; background:transparent; color:rgba(255,255,255,0.7);
                       cursor:pointer; padding:0; font-size:16px; flex-shrink:0;">✕</button>
    `;
    container.appendChild(toast);
    setTimeout(() => toast.remove(), duration);
}