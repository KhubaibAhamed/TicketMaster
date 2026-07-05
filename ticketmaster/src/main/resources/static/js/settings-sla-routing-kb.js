/**
 * settings-sla-routing-kb.js — Settings: SLA, Categories/Routing, Canned Responses, KB
 * =============================================================
 * Depends on: core.js
 *
 * Admin-only settings tabs grouped by "how tickets get shaped and routed":
 * SLA thresholds + business-hours calendar, category/sub-category management
 * with per-category escalation contact + custom field definitions, routing
 * rules (incl. auto-assignment pools), the canned-response library, and the
 * knowledge base article library.
 */

// --- SLA Config ---
const SLA_WORK_DAYS = ['MONDAY','TUESDAY','WEDNESDAY','THURSDAY','FRIDAY','SATURDAY','SUNDAY'];
const SLA_WORK_DAY_LABELS = { MONDAY:'Mon', TUESDAY:'Tue', WEDNESDAY:'Wed', THURSDAY:'Thu', FRIDAY:'Fri', SATURDAY:'Sat', SUNDAY:'Sun' };

function renderSlaWorkDayChips(selectedDays) {
    const group = document.getElementById('slaWorkDaysGroup');
    if (!group) return;
    group.innerHTML = SLA_WORK_DAYS.map(day => {
        const checked = selectedDays.includes(day) ? 'checked' : '';
        return `<label style="display:flex; align-items:center; gap:5px; font-size:12px; padding:6px 10px;
                        border:1px solid var(--g-border); border-radius:8px; cursor:pointer;">
                    <input type="checkbox" class="sla-work-day" value="${day}" ${checked}> ${SLA_WORK_DAY_LABELS[day]}
                </label>`;
    }).join('');
}

function getSelectedSlaWorkDays() {
    return Array.from(document.querySelectorAll('.sla-work-day:checked')).map(cb => cb.value);
}

function toggleSlaBusinessHoursFields() {
    const enabled = document.getElementById('slaBusinessHoursEnabled').checked;
    document.getElementById('slaBusinessHoursFields').style.display = enabled ? 'flex' : 'none';
}

async function loadSlaConfig() {
    try {
        const res = await fetchWithAuth('/api/sla-config', { headers: authHeaders() });
        if (!res.ok) throw new Error('Request failed');
        const config = await res.json();

        document.getElementById('slaHighHours').value   = config.highPriorityHours;
        document.getElementById('slaMediumHours').value = config.mediumPriorityHours;
        document.getElementById('slaLowHours').value     = config.lowPriorityHours;

        document.getElementById('slaBusinessHoursEnabled').checked = !!config.businessHoursEnabled;
        document.getElementById('slaWorkStartHour').value = config.workStartHour;
        document.getElementById('slaWorkEndHour').value   = config.workEndHour;
        renderSlaWorkDayChips((config.workDays || '').split(',').map(d => d.trim()).filter(Boolean));
        toggleSlaBusinessHoursFields();
    } catch (e) {
        console.error('Failed to load SLA config:', e);
        showToast('Failed to load SLA settings.', 'error');
    }
}

