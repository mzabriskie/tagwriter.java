package com.github.mzabriskie.tagwriter;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import javax.servlet.jsp.JspException;
import javax.servlet.jsp.PageContext;

public class TagWriterTest extends Assert {

    PageContext pageContext;

    @Before
    public void setup() {
        pageContext = new MockPageContext();
    }

    @Test
    public void testWriteTag() {}

    @Test
    public void testWriteBodyInclude() {}

    @Test
    public void testWriteBodyBuffered() {}

    @Test
    public void testWriteBodySkip() {}

    @Test
    public void testTryCatchFinallyTag() {}

    @Test
    public void testEvaluate() {
        String template = "${foo.bar.baz.name}";
        String result = null;
        boolean error;
        Foo foo = new Foo();
        Bar bar = new Bar();
        Baz baz = new Baz();
        pageContext.setAttribute("foo", foo);

        //1. Test with null bar
        try {
            result = TagWriter.evaluate(pageContext, template);
        } catch (JspException e) {
            e.printStackTrace();
        }
        assertEquals("", result);

        //2. Test with null baz
        foo.setBar(bar);
        try {
            result = TagWriter.evaluate(pageContext, template);
        } catch (JspException e) {
            e.printStackTrace();
        }
        assertEquals("", result);

        //3. Test with null name
        bar.setBaz(baz);
        try {
            result = TagWriter.evaluate(pageContext, template);
        } catch (JspException e) {
            e.printStackTrace();
        }
        assertEquals("", result);

        //4. Test with good values
        baz.setName("Foo Bar");
        try {
            result = TagWriter.evaluate(pageContext, template);
        } catch (JspException e) {
            e.printStackTrace();
        }
        assertEquals("Foo Bar", result);

        //5. Test NoSuchMethodException
        error = false;
        try {
            TagWriter.evaluate(pageContext, "${foo.name}");
        } catch (JspException e) {
            error = true;
        }
        assertTrue(error);

        //6. Test IllegalAccessException
        error = false;
        try {
            TagWriter.evaluate(pageContext, "${foo.illegal}");
        } catch (JspException e) {
            error = true;
        }
        assertTrue(error);
    }

}
