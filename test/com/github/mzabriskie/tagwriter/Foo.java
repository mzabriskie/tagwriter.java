package com.github.mzabriskie.tagwriter;

public class Foo {
    Bar bar;

    public void setBar(Bar bar) { this.bar = bar; }
    public Bar getBar() { return bar; }

    private boolean getIllegal() { return false; }
}