async function saveSlaConfig() {
    const highHours   = parseInt(document.getElementById('slaHighHours').value, 10);
    const mediumHours = parseInt(document.getElementById('slaMediumHours').value, 10);
    const lowHours    = parseInt(document.getElementById('slaLowHours').value, 10);

    const businessHoursEnabled = document.getElementById('slaBusinessHoursEnabled').checked;
    const workStartHour = parseInt(document.getElementById('slaWorkStartHour').value, 10);
    const workEndHour   = parseInt(document.getElementById('slaWorkEndHour').value, 10);
    const workDays      = getSelectedSlaWorkDays().join(',');

    const msgBox = document.getElementById('slaConfigMsg');

    if (!highHours || !mediumHours || !lowHours || highHours <= 0 || mediumHours <= 0 || lowHours <= 0) {
        msgBox.style.display = 'block';
        msgBox.style.background = '#FCE8E6';
        msgBox.style.color = '#EA4335';
        msgBox.innerText = 'All three thresholds must be positive numbers.';
        return;
    }
    if (businessHoursEnabled && (!workDays || workStartHour >= workEndHour)) {
        msgBox.style.display = 'block';
        msgBox.style.background = '#FCE8E6';
        msgBox.style.color = '#EA4335';
        msgBox.innerText = 'Pick at least one work day and a valid start/end hour range.';
        return;
    }

    try {
        const res = await fetchWithAuth('/api/sla-config', {
            method: 'PUT',
            headers: { ...authHeaders(), 'Content-Type': 'application/json' },
            body: JSON.stringify({
                highPriorityHours: highHours,
                mediumPriorityHours: mediumHours,
                lowPriorityHours: lowHours,
                businessHoursEnabled,
                workStartHour,
                workEndHour,
                workDays
            })
        });

        msgBox.style.display = 'block';
        if (res.ok) {
            msgBox.style.background = '#E6F4EA';
            msgBox.style.color = '#34A853';
            msgBox.innerText = 'SLA thresholds updated.';
            showToast('SLA thresholds saved.', 'success');
        } else {
            const errText = await res.text();
            msgBox.style.background = '#FCE8E6';
            msgBox.style.color = '#EA4335';
            msgBox.innerText = errText || 'Failed to save SLA settings.';
        }
    } catch (e) {
        console.error('Failed to save SLA config:', e);
        msgBox.style.display = 'block';
        msgBox.style.background = '#FCE8E6';
        msgBox.style.color = '#EA4335';
        msgBox.innerText = 'System error. Check console.';
    }
}

// --- Categories & Routing Rules ---
let cachedCategoriesDetailed = [];

async function loadCategoryAndRoutingManagement() {
    await Promise.all([loadCategoryTree(), loadRoutingRulesTable()]);
}

async function loadCategoryTree() {
    const container = document.getElementById('categoryTreeList');
    if (!container) return;
    renderSkeletonBlock(container, 4);
    try {
        const res = await fetchWithAuth('/api/categories/detailed', { headers: authHeaders() });
        if (!res.ok) throw new Error('Request failed');
        const categories = await res.json();
        cachedCategoriesDetailed = categories;

        // Keep the routing rule "Category" dropdown in sync
        const ruleCategorySelect = document.getElementById('newRuleCategory');
        if (ruleCategorySelect) {
            ruleCategorySelect.innerHTML = '<option value="">Any</option>' +
                categories.map(c => `<option value="${c.id}">${escapeHtml(c.name)}</option>`).join('');
        }

        if (!categories.length) {
            container.innerHTML = '<p style="color:var(--g-text-muted); font-size:13px;">No categories yet.</p>';
            return;
        }

        container.innerHTML = categories.map(c => `
            <div style="border:1px solid var(--g-border); border-radius:10px; padding:12px 14px;">
                <div style="display:flex; justify-content:space-between; align-items:center;">
                    <span style="font-weight:600; font-size:13px;">${escapeHtml(c.name)}</span>
                    <button onclick="deleteCategory(${c.id}, '${escapeHtml(c.name)}')"
                            style="border:none; background:transparent; cursor:pointer; color:var(--g-text-muted); display:flex;">
                        <span class="material-symbols-rounded" style="font-size:18px;">delete</span>
                    </button>
                </div>
                <div style="display:flex; flex-wrap:wrap; gap:6px; margin-top:8px;">
                    ${c.subCategories.map(sc => `
                        <span style="display:inline-flex; align-items:center; gap:4px; background:var(--g-background);
                                     border:1px solid var(--g-border); border-radius:12px; padding:3px 6px 3px 10px; font-size:12px;">
                            ${escapeHtml(sc.name)}
                            <span onclick="deleteSubCategory(${sc.id})" style="cursor:pointer; color:var(--g-text-muted); display:flex;">
                                <span class="material-symbols-rounded" style="font-size:14px;">close</span>
                            </span>
                        </span>
                    `).join('')}
                </div>
                <div style="display:flex; gap:6px; margin-top:8px;">
                    <input type="text" id="newSubCat-${c.id}" placeholder="New sub-category"
                           onkeydown="if(event.key==='Enter') addSubCategory(${c.id})"
                           style="flex:1; padding:6px 8px; border-radius:6px; border:1px solid var(--g-border); font-size:12px; font-family:var(--font-ui);">
                    <button class="g-btn" onclick="addSubCategory(${c.id})" style="padding:6px 12px; font-size:12px;">Add</button>
                </div>
                <div style="margin-top:10px; padding-top:10px; border-top:1px dashed var(--g-border); display:flex; flex-direction:column; gap:6px;">
                    <label style="font-size:11px; color:var(--g-text-muted);">Escalation contact (username, blank = admins)</label>
                    <input type="text" id="escContact-${c.id}" value="${escapeHtml(c.escalationContactUsername || '')}"
                           placeholder="e.g. agent_jane"
                           style="padding:6px 8px; border-radius:6px; border:1px solid var(--g-border); font-size:12px; font-family:var(--font-ui);">
                    <label style="display:flex; align-items:center; gap:6px; font-size:12px; cursor:pointer;">
                        <input type="checkbox" id="escBump-${c.id}" ${c.autoBumpPriorityOnBreach ? 'checked' : ''}>
                        Auto-bump priority on breach
                    </label>
                    <button class="g-btn" onclick="saveCategoryEscalation(${c.id})" style="padding:6px 12px; font-size:12px; align-self:flex-start;">
                        Save escalation settings
                    </button>
                </div>
                <div style="margin-top:10px; padding-top:10px; border-top:1px dashed var(--g-border);">
                    <label style="font-size:11px; color:var(--g-text-muted); display:block; margin-bottom:6px;">Custom fields</label>
                    <div id="customFieldsList-${c.id}"></div>
                    <div style="display:flex; gap:4px; margin-top:6px;">
                        <input type="text" id="newFieldName-${c.id}" placeholder="Field name"
                               style="flex:1; padding:6px 8px; border-radius:6px; border:1px solid var(--g-border); font-size:12px;">
                        <select id="newFieldType-${c.id}" style="padding:6px 8px; border-radius:6px; border:1px solid var(--g-border); font-size:12px;">
                            <option value="TEXT">Text</option>
                            <option value="NUMBER">Number</option>
                            <option value="DATE">Date</option>
                            <option value="SELECT">Dropdown</option>
                        </select>
                        <input type="text" id="newFieldOptions-${c.id}" placeholder="Options (if dropdown)"
                               style="flex:1; padding:6px 8px; border-radius:6px; border:1px solid var(--g-border); font-size:12px;">
                        <button class="g-btn" onclick="addCustomField(${c.id})" style="padding:6px 10px; font-size:12px;">Add</button>
                    </div>
                </div>
            </div>
        `).join('');
        categories.forEach(c => loadCustomFieldsForCategory(c.id));
    } catch (e) {
        console.error('Failed to load categories:', e);
        container.innerHTML = '<p style="color:var(--g-red); font-size:13px;">Failed to load categories.</p>';
    }
}

