/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.wrmsr.wava.yen.parser.input;

import static java.util.Objects.requireNonNull;

public final class StringInput
    implements Input
{
    private final String str;

    private int base;

    public StringInput(String str)
    {
        this.str = requireNonNull(str);
        base = 0;
    }

    @Override
    public char get(int offset)
    {
        return str.charAt(base + offset);
    }

    @Override
    public String get(int start, int end)
    {
        return str.substring(base + start, base + end);
    }

    @Override
    public void advance(int offset)
    {
        base += offset;
    }
}
