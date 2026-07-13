/**
 * settings-admin-security.js — Settings: Users, Security, MFA, Webhooks
 * =============================================================
 * Depends on: core.js
 *
 * Admin-only settings tabs grouped by "who can log in and how safely":
 * user directory + registration, password-reset request handling, the
 * security event audit log, per-user 2FA (TOTP) setup/enable/disable, and
 * outbound webhook management (Slack/Discord/generic on SLA breach).
 */

// --- 4. Admin Feature ---
async function adminRegisterUser() {
    const u = document.getElementById('regUser').value;
    const e = document.getElementById('regEmail').value; // <-- Grab the email
    const p = document.getElementById('regPass').value;
    const r = document.querySelector('input[name="regRole"]:checked')?.value || 'DEVELOPER';
    const msgBox = document.getElementById('adminMsg');

    try {
        const response = await fetchWithAuth('/api/auth/register', {
            method: 'POST', headers: authHeaders(),
            // Include 'email: e' in the payload sent to Spring Boot
            body: JSON.stringify({ username: u, email: e, password: p, role: r })
        });
        if (response.ok) {
            msgBox.style.display = 'block';
            msgBox.style.backgroundColor = '#E6F4EA';
            msgBox.style.color = 'var(--g-green)';
            msgBox.innerText = `✓ Account created successfully for ${u}`;

            // Clear the form
            document.getElementById('regUser').value = '';
            document.getElementById('regEmail').value = ''; // <-- Clear the email
            document.getElementById('regPass').value = '';

            loadUserDirectory();
        } else {
            // ... rest of your error handling stays exactly the same
            const err = await response.text();
            msgBox.style.display = 'block';
            msgBox.style.backgroundColor = '#FCE8E6';
            msgBox.style.color = '#C5221F';
            msgBox.innerText = err || 'Error: Could not register user.';
        }
    } catch(e) {
        msgBox.style.display = 'block';
        msgBox.style.backgroundColor = '#FCE8E6';
        msgBox.style.color = '#C5221F';
        msgBox.innerText = 'Network Error.';
    }
}

// Page title/subtitle/stats-visibility per settings tab — each destination
// now has its own sidebar entry, so it needs its own honest description
// instead of every one saying "Access Management / Manage users, roles...".
const SETTINGS_TAB_META = {
    tabUsers:    { title: 'User Directory',        subtitle: 'See and manage everyone with access to TrackTicket.', stats: true },
    tabRegister: { title: 'Register User',         subtitle: 'Create a new account and assign a role.', stats: false },
    tabRequests: { title: 'Password Requests',     subtitle: 'Review and action pending password reset requests.', stats: false },
    tabSecurity: { title: 'Security Log',          subtitle: 'Audit trail of authentication and security-relevant events.', stats: false },
    tabMfa:      { title: 'Two-Factor Authentication', subtitle: 'Require a 6-digit authenticator code at sign-in.', stats: false },
    tabSla:      { title: 'SLA Settings',          subtitle: 'Configure response-time windows by priority and business hours.', stats: false },
    tabRouting:  { title: 'Categories & Routing',  subtitle: 'Manage ticket categories and automatic routing rules.', stats: false },
    tabCanned:   { title: 'Canned Responses',      subtitle: "Reusable reply templates agents can insert into any comment box.", stats: false },
    tabKb:       { title: 'Knowledge Base',        subtitle: 'Self-service articles shown to requesters before they file a ticket.', stats: false },
    tabWebhooks: { title: 'Webhooks',              subtitle: 'Outgoing notifications fired on SLA breach.', stats: false }
};

