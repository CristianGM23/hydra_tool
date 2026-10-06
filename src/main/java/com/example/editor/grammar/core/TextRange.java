package com.example.editor.grammar.core;
public record TextRange(TextPosition start, TextPosition end) {
    public TextRange {
        start=start==null?new TextPosition(0,0):start;
        end=end==null?start:end;
    }
}
