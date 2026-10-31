const NATIVE = "ehlogin";

async function report() {
  try {
    const cookies = await browser.cookies.getAll({ domain: "e-hentai.org" });
    const value = cookies.map((c) => c.name + "=" + c.value).join("; ");
    if (/(^|; )ipb_member_id=/.test(value) && /(^|; )ipb_pass_hash=/.test(value)) {
      browser.runtime.sendNativeMessage(NATIVE, { type: "cookies", value: value });
    }
  } catch (e) {
  }
}

browser.cookies.onChanged.addListener((info) => {
  if (info.cookie && info.cookie.domain.endsWith("e-hentai.org")) report();
});

report();