function switchSettingsTab(tabId) {
    ['tabUsers', 'tabRegister', 'tabRequests', 'tabSecurity', 'tabMfa', 'tabSla', 'tabRouting', 'tabCanned', 'tabKb', 'tabWebhooks'].forEach(id => {
        const panel = document.getElementById('settingsPanel-' + id);
        if (!panel) return;
        panel.style.display = id === tabId ? 'block' : 'none';
    });

    const meta = SETTINGS_TAB_META[tabId] || SETTINGS_TAB_META.tabUsers;
    const titleEl    = document.getElementById('settingsPageTitle');
    const subtitleEl = document.getElementById('settingsPageSubtitle');
    const statsRow   = document.getElementById('settingsStatsRow');
    if (titleEl)    titleEl.innerText = meta.title;
    if (subtitleEl) subtitleEl.innerText = meta.subtitle;
    if (statsRow)   statsRow.style.display = meta.stats ? 'flex' : 'none';

    if (tabId === 'tabUsers')    loadUserDirectory();
    if (tabId === 'tabRequests') loadPasswordRequests();
    if (tabId === 'tabSecurity') loadSecurityLog(0);
    if (tabId === 'tabMfa')      loadMfaStatus();
    if (tabId === 'tabSla')      loadSlaConfig();
    if (tabId === 'tabRouting')  loadCategoryAndRoutingManagement();
    if (tabId === 'tabCanned')   loadCannedResponseManagement();
    if (tabId === 'tabKb')       loadKbManagement();
    if (tabId === 'tabWebhooks') loadWebhooks();
}

function updateRoleSelection() {
    const selected   = document.querySelector('input[name="regRole"]:checked')?.value;
    const labelDev   = document.getElementById('roleLabelDev');
    const labelAdmin = document.getElementById('roleLabelAdmin');
    const labelUser  = document.getElementById('roleLabelUser');
    if (labelDev)   labelDev.style.borderColor   = selected === 'DEVELOPER' ? 'var(--g-blue)'   : 'var(--g-border)';
    if (labelAdmin) labelAdmin.style.borderColor = selected === 'ADMIN'     ? 'var(--g-red)'          : 'var(--g-border)';
    if (labelUser)  labelUser.style.borderColor  = selected === 'USER'      ? 'var(--g-green)'          : 'var(--g-border)';
}

async function loadUserDirectory() {
    const container = document.getElementById('userDirectoryList');
    if (!container) return;
    renderSkeletonBlock(container, 4);

    try {
        const res   = await fetchWithAuth('/api/auth/users', { headers: authHeaders() });
        const users = await res.json();

        // Update stats
        const admins = users.filter(u => u.role === 'ADMIN').length;
        const devs   = users.filter(u => u.role === 'DEVELOPER').length;
        document.getElementById('statTotalUsers').innerText = users.length;
        document.getElementById('statAdmins').innerText     = admins;
        document.getElementById('statDevs').innerText       = devs;

        const currentUser = localStorage.getItem('jwt_username');

        container.innerHTML = `
            <div style="background:var(--g-surface); border:1px solid var(--g-border);
                        border-radius:16px; overflow:hidden;">
                <table class="data-table">
                    <thead>
                        <tr>
                            <th>User</th>
                            <th style="width:130px;">Role</th>
                            <th style="width:100px; text-align:right;">Action</th>
                        </tr>
                    </thead>
                    <tbody>
                        ${users.map(u => `
                            <tr>
                                <td>
                                    <div style="display:flex; align-items:center; gap:12px;">
                                        <div style="width:36px; height:36px; border-radius:50%; flex-shrink:0;
                                                    background:${u.role === 'ADMIN' ? '#FCE8E6' : '#E8F0FE'};
                                                    color:${u.role === 'ADMIN' ? 'var(--g-red)' : 'var(--g-blue)'};
                                                    display:flex; align-items:center; justify-content:center;
                                                    font-weight:600; font-size:14px;">
                                            ${u.username[0].toUpperCase()}
                                        </div>
                                        <div>
                                            <div style="font-weight:500; font-size:14px;">${escapeHtml(u.username)}</div>
                                            <div style="font-size:12px; color:var(--g-text-muted);">ID #${u.id}</div>
                                        </div>
                                    </div>
                                </td>
                                <td>
                                    <span style="display:inline-flex; align-items:center; gap:4px; font-size:12px;
                                                 font-weight:600; padding:4px 10px; border-radius:100px;
                                                 background:${u.role === 'ADMIN' ? '#FCE8E6' : '#E8F0FE'};
                                                 color:${u.role === 'ADMIN' ? 'var(--g-red)' : 'var(--g-blue)'};">
                                        ${u.role}
                                    </span>
                                </td>
                                <td style="text-align:right;">
                                    ${u.username !== currentUser ? `
                                        <button onclick="deleteUserFromDirectory(${u.id}, '${escapeHtml(u.username)}')"
                                                style="font-size:12px; color:var(--g-red); border:1px solid var(--g-red);
                                                       background:transparent; border-radius:100px; padding:4px 12px;
                                                       cursor:pointer;">
                                            Remove
                                        </button>` : `
                                        <span style="font-size:12px; color:var(--g-text-muted);">You</span>`
        }
                                </td>
                            </tr>
                        `).join('')}
                    </tbody>
                </table>
            </div>`;
    } catch(e) {
        container.innerHTML = '<p style="color:var(--g-red); font-size:13px;">Failed to load users.</p>';
    }
}

