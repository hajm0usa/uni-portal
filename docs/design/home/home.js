'use strict';

const statePicker = document.querySelector('#preview-state');
const banner = document.querySelector('#state-banner');
const dialog = document.querySelector('#detail-dialog');
const device = document.querySelector('.device');

const banners = {
  stale: ['اطلاعات نیاز به تازه‌سازی دارد', 'نمایش نسخهٔ ذخیره‌شده؛ آخرین به‌روزرسانی ۳ روز پیش.', 'به‌روزرسانی'],
  login: ['برای تازه‌سازی، دوباره وارد شوید', 'اطلاعات ذخیره‌شده همچنان در دسترس است.', 'ورود به حساب'],
  error: ['رویدادها به‌روزرسانی نشدند', 'نسخهٔ قبلی نمایش داده می‌شود. درس‌ها همچنان در دسترس‌اند.', 'تلاش دوباره'],
};

function setState(state) {
  if (![...statePicker.options].some(option => option.value === state)) return;
  statePicker.value = state;
  device.dataset.state = state;
  device.classList.toggle('is-loading', state === 'loading');
  document.querySelector('#sync-status').setAttribute('aria-busy', String(state === 'loading'));
  document.querySelector('#refresh').disabled = state === 'loading';
  document.querySelector('#featured').hidden = state === 'empty';
  document.querySelector('#upcoming').hidden = state === 'empty';
  document.querySelector('#empty-state').hidden = state !== 'empty';
  document.querySelector('#sync-label').textContent = state === 'loading'
    ? 'در حال به‌روزرسانی؛ نمایش نسخهٔ ذخیره‌شده'
    : state === 'stale' || state === 'login' ? 'آخرین به‌روزرسانی: ۱۹ آبان، ۱۰:۳۰' : 'آخرین به‌روزرسانی: ۱۰:۳۰';
  banner.hidden = !banners[state];
  if (banners[state]) {
    document.querySelector('#banner-title').textContent = banners[state][0];
    document.querySelector('#banner-description').textContent = banners[state][1];
    document.querySelector('#banner-action').textContent = banners[state][2];
  }
}

function showDetail(title, context, description) {
  document.querySelector('#dialog-title').textContent = title;
  document.querySelector('#dialog-context').textContent = context;
  document.querySelector('#dialog-description').textContent = description;
  dialog.showModal();
}

statePicker.addEventListener('change', () => setState(statePicker.value));
document.querySelector('#theme-toggle').addEventListener('click', () => {
  const dark = document.documentElement.dataset.theme !== 'dark';
  document.documentElement.dataset.theme = dark ? 'dark' : 'light';
  document.querySelector('#theme-toggle').setAttribute('aria-pressed', String(dark));
  document.querySelector('#theme-label').textContent = dark ? 'نمای روشن' : 'نمای تیره';
  document.querySelector('#theme-icon').setAttribute('href', dark ? '#sun' : '#moon');
});

document.querySelector('#refresh').addEventListener('click', () => {
  setState('loading');
  showDetail('به‌روزرسانی نمونه', '', 'این فقط پیش‌نمایش حالت بارگذاری است و درخواستی به دانشگاه نمی‌فرستد. برای دیدن نتیجه، از کنترل وضعیت صفحه استفاده کنید.');
});
document.querySelector('#banner-action').addEventListener('click', () => {
  if (statePicker.value === 'login') {
    showDetail('ورود به حساب', '', 'این پیش‌نمایش به حساب شما متصل نیست و نام کاربری یا رمز عبور دریافت نمی‌کند. در برنامهٔ اصلی، این دکمه مسیر ورود امن دانشگاه را باز می‌کند.');
  } else {
    setState('content');
  }
});
document.querySelector('#home-tab').addEventListener('click', () => {
  document.querySelector('#device-scroll').scrollTo({ top: 0, behavior: 'auto' });
});
document.querySelector('#close-dialog').addEventListener('click', () => dialog.close());

const events = {
  featured: ['تمرین دوم؛ مشتق و کاربردها', 'ریاضی عمومی · امروز، ساعت ۲۳:۵۹'],
  lab: ['گزارش آزمایشگاه دوم', 'فیزیک · فردا، ساعت ۱۸:۰۰'],
  code: ['تمرین سوم؛ حلقه‌ها', 'مبانی برنامه‌نویسی · یکشنبه، ساعت ۲۳:۵۹'],
};
document.querySelectorAll('[data-event]').forEach(button => button.addEventListener('click', () => {
  const [title, context] = events[button.dataset.event];
  showDetail(title, context, 'رویداد نمونه برای بررسی طراحی. در برنامهٔ اصلی، فعالیت پشتیبانی‌نشده با تأیید شما در مرورگر باز می‌شود؛ اینجا هیچ صفحهٔ واقعی باز نمی‌شود.');
}));
document.querySelectorAll('[data-course]').forEach(button => button.addEventListener('click', () => {
  showDetail(button.dataset.course, 'محتوای درس', 'این پیش‌نمایش فقط صفحهٔ امروز را پوشش می‌دهد. طراحی بخش‌های درس و فایل‌ها پس از تأیید این صفحه انجام می‌شود.');
}));
document.querySelectorAll('[data-destination]').forEach(button => button.addEventListener('click', () => {
  const upcoming = ['نمره‌ها', 'پیام‌ها'].includes(button.dataset.destination);
  showDetail(button.dataset.destination, upcoming ? 'به‌زودی' : 'صفحهٔ بعدی', upcoming
    ? 'این بخش در برنامه هنوز آماده نیست. در این پیش‌نمایش نیز اطلاعاتی برای آن ساخته نشده است.'
    : 'این پیش‌نمایش فقط صفحهٔ امروز را پوشش می‌دهد. این دکمه در برنامه به بخش مربوط می‌رود؛ طراحی آن بخش در مرحلهٔ بعد انجام می‌شود.');
}));

document.documentElement.dataset.theme = matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light';
if (document.documentElement.dataset.theme === 'dark') {
  document.querySelector('#theme-toggle').setAttribute('aria-pressed', 'true');
  document.querySelector('#theme-label').textContent = 'نمای روشن';
  document.querySelector('#theme-icon').setAttribute('href', '#sun');
}
setState('content');
