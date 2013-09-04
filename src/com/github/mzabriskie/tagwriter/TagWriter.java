/*

Copyright (c) 2013 by Matt Zabriskie

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in
all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
THE SOFTWARE.

*/

package com.github.mzabriskie.tagwriter;

import org.apache.jasper.runtime.BodyContentImpl;

import javax.servlet.jsp.JspException;
import javax.servlet.jsp.PageContext;
import javax.servlet.jsp.tagext.*;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TagWriter {

    /**
     * Writes a generic {@link javax.servlet.jsp.tagext.Tag} to the provided {@link javax.servlet.jsp.PageContext}.
     *
     * @param tag
     * @param pageContext
     * @throws JspException
     */
    public static void write(Tag tag, PageContext pageContext) throws JspException {
        write(tag, pageContext, null);
    }

    /**
     * Writes a {@link javax.servlet.jsp.tagext.BodyTagSupport} to the provided {@link javax.servlet.jsp.PageContext}.
     * The value of the <code>body</code> parameter will be evaluated for tokens.
     *
     * @param tag
     * @param pageContext
     * @param body
     * @throws JspException
     */
    public static void write(BodyTagSupport tag, PageContext pageContext, String body) throws JspException {
        write((Tag) tag, pageContext, body);
    }

    /**
     * Writes a {@link javax.servlet.jsp.tagext.Tag} to the provided {@link javax.servlet.jsp.PageContext} using the
     * invocation protocol according to Java's tag specification.
     *
     * @see <a href="http://download.oracle.com/javaee/5/api/javax/servlet/jsp/tagext/Tag.html">Tag Lifecycle</a>
     * @see <a href="http://download.oracle.com/javaee/5/api/javax/servlet/jsp/tagext/BodyTag.html">BodyTag Lifecycle</a>
     * @see <a href="http://download.oracle.com/javaee/5/api/javax/servlet/jsp/tagext/IterationTag.html">IterationTag Lifecycle</a>
     * @see <a href="http://download.oracle.com/javaee/5/api/javax/servlet/jsp/tagext/TryCatchFinally.html">TryCatchFinally protocol</a>
     *
     * @param tag
     * @param pageContext
     * @param body
     * @throws JspException
     */
    private static void write(Tag tag, PageContext pageContext, String body) throws JspException {
        tag.setPageContext(pageContext);
        tag.setParent(null);

        try {
            int result = tag.doStartTag();

            if (body != null) {
                // Include the body
                if (result == Tag.EVAL_BODY_INCLUDE) {
                    if (tag instanceof BodyTagSupport) {
                        do {
                            pageContext.getOut().write(evaluate(pageContext, body));
                        }
                        while(((BodyTagSupport) tag).doAfterBody() == IterationTag.EVAL_BODY_AGAIN);
                    }
                    else {
                        pageContext.getOut().write(evaluate(pageContext, body));
                    }
                }
                // Buffer the body
                else if (result == BodyTag.EVAL_BODY_BUFFERED) {
                    if (!(tag instanceof BodyTag)) {
                        throw new IllegalStateException("Buffered body content is only allowed for instances of " + BodyTag.class);
                    }

                    ((BodyTagSupport) tag).setBodyContent(new BodyContentImpl(pageContext.pushBody()));
                    ((BodyTagSupport) tag).doInitBody();

                    do {
                        ((BodyTagSupport) tag).getBodyContent().append(evaluate(pageContext, body));
                    }
                    while(((BodyTagSupport) tag).doAfterBody() == IterationTag.EVAL_BODY_AGAIN);

                    pageContext.popBody();
                }
                // Skip the body
                else if (result == Tag.SKIP_BODY) {
                    // Do nothing
                }
            }

            tag.doEndTag();
        }
        catch (Throwable t) {
            if (tag instanceof TryCatchFinally) {
                try {
                    ((TryCatchFinally) tag).doCatch(t);
                }
                catch (Throwable throwable) {
                    throw new JspException(throwable);
                }
            }
        }
        finally {
            if (tag instanceof TryCatchFinally) {
                ((TryCatchFinally) tag).doFinally();
            }
        }

        tag.release();
    }

    // Pattern for matching tokens in tag body.
    private static final Pattern TOKEN_PATTERN = Pattern.compile("(\\$\\{)([^\\}]+)(\\})");

    /**
     * Finds and replaces tokens within tag body.
     *
     * <p>
     * Options for token pattern:
     * <ul>
     * <li><code>${myVar}</code>
     * <li><code>${myVar.value}</code>
     * <li><code>${myVar.nested.value}</code>
     * </ul>
     *
     * <p>
     * The above are replaced with the value evaluated by:
     * <ul>
     * <li><code>myVar</code>
     * <li><code>myVar.getValue()</code>
     * <li><code>myVar.getNested().getValue()</code>
     * </ul>
     *
     * <p>
     * Where <code>myVar</code> is the return value of <code>pageContext.findAttribute("myVar")</code>.
     *
     * @param pageContext
     * @param body
     * @return Evaluated content body
     * @throws JspException
     */
    static String evaluate(PageContext pageContext, String body) throws JspException {
        Matcher matcher = TOKEN_PATTERN.matcher(body);
        StringBuffer result = new StringBuffer();

        // Find any tokens that need to be replaced
        while (matcher.find()) {
            String[] parts = matcher.group(2).split("\\.");
            Object var = null;

            try {
                var = pageContext.findAttribute(parts[0]);
            } catch (RuntimeException e) {
                /* Ignore this exception, most likely NPE which will be handled by var initialized as null above */
            }

            if (parts.length > 1) {
                for (int i=1; i<parts.length; i++) {
                    try {
                        // Gracefully handle NullPointerException
                        if (var == null) break;

                        Class clazz = var.getClass();
                        Method method = clazz.getMethod("get" + parts[i].substring(0, 1).toUpperCase() + parts[i].substring(1));
                        var = method.invoke(var);
                    }
                    catch (NoSuchMethodException e) {
                        throw new JspException(e);
                    }
                    catch (InvocationTargetException e) {
                        throw new JspException(e);
                    }
                    catch (IllegalAccessException e) {
                        throw new JspException(e);
                    }
                }
            }

            // Display an empty string as opposed to "null"
            if (var == null) {
                var = "";
            }

            matcher.appendReplacement(result, String.valueOf(var));
        }
        matcher.appendTail(result);

        return result.toString();
    }

}