async function deleteUserFromDirectory(userId, username) {
    if (!confirm(`Remove user "${username}"? This cannot be undone.`)) return;

    try {
        const response = await fetchWithAuth(`/api/auth/users/${userId}`, {
            method: 'DELETE',
            headers: authHeaders()
        });

        if (response.ok) {
            showToast(`User "${username}" removed.`, 'success');
            loadUserDirectory(); // Refresh the list
        } else if (response.status === 409) {
            const errorMsg = await response.text();
            showToast(errorMsg, 'error');
        } else {
            console.error("Delete failed with status:", response.status);
            showToast('Failed to delete user.', 'error');
        }
    } catch (error) {
        console.error("Network or script error during delete:", error);
        showToast('System error. Check console.', 'error');
    }
}

async function loadPasswordRequests() {
    const container = document.getElementById('passwordRequestsList');
    if (!container) return;
    renderSkeletonBlock(container, 3);

    try {
        const res      = await fetchWithAuth('/api/auth/password-requests', { headers: authHeaders() });
        const requests = await res.json();

        // Update badge
        const badge = document.getElementById('statRequests');
        if (badge) badge.innerText = requests.length;

        if (!requests.length) {
            container.innerHTML = `
                <div style="text-align:center; padding:48px; background:var(--g-surface);
                            border:1px solid var(--g-border); border-radius:16px;">
                    <span class="material-symbols-rounded" style="font-size:48px; color:var(--g-text-muted);">lock_open</span>
                    <p style="color:var(--g-text-muted); margin:12px 0 0 0; font-size:14px;">
                        No pending password reset requests.
                    </p>
                </div>`;
            return;
        }

        container.innerHTML = `
            <div style="background:var(--g-surface); border:1px solid var(--g-border);
                        border-radius:16px; overflow:hidden;">
                <table class="data-table">
                    <thead>
                        <tr>
                            <th>User</th>
                            <th style="width:180px;">Requested At</th>
                            <th style="width:280px; text-align:right;">Action</th>
                        </tr>
                    </thead>
                    <tbody>
                        ${requests.map(r => `
                            <tr>
                                <td>
                                    <div style="display:flex; align-items:center; gap:12px;">
                                        <div style="width:36px; height:36px; border-radius:50%; background:#FEF7E0;
                                                    color:#B06000; display:flex; align-items:center;
                                                    justify-content:center; font-weight:600; font-size:14px;">
                                            ${r.username[0].toUpperCase()}
                                        </div>
                                        <div>
                                            <div style="font-weight:500;">${escapeHtml(r.username)}</div>
                                            <div style="font-size:12px; color:var(--g-yellow); font-weight:600;">
                                                🔐 Requesting password reset
                                            </div>
                                        </div>
                                    </div>
                                </td>
                                <td style="font-size:13px; color:var(--g-text-muted);">
                                    ${new Date(r.requestedAt).toLocaleString('en-IN', {dateStyle:'medium', timeStyle:'short'})}
                                </td>
                                <td style="text-align:right;">
                                    <div style="display:flex; gap:8px; justify-content:flex-end; align-items:center;">
                                        <input type="password" id="newPass-${r.id}"
                                               placeholder="New password"
                                               style="font-size:12px; padding:6px 10px; border:1px solid var(--g-border);
                                                      border-radius:6px; font-family:var(--font-ui); outline:none;
                                                      width:140px;">
                                        <button onclick="resetUserPassword(${r.id})"
                                                class="g-btn g-btn-primary"
                                                style="padding:0 12px; height:30px; font-size:12px;">
                                            Reset
                                        </button>
                                        <button onclick="dismissPasswordRequest(${r.id})"
                                                style="font-size:12px; color:var(--g-text-muted); border:1px solid var(--g-border);
                                                       background:transparent; border-radius:100px; padding:4px 12px; cursor:pointer;">
                                            Dismiss
                                        </button>
                                    </div>
                                </td>
                            </tr>
                        `).join('')}
                    </tbody>
                </table>
            </div>`;
    } catch(e) {
        container.innerHTML = '<p style="color:var(--g-red); font-size:13px;">Failed to load requests.</p>';
    }
}

