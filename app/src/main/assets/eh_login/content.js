(function () {
  const NATIVE = "ehlogin";
  const send = (msg) => browser.runtime.sendNativeMessage(NATIVE, msg);
  const TURNSTILE = 'input[name="cf-turnstile-response"],input[id^="cf-chl-widget-"]';

  let theme = null;
  let themeRequested = false;
  let widgetShown = false;
  let solvedSent = false;
  let submitted = false;

  function isChallenge() {
    return document.title.indexOf("Just a moment") !== -1
      || !!document.querySelector(TURNSTILE + ",#challenge-stage,#challenge-form,iframe[src*='challenges.cloudflare.com']");
  }

  function widgetBox() {
    const input = document.querySelector(TURNSTILE);
    if (!input) return null;
    let box = input.parentElement;
    while (box && box.parentElement && box.parentElement !== document.body) {
      const r = box.getBoundingClientRect();
      if (r.width >= 280 && r.height >= 60 && r.height <= 150) break;
      box = box.parentElement;
    }
    return box;
  }

  function blend() {
    if (!theme) return;
    let style = document.getElementById("nc-blend");
    if (!style) {
      style = document.createElement("style");
      style.id = "nc-blend";
      (document.head || document.documentElement).appendChild(style);
    }
    style.textContent = "html,body,.main-wrapper,.main-content{background:" + theme.bg + "!important}"
      + "html{color-scheme:" + (theme.dark ? "dark" : "light") + "}"
      + ".footer,h1,h2,p,noscript{display:none!important}"
      + "html,body{margin:0!important;padding:0!important;overflow:hidden!important;height:100%!important}"
      + ".main-wrapper{display:flex!important;align-items:center!important;justify-content:center!important;min-height:100vh!important;height:100vh!important;padding:0!important;margin:0!important}"
      + ".main-content{margin:0 auto!important;padding:0!important;width:auto!important;max-width:none!important;display:flex!important;align-items:center!important;justify-content:center!important}"
      + "[data-nc-box]{margin:0!important}";
  }

  function handleChallenge() {
    if (!themeRequested) {
      themeRequested = true;
      send({ type: "theme" }).then((reply) => {
        try { theme = typeof reply === "string" ? JSON.parse(reply) : reply; } catch (e) {}
        blend();
      });
    }
    blend();
    const box = widgetBox();
    if (!box) return;
    box.setAttribute("data-nc-box", "1");
    if (widgetShown) return;
    widgetShown = true;
    try { box.scrollIntoView({ block: "center", inline: "center" }); } catch (e) {}
    send({ type: "captcha" });
    const watch = setInterval(() => {
      const input = document.querySelector(TURNSTILE);
      if (solvedSent || !input) return;
      if (input.value && input.value.length > 20) {
        solvedSent = true;
        clearInterval(watch);
        send({ type: "solved" });
      }
    }, 150);
    setTimeout(() => clearInterval(watch), 120000);
  }

  function loginForm() {
    for (const f of document.querySelectorAll("form")) {
      if (f.querySelector('input[type="password"]')) return f;
    }
    return null;
  }

  function handleForm(form) {
    if (submitted) return;
    submitted = true;
    send({ type: "form" }).then((reply) => {
      const r = typeof reply === "string" ? JSON.parse(reply) : reply;
      if (!r || !r.u) {
        submitted = false;
        return;
      }
      const user = form.querySelector('input[name="UserName"]') || form.querySelector('input[type="text"]');
      if (user) user.value = r.u;
      form.querySelector('input[type="password"]').value = r.p;
      if (r.inv) {
        for (const b of form.querySelectorAll('input[type="checkbox"]')) {
          let text = "";
          if (b.id) {
            const label = document.querySelector('label[for="' + b.id + '"]');
            if (label) text += label.textContent;
          }
          const parent = b.closest("td,li,div,tr,p,span");
          if (parent) text += " " + parent.textContent;
          if (/invisible|active users/i.test(text)) {
            b.checked = true;
            break;
          }
        }
      }
      const submit = form.querySelector('input[type="submit"],button[type="submit"]')
        || form.querySelector('input[name="ipb_login_submit"]')
        || form.querySelector("button");
      if (submit) submit.click();
      else form.submit();
    });
  }

  function tick() {
    if (isChallenge()) {
      handleChallenge();
      return;
    }
    const form = loginForm();
    if (form) handleForm(form);
  }

  const observer = new MutationObserver(tick);
  observer.observe(document.documentElement, { childList: true, subtree: true, attributes: true });
  tick();
  const interval = setInterval(tick, 700);
  setTimeout(() => {
    observer.disconnect();
    clearInterval(interval);
  }, 60000);
})();
