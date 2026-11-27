import test from 'node:test';
import assert from 'node:assert/strict';
import {rankFreeCandidates} from './index.js';

test('auto model ranking puts healthy free models before failing ones', () => {
  const rows = [
    {id: 'z/free', free: true},
    {id: 'a/free', free: true},
    {id: 'paid/model', free: false},
    {id: 'b/free', free: true}
  ];
  const ranked = rankFreeCandidates(rows, id => id === 'a/free');
  assert.deepEqual(ranked.map(x => x.id), ['b/free', 'z/free', 'a/free']);
  assert.ok(!ranked.some(x => x.id === 'paid/model'));
});

test('auto model ranking rejects malformed entries without throwing', () => {
  const ranked = rankFreeCandidates([
    null, {}, {id: 7, free: true}, {id: 'ok/free', free: true}
  ]);
  assert.deepEqual(ranked.map(x => x.id), ['ok/free']);
});