function toggleNewRuleAgentsField() {
    const enabled = document.getElementById('newRuleAutoAssign').checked;
    document.getElementById('newRuleAgentsWrapper').style.display = enabled ? 'block' : 'none';
}

async function saveCategoryEscalation(categoryId) {
    const contact = document.getElementById(`escContact-${categoryId}`).value.trim();
    const autoBump = document.getElementById(`escBump-${categoryId}`).checked;

    try {
        const res = await fetchWithAuth(`/api/categories/${categoryId}`, {
            method: 'PUT',
            headers: { ...authHeaders(), 'Content-Type': 'application/json' },
            body: JSON.stringify({ escalationContactUsername: contact, autoBumpPriorityOnBreach: autoBump })
        });
        if (res.ok) {
            showMgmtMsg('catMgmtMsg', 'Escalation settings saved.', false);
            loadCategoryTree();
        } else {
            const err = await res.text();
            showMgmtMsg('catMgmtMsg', err || 'Failed to save escalation settings.', true);
        }
    } catch (e) {
        console.error(e);
        showMgmtMsg('catMgmtMsg', 'System error. Check console.', true);
    }
}

async function createCategory() {
    const input = document.getElementById('newCategoryName');
    const name = input.value.trim();
    if (!name) { showMgmtMsg('catMgmtMsg', 'Category name cannot be blank.', true); return; }

    try {
        const res = await fetchWithAuth('/api/categories', {
            method: 'POST',
            headers: { ...authHeaders(), 'Content-Type': 'application/json' },
            body: JSON.stringify({ name })
        });
        if (res.ok) {
            input.value = '';
            showMgmtMsg('catMgmtMsg', `Category "${name}" created.`, false);
            loadCategoryTree();
        } else {
            const err = await res.text();
            showMgmtMsg('catMgmtMsg', err || 'Failed to create category.', true);
        }
    } catch (e) {
        console.error(e);
        showMgmtMsg('catMgmtMsg', 'System error. Check console.', true);
    }
}