// --- Security Event Log ---
let secLogCurrentPage = 0;
let secLogTotalPages  = 0;

const SEC_EVENT_LABELS = {
    LOGIN_SUCCESS:   { text: 'Login success',   color: 'var(--g-green)', bg: '#E6F4EA' },
    LOGIN_FAILURE:   { text: 'Login failure',   color: 'var(--g-red)', bg: '#FCE8E6' },
    LOGIN_BLOCKED:   { text: 'Login blocked',   color: 'var(--g-red)', bg: '#FCE8E6' },
    LOGOUT:          { text: 'Logout',          color: 'var(--g-text-muted)', bg: '#F1F3F4' },
    TOKEN_REFRESHED: { text: 'Token refreshed', color: 'var(--g-blue-dark)', bg: '#E8F0FE' }
};

function clearSecurityLogFilters() {
    document.getElementById('secLogEventType').value = '';
    document.getElementById('secLogUsername').value  = '';
    document.getElementById('secLogFrom').value       = '';
    document.getElementById('secLogTo').value         = '';
    loadSecurityLog(0);
}

async function loadSecurityLog(page) {
    const tbody = document.getElementById('secLogBody');
    if (!tbody) return;
    if (page < 0) return;

    secLogCurrentPage = page;
    renderSkeletonRows(tbody, 5, 6, ['40%', '35%', '55%', '65%', '50%']);

    const eventType = document.getElementById('secLogEventType').value;
    const username  = document.getElementById('secLogUsername').value.trim();
    const from      = document.getElementById('secLogFrom').value;
    const to        = document.getElementById('secLogTo').value;

    const params = new URLSearchParams({ page: page, size: 25 });
    if (eventType) params.set('eventType', eventType);
    if (username)  params.set('username', username);
    if (from)      params.set('from', from);
    if (to)        params.set('to', to);

    try {
        const res = await fetchWithAuth(`/api/security-events?${params.toString()}`, { headers: authHeaders() });
        if (!res.ok) throw new Error('Request failed');
        const data = await res.json();

        secLogTotalPages = data.totalPages || 0;

        if (!data.content || !data.content.length) {
            tbody.innerHTML = '<tr><td colspan="5" style="text-align:center; color:var(--g-text-muted); padding:32px;">No matching events.</td></tr>';
        } else {
            tbody.innerHTML = data.content.map(ev => {
                const meta = SEC_EVENT_LABELS[ev.eventType] || { text: ev.eventType, color: 'var(--g-text-muted)', bg: '#F1F3F4' };
                return `
                    <tr>
                        <td><span style="display:inline-block; padding:3px 10px; border-radius:12px; font-size:12px; font-weight:500; color:${meta.color}; background:${meta.bg};">${meta.text}</span></td>
                        <td>${escapeHtml(ev.username || '—')}</td>
                        <td style="font-family:monospace; font-size:12px;">${escapeHtml(ev.ipAddress || '—')}</td>
                        <td style="font-size:13px; color:var(--g-text-muted);">${escapeHtml(ev.detail || '—')}</td>
                        <td style="font-size:13px; color:var(--g-text-muted);">${formatHistoryTimestamp(ev.occurredAt)}</td>
                    </tr>`;
            }).join('');
        }

        const pageInfo = document.getElementById('secLogPageInfo');
        if (pageInfo) {
            const total = data.totalElements ?? 0;
            pageInfo.innerText = total ? `Page ${page + 1} of ${secLogTotalPages} — ${total} event(s)` : '';
        }
        document.getElementById('secLogPrevBtn').disabled = page <= 0;
        document.getElementById('secLogNextBtn').disabled = page >= secLogTotalPages - 1;
    } catch (e) {
        console.error('Failed to load security log:', e);
        tbody.innerHTML = '<tr><td colspan="5" style="text-align:center; color:var(--g-red); padding:32px;">Failed to load security events.</td></tr>';
    }
}

