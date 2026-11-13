'use strict';

// Browser-only checks; no test code runs in the normal design preview.
if (new URLSearchParams(location.search).has('test')) {
  (async () => {
    const results = [];
    const assert = (condition, message) => {
      if (!condition) throw new Error(message);
      results.push(message);
    };
    const $ = selector => document.querySelector(selector);
    const chooseState = state => {
      $('#preview-state').value = state;
      $('#preview-state').dispatchEvent(new Event('change'));
    };
    const luminance = hex => {
      const channels = hex.trim().match(/[a-f\d]{2}/gi).map(value => {
        const s = parseInt(value, 16) / 255;
        return s <= .04045 ? s / 12.92 : ((s + .055) / 1.055) ** 2.4;
      });
      return channels[0] * .2126 + channels[1] * .7152 + channels[2] * .0722;
    };
    const checkContrast = () => {
      const style = getComputedStyle(document.documentElement);
      const color = token => luminance(style.getPropertyValue(token));
      for (const [foreground, background] of [
        ['--text', '--surface'], ['--text', '--featured'], ['--text', '--raised'],
        ['--secondary', '--surface'], ['--secondary', '--featured'], ['--secondary', '--raised'],
        ['--muted', '--surface'], ['--muted', '--canvas'],
        ['--primary', '--surface'], ['--primary', '--featured'], ['--primary', '--raised'],
        ['--primary', '--primary-soft'], ['--warning', '--warning-soft'],
        ['--on-primary', '--primary'],
      ]) {
        const a = color(foreground), b = color(background);
        const ratio = (Math.max(a, b) + .05) / (Math.min(a, b) + .05);
        assert(ratio >= 4.5, `${document.documentElement.dataset.theme}: ${foreground}/${background} >= 4.5 (${ratio.toFixed(2)})`);
      }
    };

    try {
      document.documentElement.dataset.testViewport = `${innerWidth}x${innerHeight}`;
      await document.fonts.ready;
      await Promise.all([400, 500, 600].map(weight => document.fonts.load(`${weight} 16px Vazirmatn`, 'دانشگاه')));
      assert([...document.fonts].some(font => font.family === 'Vazirmatn' && font.status === 'loaded'), 'Bundled variable font loads');
      assert(document.documentElement.lang === 'fa' && document.documentElement.dir === 'rtl', 'Persian RTL document');
      const ids = [...document.querySelectorAll('[id]')].map(element => element.id);
      assert(new Set(ids).size === ids.length, 'Document IDs are unique');
      assert($('.logo img').complete && $('.logo img').naturalWidth > 0, 'Existing university logo loads');
      assert(getComputedStyle($('.logo')).backgroundColor === 'rgba(0, 0, 0, 0)', 'Logo background is transparent');
      assert(getComputedStyle($('.logo')).borderTopWidth === '0px', 'Logo has no surrounding border');
      assert(getComputedStyle($('.page-heading h2')).fontWeight === '600', 'Page heading uses real semibold weight');
      assert(getComputedStyle($('.featured h3')).fontWeight === '500', 'Featured title uses medium weight');
      assert(!$('#upcoming').textContent.includes($('.featured h3').textContent), 'Featured event is not duplicated');

      for (const state of ['loading', 'stale', 'login', 'error']) {
        chooseState(state);
        assert(!$('#featured').hidden && !$('#upcoming').hidden, `${state} retains saved events`);
        assert(!$('.courses-section').hidden, `${state} retains courses`);
        assert($('#state-banner').hidden === (state === 'loading'), `${state} banner visibility`);
      }
      chooseState('empty');
      assert($('#featured').hidden && $('#upcoming').hidden && !$('#empty-state').hidden, 'Empty state replaces events, not courses');
      assert($('.agenda-scope').textContent.includes('۷ روز آینده') && !$('.device').textContent.includes('حداکثر ۶ رویداد'), 'Design shows the date range without a six-event cap');
      chooseState('stale');
      $('#banner-action').click();
      assert($('.device').dataset.state === 'content', 'Retry restores the mock content state');

      chooseState('login');
      $('#banner-action').click();
      assert($('#detail-dialog').open && !document.querySelector('input'), 'Login preview collects no credentials');
      $('#close-dialog').click();
      assert(!$('#detail-dialog').open, 'Dialog closes');
      chooseState('content');
      for (const button of document.querySelectorAll('[data-event], [data-course], [data-destination]')) {
        button.click();
        assert($('#detail-dialog').open && $('#dialog-title').textContent.length > 0, `Preview opens: ${button.textContent.trim().replace(/\s+/g, ' ')}`);
        $('#close-dialog').click();
      }
      $('#refresh').click();
      assert($('.device').dataset.state === 'loading' && $('#refresh').disabled, 'Refresh shows a bounded manual loading preview');
      $('#close-dialog').click();
      chooseState('content');

      for (const theme of ['light', 'dark']) {
        if (document.documentElement.dataset.theme !== theme) $('#theme-toggle').click();
        assert($('#theme-toggle').getAttribute('aria-pressed') === String(theme === 'dark'), `${theme} toggle state`);
        checkContrast();
      }
      $('#theme-toggle').click();
      assert(document.documentElement.dataset.theme === 'light', 'Theme can return to light');
      assert(document.documentElement.scrollWidth <= innerWidth + 1, 'No page horizontal overflow');
      assert($('#device-scroll').scrollWidth <= $('#device-scroll').clientWidth + 1, 'No app horizontal overflow');
      for (const button of document.querySelectorAll('button, select')) {
        if (button.getClientRects().length === 0) continue;
        const rect = button.getBoundingClientRect();
        assert(rect.width >= 48 && rect.height >= 48, `48px touch target: ${button.getAttribute('aria-label') || button.id || button.textContent.trim().replace(/\s+/g, ' ')}`);
      }
      const app = $('.device').getBoundingClientRect();
      const footer = $('.bottom-nav').getBoundingClientRect();
      assert(footer.bottom <= app.bottom + 1, 'Navigation stays within app frame');
      $('#device-scroll').scrollTop = $('#device-scroll').scrollHeight;
      assert(Math.abs($('.bottom-nav').getBoundingClientRect().top - footer.top) < 1, 'Navigation remains fixed while app content scrolls');
      $('#home-tab').click();
      assert($('#device-scroll').scrollTop === 0, 'Home returns content to the top');
      assert(performance.getEntriesByType('resource').every(entry => new URL(entry.name).origin === location.origin), 'All runtime resources are local');
      document.documentElement.dataset.testStatus = 'passed';
    } catch (error) {
      results.push(`FAIL: ${error.message}`);
      document.documentElement.dataset.testStatus = 'failed';
    }
    const output = document.createElement('output');
    output.id = 'test-results';
    output.textContent = results.join('\n');
    document.body.append(output);
  })();
}
