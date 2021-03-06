/* 
 * The MIT License
 *
 * Copyright 2013 Tim Boudreau.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package com.mastfrog.acteur.headers;

import com.mastfrog.util.preconditions.Checks;
import com.mastfrog.util.strings.Strings;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.util.internal.AppendableCharSequence;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author Tim Boudreau
 */
final class AllowHeader extends AbstractHeader<Method[]> {

    AllowHeader(boolean isAllowOrigin) {
        super(Method[].class, isAllowOrigin ? HttpHeaderNames.ACCESS_CONTROL_ALLOW_METHODS : HttpHeaderNames.ALLOW);
    }

    @Override
    public CharSequence toCharSequence(Method[] value) {
        Checks.notNull("value", value);
        if (value.length == 1) {
            return value[0].toCharSequence();
        }
        AppendableCharSequence append = new AppendableCharSequence(15);
        for (int i = 0; i < value.length; i++) {
            append.append(value[i].toCharSequence());
            if (i != value.length -1) {
                append.append(',');
            }
        }
        return append;
    }

    @Override
    public Method[] toValue(CharSequence value) {
        Checks.notNull("value", value);
        CharSequence[] s = Strings.split(',', value);
        Method[] result = new Method[s.length];
        for (int i = 0; i < s.length; i++) {
            try {
                result[i] = Method.valueOf(s[i]);
            } catch (Exception e) {
                Logger.getLogger(AllowHeader.class.getName()).log(Level.INFO, "Bad methods in allow header '" + value + "'", e);
                return null;
            }
        }
        return result;
    }

}
