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

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.wrmsr.wava.clang.api.CxChildVisitResult;
import com.wrmsr.wava.clang.api.CxCursor;
import com.wrmsr.wava.clang.api.CxCursorKind;
import com.wrmsr.wava.clang.api.CxEvalValue;
import com.wrmsr.wava.clang.api.CxIndex;
import com.wrmsr.wava.clang.api.CxRuntime;
import com.wrmsr.wava.clang.api.CxTranslationUnit;
import com.wrmsr.wava.clang.api.CxTranslationUnitFlags;
import com.wrmsr.wava.util.POSIXUtils;

import static com.wrmsr.wava.util.function.Bind.bind;

// https://github.com/neelance/ffi_gen

// https://github.com/jnr/jffi/blob/master/src/test/java/com/kenai/jffi/ClosureTest.java
// https://github.com/jnr/jffi/blob/master/src/test/java/com/kenai/jffi/NumberTest.java
// https://github.com/jnr/jffi/blob/master/src/test/java/com/kenai/jffi/InvokerTest.java

// static void DoPrintMacros(Preprocessor &PP, raw_ostream *OS) {

// http://clang-developers.42468.n3.nabble.com/Extracting-macro-information-using-libclang-the-C-Interface-to-Clang-td4042648.html ugh
// https://fossies.org/linux/rspamd/clang-plugin/printf_check.cc hi

public class TestLibClangJnr
{
    public static CxChildVisitResult printCursor(int indent, CxCursor cursor, CxCursor parent)
    {
        if (!cursor.cursorIsNull()) {
            System.out.println(String.format("%s%-20s %-20s %s", Strings.repeat(" ", indent), cursor.getKind(), cursor.getSpelling(), cursor.getType().getSpelling()));
            if (cursor.getKind() == CxCursorKind.VarDecl || cursor.getKind() == CxCursorKind.IntegerLiteral || cursor.getKind() == CxCursorKind.StringLiteral) {
                CxEvalValue value = cursor.evaluate();
                if (value != null) {
                    System.out.println("***" + value);
                }
            }
            cursor.visitChildren(bind(TestLibClangJnr::printCursor, indent + 2)::apply);
            return CxChildVisitResult.Continue;
        }
        else {
            return CxChildVisitResult.Break;
        }
    }

    public static void main(String[] args)
            throws Throwable
    {
        System.out.println(POSIXUtils.getPOSIX().getpid());
        System.in.read();

        String libraryPath = "/Users/spinlock/src/llvm/clang/build/lib/libclang.dylib";
        libraryPath = "/Users/spinlock/src/llvm/clang/cmake-build-debug/lib/libclang.dylib";

        CxRuntime cxRuntime = JffiClangApi.create(libraryPath);

        try (CxIndex idx = cxRuntime.createIndex(0, 0)) {
            try (CxTranslationUnit tu = idx.parseTranslationUnit(
                    "/Users/spinlock/src/wrmsr/wava/tmp/a.cpp",
                    ImmutableList.of(
                            "-std=c++14",
                            "-I/Users/spinlock/src/wrmsr/wava/tmp/wasm-install/sysroot/include",
                            "--target=wasm32"
                    ),
                    ImmutableSet.of(CxTranslationUnitFlags.DetailedPreprocessingRecord))) {
                CxCursor cursor = tu.getCursor();

                cursor.visitChildren(bind(TestLibClangJnr::printCursor, 0)::apply);
            }
        }
    }
}