async function resetUserPassword(requestId) {
    const input = document.getElementById('newPass-' + requestId);
    const newPassword = input?.value?.trim();

    if (!newPassword || newPassword.length < 8) {
        showToast('Password must be at least 8 characters.', 'warning');
        return;
    }
    try {
        const res = await fetchWithAuth(`/api/auth/password-requests/${requestId}/reset`, {
            method: 'POST', headers: authHeaders(),
            body: JSON.stringify({ newPassword })
        });
        if (res.ok) loadPasswordRequests();
        else showToast('Failed to reset password.', 'error');
    } catch(e) { console.error(e); }
}

async function dismissPasswordRequest(requestId) {
    try {
        await fetchWithAuth(`/api/auth/password-requests/${requestId}/dismiss`, {
            method: 'PUT', headers: authHeaders()
        });
        loadPasswordRequests();
    } catch(e) { console.error(e); }
}

// --- MFA / 2FA (Phase E) ---
async function loadMfaStatus() {
    const view = document.getElementById('mfaStatusView');
    if (!view) return;
    try {
        const res = await fetchWithAuth('/api/auth/mfa/status', { headers: authHeaders() });
        const data = await res.json();
        view.innerHTML = data.mfaEnabled
            ? `<p style="font-size:13px; color:var(--g-text-muted); margin-bottom:12px;">2FA is currently <strong>enabled</strong> on your account.</p>
               <input type="text" id="mfaDisableCode" placeholder="Enter code to disable" maxlength="6"
                      style="padding:8px 10px; border-radius:8px; border:1px solid var(--g-border); font-size:13px; margin-right:8px;">
               <button class="g-btn" onclick="disableMfa()" style="font-size:13px;">Disable 2FA</button>`
            : `<button class="g-btn g-btn-primary" onclick="startMfaSetup()" style="font-size:13px;">Enable 2FA</button>
               <div id="mfaSetupPanel" style="display:none; margin-top:16px;"></div>`;
    } catch (e) {
        console.error(e);
        view.innerHTML = '<p style="color:var(--g-red); font-size:13px;">Failed to load 2FA status.</p>';
    }
}

async function startMfaSetup() {
    try {
        const res = await fetchWithAuth('/api/auth/mfa/setup', { method: 'POST', headers: authHeaders() });
        const data = await res.json();
        const panel = document.getElementById('mfaSetupPanel');
        panel.innerHTML = `
            <p style="font-size:13px; color:var(--g-text-muted);">Scan this with your authenticator app (Google Authenticator, Authy, etc.):</p>
            <div id="mfaQrCode" style="margin:12px 0;"></div>
            <p style="font-size:12px; color:var(--g-text-muted);">Or enter this secret manually: <code>${data.secret}</code></p>
            <input type="text" id="mfaEnableCode" placeholder="Enter the 6-digit code" maxlength="6"
                   style="padding:8px 10px; border-radius:8px; border:1px solid var(--g-border); font-size:13px; margin-top:8px; margin-right:8px;">
            <button class="g-btn g-btn-primary" onclick="confirmMfaEnable('${data.secret}')" style="font-size:13px;">Confirm</button>
        `;
        panel.style.display = 'block';
        new QRCode(document.getElementById('mfaQrCode'), { text: data.otpAuthUrl, width: 180, height: 180 });
    } catch (e) {
        console.error(e);
        showToast('Failed to start 2FA setup.', 'error');
    }
}

async function confirmMfaEnable(secret) {
    const code = document.getElementById('mfaEnableCode').value.trim();
    try {
        const res = await fetchWithAuth('/api/auth/mfa/enable', {
            method: 'POST', headers: { ...authHeaders(), 'Content-Type': 'application/json' },
            body: JSON.stringify({ secret, code })
        });
        if (res.ok) {
            showToast('2FA enabled.', 'success');
            loadMfaStatus();
        } else {
            const err = await res.text();
            showToast(err || 'Invalid code.', 'error');
        }
    } catch (e) {
        console.error(e);
        showToast('System error. Check console.', 'error');
    }
}

