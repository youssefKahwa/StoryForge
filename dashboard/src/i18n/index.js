import en from './messages/en.json';

export const DEFAULT_LOCALE = 'en';

const catalogs = { en };

export const messagesFor = (locale) => catalogs[locale] || catalogs[DEFAULT_LOCALE];
