/*===-- clang-c/Index.h - Indexing Public C Interface -------------*- C -*-===*\
|*                                                                            *|
|*                     The LLVM Compiler Infrastructure                       *|
|*                                                                            *|
|* This file is distributed under the University of Illinois Open Source      *|
|* License. See LICENSE_LLVM for details.                                     *|
|*                                                                            *|
|*===----------------------------------------------------------------------===*|
|*                                                                            *|
|* This header provides a public interface to a Clang library for extracting  *|
|* high-level symbol information from source files without exposing the full  *|
|* Clang C++ API.                                                             *|
|*                                                                            *|
\*===----------------------------------------------------------------------===*/
package com.wrmsr.wava.clang.jffi;

import com.kenai.jffi.Platform;

import static java.util.Objects.requireNonNull;

abstract class JffiPointer
        implements AutoCloseable
{
    static final int SIZE = Platform.getPlatform().addressSize();
    static final long MASK = Platform.getPlatform().addressMask();

    final JffiCxRuntime runtime;
    final long address;

    JffiPointer(JffiCxRuntime runtime, long address)
    {
        this.runtime = requireNonNull(runtime);
        this.address = address & MASK;
    }

    @Override
    public void close()
            throws Exception
    {
    }
}
