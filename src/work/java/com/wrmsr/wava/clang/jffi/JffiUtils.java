/*
 * Copyright (C) 2008-2010 Wayne Meissner
 *
 * This file is part of the JNR project.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.wrmsr.wava.clang.jffi;

import com.google.common.base.Throwables;
import com.kenai.jffi.Closure;
import com.kenai.jffi.Function;
import com.kenai.jffi.HeapInvocationBuffer;
import com.kenai.jffi.Invoker;
import com.kenai.jffi.Library;
import com.kenai.jffi.ObjectParameterStrategy;
import com.kenai.jffi.Platform;
import com.kenai.jffi.Type;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.google.common.base.Preconditions.checkState;

// https://github.com/jnr/jffi/blob/master/src/test/java/com/kenai/jffi/ClosureTest.java
// https://github.com/jnr/jffi/blob/master/src/test/java/com/kenai/jffi/NumberTest.java
// https://github.com/jnr/jffi/blob/master/src/test/java/com/kenai/jffi/InvokerTest.java
final class JffiUtils
{
    private JffiUtils()
    {
    }

    private static final Pattern BAD_ELF = Pattern.compile("(.*): (invalid ELF header|file too short|invalid file format)");
    private static final Pattern ELF_GROUP = Pattern.compile("GROUP\\s*\\(\\s*(\\S*).*\\)");

    static Library openLibrary(String path)
    {
        Library lib;

        lib = Library.getCachedInstance(path, Library.LAZY | Library.GLOBAL);
        if (lib != null) {
            return lib;
        }

        // If dlopen() fails with 'invalid ELF header', then it is likely to be a ld script - parse it for the real library path
        Matcher badElf = BAD_ELF.matcher(Library.getLastError());
        if (badElf.lookingAt()) {
            File f = new File(badElf.group(1));
            if (f.isFile() && f.length() < (4 * 1024)) {
                Matcher sharedObject = ELF_GROUP.matcher(readAll(f));
                if (sharedObject.find()) {
                    return Library.getCachedInstance(sharedObject.group(1), Library.LAZY | Library.GLOBAL);
                }
            }
        }

        return null;
    }

    private static String readAll(File f)
    {
        BufferedReader br = null;
        try {
            br = new BufferedReader(new InputStreamReader(new FileInputStream(f)));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line);
            }
            return sb.toString();
        }
        catch (IOException e) {
            throw new RuntimeException(e);
        }
        finally {
            if (br != null) {
                try {
                    br.close();
                }
                catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        }
    }

    static final class Address
            extends Number
    {
        final int SIZE = Platform.getPlatform().addressSize();
        final long MASK = Platform.getPlatform().addressMask();

        final long address;

        Address(long address)
        {
            this.address = address & MASK;
        }

        Address(Closure.Handle closure)
        {
            this(closure.getAddress());
        }

        @Override
        public int intValue()
        {
            return (int) address;
        }

        @Override
        public long longValue()
        {
            return address;
        }

        @Override
        public float floatValue()
        {
            return (float) address;
        }

        @Override
        public double doubleValue()
        {
            return (double) address;
        }
    }

    static final class HeapArrayStrategy
            extends ObjectParameterStrategy
    {
        private int offset, length;

        HeapArrayStrategy(int offset, int length)
        {
            super(HEAP);
            this.offset = offset;
            this.length = length;
        }

        @Override
        public long address(Object parameter)
        {
            return 0L;
        }

        @Override
        public Object object(Object parameter)
        {
            return parameter;
        }

        @Override
        public int offset(Object parameter)
        {
            return offset;
        }

        @Override
        public int length(Object parameter)
        {
            return length;
        }
    }

    static final class DirectStrategy
            extends ObjectParameterStrategy
    {
        DirectStrategy()
        {
            super(DIRECT);
        }

        @Override
        public long address(Object parameter)
        {
            return ((Address) parameter).address;
        }

        @Override
        public Object object(Object parameter)
        {
            throw new IllegalStateException("not a heap object");
        }

        @Override
        public int offset(Object parameter)
        {
            throw new IllegalStateException("not a heap object");
        }

        @Override
        public int length(Object parameter)
        {
            throw new IllegalStateException("not a heap object");
        }
    }

    /**
     * Creates a new InvocationHandler mapping methods in the <tt>interfaceClass</tt>
     * to functions in the native library.
     *
     * @param <T> the type of <tt>interfaceClass</tt>
     * @param name the native library to load
     * @param interfaceClass the interface that contains the native method description
     * @return a new instance of <tt>interfaceClass</tt> that can be used to call
     * functions in the native library.
     */
    static <T> T loadLibrary(String name, Class<T> interfaceClass)
    {
        Library lib = Library.getCachedInstance(name, Library.LAZY);
        if (lib == null) {
            throw new UnsatisfiedLinkError(String.format("Could not load '%s': %s",
                    name, Library.getLastError()));
        }
        return interfaceClass.cast(Proxy.newProxyInstance(interfaceClass.getClassLoader(),
                new Class[] {interfaceClass},
                new NativeInvocationHandler(lib)));
    }
}
