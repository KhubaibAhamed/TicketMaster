/**
 * shortcuts-init.js — Keyboard shortcuts & app bootstrap
 * =============================================================
 * ⚠️ LOAD THIS FILE LAST, after every other app script.
 *
 * Why it must be last: this file registers event listeners that reference
 * named functions defined in other files (e.g. stopPresenceTracking from
 * ticket-detail.js, scheduleRelatedArticlesSearch from ticket-create.js).
 * Those functions must already exist by the time this file runs, or the
 * listener registration throws "X is not defined".
 *
 * Contains: the keyboard-shortcut handler + help overlay, the DOMContentLoaded
 * bootstrap (role-based nav setup, initial data loads, polling intervals),
 * and the small handful of addEventListener wiring calls that don't belong
 * inside any single feature file.
 */

document.addEventListener('DOMContentLoaded', () => {
    guardAuth();

    // Setup Avatar
    const user = localStorage.getItem('jwt_username');
    if (user) document.getElementById('userAvatar').innerText = user.charAt(0).toUpperCase();

    // 4. Role-Based Access Control (Admin Reveal)
    const role = localStorage.getItem('jwt_role');
    if (role === 'ADMIN') {
        const reportsNav    = document.getElementById('reportsNavItem');
        const accessMgmtNav = document.getElementById('accessMgmtNavItem');
        if (reportsNav)    reportsNav.style.display    = 'flex';
        if (accessMgmtNav) accessMgmtNav.style.display = 'flex';
    }
    if (role === 'DEVELOPER') {
        const widget = document.getElementById('devPersonalWidget');
        if (widget) widget.style.display = 'block';
    }
    if (role === 'USER') {
        // Show only My Requests in nav
        const myReqNav = document.getElementById('myRequestsNavItem');
        if (myReqNav) myReqNav.style.display = 'flex';

        // Hide all nav items except Raise New Incident and My Requests
        const hideLabels = ['Dashboard Overview', 'Operations Console', 'Reports', 'Access Management'];
        document.querySelectorAll('.nav-item').forEach(item => {
            const label = item.querySelector('.nav-label');
            if (label && hideLabels.includes(label.innerText.trim())) {
                item.style.display = 'none';
            }
        });

        // Also hide the console sub-menu group entirely for USER
        const consoleGroup = document.getElementById('consoleNavGroup');
        if (consoleGroup) consoleGroup.style.display = 'none';

        // Boot into My Requests (console locked to current user's tickets)
        setMyRequestsMode();
        setTimeout(() => {
            switchView('viewConsole', document.getElementById('myRequestsNavItem'));
        }, 50); // Small delay ensures DOM is ready
    }

    // Live Clock
    setInterval(() => {
        const d = new Date();
        document.getElementById('liveClock').innerText = d.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
    }, 1000);

    // Apply saved nav collapse state
    if (localStorage.getItem('navCollapsed') === 'true') {
        document.querySelector('.md-nav-drawer')?.classList.add('nav-collapsed');
    }

    // Apply saved dark mode preference
    if (localStorage.getItem('darkMode') === 'true') {
        document.body.setAttribute('data-theme', 'dark');
        const icon = document.getElementById('darkModeIcon');
        if (icon) icon.innerText = 'light_mode';
    }

    refreshDataPipeline();
    loadCategories();
    loadUsers();
    loadAgentWorkload();
    refreshNotifications();
    if (localStorage.getItem('jwt_role') === 'ADMIN') {
        fetchWithAuth('/api/auth/password-requests', { headers: authHeaders() })
            .then(r => r.json())
            .then(data => {
                const badge = document.getElementById('statRequests');
                if (badge) badge.innerText = data.length;
            }).catch(() => {});
    }
    // Poll every 60 seconds — matches the SLA breach scheduler cadence so new breach
    // notifications appear in the bell within a minute of being written to the DB
    setInterval(refreshNotifications, 60000);
    // Auto-refresh the operations console every 2 minutes so concurrent admins
    // see each other's changes without manually reloading
    setInterval(() => {
        const consoleView = document.getElementById('viewConsole');
        if (consoleView && consoleView.classList.contains('active-view')) {
            loadConsolePage(consoleCurrentPage, consoleSearchQuery);
        }
    }, 120000);
});

let lastKey = null;
let lastKeyTime = 0;

