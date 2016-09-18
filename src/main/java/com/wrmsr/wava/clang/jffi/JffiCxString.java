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
package com.wrmsr.wava.clang.jffi;

import com.kenai.jffi.Type;
import com.wrmsr.wava.clang.CxString;

import static com.google.common.base.Preconditions.checkState;
import static com.kenai.jffi.Struct.newStruct;

@SuppressWarnings("WeakerAccess")
public final class JffiCxString
        extends JffiStruct
        implements CxString
{
    static final Descriptor<JffiCxString> DESCRIPTOR = new Descriptor<>(
            JffiCxString.class,
            JffiCxString::new,
            newStruct(
                    Type.POINTER,
                    Type.UINT32));

    private boolean isDisposed = false;

    JffiCxString(JffiCxRuntime runtime, byte[] bytes)
    {
        super(runtime, bytes);
    }

    @Override
    public String get()
    {
        checkState(!isDisposed);
        return runtime.getLibClang().clang_getCString(this);
    }

    @Override
    public void close()
            throws Exception
    {
        if (!isDisposed) {
            runtime.getLibClang().clang_disposeString(this);
            isDisposed = true;
        }
    }
}
