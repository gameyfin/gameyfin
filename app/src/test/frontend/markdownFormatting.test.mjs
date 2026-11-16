import assert from 'node:assert/strict';
import {test} from 'node:test';
import {formatMarkdown} from '../../main/frontend/components/general/input/markdownFormatting.ts';

test('inline formatting preserves surrounding text and inserts empty markers', () => {
    assert.equal(formatMarkdown('before word after', {start: 7, end: 11}, 'bold').value, 'before **word** after');
    assert.deepEqual(formatMarkdown('', {start: 0, end: 0}, 'italic'), {value: '**', selection: {start: 1, end: 1}});
});
test('block formatting expands to lines without changing adjacent lines', () => {
    assert.equal(formatMarkdown('one\ntwo\nthree', {start: 1, end: 7}, 'number').value, '1. one\n2. two\nthree');
    assert.equal(formatMarkdown('one\ntwo', {start: 4, end: 4}, 'quote').value, 'one\n> two');
    assert.equal(formatMarkdown('one\ntwo', {start: 0, end: 4}, 'bullet').value, '- one\ntwo');
    assert.equal(formatMarkdown('\nhello', {start: 0, end: 0}, 'heading').value, '## \nhello');
});

test('empty actions insert only syntax and position the caret for typing', () => {
    for (const [action, value, caret] of [
        ['bold', '****', 2], ['italic', '**', 1], ['code', '``', 1],
        ['heading', '## ', 3], ['bullet', '- ', 2], ['number', '1. ', 3],
        ['quote', '> ', 2], ['block', '```\n\n```', 4], ['link', '[]()', 1]
    ]) {
        assert.deepEqual(formatMarkdown('', {start: 0, end: 0}, action),
            {value, selection: {start: caret, end: caret}}, action);
    }
    assert.deepEqual(formatMarkdown('before after', {start: 7, end: 7}, 'bold'),
        {value: 'before ****after', selection: {start: 9, end: 9}});
});
test('link insertion selects the URL for replacement', () => {
    const result = formatMarkdown('game', {start: 0, end: 4}, 'link');
    assert.equal(result.value.slice(result.selection.start, result.selection.end), 'https://example.com');
});

test('code formatting supports inline and fenced blocks', () => {
    assert.equal(formatMarkdown('code', {start: 0, end: 4}, 'code').value, '`code`');
    assert.equal(formatMarkdown('code', {start: 0, end: 4}, 'block').value, '```\ncode\n```');
});