document.addEventListener('keydown', (e) => {
    // Never fire shortcuts when user is typing in an input, textarea, or select
    const tag = document.activeElement.tagName;
    if (['INPUT', 'TEXTAREA', 'SELECT'].includes(tag)) return;

    // Never fire if a modal is open
    const modals = ['createModal', 'resolveModal', 'ticketDetailModal'];
    if (modals.some(id => {
        const el = document.getElementById(id);
        return el && el.open;
    })) {
        // Esc still works to close modals
        if (e.key === 'Escape') {
            modals.forEach(id => {
                const el = document.getElementById(id);
                if (el && el.open) el.close();
            });
        }
        return;
    }

    const now = Date.now();
    const key = e.key.toLowerCase();

    // Two-key sequence: G → D or G → C (must be within 1 second)
    if (lastKey === 'g' && (now - lastKeyTime) < 1000) {
        if (key === 'd') {
            e.preventDefault();
            switchView('viewDashboard', document.querySelectorAll('.nav-item')[1]);
            lastKey = null;
            return;
        }
        if (key === 'c') {
            e.preventDefault();
            switchView('viewConsole', document.querySelectorAll('.nav-item')[2]);
            lastKey = null;
            return;
        }
    }

    // Single-key shortcuts
    switch (key) {
        case 'n':
            e.preventDefault();
            document.getElementById('createModal')?.showModal();
            break;

        case 'escape':
            modals.forEach(id => {
                const el = document.getElementById(id);
                if (el && el.open) el.close();
            });
            // Also close notification panel and shortcut help
            document.getElementById('notifPanel').style.display = 'none';
            notifPanelOpen = false;
            closeShortcutHelp();
            break;

        case '?':
            e.preventDefault();
            toggleShortcutHelp();
            break;

        case 'g':
            lastKey = 'g';
            lastKeyTime = now;
            return; // Don't reset lastKey below
    }

    lastKey = null;
});
document.getElementById('ticketDetailModal')?.addEventListener('close', stopPresenceTracking);
document.getElementById('title')?.addEventListener('input', scheduleRelatedArticlesSearch);
document.getElementById('description')?.addEventListener('input', scheduleRelatedArticlesSearch);

// ===== Shortcut help overlay =====
function toggleShortcutHelp() {
    const existing = document.getElementById('shortcutHelpOverlay');
    if (existing) { existing.remove(); return; }

    const overlay = document.createElement('div');
    overlay.id = 'shortcutHelpOverlay';
    overlay.onclick = closeShortcutHelp;
    overlay.style.cssText = `
        position: fixed; inset: 0; background: rgba(0,0,0,0.5);
        display: flex; align-items: center; justify-content: center;
        z-index: 9999; backdrop-filter: blur(2px);
    `;

    overlay.innerHTML = `
        <div onclick="event.stopPropagation()" style="
            background: var(--g-surface); border-radius: 16px; padding: 32px;
            min-width: 360px; box-shadow: 0 24px 48px rgba(0,0,0,0.2);
            color: var(--g-text-main); font-family: var(--font-ui);">
            <div style="display:flex; justify-content:space-between; align-items:center; margin-bottom:24px;">
                <h3 style="margin:0; font-family:var(--font-display); font-size:18px;">Keyboard Shortcuts</h3>
                <button onclick="closeShortcutHelp()"
                        style="border:none; background:transparent; cursor:pointer;
                               color:var(--g-text-muted); font-size:20px;">✕</button>
            </div>
            <table style="width:100%; border-collapse:collapse; font-size:14px;">
                <tr style="border-bottom:1px solid var(--g-border);">
                    <td style="padding:10px 0; color:var(--g-text-muted);">Raise new incident</td>
                    <td style="padding:10px 0; text-align:right;">
                        <kbd style="background:var(--g-background); border:1px solid var(--g-border);
                                    border-radius:4px; padding:2px 8px; font-size:12px;">N</kbd>
                    </td>
                </tr>
                <tr style="border-bottom:1px solid var(--g-border);">
                    <td style="padding:10px 0; color:var(--g-text-muted);">Go to Dashboard</td>
                    <td style="padding:10px 0; text-align:right;">
                        <kbd style="background:var(--g-background); border:1px solid var(--g-border);
                                    border-radius:4px; padding:2px 8px; font-size:12px;">G</kbd>
                        then
                        <kbd style="background:var(--g-background); border:1px solid var(--g-border);
                                    border-radius:4px; padding:2px 8px; font-size:12px;">D</kbd>
                    </td>
                </tr>
                <tr style="border-bottom:1px solid var(--g-border);">
                    <td style="padding:10px 0; color:var(--g-text-muted);">Go to Console</td>
                    <td style="padding:10px 0; text-align:right;">
                        <kbd style="background:var(--g-background); border:1px solid var(--g-border);
                                    border-radius:4px; padding:2px 8px; font-size:12px;">G</kbd>
                        then
                        <kbd style="background:var(--g-background); border:1px solid var(--g-border);
                                    border-radius:4px; padding:2px 8px; font-size:12px;">C</kbd>
                    </td>
                </tr>
                <tr style="border-bottom:1px solid var(--g-border);">
                    <td style="padding:10px 0; color:var(--g-text-muted);">Close modal / panel</td>
                    <td style="padding:10px 0; text-align:right;">
                        <kbd style="background:var(--g-background); border:1px solid var(--g-border);
                                    border-radius:4px; padding:2px 8px; font-size:12px;">Esc</kbd>
                    </td>
                </tr>
                <tr>
                    <td style="padding:10px 0; color:var(--g-text-muted);">Show this help</td>
                    <td style="padding:10px 0; text-align:right;">
                        <kbd style="background:var(--g-background); border:1px solid var(--g-border);
                                    border-radius:4px; padding:2px 8px; font-size:12px;">?</kbd>
                    </td>
                </tr>
            </table>
        </div>
    `;

    document.body.appendChild(overlay);
}

function closeShortcutHelp() {
    document.getElementById('shortcutHelpOverlay')?.remove();
}

// ===== Toast Notifications =====