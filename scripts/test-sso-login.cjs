// Dependency-free tests of the exact WebView script; DOM objects below model browser-owned controls.
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const script = fs.readFileSync('app/src/main/assets/sso-login.js', 'utf8');
const origin = 'https://sso1.ut.ac.ir';
const path = '/ApiContainer.SSO.RCL1/Account/login';

function page() {
    const field = (name, type, value = '') => ({ name, type, value, disabled: false });
    const fields = [
        field('ReturnUrl', 'hidden', '/ApiContainer.SSO.RCL1/connect/authorize/callback?state=XXXX'),
        field('captcha', 'hidden'), field('Username', 'text'), field('Password', 'password'),
        { ...field('RememberLogin', 'checkbox', 'true'), checked: true },
        field('__RequestVerificationToken', 'hidden', 'XXXX'), field('RememberLogin', 'hidden', 'false'),
    ];
    const attrs = { formaction: path };
    const form = { method: 'post', target: '', elements: fields };
    const button = {
        name: 'button', value: 'login', disabled: false, form,
        getAttribute: name => attrs[name] || null,
        click() { this.clicks = (this.clicks || 0) + 1; },
    };
    fields.push(button);
    const window = {};
    window.top = window;
    const context = {
        window, location: { origin }, URL,
        document: { baseURI: origin + path, querySelectorAll: () => [button] },
    };
    return { context, fields, form, button, attrs, named: name => fields.find(f => f.name === name) };
}

function run(page, credentials = null) {
    return vm.runInNewContext(`(${script})(${JSON.stringify(credentials)})`, page.context, { timeout: 1000 });
}

const credentials = { username: 'synthetic-user', password: 'synthetic-password"\\\n' };
const valid = page();
assert.equal(run(valid), 'ready');
assert.equal(valid.named('Username').value, '');
assert.equal(valid.button.clicks, undefined);
assert.equal(run(valid, credentials), 'submitted');
assert.equal(valid.named('Username').value, credentials.username);
assert.equal(valid.named('Password').value, credentials.password);
assert.equal(valid.button.clicks, 1);
assert.equal(valid.named('__RequestVerificationToken').value, 'XXXX');
assert.equal(valid.named('ReturnUrl').value, '/ApiContainer.SSO.RCL1/connect/authorize/callback?state=XXXX');
assert.deepEqual(valid.fields.filter(f => f.name === 'RememberLogin').map(f => f.value), ['true', 'false']);

const mutations = [
    p => { p.context.location.origin = 'http://sso1.ut.ac.ir'; },
    p => { p.context.location.origin = 'https://sso1.ut.ac.ir.evil.invalid'; },
    p => { p.context.window.top = {}; },
    p => { p.attrs.formaction = 'https://evil.invalid/login'; },
    p => { p.attrs.formaction = 'https://user@sso1.ut.ac.ir' + path; },
    p => { p.attrs.formaction = 'https://sso1.ut.ac.ir:444' + path; },
    p => { p.attrs.formaction = path + '?unexpected=1'; },
    p => { p.attrs.formaction = '/other'; },
    p => { delete p.attrs.formaction; },
    p => { p.form.method = 'get'; },
    p => { p.attrs.formmethod = 'get'; },
    p => { p.form.target = '_blank'; },
    p => { p.button.form = null; },
    p => { p.button.disabled = true; },
    p => { p.named('Password').type = 'text'; },
    p => { p.named('Username').disabled = true; },
    p => { p.named('__RequestVerificationToken').value = ''; },
    p => { p.named('__RequestVerificationToken').type = 'text'; },
    p => { p.named('ReturnUrl').value = '//evil.invalid/'; },
    p => { p.named('captcha').value = 'challenge'; },
    p => { p.named('captcha').type = 'text'; },
    p => { p.fields.push({ name: 'otp', type: 'text' }); },
    p => { p.fields.push({ ...p.named('Password') }); },
    p => { p.fields.push({ ...p.named('__RequestVerificationToken'), value: 'different' }); },
    p => { p.fields.push({ ...p.named('__RequestVerificationToken') }); },
    p => { p.context.document.querySelectorAll = () => []; },
    p => { p.context.document.querySelectorAll = () => [p.button, p.button]; },
];
for (const mutate of mutations) {
    const invalid = page();
    mutate(invalid);
    assert.notEqual(run(invalid, credentials), 'submitted');
    assert.equal(invalid.named('Username').value, '');
    assert.equal(invalid.named('Password').value, '');
    assert.equal(invalid.button.clicks, undefined);
}
console.log(`SSO script: valid form and ${mutations.length} unsafe/changed forms checked.`);
