package com.example.editor.grammar.core;
public record TextPosition(int line, int column) {
    public TextPosition { line=Math.max(0,line); column=Math.max(0,column); }
}
