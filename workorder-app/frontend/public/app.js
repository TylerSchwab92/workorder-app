// Client-side script. Compiled to public/app.js and loaded directly by
// index.html -- no bundler, no framework. Talks straight to the Java
// backend's REST API (CORS is already turned on there).
const STATUSES = ['OPEN', 'IN_PROGRESS', 'ON_HOLD', 'COMPLETED', 'CANCELLED'];
const TERMINAL_STATUSES = new Set(['COMPLETED', 'CANCELLED']);
const PAGE_SIZE = 5;
const API_BASE = window.__API_BASE_URL__;
// Holds whatever the last fetch returned (already filtered by status on
// the server side), so paging through it doesn't need another request.
let allItems = [];
let currentPage = 1;
function qs(selector) {
    const el = document.querySelector(selector);
    if (!el) {
        throw new Error(`Element not found: ${selector}`);
    }
    return el;
}
function showError(message) {
    const banner = qs('#error-banner');
    banner.textContent = message;
    banner.hidden = false;
}
function clearError() {
    const banner = qs('#error-banner');
    banner.hidden = true;
    banner.textContent = '';
}
async function apiRequest(path, init) {
    const res = await fetch(`${API_BASE}${path}`, {
        headers: { 'Content-Type': 'application/json' },
        ...init,
    });
    const body = await res.json().catch(() => ({}));
    if (!res.ok) {
        const message = typeof body?.error === 'string' ? body.error : `Request failed (${res.status})`;
        throw new Error(message);
    }
    return body;
}
function formatTimestamp(iso) {
    const date = new Date(iso);
    return Number.isNaN(date.getTime()) ? iso : date.toLocaleString();
}
function renderRow(wo) {
    const tr = document.createElement('tr');
    const idCell = document.createElement('td');
    idCell.textContent = String(wo.id);
    tr.appendChild(idCell);
    const titleCell = document.createElement('td');
    titleCell.textContent = wo.title;
    if (wo.description) {
        const desc = document.createElement('div');
        desc.className = 'description';
        desc.textContent = wo.description;
        titleCell.appendChild(desc);
    }
    tr.appendChild(titleCell);
    const assignedCell = document.createElement('td');
    const assignedInput = document.createElement('input');
    assignedInput.type = 'text';
    assignedInput.value = wo.assignedTo ?? '';
    assignedInput.placeholder = 'Unassigned';
    assignedInput.maxLength = 100;
    assignedInput.className = 'assigned-to-input';
    assignedInput.dataset.originalValue = wo.assignedTo ?? '';
    assignedInput.addEventListener('keydown', (e) => {
        if (e.key === 'Enter') {
            assignedInput.blur();
        }
    });
    assignedInput.addEventListener('blur', () => {
        const newValue = assignedInput.value.trim();
        const originalValue = assignedInput.dataset.originalValue ?? '';
        if (newValue !== originalValue) {
            void onAssignedToChange(wo.id, newValue, assignedInput);
        }
    });
    assignedCell.appendChild(assignedInput);
    tr.appendChild(assignedCell);
    const statusCell = document.createElement('td');
    const select = document.createElement('select');
    select.setAttribute('aria-label', `Status for work order ${wo.id}`);
    for (const status of STATUSES) {
        const option = document.createElement('option');
        option.value = status;
        option.textContent = status.replace('_', ' ');
        option.selected = status === wo.status;
        select.appendChild(option);
    }
    select.disabled = TERMINAL_STATUSES.has(wo.status);
    select.addEventListener('change', () => onStatusChange(wo.id, select.value, select));
    statusCell.appendChild(select);
    tr.appendChild(statusCell);
    const updatedCell = document.createElement('td');
    updatedCell.textContent = formatTimestamp(wo.updatedAt);
    tr.appendChild(updatedCell);
    return tr;
}
async function loadWorkOrders() {
    clearError();
    const statusFilter = qs('#status-filter').value;
    const query = statusFilter ? `?status=${statusFilter}` : '';
    try {
        const data = await apiRequest(`/api/workorders${query}`);
        allItems = data.items;
        currentPage = 1;
        renderPage();
    }
    catch (err) {
        showError(err instanceof Error ? err.message : 'Failed to load work orders.');
    }
}
// Renders whichever page of allItems is currently selected, without
// making another request -- used for Previous/Next.
function renderPage() {
    const tbody = qs('#workorders-body');
    tbody.innerHTML = '';
    if (allItems.length === 0) {
        const tr = document.createElement('tr');
        const td = document.createElement('td');
        td.colSpan = 5;
        td.textContent = 'No work orders yet.';
        td.className = 'empty-state';
        tr.appendChild(td);
        tbody.appendChild(tr);
        updatePaginationControls();
        return;
    }
    const totalPages = Math.max(1, Math.ceil(allItems.length / PAGE_SIZE));
    if (currentPage > totalPages) {
        currentPage = totalPages;
    }
    const start = (currentPage - 1) * PAGE_SIZE;
    const pageItems = allItems.slice(start, start + PAGE_SIZE);
    for (const wo of pageItems) {
        tbody.appendChild(renderRow(wo));
    }
    updatePaginationControls();
}
function updatePaginationControls() {
    const totalPages = Math.max(1, Math.ceil(allItems.length / PAGE_SIZE));
    qs('#page-indicator').textContent = `Page ${currentPage} of ${totalPages}`;
    qs('#prev-page-button').disabled = currentPage <= 1;
    qs('#next-page-button').disabled = currentPage >= totalPages;
}
async function onStatusChange(id, newStatus, select) {
    clearError();
    const previousValue = select.dataset.previousValue ?? select.value;
    select.disabled = true;
    try {
        await apiRequest(`/api/workorders/${id}`, {
            method: 'PATCH',
            body: JSON.stringify({ status: newStatus }),
        });
        await loadWorkOrders();
    }
    catch (err) {
        showError(err instanceof Error ? err.message : 'Failed to update status.');
        select.value = previousValue;
        select.disabled = false;
    }
}
async function onAssignedToChange(id, newValue, input) {
    clearError();
    input.disabled = true;
    try {
        await apiRequest(`/api/workorders/${id}`, {
            method: 'PATCH',
            body: JSON.stringify({ assignedTo: newValue || null }),
        });
        input.dataset.originalValue = newValue;
    }
    catch (err) {
        showError(err instanceof Error ? err.message : 'Failed to update assigned to.');
        input.value = input.dataset.originalValue ?? '';
    }
    finally {
        input.disabled = false;
    }
}
async function onCreateSubmit(event) {
    event.preventDefault();
    clearError();
    const form = event.currentTarget;
    const titleInput = qs('#title-input');
    const descriptionInput = qs('#description-input');
    const assignedToInput = qs('#assignedTo-input');
    const submitButton = qs('#create-button');
    submitButton.disabled = true;
    try {
        await apiRequest('/api/workorders', {
            method: 'POST',
            body: JSON.stringify({
                title: titleInput.value,
                description: descriptionInput.value || null,
                assignedTo: assignedToInput.value || null,
            }),
        });
        form.reset();
        await loadWorkOrders();
    }
    catch (err) {
        showError(err instanceof Error ? err.message : 'Failed to create work order.');
    }
    finally {
        submitButton.disabled = false;
    }
}
function init() {
    qs('#create-form').addEventListener('submit', (e) => {
        void onCreateSubmit(e);
    });
    qs('#refresh-button').addEventListener('click', () => {
        void loadWorkOrders();
    });
    qs('#status-filter').addEventListener('change', () => {
        void loadWorkOrders();
    });
    qs('#prev-page-button').addEventListener('click', () => {
        if (currentPage > 1) {
            currentPage--;
            renderPage();
        }
    });
    qs('#next-page-button').addEventListener('click', () => {
        const totalPages = Math.max(1, Math.ceil(allItems.length / PAGE_SIZE));
        if (currentPage < totalPages) {
            currentPage++;
            renderPage();
        }
    });
    void loadWorkOrders();
}
document.addEventListener('DOMContentLoaded', init);
export {};
