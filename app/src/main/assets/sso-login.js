function (credentials) {
    "use strict";
    try {
        const origin = "https://sso1.ut.ac.ir";
        const path = "/ApiContainer.SSO.RCL1/Account/login";
        if (window !== window.top || location.origin !== origin) return "blocked";
        const buttons = Array.from(document.querySelectorAll('button[name="button"][value="login"]'));
        if (buttons.length !== 1) return "unsupported";
        const button = buttons[0];
        const form = button.form;
        if (!form || button.disabled || !button.getAttribute("formaction")) return "unsupported";
        const action = new URL(button.getAttribute("formaction"), document.baseURI);
        const method = (button.getAttribute("formmethod") || form.method).toLowerCase();
        const target = button.getAttribute("formtarget") || form.target;
        if (action.origin !== origin || action.pathname !== path || action.search || action.hash ||
            action.username || action.password || method !== "post" || (target && target !== "_self")) return "blocked";
        const fields = Array.from(form.elements);
        const named = name => fields.filter(field => field.name === name && !field.disabled);
        const single = name => named(name).length === 1 ? named(name)[0] : null;
        const user = single("Username"), password = single("Password");
        const token = named("__RequestVerificationToken"), returnUrl = single("ReturnUrl");
        const captcha = single("captcha"), remember = named("RememberLogin");
        if (!user || user.type !== "text" || !password || password.type !== "password" ||
            !returnUrl || returnUrl.type !== "hidden" || !returnUrl.value.startsWith("/ApiContainer.SSO.RCL1/connect/authorize/callback?") ||
            token.length !== 1 || token[0].type !== "hidden" || !token[0].value ||
            !captcha || captcha.type !== "hidden" || captcha.value !== "" ||
            remember.length !== 2 || remember[0].type !== "checkbox" || remember[0].value !== "true" ||
            remember[1].type !== "hidden" || remember[1].value !== "false") return "unsupported";
        // A changed/challenged form is manual-only; never guess or submit extra controls.
        const allowed = new Set(["ReturnUrl", "captcha", "Username", "Password", "RememberLogin", "button", "__RequestVerificationToken"]);
        if (fields.some(field => field.name && !field.disabled && !allowed.has(field.name))) return "unsupported";
        if (!credentials) return "ready";
        if (typeof credentials.username !== "string" || !credentials.username ||
            typeof credentials.password !== "string" || !credentials.password) return "blocked";
        user.value = credentials.username;
        password.value = credentials.password;
        remember[0].checked = true;
        // Click the observed button so browser ownership, submitter and live anti-forgery fields are preserved.
        button.click();
        return "submitted";
    } catch (_) {
        return "unsupported";
    }
}
