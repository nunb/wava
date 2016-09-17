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

    private static final class NativeInvocationHandler
            implements InvocationHandler
    {
        private final ConcurrentMap<Method, MethodInvoker> invokers = new ConcurrentHashMap<>();
        private final Library library;

        NativeInvocationHandler(Library library)
        {
            this.library = library;
        }

        @Override
        public Object invoke(Object self, Method method, Object[] argArray)
                throws Throwable
        {
            return getMethodInvoker(method).invoke(argArray);
        }

        /**
         * Gets the {@link Invoker} for a method.
         *
         * @param method the method defined in the interface class
         * @return the <tt>Invoker</tt> to use to invoke the native function
         */
        private MethodInvoker getMethodInvoker(Method method)
        {
            MethodInvoker invoker = invokers.get(method);
            if (invoker != null) {
                return invoker;
            }
            invokers.put(method, invoker = createInvoker(library, method));
            return invoker;
        }
    }

    private static MethodInvoker createInvoker(Library library, Method method)
    {
        Class returnType = method.getReturnType();
        Class[] parameterTypes = method.getParameterTypes();
        Type ffiReturnType = convertClassToFFI(returnType);
        Type[] ffiParameterTypes = new Type[parameterTypes.length];
        for (int i = 0; i < ffiParameterTypes.length; ++i) {
            ffiParameterTypes[i] = convertClassToFFI(parameterTypes[i]);
        }
        final long address = library.getSymbolAddress(method.getName());
        if (address == 0) {
            throw new UnsatisfiedLinkError(String.format("Could not locate '%s': %s",
                    method.getName(), Library.getLastError()));
        }
        Function function = new Function(address, ffiReturnType, ffiParameterTypes);
        return new DefaultMethodInvoker(library, function, returnType, parameterTypes);
    }

    private static Type convertClassToFFI(Class type)
    {
        if (type == void.class || type == Void.class) {
            return Type.VOID;
        }
        else if (type == byte.class || type == Byte.class) {
            return Type.SINT8;
        }
        else if (type == short.class || type == Short.class) {
            return Type.SINT16;
        }
        else if (type == int.class || type == Integer.class) {
            return Type.SINT32;
        }
        else if (type == long.class || type == Long.class) {
            return Type.SINT64;
        }
        else if (type == float.class || type == Float.class) {
            return Type.FLOAT;
        }
        else if (type == double.class || type == Double.class) {
            return Type.DOUBLE;
        }
        else if (BigDecimal.class.isAssignableFrom(type)) {
            return Type.LONGDOUBLE;
        }
        else if (Address.class.isAssignableFrom(type)) {
            return Type.POINTER;
        }
        else if (JffiStruct.class.isAssignableFrom(type)) {
            try {
                return (Type) type.getDeclaredField("STRUCT").get(null);
            }
            catch (ReflectiveOperationException e) {
                throw Throwables.propagate(e);
            }
        }
        else {
            throw new IllegalArgumentException("Unknown type: " + type);
        }
    }

    private interface MethodInvoker
    {
        Object invoke(Object[] args);
    }

    private static final class DefaultMethodInvoker
            implements MethodInvoker
    {
        private final Library library;
        private final Function function;
        private final Class returnType;
        private final Class[] parameterTypes;

        DefaultMethodInvoker(Library library, Function function, Class returnType, Class[] parameterTypes)
        {
            this.library = library;
            this.function = function;
            this.returnType = returnType;
            this.parameterTypes = parameterTypes;
        }

        @Override
        public Object invoke(Object[] args)
        {
            HeapInvocationBuffer buffer = new HeapInvocationBuffer(function);
            checkState((parameterTypes.length == 0 && args == null) || (parameterTypes.length == args.length));
            for (int i = 0; i < parameterTypes.length; ++i) {
                if (parameterTypes[i] == byte.class || parameterTypes[i] == Byte.class) {
                    buffer.putByte(((Number) args[i]).intValue());
                }
                else if (parameterTypes[i] == short.class || parameterTypes[i] == Short.class) {
                    buffer.putShort(((Number) args[i]).intValue());
                }
                else if (parameterTypes[i] == int.class || parameterTypes[i] == Integer.class) {
                    buffer.putInt(((Number) args[i]).intValue());
                }
                else if (parameterTypes[i] == long.class || parameterTypes[i] == Long.class) {
                    buffer.putLong(((Number) args[i]).longValue());
                }
                else if (parameterTypes[i] == float.class || parameterTypes[i] == Float.class) {
                    buffer.putFloat(((Number) args[i]).floatValue());
                }
                else if (parameterTypes[i] == double.class || parameterTypes[i] == Double.class) {
                    buffer.putDouble(((Number) args[i]).doubleValue());
                }
                else if (BigDecimal.class.isAssignableFrom(parameterTypes[i])) {
                    buffer.putLongDouble(BigDecimal.class.cast(args[i]));
                }
                else if (Address.class.isAssignableFrom(parameterTypes[i])) {
                    buffer.putAddress(((Address) args[i]).address);
                }
                else {
                    throw new RuntimeException("Unknown parameter type: " + parameterTypes[i]);
                }
            }
            Invoker invoker = Invoker.getInstance();
            if (returnType == void.class || returnType == Void.class) {
                invoker.invokeInt(function, buffer);
                return null;
            }
            else if (returnType == byte.class || returnType == Byte.class) {
                return Byte.valueOf((byte) invoker.invokeInt(function, buffer));
            }
            else if (returnType == short.class || returnType == Short.class) {
                return Short.valueOf((short) invoker.invokeInt(function, buffer));
            }
            else if (returnType == int.class || returnType == Integer.class) {
                return Integer.valueOf(invoker.invokeInt(function, buffer));
            }
            else if (returnType == long.class || returnType == Long.class) {
                return Long.valueOf(invoker.invokeLong(function, buffer));
            }
            else if (returnType == float.class || returnType == Float.class) {
                return Float.valueOf(invoker.invokeFloat(function, buffer));
            }
            else if (returnType == double.class || returnType == Double.class) {
                return Double.valueOf(invoker.invokeDouble(function, buffer));
            }
            else if (BigDecimal.class.isAssignableFrom(returnType)) {
                return invoker.invokeBigDecimal(function, buffer);
            }
            else if (Address.class.isAssignableFrom(returnType)) {
                return new Address(invoker.invokeAddress(function, buffer));
            }
            else if (JffiStruct.class.isAssignableFrom(returnType)) {
                return invoker.invokeStruct(function, buffer);
            }
            throw new RuntimeException("Unknown return type: " + returnType);
        }
    }
}