async function deleteCategory(id, name) {
    if (!confirm(`Delete category "${name}"? This also removes its sub-categories.`)) return;
    try {
        const res = await fetchWithAuth(`/api/categories/${id}`, { method: 'DELETE', headers: authHeaders() });
        if (res.ok) {
            showMgmtMsg('catMgmtMsg', `Category "${name}" deleted.`, false);
            loadCategoryTree();
        } else {
            const err = await res.text();
            showMgmtMsg('catMgmtMsg', err || 'Failed to delete category.', true);
        }
    } catch (e) {
        console.error(e);
        showMgmtMsg('catMgmtMsg', 'System error. Check console.', true);
    }
}

async function addSubCategory(categoryId) {
    const input = document.getElementById(`newSubCat-${categoryId}`);
    const name = input.value.trim();
    if (!name) return;

    try {
        const res = await fetchWithAuth(`/api/categories/${categoryId}/subcategories`, {
            method: 'POST',
            headers: { ...authHeaders(), 'Content-Type': 'application/json' },
            body: JSON.stringify({ name })
        });
        if (res.ok) {
            loadCategoryTree();
        } else {
            const err = await res.text();
            showMgmtMsg('catMgmtMsg', err || 'Failed to add sub-category.', true);
        }
    } catch (e) {
        console.error(e);
        showMgmtMsg('catMgmtMsg', 'System error. Check console.', true);
    }
}

async function deleteSubCategory(id) {
    if (!confirm('Delete this sub-category?')) return;
    try {
        const res = await fetchWithAuth(`/api/categories/subcategories/${id}`, { method: 'DELETE', headers: authHeaders() });
        if (res.ok) {
            loadCategoryTree();
        } else {
            const err = await res.text();
            showMgmtMsg('catMgmtMsg', err || 'Failed to delete sub-category.', true);
        }
    } catch (e) {
        console.error(e);
        showMgmtMsg('catMgmtMsg', 'System error. Check console.', true);
    }
}

// --- Custom fields per category ---
// NOTE (fixed during code-cleanup pass): these three functions are called from the
// category-card template above (customFieldsList-${c.id}, addCustomField(...), etc.)
// but were missing from the original app.js — the buttons existed in the HTML but had
// nothing to call. Restored here so the "Custom fields" section of each category card
// actually works.
async function loadCustomFieldsForCategory(categoryId) {
    const container = document.getElementById(`customFieldsList-${categoryId}`);
    if (!container) return;
    try {
        const res = await fetchWithAuth(`/api/custom-fields/by-category/${categoryId}`, { headers: authHeaders() });
        const fields = await res.json();
        container.innerHTML = fields.length ? fields.map(f => `
            <div style="display:flex; justify-content:space-between; align-items:center; font-size:12px; padding:4px 0;">
                <span>${escapeHtml(f.fieldName)} (${f.fieldType}${f.required ? ', required' : ''})</span>
                <span onclick="deleteCustomField(${f.id}, ${categoryId})" style="cursor:pointer; color:var(--g-text-muted); display:flex;">
                    <span class="material-symbols-rounded" style="font-size:16px;">close</span>
                </span>
            </div>
        `).join('') : '<p style="font-size:12px; color:var(--g-text-muted); margin:0;">No custom fields yet.</p>';
    } catch (e) {
        console.error(e);
    }
}

async function addCustomField(categoryId) {
    const fieldName = document.getElementById(`newFieldName-${categoryId}`).value.trim();
    const fieldType = document.getElementById(`newFieldType-${categoryId}`).value;
    const options = document.getElementById(`newFieldOptions-${categoryId}`).value.trim();
    if (!fieldName) { showToast('Field name required.', 'warning'); return; }

    try {
        const res = await fetchWithAuth('/api/custom-fields', {
            method: 'POST', headers: { ...authHeaders(), 'Content-Type': 'application/json' },
            body: JSON.stringify({ categoryId, fieldName, fieldType, options: options || null, required: false, fieldOrder: 0 })
        });
        if (res.ok) {
            document.getElementById(`newFieldName-${categoryId}`).value = '';
            document.getElementById(`newFieldOptions-${categoryId}`).value = '';
            loadCustomFieldsForCategory(categoryId);
        } else {
            const err = await res.text();
            showToast(err || 'Failed to add field.', 'error');
        }
    } catch (e) {
        console.error(e);
        showToast('System error. Check console.', 'error');
    }
}

