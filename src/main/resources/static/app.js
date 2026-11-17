(() => {
  "use strict";

  const chat = document.getElementById("chat");
  const form = document.getElementById("form");
  const input = document.getElementById("input");
  const sendBtn = document.getElementById("sendBtn");
  const resetBtn = document.getElementById("resetBtn");
  const welcome = document.getElementById("welcome");

  let totalPages = 0;
  let busy = false;
  let controller = null;

  // ---------- Session ----------
  const newId = () => (crypto.randomUUID ? crypto.randomUUID() : String(Date.now()) + Math.random());
  let sessionId = sessionStorage.getItem("cncSession") || newId();
  sessionStorage.setItem("cncSession", sessionId);

  fetch("/api/cours/info").then(r => r.json()).then(d => { totalPages = d.pages; }).catch(() => {});

  // ---------- Markdown ----------
  marked.setOptions({ gfm: true, breaks: true });

  function renderMarkdown(el, text) {
    const html = marked.parse(text);
    el.innerHTML = window.DOMPurify ? DOMPurify.sanitize(html) : html;
    linkifyPages(el);
  }

  // « (page 12) », « page 12 », « pages 12 et 13 », « p. 40-42 » → liens cliquables
  const PAGE_GROUP = /(?:\bpages?|\bp\.)\s*\d+(?:\s*(?:,|et|à|-|–)\s*\d+)*/gi;

  function linkifyPages(root) {
    const walker = document.createTreeWalker(root, NodeFilter.SHOW_TEXT, {
      acceptNode: n => n.parentElement.closest("pre, code, a")
        ? NodeFilter.FILTER_REJECT : NodeFilter.FILTER_ACCEPT
    });
    const nodes = [];
    while (walker.nextNode()) nodes.push(walker.currentNode);

    for (const node of nodes) {
      const text = node.nodeValue;
      PAGE_GROUP.lastIndex = 0;
      if (!PAGE_GROUP.test(text)) continue;
      PAGE_GROUP.lastIndex = 0;

      const frag = document.createDocumentFragment();
      let last = 0, m;
      while ((m = PAGE_GROUP.exec(text)) !== null) {
        frag.append(text.slice(last, m.index));
        const group = m[0];
        let pos = 0;
        for (const num of group.matchAll(/\d+/g)) {
          frag.append(group.slice(pos, num.index));
          const a = document.createElement("a");
          a.className = "page-link";
          a.href = "#page-" + num[0];
          a.dataset.page = num[0];
          a.textContent = num[0];
          frag.append(a);
          pos = num.index + num[0].length;
        }
        frag.append(group.slice(pos));
        last = m.index + group.length;
      }
      frag.append(text.slice(last));
      node.replaceWith(frag);
    }
  }

  // ---------- Messages ----------
  function addMessage(role, content) {
    welcome.hidden = true;
    const wrap = document.createElement("div");
    wrap.className = "msg " + role;
    const bubble = document.createElement("div");
    bubble.className = "bubble";
    if (role === "user" || role === "error") bubble.textContent = content;
    else if (content) renderMarkdown(bubble, content);
    wrap.append(bubble);
    chat.append(wrap);
    scrollDown(true);
    return bubble;
  }

  function showTyping(bubble) {
    bubble.innerHTML = '<span class="typing"><span class="dots"><i></i><i></i><i></i></span>en train d\'écrire…</span>';
  }

  function scrollDown(force) {
    const nearBottom = chat.scrollHeight - chat.scrollTop - chat.clientHeight < 120;
    if (force || nearBottom) chat.scrollTop = chat.scrollHeight;
  }

  function setBusy(b) {
    busy = b;
    sendBtn.disabled = b;
  }

  // ---------- Envoi + lecture du flux SSE ----------
  async function send(question) {
    if (busy || !question.trim()) return;
    setBusy(true);
    addMessage("user", question);
    const bubble = addMessage("assistant", "");
    showTyping(bubble);

    let answer = "";
    let pending = false;
    const flush = () => { pending = false; renderMarkdown(bubble, answer); scrollDown(false); };

    controller = new AbortController();
    try {
      const resp = await fetch("/api/chat", {
        method: "POST",
        headers: { "Content-Type": "application/json", "Accept": "text/event-stream, application/json" },
        body: JSON.stringify({ sessionId, message: question }),
        signal: controller.signal
      });

      if (!resp.ok || !resp.body) {
        let msg = "Erreur " + resp.status;
        try { msg = (await resp.json()).message || msg; } catch (_) { /* corps non JSON */ }
        throw new Error(msg);
      }

      const reader = resp.body.getReader();
      const decoder = new TextDecoder();
      let buffer = "";

      for (;;) {
        const { value, done } = await reader.read();
        if (done) break;
        buffer += decoder.decode(value, { stream: true }).replace(/\r\n/g, "\n");

        let sep;
        while ((sep = buffer.indexOf("\n\n")) !== -1) {
          const raw = buffer.slice(0, sep);
          buffer = buffer.slice(sep + 2);
          const evt = parseEvent(raw);
          if (!evt) continue;

          if (evt.event === "token") {
            answer += evt.data.t;
            if (!pending) { pending = true; requestAnimationFrame(flush); }
          } else if (evt.event === "error") {
            throw new Error(evt.data.message || "Erreur inconnue");
          }
        }
      }
      if (!answer) throw new Error("Réponse vide de l'API.");
      flush();
    } catch (err) {
      if (err.name === "AbortError") {
        if (!answer) bubble.parentElement.remove();
      } else {
        if (!answer) bubble.parentElement.remove();
        addMessage("error", "⚠️ " + err.message);
      }
    } finally {
      controller = null;
      setBusy(false);
      input.focus();
    }
  }

  function parseEvent(raw) {
    let event = "message";
    const data = [];
    for (const line of raw.split("\n")) {
      if (line.startsWith("event:")) event = line.slice(6).trim();
      else if (line.startsWith("data:")) data.push(line.slice(5).replace(/^ /, ""));
    }
    if (!data.length) return null;
    try { return { event, data: JSON.parse(data.join("\n")) }; } catch (_) { return null; }
  }

  // ---------- Formulaire ----------
  form.addEventListener("submit", e => {
    e.preventDefault();
    const q = input.value;
    input.value = "";
    autoGrow();
    send(q);
  });

  input.addEventListener("keydown", e => {
    if (e.key === "Enter" && !e.shiftKey && !e.isComposing) {
      e.preventDefault();
      form.requestSubmit();
    }
  });

  function autoGrow() {
    input.style.height = "auto";
    input.style.height = Math.min(input.scrollHeight, 160) + "px";
  }
  input.addEventListener("input", autoGrow);

  document.getElementById("suggestions").addEventListener("click", e => {
    if (e.target.tagName === "BUTTON") send(e.target.textContent);
  });

  resetBtn.addEventListener("click", async () => {
    if (controller) controller.abort();
    try {
      await fetch("/api/reset", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ sessionId })
      });
    } catch (_) { /* hors ligne : on repart quand même à zéro */ }
    sessionId = newId();
    sessionStorage.setItem("cncSession", sessionId);
    chat.querySelectorAll(".msg").forEach(m => m.remove());
    welcome.hidden = false;
    input.focus();
  });

  // ---------- Modale « page du cours » ----------
  const modal = document.getElementById("modal");
  const modalImg = document.getElementById("modalImg");
  const modalTitle = document.getElementById("modalTitle");
  const modalLoading = document.getElementById("modalLoading");
  const prevBtn = document.getElementById("prevPage");
  const nextBtn = document.getElementById("nextPage");
  let currentPage = 1;

  function openPage(n) {
    if (totalPages && (n < 1 || n > totalPages)) return;
    currentPage = n;
    modalTitle.textContent = "Page " + n + (totalPages ? " / " + totalPages : "");
    prevBtn.disabled = n <= 1;
    nextBtn.disabled = totalPages > 0 && n >= totalPages;
    modalLoading.hidden = false;
    modalLoading.textContent = "Chargement…";
    modalImg.alt = "Page " + n + " du cours";
    modalImg.src = "/api/cours/page/" + n;
    modal.hidden = false;
  }

  modalImg.addEventListener("load", () => { modalLoading.hidden = true; });
  modalImg.addEventListener("error", () => { modalLoading.textContent = "Page introuvable."; });

  function closeModal() { modal.hidden = true; modalImg.removeAttribute("src"); }

  chat.addEventListener("click", e => {
    const a = e.target.closest("a.page-link");
    if (!a) return;
    e.preventDefault();
    openPage(parseInt(a.dataset.page, 10));
  });
  prevBtn.addEventListener("click", () => openPage(currentPage - 1));
  nextBtn.addEventListener("click", () => openPage(currentPage + 1));
  document.getElementById("closeModal").addEventListener("click", closeModal);
  modal.addEventListener("click", e => { if (e.target === modal) closeModal(); });
  document.addEventListener("keydown", e => {
    if (modal.hidden) return;
    if (e.key === "Escape") closeModal();
    else if (e.key === "ArrowLeft" && currentPage > 1) openPage(currentPage - 1);
    else if (e.key === "ArrowRight") openPage(currentPage + 1);
  });

  input.focus();
})();
