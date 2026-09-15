// Offline behavioral test of the actual browser script. Run with node --test.
const {test} = require('node:test');
const assert = require('node:assert/strict');
const {readFileSync} = require('node:fs');
const {resolve} = require('node:path');
const {runInNewContext} = require('node:vm');
const source = readFileSync(resolve(__dirname,
    '../../src/main/resources/static/js/tournament-menu-filters.js'), 'utf8');

test('each checkbox submits only its local GET form immediately on change', () => {
    const listeners = [{}, {}];
    let submissions = 0;
    const form = {
        querySelectorAll(selector) {
            assert.equal(selector, 'input[type="checkbox"]');
            return listeners.map(events => ({addEventListener: (name, callback) => events[name] = callback}));
        },
        requestSubmit() { submissions++; }
    };
    runInNewContext(source, {document: {getElementById(id) {
        assert.equal(id, 'tournament-menu-filters');
        return form;
    }}});
    assert.equal(submissions, 0);
    for (const events of listeners) {
        assert.deepEqual(Object.keys(events), ['change']);
        events.change();
        events.change();
    }
    assert.equal(submissions, 4);
});

test('an unavailable catalogue does not submit or throw', () => {
    runInNewContext(source, {document: {getElementById: () => null}});
});