async function deleteCustomField(fieldId, categoryId) {
    try {
        await fetchWithAuth(`/api/custom-fields/${fieldId}`, { method: 'DELETE', headers: authHeaders() });
        loadCustomFieldsForCategory(categoryId);
    } catch (e) {
        console.error(e);
        showToast('Failed to delete field.', 'error');
    }
}

// --- Canned Responses ---
let cachedCannedResponses = [];

async function loadCannedResponseManagement() {
    const container = document.getElementById('cannedResponseList');
    if (!container) return;
    renderSkeletonBlock(container, 3);
    try {
        const res = await fetchWithAuth('/api/canned-responses', { headers: authHeaders() });
        if (!res.ok) throw new Error('Request failed');
        const responses = await res.json();
        cachedCannedResponses = responses;

        if (!responses.length) {
            container.innerHTML = '<p style="color:var(--g-text-muted); font-size:13px;">No canned responses yet.</p>';
            return;
        }

        container.innerHTML = responses.map(r => `
            <div style="border:1px solid var(--g-border); border-radius:10px; padding:12px 14px;">
                <div style="display:flex; justify-content:space-between; align-items:center;">
                    <span style="font-weight:600; font-size:13px;">${escapeHtml(r.title)}</span>
                    <button onclick="deleteCannedResponse(${r.id}, '${escapeHtml(r.title)}')"
                            style="border:none; background:transparent; cursor:pointer; color:var(--g-text-muted); display:flex;">
                        <span class="material-symbols-rounded" style="font-size:18px;">delete</span>
                    </button>
                </div>
                <p style="margin:6px 0 0 0; font-size:12px; color:var(--g-text-muted); white-space:pre-wrap; max-height:60px; overflow:hidden;">${escapeHtml(r.body)}</p>
            </div>
        `).join('');
    } catch (e) {
        console.error('Failed to load canned responses:', e);
        container.innerHTML = '<p style="color:var(--g-red); font-size:13px;">Failed to load canned responses.</p>';
    }
}

async function createCannedResponse() {
    const title = document.getElementById('newCannedTitle').value.trim();
    const body = document.getElementById('newCannedBody').value.trim();
    if (!title || !body) { showMgmtMsg('cannedMgmtMsg', 'Title and body are both required.', true); return; }

    try {
        const res = await fetchWithAuth('/api/canned-responses', {
            method: 'POST',
            headers: { ...authHeaders(), 'Content-Type': 'application/json' },
            body: JSON.stringify({ title, body })
        });
        if (res.ok) {
            document.getElementById('newCannedTitle').value = '';
            document.getElementById('newCannedBody').value = '';
            showMgmtMsg('cannedMgmtMsg', 'Canned response created.', false);
            loadCannedResponseManagement();
        } else {
            const err = await res.text();
            showMgmtMsg('cannedMgmtMsg', err || 'Failed to create canned response.', true);
        }
    } catch (e) {
        console.error(e);
        showMgmtMsg('cannedMgmtMsg', 'System error. Check console.', true);
    }
}

async function deleteCannedResponse(id, title) {
    if (!confirm(`Delete canned response "${title}"?`)) return;
    try {
        const res = await fetchWithAuth(`/api/canned-responses/${id}`, { method: 'DELETE', headers: authHeaders() });
        if (res.ok) {
            showMgmtMsg('cannedMgmtMsg', `"${title}" deleted.`, false);
            loadCannedResponseManagement();
        } else {
            const err = await res.text();
            showMgmtMsg('cannedMgmtMsg', err || 'Failed to delete.', true);
        }
    } catch (e) {
        console.error(e);
        showMgmtMsg('cannedMgmtMsg', 'System error. Check console.', true);
    }
}

