import assert from 'node:assert/strict';
import {test} from 'node:test';
import {MarkdownHistory} from '../../main/frontend/components/general/input/markdownHistory.ts';

const snapshot = (value, start = value.length, end = start) => ({value, selection: {start, end}});

test('typing and formatting share undo and redo with selection restoration', () => {
    const history = new MarkdownHistory('');
    history.record(snapshot(''), snapshot('a'), 'insertText', 100);
    history.record(snapshot('a'), snapshot('ab'), 'insertText', 200);
    history.record(snapshot('ab', 0, 2), snapshot('**ab**', 2, 4));
    assert.deepEqual(history.undo({start: 2, end: 4}), snapshot('ab', 0, 2));
    assert.deepEqual(history.undo({start: 0, end: 2}), snapshot(''));
    assert.deepEqual(history.redo({start: 0, end: 0}), snapshot('ab', 0, 2));
    assert.deepEqual(history.redo({start: 0, end: 2}), snapshot('**ab**', 2, 4));
});

test('new edits discard redo and external resets discard stale history', () => {
    const history = new MarkdownHistory('');
    history.record(snapshot(''), snapshot('hello'));
    history.undo({start: 5, end: 5});
    history.record(snapshot(''), snapshot('other'));
    assert.equal(history.redo({start: 5, end: 5}), undefined);
    history.sync('source changed');
    assert.equal(history.undo({start: 0, end: 0}), undefined);
});

test('pauses, cursor moves, and paste create separate undo steps', () => {
    const history = new MarkdownHistory('');
    history.record(snapshot(''), snapshot('a'), 'insertText', 100);
    history.record(snapshot('a'), snapshot('ab'), 'insertText', 1000);
    history.record(snapshot('ab', 0), snapshot('Xab', 1), 'insertText', 1100);
    history.record(snapshot('Xab', 1), snapshot('Xpastedab', 7));
    assert.deepEqual(history.undo({start: 7, end: 7}), snapshot('Xab', 1));
    assert.deepEqual(history.undo({start: 1, end: 1}), snapshot('ab', 0));
    assert.deepEqual(history.undo({start: 0, end: 0}), snapshot('a'));
});