async function disableMfa() {
    const code = document.getElementById('mfaDisableCode').value.trim();
    try {
        const res = await fetchWithAuth('/api/auth/mfa/disable', {
            method: 'POST', headers: { ...authHeaders(), 'Content-Type': 'application/json' },
            body: JSON.stringify({ code })
        });
        if (res.ok) {
            showToast('2FA disabled.', 'success');
            loadMfaStatus();
        } else {
            const err = await res.text();
            showToast(err || 'Invalid code.', 'error');
        }
    } catch (e) {
        console.error(e);
        showToast('System error. Check console.', 'error');
    }
}

async function loadWebhooks() {
    const container = document.getElementById('webhookList');
    if (!container) return;
    try {
        const res = await fetchWithAuth('/api/webhooks', { headers: authHeaders() });
        const hooks = await res.json();
        if (!hooks.length) {
            container.innerHTML = '<p style="color:var(--g-text-muted); font-size:13px;">No webhooks configured.</p>';
            return;
        }
        container.innerHTML = hooks.map(h => `
            <div style="border:1px solid var(--g-border); border-radius:10px; padding:12px 14px; display:flex; justify-content:space-between; align-items:center;">
                <div>
                    <span style="font-weight:600; font-size:13px;">${h.platform}</span>
                    <span style="font-size:12px; color:var(--g-text-muted); margin-left:8px;">${escapeHtml(h.url)}</span>
                    <span style="font-size:11px; margin-left:8px; color:${h.enabled ? 'var(--g-green)' : 'var(--g-text-muted)'};">${h.enabled ? 'Enabled' : 'Disabled'}</span>
                </div>
                <div style="display:flex; gap:6px;">
                    <button class="g-btn" style="font-size:11px; padding:4px 10px;" onclick="testWebhook(${h.id})">Test</button>
                    <button class="g-btn" style="font-size:11px; padding:4px 10px;" onclick="toggleWebhook(${h.id})">${h.enabled ? 'Disable' : 'Enable'}</button>
                    <button onclick="deleteWebhook(${h.id})" style="border:none; background:transparent; cursor:pointer; color:var(--g-text-muted); display:flex;">
                        <span class="material-symbols-rounded" style="font-size:18px;">delete</span>
                    </button>
                </div>
            </div>
        `).join('');
    } catch (e) {
        console.error(e);
        container.innerHTML = '<p style="color:var(--g-red); font-size:13px;">Failed to load webhooks.</p>';
    }
}

async function createWebhook() {
    const url = document.getElementById('newWebhookUrl').value.trim();
    const platform = document.getElementById('newWebhookPlatform').value;
    if (!url) { showMgmtMsg('webhookMgmtMsg', 'URL cannot be blank.', true); return; }

    try {
        const res = await fetchWithAuth('/api/webhooks', {
            method: 'POST', headers: { ...authHeaders(), 'Content-Type': 'application/json' },
            body: JSON.stringify({ url, platform })
        });
        if (res.ok) {
            document.getElementById('newWebhookUrl').value = '';
            showMgmtMsg('webhookMgmtMsg', 'Webhook added.', false);
            loadWebhooks();
        } else {
            const err = await res.text();
            showMgmtMsg('webhookMgmtMsg', err || 'Failed to add webhook.', true);
        }
    } catch (e) {
        console.error(e);
        showMgmtMsg('webhookMgmtMsg', 'System error. Check console.', true);
    }
}

async function testWebhook(id) {
    try {
        const res = await fetchWithAuth(`/api/webhooks/${id}/test`, { method: 'POST', headers: authHeaders() });
        showToast(res.ok ? 'Test message sent.' : 'Test failed.', res.ok ? 'success' : 'error');
    } catch (e) {
        console.error(e);
        showToast('System error. Check console.', 'error');
    }
}

async function toggleWebhook(id) {
    try {
        await fetchWithAuth(`/api/webhooks/${id}/toggle`, { method: 'PUT', headers: authHeaders() });
        loadWebhooks();
    } catch (e) {
        console.error(e);
        showToast('Failed to toggle webhook.', 'error');
    }
}

async function deleteWebhook(id) {
    if (!confirm('Delete this webhook?')) return;
    try {
        await fetchWithAuth(`/api/webhooks/${id}`, { method: 'DELETE', headers: authHeaders() });
        loadWebhooks();
    } catch (e) {
        console.error(e);
        showToast('Failed to delete webhook.', 'error');
    }
}