// --- Knowledge Base ---
async function loadKbManagement() {
    const catSelect = document.getElementById('newKbCategory');
    if (catSelect && catSelect.options.length <= 1 && cachedCategoriesDetailed.length) {
        catSelect.innerHTML = '<option value="">No category</option>' +
            cachedCategoriesDetailed.map(c => `<option value="${c.id}">${escapeHtml(c.name)}</option>`).join('');
    }

    const container = document.getElementById('kbArticleList');
    if (!container) return;
    renderSkeletonBlock(container, 3);
    try {
        const res = await fetchWithAuth('/api/kb/articles', { headers: authHeaders() });
        if (!res.ok) throw new Error('Request failed');
        const articles = await res.json();

        if (!articles.length) {
            container.innerHTML = '<p style="color:var(--g-text-muted); font-size:13px;">No articles yet.</p>';
            return;
        }

        container.innerHTML = articles.map(a => `
            <div style="border:1px solid var(--g-border); border-radius:10px; padding:12px 14px;">
                <div style="display:flex; justify-content:space-between; align-items:center;">
                    <span style="font-weight:600; font-size:13px;">${escapeHtml(a.title)}</span>
                    <button onclick="deleteKbArticle(${a.id}, '${escapeHtml(a.title)}')"
                            style="border:none; background:transparent; cursor:pointer; color:var(--g-text-muted); display:flex;">
                        <span class="material-symbols-rounded" style="font-size:18px;">delete</span>
                    </button>
                </div>
                <div style="font-size:11px; color:var(--g-text-muted); margin-top:2px;">
                    ${a.category ? escapeHtml(a.category.name) : 'Uncategorized'}
                    ${a.sourceTicketId ? ` · from Ticket #${a.sourceTicketId}` : ''}
                </div>
                <p style="margin:6px 0 0 0; font-size:12px; color:var(--g-text-muted); white-space:pre-wrap; max-height:60px; overflow:hidden;">${escapeHtml(a.body)}</p>
            </div>
        `).join('');
    } catch (e) {
        console.error('Failed to load KB articles:', e);
        container.innerHTML = '<p style="color:var(--g-red); font-size:13px;">Failed to load articles.</p>';
    }
}

async function createKbArticle() {
    const title = document.getElementById('newKbTitle').value.trim();
    const body = document.getElementById('newKbBody').value.trim();
    const categoryId = document.getElementById('newKbCategory').value;
    if (!title || !body) { showMgmtMsg('kbMgmtMsg', 'Title and body are both required.', true); return; }

    try {
        const res = await fetchWithAuth('/api/kb/articles', {
            method: 'POST',
            headers: { ...authHeaders(), 'Content-Type': 'application/json' },
            body: JSON.stringify({ title, body, categoryId: categoryId ? parseInt(categoryId, 10) : null })
        });
        if (res.ok) {
            document.getElementById('newKbTitle').value = '';
            document.getElementById('newKbBody').value = '';
            showMgmtMsg('kbMgmtMsg', 'Article published.', false);
            loadKbManagement();
        } else {
            const err = await res.text();
            showMgmtMsg('kbMgmtMsg', err || 'Failed to publish article.', true);
        }
    } catch (e) {
        console.error(e);
        showMgmtMsg('kbMgmtMsg', 'System error. Check console.', true);
    }
}

async function deleteKbArticle(id, title) {
    if (!confirm(`Delete article "${title}"?`)) return;
    try {
        const res = await fetchWithAuth(`/api/kb/articles/${id}`, { method: 'DELETE', headers: authHeaders() });
        if (res.ok) {
            showMgmtMsg('kbMgmtMsg', `"${title}" deleted.`, false);
            loadKbManagement();
        } else {
            const err = await res.text();
            showMgmtMsg('kbMgmtMsg', err || 'Failed to delete.', true);
        }
    } catch (e) {
        console.error(e);
        showMgmtMsg('kbMgmtMsg', 'System error. Check console.', true);
    }
}

async function loadRoutingRulesTable() {
    const tbody = document.getElementById('routingRulesBody');
    if (!tbody) return;
    renderSkeletonRows(tbody, 5, 3, ['20%', '60%', '50%', '70%', '30%']);
    try {
        const res = await fetchWithAuth('/api/routing-rules', { headers: authHeaders() });
        if (!res.ok) throw new Error('Request failed');
        const rules = await res.json();

        if (!rules.length) {
            tbody.innerHTML = '<tr><td colspan="6" style="text-align:center; color:var(--g-text-muted); padding:24px;">No routing rules yet — new tickets fall back to manual assignment.</td></tr>';
            return;
        }

        tbody.innerHTML = rules.map(r => `
            <tr>
                <td>${r.ruleOrder}</td>
                <td>${r.category ? escapeHtml(r.category.name) : '<span style="color:var(--g-text-muted);">Any</span>'}</td>
                <td>${r.priority ? escapeHtml(r.priority) : '<span style="color:var(--g-text-muted);">Any</span>'}</td>
                <td>${escapeHtml(r.targetQueue)}</td>
                <td>${r.autoAssignEnabled
                    ? `<span style="font-size:12px;">${escapeHtml(r.eligibleAgents || '')}</span>`
                    : '<span style="color:var(--g-text-muted); font-size:12px;">Off</span>'}</td>
                <td>
                    <button onclick="deleteRoutingRule(${r.id})"
                            style="border:none; background:transparent; cursor:pointer; color:var(--g-text-muted); display:flex;">
                        <span class="material-symbols-rounded" style="font-size:18px;">delete</span>
                    </button>
                </td>
            </tr>
        `).join('');
    } catch (e) {
        console.error('Failed to load routing rules:', e);
        tbody.innerHTML = '<tr><td colspan="6" style="text-align:center; color:var(--g-red); padding:24px;">Failed to load routing rules.</td></tr>';
    }
}

async function createRoutingRule() {
    const ruleOrder = parseInt(document.getElementById('newRuleOrder').value, 10);
    const categoryId = document.getElementById('newRuleCategory').value;
    const priority = document.getElementById('newRulePriority').value;
    const targetQueue = document.getElementById('newRuleQueue').value.trim();
    const autoAssignEnabled = document.getElementById('newRuleAutoAssign').checked;
    const eligibleAgents = document.getElementById('newRuleAgents').value.trim();

    if (!ruleOrder || ruleOrder < 1) { showMgmtMsg('routingMgmtMsg', 'Order must be a positive number.', true); return; }
    if (!targetQueue) { showMgmtMsg('routingMgmtMsg', 'Target queue cannot be blank.', true); return; }
    if (autoAssignEnabled && !eligibleAgents) { showMgmtMsg('routingMgmtMsg', 'List at least one eligible agent, or turn off auto-assign.', true); return; }

    const payload = {
        ruleOrder,
        targetQueue,
        category: categoryId ? { id: parseInt(categoryId, 10) } : null,
        priority: priority || null,
        autoAssignEnabled,
        eligibleAgents: autoAssignEnabled ? eligibleAgents : null
    };

    try {
        const res = await fetchWithAuth('/api/routing-rules', {
            method: 'POST',
            headers: { ...authHeaders(), 'Content-Type': 'application/json' },
            body: JSON.stringify(payload)
        });
        if (res.ok) {
            document.getElementById('newRuleQueue').value = '';
            document.getElementById('newRuleAgents').value = '';
            document.getElementById('newRuleAutoAssign').checked = false;
            toggleNewRuleAgentsField();
            showMgmtMsg('routingMgmtMsg', 'Routing rule added.', false);
            loadRoutingRulesTable();
        } else {
            const err = await res.text();
            showMgmtMsg('routingMgmtMsg', err || 'Failed to add routing rule.', true);
        }
    } catch (e) {
        console.error(e);
        showMgmtMsg('routingMgmtMsg', 'System error. Check console.', true);
    }
}

async function deleteRoutingRule(id) {
    if (!confirm('Delete this routing rule?')) return;
    try {
        const res = await fetchWithAuth(`/api/routing-rules/${id}`, { method: 'DELETE', headers: authHeaders() });
        if (res.ok) {
            showMgmtMsg('routingMgmtMsg', 'Routing rule deleted.', false);
            loadRoutingRulesTable();
        } else {
            const err = await res.text();
            showMgmtMsg('routingMgmtMsg', err || 'Failed to delete routing rule.', true);
        }
    } catch (e) {
        console.error(e);
        showMgmtMsg('routingMgmtMsg', 'System error. Check console.', true);
    }
}